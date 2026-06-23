package com.mcplugin.mechanics.security.auth;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.config.MessagesManager;

/**
 * Чтение конфигурации системы авторизации.
 * Все настройки auth.* из config.yml вынесены в отдельный класс
 * для централизованного доступа и простоты тестирования.
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
        return getInt("auth.min_password_length", 8);
    }

    public static int getMaxPasswordLength() {
        return getInt("auth.max_password_length", 32);
    }

    public static int getLoginTimeoutSeconds() {
        return getInt("auth.login_timeout_seconds", 60);
    }

    public static int getMaxWrongAttempts() {
        return getInt("auth.max_wrong_attempts", 5);
    }

    public static int getRequestCooldownSeconds() {
        return getInt("auth.request_cooldown_seconds", 5);
    }

    public static long getRequestCooldownMs() {
        return getRequestCooldownSeconds() * 1000L;
    }

    public static int getMaxAccountsPerIp() {
        return Math.max(getInt("auth.max_accounts_per_ip", 3), 0);
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
        // stub — сообщения теперь в MessagesManager, этот метод для совместимости
    }
}
