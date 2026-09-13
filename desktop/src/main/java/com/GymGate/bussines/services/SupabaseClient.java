package com.GymGate.bussines.services;


import com.GymGate.bussines.util.AppPaths;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SupabaseClient {

    private final static HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final static ObjectMapper mapper = new ObjectMapper();

    /** Supabase project ref + public publishable (anon) key — compiled in. Neither
     *  is a secret: the key is RLS-protected and ships inside every client anyway.
     *  Baking them in removes a config file that could go missing or be hand-edited.
     *  To point the app at another Supabase project, change these two constants. */
    private final static String projectId = "fapdvyrtkgwyjjaecpfr";
    private final static String publishableKey = "sb_publishable_-R3D05EkczCSgRpvGrUNmQ_6AGhO-qf";

    /** Supabase Auth (GoTrue) credentials, loaded once at class init from
     *  {@code /supabase.properties} — the only thing that file still carries.
     *  Every REST call authenticates as this user (Bearer = their access token)
     *  instead of as the bare publishable key, so row-owner defaults and RLS see
     *  a real {@code auth.uid()}. Blank when the file is absent or not filled in:
     *  once a login is actually needed (no usable saved token) that trips the
     *  kill switch — same "trial ended" lock-out as a rejected login. */
    private final static String SUPABASE_CONFIG = "/supabase.properties";
    private final static String authEmail;
    private final static String authPassword;

    static {
        String em = "";
        String pw = "";
        try (InputStream in = SupabaseClient.class.getResourceAsStream(SUPABASE_CONFIG)) {
            if (in == null) {
                System.err.println("Supabase auth: " + SUPABASE_CONFIG + " not found on classpath — "
                        + "the app will lock once a login is needed");
            } else {
                Properties props = new Properties();
                props.load(in);
                em = props.getProperty("email", "").trim();
                pw = props.getProperty("password", "").trim();
                if (em.isEmpty() || pw.isEmpty()) {
                    System.err.println("Supabase auth: " + SUPABASE_CONFIG
                            + " has no 'email'/'password' — the app will lock once a login is needed");
                }
            }
        } catch (IOException e) {
            System.err.println("Supabase auth: could not read " + SUPABASE_CONFIG + " — " + e.getMessage());
        }
        authEmail = em;
        authPassword = pw;
    }

    // ---- Auth: use the saved JWT, else log in with email/password -------------

    private final static Object SESSION_LOCK = new Object();

    /** In-memory copy of the current access token and its absolute expiry
     *  (epoch seconds). Guarded by {@link #SESSION_LOCK}. The token is a
     *  long-lived JWT (the project sets a ~1-week expiry), so there is no
     *  refresh-token dance — when it expires we simply log in again. */
    private static String accessToken;
    private static long   accessTokenExpiresAt;
    private static boolean triedDiskSession;

    /** Lazily-resolved path of the on-disk session (plaintext properties: same
     *  trust level as the compiled-in publishable key / bundled credentials).
     *  Null when there is no writable data dir — the token then lives in memory
     *  only. */
    private static Path sessionFile;
    private static boolean sessionFileResolved;

    private static Path sessionFile() {
        if (!sessionFileResolved) {
            sessionFileResolved = true;
            try {
                sessionFile = AppPaths.resolve("supabase-session.properties").toPath();
            } catch (RuntimeException e) {
                System.err.println("Supabase auth: no writable data dir for the session file; "
                        + "keeping the token in memory only (" + e.getMessage() + ")");
            }
        }
        return sessionFile;
    }

    /**
     * A valid user access token, or {@code null} when one cannot be obtained.
     * Resolution order, exactly as specified:
     * <ol>
     *   <li>a still-valid in-memory token (from this run);</li>
     *   <li>the token in the session file on disk, if it is not expired;</li>
     *   <li>otherwise — token expired, or no session file — fall back to the
     *       {@code email}/{@code password} in {@code supabase.properties}:
     *       <ul>
     *         <li>credentials missing → trip the kill switch ("trial ended");</li>
     *         <li>credentials present → log in: GoTrue accepts → new token saved
     *             to disk and returned; GoTrue rejects → trip the kill switch;</li>
     *         <li>login could not be attempted (offline / 5xx) → {@code null},
     *             retried on the next tick, no lock.</li>
     *       </ul></li>
     * </ol>
     * Never throws.
     */
    private static String authToken() {
        if (authRevoked.get()) {
            return null;
        }
        synchronized (SESSION_LOCK) {
            if (isFresh(Instant.now().getEpochSecond())) {
                return accessToken;
            }
            if (!triedDiskSession) {
                triedDiskSession = true;
                loadSession();
                if (isFresh(Instant.now().getEpochSecond())) {
                    return accessToken;
                }
            }
            if (authEmail.isEmpty() || authPassword.isEmpty()) {
                tripAuthRevoked("auth", "no Supabase email/password in " + SUPABASE_CONFIG
                        + " and no usable saved token");
                return null;
            }
            return login() ? accessToken : null;
        }
    }

    /** In-memory token good for at least another minute. Call under lock. */
    private static boolean isFresh(long nowEpoch) {
        return accessToken != null && accessTokenExpiresAt - nowEpoch > 60;
    }

    /**
     * Signs in with the email/password from {@code supabase.properties} against
     * {@code POST .../auth/v1/token?grant_type=password}. On success stores the
     * new token (memory + disk) and returns true.
     *
     * <p>If the server <b>rejects</b> the login — a 4xx (wrong credentials in
     * {@code supabase.properties}, or a dead publishable key) — the kill switch
     * is tripped and the "trial ended" screen is shown. A transient failure
     * (5xx, 429, a 2xx with no token, or the server being unreachable) just
     * returns false to retry on the next tick. Callers ({@link #authToken()})
     * guarantee the credentials are non-blank. Must be called holding
     * {@link #SESSION_LOCK}. Never throws.
     */
    private static boolean login() {
        String jsonBody = mapper.createObjectNode()
                .put("email", authEmail).put("password", authPassword).toString();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://" + projectId + ".supabase.co/auth/v1/token?grant_type=password"))
                .timeout(Duration.ofSeconds(20))
                .header("apikey", publishableKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status / 100 == 2) {
                JsonNode body = mapper.readTree(response.body());
                String at = body.path("access_token").asText("");
                if (!at.isEmpty()) {
                    long now = Instant.now().getEpochSecond();
                    accessToken = at;
                    accessTokenExpiresAt = body.path("expires_at")
                            .asLong(now + body.path("expires_in").asLong(3600));
                    saveSession();
                    System.out.println("Supabase login succeeded; token valid for ~"
                            + Math.max(0, accessTokenExpiresAt - now) + "s");
                    return true;
                }
                System.err.println("Supabase login returned 2xx but no access_token; retrying next tick: "
                        + response.body());
                return false;
            }

            if (status / 100 == 4) {
                // The server reached us and refused the login: wrong email/password
                // in supabase.properties, or a revoked publishable key.
                tripAuthRevoked("login", "GoTrue rejected the login (HTTP " + status + "): " + response.body());
                return false;
            }
            // 5xx / 429 — GoTrue is having a moment; retry next tick, don't lock.
            System.err.println("Supabase login failed (HTTP " + status + "); retrying next tick: " + response.body());
            return false;
        } catch (IOException e) {
            System.err.println("Supabase login skipped (network unavailable): " + e.getMessage());
            return false;
        } catch (InterruptedException e) {
            // App shutting down mid-request — not a login failure.
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Drops the cached + on-disk token so the next {@link #authToken()} logs in
     *  again. Called after a REST request still 401s with a token we believed
     *  was valid. */
    private static void invalidateAccessToken() {
        synchronized (SESSION_LOCK) {
            accessToken = null;
            accessTokenExpiresAt = 0;
            deleteSession();
        }
    }

    private static void loadSession() {
        Path file = sessionFile();
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try (InputStream in = Files.newInputStream(file)) {
            Properties p = new Properties();
            p.load(in);
            String at = p.getProperty("access_token", "").trim();
            if (!at.isEmpty()) {
                accessToken = at;
                long exp = 0;
                try {
                    exp = Long.parseLong(p.getProperty("expires_at", "0").trim());
                } catch (NumberFormatException ignored) {
                }
                accessTokenExpiresAt = exp;
            }
        } catch (IOException e) {
            System.err.println("Supabase auth: could not read the saved session — " + e.getMessage());
        }
    }

    private static void saveSession() {
        Path file = sessionFile();
        if (file == null) {
            return;
        }
        Properties p = new Properties();
        if (accessToken != null) p.setProperty("access_token", accessToken);
        p.setProperty("expires_at", Long.toString(accessTokenExpiresAt));
        try (OutputStream out = Files.newOutputStream(file)) {
            p.store(out, "GymGate Supabase session — do not edit. Delete to force a re-login.");
        } catch (IOException e) {
            System.err.println("Supabase auth: could not save the session — " + e.getMessage());
        }
    }

    private static void deleteSession() {
        Path file = sessionFile();
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            System.err.println("Supabase auth: could not delete the stale session file — " + e.getMessage());
        }
    }

    /**
     * Sends an authenticated request, built from a bearer token by {@code build}.
     *
     * <p>On a 401 with a token we believed valid, drops it and logs in again:
     * <ul>
     *   <li>fresh login succeeds, retry also 401 → the key really is revoked,
     *       trip the kill switch;</li>
     *   <li>fresh login is rejected (4xx) → {@link #login} tripped the kill
     *       switch; hand the 401 back;</li>
     *   <li>fresh login fails transiently (offline / 5xx) → return {@code null}
     *       so the caller retries next tick <em>without</em> locking — we can't
     *       tell an expired token from a revoked key here.</li>
     * </ul>
     *
     * <p>Returns {@code null} when no token can be obtained at all — the caller
     * treats that exactly like a failed send.
     */
    private static HttpResponse<String> authedSend(Function<String, HttpRequest> build, String context)
            throws IOException, InterruptedException {
        String token = authToken();
        if (token == null) {
            return null;
        }
        HttpResponse<String> response = client.send(build.apply(token), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 401 || authRevoked.get()) {
            return response;
        }
        invalidateAccessToken();
        String fresh = authToken();
        if (authRevoked.get()) {
            return response;          // login() rejected the credentials and locked us out
        }
        if (fresh == null) {
            return null;              // transient re-login failure — retry next tick, don't lock
        }
        response = client.send(build.apply(fresh), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 401) {
            noteIfAuthRevoked(401, context);
        }
        return response;
    }

    /** One-way kill-switch latch. Set when authentication is lost for good:
     *  a login GoTrue rejects (wrong credentials / revoked key), missing
     *  credentials when a login is needed, or a REST 401 that survives a fresh
     *  login. Once set, every call short-circuits to its "failed" result and the
     *  UI is locked. Not persisted — a restart clears it and retries. */
    private final static AtomicBoolean authRevoked = new AtomicBoolean(false);

    /** Registered by the app shell. Invoked exactly once, on the thread that
     *  first sees auth is lost, so it can lock the UI (the "trial ended" screen). */
    private static volatile Runnable authRevokedHandler;

    public static void setAuthRevokedHandler(Runnable handler) {
        authRevokedHandler = handler;
    }

    public static boolean isAuthRevoked() {
        return authRevoked.get();
    }

    /** Call with every non-2xx status. A 401 means the key is dead: latch it and
     *  fire the handler once. Returns true iff the status was a 401. */
    private static boolean noteIfAuthRevoked(int status, String context) {
        if (status != 401) {
            return false;
        }
        tripAuthRevoked(context, "HTTP 401 — key rotated or revoked");
        return true;
    }

    /** Latches {@link #authRevoked} and fires {@link #authRevokedHandler} once,
     *  on the first thread to see the auth failure. Used both for a 401 on a REST
     *  call (revoked publishable key) and for GoTrue refusing the email/password
     *  login (wrong credentials in {@code supabase.properties}). */
    private static void tripAuthRevoked(String context, String detail) {
        if (authRevoked.compareAndSet(false, true)) {
            System.err.println("Supabase auth lost during " + context + " — " + detail);
            Runnable handler = authRevokedHandler;
            if (handler != null) {
                try {
                    handler.run();
                } catch (Throwable t) {
                    System.err.println("authRevokedHandler failed: " + t.getMessage());
                }
            }
        }
    }

    /** Max rows per upsert POST — bounds the request body and the echoed
     *  representation, and stays well under any PostgREST payload limit. */
    private final static int MAX_ROWS_PER_REQUEST = 500;

    /**
     * Bulk-upserts the given rows into the Supabase {@code table} through the
     * PostgREST endpoint. Each entry in {@code objects} is a JSON object string
     * carrying its {@code id} (the client-generated SQLite id) but NOT
     * {@code user_id} — that column defaults to {@code auth.uid()} server-side.
     * The remote primary key is the composite {@code (id, user_id)}, so the
     * conflict target is passed explicitly as {@code ?on_conflict=id,user_id}:
     * a re-pushed row updates this user's copy (merge-duplicates), and a row
     * with the same {@code id} owned by a different build's user is a separate
     * row, not a collision. Large inputs are split into chunks of
     * {@link #MAX_ROWS_PER_REQUEST}.
     *
     * <p>Never throws. Returns {@code true} ONLY when every chunk was accepted
     * <em>and</em> Supabase echoed back a row for every row sent — a bare 2xx is
     * not proof of persistence (an upsert with no conflict target, or an RLS
     * policy / trigger that silently drops rows, still returns 2xx). Any missing
     * connection, timeout, error response, or short echo is reported as
     * {@code false} so the caller keeps the rows unsynced and retries.
     */
    public static boolean send(List<String> objects, String table) {

        if (objects.isEmpty()) return true;
        if (authRevoked.get()) return false;

        for (int from = 0; from < objects.size(); from += MAX_ROWS_PER_REQUEST) {
            int to = Math.min(from + MAX_ROWS_PER_REQUEST, objects.size());
            if (!sendChunk(objects.subList(from, to), table)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Unauthenticated connectivity check — a short {@code GET} to the project's
     * public {@code /auth/v1/health}. Any HTTP response (even an error status)
     * means the internet and Supabase are reachable; a timeout or network
     * exception means offline. Blocks up to ~8s, so call it off the FX thread.
     */
    public static boolean isReachable() {
        try {
            client.send(HttpRequest.newBuilder()
                    .uri(URI.create("https://" + projectId + ".supabase.co/auth/v1/health"))
                    .timeout(Duration.ofSeconds(8))
                    .header("apikey", publishableKey)
                    .GET()
                    .build(), HttpResponse.BodyHandlers.discarding());
            return true;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Cheap authenticated probe: a tiny {@code GET .../rest/v1/plans?select=id&limit=1}.
     * Called once per sync tick so an expired token is renewed — and a revoked
     * key or missing/wrong credentials are caught — within ~60s even when the gym
     * is idle and nothing is being pushed. An RLS 403, a 404, or an offline
     * failure are all ignored; lock-out is driven by {@link #authToken()} /
     * {@link #login()} / a surviving 401.
     */
    public static void pingAuth() {
        if (authRevoked.get()) {
            return;
        }
        try {
            // authedSend + authToken already trip the kill switch on a dead key or
            // wrong credentials; the response itself is not needed.
            authedSend(token -> HttpRequest.newBuilder()
                    .uri(URI.create("https://" + projectId + ".supabase.co/rest/v1/plans?select=id&limit=1"))
                    .timeout(Duration.ofSeconds(15))
                    .header("apikey", publishableKey)
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build(), "auth probe");
        } catch (IOException e) {
            // Offline / DNS / TLS — not an auth problem, leave it alone.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean sendChunk(List<String> objects, String table) {

        // ?on_conflict=id,user_id — the tables' PK is composite (id, user_id);
        // see docs/supabase-composite-pk.sql. merge-duplicates then upserts on
        // that key, so each build's user has its own id space.
        String url = "https://" + projectId + ".supabase.co/rest/v1/" + table + "?on_conflict=id,user_id";
        String body = "[" + String.join(",", objects) + "]";

        try {
            HttpResponse<String> response = authedSend(token -> HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("apikey", publishableKey)
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "resolution=merge-duplicates,return=representation")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(), "upsert into '" + table + "'");
            if (response == null) {
                return false;
            }
            if (response.statusCode() / 100 != 2) {
                noteIfAuthRevoked(response.statusCode(), "upsert into '" + table + "'");
                System.err.println("Supabase upsert into '" + table + "' rejected (HTTP "
                        + response.statusCode() + "): " + response.body());
                return false;
            }

            int persisted;
            try {
                JsonNode returned = mapper.readTree(response.body());
                persisted = returned.isArray() ? returned.size() : -1;
            } catch (IOException parse) {
                System.err.println("Supabase upsert into '" + table
                        + "' returned an unreadable body; treating as not persisted: " + parse.getMessage());
                return false;
            }

            if (persisted < objects.size()) {
                System.err.println("Supabase upsert into '" + table + "' persisted "
                        + persisted + "/" + objects.size() + " rows; leaving them unsynced to retry");
                return false;
            }
            return true;
        } catch (IOException e) {
            // Offline, DNS failure, connection refused, timeout, ... — expected
            // when the client has no internet. Leave the rows unsynced; the next
            // scheduled run will retry them.
            System.err.println("Supabase sync for '" + table + "' skipped (network unavailable): " + e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Fetches the set of primary-key {@code id}s that currently exist in the
     * Supabase {@code table} (PostgREST {@code ?select=id}). Used by
     * {@code SyncingService} to detect rows that are marked synced locally but
     * are absent remotely (e.g. a batch that returned 2xx without persisting, or
     * a remotely-wiped table) so they can be re-queued.
     *
     * <p>Same never-throws contract as {@link #send}: returns {@code null} on a
     * missing connection, timeout, non-2xx response, or unparseable body — the
     * caller must treat {@code null} as "unknown" and leave local state alone.
     */
    public static Set<Integer> existingIds(String table) {

        if (authRevoked.get()) return null;

        Set<Integer> ids = new HashSet<>();
        int pageSize = 1000;

        for (int offset = 0; ; offset += pageSize) {

            String url = "https://" + projectId + ".supabase.co/rest/v1/" + table
                    + "?select=id&order=id.asc&limit=" + pageSize + "&offset=" + offset;

            int pageCount;
            try {
                HttpResponse<String> response = authedSend(token -> HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .header("apikey", publishableKey)
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build(), "id fetch for '" + table + "'");
                if (response == null) {
                    return null;
                }
                if (response.statusCode() / 100 != 2) {
                    noteIfAuthRevoked(response.statusCode(), "id fetch for '" + table + "'");
                    System.err.println("Supabase id fetch for '" + table + "' rejected (HTTP "
                            + response.statusCode() + "): " + response.body());
                    return null;
                }
                JsonNode arr = mapper.readTree(response.body());
                if (!arr.isArray()) {
                    System.err.println("Supabase id fetch for '" + table + "' returned an unexpected body; skipping reconcile");
                    return null;
                }
                pageCount = arr.size();
                for (JsonNode node : arr) {
                    JsonNode id = node.get("id");
                    if (id != null && id.canConvertToInt()) {
                        ids.add(id.asInt());
                    }
                }
            } catch (IOException e) {
                System.err.println("Supabase id fetch for '" + table + "' skipped (network unavailable): " + e.getMessage());
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }

            // Last page reached. (If a project lowers PostgREST's db-max-rows
            // below pageSize this stops early and reconcile simply re-pushes the
            // rows it couldn't see — an idempotent upsert, never data loss.)
            if (pageCount < pageSize) {
                return ids;
            }
        }
    }

    /**
     * Batch-deletes the rows whose primary key {@code id} is in {@code ids} from
     * the Supabase {@code table} (PostgREST {@code ?id=in.(...)}). Same
     * never-throws contract as {@link #send}: returns {@code true} only when the
     * delete was accepted (or there was nothing to delete), {@code false} on a
     * missing connection, timeout, or error response.
     */
    public static boolean delete(List<Integer> ids, String table) {

        if (ids.isEmpty()) return true;
        if (authRevoked.get()) return false;

        String idList = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        String url = "https://" + projectId + ".supabase.co/rest/v1/" + table + "?id=in.(" + idList + ")";

        try {
            HttpResponse<String> response = authedSend(token -> HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("apikey", publishableKey)
                    .header("Authorization", "Bearer " + token)
                    .header("Prefer", "return=minimal")
                    .DELETE()
                    .build(), "delete from '" + table + "'");
            if (response == null) {
                return false;
            }
            if (response.statusCode() / 100 == 2) {
                return true;
            }
            noteIfAuthRevoked(response.statusCode(), "delete from '" + table + "'");
            System.err.println("Supabase delete from '" + table + "' rejected (HTTP "
                    + response.statusCode() + "): " + response.body());
            return false;
        } catch (IOException e) {
            System.err.println("Supabase delete from '" + table + "' skipped (network unavailable): " + e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * This user's rows in {@code table} that Supabase currently marks
     * {@code synced = 0} — edits made on another client (the owner's phone) that
     * the desktop has not applied yet. Paged. Same never-throws contract as
     * {@link #send}: {@code null} on a missing connection, timeout, non-2xx, or
     * unreadable body (caller must leave local state alone); an empty list means
     * there is nothing to pull.
     */
    public static List<JsonNode> fetchUnsynced(String table) {
        return fetchPaged(table, "synced=eq.0&", "unsynced fetch for '" + table + "'");
    }

    /**
     * Every row this user owns in {@code table}, unfiltered — used once, to
     * seed a brand-new local database from the Supabase copy (see
     * {@code SyncingService.restoreFromCloud}). Paged. Same never-throws
     * contract as {@link #send}: {@code null} on a missing connection, timeout,
     * non-2xx, or unreadable body; an empty list means the table is genuinely
     * empty in the cloud.
     */
    public static List<JsonNode> fetchAll(String table) {
        return fetchPaged(table, "", "full fetch for '" + table + "'");
    }

    /** Shared paging loop behind {@link #fetchUnsynced} and {@link #fetchAll} —
     *  {@code filter} is an optional {@code "col=eq.val&"} fragment prepended to
     *  the query string, empty for an unfiltered fetch. */
    private static List<JsonNode> fetchPaged(String table, String filter, String context) {
        if (authRevoked.get()) return null;

        List<JsonNode> out = new ArrayList<>();
        int pageSize = 1000;

        for (int offset = 0; ; offset += pageSize) {
            String url = "https://" + projectId + ".supabase.co/rest/v1/" + table
                    + "?" + filter + "order=id.asc&limit=" + pageSize + "&offset=" + offset;
            try {
                HttpResponse<String> response = authedSend(token -> HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .header("apikey", publishableKey)
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build(), context);
                if (response == null) {
                    return null;
                }
                if (response.statusCode() / 100 != 2) {
                    noteIfAuthRevoked(response.statusCode(), context);
                    System.err.println("Supabase " + context + " rejected (HTTP "
                            + response.statusCode() + "): " + response.body());
                    return null;
                }
                JsonNode arr = mapper.readTree(response.body());
                if (!arr.isArray()) {
                    System.err.println("Supabase " + context + " returned an unexpected body");
                    return null;
                }
                arr.forEach(out::add);
                if (arr.size() < pageSize) {
                    return out;
                }
            } catch (IOException e) {
                System.err.println("Supabase " + context + " skipped (network unavailable): " + e.getMessage());
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }

    /**
     * Sets {@code synced = 1} on the given rows of {@code table} in Supabase —
     * called after the desktop has pulled and applied them, so they are not
     * pulled again. Chunked under the URL length limit. Same never-throws
     * contract; {@code true} only when every chunk was accepted.
     */
    public static boolean markRemoteSynced(String table, List<Integer> ids) {
        if (ids.isEmpty()) return true;
        if (authRevoked.get()) return false;

        for (int from = 0; from < ids.size(); from += MAX_ROWS_PER_REQUEST) {
            List<Integer> chunk = ids.subList(from, Math.min(from + MAX_ROWS_PER_REQUEST, ids.size()));
            String idList = chunk.stream().map(String::valueOf).collect(Collectors.joining(","));
            String url = "https://" + projectId + ".supabase.co/rest/v1/" + table + "?id=in.(" + idList + ")";
            try {
                HttpResponse<String> response = authedSend(token -> HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .header("apikey", publishableKey)
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .header("Prefer", "return=minimal")
                        .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"synced\":1}"))
                        .build(), "mark synced in '" + table + "'");
                if (response == null || response.statusCode() / 100 != 2) {
                    if (response != null) {
                        noteIfAuthRevoked(response.statusCode(), "mark synced in '" + table + "'");
                        System.err.println("Supabase mark-synced for '" + table + "' rejected (HTTP "
                                + response.statusCode() + "): " + response.body());
                    }
                    return false;
                }
            } catch (IOException e) {
                System.err.println("Supabase mark-synced for '" + table + "' skipped (network unavailable): " + e.getMessage());
                return false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

}
