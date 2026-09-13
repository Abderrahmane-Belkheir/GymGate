package com.GymGate.bussines.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A single, reusable WhatsApp Web window.
 *
 * <p>The first {@link #openChat} launches a small Edge/Chrome app-mode window on
 * a dedicated profile ({@code %LOCALAPPDATA%\GymGate\wa-profile}) with remote
 * debugging on {@link #DEBUG_PORT}. Every later call steers that same window to
 * the new chat over the DevTools protocol instead of opening another window. If
 * the window has been closed (or the port isn't answering) it launches a fresh
 * one; if no Chromium browser is installed it falls back to the default browser.
 *
 * <p>Blocks on HTTP + WebSocket for ~1–2s — call it off the FX thread.
 */
public final class WhatsAppWindow {

    private static final int DEBUG_PORT = 9339;
    /** Window size, also used by callers to centre it over their own window. */
    public static final int WIN_W = 760;
    public static final int WIN_H = 720;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)).build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WhatsAppWindow() {
    }

    /**
     * Opens WhatsApp Web on a chat with {@code e164} (digits only, no '+') with
     * {@code text} pre-filled, reusing the one window when it is still open.
     * {@code topLeft} places the window on first open (may be {@code null}).
     */
    public static synchronized void openChat(String e164, String text, int[] topLeft) {
        if (e164 == null || e164.isBlank()) {
            return;
        }
        String url = "https://web.whatsapp.com/send?phone=" + e164 + "&text="
                + URLEncoder.encode(text == null ? "" : text, StandardCharsets.UTF_8).replace("+", "%20");
        if (!steerOpenWindow(url)) {
            launchWindow(url, topLeft);
        }
    }

    // ---- reuse the open window via the DevTools protocol ---------------------

    private static boolean steerOpenWindow(String url) {
        String pageWs = whatsAppPageSocket();
        if (pageWs == null) {
            return false;
        }
        try {
            WebSocket ws = HTTP.newWebSocketBuilder()
                    .buildAsync(URI.create(pageWs), new WebSocket.Listener() { })
                    .get(3, TimeUnit.SECONDS);
            ws.sendText("{\"id\":1,\"method\":\"Page.navigate\",\"params\":{\"url\":\""
                    + url.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}}", true).join();
            ws.sendText("{\"id\":2,\"method\":\"Page.bringToFront\"}", true).join();
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
            return true;
        } catch (Exception e) {
            System.err.println("WhatsApp window steer failed, opening a new one: " + e.getMessage());
            return false;
        }
    }

    /** DevTools socket URL of the open {@code web.whatsapp.com} page, or null. */
    private static String whatsAppPageSocket() {
        try {
            HttpResponse<String> res = HTTP.send(HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + DEBUG_PORT + "/json"))
                    .timeout(Duration.ofSeconds(2))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            for (JsonNode t : MAPPER.readTree(res.body())) {
                if ("page".equals(t.path("type").asText())
                        && t.path("url").asText().contains("web.whatsapp.com")) {
                    String ws = t.path("webSocketDebuggerUrl").asText(null);
                    if (ws != null && !ws.isBlank()) {
                        return ws;
                    }
                }
            }
        } catch (Exception ignored) {
            // no window / port silent / a different profile — the caller launches one
        }
        return null;
    }

    // ---- first launch -------------------------------------------------------

    private static void launchWindow(String url, int[] topLeft) {
        File browser = findChromiumBrowser();
        if (browser != null) {
            List<String> cmd = new ArrayList<>(List.of(
                    browser.getAbsolutePath(),
                    "--app=" + url,
                    "--window-size=" + WIN_W + "," + WIN_H,
                    "--remote-debugging-port=" + DEBUG_PORT));
            File profile = gymGateSubDir("wa-profile");
            if (profile != null) {
                cmd.add("--user-data-dir=" + profile.getAbsolutePath());
            }
            if (topLeft != null) {
                cmd.add("--window-position=" + topLeft[0] + "," + topLeft[1]);
            }
            try {
                new ProcessBuilder(cmd).start();
                return;
            } catch (IOException ex) {
                System.err.println("WhatsApp app-window launch failed, opening default browser: " + ex.getMessage());
            }
        }
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (IOException | RuntimeException ex) {
            System.err.println("Could not open WhatsApp Web: " + ex.getMessage());
        }
    }

    /** {@code %LOCALAPPDATA%\GymGate\<name>}, or {@code null} if LOCALAPPDATA is unset. */
    private static File gymGateSubDir(String name) {
        String lad = System.getenv("LOCALAPPDATA");
        return lad == null ? null : new File(lad + "\\GymGate", name);
    }

    /** First installed Chromium browser (Edge, then Chrome), or {@code null}. */
    private static File findChromiumBrowser() {
        String pf = System.getenv("ProgramFiles");
        String pf86 = System.getenv("ProgramFiles(x86)");
        String lad = System.getenv("LOCALAPPDATA");
        String[] candidates = {
                pf86 != null ? pf86 + "\\Microsoft\\Edge\\Application\\msedge.exe" : null,
                pf != null ? pf + "\\Microsoft\\Edge\\Application\\msedge.exe" : null,
                pf != null ? pf + "\\Google\\Chrome\\Application\\chrome.exe" : null,
                pf86 != null ? pf86 + "\\Google\\Chrome\\Application\\chrome.exe" : null,
                lad != null ? lad + "\\Google\\Chrome\\Application\\chrome.exe" : null,
        };
        for (String c : candidates) {
            if (c != null && new File(c).isFile()) {
                return new File(c);
            }
        }
        return null;
    }
}
