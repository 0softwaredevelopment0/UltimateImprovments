package com.mcplugin.module;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль проверки — сравнивает версию плагина с версией сервера,
 * а также проверяет тип ядра (рекомендуется Leaf).
 * <p>
 * Неessential — если проверка не удалась, плагин всё равно работает.
 */
public class VersionCheckModule extends PluginModule {

    /** Ожидаемое имя серверного ядра. */
    private static final String EXPECTED_SERVER_NAME = "Leaf";

    public VersionCheckModule() {
        super("VersionCheck", false);
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
        plugin.getLogger().info("");
        plugin.getLogger().info("╔═══════════════════════════════════════╗");
        plugin.getLogger().info("║         Version Information           ║");
        plugin.getLogger().info("╠═══════════════════════════════════════╣");
        plugin.getLogger().info("║ Plugin ver: " + padRight(pluginVersion, 35) + "║");
        if (apiVersion != null) {
            plugin.getLogger().info("║ API ver:    " + padRight(apiVersion, 35) + "║");
        }
        plugin.getLogger().info("║ Server:     " + padRight(serverVersion, 35) + "║");
        plugin.getLogger().info("║ Bukkit:     " + padRight(bukkitVersion, 35) + "║");
        plugin.getLogger().info("║ ServerName: " + padRight(serverName, 35) + "║");
        plugin.getLogger().info("╚═══════════════════════════════════════╝");
        plugin.getLogger().info("");

        // =========================
        // ПРОВЕРКА ТИПА ЯДРА (Leaf или нет)
        // =========================
        checkServerSoftware(plugin, serverName, serverVersion);

        // =========================
        // ПРОВЕРКА ВЕРСИИ MINECRAFT
        // =========================
        checkMinecraftVersion(plugin, pluginVersion, bukkitVersion);

        // =========================
        // ПРОВЕРКА НАЛИЧИЯ LUCKPERMS
        // =========================
        checkLuckPerms(plugin);

        // =========================
        // ПРОВЕРКА СОВМЕСТИМОСТИ ВЕРСИИ ПЛАГИНА
        // =========================
        checkPluginVersionCompatibility(plugin, pluginVersion, serverVersion);
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
            plugin.getLogger().info("[VersionCheck] \u2713 Server software: " + serverName + " (recommended)");
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

        plugin.getLogger().warning("");
        plugin.getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        plugin.getLogger().warning("!  SERVER SOFTWARE NOT RECOMMENDED                  !");
        plugin.getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        plugin.getLogger().warning("!  Detected:        " + padRight(serverName + " (" + getServerShortVersion(serverVersion) + ")", 33) + "!");
        plugin.getLogger().warning("!  Recommended:     " + padRight(EXPECTED_SERVER_NAME, 33) + "!");
        plugin.getLogger().warning("!                                                   !");
        plugin.getLogger().warning("!  This plugin is designed and tested for Leaf.      !");
        if (isPaper) {
            plugin.getLogger().warning("!  While Paper is compatible, some features may     !");
            plugin.getLogger().warning("!  not work as expected.                             !");
        } else {
            plugin.getLogger().warning("!  Your server software may not be compatible!       !");
            plugin.getLogger().warning("!  Features may be broken or missing entirely.       !");
        }
        plugin.getLogger().warning("!                                                   !");
        plugin.getLogger().warning("!  Download Leaf: https://github.com/Winds-Studio/Leaf!");
        plugin.getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        plugin.getLogger().warning("");
    }

    // =========================
    // ПРОВЕРКА ВЕРСИИ MINECRAFT
    // =========================

    private void checkMinecraftVersion(JavaPlugin plugin, String pluginVersion, String bukkitVersion) {
        // Пытаемся извлечь MC версию из Bukkit.getVersion()
        // "git-Paper-26.2 (MC: 1.21.5)" → "1.21.5"
        String serverFullVersion = Bukkit.getVersion();
        String mcVersion = extractMcVersion(serverFullVersion);

        if (mcVersion == null) {
            // Fallback: Bukkit.getBukkitVersion() возвращает "1.21.4-R0.1-SNAPSHOT"
            // (на Leaf может вернуть "26.2-R0.1-SNAPSHOT")
            mcVersion = bukkitVersion.split("-")[0];
        }

        // Убираем патч-версию для сравнения (1.21.4 → 1.21)
        String mcMajorMinor = getMajorMinor(mcVersion);

        // Парсим версию плагина
        // plugin.yml version = "26.2" — это Paper internal version
        // Paper 26.x → Minecraft 1.21.x
        String pluginMajorStr = pluginVersion.contains(".")
                ? pluginVersion.split("\\.")[0]
                : pluginVersion;

        try {
            int pluginMajor = Integer.parseInt(pluginMajorStr);

            if (pluginMajor >= 20) {
                // Paper internal version: 26 → MC 1.21
                int expectedMcMinor = pluginMajor - 5; // 26 - 5 = 21 → 1.21
                String expectedPrefix = "1." + expectedMcMinor;

                // Определяем, не является ли mcVersion тоже Paper internal версией
                // (например, на Leaf Bukkit.getBukkitVersion() может вернуть "26.2-R0.1-SNAPSHOT")
                String mcMajorStr = mcVersion.contains(".")
                        ? mcVersion.split("\\.")[0]
                        : mcVersion;
                boolean mcIsPaperInternal = false;
                try {
                    int mcMajor = Integer.parseInt(mcMajorStr);
                    mcIsPaperInternal = mcMajor >= 20;
                } catch (NumberFormatException ignored) {}

                boolean mismatch;
                if (mcIsPaperInternal) {
                    // Обе версии — Paper internal: сравниваем напрямую
                    String pluginMajorMinor = getMajorMinor(pluginVersion);
                    mismatch = !mcMajorMinor.equals(pluginMajorMinor);
                } else {
                    // mcVersion — MC версия (1.21.x): сравниваем через конвертацию
                    mismatch = !mcMajorMinor.equals(expectedPrefix);
                }

                if (mismatch) {
                    String serverPaperVer = extractServerVersionNumber(serverFullVersion);

                    plugin.getLogger().warning("");
                    plugin.getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                    plugin.getLogger().warning("!  VERSION MISMATCH DETECTED!                                    !");
                    plugin.getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                    plugin.getLogger().warning("!                                                                 !");
                    plugin.getLogger().warning("!  Plugin built for:   " + padRight(pluginVersion, 35) + "!");
                    plugin.getLogger().warning("!  Server running:     " + padRight(serverPaperVer, 35) + "!");
                    plugin.getLogger().warning("!  MC version:         " + padRight(mcVersion, 35) + "!");
                    plugin.getLogger().warning("!                                                                 !");
                    plugin.getLogger().warning("!  The plugin may not work correctly!                             !");
                    plugin.getLogger().warning("!  Update your server or plugin to matching versions.             !");
                    plugin.getLogger().warning("!                                                                 !");
                    plugin.getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                    plugin.getLogger().warning("");
                }
            } else {
                // Плагин использует Minecraft-версию (1.21.x)
                String pluginMcVersion = getMajorMinor(pluginVersion);
                if (!mcMajorMinor.equals(pluginMcVersion)) {
                    plugin.getLogger().warning("[VersionCheck] \u26A0 Plugin version " + pluginVersion
                            + " may not match server version " + mcVersion);
                }
            }
        } catch (NumberFormatException e) {
            // Версия не начинается с числа — не можем сравнить
            plugin.getLogger().fine("[VersionCheck] Cannot parse plugin version: " + pluginVersion);
        }
    }

    // =========================
    // ПРОВЕРКА LUCKPERMS
    // =========================

    private void checkLuckPerms(JavaPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            plugin.getLogger().info("[VersionCheck] \u2713 LuckPerms detected — permission system ready.");
            return;
        }

        plugin.getLogger().warning("");
        plugin.getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        plugin.getLogger().warning("!  LUCKPERMS NOT FOUND!                                       !");
        plugin.getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        plugin.getLogger().warning("!                                                                 !");
        plugin.getLogger().warning("!  This plugin uses LuckPerms for full permission management.    !");
        plugin.getLogger().warning("!  Without LuckPerms, many permission-based features will       !");
        plugin.getLogger().warning("!  NOT work correctly:                                          !");
        plugin.getLogger().warning("!    - /mp sethome, /mp home, /mp delhome                       !");
        plugin.getLogger().warning("!    - /mp auth (forcelogin, resetauth, chgpass, delsession)    !");
        plugin.getLogger().warning("!    - /mp power (off, reboot)                                  !");
        plugin.getLogger().warning("!    - /mp structures (dfc, magnet)                             !");
        plugin.getLogger().warning("!    - And many other commands                                  !");
        plugin.getLogger().warning("!                                                                 !");
        plugin.getLogger().warning("!  Download LuckPerms: https://luckperms.net/download           !");
        plugin.getLogger().warning("!  Or place LuckPerms.jar in your plugins/ folder               !");
        plugin.getLogger().warning("!                                                                 !");
        plugin.getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        plugin.getLogger().warning("");
    }

    // =========================
    // ПРОВЕРКА СОВМЕСТИМОСТИ ВЕРСИИ ПЛАГИНА (contains)
    // =========================

    private void checkPluginVersionCompatibility(JavaPlugin plugin, String pluginVersion, String serverVersion) {
        // Проверяем, содержится ли версия плагина в строке версии сервера
        // Например: плагин 26.2, сервер "git-Paper-26.2 (MC: 1.21.5)" — совпадает
        if (serverVersion.contains(pluginVersion)) {
            plugin.getLogger().info("[VersionCheck] \u2713 Plugin version matches server version.");
            return;
        }

        // Дополнительно проверяем BukkitVersion на всякий случай
        String bukkitVersion = Bukkit.getBukkitVersion();
        if (bukkitVersion.contains(pluginVersion)) {
            plugin.getLogger().info("[VersionCheck] \u2713 Plugin version matches Bukkit version.");
            return;
        }

        plugin.getLogger().warning("");
        plugin.getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        plugin.getLogger().warning("!  PLUGIN VERSION MISMATCH!                                    !");
        plugin.getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        plugin.getLogger().warning("!                                                                 !");
        plugin.getLogger().warning("!  Plugin version:    " + padRight(pluginVersion, 35) + "!");
        plugin.getLogger().warning("!  Server version:    " + padRight(serverVersion, 35) + "!");
        plugin.getLogger().warning("!  Bukkit version:    " + padRight(bukkitVersion, 35) + "!");
        plugin.getLogger().warning("!                                                                 !");
        plugin.getLogger().warning("!  The plugin version '" + pluginVersion + "' was not found        !");
        plugin.getLogger().warning("!  in the server or Bukkit version string.                        !");
        plugin.getLogger().warning("!  This may indicate an incompatible server version.              !");
        plugin.getLogger().warning("!                                                                 !");
        plugin.getLogger().warning("!  Update your server or plugin to matching versions.             !");
        plugin.getLogger().warning("!                                                                 !");
        plugin.getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        plugin.getLogger().warning("");
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

    /** Извлекает major.minor из версии (1.21.4 → 1.21). */
    private String getMajorMinor(String version) {
        if (version == null) return "";
        String[] parts = version.split("\\.");
        if (parts.length >= 2) {
            return parts[0] + "." + parts[1];
        }
        return parts[0];
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
