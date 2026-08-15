package com.ultimateimprovments.mechanics.security.auth;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Built-in HTTP server for GitHub 2FA.
 * <p>
 * Workflow:
 * <ol>
 *   <li>The player clicks a link in chat → {@code GET <public_url>/auth?state=...}</li>
 *   <li>The server redirects to {@code github.com/login/oauth/authorize} (OAuth App).</li>
 *   <li>GitHub redirects back: {@code GET <public_url>/callback?code=...&state=...}</li>
 *   <li>The server exchanges the code for an access token, fetches the GitHub login
 *       and verifies it against the one linked to the player's UUID ({@link Auth2FA#getGithubUsername}).</li>
 *   <li>On match — the session is marked approved and the player is authenticated.</li>
 * </ol>
 * <p>
 * Implemented on plain {@link ServerSocket} (no jdk.httpserver), so it does not depend
 * on the {@code jdk.httpserver} module being present in the server runtime. Handles only
 * GET requests; everything else — 404.
 */
public class GithubAuthServer {

    private static GithubAuthServer instance;

    private static final String GITHUB_AUTHORIZE_URL = "https://github.com/login/oauth/authorize";
    private static final String GITHUB_TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String GITHUB_API_USER_URL = "https://api.github.com/user";

    private static final Pattern JSON_STRING_PATTERN = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServerSocket serverSocket;
    private Thread acceptThread;

    private GithubAuthServer() {}

    // =========================
    // LIFECYCLE
    // =========================
    public static synchronized void init() {
        if (instance != null && instance.running.get()) return;
        instance = new GithubAuthServer();
        instance.start();
    }

    public static synchronized void shutdown() {
        if (instance != null) {
            instance.stop();
            instance = null;
        }
    }

    /** true if the HTTP server is actually listening on the port (avoids giving out dead links). */
    public static boolean isRunning() {
        return instance != null && instance.running.get();
    }

    private void start() {
        int port = AuthConfig.getGithubPort();

        if (AuthConfig.getGithubClientId().isEmpty() || AuthConfig.getGithubClientSecret().isEmpty()) {
            ConsoleLogger.warn("[Auth2FA] GitHub 2FA enabled, but auth.2fa.github.client_id / client_secret are empty!");
            ConsoleLogger.warn("[Auth2FA] Create an OAuth App at https://github.com/settings/developers and fill the config.");
            return;
        }
        if (AuthConfig.getGithubPublicUrl().isBlank()) {
            ConsoleLogger.warn("[Auth2FA] auth.2fa.github.public_url is empty — using server-ip + port. "
                    + "On most hosts you MUST set the public URL (http://<ip-or-domain>:<port>).");
        }

        try {
            serverSocket = new ServerSocket(port);
            running.set(true);
            acceptThread = new Thread(this::acceptLoop, "UI-GithubAuth");
            acceptThread.setDaemon(true);
            acceptThread.start();
            ConsoleLogger.info("[Auth2FA] GitHub 2FA HTTP server listening on port " + port
                    + " (callback: " + getPublicBase() + "/callback)");
        } catch (Exception e) {
            ConsoleLogger.warn("[Auth2FA] GitHub 2FA HTTP server failed to bind port " + port
                    + ": " + e.getMessage());
        }
    }

    private void stop() {
        running.set(false);
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception ignored) {}
        serverSocket = null;
    }

    // =========================
    // ACCEPT LOOP
    // =========================
    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                Thread handler = new Thread(() -> handleConnection(socket), "UI-GithubAuth-Conn");
                handler.setDaemon(true);
                handler.start();
            } catch (Exception e) {
                if (running.get()) {
                    ConsoleLogger.warn("[Auth2FA] HTTP accept error: " + e.getMessage());
                }
            }
        }
    }

    private void handleConnection(Socket socket) {
        try {
            // Slow-loris protection: read headers for no longer than 10 seconds.
            socket.setSoTimeout(10_000);
        } catch (Exception ignored) {}
        try (Socket s = socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             OutputStream out = s.getOutputStream()) {

            String requestLine = in.readLine();
            if (requestLine == null) return;

            String[] parts = requestLine.split(" ");
            if (parts.length < 2 || !"GET".equalsIgnoreCase(parts[0])) {
                sendText(out, 405, "Method Not Allowed");
                return;
            }
            String rawPath = parts[1];

            // Read headers (until an empty line)
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {}

            String path = rawPath;
            String query = "";
            int qi = rawPath.indexOf('?');
            if (qi >= 0) {
                path = rawPath.substring(0, qi);
                query = rawPath.substring(qi + 1);
            }

            Map<String, String> params = parseQuery(query);

            switch (path) {
                case "/", "/auth" -> handleAuth(out, params);
                case "/callback" -> handleCallback(out, params);
                default -> sendText(out, 404, "Not Found");
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[Auth2FA] HTTP handler error: " + e.getMessage());
        }
    }

    // =========================
    // ENDPOINTS
    // =========================

    /** GET /auth?state=... → 302 redirect to GitHub authorize. */
    private void handleAuth(OutputStream out, Map<String, String> params) throws Exception {
        String state = params.get("state");
        UUID uuid = state == null ? null : Auth2FA.resolveState(state);

        if (uuid == null) {
            sendText(out, 400, "Invalid or expired authorization link. "
                    + "Please ask for a fresh link in the game chat (relog or /ui auth login).");
            return;
        }

        String callback = getPublicBase() + "/callback";
        String authorizeUrl = GITHUB_AUTHORIZE_URL
                + "?client_id=" + urlEncode(AuthConfig.getGithubClientId())
                + "&redirect_uri=" + urlEncode(callback)
                + "&scope=" + urlEncode("read:user")
                + "&state=" + urlEncode(state);

        ConsoleLogger.info("[Auth2FA] Redirecting player " + uuid + " to GitHub authorization...");
        sendRedirect(out, authorizeUrl);
    }

    /** GET /callback?code=...&state=... → code exchange, account verification, approval. */
    private void handleCallback(OutputStream out, Map<String, String> params) throws Exception {
        String code = params.get("code");
        String state = params.get("state");
        UUID uuid = state == null ? null : Auth2FA.resolveState(state);

        if (uuid == null) {
            sendText(out, 400, "Invalid or expired authorization session. "
                    + "Please click a fresh link in the game chat.");
            return;
        }
        if (code == null || code.isEmpty()) {
            sendText(out, 400, "GitHub did not return an authorization code. Try again.");
            return;
        }

        String expected = Auth2FA.getGithubUsername(uuid);
        String accessToken = exchangeCodeForToken(code);
        if (accessToken == null) {
            sendText(out, 502, "GitHub authorization failed (could not obtain an access token). Try again.");
            return;
        }
        String githubLogin = fetchGithubLogin(accessToken);
        if (githubLogin == null) {
            sendText(out, 502, "GitHub authorization failed (could not fetch your profile). Try again.");
            return;
        }

        if (expected == null || !expected.equalsIgnoreCase(githubLogin)) {
            ConsoleLogger.warn("[Auth2FA] GitHub account '" + githubLogin
                    + "' tried to authorize for " + uuid + " (expected '" + expected + "') — rejected.");
            sendText(out, 403, "Your GitHub account '" + githubLogin
                    + "' is NOT linked to this Minecraft account (linked: '"
                    + (expected == null ? "none" : expected) + "').");
            return;
        }

        // ✅ Approve and authenticate the player on the main thread
        Auth2FA.markApproved(uuid, githubLogin);
        ConsoleLogger.info("[Auth2FA] GitHub account '" + githubLogin + "' authorized player " + uuid);

        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            AuthAuthenticator auth = AuthAuthenticator.getInstance();
            if (auth != null) {
                auth.completeGithubAuth(uuid);
            }
        });

        sendText(out, 200, "<h2>✔ Authorization successful!</h2><p>Your GitHub account '"
                + githubLogin + "' is verified. You can return to the game now.</p>");
    }

    // =========================
    // GITHUB API
    // =========================

    /** POST code → access token (GitHub OAuth App flow). */
    private String exchangeCodeForToken(String code) {
        HttpURLConnection conn = null;
        try {
            String body = "client_id=" + urlEncode(AuthConfig.getGithubClientId())
                    + "&client_secret=" + urlEncode(AuthConfig.getGithubClientSecret())
                    + "&code=" + urlEncode(code)
                    + "&redirect_uri=" + urlEncode(getPublicBase() + "/callback");

            conn = (HttpURLConnection) new URI(GITHUB_TOKEN_URL).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            return extractJsonValue(readResponse(conn), "access_token");
        } catch (Exception e) {
            ConsoleLogger.warn("[Auth2FA] Token exchange failed: " + e.getMessage());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** GET /user → GitHub login (username) of the authorized account. */
    private String fetchGithubLogin(String accessToken) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URI(GITHUB_API_USER_URL).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            return extractJsonValue(readResponse(conn), "login");
        } catch (Exception e) {
            ConsoleLogger.warn("[Auth2FA] GitHub user fetch failed: " + e.getMessage());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String readResponse(HttpURLConnection conn) {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = conn.getInputStream()) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = is.read(buf)) != -1) {
                sb.append(new String(buf, 0, len, StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {}
        try (InputStream es = conn.getErrorStream()) {
            if (es != null) {
                byte[] buf = new byte[1024];
                int len;
                while ((len = es.read(buf)) != -1) {
                    sb.append(new String(buf, 0, len, StandardCharsets.UTF_8));
                }
            }
        } catch (Exception ignored) {}
        return sb.toString();
    }

    /** Extracts a string JSON field value (without JSON libraries). */
    private String extractJsonValue(String json, String key) {
        if (json == null) return null;
        Matcher m = JSON_STRING_PATTERN.matcher(json);
        while (m.find()) {
            if (m.group(1).equals(key)) return m.group(2);
        }
        return null;
    }

    // =========================
    // HTTP HELPERS
    // =========================

    /** Base public URL: public_url from config, otherwise server-ip:port. */
    public static String getPublicBase() {
        String pub = AuthConfig.getGithubPublicUrl();
        if (pub != null && !pub.isBlank()) {
            return pub.endsWith("/") ? pub.substring(0, pub.length() - 1) : pub;
        }
        String ip = "";
        try {
            ip = Bukkit.getServer().getIp();
        } catch (Exception ignored) {}
        if (ip == null || ip.isBlank()) ip = "localhost";
        return "http://" + ip + ":" + AuthConfig.getGithubPort();
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) return params;
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) continue;
            String[] kv = pair.split("=", 2);
            String k = urlDecode(kv[0]);
            String v = kv.length > 1 ? urlDecode(kv[1]) : "";
            params.put(k, v);
        }
        return params;
    }

    private String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    private String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    private void sendRedirect(OutputStream out, String location) throws Exception {
        String body = "<html><body><a href=\"" + location + "\">Continue to GitHub</a></body></html>";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        out.write(("HTTP/1.1 302 Found\r\n"
                + "Location: " + location + "\r\n"
                + "Content-Type: text/html; charset=utf-8\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Connection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.flush();
    }

    private void sendText(OutputStream out, int statusCode, String text) throws Exception {
        String reason = statusCode == 200 ? "OK"
                : statusCode == 302 ? "Found"
                : statusCode == 400 ? "Bad Request"
                : statusCode == 403 ? "Forbidden"
                : statusCode == 404 ? "Not Found"
                : statusCode == 405 ? "Method Not Allowed"
                : "Error";
        String body = "<html><body style=\"font-family:monospace;margin:40px\"><h2>" + statusCode
                + " " + reason + "</h2><p>" + text + "</p></body></html>";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        out.write(("HTTP/1.1 " + statusCode + " " + reason + "\r\n"
                + "Content-Type: text/html; charset=utf-8\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Connection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.flush();
    }
}
