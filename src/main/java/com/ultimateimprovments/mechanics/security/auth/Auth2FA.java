package com.ultimateimprovments.mechanics.security.auth;

import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.util.ConsoleLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 2FA system — login confirmation via GitHub OAuth.
 * <p>
 * Flow:
 * 1. The player links GitHub: /ui auth 2fa setup &lt;github_username&gt;
 * 2. On login (after the password): the plugin starts a challenge and writes
 *    a clickable link to {@code <public_url>/auth?state=...} into the chat
 * 3. The player opens the link → GitHub OAuth → redirect to /callback
 * 4. The plugin checks the GitHub account against the one linked to the player's UUID
 * 5. On a match — login is allowed; the session lives for 1 hour (auth.session_duration_minutes)
 * <p>
 * Requires an OAuth App at https://github.com/settings/developers and an open port
 * (auth.2fa.github.port) + public_url in the config.
 */
public class Auth2FA {

    private static Auth2FA instance;
    private static boolean tableChecked = false;

    /** Maximum confirmation wait time (5 minutes). */
    private static final long CHALLENGE_TIMEOUT_MS = 5 * 60_000L;

    /** uuid → pending confirmation. */
    private final Map<UUID, PendingAuth> pendingAuths = new ConcurrentHashMap<>();
    /** state-token → uuid (one-time binding for the OAuth callback). */
    private final Map<String, UUID> stateToUuid = new ConcurrentHashMap<>();

    // =========================
    // INIT
    // =========================
    public static void init() {
        instance = new Auth2FA();
        initTable();
        ConsoleLogger.info("[Auth2FA] Initialized (GitHub OAuth).");

        if (AuthConfig.isGithub2FAEnabled()) {
            GithubAuthServer.init();
        } else {
            ConsoleLogger.info("[Auth2FA] GitHub 2FA is disabled in config.yml (auth.2fa.github.enabled: false).");
        }
    }

    public static Auth2FA getInstance() {
        return instance;
    }

    // =========================
    // DB TABLE
    // =========================
    private static void initTable() {
        if (tableChecked) return;
        try (Connection con = DatabaseManager.getConnection();
             var st = con.createStatement()) {

            // One-time migration: the old telegram table (telegram_chat_id) is no
            // longer used — drop it only if it still has the old schema.
            try (ResultSet rs = st.executeQuery("PRAGMA table_info(auth_2fa)")) {
                boolean isLegacyTelegram = false;
                while (rs.next()) {
                    if ("telegram_chat_id".equals(rs.getString("name"))) {
                        isLegacyTelegram = true;
                        break;
                    }
                }
                if (isLegacyTelegram) {
                    st.execute("DROP TABLE auth_2fa");
                    ConsoleLogger.info("[Auth2FA] Dropped legacy Telegram 2FA table (migrating to GitHub OAuth).");
                }
            }

            st.execute("""
                CREATE TABLE IF NOT EXISTS auth_2fa (
                    uuid TEXT PRIMARY KEY,
                    github_username TEXT NOT NULL,
                    enabled INTEGER DEFAULT 1
                );
            """);

            tableChecked = true;
        } catch (Exception e) {
            ConsoleLogger.warn("[Auth2FA] DB init failed: " + e.getMessage());
        }
    }

    // =========================
    // GET/SET 2FA SETTINGS
    // =========================
    public static boolean isEnabled(UUID uuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT enabled FROM auth_2fa WHERE uuid = ?")) {

            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt("enabled") == 1;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /** The player's linked GitHub username (or null). */
    public static String getGithubUsername(UUID uuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT github_username FROM auth_2fa WHERE uuid = ?")) {

            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("github_username");
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[Auth2FA] Get GitHub username failed: " + e.getMessage());
        }
        return null;
    }

    public static void setEnabled(UUID uuid, String githubUsername, boolean enabled) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT OR REPLACE INTO auth_2fa (uuid, github_username, enabled) VALUES (?, ?, ?)")) {

            ps.setString(1, uuid.toString());
            ps.setString(2, githubUsername);
            ps.setInt(3, enabled ? 1 : 0);
            ps.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.warn("[Auth2FA] Save 2FA settings failed: " + e.getMessage());
        }
    }

    public static void remove(UUID uuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM auth_2fa WHERE uuid = ?")) {

            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.warn("[Auth2FA] Remove 2FA failed: " + e.getMessage());
        }
        if (instance != null) {
            instance.clearPending(uuid);
        }
    }

    // =========================
    // START CHALLENGE
    // =========================

    /**
     * Starts a GitHub challenge: generates a one-time state token.
     *
     * @return the state token (used as requestId), or null if 2FA is not set up
     *         or the HTTP server is not running
     */
    public String sendConfirmation(UUID uuid, String playerName) {
        if (!AuthConfig.isGithub2FAEnabled()) return null;
        if (!GithubAuthServer.isRunning()) return null;

        clearPending(uuid);

        String state = UUID.randomUUID().toString().replace("-", "");
        pendingAuths.put(uuid, new PendingAuth(state, System.currentTimeMillis()));
        stateToUuid.put(state, uuid);

        ConsoleLogger.info("[Auth2FA] Challenge started for " + playerName
                + " (github: " + getGithubUsername(uuid) + ", state: " + state + ")");
        return state;
    }

    /**
     * The clickable link for the player: {@code <public_url>/auth?state=...}
     * or null if the challenge is not running.
     */
    public String getAuthUrl(UUID uuid) {
        PendingAuth pending = pendingAuths.get(uuid);
        if (pending == null) return null;
        return GithubAuthServer.getPublicBase() + "/auth?state=" + pending.state;
    }

    /** Reverse binding state → uuid (called from the HTTP callback). */
    public static UUID resolveState(String state) {
        if (instance == null || state == null) return null;
        return instance.stateToUuid.get(state);
    }

    /** Marks the challenge as approved (called from the HTTP callback). */
    public static void markApproved(UUID uuid, String githubUsername) {
        if (instance == null) return;
        PendingAuth pending = instance.pendingAuths.get(uuid);
        if (pending == null) return;
        pending.approved = true;
        pending.githubLogin = githubUsername;
    }

    // =========================
    // CHECK CONFIRMATION STATUS (POLLING)
    // =========================
    public String checkConfirmation(UUID uuid) {
        PendingAuth pending = pendingAuths.get(uuid);
        if (pending == null) return "not_found";

        if (System.currentTimeMillis() - pending.createdAt > CHALLENGE_TIMEOUT_MS) {
            removePending(uuid);
            return "timeout";
        }

        if (pending.approved) {
            removePending(uuid);
            return "approved";
        }

        return "pending";
    }

    // =========================
    // UTILITY
    // =========================
    public boolean hasPendingConfirmation(UUID uuid) {
        PendingAuth pending = pendingAuths.get(uuid);
        if (pending == null) return false;
        if (System.currentTimeMillis() - pending.createdAt > CHALLENGE_TIMEOUT_MS) {
            removePending(uuid);
            return false;
        }
        return true;
    }

    public void clearPending(UUID uuid) {
        PendingAuth pending = pendingAuths.remove(uuid);
        if (pending != null) {
            stateToUuid.remove(pending.state);
        }
    }

    private void removePending(UUID uuid) {
        clearPending(uuid);
    }

    // =========================
    // INNER CLASS
    // =========================
    private static class PendingAuth {
        final String state;
        final long createdAt;
        volatile boolean approved;
        String githubLogin;

        PendingAuth(String state, long createdAt) {
            this.state = state;
            this.createdAt = createdAt;
        }
    }
}
