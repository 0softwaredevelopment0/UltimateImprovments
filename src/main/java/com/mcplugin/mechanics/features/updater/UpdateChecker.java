package com.mcplugin.mechanics.features.updater;

import com.mcplugin.core.Main;
import com.mcplugin.config.MessagesManager;
import com.mcplugin.database.DatabaseManager;
import com.mcplugin.util.MessageUtil;
import com.mcplugin.util.ConsoleLogger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

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
 * Auto-updater: сравнивает номер версии из имени JAR-файла в папке {@code Jar/}
 * на GitHub с текущей версией плагина.
 * <p>
 * Логика:
 * <ol>
 *   <li>Читаем текущую версию плагина (например "1.7.54");</li>
 *   <li>Запрашиваем GitHub API {@code /contents/Jar/} — получаем список файлов;</li>
 *   <li>Находим все {@code .jar} файлы, извлекаем версию из имени;</li>
 *   <li>Выбираем JAR с самой новой версией;</li>
 *   <li>Сравниваем по компонентам (major.minor.commits):</li>
 *   <li>Если jar-версия > текущей → UPDATE_AVAILABLE;</li>
 *   <li>Если jar-версия <= текущей → UP_TO_DATE;</li>
 *   <li>После успешной загрузки сохраняем в БД версию (чтобы не перекачивать).</li>
 * </ol>
 */
public class UpdateChecker {

    // =========================
    // ⚙ КОНФИГУРАЦИЯ
    // =========================
    private static final String GITHUB_OWNER = "rizer001";
    private static final String GITHUB_REPO = "MC-Plugin";
    /** GitHub Contents API — список файлов в папке Jar/ репозитория. */
    private static final String JAR_DIR_API_URL = "https://api.github.com/repos/"
            + GITHUB_OWNER + "/" + GITHUB_REPO + "/contents/Jar/";
    private static final String USER_AGENT = "MC-Plugin-Updater";
    private static final int TIMEOUT_SECONDS = 15;

    /** Regex для извлечения major.minor.commits из имени jar-файла. */
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");
    /** Regex для jar-файла вида "MC-Plugin-1.7.54.jar". */
    private static final Pattern JAR_FILE_PATTERN = Pattern.compile(
            Pattern.quote(GITHUB_REPO) + "-(\\d+\\.\\d+(?:\\.\\d+)?)\\.jar", Pattern.CASE_INSENSITIVE);

    // =========================
    // СТАТУС (volatile — пишется из async, читается с main)
    // =========================
    public enum UpdateStatus {
        UP_TO_DATE,
        UPDATE_AVAILABLE,
        UPDATE_DOWNLOADED,
        UPDATE_FAILED,
        CHECK_FAILED
    }

    private static volatile UpdateStatus status = UpdateStatus.UP_TO_DATE;
    private static volatile String latestJarVersion = "";  // версия из имени последнего jar (напр. "1.7.75")
    private static volatile String errorMessage = "";

    // Кеш для /mp updatejar — чтобы не дёргать API повторно
    private static volatile String cachedDownloadUrl = "";
    private static volatile String cachedJarName = "";     // имя файла (напр. "MC-Plugin-1.7.75.jar")
    private static volatile String cachedJarVersion = "";  // версия из jar-файла

    public static UpdateStatus getStatus() { return status; }
    public static String getLatestTag() { return latestJarVersion; }
    public static String getErrorMessage() { return errorMessage; }

    // =========================
    // ЗАПУСК ПРОВЕРКИ (вызывается из Main.onEnable)
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
    // ОСНОВНАЯ ЛОГИКА (старт сервера — только проверка, без авто-загрузки)
    // =========================
    private static void performCheck(Main plugin) throws Exception {
        File pluginDir = plugin.getDataFolder().getParentFile();
        File currentJar = plugin.getPluginFile();

        // ════════════════════════════════════════
        // 0. Очистка orphaned файлов от предыдущих запусков
        // ════════════════════════════════════════
        cleanupOrphanedFiles(pluginDir, currentJar);

        // ════════════════════════════════════════
        // 1. Текущая версия плагина (из plugin.yml)
        // ════════════════════════════════════════
        String currentVersion = plugin.getDescription().getVersion();
        String storedVersion = getStoredTag();  // хранит версию последнего установленного jar
        ConsoleLogger.info("[Updater] Current version: " + currentVersion);
        ConsoleLogger.info("[Updater] Last installed jar: "
                + (storedVersion.isEmpty() ? "<none>" : storedVersion));

        // ════════════════════════════════════════
        // 2. HTTP-запрос к GitHub Contents API — список файлов в Jar/
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
        // 3. Сравниваем версии
        // ════════════════════════════════════════
        if (!isNewer(jarVersion, currentVersion)) {
            // Текущая >= jar-версии → мы не старше
            ConsoleLogger.info("[Updater] Up to date (current: "
                    + currentVersion + " >= jar: " + jarVersion + ")");
            latestJarVersion = jarVersion;
            status = UpdateStatus.UP_TO_DATE;
            return;
        }

        // ════════════════════════════════════════
        // 4. Jar новее → проверяем, не установлен ли уже
        // ════════════════════════════════════════
        if (jarVersion.equals(storedVersion)) {
            // Уже скачан, ждёт рестарта
            ConsoleLogger.info("[Updater] Update " + jarVersion
                    + " already downloaded — restart required.");
            latestJarVersion = jarVersion;
            status = UpdateStatus.UPDATE_DOWNLOADED;
            return;
        }

        // ════════════════════════════════════════
        // 5. Обновление доступно!
        // ════════════════════════════════════════
        latestJarVersion = jarVersion;
        status = UpdateStatus.UPDATE_AVAILABLE;

        ConsoleLogger.warn("");
        ConsoleLogger.warn("===========================================");
        ConsoleLogger.warn("  [UPDATE AVAILABLE] " + latestJar.name);
        ConsoleLogger.warn("  Jar: " + jarVersion);
        ConsoleLogger.warn("  Current: v" + currentVersion);
        ConsoleLogger.warn("");
        ConsoleLogger.warn("  To install, type: /mp updatejar");
        ConsoleLogger.warn("  To ignore this update, do nothing.");
        ConsoleLogger.warn("===========================================");
        ConsoleLogger.warn("");
    }

    // =========================
    // 🔍 GITHUB CONTENTS API — Jar/
    // =========================

    /**
     * Данные о jar-файле из папки Jar/ на GitHub.
     */
    private static class JarFileInfo {
        final String name;          // "MC-Plugin-1.7.75.jar"
        final String version;       // "1.7.75"
        final String downloadUrl;   // raw.githubusercontent.com URL

        JarFileInfo(String name, String version, String downloadUrl) {
            this.name = name;
            this.version = version;
            this.downloadUrl = downloadUrl;
        }
    }

    /**
     * Запрашивает GitHub Contents API для папки {@code Jar/} и возвращает
     * информацию о самом новом jar-файле (с наибольшей версией).
     *
     * @return JarFileInfo или null если jar-файлов нет
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

        // Парсим JSON-массив
        JsonArray items;
        try {
            items = JsonParser.parseString(response.body()).getAsJsonArray();
        } catch (Exception e) {
            // Если ответ не массив — значит это объект с ошибкой или это не директория
            ConsoleLogger.warn("[Updater] Unexpected API response format");
            e.printStackTrace();
            return null;
        }

        if (items.isEmpty()) {
            return null;
        }

        // Ищем jar-файлы и выбираем самый новый по версии
        JarFileInfo best = null;
        int[] bestVersion = null;

        for (JsonElement elem : items) {
            JsonObject item = elem.getAsJsonObject();
            String type = item.get("type").getAsString();
            if (!"file".equals(type)) continue;

            String name = item.get("name").getAsString();
            if (!name.endsWith(".jar")) continue;

            // Извлекаем версию из имени "MC-Plugin-1.7.75.jar"
            Matcher m = JAR_FILE_PATTERN.matcher(name);
            if (!m.find()) continue;

            String versionStr = m.group(1);
            int[] versionInts = parseVersionToInts(versionStr);
            if (versionInts == null) continue;

            // Сравниваем с текущим лучшим
            if (best == null || compareVersions(versionInts, bestVersion) > 0) {
                String downloadUrl = item.get("download_url").getAsString();
                best = new JarFileInfo(name, versionStr, downloadUrl);
                bestVersion = versionInts;
            }
        }

        if (best != null) {
            // Кешируем для /mp updatejar
            cachedDownloadUrl = best.downloadUrl;
            cachedJarName = best.name;
            cachedJarVersion = best.version;

            ConsoleLogger.info("[Updater] Found latest jar: " + best.name
                    + " (v" + best.version + ")");
        }

        return best;
    }

    /**
     * Сравнивает два версионных массива.
     * @return положительное число если a > b, 0 если равны, отрицательное если a < b
     */
    private static int compareVersions(int[] a, int[] b) {
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) return a[i] - b[i];
        }
        return 0;
    }

    // =========================
    // 🔢 ПАРСИНГ И СРАВНЕНИЕ ВЕРСИЙ
    // =========================

    /**
     * Извлекает номер версии из строки.
     * Примеры: "1.8.23" → "1.8.23", "1.7" → "1.7.0", "v1.8.23" → "1.8.23".
     *
     * @param input строка, содержащая номер версии
     * @return строка версии "major.minor.patch" или null если не удалось распарсить
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
     * Сравнивает две версии по компонентам (major.minor.patch).
     *
     * @param jarVersion версия из jar-файла (например "1.8.23")
     * @param currentVersion текущая версия плагина (например "1.7.54")
     * @return true если jar новее current (т.е. есть обновление)
     */
    private static boolean isNewer(String jarVersion, String currentVersion) {
        int[] jar = parseVersionToInts(jarVersion);
        int[] cur = parseVersionToInts(currentVersion);
        if (jar == null || cur == null) return false;

        if (jar[0] != cur[0]) return jar[0] > cur[0];
        if (jar[1] != cur[1]) return jar[1] > cur[1];
        return jar[2] > cur[2];
    }

    /** Парсит "1.7.54" в int[]{1, 7, 54}. */
    private static int[] parseVersionToInts(String versionString) {
        Matcher m = VERSION_PATTERN.matcher(versionString);
        if (!m.find()) return null;
        int major = Integer.parseInt(m.group(1));
        int minor = Integer.parseInt(m.group(2));
        int patch = m.group(3) != null ? Integer.parseInt(m.group(3)) : 0;
        return new int[]{major, minor, patch};
    }

    // =========================
    // 💾 РАБОТА С БД
    // =========================

    /** Читает последнюю установленную версию jar из таблицы updater_state. */
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

    /** Сохраняет версию jar в таблицу updater_state. */
    private static void saveStoredTag(String version) {
        Connection con = DatabaseManager.getConnection();
        if (con == null) return;

        try {
            // Очищаем старый ключ от предыдущей системы (релизы)
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
    // 🗑 ОЧИСТКА ORPHANED ФАЙЛОВ
    // =========================
    private static void cleanupOrphanedFiles(File pluginDir, File currentJar) {
        File updateFile = new File(pluginDir, currentJar.getName() + ".update");
        try { Files.deleteIfExists(updateFile.toPath()); } catch (Exception ignored) {}

        File bakFile = new File(pluginDir, currentJar.getName() + ".bak");
        try { Files.deleteIfExists(bakFile.toPath()); } catch (Exception ignored) {}
    }

    // =========================
    // 🔍 /mp checkver — РУЧНАЯ ПРОВЕРКА ОБНОВЛЕНИЙ
    // =========================

    /**
     * Выполняет асинхронную проверку GitHub (папка Jar/) на наличие новых версий
     * и отправляет результат отправителю команды.
     */
    public static void checkOnly(CommandSender sender) {
        Main plugin = Main.getInstance();
        String senderName = sender instanceof Player ? ((Player) sender).getName() : "Console";
        ConsoleLogger.info("[Updater] Manual update check requested by " + senderName);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String currentVersion = plugin.getDescription().getVersion();
                String storedVersion = getStoredTag();

                // Шаг 1: получаем последний jar из Jar/
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

                // Отправляем результат на главном потоке
                final String finalJarName = latestJar.name;
                final String finalJarVer = jarVersion != null ? jarVersion : "<unparseable>";
                final String finalCurrentVer = currentVersion;
                final boolean finalIsNew = isNewRelease;
                final boolean finalHasVer = hasVersion;
                final boolean finalPending = isPendingRestart;

                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.header",
                            "<gold>=== <white>MC-Plugin — Update Check</white> ===")));
                    sender.sendMessage("");
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.plugin_version",
                            "<gray>Your version:</gray> <white>{version}</white>")
                            .replace("%version}", finalCurrentVer)));
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.latest_release",
                            "<gray>GitHub Jar/:</gray> <white>{name}</white>")
                            .replace("%name}", finalJarName)));

                    if (finalHasVer) {
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "update.release_version",
                                "<gray>Jar version:</gray> <white>{version}</white>")
                                .replace("%version}", finalJarVer)));
                    }

                    if (finalPending && finalIsNew) {
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "update.already_downloaded",
                                "<yellow>⟳</yellow> <gray>Update already downloaded!</gray> <white>{ver}</white> <gray>— restart server to apply.</gray>")
                                .replace("%ver}", finalJarVer)));
                    } else if (finalIsNew) {
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "update.update_available",
                                "<green>✨</green> <white>Update available!</white> <white>{jar}</white>")
                                .replace("%jar}", finalJarName)));
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "update.update_from_to",
                                "<gray>v{from} → v{to}</gray>")
                                .replace("%from}", finalCurrentVer)
                                .replace("%to}", finalJarVer)));

                        if (sender instanceof Player) {
                            TextComponent updateButton = new TextComponent(MessageUtil.legacy(
                                    MessagesManager.getString("update.install_button",
                                            "<dark_green>[<green>✔ Install Update</green><dark_green>]</dark_green>")));
                            updateButton.setClickEvent(new ClickEvent(
                                    ClickEvent.Action.RUN_COMMAND,
                                    "/mp updatejar"));
                            updateButton.setHoverEvent(new HoverEvent(
                                    HoverEvent.Action.SHOW_TEXT,
                                    new ComponentBuilder("§aClick to download and install update\n")
                                            .append("§7File: §f" + finalJarName + "\n")
                                            .append("§7Restart required after installation")
                                            .create()));
                            ((Player) sender).spigot().sendMessage(updateButton);

                            sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                    "update.install_hint",
                                    "<gray> or type </gray><white>/mp updatejar</white>")));
                        } else {
                            sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                    "update.install_console",
                                    "<gray>To install, type: </gray><white>/mp updatejar</white>")));
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
                                + "<gray>(v{current} ≥ v{jar})</gray>")
                                .replace("%current}", finalCurrentVer)
                                .replace("%jar}", finalJarVer)));
                    }

                    sender.sendMessage("");
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.header",
                            "<gold>=== <white>MC-Plugin — Update Check</white> ===")));
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
                            "<gray>GitHub did not respond within {seconds} seconds.</gray>")
                            .replace("%seconds}", String.valueOf(TIMEOUT_SECONDS))));
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
                            "<gray>{type}: {message}</gray>")
                            .replace("%type}", e.getClass().getSimpleName())
                            .replace("%message}", e.getMessage() != null ? e.getMessage() : "")));
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.install_error_console",
                            "<gray>Stack trace in server console.</gray>")));
                });
            }
        });
    }

    // =========================
    // 📥 /mp updatejar — СКАЧАТЬ И УСТАНОВИТЬ ОБНОВЛЕНИЕ
    // =========================

    /**
     * Скачивает последний JAR из папки {@code Jar/} на GitHub, заменяет текущий.
     * После успешной замены сохраняет в БД версию jar.
     */
    public static void downloadAndReplace(CommandSender sender) {
        Main plugin = Main.getInstance();
        String senderName = sender instanceof Player ? ((Player) sender).getName() : "Console";
        ConsoleLogger.info("[Updater] /mp updatejar requested by " + senderName);

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

                // Используем кеш если есть, иначе фетчим API
                String downloadUrl;
                String jarName;
                String jarVersion;

                if (!cachedDownloadUrl.isEmpty()) {
                    downloadUrl = cachedDownloadUrl;
                    jarName = cachedJarName;
                    jarVersion = cachedJarVersion;
                    ConsoleLogger.info("[Updater] Using cached jar info: " + jarName);
                } else {
                    // Фетчим свежие данные с GitHub
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

                // Проверяем — не тот ли самый jar уже установлен?
                String storedVersion = getStoredTag();
                if (jarVersion != null && jarVersion.equals(storedVersion)) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "update.already_installed",
                                "<green>✔</green> <green>This version is already installed! "
                                + "(</green><white>{ver}</white><green>)</green>")
                                .replace("%ver}", jarVersion)));
                    });
                    return;
                }

                // Статус: загрузка
                final String finalJarName = jarName;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.downloading_release",
                            "<yellow>⟳</yellow> <gray>Downloading update</gray> <white>{name}</white><gray>...</gray>")
                            .replace("%name}", finalJarName)));
                });

                // Скачивание JAR
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
                                "<red>❌ Download error: HTTP {status}</red>")
                                .replace("%status}", String.valueOf(downloadResponse.statusCode()))));
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

                // Замена JAR
                boolean replaced = replaceJar(plugin, currentJar, tempFile, jarName);

                if (replaced) {
                    // Сохраняем версию jar (чтобы не перекачивать при следующей проверке)
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
                                "<gray>File:</gray> <white>{name}</white>")
                                .replace("%name}", finalJarName)));
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "update.install_size",
                                "<gray>Downloaded:</gray> <white>{size} KB</white>")
                                .replace("%size}", String.valueOf(downloadedKB))));
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
                ConsoleLogger.error("[Updater] /mp updatejar failed!");
                e.printStackTrace();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.install_error",
                            "<red>❌ Error downloading update!</red>")));
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.install_error_detail",
                            "<gray>{type}: {message}</gray>")
                            .replace("%type}", e.getClass().getSimpleName())
                            .replace("%message}", e.getMessage() != null ? e.getMessage() : "")));
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.install_error_console",
                            "<gray>Stack trace in server console.</gray>")));
                });
            }
        });
    }

    /** @return true если замена прошла успешно (включая fallback) */
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

        // ШАГ 1: Backup
        boolean backupDone = false;
        try {
            Files.move(targetPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            ConsoleLogger.info("[Updater] Backed up current JAR");
            backupDone = true;
        } catch (Exception e) {
            ConsoleLogger.warn("[Updater] Backup failed (non-critical): " + e.getMessage());
            e.printStackTrace();
        }

        // ШАГ 2: Перемещаем новый JAR на место текущего
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
    // 🔄 FALLBACK: поместить JAR в папку plugins когда replace не удался
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
