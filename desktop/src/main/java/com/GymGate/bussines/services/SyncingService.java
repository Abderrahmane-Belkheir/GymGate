package com.GymGate.bussines.services;

import com.GymGate.UI.NotificationCenter;
import com.GymGate.UI.controller.HomeController;
import com.GymGate.bussines.db.DatabaseManager;
import com.GymGate.bussines.db.dao.DeletionDao;
import com.GymGate.bussines.models.Sexe;
import com.GymGate.model.NotificationData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.application.Platform;


import java.math.BigDecimal;
import java.net.http.HttpRequest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadFactory;


public class SyncingService {

    /** Keeps {@code id IN (?, ?, …)} well under SQLite's 999-host-parameter limit. */
    private static final int MAX_IDS_PER_STATEMENT = 500;

    /** Every table that carries a {@code synced} flag, in FK-safe push order. */
    private static final String[] SYNCED_TABLES = {
            "plans", "members", "payments", "attendance", "face_embeddings", "member_photos","seance"
    };

    /** Tables also pulled DOWN from Supabase each tick (owner edits on the phone),
     *  FK-safe order. Runs before the push so an owner edit made in the same
     *  minute as a staff edit wins. */
    private static final String[] PULL_TABLES = { "plans", "members", "payments","seance" };

    /** {@code created_at} is kept from the local row on a pull; {@code synced} is
     *  forced to 1. Everything else in a cloud row overwrites the local value. */
    private static final Set<String> PULL_SKIP_COLUMNS = Set.of("synced", "created_at");

    /** A restore has no local row to preserve {@code created_at} from — it's a
     *  fresh seed, so the cloud's original timestamp is written too. */
    private static final Set<String> RESTORE_SKIP_COLUMNS = Set.of("synced");

    /** The two BLOB columns PostgREST returns as a {@code "\x"+hex} string
     *  rather than a JSON-native type — everywhere else a cloud row's columns
     *  map onto local ones with no special handling. */
    private static boolean isByteaColumn(String table, String column) {
        return ("face_embeddings".equals(table) && "embedding".equals(column))
                || ("member_photos".equals(table) && "image".equals(column));
    }

    private static final int TICK_SECONDS = 60;
    /**
     * One full reconcile pass over every table every this many ticks — i.e. ~1h.
     * Reconcile downloads the complete remote id list for all seven tables
     * (uncompressed), which on a cellular link is by far the heaviest thing the
     * sync does. It is only a safety net for a row stuck at {@code synced = 1}
     * that never actually landed remotely, so an hourly sweep is plenty; the
     * per-minute push/pull already keeps normal traffic flowing.
     */
    private static final int FULL_PASS_EVERY = 60;

    private final Connection connection;
    private final ObjectMapper mapper;
    private final ScheduledExecutorService executorService;
    private final DeletionDao deletionDao;
    private final Map<String, List<String>> columnCache = new HashMap<>();
    private long tick = 0;
    private static SyncingService instance;
    /**
     * True only if every table pushed cleanly on the last periodic {@link #cycle()}
     * (nothing pending, or Supabase accepted the batch). Read from other threads
     * (a DAO calling {@link #pushDirectly}/{@link #deleteDirectly} right after a
     * local write) and written from the sync thread inside {@link #cycle()} —
     * {@code volatile} so a write on one is actually visible on the other.
     * Gates the direct-push path only: skip an immediate attempt we already know
     * would fail (offline, revoked key) and let the next periodic tick retry
     * instead of spending a network round-trip finding that out again.
     */
    private volatile boolean canPushDirectly = false;

    /** Daemon so a running sync tick can never keep the JVM alive after the UI
     *  closes; {@link #stop()} is still the clean path (finishes teardown, stops
     *  scheduling) but a hard exit no longer hangs on this thread. */
    private static final ThreadFactory THREAD_FACTORY = r -> {
        Thread t = new Thread(r, "syncing-service");
        t.setDaemon(true);
        return t;
    };

    private SyncingService(){this.connection= DatabaseManager.getInstance().getConnection();this.mapper=new ObjectMapper();this.executorService= Executors.newScheduledThreadPool(1, THREAD_FACTORY);this.deletionDao= DeletionDao.getInstance();}

    public void start(){
        executorService.scheduleAtFixedRate(this::cycle, 0, TICK_SECONDS, TimeUnit.SECONDS);
    }

    /** Stops the periodic sync (app shutdown / restart). Idempotent. Waits
     *  briefly for an in-flight tick so a push mid-flight isn't cut off. */
    public void stop(){
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(3, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * One scheduled tick, all on the single scheduler thread so the shared DB
     * connection is never touched concurrently.
     *
     * <ul>
     *   <li><b>Every tick</b> (~60s): push each table's not-yet-synced rows and
     *       replay pending deletions. Cheap when idle — just a local
     *       {@code WHERE synced = 0} per table, no HTTP unless there is
     *       something to send.</li>
     *   <li><b>Every {@value #FULL_PASS_EVERY}th tick</b> (~1h, and the first
     *       run): also run {@link #reconcile} on every table. Reconcile lists all
     *       remote ids per table, so it is deliberately kept off the fast
     *       cadence.</li>
     * </ul>
     */
    private void cycle() {
        try {
            // Catch a rotated / revoked Supabase key within one tick, even when
            // the gym is idle and nothing below would otherwise hit the network.
            SupabaseClient.pingAuth();

            boolean fullPass = (tick % FULL_PASS_EVERY == 0);
            tick++;

            if (fullPass) {
                for (String table : SYNCED_TABLES) {
                    try {
                        reconcile(table);
                    } catch (SQLException e) {
                        System.err.println("Reconcile query failed for '" + table + "': " + e.getMessage());
                    }
                }
            }

            // Pull owner edits down first: if the same row was changed by staff
            // locally and by the owner remotely within this minute, the pull
            // overwrites the local row and flips it to synced=1, so the push
            // below skips it — the owner's change wins.
            PullTally pulled = new PullTally();
            for (String table : PULL_TABLES) {
                pullTable(table, pulled);
            }
            applyPullSideEffects(pulled);
            boolean allPushed=true;
            for (String table : SYNCED_TABLES) {
                boolean tablePushed = pushTable(table);
                if (!tablePushed) {
                    canPushDirectly = false;
                }
                allPushed = tablePushed && allPushed;
            }
            if (allPushed) canPushDirectly = true;
            syncDeletions();
        } catch (Exception e) {
            // Never let a failed tick kill the schedule.
            System.err.println("Sync cycle failed: " + e.getMessage());
        }
    }

    /** FK-safe order for a full restore: {@code plans}/{@code seance} have no
     *  dependency on the others, {@code members} needs {@code plans},
     *  everything else needs {@code members} (and {@code payments} also needs
     *  {@code plans}/{@code seance}). */
    private static final String[] RESTORE_TABLES = {
            "plans", "seance", "members", "payments", "attendance", "face_embeddings", "member_photos"
    };

    /**
     * Seeds a brand-new local database from the Supabase copy — the reverse of
     * the periodic push. Called once from the startup path (GymGateEntry) as its
     * own progress step, but ONLY when the database file had to be created this
     * run; a normal start skips it entirely.
     *
     * <p>For each table in {@link #RESTORE_TABLES} (FK-safe order): page every
     * row down from Supabase ({@link SupabaseClient#fetchAll}) and upsert it
     * into the local table with {@code synced = 1} — it is already in the
     * cloud, so the periodic push must not re-send it. An upsert rather than a
     * plain insert because {@code seance} is pre-seeded with 4 zero-price rows
     * by {@link DatabaseManager} before this ever runs (see its schema
     * creation), so those ids already exist locally and the real cloud prices
     * need to overwrite them, not conflict with them.
     *
     * <p>{@code pending_deletions} is left empty — a fresh db has none.
     *
     * <p>Contract: must run AFTER {@link DatabaseManager#getInstance()} (schema
     * exists) and BEFORE {@link #start()} (so the periodic push cannot race the
     * seed). Never throws — a failed or partial restore (offline, one table
     * erroring, one bad row) leaves the app usable with whatever local data
     * resulted, empty database included. {@code plans}/{@code members}/
     * {@code payments}/{@code seance} self-heal on the very next periodic tick
     * ({@link #PULL_TABLES} pulls them regardless); {@code attendance}/
     * {@code face_embeddings}/{@code member_photos} are push-only afterward, so
     * a table that didn't come down here on a bad connection stays empty until
     * the app is reinstalled and this runs again.
     *
     * <p>{@code DatabaseManager}'s constructor already loaded
     * {@code face_embeddings} into memory once, against what was — on a fresh
     * install — necessarily an empty table, since it runs before this method
     * ever gets a chance to populate it. So once every table above has been
     * restored, {@link DatabaseManager#reloadMemberEmbeddings()} re-reads it —
     * otherwise recognition would see zero known faces until the app happened
     * to be restarted a second time.
     */
    public void restoreFromCloud() {
        for (String table : RESTORE_TABLES) {
            try {
                restoreTable(table);
            } catch (RuntimeException e) {
                System.err.println("Cloud restore of '" + table + "' failed (non-fatal): " + e.getMessage());
            }
        }
        try {
            DatabaseManager.getInstance().reloadMemberEmbeddings();
        } catch (RuntimeException e) {
            System.err.println("Reloading embeddings after cloud restore failed (non-fatal): " + e.getMessage());
        }
    }

    private void restoreTable(String table) {
        List<JsonNode> rows = SupabaseClient.fetchAll(table);
        if (rows == null || rows.isEmpty()) {
            return;
        }
        int applied = 0;
        for (JsonNode row : rows) {
            try {
                upsertLocal(table, row, RESTORE_SKIP_COLUMNS);
                applied++;
            } catch (Exception e) {
                // Catches SQLException (FK violation — its parent row didn't
                // make it down, or never existed) and any RuntimeException (a
                // malformed bytea string from HexFormat, say) alike: one bad
                // row must not sink the rest of the table's restore.
                System.err.println("Restore upsert failed for " + table + "#" + jsonText(row, "id")
                        + " (skipped): " + e.getMessage());
            }
        }
        System.out.println("Restored " + applied + "/" + rows.size() + " row(s) into " + table.toUpperCase()
                + " from Supabase");
    }

    /**
     * Replays locally-recorded deletions against Supabase, one batch DELETE per
     * table, clearing each table's tombstones only once its remote delete has
     * been accepted.
     */
    private void syncDeletions() {
        Map<String, List<Integer>> pending;
        try {
            pending = deletionDao.pending();
        } catch (SQLException e) {
            System.err.println("Local deletion query failed: " + e.getMessage());
            return;
        }
        pending.forEach((table, ids) -> {
            if (SupabaseClient.delete(ids, table)) {
                try {
                    deletionDao.clear(table, ids);
                } catch (SQLException e) {
                    System.err.println("Failed to clear deletion tombstones for '" + table + "': " + e.getMessage());
                }
            }
        });
    }

    /**
     * Pulls the owner's edits for one table: fetches the Supabase rows still
     * flagged {@code synced = 0}, upserts each into the local table (INSERT new,
     * UPDATE existing) with {@code synced = 1}, then clears the remote flag so
     * they are not pulled again. A row that fails to apply locally (e.g. an FK
     * whose parent hasn't arrived yet) is skipped and retried next tick.
     *
     * <p>If the remote flag can't be cleared (network blip), the rows are pulled
     * and re-applied next tick — an idempotent upsert of the same data.
     *
     * <p>Once the local rows are in place, the always-loaded screens are nudged
     * to re-read via {@link AppEvents} — normally an "in-app mutation only" bus,
     * but a pulled row is exactly that from the UI's point of view: a payment
     * pulled from the owner's phone is a new row for the Payments list, a pulled
     * member may move between the Active / Inactive filters, a pulled plan
     * refreshes the Plans grid. What changed is rolled into {@code tally};
     * {@link #applyPullSideEffects} then updates the Home tiles and posts a
     * notification for each plan / member change (payments stay silent).
     */
    private void pullTable(String table, PullTally tally) {
        List<JsonNode> rows;
        try {
            rows = SupabaseClient.fetchUnsynced(table);
        } catch (RuntimeException e) {
            System.err.println("Pull fetch failed for '" + table + "': " + e.getMessage());
            return;
        }
        if (rows == null || rows.isEmpty()) {
            return;
        }

        List<Integer> applied = new ArrayList<>();
        for (JsonNode row : rows) {
            JsonNode idNode = row.get("id");
            if (idNode == null || !idNode.canConvertToInt()) {
                continue;
            }
            int id = idNode.asInt();
            try {
                boolean wasExpiringToday = "members".equals(table) && countsAsExpiringToday(id);
                MemberSnap before = "members".equals(table) ? memberSnapshot(id) : null;

                boolean inserted = upsertLocal(table, row);
                applied.add(id);

                switch (table) {
                    case "payments" -> {
                        // Feeds the Revenue tile only — never a notification.
                        if (inserted) {
                            JsonNode amount = row.get("amount");
                            if (amount != null && amount.isNumber()) {
                                tally.revenue += amount.asLong();
                            }
                        }
                    }
                    case "members" -> {
                        MemberChange change = classifyMemberChange(row, before);
                        tally.notes.add(memberNote(row, change));
                        if (change == MemberChange.RENEWED) {
                            tally.memberRenewals++;
                        }
                        if (wasExpiringToday && !countsAsExpiringToday(id)) {
                            tally.expiryCleared++;
                        }
                    }
                    case "plans" -> tally.notes.add(planNote(row, inserted));
                    // Seance rows are seeded once and never inserted/deleted after —
                    // the only real-world change is the owner editing a price, so
                    // there's no RENEWED/CANCELED-style classification to do, just
                    // a plain "this price changed" note (see SeanceDao's own comment).
                    case "seance" -> tally.notes.add(seanceNote(row));
                    default -> { }
                }
            } catch (SQLException e) {
                System.err.println("Pull upsert failed for " + table + "#" + id + " (will retry): " + e.getMessage());
            }
        }
        if (applied.isEmpty()) {
            return;
        }

        tally.applied += applied.size();
        notifyScreens(table);

        if (SupabaseClient.markRemoteSynced(table, applied)) {
            System.out.println("Pulled " + applied.size() + " owner edit(s) into " + table.toUpperCase());
        } else {
            System.err.println("Pulled " + applied.size() + " row(s) into " + table
                    + " but couldn't clear the remote synced flag — will re-pull next tick");
        }
    }

    /** What a whole pull pass changed: the Home-tile deltas plus one ready-made
     *  notification per plan / member row (payments never get a notification). */
    private static final class PullTally {
        int applied;         // rows upserted across every pull table
        long revenue;        // Σ amount of newly-inserted payment rows
        int memberRenewals;  // members the pull classified as RENEWED (see classifyMemberChange)
        int expiryCleared;   // members who dropped out of "expiring today" via a pulled renewal
        final List<NotificationData> notes = new ArrayList<>();
    }

    /**
     * After a pull pass: bump Home's live tiles and raise the collected
     * notifications. Revenue follows the payment rows (their amount, whenever
     * one arrives new); Renewed Subs follows the *member* event — a member row
     * the pull classifies as {@link MemberChange#RENEWED} — not the payment, so
     * it ticks up as soon as the renewal is recognized even if its payment row
     * lands on a later tick. If the member had been "expiring today" that tile
     * ticks down. The user-facing text talks about the membership / plan, never
     * the payment.
     */
    private void applyPullSideEffects(PullTally t) {
        if (t.applied == 0) {
            return;
        }

        int revenueDelta = (int) t.revenue;
        int renewals = t.memberRenewals;
        int expiryDelta = -t.expiryCleared;
        Platform.runLater(() -> {
            if (revenueDelta != 0) {
                HomeController.updateStatValue(I18nService.get("Revenue"), revenueDelta);
            }
            if (renewals > 0) {
                HomeController.updateStatValue(I18nService.get("Renewed_Subs"), renewals);
            }
            if (expiryDelta != 0) {
                HomeController.updateStatValue(I18nService.get("Expires_Today"), expiryDelta);
            }
        });

        NotificationCenter center = NotificationCenter.getInstance();
        for (NotificationData n : t.notes) {
            center.push(n);
        }
    }

    /** The local plan_id / end_date of a member just before a pull overwrites it,
     *  so {@link #memberNote} can tell a renewal from a cancellation. */
    private record MemberSnap(boolean existed, boolean hadPlan, String endDate) { }

    private MemberSnap memberSnapshot(int id) throws SQLException {
        try (PreparedStatement st = connection.prepareStatement(
                "SELECT plan_id, end_date FROM members WHERE id = ?")) {
            st.setInt(1, id);
            try (ResultSet rs = st.executeQuery()) {
                if (!rs.next()) {
                    return new MemberSnap(false, false, null);
                }
                rs.getInt("plan_id");
                boolean hadPlan = !rs.wasNull();
                return new MemberSnap(true, hadPlan, rs.getString("end_date"));
            }
        }
    }

    /** What a pulled member row amounts to, judged against its local state just
     *  before the pull overwrote it. Drives both the notification text and the
     *  Renewed Subs tile — a single source of truth for "was this a renewal". */
    private enum MemberChange { RENEWED, CANCELED, UPDATED }

    private static MemberChange classifyMemberChange(JsonNode row, MemberSnap before) {
        JsonNode planId = row.get("plan_id");
        boolean nowHasPlan = planId != null && !planId.isNull();
        String newEnd = jsonText(row, "end_date");

        if (before != null && before.hadPlan() && !nowHasPlan) {
            return MemberChange.CANCELED;
        }

        boolean renewed = nowHasPlan && (before == null || !before.existed() || !before.hadPlan()
                || (!newEnd.isEmpty() && before.endDate() != null && newEnd.compareTo(before.endDate()) > 0));
        return renewed ? MemberChange.RENEWED : MemberChange.UPDATED;
    }

    /** "&lt;name&gt; got renewed / canceled from the owner's phone" — the payment
     *  behind a renewal is never mentioned. */
    private static NotificationData memberNote(JsonNode row, MemberChange change) {
        String name = (jsonText(row, "first_name") + " " + jsonText(row, "last_name")).trim();
        return switch (change) {
            case CANCELED -> new NotificationData("fas-ban", "icon-tile-orange",
                    I18nService.get("Notif_member_title"),
                    I18nService.get("Notif_member_canceled").replace("{name}", name));
            case RENEWED -> new NotificationData("fas-sync-alt", "icon-tile-green",
                    I18nService.get("Notif_member_title"),
                    I18nService.get("Notif_member_renewed").replace("{name}", name));
            case UPDATED -> new NotificationData("fas-pen", "icon-tile-blue",
                    I18nService.get("Notif_member_title"),
                    I18nService.get("Notif_member_updated").replace("{name}", name));
        };
    }

    /** "Plan &lt;name&gt; added / updated from the owner's phone". */
    private static NotificationData planNote(JsonNode row, boolean inserted) {
        String name = jsonText(row, "name");
        return new NotificationData(inserted ? "fas-plus" : "fas-pen", "icon-tile-blue",
                I18nService.get("Notif_plan_title"),
                I18nService.get(inserted ? "Notif_plan_added" : "Notif_plan_updated").replace("{name}", name));
    }

    /** "&lt;Male/Female · With cardio/Standard&gt; price was changed to &lt;price&gt; DZD
     *  from the owner's phone" — the only field that ever actually changes on a
     *  {@code seance} row, see {@link com.GymGate.bussines.db.dao.SeanceDao}. */
    private static NotificationData seanceNote(JsonNode row) {
        Sexe sexe = Sexe.valueOf(jsonText(row, "sexe"));
        boolean cardio = row.path("cardio").asBoolean(false);
        String category = I18nService.get(sexe == Sexe.MALE ? "Male" : "Female")
                + " · " + I18nService.get(cardio ? "With_cardio" : "Standard");
        String price = String.valueOf(row.path("price").asLong());
        return new NotificationData("fas-tag", "icon-tile-blue",
                I18nService.get("Single_Session"),
                I18nService.get("Notif_seance_updated")
                        .replace("{category}", category)
                        .replace("{price}", price));
    }

    private static String jsonText(JsonNode row, String field) {
        JsonNode n = row.get(field);
        return n == null || n.isNull() ? "" : n.asText();
    }

    /** Maps a just-pulled table to the in-app event that makes the open screens
     *  re-read. Tables with no live screen fall through silently. */
    private void notifyScreens(String table) {
        switch (table) {
            case "payments" -> AppEvents.publish(AppEvents.Type.PAYMENT_ADDED);
            case "members"  -> AppEvents.publish(AppEvents.Type.MEMBER_UPDATED);
            // PlansController re-reads séance prices in the same refresh as
            // plans (see its PLAN_UPDATED subscription) — one screen, one event.
            case "plans", "seance" -> AppEvents.publish(AppEvents.Type.PLAN_UPDATED);
            default -> { /* no live listener */ }
        }
    }

    /** Whether member {@code id} is currently counted by Home's "Expires Today"
     *  tile — the same rule as {@code MemberDao.count(EXPIRED)}: their plan ends
     *  today, or their remaining days have hit zero and they checked in today. */
    private boolean countsAsExpiringToday(int id) throws SQLException {
        String today = LocalDate.now().toString();
        String sql = """
            SELECT COUNT(*) FROM members
            WHERE id = ?
              AND ( date(end_date) = ?
                    OR ( remaining_days <= 0 AND EXISTS (
                           SELECT 1 FROM attendance
                           WHERE attendance.member_id = members.id
                             AND substr(attendance.date, 1, 10) = ? ) ) )
            """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.setString(2, today);
            stmt.setString(3, today);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /** INSERT the cloud row, or UPDATE it if the id already exists locally, with
     *  {@code synced = 1}. {@code created_at} and any Supabase-only column
     *  ({@code user_id}, …) are left untouched.
     *  @return {@code true} if a new row was inserted, {@code false} on update. */
    private boolean upsertLocal(String table, JsonNode row) throws SQLException {
        return upsertLocal(table, row, PULL_SKIP_COLUMNS);
    }

    /** Same as {@link #upsertLocal(String, JsonNode)} but with a caller-chosen
     *  skip set — {@link #restoreTable} uses {@link #RESTORE_SKIP_COLUMNS} so a
     *  fresh seed also restores each row's original {@code created_at}. */
    private boolean upsertLocal(String table, JsonNode row, Set<String> skipColumns) throws SQLException {
        boolean existed;
        try (PreparedStatement check = connection.prepareStatement("SELECT 1 FROM " + table + " WHERE id = ?")) {
            check.setLong(1, row.get("id").asLong());
            try (ResultSet rs = check.executeQuery()) {
                existed = rs.next();
            }
        }

        List<String> use = new ArrayList<>();
        for (String col : localColumns(table)) {
            if (skipColumns.contains(col)) {
                continue;
            }
            if (row.hasNonNull(col) || (row.has(col) && row.get(col).isNull())) {
                use.add(col);
            }
        }
        if (!use.contains("id")) {
            throw new SQLException("cloud row has no usable 'id'");
        }

        String columns = String.join(", ", use) + ", synced";
        String placeholders = use.stream().map(c -> "?").collect(Collectors.joining(", ")) + ", 1";
        String updates = use.stream()
                .filter(c -> !c.equals("id"))
                .map(c -> c + " = excluded." + c)
                .collect(Collectors.joining(", "));
        String sql = "INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ") "
                + "ON CONFLICT(id) DO UPDATE SET " + (updates.isEmpty() ? "synced = 1" : updates + ", synced = 1");

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (int i = 0; i < use.size(); i++) {
                String column = use.get(i);
                bind(stmt, i + 1, isByteaColumn(table, column), row.get(column));
            }
            stmt.executeUpdate();
        }
        return !existed;
    }

    private static void bind(PreparedStatement stmt, int idx, boolean bytea, JsonNode v) throws SQLException {
        if (v == null || v.isNull()) {
            stmt.setNull(idx, bytea ? Types.BLOB : Types.VARCHAR);
        } else if (bytea) {
            // PostgREST's bytea hex-input format: "\x" + hex — see rowToJson's
            // encoding of the same columns going the other way.
            String hex = v.asText();
            if (hex.startsWith("\\x")) {
                hex = hex.substring(2);
            }
            stmt.setBytes(idx, HexFormat.of().parseHex(hex));
        } else if (v.isBoolean())             stmt.setInt(idx, v.asBoolean() ? 1 : 0);
        else if (v.isIntegralNumber())        stmt.setLong(idx, v.asLong());
        else if (v.isNumber())                stmt.setDouble(idx, v.asDouble());
        else                                  stmt.setString(idx, v.asText());
    }

    private List<String> localColumns(String table) throws SQLException {
        List<String> cached = columnCache.get(table);
        if (cached != null) {
            return cached;
        }
        List<String> cols = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement("PRAGMA table_info(" + table + ")");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                cols.add(rs.getString("name"));
            }
        }
        columnCache.put(table, cols);
        return cols;
    }

    /**
     * Pushes a table's not-yet-synced rows to Supabase and, only if the batch
     * was accepted, flips exactly those rows to synced=1 — matched by the ids
     * captured during the fetch, so rows inserted between the fetch and the
     * update are never marked without having been sent. Runs every tick; a no-op
     * (one local query, no HTTP) when nothing is pending.
     */
    private boolean pushTable(String table) {
        try {
            Batch batch = fetchNonSynced(table);
            if (batch.ids().isEmpty()) {
                return true;
            }
            if (SupabaseClient.send(batch.rows(), table)) {
                markSynced(table, batch.ids());
                return true;
            }
        } catch (SQLException e) {
            System.err.println("Local sync query failed for '" + table + "': " + e.getMessage());
        }
        return false;
    }

    /**
     * Best-effort immediate push of one table's not-yet-synced rows, called
     * right after a local create/update — in ADDITION to, never instead of,
     * the periodic {@link #cycle()}. Skipped entirely when {@link #canPushDirectly}
     * is already false: the last periodic cycle found Supabase unreachable or
     * rejecting, so trying again this instant would very likely fail too and
     * only spend cellular data finding that out — the next tick retries
     * regardless of this call.
     *
     * <p>Submitted onto the SAME single-thread scheduler as {@link #cycle()},
     * so it can never run concurrently with a scheduled tick and race the
     * shared DB connection — it simply queues immediately before or after
     * whatever tick is in flight. Never blocks the caller.
     */
    public void pushDirectly(String table) {
        if (!canPushDirectly) {
            return;
        }
        try {
            executorService.execute(() -> {
                if (!pushTable(table)) {
                    canPushDirectly = false;
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // The scheduler was shut down (app closing) between the flag check
            // above and this submission. The row is already durably synced=0
            // locally; if the app restarts, the periodic cycle picks it up.
            // Never let a shutdown race surface as an error to the caller.
        }
    }

    /**
     * Best-effort immediate replay of every currently-pending deletion
     * tombstone for one table, called right after a local delete — in
     * ADDITION to, never instead of, the periodic {@link #syncDeletions()}.
     * Deliberately flushes the WHOLE table's backlog (like {@link #pushDirectly}
     * does), not just the row that triggered this call: a burst of several
     * deletes in quick succession would otherwise fire one HTTP DELETE per
     * row instead of one batched call, and — same as pushDirectly — a second
     * call arriving while the first is still queued simply finds nothing left
     * to send once the first has run, instead of re-sending duplicates.
     *
     * <p>Same gating/threading rules as {@link #pushDirectly}. On success the
     * flushed tombstones are cleared; on failure they're left in
     * {@code pending_deletions} for the next periodic tick to retry, same as
     * any other failed delete.
     */
    public void deleteDirectly(String table) {
        if (!canPushDirectly) {
            return;
        }
        try {
            executorService.execute(() -> {
                List<Integer> ids;
                try {
                    ids = deletionDao.pending().getOrDefault(table, List.of());
                } catch (SQLException e) {
                    System.err.println("Local deletion query failed for '" + table + "': " + e.getMessage());
                    // Same treatment pushDirectly gives a local failure in
                    // pushTable/fetchNonSynced: canPushDirectly means "confident
                    // the sync path is healthy right now", and a failure is a
                    // failure whether it's local or remote — back off until the
                    // next periodic cycle re-verifies, rather than only backing
                    // off for network-level failures.
                    canPushDirectly = false;
                    return;
                }
                if (ids.isEmpty()) {
                    return;
                }
                if (SupabaseClient.delete(ids, table)) {
                    try {
                        deletionDao.clear(table, ids);
                    } catch (SQLException e) {
                        System.err.println("Failed to clear deletion tombstones for '"
                                + table + "': " + e.getMessage());
                    }
                } else {
                    canPushDirectly = false;
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // Same shutdown race as pushDirectly — the tombstone already
            // recorded locally survives; the next run's periodic tick replays it.
        }
    }

    /**
     * Full-pass safety net (~5 min): clears {@code synced=1} on every local row
     * of {@code table} whose id is not present in Supabase, so the next
     * {@link #pushTable} re-uploads it. {@code synced=1} is otherwise a one-way
     * latch — a row marked synced without actually landing remotely (a batch
     * that returned 2xx without persisting, a remotely-wiped table) would be
     * orphaned forever. A local row missing remotely can only mean that or an
     * out-of-band delete; legitimate deletes go through {@code pending_deletions},
     * which removes the row locally too, so they are never seen here. No-ops when
     * the remote id list can't be fetched (offline / error):
     * {@code SupabaseClient.existingIds} returns null and local state is left
     * untouched.
     */
    private void reconcile(String table) throws SQLException {
        List<Integer> localSynced = syncedIds(table);
        if (localSynced.isEmpty()) {
            return;
        }
        Set<Integer> remote = SupabaseClient.existingIds(table);
        if (remote == null) {
            return;
        }
        List<Integer> missing = new ArrayList<>();
        for (int id : localSynced) {
            if (!remote.contains(id)) {
                missing.add(id);
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        clearSynced(table, missing);
        System.out.println("Reconcile " + table.toUpperCase() + ": " + missing.size()
                + " row(s) marked synced but missing in Supabase — re-queued");
    }

    private List<Integer> syncedIds(String table) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement("SELECT id FROM " + table + " WHERE synced = 1");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getInt("id"));
            }
        }
        return ids;
    }

    private void clearSynced(String table, List<Integer> ids) throws SQLException {
        setSynced(table, ids, 0);
    }

    private void markSynced(String table, List<Integer> ids) throws SQLException {
        setSynced(table, ids, 1);
    }

    /**
     * Sets {@code synced} to {@code value} for the given ids, in chunks small
     * enough to stay under SQLite's host-parameter limit — a single
     * {@code IN (?, ?, …)} with a few thousand params throws, which (post-push)
     * would strand rows at synced=0 and re-upload them forever.
     * {@code table} is always one of sync()'s fixed literals, never user input.
     */
    private void setSynced(String table, List<Integer> ids, int value) throws SQLException {
        for (int from = 0; from < ids.size(); from += MAX_IDS_PER_STATEMENT) {
            List<Integer> chunk = ids.subList(from, Math.min(from + MAX_IDS_PER_STATEMENT, ids.size()));
            String placeholders = String.join(",", Collections.nCopies(chunk.size(), "?"));
            try (PreparedStatement stmt = connection.prepareStatement(
                    "UPDATE " + table + " SET synced = " + value + " WHERE id IN (" + placeholders + ")")) {
                for (int i = 0; i < chunk.size(); i++) {
                    stmt.setInt(i + 1, chunk.get(i));
                }
                stmt.executeUpdate();
            }
        }
    }

    private Batch fetchNonSynced(String table) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        List<String> rows = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement("SELECT * FROM " + table + " WHERE synced = 0");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getInt("id"));
                rows.add(rowToJson(rs));
            }
        }
        return new Batch(ids, rows);
    }

    private record Batch(List<Integer> ids, List<String> rows) {}

    /** Serialises the ResultSet's current row into a JSON object string keyed by column name. */
    private String rowToJson(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        ObjectNode row = mapper.createObjectNode();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            String column = meta.getColumnLabel(i);
            if (column.equals("synced")) {
                // Local rows being pushed are synced=0; the copy that lands in
                // Supabase should record them as already synced.
                row.put(column, 1);
                continue;
            }
            Object value = rs.getObject(i);
            if (value == null) {
                row.putNull(column);
            } else if (value instanceof Integer v) {
                row.put(column, v);
            } else if (value instanceof Long v) {
                row.put(column, v);
            } else if (value instanceof Double v) {
                row.put(column, v);
            } else if (value instanceof Float v) {
                row.put(column, v);
            } else if (value instanceof BigDecimal v) {
                row.put(column, v);
            } else if (value instanceof Boolean v) {
                row.put(column, v);
            } else if (value instanceof byte[] v) {
                // PostgreSQL bytea hex-input format ("\x" + hex): PostgREST feeds
                // this straight into a bytea column, so the raw bytes are stored
                // as-is. Sending the byte[] to Jackson instead would base64 it,
                // and a bytea column would then store the base64 *string's* bytes
                // (a double wrap). Restore reverses this: strip "\x", parse hex.
                row.put(column, "\\x" + HexFormat.of().formatHex(v));
            } else {
                row.put(column, value.toString());
            }
        }
        return row.toString();
    }

    /** Stops the sync scheduler only if it was ever started — safe to call from
     *  the shutdown path without forcing the service (and its DB connection) to
     *  be constructed just to tear it down again. */
    public static synchronized void stopIfRunning(){
        if (instance != null) {
            instance.stop();
        }
    }

    public static SyncingService getInstance(){
        if(instance==null){
            instance=new SyncingService();
        }
        return instance;
    }
}
