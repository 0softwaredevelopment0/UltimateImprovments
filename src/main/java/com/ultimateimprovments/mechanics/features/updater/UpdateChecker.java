package com.ultimateimprovments.mechanics.features.updater;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.ConsoleLogger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auto-updater: compares the version from the JAR filename in the {@code Jar/}
 * folder on GitHub with the plugin's current version.
 * <p>
 * Logic:
 * <ol>
 *   <li>Read the plugin's current version (e.g. "1.7.54");</li>
 *   <li>Query the GitHub API {@code /contents/Jar/} — get the file list;</li>
 *   <li>Find all {@code .jar} files, extract the version from the name;</li>
 *   <li>Pick the JAR with the newest version;</li>
 *   <li>Compare by components (major.minor.commits):</li>
 *   <li>If jar-version > current → UPDATE_AVAILABLE;</li>
 *   <li>If jar-version <= current → UP_TO_DATE;</li>
 *   <li>After a successful download, store the version in the DB (to avoid re-downloading).</li>
 * </ol>
 */
public class UpdateChecker {

    // =========================
    // ⚙ CONFIGURATION
    // =========================
    private static final String GITHUB_OWNER = "rizer001";
    private static final String GITHUB_REPO = "UltimateImprovments";
    /** GitHub Contents API — file list in the repository's Jar/ folder. */
    private static final String JAR_DIR_API_URL = "https://api.github.com/repos/"
            + GITHUB_OWNER + "/" + GITHUB_REPO + "/contents/Jar/";
    private static final String USER_AGENT = "UltimateImprovments-Updater";
    private static final int TIMEOUT_SECONDS = 15;

    /** Regex for extracting major.minor.commits from a jar filename. */
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");
    /** Regex for a jar file like "UltimateImprovments-1.7.54.jar". */
    private static final Pattern JAR_FILE_PATTERN = Pattern.compile(
            Pattern.quote(GITHUB_REPO) + "-(\\d+\\.\\d+(?:\\.\\d+)?)\\.jar", Pattern.CASE_INSENSITIVE);

    // =========================
    // STATUS (volatile — written from async, read from main)
    // =========================
    public enum UpdateStatus {
        UP_TO_DATE,
        UPDATE_AVAILABLE,
        UPDATE_DOWNLOADED,
        UPDATE_FAILED,
        CHECK_FAILED
    }

    private static volatile UpdateStatus status = UpdateStatus.UP_TO_DATE;
    private static volatile String latestJarVersion = "";  // version from the latest jar filename (e.g. "1.7.75")
    private static volatile String errorMessage = "";

    // Cache for /ui updatejar — to avoid hitting the API repeatedly
    private static volatile String cachedDownloadUrl = "";
    private static volatile String cachedJarName = "";     // filename (e.g. "UltimateImprovments-1.7.75.jar")
    private static volatile String cachedJarVersion = "";  // version from the jar file

    public static UpdateStatus getStatus() { return status; }
    public static String getLatestTag() { return latestJarVersion; }
    public static String getErrorMessage() { return errorMessage; }

    // =========================
    // CHECK START (called from Main.onEnable)
    // =========================
    public static void checkAsync() {
        Main plugin = Main.getInstance();
        ConsoleLogger.info("[Updater] Checking for updates (Jar/ folder)...");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                performCheck(plugin);
            } catch (Exception e) {
                status = UpdateStatus.CHECK_FAILED;
                errorMessage = e.getMessage();
                ConsoleLogger.warn("[Updater] Check failed: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    // =========================
    // MAIN LOGIC (server start — check only, no auto-download)
    // =========================
    private static void performCheck(Main plugin) throws Exception {
        File pluginDir = plugin.getDataFolder().getParentFile();
        File currentJar = plugin.getPluginFile();

        // ════════════════════════════════════════
        // 0. Clean up orphaned files from previous runs
        // ════════════════════════════════════════
        cleanupOrphanedFiles(pluginDir, currentJar);

        // ════════════════════════════════════════
        // 1. Plugin's current version (from plugin.yml)
        // ════════════════════════════════════════
        String currentVersion = plugin.getDescription().getVersion();
        String storedVersion = getStoredTag();  // stores the version of the last installed jar
        ConsoleLogger.info("[Updater] Current version: " + currentVersion);
        ConsoleLogger.info("[Updater] Last installed jar: "
                + (storedVersion.isEmpty() ? "<none>" : storedVersion));

        // ════════════════════════════════════════
        // 2. HTTP request to the GitHub Contents API — file list in Jar/
        // ════════════════════════════════════════
        JarFileInfo latestJar = fetchLatestJarFromRepo(plugin);
        if (latestJar == null) {
            ConsoleLogger.info("[Updater] No jar files found in Jar/ folder — up to date.");
            status = UpdateStatus.UP_TO_DATE;
            return;
        }

        String jarVersion = latestJar.version;
        ConsoleLogger.info("[Updater] Latest jar in Jar/: " + latestJar.name
                + " (version: " + jarVersion + ")");

        // ════════════════════════════════════════
        // 3. Compare versions
        // ════════════════════════════════════════
        if (!isNewer(jarVersion, currentVersion)) {
            // Current >= jar-version → we are not behind
            ConsoleLogger.info("[Updater] Up to date (current: "
                    + currentVersion + " >= jar: " + jarVersion + ")");
            latestJarVersion = jarVersion;
            status = UpdateStatus.UP_TO_DATE;
            return;
        }

        // ════════════════════════════════════════
        // 4. Jar is newer → check whether it's already installed
        // ════════════════════════════════════════
        if (jarVersion.equals(storedVersion)) {
            // Already downloaded, waiting for a restart
            ConsoleLogger.info("[Updater] Update " + jarVersion
                    + " already downloaded — restart required.");
            latestJarVersion = jarVersion;
            status = UpdateStatus.UPDATE_DOWNLOADED;
            return;
        }

        // ════════════════════════════════════════
        // 5. Update available!
        // ════════════════════════════════════════
        latestJarVersion = jarVersion;
        status = UpdateStatus.UPDATE_AVAILABLE;

        ConsoleLogger.warn("");
        ConsoleLogger.warn("===========================================");
        ConsoleLogger.warn("  [UPDATE AVAILABLE] " + latestJar.name);
        ConsoleLogger.warn("  Jar: " + jarVersion);
        ConsoleLogger.warn("  Current: v" + currentVersion);
        ConsoleLogger.warn("");
        ConsoleLogger.warn("  To install, type: /ui updatejar");
        ConsoleLogger.warn("  To ignore this update, do nothing.");
        ConsoleLogger.warn("===========================================");
        ConsoleLogger.warn("");
    }

    // =========================
    // 🔍 GITHUB CONTENTS API — Jar/
    // =========================

    /**
     * Data about a jar file from the Jar/ folder on GitHub.
     */
    private static class JarFileInfo {
        final String name;          // "UltimateImprovments-1.7.75.jar"
        final String version;       // "1.7.75"
        final String downloadUrl;   // raw.githubusercontent.com URL

        JarFileInfo(String name, String version, String downloadUrl) {
            this.name = name;
            this.version = version;
            this.downloadUrl = downloadUrl;
        }
    }

    /**
     * Queries the GitHub Contents API for the {@code Jar/} folder and returns
     * info about the newest jar file (with the highest version).
     *
     * @return JarFileInfo or null if there are no jar files
     */
    private static JarFileInfo fetchLatestJarFromRepo(Main plugin) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(JAR_DIR_API_URL))
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 403 || response.statusCode() == 429) {
            ConsoleLogger.warn("[Updater] GitHub API rate limit exceeded (HTTP "
                    + response.statusCode() + "). Check will be skipped.");
            status = UpdateStatus.CHECK_FAILED;
            return null;
        }

        if (response.statusCode() != 200) {
            ConsoleLogger.warn("[Updater] GitHub Contents API returned HTTP "
                    + response.statusCode());
            status = UpdateStatus.CHECK_FAILED;
            return null;
        }

        // Parse the JSON array
        JsonArray items;
        try {
            items = JsonParser.parseString(response.body()).getAsJsonArray();
        } catch (Exception e) {
            // If the response is not an array — it's an error object or not a directory
            ConsoleLogger.warn("[Updater] Unexpected API response format");
            e.printStackTrace();
            return null;
        }

        if (items.isEmpty()) {
            return null;
        }

        // Look for jar files and pick the newest by version
        JarFileInfo best = null;
        int[] bestVersion = null;

        for (JsonElement elem : items) {
            JsonObject item = elem.getAsJsonObject();
            String type = item.get("type").getAsString();
            if (!"file".equals(type)) continue;

            String name = item.get("name").getAsString();
            if (!name.endsWith(".jar")) continue;

            // Extract the version from the name "UltimateImprovments-1.7.75.jar"
            Matcher m = JAR_FILE_PATTERN.matcher(name);
            if (!m.find()) continue;

            String versionStr = m.group(1);
            int[] versionInts = parseVersionToInts(versionStr);
            if (versionInts == null) continue;

            // Compare with the current best
            if (best == null || compareVersions(versionInts, bestVersion) > 0) {
                String downloadUrl = item.get("download_url").getAsString();
                best = new JarFileInfo(name, versionStr, downloadUrl);
                bestVersion = versionInts;
            }
        }

        if (best != null) {
            // Cache for /ui updatejar
            cachedDownloadUrl = best.downloadUrl;
            cachedJarName = best.name;
            cachedJarVersion = best.version;

            ConsoleLogger.info("[Updater] Found latest jar: " + best.name
                    + " (v" + best.version + ")");
        }

        return best;
    }

    /**
     * Compares two version arrays.
     * @return a positive number if a > b, 0 if equal, negative if a < b
     */
    private static int compareVersions(int[] a, int[] b) {
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) return a[i] - b[i];
        }
        return 0;
    }

    // =========================
    // 🔢 VERSION PARSING AND COMPARISON
    // =========================

    /**
     * Extracts a version number from a string.
     * Examples: "1.8.23" → "1.8.23", "1.7" → "1.7.0", "v1.8.23" → "1.8.23".
     *
     * @param input the string containing a version number
     * @return the version string "major.minor.patch" or null if it couldn't be parsed
     */
    private static String parseVersion(String input) {
        if (input == null || input.isEmpty()) return null;
        Matcher m = VERSION_PATTERN.matcher(input);
        if (!m.find()) return null;
        int major = Integer.parseInt(m.group(1));
        int minor = Integer.parseInt(m.group(2));
        int patch = m.group(3) != null ? Integer.parseInt(m.group(3)) : 0;
        return major + "." + minor + "." + patch;
    }

    /**
     * Compares two versions by components (major.minor.patch).
     *
     * @param jarVersion the version from the jar file (e.g. "1.8.23")
     * @param currentVersion the plugin's current version (e.g. "1.7.54")
     * @return true if the jar is newer than current (i.e. an update exists)
     */
    private static boolean isNewer(String jarVersion, String currentVersion) {
        int[] jar = parseVersionToInts(jarVersion);
        int[] cur = parseVersionToInts(currentVersion);
        if (jar == null || cur == null) return false;

        if (jar[0] != cur[0]) return jar[0] > cur[0];
        if (jar[1] != cur[1]) return jar[1] > cur[1];
        return jar[2] > cur[2];
    }

    /** Parses "1.7.54" into int[]{1, 7, 54}. */
    private static int[] parseVersionToInts(String versionString) {
        Matcher m = VERSION_PATTERN.matcher(versionString);
        if (!m.find()) return null;
        int major = Integer.parseInt(m.group(1));
        int minor = Integer.parseInt(m.group(2));
        int patch = m.group(3) != null ? Integer.parseInt(m.group(3)) : 0;
        return new int[]{major, minor, patch};
    }

    // =========================
    // 💾 DB WORK
    // =========================

    /** Reads the last installed jar version from the updater_state table. */
    private static String getStoredTag() {
        Connection con = DatabaseManager.getConnection();
        if (con == null) return "";

        try (PreparedStatement ps = con.prepareStatement(
                "SELECT value FROM updater_state WHERE key = 'installed_jar_version'")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("value");
            }
        } catch (Exception e) {
            Main.getInstance().getLogger().fine("[Updater] DB read version error: " + e.getMessage());
            e.printStackTrace();
        }
        return "";
    }

    /** Saves the jar version into the updater_state table. */
    private static void saveStoredTag(String version) {
        Connection con = DatabaseManager.getConnection();
        if (con == null) return;

        try {
            // Clear the old key from the previous system (releases)
            try (PreparedStatement clean = con.prepareStatement(
                    "DELETE FROM updater_state WHERE key = 'installed_tag'")) {
                clean.executeUpdate();
            }

            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE updater_state SET value = ? WHERE key = 'installed_jar_version'")) {
                ps.setString(1, version);
                if (ps.executeUpdate() == 0) {
                    try (PreparedStatement insert = con.prepareStatement(
                            "INSERT INTO updater_state (key, value) VALUES ('installed_jar_version', ?)")) {
                        insert.setString(1, version);
                        insert.executeUpdate();
                    }
                }
            }
        } catch (Exception e) {
            ConsoleLogger.warn(
                    "[Updater] Failed to save version to DB: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =========================
    // 🗑 ORPHANED FILE CLEANUP
    // =========================
    private static void cleanupOrphanedFiles(File pluginDir, File currentJar) {
        File updateFile = new File(pluginDir, currentJar.getName() + ".update");
        try { Files.deleteIfExists(updateFile.toPath()); } catch (Exception ignored) {}

        File bakFile = new File(pluginDir, currentJar.getName() + ".bak");
        try { Files.deleteIfExists(bakFile.toPath()); } catch (Exception ignored) {}
    }

    // =========================
    // 🔍 /ui checkver — MANUAL UPDATE CHECK
    // =========================

    /**
     * Runs an async GitHub check (Jar/ folder) for new versions
     * and sends the result to the command sender.
     */
    public static void checkOnly(CommandSender sender) {
        Main plugin = Main.getInstance();
        String senderName = sender instanceof Player ? ((Player) sender).getName() : "Console";
        ConsoleLogger.info("[Updater] Manual update check requested by " + senderName);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String currentVersion = plugin.getDescription().getVersion();
                String storedVersion = getStoredTag();

                // Step 1: get the latest jar from Jar/
                JarFileInfo latestJar = fetchLatestJarFromRepo(plugin);
                if (latestJar == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "update.check_error", "<red>❌ No jar files found in GitHub Jar/ folder!</red>")));
                    });
                    return;
                }

                String jarVersion = latestJar.version;
                boolean hasVersion = jarVersion != null;
                boolean isNewRelease = hasVersion && isNewer(jarVersion, currentVersion);
                boolean isPendingRestart = jarVersion != null && jarVersion.equals(storedVersion);

                // Send the result on the main thread
                final String finalJarName = latestJar.name;
                final String finalJarVer = jarVersion != null ? jarVersion : "<unparseable>";
                final String finalCurrentVer = currentVersion;
                final boolean finalIsNew = isNewRelease;
                final boolean finalHasVer = hasVersion;
                final boolean finalPending = isPendingRestart;

                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.header",
                            "<gold>=== <white>UltimateImprovments — Update Check</white> ===")));
                    sender.sendMessage("");
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.plugin_version",
                            "<gray>Your version:</gray> <white>%version%</white>")
                            .replace("%version%", finalCurrentVer)));
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.latest_release",
                            "<gray>GitHub Jar/:</gray> <white>%name%</white>")
                            .replace("%name%", finalJarName)));

                    if (finalHasVer) {
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "update.release_version",
                                "<gray>Jar version:</gray> <white>%version%</white>")
                                .replace("%version%", finalJarVer)));
                    }

                    if (finalPending && finalIsNew) {
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "update.already_downloaded",
                                "<yellow>⟳</yellow> <gray>Update already downloaded!</gray> <white>%ver%</white> <gray>— restart server to apply.</gray>")
                                .replace("%ver%", finalJarVer)));
                    } else if (finalIsNew) {
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "update.update_available",
                                "<green>✨</green> <white>Update available!</white> <white>%jar%</white>")
                                .replace("%jar%", finalJarName)));
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "update.update_from_to",
                                "<gray>v%from% → v%to%</gray>")
                                .replace("%from%", finalCurrentVer)
                                .replace("%to%", finalJarVer)));

                        if (sender instanceof Player) {
                            net.kyori.adventure.text.Component updateButton = MessageUtil.parse(
                                    MessagesManager.getString("update.install_button",
                                            "<dark_green>[<green>✔ Install Update</green><dark_green>]</dark_green>"))
                                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/ui updatejar"))
                                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                            MessageUtil.parse("<green>Click to download and install update\n"
                                                    + "<gray>File: <white>" + finalJarName + "\n"
                                                    + "<gray>Restart required after installation")));
                            sender.sendMessage(updateButton);

                            sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                    "update.install_hint",
                                    "<gray> or type </gray><white>/ui updatejar</white>")));
                        } else {
                            sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                    "update.install_console",
                                    "<gray>To install, type: </gray><white>/ui updatejar</white>")));
                        }

                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "update.ignore_hint",
                                "<gray>To ignore, do nothing.</gray>")));
                    } else if (!finalHasVer) {
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "update.cant_parse_version",
                                "<yellow>⚠</yellow> <gray>Cannot parse version from jar file. Skipping.</gray>")));
                    } else {
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "update.up_to_date",
                                "<green>✔</green> <green>All up to date!</green> "
                                + "<gray>(v%current% ≥ v%jar%)</gray>")
                                .replace("%current%", finalCurrentVer)
                                .replace("%jar%", finalJarVer)));
                    }

                    sender.sendMessage("");
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.header",
                            "<gold>=== <white>UltimateImprovments — Update Check</white> ===")));
                });

            } catch (java.net.UnknownHostException e) {
                ConsoleLogger.warn("[Updater] Manual check failed: DNS resolution error");
                e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.connection_error",
                            "<red>❌ Connection error with GitHub!</red>")));
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.dns_error_hint",
                            "<gray>Could not resolve DNS for api.github.com</gray>")));
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.connection_check_hint",
                            "<gray>Check server internet connection.</gray>")));
                });
            } catch (java.net.http.HttpTimeoutException e) {
                ConsoleLogger.warn("[Updater] Manual check failed: Connection timeout");
                e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.timeout_error",
                            "<red>❌ Connection timeout with GitHub!</red>")));
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.timeout_hint",
                            "<gray>GitHub did not respond within %seconds% seconds.</gray>")
                            .replace("%seconds%", String.valueOf(TIMEOUT_SECONDS))));
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.timeout_retry_hint",
                            "<gray>Check internet connection or try again later.</gray>")));
                });
            } catch (Exception e) {
                ConsoleLogger.warn("[Updater] Manual check failed: " + e.getMessage());
                e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.check_error",
                            "<red>❌ Error checking for updates!</red>")));
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.install_error_detail",
                            "<gray>%type%: %message%</gray>")
                            .replace("%type%", e.getClass().getSimpleName())
                            .replace("%message%", e.getMessage() != null ? e.getMessage() : "")));
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.install_error_console",
                            "<gray>Stack trace in server console.</gray>")));
                });
            }
        });
    }

    // =========================
    // 📥 /ui updatejar — DOWNLOAD AND INSTALL THE UPDATE
    // =========================

    /**
     * Downloads the latest JAR from the {@code Jar/} folder on GitHub and replaces the current one.
     * After a successful replacement, saves the jar version to the DB.
     */
    public static void downloadAndReplace(CommandSender sender) {
        Main plugin = Main.getInstance();
        String senderName = sender instanceof Player ? ((Player) sender).getName() : "Console";
        ConsoleLogger.info("[Updater] /ui updatejar requested by " + senderName);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                File pluginDir = plugin.getDataFolder().getParentFile();
                File currentJar = plugin.getPluginFile();

                if (currentJar == null || !currentJar.exists()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "update.cant_find_jar",
                                "<red>❌ Cannot find current plugin JAR file!</red>")));
                    });
                    return;
                }

                cleanupOrphanedFiles(pluginDir, currentJar);

                // Use the cache if available, otherwise fetch the API
                String downloadUrl;
                String jarName;
                String jarVersion;

                if (!cachedDownloadUrl.isEmpty()) {
                    downloadUrl = cachedDownloadUrl;
                    jarName = cachedJarName;
                    jarVersion = cachedJarVersion;
                    ConsoleLogger.info("[Updater] Using cached jar info: " + jarName);
                } else {
                    // Fetch fresh data from GitHub
                    JarFileInfo latestJar = fetchLatestJarFromRepo(plugin);
                    if (latestJar == null) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                    "update.no_release_info",
                                    "<red>❌ Could not find any jar files in GitHub Jar/ folder!</red>")));
                        });
                        return;
                    }
                    downloadUrl = latestJar.downloadUrl;
                    jarName = latestJar.name;
                    jarVersion = latestJar.version;
                }

                if (downloadUrl == null || downloadUrl.isEmpty()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "update.no_jar_in_release",
                                "<red>❌ No download URL for jar</red>")));
                    });
                    return;
                }

                // Check — isn't the same jar already installed?
                String storedVersion = getStoredTag();
                if (jarVersion != null && jarVersion.equals(storedVersion)) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "update.already_installed",
                                "<green>✔</green> <green>This version is already installed! "
                                + "(</green><white>%ver%</white><green>)</green>")
                                .replace("%ver%", jarVersion)));
                    });
                    return;
                }

                // Status: downloading
                final String finalJarName = jarName;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.downloading_release",
                            "<yellow>⟳</yellow> <gray>Downloading update</gray> <white>%name%</white><gray>...</gray>")
                            .replace("%name%", finalJarName)));
                });

                // Downloading the JAR
                File tempFile = new File(pluginDir,
                        plugin.getDescription().getName() + ".jar.update");

                HttpClient client = HttpClient.newHttpClient();
                HttpRequest downloadRequest = HttpRequest.newBuilder()
                        .uri(URI.create(downloadUrl))
                        .header("Accept", "application/octet-stream")
                        .timeout(Duration.ofSeconds(120))
                        .GET()
                        .build();

                HttpResponse<InputStream> downloadResponse = client.send(downloadRequest,
                        HttpResponse.BodyHandlers.ofInputStream());

                if (downloadResponse.statusCode() != 200
                        && downloadResponse.statusCode() != 302) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "update.download_error",
                                "<red>❌ Download error: HTTP %status%</red>")
                                .replace("%status%", String.valueOf(downloadResponse.statusCode()))));
                    });
                    return;
                }

                long totalBytes = 0;
                try (InputStream in = downloadResponse.body();
                     FileOutputStream out = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        totalBytes += read;
                    }
                }

                final long downloadedKB = totalBytes / 1024;

                // Replacing the JAR
                boolean replaced = replaceJar(plugin, currentJar, tempFile, jarName);

                if (replaced) {
                    // Save the jar version (to avoid re-downloading on the next check)
                    if (jarVersion != null) {
                        saveStoredTag(jarVersion);
                    }

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "update.install_success_header",
                                "<gold>=== <green>Update Installed!</green> ===")));
                        sender.sendMessage("");
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "update.install_release",
                                "<gray>File:</gray> <white>%name%</white>")
                                .replace("%name%", finalJarName)));
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "update.install_size",
                                "<gray>Downloaded:</gray> <white>%size% KB</white>")
                                .replace("%size%", String.valueOf(downloadedKB))));
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "update.install_restart",
                                "<red>⚠ Restart the server to apply the update!</red>")));
                    });
                } else {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "update.install_failed_replace",
                                "<red>❌ Failed to replace JAR (file in use by process).</red>")));
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "update.install_failed_manual",
                                "<gray>Check server console for manual replacement instructions.</gray>")));
                    });
                }

            } catch (Exception e) {
                ConsoleLogger.error("[Updater] /ui updatejar failed!");
                e.printStackTrace();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.install_error",
                            "<red>❌ Error downloading update!</red>")));
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.install_error_detail",
                            "<gray>%type%: %message%</gray>")
                            .replace("%type%", e.getClass().getSimpleName())
                            .replace("%message%", e.getMessage() != null ? e.getMessage() : "")));
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.install_error_console",
                            "<gray>Stack trace in server console.</gray>")));
                });
            }
        });
    }

    /** @return true if the replacement succeeded (including fallback) */
    private static boolean replaceJar(Main plugin, File currentJar, File updateFile, String jarName) {
        if (currentJar == null || !currentJar.exists()) {
            ConsoleLogger.warn("[Updater] Cannot find current JAR file");
            status = UpdateStatus.UPDATE_FAILED;
            return false;
        }

        Path updatePath = updateFile.toPath();
        Path targetPath = currentJar.toPath();
        Path backupPath = new File(currentJar.getParentFile(),
                currentJar.getName() + ".bak").toPath();

        // STEP 1: Backup
        boolean backupDone = false;
        try {
            Files.move(targetPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            ConsoleLogger.info("[Updater] Backed up current JAR");
            backupDone = true;
        } catch (Exception e) {
            ConsoleLogger.warn("[Updater] Backup failed (non-critical): " + e.getMessage());
            e.printStackTrace();
        }

        // STEP 2: Move the new JAR into the current one's place
        try {
            Files.move(updatePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            ConsoleLogger.error("[Updater] Failed to replace JAR: " + e.getMessage());
            e.printStackTrace();

            boolean backupRestored = false;
            if (backupDone) {
                try {
                    Files.move(backupPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    ConsoleLogger.info("[Updater] Backup restored");
                    backupRestored = true;
                } catch (Exception restoreErr) {
                    ConsoleLogger.error("[Updater] Could not restore backup! "
                            + "Manual recovery needed. Backup at: " + backupPath);
                    restoreErr.printStackTrace();
                }
            }

            if (backupRestored) {
                status = UpdateStatus.UPDATE_FAILED;
                errorMessage = "Could not replace JAR file: " + e.getMessage();
                return false;
            }

            boolean fallbackSuccess = placeUpdateInPluginsFolder(plugin, updateFile, currentJar, jarName);
            if (fallbackSuccess) {
                return true;
            }

            status = UpdateStatus.UPDATE_FAILED;
            errorMessage = "Could not replace JAR file: " + e.getMessage();
            return false;
        }

        try { Files.deleteIfExists(backupPath); } catch (Exception ignored) {}

        status = UpdateStatus.UPDATE_DOWNLOADED;
        ConsoleLogger.info("");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("  [UPDATE INSTALLED]");
        ConsoleLogger.info("  JAR: " + jarName);
        ConsoleLogger.info("");
        ConsoleLogger.info("  Restart server to apply the update.");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("");
        return true;
    }

    // =========================
    // 🔄 FALLBACK: put the JAR into the plugins folder when replace fails
    // =========================

    private static boolean placeUpdateInPluginsFolder(Main plugin, File updateFile,
                                                       File currentJar, String jarName) {
        Path updatePath = updateFile.toPath();
        File pluginDir = currentJar.getParentFile();
        Path targetPath = currentJar.toPath();

        if (!Files.exists(targetPath)) {
            try {
                Files.copy(updatePath, targetPath);
                try { Files.deleteIfExists(updatePath); } catch (Exception ignored) {}
                ConsoleLogger.info("[Updater] JAR copied to plugins folder (copy fallback)");
                status = UpdateStatus.UPDATE_DOWNLOADED;
                logFallbackSuccess(plugin, jarName);
                return true;
            } catch (Exception copyErr) {
                ConsoleLogger.warn("[Updater] Copy fallback failed: " + copyErr.getMessage());
                copyErr.printStackTrace();
            }
        }

        String fallbackName = currentJar.getName().replace(".jar", "") + "-NEW.jar";
        File fallbackFile = new File(pluginDir, fallbackName);
        try {
            Files.move(updatePath, fallbackFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            ConsoleLogger.warn("");
            ConsoleLogger.warn("===========================================");
            ConsoleLogger.warn("  [UPDATE DOWNLOADED — MANUAL STEP REQUIRED]");
            ConsoleLogger.warn("  JAR: " + jarName);
            ConsoleLogger.warn("");
            ConsoleLogger.warn("  New JAR placed at: plugins/" + fallbackName);
            ConsoleLogger.warn("");
            ConsoleLogger.warn("  To apply: stop server, then:");
            ConsoleLogger.warn("    1) Delete old JAR: " + currentJar.getName());
            ConsoleLogger.warn("    2) Rename " + fallbackName + " -> " + currentJar.getName());
            ConsoleLogger.warn("    3) Delete " + currentJar.getName() + ".bak");
            ConsoleLogger.warn("    4) Start server");
            ConsoleLogger.warn("===========================================");
            ConsoleLogger.warn("");
            status = UpdateStatus.UPDATE_DOWNLOADED;
            return true;
        } catch (Exception renameErr) {
            ConsoleLogger.error("[Updater] All fallback strategies failed: " + renameErr.getMessage());
            ConsoleLogger.error("[Updater] Update file left at: " + updateFile.getAbsolutePath());
            ConsoleLogger.error("[Updater] Manual recovery: stop server, move this file to plugins/"
                    + currentJar.getName());
            renameErr.printStackTrace();
        }

        return false;
    }

    private static void logFallbackSuccess(Main plugin, String jarName) {
        ConsoleLogger.warn("");
        ConsoleLogger.warn("===========================================");
        ConsoleLogger.warn("  [UPDATE READY — RESTART REQUIRED]");
        ConsoleLogger.warn("  JAR: " + jarName);
        ConsoleLogger.warn("");
        ConsoleLogger.warn("  New JAR placed in plugins folder.");
        ConsoleLogger.warn("  Restart server to apply the update.");
        ConsoleLogger.warn("===========================================");
        ConsoleLogger.warn("");
    }
}
