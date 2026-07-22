package com.ultimateimprovments.module;

import com.ultimateimprovments.util.ConsoleLogger;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль проверки — проверяет тип ядра (рекомендуется Leaf),
 * наличие LuckPerms и совместимость API-версии с сервером.
 * <p>
 * Версия плагина теперь универсальный идентификатор (формат: major.minor.commits),
 * не привязанный к Paper/Leaf версии. Используется чекером обновлений.
 * <p>
 * Неessential — если проверка не удалась, плагин всё равно работает.
 */
public class VersionCheckModule extends PluginModule {

    /** Ожидаемое имя серверного ядра. */
    private static final String EXPECTED_SERVER_NAME = "Leaf";

    public VersionCheckModule() {
        super("VersionCheck", "infrastructure/core", false);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        String pluginVersion = plugin.getDescription().getVersion();
        String apiVersion = plugin.getDescription().getAPIVersion();
        String serverVersion = Bukkit.getVersion();
        String bukkitVersion = Bukkit.getBukkitVersion();
        String serverName = Bukkit.getServer().getName();

        // =========================
        // ВЫВОД ИНФОРМАЦИИ О ВЕРСИЯХ
        // =========================
        ConsoleLogger.info("");
        ConsoleLogger.info("╔═══════════════════════════════════════╗");
        ConsoleLogger.info("║         Version Information           ║");
        ConsoleLogger.info("╠═══════════════════════════════════════╣");
        ConsoleLogger.info("║ Plugin ver: " + padRight(pluginVersion, 35) + "║");
        if (apiVersion != null) {
            ConsoleLogger.info("║ API ver:    " + padRight(apiVersion, 35) + "║");
        }
        ConsoleLogger.info("║ Server:     " + padRight(serverVersion, 35) + "║");
        ConsoleLogger.info("║ Bukkit:     " + padRight(bukkitVersion, 35) + "║");
        ConsoleLogger.info("║ ServerName: " + padRight(serverName, 35) + "║");
        ConsoleLogger.info("╚═══════════════════════════════════════╝");
        ConsoleLogger.info("");

        // =========================
        // ПРОВЕРКА ТИПА ЯДРА (Leaf или нет)
        // =========================
        checkServerSoftware(plugin, serverName, serverVersion);

        // =========================
        // ПРОВЕРКА СОВМЕСТИМОСТИ API-ВЕРСИИ С СЕРВЕРОМ
        // =========================
        checkApiCompatibility(plugin, apiVersion, serverVersion, bukkitVersion);

        // =========================
        // ПРОВЕРКА НАЛИЧИЯ LUCKPERMS
        // =========================
        checkLuckPerms(plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        // Nothing to clean up
    }

    // =========================
    // ПРОВЕРКА ЯДРА
    // =========================

    private void checkServerSoftware(JavaPlugin plugin, String serverName, String serverVersion) {
        if (EXPECTED_SERVER_NAME.equalsIgnoreCase(serverName)) {
            ConsoleLogger.info("[VersionCheck] \u2713 Server software: " + serverName + " (recommended)");
            return;
        }

        // Определяем, является ли сервер Paper-совместимым
        boolean isPaper = false;
        try {
            Class.forName("io.papermc.paper.configuration.Configuration");
            isPaper = true;
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("com.destroystokyo.paper.PaperConfig");
                isPaper = true;
            } catch (ClassNotFoundException ignored) {}
        }

        ConsoleLogger.warn("");
        ConsoleLogger.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        ConsoleLogger.warn("!  SERVER SOFTWARE NOT RECOMMENDED                  !");
        ConsoleLogger.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        ConsoleLogger.warn("!  Detected:        " + padRight(serverName + " (" + getServerShortVersion(serverVersion) + ")", 33) + "!");
        ConsoleLogger.warn("!  Recommended:     " + padRight(EXPECTED_SERVER_NAME, 33) + "!");
        ConsoleLogger.warn("!                                                   !");
        ConsoleLogger.warn("!  This plugin is designed and tested for Leaf.      !");
        if (isPaper) {
            ConsoleLogger.warn("!  While Paper is compatible, some features may     !");
            ConsoleLogger.warn("!  not work as expected.                             !");
        } else {
            ConsoleLogger.warn("!  Your server software may not be compatible!       !");
            ConsoleLogger.warn("!  Features may be broken or missing entirely.       !");
        }
        ConsoleLogger.warn("!                                                   !");
        ConsoleLogger.warn("!  Download Leaf: https://github.com/Winds-Studio/Leaf!");
        ConsoleLogger.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        ConsoleLogger.warn("");
    }

    // =========================
    // ПРОВЕРКА СОВМЕСТИМОСТИ API-ВЕРСИИ
    // =========================

    /**
     * Сравнивает api-version из plugin.yml с версией сервера.
     * api-version = "26.2" — Paper internal version (26.x = MC 1.21.x).
     * <p>
     * Версия плагина (plugin_version) теперь универсальный идентификатор
     * формата major.minor.commits и НЕ используется для проверки совместимости.
     */
    private void checkApiCompatibility(JavaPlugin plugin, String apiVersion,
                                        String serverVersion, String bukkitVersion) {
        if (apiVersion == null) return;

        // Извлекаем Paper-версию сервера из Bukkit.getVersion()
        // "git-Paper-26.2 (MC: 1.21.5)" → "26.2"
        String serverPaperVer = extractServerVersionNumber(serverVersion);

        // Guard: если версия сервера не парсится как число.число — не можем сравнить, пропускаем
        if (!isNumericVersion(serverPaperVer)) {
            ConsoleLogger.info("[VersionCheck] Cannot parse server version ("
                    + serverPaperVer + ") — skipping API compatibility check.");
            return;
        }

        String serverMajorMinor = getMajorMinor(serverPaperVer);
        String apiMajorMinor = getMajorMinor(apiVersion);

        if (serverMajorMinor.equals(apiMajorMinor)) {
            ConsoleLogger.info("[VersionCheck] \u2713 API version " + apiVersion
                    + " matches server (" + serverPaperVer + ")");
            return;
        }

        // Проверяем BukkitVersion как fallback
        String bukkitMajorMinor = getMajorMinor(bukkitVersion.split("-")[0]);
        if (bukkitMajorMinor.equals(apiMajorMinor)) {
            ConsoleLogger.info("[VersionCheck] \u2713 API version " + apiVersion
                    + " matches Bukkit version (" + bukkitVersion + ")");
            return;
        }

        // Несовпадение API-версии
        String mcVersion = extractMcVersion(serverVersion);

        ConsoleLogger.warn("");
        ConsoleLogger.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        ConsoleLogger.warn("!  API VERSION MISMATCH!                                        !");
        ConsoleLogger.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        ConsoleLogger.warn("!                                                                 !");
        ConsoleLogger.warn("!  Plugin API:        " + padRight(apiVersion, 35) + "!");
        ConsoleLogger.warn("!  Server version:    " + padRight(serverPaperVer, 35) + "!");
        if (mcVersion != null) {
            ConsoleLogger.warn("!  MC version:        " + padRight(mcVersion, 35) + "!");
        }
        ConsoleLogger.warn("!                                                                 !");
        ConsoleLogger.warn("!  The plugin may not work correctly!                             !");
        ConsoleLogger.warn("!  Update your server or plugin to matching versions.             !");
        ConsoleLogger.warn("!                                                                 !");
        ConsoleLogger.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        ConsoleLogger.warn("");
    }

    // =========================
    // ПРОВЕРКА LUCKPERMS
    // =========================

    private void checkLuckPerms(JavaPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            ConsoleLogger.info("[VersionCheck] \u2713 LuckPerms detected — permission system ready.");
            return;
        }

        ConsoleLogger.warn("");
        ConsoleLogger.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        ConsoleLogger.warn("!  LUCKPERMS NOT FOUND!                                       !");
        ConsoleLogger.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        ConsoleLogger.warn("!                                                                 !");
        ConsoleLogger.warn("!  This plugin uses LuckPerms for full permission management.    !");
        ConsoleLogger.warn("!  Without LuckPerms, many permission-based features will       !");
        ConsoleLogger.warn("!  NOT work correctly:                                          !");
        ConsoleLogger.warn("!    - /mp sethome, /mp home, /mp delhome                       !");
        ConsoleLogger.warn("!    - /mp auth (forcelogin, resetauth, chgpass, delsession)    !");
        ConsoleLogger.warn("!    - /mp power (off, reboot)                                  !");
        ConsoleLogger.warn("!    - /mp structures (dfc, magnet)                             !");
        ConsoleLogger.warn("!    - And many other commands                                  !");
        ConsoleLogger.warn("!                                                                 !");
        ConsoleLogger.warn("!  Download LuckPerms: https://luckperms.net/download           !");
        ConsoleLogger.warn("!  Or place LuckPerms.jar in your plugins/ folder               !");
        ConsoleLogger.warn("!                                                                 !");
        ConsoleLogger.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        ConsoleLogger.warn("");
    }

    // =========================
    // HELPERS
    // =========================

    private String padRight(String s, int length) {
        if (s == null) s = "null";
        if (s.length() >= length) return s.substring(0, length);
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < length) sb.append(' ');
        return sb.toString();
    }

    /** Извлекает major.minor из версии (1.21.4 → 1.21, 26.2 → 26.2). */
    private String getMajorMinor(String version) {
        if (version == null) return "";
        String[] parts = version.split("\\.");
        if (parts.length >= 2) {
            return parts[0] + "." + parts[1];
        }
        return parts[0];
    }

    /** Проверяет, выглядит ли строка как числовая версия (например "26.2"). */
    private boolean isNumericVersion(String version) {
        if (version == null || version.isEmpty()) return false;
        // Должна начинаться с цифры и содержать точку
        return version.matches("\\d+\\.\\d+.*");
    }

    /** Извлекает MC-версию из строки Bukkit.getVersion() вида "git-Paper-26.2 (MC: 1.21.5)". */
    private String extractMcVersion(String version) {
        if (version == null) return null;
        int start = version.indexOf("(MC:");
        if (start == -1) return null;
        int end = version.indexOf(")", start);
        if (end == -1) return null;
        String mcPart = version.substring(start + 4, end).trim();
        return mcPart.isEmpty() ? null : mcPart;
    }

    /** Извлекает Paper/Leaf-версию из полной строки Bukkit.getVersion() ("git-Paper-26.2.build.+..." → "26.2.build.+"). */
    private String extractServerVersionNumber(String version) {
        if (version == null) return "?";
        // "git-Paper-26.2.build.+ (MC: 1.21.5)" → берём часть до пробела
        String firstPart = version.split(" ")[0]; // "git-Paper-26.2.build.+"
        // Последний сегмент после последнего '-' — это версия
        int lastDash = firstPart.lastIndexOf("-");
        if (lastDash >= 0 && lastDash < firstPart.length() - 1) {
            return firstPart.substring(lastDash + 1);
        }
        return firstPart;
    }

    /** Извлекает короткую версию из полной строки Bukkit.getVersion(). */
    private String getServerShortVersion(String version) {
        // "git-Leaf-123 (MC: 1.21.4)" → "MC: 1.21.4"
        if (version.contains("(MC:") || version.contains("(MC: ")) {
            int start = version.indexOf("(MC:");
            int end = version.indexOf(")", start);
            if (end > start) {
                return version.substring(start + 1, end).trim();
            }
        }
        // Fallback: просто берём последние 10 символов
        if (version.length() > 20) {
            return "..." + version.substring(version.length() - 15);
        }
        return version;
    }
}
