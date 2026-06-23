package com.mcplugin.mechanics.features.updater;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.config.MessagesManager;
import com.mcplugin.infrastructure.database.DatabaseManager;
import com.mcplugin.infrastructure.util.MessageUtil;

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
 * Auto-updater: сравнивает номер версии из названия GitHub-релиза
 * с текущей версией плагина (plugin.yml → getDescription().getVersion()).
 * <p>
 * Логика:
 * <ol>
 *   <li>Читаем текущую версию плагина (например "1.7.54");</li>
 *   <li>Запрашиваем GitHub API {@code /releases?per_page=1} — получаем тег последнего релиза;</li>
 *   <li>Извлекаем номер версии из тега (например "v1.8.23" → "1.8.23");</li>
 *   <li>Сравниваем по компонентам (major.minor.commits):</li>
 *   <li>Если текущая >= релизной → UP_TO_DATE (мы новее или такие же);</li>
 *   <li>Если текущая < релизной → UPDATE_AVAILABLE;</li>
 *   <li>После успешной загрузки сохраняем в БД тег релиза (чтобы не перекачивать).</li>
 * </ol>
 */
public class UpdateChecker {

    // =========================
    // ⚙ КОНФИГУРАЦИЯ
    // =========================
    private static final String GITHUB_OWNER = "Minecraft337";
    private static final String GITHUB_REPO = "MC-Plugin";
    private static final String RELEASES_API_URL = "https://api.github.com/repos/"
            + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases?per_page=1";
    private static final String USER_AGENT = "MC-Plugin-Updater";
    private static final int TIMEOUT_SECONDS = 15;

    /** Regex для извлечения major.minor.commits из тега релиза. */
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

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
    private static volatile String latestTag = "";
    private static volatile String errorMessage = "";

    // Кеш для /mp updatejar — чтобы не дёргать API повторно
    private static volatile String cachedDownloadUrl = "";
    private static volatile String cachedReleaseName = "";
    private static volatile String cachedGithubTag = "";

    public static UpdateStatus getStatus() { return status; }
    public static String getLatestTag() { return latestTag; }
    public static String getErrorMessage() { return errorMessage; }

    // =========================
    // ЗАПУСК ПРОВЕРКИ (вызывается из Main.onEnable)
    // =========================
    public static void checkAsync() {
        Main plugin = Main.getInstance();
        plugin.getLogger().info("[Updater] Checking for updates...");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                performCheck(plugin);
            } catch (Exception e) {
                status = UpdateStatus.CHECK_FAILED;
                errorMessage = e.getMessage();
                plugin.getLogger().warning("[Updater] Check failed: " + e.getMessage());
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
        String storedTag = getStoredTag();
        plugin.getLogger().info("[Updater] Current version: " + currentVersion);
        plugin.getLogger().info("[Updater] Last installed release: "
                + (storedTag.isEmpty() ? "<none>" : storedTag));

        // ════════════════════════════════════════
        // 2. HTTP-запрос к GitHub API — последний релиз
        // ════════════════════════════════════════
        String githubTag = fetchLatestReleaseTag(plugin);
        if (githubTag == null) {
            plugin.getLogger().info("[Updater] No releases found — up to date.");
            status = UpdateStatus.UP_TO_DATE;
            return;
        }

        // ════════════════════════════════════════
        // 3. Извлекаем номер версии из тега релиза
        // ════════════════════════════════════════
        String releaseVersion = parseVersion(githubTag);
        if (releaseVersion == null) {
            plugin.getLogger().warning("[Updater] Cannot parse version from tag: " + githubTag);
            status = UpdateStatus.CHECK_FAILED;
            return;
        }

        plugin.getLogger().info("[Updater] GitHub latest release: "
                + githubTag + " (version: " + releaseVersion + ")");

        // ════════════════════════════════════════
        // 4. Сравниваем версии
        // ════════════════════════════════════════
        if (!isNewer(releaseVersion, currentVersion)) {
            // Текущая >= релизной → мы не старше
            plugin.getLogger().info("[Updater] Up to date (current: "
                    + currentVersion + " >= release: " + releaseVersion + ")");
            latestTag = githubTag;
            status = UpdateStatus.UP_TO_DATE;
            return;
        }

        // ════════════════════════════════════════
        // 5. Релиз новее → проверяем, не установлен ли уже
        // ════════════════════════════════════════
        if (githubTag.equals(storedTag)) {
            // Уже скачан, ждёт рестарта
            plugin.getLogger().info("[Updater] Release " + githubTag
                    + " already downloaded — restart required.");
            latestTag = githubTag;
            status = UpdateStatus.UPDATE_DOWNLOADED;
            return;
        }

        // ════════════════════════════════════════
        // 6. Обновление доступно!
        // ════════════════════════════════════════
        latestTag = githubTag;
        status = UpdateStatus.UPDATE_AVAILABLE;

        plugin.getLogger().warning("");
        plugin.getLogger().warning("===========================================");
        plugin.getLogger().warning("  [UPDATE AVAILABLE] " + cachedReleaseName);
        plugin.getLogger().warning("  Release: " + githubTag + " (v" + releaseVersion + ")");
        plugin.getLogger().warning("  Current: v" + currentVersion);
        plugin.getLogger().warning("");
        plugin.getLogger().warning("  To install, type: /mp updatejar");
        plugin.getLogger().warning("  To ignore this update, do nothing.");
        plugin.getLogger().warning("===========================================");
        plugin.getLogger().warning("");
    }

    // =========================
    // 🔍 API HELPERS
    // =========================

    /** Запрашивает GitHub API и возвращает тег последнего релиза + кеширует URL для скачивания. */
    private static String fetchLatestReleaseTag(Main plugin) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(RELEASES_API_URL))
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            plugin.getLogger().warning("[Updater] GitHub Releases API returned HTTP "
                    + response.statusCode());
            status = UpdateStatus.CHECK_FAILED;
            return null;
        }

        JsonArray releases = JsonParser.parseString(response.body()).getAsJsonArray();
        if (releases.isEmpty()) {
            // Нет релизов — не ошибка, просто нечего скачивать
            plugin.getLogger().info("[Updater] No releases found in GitHub repository");
            return null;
        }

        JsonObject release = releases.get(0).getAsJsonObject();
        String tag = release.get("tag_name").getAsString();
        String releaseName = release.has("name") && !release.get("name").isJsonNull()
                ? release.get("name").getAsString() : tag;

        // Ищем JAR и кешируем
        String downloadUrl = findJarAsset(release);
        if (downloadUrl != null) {
            cachedDownloadUrl = downloadUrl;
            cachedReleaseName = releaseName;
            cachedGithubTag = tag;
        }

        plugin.getLogger().info("[Updater] GitHub latest release: " + tag);
        return tag;
    }

    // =========================
    // 🔢 ПАРСИНГ И СРАВНЕНИЕ ВЕРСИЙ
    // =========================

    /**
     * Извлекает номер версии из тега релиза.
     * Примеры: "v1.8.23" → "1.8.23", "Hotfix-1.7.1" → "1.7.1",
     *          "release-2.0.0" → "2.0.0", "26.2" → "26.2.0".
     *
     * @param tag тег GitHub-релиза (например "v1.8.23")
     * @return строка версии "major.minor.patch" или null если не удалось распарсить
     */
    private static String parseVersion(String tag) {
        if (tag == null || tag.isEmpty()) return null;
        Matcher m = VERSION_PATTERN.matcher(tag);
        if (!m.find()) return null;
        int major = Integer.parseInt(m.group(1));
        int minor = Integer.parseInt(m.group(2));
        int patch = m.group(3) != null ? Integer.parseInt(m.group(3)) : 0;
        return major + "." + minor + "." + patch;
    }

    /**
     * Сравнивает две версии по компонентам (major.minor.patch).
     *
     * @param releaseVersion версия из GitHub-релиза (например "1.8.23")
     * @param currentVersion текущая версия плагина (например "1.7.54")
     * @return true если release новее current (т.е. есть обновление)
     */
    private static boolean isNewer(String releaseVersion, String currentVersion) {
        int[] rel = parseVersionToInts(releaseVersion);
        int[] cur = parseVersionToInts(currentVersion);
        if (rel == null || cur == null) return false;

        if (rel[0] != cur[0]) return rel[0] > cur[0];
        if (rel[1] != cur[1]) return rel[1] > cur[1];
        return rel[2] > cur[2];
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

    /** Читает последний установленный тег релиза из таблицы updater_state. */
    private static String getStoredTag() {
        Connection con = DatabaseManager.getConnection();
        if (con == null) return "";

        try (PreparedStatement ps = con.prepareStatement(
                "SELECT value FROM updater_state WHERE key = 'installed_tag'")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("value");
            }
        } catch (Exception e) {
            Main.getInstance().getLogger().fine("[Updater] DB read tag error: " + e.getMessage());
        }
        return "";
    }

    /** Сохраняет тег релиза в таблицу updater_state. */
    private static void saveStoredTag(String tag) {
        Connection con = DatabaseManager.getConnection();
        if (con == null) return;

        try {
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE updater_state SET value = ? WHERE key = 'installed_tag'")) {
                ps.setString(1, tag);
                if (ps.executeUpdate() == 0) {
                    try (PreparedStatement insert = con.prepareStatement(
                            "INSERT INTO updater_state (key, value) VALUES ('installed_tag', ?)")) {
                        insert.setString(1, tag);
                        insert.executeUpdate();
                    }
                }
            }
        } catch (Exception e) {
            Main.getInstance().getLogger().warning(
                    "[Updater] Failed to save tag to DB: " + e.getMessage());
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
    // 🔍 ПОИСК JAR В АССЕТАХ РЕЛИЗА
    // =========================
    private static String findJarAsset(JsonObject release) {
        JsonArray assets = release.getAsJsonArray("assets");
        if (assets == null || assets.isEmpty()) return null;

        // Приоритет: .jar с именем репозитория
        for (JsonElement elem : assets) {
            JsonObject asset = elem.getAsJsonObject();
            String name = asset.get("name").getAsString();
            if (name.toLowerCase().contains(GITHUB_REPO.toLowerCase())
                    && name.endsWith(".jar")) {
                return asset.get("browser_download_url").getAsString();
            }
        }
        // Fallback: любой .jar
        for (JsonElement elem : assets) {
            JsonObject asset = elem.getAsJsonObject();
            String name = asset.get("name").getAsString();
            if (name.endsWith(".jar")) {
                return asset.get("browser_download_url").getAsString();
            }
        }
        return null;
    }

    // =========================
    // 🔍 /mp checkver или /mp checkupdates — РУЧНАЯ ПРОВЕРКА ОБНОВЛЕНИЙ
    // =========================

    /**
     * Выполняет асинхронную проверку GitHub на наличие новых релизов
     * и отправляет результат отправителю команды (игроку или консоли).
     * Сравнивает номера версий: если релиз новее текущей — предлагает обновиться.
     */
    public static void checkOnly(CommandSender sender) {
        Main plugin = Main.getInstance();
        String senderName = sender instanceof Player ? ((Player) sender).getName() : "Console";
        plugin.getLogger().info("[Updater] Manual update check requested by " + senderName);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String currentVersion = plugin.getDescription().getVersion();
                String storedTag = getStoredTag();

                // Шаг 1: получаем последний релиз
                String githubTag = fetchLatestReleaseTag(plugin);
                if (githubTag == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "update.check_error", "<red>❌ Error checking for updates!</red>")));
                    });
                    return;
                }

                // Шаг 2: парсим версию релиза
                String releaseVersion = parseVersion(githubTag);
                boolean hasVersion = releaseVersion != null;
                boolean isNewRelease = hasVersion && isNewer(releaseVersion, currentVersion);
                boolean isPendingRestart = githubTag.equals(storedTag);

                // Отправляем результат на главном потоке
                final String finalTag = githubTag;
                final String finalReleaseName = !cachedReleaseName.isEmpty()
                        ? cachedReleaseName : finalTag;
                final String finalReleaseVer = releaseVersion != null
                        ? releaseVersion : "<unparseable>";
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
                            .replace("{version}", finalCurrentVer)));
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.latest_release",
                            "<gray>Latest release:</gray> <white>{tag}</white>")
                            .replace("{tag}", finalTag)));

                    if (finalHasVer) {
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "update.release_version",
                                "<gray>Release version:</gray> <white>{version}</white>")
                                .replace("{version}", finalReleaseVer)));
                    }

                    if (finalPending && finalIsNew) {
                        // Уже скачан, ждёт рестарта
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "update.already_downloaded",
                                "<yellow>⟳</yellow> <gray>Update already downloaded!</gray> <white>{tag}</white> <gray>— restart server to apply.</gray>")
                                .replace("{tag}", finalTag)));
                    } else if (finalIsNew) {
                        // Доступно обновление
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "update.update_available",
                                "<green>✨</green> <white>Update available!</white> <white>{release}</white>")
                                .replace("{release}", finalReleaseName)));
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "update.update_from_to",
                                "<gray>v{from} → v{to}</gray>")
                                .replace("{from}", finalCurrentVer)
                                .replace("{to}", finalReleaseVer)));

                        // Install button for players
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
                                            .append("§7Release: §f" + finalReleaseName + "\n")
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
                                "<yellow>⚠</yellow> <gray>Cannot parse version from release tag. Skipping.</gray>")));
                    } else {
                        // Up to date or newer
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "update.up_to_date",
                                "<green>✔</green> <green>All up to date!</green> "
                                + "<gray>(v{current} ≥ v{release})</gray>")
                                .replace("{current}", finalCurrentVer)
                                .replace("{release}", finalReleaseVer)));
                    }

                    sender.sendMessage("");
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.header",
                            "<gold>=== <white>MC-Plugin — Update Check</white> ===")));
                });

            } catch (java.net.UnknownHostException e) {
                plugin.getLogger().warning("[Updater] Manual check failed: DNS resolution error");
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
                plugin.getLogger().warning("[Updater] Manual check failed: Connection timeout");
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.timeout_error",
                            "<red>❌ Connection timeout with GitHub!</red>")));
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.timeout_hint",
                            "<gray>GitHub did not respond within {seconds} seconds.</gray>")
                            .replace("{seconds}", String.valueOf(TIMEOUT_SECONDS))));
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.timeout_retry_hint",
                            "<gray>Check internet connection or try again later.</gray>")));
                });
            } catch (Exception e) {
                plugin.getLogger().warning("[Updater] Manual check failed: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.check_error",
                            "<red>❌ Error checking for updates!</red>")));
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.install_error_detail",
                            "<gray>{type}: {message}</gray>")
                            .replace("{type}", e.getClass().getSimpleName())
                            .replace("{message}", e.getMessage() != null ? e.getMessage() : "")));
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
     * Скачивает последний JAR с GitHub, заменяет текущий.
     * При ошибке — fallback в папку plugins + стектрейс в консоль.
     * После успешной замены сохраняет в БД тег релиза.
     */
    public static void downloadAndReplace(CommandSender sender) {
        Main plugin = Main.getInstance();
        String senderName = sender instanceof Player ? ((Player) sender).getName() : "Console";
        plugin.getLogger().info("[Updater] /mp updatejar requested by " + senderName);

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
                String githubTag;

                if (!cachedDownloadUrl.isEmpty() && !cachedGithubTag.isEmpty()) {
                    downloadUrl = cachedDownloadUrl;
                    githubTag = cachedGithubTag;
                    plugin.getLogger().info("[Updater] Using cached release: " + githubTag);
                } else {
                    // Фетчим свежие данные с GitHub
                    githubTag = fetchLatestReleaseTag(plugin);
                    if (githubTag == null) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                    "update.no_release_info",
                                    "<red>❌ Could not get latest release info!</red>")));
                        });
                        return;
                    }
                    downloadUrl = cachedDownloadUrl;
                    if (downloadUrl == null || downloadUrl.isEmpty()) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                    "update.no_jar_in_release",
                                    "<red>❌ No JAR file found in release </red><yellow>{tag}</yellow><red>!</red>")
                                    .replace("{tag}", githubTag)));
                        });
                        return;
                    }
                }

                // Проверяем — не тот ли самый тег уже установлен?
                String storedTag = getStoredTag();
                if (githubTag.equals(storedTag)) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "update.already_installed",
                                "<green>✔</green> <green>This version is already installed! "
                                + "(</green><white>{tag}</white><green>)</green>")
                                .replace("{tag}", githubTag)));
                    });
                    return;
                }

                // Статус: загрузка
                final String finalTag = githubTag;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.downloading_release",
                            "<yellow>⟳</yellow> <gray>Downloading update</gray> <white>{tag}</white><gray>...</gray>")
                            .replace("{tag}", finalTag)));
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
                                .replace("{status}", String.valueOf(downloadResponse.statusCode()))));
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
                boolean replaced = replaceJar(plugin, currentJar, tempFile, finalTag);

                if (replaced) {
                    // Сохраняем тег релиза (чтобы не перекачивать при следующей проверке)
                    saveStoredTag(finalTag);

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "update.install_success_header",
                                "<gold>=== <green>Update Installed!</green> ===")));
                        sender.sendMessage("");
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "update.install_release",
                                "<gray>Release:</gray> <white>{tag}</white>")
                                .replace("{tag}", finalTag)));
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "update.install_size",
                                "<gray>Downloaded:</gray> <white>{size} KB</white>")
                                .replace("{size}", String.valueOf(downloadedKB))));
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
                // СтекТрейс в консоль
                plugin.getLogger().severe("[Updater] /mp updatejar failed!");
                e.printStackTrace();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.install_error",
                            "<red>❌ Error downloading update!</red>")));
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.install_error_detail",
                            "<gray>{type}: {message}</gray>")
                            .replace("{type}", e.getClass().getSimpleName())
                            .replace("{message}", e.getMessage() != null ? e.getMessage() : "")));
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "update.install_error_console",
                            "<gray>Stack trace in server console.</gray>")));
                });
            }
        });
    }

    /** @return true если замена прошла успешно (включая fallback) */
    private static boolean replaceJar(Main plugin, File currentJar, File updateFile, String tagName) {
        if (currentJar == null || !currentJar.exists()) {
            plugin.getLogger().warning("[Updater] Cannot find current JAR file");
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
            plugin.getLogger().info("[Updater] Backed up current JAR");
            backupDone = true;
        } catch (Exception e) {
            plugin.getLogger().warning("[Updater] Backup failed (non-critical): " + e.getMessage());
        }

        // ШАГ 2: Перемещаем новый JAR на место текущего
        try {
            Files.move(updatePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            plugin.getLogger().severe("[Updater] Failed to replace JAR: " + e.getMessage());

            // Try to restore backup first
            boolean backupRestored = false;
            if (backupDone) {
                try {
                    Files.move(backupPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().info("[Updater] Backup restored");
                    backupRestored = true;
                } catch (Exception restoreErr) {
                    plugin.getLogger().severe("[Updater] Could not restore backup! "
                            + "Manual recovery needed. Backup at: " + backupPath);
                }
            }

            if (backupRestored) {
                status = UpdateStatus.UPDATE_FAILED;
                errorMessage = "Could not replace JAR file: " + e.getMessage();
                return false;
            }

            // FALLBACK: поместить скачанный JAR в папку plugins
            boolean fallbackSuccess = placeUpdateInPluginsFolder(plugin, updateFile, currentJar, tagName);
            if (fallbackSuccess) {
                return true;
            }

            status = UpdateStatus.UPDATE_FAILED;
            errorMessage = "Could not replace JAR file: " + e.getMessage();
            return false;
        }

        // УСПЕХ — прямой replace сработал
        try { Files.deleteIfExists(backupPath); } catch (Exception ignored) {}

        status = UpdateStatus.UPDATE_DOWNLOADED;
        plugin.getLogger().info("");
        plugin.getLogger().info("===========================================");
        plugin.getLogger().info("  [UPDATE INSTALLED]");
        plugin.getLogger().info("  Release: " + tagName);
        plugin.getLogger().info("");
        plugin.getLogger().info("  Restart server to apply the update.");
        plugin.getLogger().info("===========================================");
        plugin.getLogger().info("");
        return true;
    }

    // =========================
    // 🔄 FALLBACK: поместить JAR в папку plugins когда replace не удался
    // =========================

    private static boolean placeUpdateInPluginsFolder(Main plugin, File updateFile,
                                                       File currentJar, String tagName) {
        Path updatePath = updateFile.toPath();
        File pluginDir = currentJar.getParentFile();
        Path targetPath = currentJar.toPath();

        // Стратегия 1: попробовать Files.copy вместо Files.move
        if (!Files.exists(targetPath)) {
            try {
                Files.copy(updatePath, targetPath);
                try { Files.deleteIfExists(updatePath); } catch (Exception ignored) {}
                plugin.getLogger().info("[Updater] JAR copied to plugins folder (copy fallback)");
                status = UpdateStatus.UPDATE_DOWNLOADED;
                logFallbackSuccess(plugin, tagName);
                return true;
            } catch (Exception copyErr) {
                plugin.getLogger().warning("[Updater] Copy fallback failed: " + copyErr.getMessage());
            }
        }

        // Стратегия 2: переименовать .update в узнаваемое имя в папке plugins
        String fallbackName = currentJar.getName().replace(".jar", "") + "-NEW.jar";
        File fallbackFile = new File(pluginDir, fallbackName);
        try {
            Files.move(updatePath, fallbackFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().warning("");
            plugin.getLogger().warning("===========================================");
            plugin.getLogger().warning("  [UPDATE DOWNLOADED — MANUAL STEP REQUIRED]");
            plugin.getLogger().warning("  Release: " + tagName);
            plugin.getLogger().warning("");
            plugin.getLogger().warning("  New JAR placed at: plugins/" + fallbackName);
            plugin.getLogger().warning("");
            plugin.getLogger().warning("  To apply: stop server, then:");
            plugin.getLogger().warning("    1) Delete old JAR: " + currentJar.getName());
            plugin.getLogger().warning("    2) Rename " + fallbackName + " -> " + currentJar.getName());
            plugin.getLogger().warning("    3) Delete " + currentJar.getName() + ".bak");
            plugin.getLogger().warning("    4) Start server");
            plugin.getLogger().warning("===========================================");
            plugin.getLogger().warning("");
            status = UpdateStatus.UPDATE_DOWNLOADED;
            return true;
        } catch (Exception renameErr) {
            plugin.getLogger().severe("[Updater] All fallback strategies failed: " + renameErr.getMessage());
            plugin.getLogger().severe("[Updater] Update file left at: " + updateFile.getAbsolutePath());
            plugin.getLogger().severe("[Updater] Manual recovery: stop server, move this file to plugins/"
                    + currentJar.getName());
        }

        return false;
    }

    private static void logFallbackSuccess(Main plugin, String tagName) {
        plugin.getLogger().warning("");
        plugin.getLogger().warning("===========================================");
        plugin.getLogger().warning("  [UPDATE READY — RESTART REQUIRED]");
        plugin.getLogger().warning("  Release: " + tagName);
        plugin.getLogger().warning("");
        plugin.getLogger().warning("  New JAR placed in plugins folder.");
        plugin.getLogger().warning("  Restart server to apply the update.");
        plugin.getLogger().warning("===========================================");
        plugin.getLogger().warning("");
    }
}
