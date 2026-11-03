package com.ultimateimprovments.mechanics.security.auth;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.config.MessagesManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads the authentication system configuration.
 * All auth.* settings from config.yml are extracted into a separate class
 * for centralized access and easy testing.
 */
public class AuthConfig {

    private AuthConfig() {}

    // =========================
    // BOOLEAN CHECKS
    // =========================
    public static boolean isEnabled() {
        try {
            return Main.getInstance() != null
                    && Main.getInstance().getConfig() != null
                    && Main.getInstance().getConfig().getBoolean("auth.enabled", true);
        } catch (Exception e) {
            return true;
        }
    }

    public static boolean isIpCheckEnabled() {
        return getBool("auth.check_ip.enabled", true);
    }

    public static boolean isDupNameCheckEnabled() {
        return getBool("auth.check_duplicate_name.enabled", true);
    }

    // =========================
    // INT GETTERS
    // =========================
    public static int getSessionDurationMinutes() {
        return getInt("auth.session_duration_minutes", 60);
    }

    public static long getSessionDurationMs() {
        return getSessionDurationMinutes() * 60000L;
    }

    public static int getMinPasswordLength() {
        return passwordInt("min_length", "auth.min_password_length", 8);
    }

    public static int getMaxPasswordLength() {
        return passwordInt("max_length", "auth.max_password_length", 32);
    }

    public static int getLoginTimeoutSeconds() {
        return getInt("auth.login_timeout_seconds", 60);
    }

    public static int getMaxWrongAttempts() {
        return getInt("auth.max_wrong_attempts", 5);
    }

    public static int getRequestCooldownSeconds() {
        return getInt("auth.request_cooldown_seconds", 15);
    }

    public static long getRequestCooldownMs() {
        return getRequestCooldownSeconds() * 1000L;
    }

    public static int getMaxAccountsPerIp() {
        return Math.max(getInt("auth.max_accounts_per_ip", 3), 0);
    }

    // =========================
    // PASSWORD REQUIREMENTS ([auth.password])
    // =========================

    /** At least one digit (0-9) is required. */
    public static boolean isRequireDigit() {
        return getBool("auth.password.require_digit", true);
    }

    /** At least one lowercase AND one uppercase English letter is required. */
    public static boolean isRequireMixedCase() {
        return getBool("auth.password.require_case", true);
    }

    /** At least one special character (!, @, #, $, %, &, *, ...) is required. */
    public static boolean isRequireSpecialChar() {
        return getBool("auth.password.require_special", true);
    }

    /** Passwords from {@link #getCommonPasswords()} are rejected. */
    public static boolean isBlockCommonPasswords() {
        return getBool("auth.password.block_common", true);
    }

    /**
     * Lower-cased list of forbidden passwords from {@code auth.password.common_passwords}
     * (a TOML array). Falls back to {@link #DEFAULT_COMMON_PASSWORDS} when the list is
     * missing, empty or unreadable.
     */
    public static List<String> getCommonPasswords() {
        try {
            List<String> configured = Main.getInstance().getConfig()
                    .getStringList("auth.password.common_passwords");
            if (configured != null && !configured.isEmpty()) {
                List<String> normalized = new ArrayList<>(configured.size());
                for (String raw : configured) {
                    if (raw == null) continue;
                    String value = raw.trim().toLowerCase(Locale.ROOT);
                    if (!value.isEmpty()) normalized.add(value);
                }
                if (!normalized.isEmpty()) return normalized;
            }
        } catch (Exception e) {
            // fall through to the built-in list
        }
        return DEFAULT_COMMON_PASSWORDS;
    }

    private static final List<String> DEFAULT_COMMON_PASSWORDS = List.of(
            "123456", "12345678", "123456789", "1234567890", "111111",
            "123123", "password", "passw0rd", "qwerty", "qwerty123",
            "qwertyuiop", "abc123", "letmein", "welcome", "admin",
            "root", "iloveyou", "monkey", "dragon", "football",
            "master", "sunshine", "princess", "minecraft", "minecraft123");

    /**
     * Reads a length from {@code auth.password.<key>}, falling back to the legacy
     * {@code auth.min_password_length}/{@code auth.max_password_length} keys so old
     * configs keep working without manual migration.
     */
    private static int passwordInt(String key, String legacyKey, int def) {
        try {
            if (Main.getInstance().getConfig().isSet("auth.password." + key)) {
                return getInt("auth.password." + key, def);
            }
        } catch (Exception ignored) {
            // fall through to the legacy key
        }
        return getInt(legacyKey, def);
    }

    // =========================
    // GITHUB 2FA
    // =========================
    public static boolean isGithub2FAEnabled() {
        return getBool("auth.twofa.github.enabled", false);
    }

    public static String getGithubClientId() {
        return getString("auth.twofa.github.client_id", "");
    }

    public static String getGithubClientSecret() {
        return getString("auth.twofa.github.client_secret", "");
    }

    public static int getGithubPort() {
        return Math.max(getInt("auth.twofa.github.port", 8080), 1);
    }

    public static String getGithubPublicUrl() {
        return getString("auth.twofa.github.public_url", "");
    }

    // =========================
    // MESSAGE GETTERS
    // =========================
    public static String getMessage(String path, String def) {
        return MessagesManager.getString("auth.messages." + path, def);
    }

    // =========================
    // RAW GETTERS
    // =========================
    private static int getInt(String path, int def) {
        try {
            return Main.getInstance().getConfig().getInt(path, def);
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean getBool(String path, boolean def) {
        try {
            return Main.getInstance().getConfig().getBoolean(path, def);
        } catch (Exception e) {
            return def;
        }
    }

    private static String getString(String path, String def) {
        try {
            return Main.getInstance().getConfig().getString(path, def);
        } catch (Exception e) {
            return def;
        }
    }

    public static void reloadMessages() {
        // stub — messages now live in MessagesManager, this method is for compatibility
    }
}
