package com.ultimateimprovments.module;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Check module — checks the server API-version compatibility (minimum required
 * version, default {@code 26.3}), LuckPerms presence and prints version information.
 * <p>
 * The API-version check reads the ACTUAL runtime server version (Paper
 * {@code getMinecraftVersion()} with fallbacks to {@code Bukkit.getVersion()} /
 * {@code Bukkit.getBukkitVersion()}), NOT the api-version declared in plugin.yml —
 * so editing the plugin's own api-version cannot bypass the check.
 * <p>
 * Config: {@code [version_check]} in UI-Core.toml ({@code enabled}, {@code min_api_version}).
 * <p>
 * Non-essential — if the check fails, the plugin still works.
 */
public class VersionCheckModule extends PluginModule {

    /** Minimum required server API version when the config value is missing or invalid. */
    private static final String DEFAULT_MIN_API_VERSION = "26.3";

    /** Border line of the warning banner (54 chars, same format as the other banners). */
    private static final String BANNER_BORDER = "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!";

    public VersionCheckModule() {
        super("VersionCheck", "infrastructure/core", false);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        String pluginVersion = plugin.getPluginMeta().getVersion();
        String apiVersion = plugin.getPluginMeta().getAPIVersion();
        String serverVersion = Bukkit.getVersion();
        String bukkitVersion = Bukkit.getBukkitVersion();
        String serverName = Bukkit.getServer().getName();

        // =========================
        // PRINT VERSION INFORMATION
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
        // CHECK THE SERVER API VERSION (minimum required, default 26.3)
        // =========================
        checkApiVersion(serverName, serverVersion, bukkitVersion);

        // =========================
        // CHECK LUCKPERMS PRESENCE
        // =========================
        checkLuckPerms(plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        // Nothing to clean up
    }

    // =========================
    // API-VERSION CHECK
    // =========================

    /**
     * Compares the ACTUAL running server API version with the configured minimum
     * ({@code version_check.min_api_version}, default {@code 26.3}).
     * 26.3 and every newer version pass; anything older prints the
     * UNSUPPORTED API VERSION banner.
     */
    private void checkApiVersion(String serverName, String serverVersion, String bukkitVersion) {
        // Config: [version_check] in UI-Core.toml
        boolean enabled = true;
        String minVersion = DEFAULT_MIN_API_VERSION;
        ConfigurationSection cfg = Main.getInstance().getConfig().getConfigurationSection("version_check");
        if (cfg != null) {
            enabled = cfg.getBoolean("enabled", true);
            String configured = cfg.getString("min_api_version", DEFAULT_MIN_API_VERSION);
            if (configured != null && isNumericVersion(configured.trim())) {
                minVersion = normalizeToMajorMinor(configured.trim());
            }
        }
        if (!enabled) {
            ConsoleLogger.info("[VersionCheck] API version check is disabled in config (version_check.enabled = false).");
            return;
        }

        // Resolve the ACTUAL runtime server version. The api-version from plugin.yml
        // is deliberately NOT used here: it can be edited to bypass the check.
        String serverApi = resolveServerApiVersion(serverVersion, bukkitVersion);
        if (!isNumericVersion(serverApi)) {
            ConsoleLogger.info("[VersionCheck] Cannot determine the server API version — skipping the check.");
            return;
        }

        if (compareVersions(serverApi, minVersion) >= 0) {
            ConsoleLogger.info("[VersionCheck] \u2713 Server API version: " + serverApi
                    + " (supported, requires " + minVersion + "+)");
            return;
        }

        // Unsupported API version
        ConsoleLogger.warn("");
        ConsoleLogger.warn(BANNER_BORDER);
        ConsoleLogger.warn(bannerLine("UNSUPPORTED API VERSION"));
        ConsoleLogger.warn(BANNER_BORDER);
        ConsoleLogger.warn(bannerLine("Detected:        " + serverApi + " (" + serverName + ")"));
        ConsoleLogger.warn(bannerLine("Required:        " + minVersion + " or newer"));
        ConsoleLogger.warn(bannerLine(""));
        ConsoleLogger.warn(bannerLine("This plugin requires API version " + minVersion + " or newer."));
        ConsoleLogger.warn(bannerLine("Older versions are NOT supported: features may be"));
        ConsoleLogger.warn(bannerLine("broken or missing entirely."));
        ConsoleLogger.warn(bannerLine(""));
        ConsoleLogger.warn(bannerLine("Please update your server software."));
        ConsoleLogger.warn(BANNER_BORDER);
        ConsoleLogger.warn("");
    }

    /**
     * Resolves the ACTUAL API version of the running server at runtime.
     * Order: Paper {@code getMinecraftVersion()} → version from {@code Bukkit.getVersion()}
     * → version from {@code Bukkit.getBukkitVersion()}. None of these sources depend
     * on plugin.yml, so editing the declared api-version cannot bypass the check.
     */
    private String resolveServerApiVersion(String serverVersion, String bukkitVersion) {
        try {
            String normalized = normalizeToMajorMinor(Bukkit.getServer().getMinecraftVersion());
            if (normalized != null) return normalized;
        } catch (Throwable ignored) {
            // Not a Paper-family server or older API — fall through
        }
        String normalized = normalizeToMajorMinor(extractServerVersionNumber(serverVersion));
        if (normalized != null) return normalized;
        return normalizeToMajorMinor(bukkitVersion == null ? null : bukkitVersion.split("-")[0]);
    }

    // =========================
    // LUCKPERMS CHECK
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
        ConsoleLogger.warn("!    - /ui sethome, /ui home, /ui delhome                       !");
        ConsoleLogger.warn("!    - /ui auth (forcelogin, resetauth, chgpass, delsession)    !");
        ConsoleLogger.warn("!    - /ui power (off, reboot)                                  !");
        ConsoleLogger.warn("!    - /ui structures (dfc, magnet)                             !");
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

    /** Formats a banner content line: "!" + content padded to 52 + "!". */
    private String bannerLine(String content) {
        return "!" + padRight(content, 52) + "!";
    }

    /** Checks whether a string looks like a numeric version (e.g. "26.3"). */
    private boolean isNumericVersion(String version) {
        if (version == null || version.isEmpty()) return false;
        // Must start with a digit and contain a dot
        return version.matches("\\d+\\.\\d+.*");
    }

    /** Parses the leading numeric parts of a version ("26.2.build.+" → [26, 2]). */
    private int[] versionParts(String version) {
        if (version == null) return new int[0];
        List<Integer> parts = new ArrayList<>();
        for (String part : version.split("\\.")) {
            if (!part.matches("\\d+")) break;
            parts.add(Integer.parseInt(part));
        }
        int[] arr = new int[parts.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = parts.get(i);
        return arr;
    }

    /**
     * Reduces a version to its numeric major.minor prefix ("26.2.build.+" → "26.2").
     * Returns null if there are fewer than two numeric parts.
     */
    private String normalizeToMajorMinor(String version) {
        int[] parts = versionParts(version);
        if (parts.length < 2) return null;
        StringBuilder sb = new StringBuilder();
        for (int part : parts) {
            if (sb.length() > 0) sb.append('.');
            sb.append(part);
        }
        return sb.toString();
    }

    /** Compares two numeric versions: negative if a &lt; b, zero if equal, positive if a &gt; b. */
    private int compareVersions(String a, String b) {
        int[] pa = versionParts(a);
        int[] pb = versionParts(b);
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int x = i < pa.length ? pa[i] : 0;
            int y = i < pb.length ? pb[i] : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    /** Extracts the Paper/Leaf version from the full Bukkit.getVersion() string ("git-Paper-26.2.build.+..." → "26.2.build.+"). */
    private String extractServerVersionNumber(String version) {
        if (version == null) return "?";
        // "git-Paper-26.2.build.+ (MC: 1.21.5)" → take the part before the space
        String firstPart = version.split(" ")[0]; // "git-Paper-26.2.build.+"
        // The last segment after the last '-' is the version
        int lastDash = firstPart.lastIndexOf("-");
        if (lastDash >= 0 && lastDash < firstPart.length() - 1) {
            return firstPart.substring(lastDash + 1);
        }
        return firstPart;
    }
}
