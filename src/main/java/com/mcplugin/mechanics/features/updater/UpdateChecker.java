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

/**
 * Auto-updater: проверяет последний коммит на GitHub, а не номер версии.
 * <p>
 * Логика:
 * <ol>
 *   <li>Читаем из БД последний проверенный SHA коммита и последний установленный тег релиза;</li>
 *   <li>Запрашиваем GitHub API {@code /commits/main} — получаем SHA последнего коммита;</li>
 *   <li>Если SHA совпадает с сохранённым — UP_TO_DATE;</li>
 *   <li>Если SHA отличается — запрашиваем {@code /releases/latest} — получаем тег релиза;</li>
 *   <li>Если тег релиза отличается от установленного — новый коммит + новый релиз → UPDATE_AVAILABLE;</li>
 *   <li>Если тег релиза совпадает — есть новые коммиты, но без релиза → не показываем обновление;</li>
 *   <li>После успешной загрузки сохраняем в БД и новый SHA, и новый тег.</li>
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
    private static final String COMMITS_API_URL = "https://api.github.com/repos/"
            + GITHUB_OWNER + "/" + GITHUB_REPO + "/commits?per_page=1";
    private static final String USER_AGENT = "MC-Plugin-Updater";
    private static final int TIMEOUT_SECONDS = 15;

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
        // 1. Читаем последний SHA и тег из БД
        // ════════════════════════════════════════
        String storedSha = getStoredSha();
        String storedTag = getStoredTag();
        plugin.getLogger().info("[Updater] Last known commit: "
                + (storedSha.isEmpty() ? "<none>" : storedSha.substring(0, Math.min(7, storedSha.length())) + "..."));
        plugin.getLogger().info("[Updater] Last installed release: "
                + (storedTag.isEmpty() ? "<none>" : storedTag));

        // ════════════════════════════════════════
        // 2. HTTP-запрос к GitHub API — проверяем последний коммит
        // ════════════════════════════════════════
        String latestCommitSha = fetchLatestCommitSha(plugin);
        if (latestCommitSha == null) return; // CHECK_FAILED уже установлен

        // ════════════════════════════════════════
        // 3. Сравниваем SHA коммита
        // ════════════════════════════════════════
        if (latestCommitSha.equals(storedSha)) {
            plugin.getLogger().info("[Updater] No new commits (SHA: "
                    + latestCommitSha.substring(0, Math.min(7, latestCommitSha.length())) + "...)");
            status = UpdateStatus.UP_TO_DATE;
            return;
        }

        plugin.getLogger().info("[Updater] New commit detected: "
                + latestCommitSha.substring(0, Math.min(7, latestCommitSha.length())) + "...");

        // ════════════════════════════════════════
        // 4. SHA изменился — проверяем, есть ли новый релиз
        // ════════════════════════════════════════
        String githubTag = fetchLatestReleaseTag(plugin);
        if (githubTag == null) {
            // fetchLatestReleaseTag мог установить CHECK_FAILED (HTTP ошибка)
            // или не найти релизов — в любом случае не можем обновиться
            if (status != UpdateStatus.CHECK_FAILED) {
                plugin.getLogger().info("[Updater] New commits found (SHA: "
                        + latestCommitSha.substring(0, Math.min(7, latestCommitSha.length())) + "...)"
                        + " but no releases available — update not announced.");
                latestTag = "";
                status = UpdateStatus.UP_TO_DATE;
            }
            return;
        }

        // Релиз найден — download URL уже закеширован в fetchLatestReleaseTag
        if (cachedDownloadUrl.isEmpty()) {
            plugin.getLogger().warning("[Updater] No JAR asset found in release " + githubTag + " — skipping");
            status = UpdateStatus.UPDATE_FAILED;
            latestTag = githubTag;
            return;
        }

        // ════════════════════════════════════════
        // 5. Сравниваем тег релиза с установленным
        // ════════════════════════════════════════
        if (githubTag.equals(storedTag)) {
            plugin.getLogger().info("[Updater] New commits found but release tag unchanged (" + githubTag
                    + ") — no update announced without a new release.");
            latestTag = githubTag;
            status = UpdateStatus.UP_TO_DATE;
            return;
        }

        // ════════════════════════════════════════
        // 6. Новый коммит + новый релиз — обновление доступно!
        // ════════════════════════════════════════
        plugin.getLogger().info("[Updater] New release detected: "
                + githubTag + " (was: " + (storedTag.isEmpty() ? "<none>" : storedTag) + ")");

        // Данные для скачивания уже закешированы в fetchLatestReleaseTag
        // (cachedDownloadUrl, cachedReleaseName, cachedGithubTag)
        cachedGithubTag = githubTag;
        latestTag = githubTag;
        status = UpdateStatus.UPDATE_AVAILABLE;

        plugin.getLogger().warning("");
        plugin.getLogger().warning("===========================================");
        plugin.getLogger().warning("  [UPDATE AVAILABLE] " + cachedReleaseName);
        plugin.getLogger().warning("  Release: " + githubTag);
        plugin.getLogger().warning("");
        plugin.getLogger().warning("  To install, type: /mp updatejar");
        plugin.getLogger().warning("  To ignore this update, do nothing.");
        plugin.getLogger().warning("===========================================");
        plugin.getLogger().warning("");
    }

    // =========================
    // 🔍 API HELPERS
    // =========================

    /** Запрашивает GitHub API и возвращает SHA последнего коммита. */
    private static String fetchLatestCommitSha(Main plugin) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(COMMITS_API_URL))
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            plugin.getLogger().warning("[Updater] GitHub Commits API returned HTTP "
                    + response.statusCode());
            status = UpdateStatus.CHECK_FAILED;
            return null;
        }

        JsonArray commits = JsonParser.parseString(response.body()).getAsJsonArray();
        if (commits.isEmpty()) {
            plugin.getLogger().warning("[Updater] No commits found in GitHub repository");
            status = UpdateStatus.CHECK_FAILED;
            return null;
        }

        JsonObject commit = commits.get(0).getAsJsonObject();
        String sha = commit.get("sha").getAsString();
        String message = "";
        try {
            message = commit.getAsJsonObject("commit").get("message").getAsString();
            // Берём только первую строку
            int nl = message.indexOf('\n');
            if (nl > 0) message = message.substring(0, nl);
        } catch (Exception ignored) {}

        plugin.getLogger().info("[Updater] GitHub latest commit: "
                + sha.substring(0, Math.min(7, sha.length())) + "... "
                + (message.isEmpty() ? "" : "\"" + message + "\""));
        return sha;
    }

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
    // 💾 РАБОТА С БД
    // =========================

    /** Читает последний SHA коммита из таблицы updater_state. */
    private static String getStoredSha() {
        Connection con = DatabaseManager.getConnection();
        if (con == null) return "";

        try (PreparedStatement ps = con.prepareStatement(
                "SELECT value FROM updater_state WHERE key = 'latest_commit_sha'")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("value");
            }
        } catch (Exception e) {
            Main.getInstance().getLogger().fine("[Updater] DB read sha error: " + e.getMessage());
        }
        return "";
    }

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

    /** Сохраняет SHA коммита в таблицу updater_state. */
    private static void saveStoredSha(String sha) {
        Connection con = DatabaseManager.getConnection();
        if (con == null) return;

        try {
            // UPSERT: пытаемся обновить, если нет — вставляем
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE updater_state SET value = ? WHERE key = 'latest_commit_sha'")) {
                ps.setString(1, sha);
                if (ps.executeUpdate() == 0) {
                    try (PreparedStatement insert = con.prepareStatement(
                            "INSERT INTO updater_state (key, value) VALUES ('latest_commit_sha', ?)")) {
                        insert.setString(1, sha);
                        insert.executeUpdate();
                    }
                }
            }
        } catch (Exception e) {
            Main.getInstance().getLogger().warning(
                    "[Updater] Failed to save commit SHA to DB: " + e.getMessage());
        }
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
     * Выполняет асинхронную проверку GitHub на наличие новых коммитов/релизов
     * и отправляет результат отправителю команды (игроку или консоли).
     * Если обновление доступно — предлагает ввести /mp updatejar.
     */
    public static void checkOnly(CommandSender sender) {
        Main plugin = Main.getInstance();
        String senderName = sender instanceof Player ? ((Player) sender).getName() : "Console";
        plugin.getLogger().info("[Updater] Manual update check requested by " + senderName);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String storedSha = getStoredSha();
                String storedTag = getStoredTag();
                String pluginVersion = plugin.getDescription().getVersion();

                // Шаг 1: проверяем последний коммит
                String latestCommitSha = fetchLatestCommitSha(plugin);
                if (latestCommitSha == null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.check_error", "<red>❌ Error checking for updates!</red>")));
                });
                    return;
                }

                boolean hasNewCommits = !latestCommitSha.equals(storedSha);
                String shortSha = latestCommitSha.substring(0, Math.min(7, latestCommitSha.length()));

                // Шаг 2: проверяем последний релиз
                String githubTag = fetchLatestReleaseTag(plugin);
                boolean hasRelease = githubTag != null;
                boolean isNewRelease = hasRelease && !githubTag.equals(storedTag);
                boolean isFirstRun = storedSha.isEmpty() && storedTag.isEmpty();

                // Отправляем результат на главном потоке
                final String finalSha = shortSha;
                final String finalTag = githubTag != null ? githubTag : "<none>";
                final String finalReleaseName = !cachedReleaseName.isEmpty() ? cachedReleaseName : finalTag;

                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.header", "<gold>=== <white>MC-Plugin — Update Check</white> ===")));
                    sender.sendMessage("");
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.plugin_version", "<gray>Plugin version:</gray> <white>{version}</white>").replace("{version}", pluginVersion)));
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.latest_commit", "<gray>Latest commit:</gray> <white>{sha}</white>").replace("{sha}", finalSha)));
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.latest_release", "<gray>Latest release:</gray> <white>{tag}</white>").replace("{tag}", finalTag)));

                    if (!hasRelease) {
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.no_releases", "<red>⚠ No releases in the repository!</red>")));
                    } else if (isFirstRun) {
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.first_run", "<yellow>ℹ</yellow> <gray>First run check.</gray>")));
                        if (isNewRelease) {
                            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.update_available", "<green>✨</green> <white>Update available!</white> <white>{release}</white>").replace("{release}", finalReleaseName)));
                        }
                    } else if (isNewRelease) {
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.update_available", "<green>✨</green> <white>Update available!</white> <white>{release}</white>").replace("{release}", finalReleaseName)));
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.update_from_to", "<gray>Was:</gray> <white>{from}</white> <gray>→ Now:</gray> <green>{to}</green>")
                                .replace("{from}", storedTag.isEmpty() ? "<none>" : storedTag)
                                .replace("{to}", finalTag)));
                    } else if (hasNewCommits) {
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.new_commits_no_release", "<yellow>ℹ</yellow> <gray>New commits found, but no new release yet.</gray>")));
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.wait_for_release", "<gray>Wait for the next release.</gray>")));
                    } else {
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.up_to_date", "<green>✔</green> <green>All up to date! Latest commit already installed.</green>")));
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.header", "<gold>=== <white>MC-Plugin — Update Check</white> ===")));
                        return;
                    }

                    if (isNewRelease && hasRelease) {
                        // Install button for players
                        if (sender instanceof Player) {
                            TextComponent updateButton = new TextComponent(MessageUtil.legacy(MessagesManager.getString("update.install_button", "<dark_green>[<green>✔ Install Update</green><dark_green>]</dark_green>")));
                            updateButton.setClickEvent(new ClickEvent(
                                    ClickEvent.Action.RUN_COMMAND,
                                    "/mp updatejar"
                            ));
                            updateButton.setHoverEvent(new HoverEvent(
                                    HoverEvent.Action.SHOW_TEXT,
                                    new ComponentBuilder("§aClick to download and install update\n")
                                            .append("§7Release: §f" + finalReleaseName + "\n")
                                            .append("§7Restart required after installation")
                                            .create()
                            ));
                            ((Player) sender).spigot().sendMessage(updateButton);

                            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.install_hint", "<gray> or type </gray><white>/mp updatejar</white>")));
                        } else {
                            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.install_console", "<gray>To install, type: </gray><white>/mp updatejar</white>")));
                        }

                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.ignore_hint", "<gray>To ignore, do nothing.</gray>")));
                    }

                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.header", "<gold>=== <white>MC-Plugin — Update Check</white> ===")));
                });

            } catch (java.net.UnknownHostException e) {
                plugin.getLogger().warning("[Updater] Manual check failed: DNS resolution error");
                e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.connection_error", "<red>❌ Connection error with GitHub!</red>")));
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.dns_error_hint", "<gray>Could not resolve DNS for api.github.com</gray>")));
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.connection_check_hint", "<gray>Check server internet connection.</gray>")));
                });
            } catch (java.net.http.HttpTimeoutException e) {
                plugin.getLogger().warning("[Updater] Manual check failed: Connection timeout");
                e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.timeout_error", "<red>❌ Connection timeout with GitHub!</red>")));
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.timeout_hint", "<gray>GitHub did not respond within {seconds} seconds.</gray>").replace("{seconds}", String.valueOf(TIMEOUT_SECONDS))));
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.timeout_retry_hint", "<gray>Check internet connection or try again later.</gray>")));
                });
            } catch (Exception e) {
                plugin.getLogger().warning("[Updater] Manual check failed: " + e.getMessage());
                e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.check_error", "<red>❌ Error checking for updates!</red>")));
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.install_error_detail", "<gray>{type}: {message}</gray>")
                            .replace("{type}", e.getClass().getSimpleName())
                            .replace("{message}", e.getMessage() != null ? e.getMessage() : "")));
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.install_error_console", "<gray>Stack trace in server console.</gray>")));
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
     * После успешной замены сохраняет в БД и SHA коммита, и тег релиза.
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
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.cant_find_jar", "<red>❌ Cannot find current plugin JAR file!</red>")));
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
                            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.no_release_info", "<red>❌ Could not get latest release info!</red>")));
                        });
                        return;
                    }
                    downloadUrl = cachedDownloadUrl;
                    if (downloadUrl == null || downloadUrl.isEmpty()) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.no_jar_in_release", "<red>❌ No JAR file found in release </red><yellow>{tag}</yellow><red>!</red>").replace("{tag}", githubTag)));
                        });
                        return;
                    }
                }

        // Проверяем — не тот ли самый тег уже установлен?
        String storedTag = getStoredTag();
        if (githubTag.equals(storedTag)) {
            // Если тег совпадает — новый JAR скачать неоткуда (тот же релиз)
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.already_installed", "<green>✔</green> <green>This version is already installed! (</green><white>{tag}</white><green>)</green>").replace("{tag}", githubTag)));
            });
            return;
        }

                // Статус: загрузка
                final String finalTag = githubTag;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.downloading_release", "<yellow>⟳</yellow> <gray>Downloading update</gray> <white>{tag}</white><gray>...</gray>").replace("{tag}", finalTag)));
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
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.download_error", "<red>❌ Download error: HTTP {status}</red>").replace("{status}", String.valueOf(downloadResponse.statusCode()))));
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
                    // Сохраняем и SHA коммита, и тег релиза
                    String latestSha = fetchLatestCommitSha(plugin);
                    if (latestSha != null) {
                        saveStoredSha(latestSha);
                    }
                    saveStoredTag(finalTag);

                    final String finalSha = (latestSha != null)
                            ? latestSha.substring(0, Math.min(7, latestSha.length())) : "?";

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.install_success_header", "<gold>=== <green>Update Installed!</green> ===")));
                        sender.sendMessage("");
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.install_release", "<gray>Release:</gray> <white>{tag}</white>").replace("{tag}", finalTag)));
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.install_commit", "<gray>Commit:</gray> <white>{sha}</white>").replace("{sha}", finalSha)));
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.install_size", "<gray>Downloaded:</gray> <white>{size} KB</white>").replace("{size}", String.valueOf(downloadedKB))));
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.install_restart", "<red>⚠ Restart the server to apply the update!</red>")));
                    });
                } else {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.install_failed_replace", "<red>❌ Failed to replace JAR (file in use by process).</red>")));
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.install_failed_manual", "<gray>Check server console for manual replacement instructions.</gray>")));
                    });
                }

            } catch (Exception e) {
                // СтекТрейс в консоль
                plugin.getLogger().severe("[Updater] /mp updatejar failed!");
                e.printStackTrace();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.install_error", "<red>❌ Error downloading update!</red>")));
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.install_error_detail", "<gray>{type}: {message}</gray>")
                            .replace("{type}", e.getClass().getSimpleName())
                            .replace("{message}", e.getMessage() != null ? e.getMessage() : "")));
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.install_error_console", "<gray>Stack trace in server console.</gray>")));
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

            // Если backup восстановлен — плагин в рабочем состоянии, fallback не нужен
            if (backupRestored) {
                status = UpdateStatus.UPDATE_FAILED;
                errorMessage = "Could not replace JAR file: " + e.getMessage();
                return false;
            }

            // FALLBACK: поместить скачанный JAR в папку plugins
            // чтобы админ мог применить его при следующем перезапуске
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

    /**
     * Пытается разместить скачанный JAR в папке plugins, когда прямая
     * замена невозможна (Windows — файл занят процессом).
     *
     * @return true если JAR удалось разместить в папке plugins
     */
    private static boolean placeUpdateInPluginsFolder(Main plugin, File updateFile,
                                                       File currentJar, String tagName) {
        Path updatePath = updateFile.toPath();
        File pluginDir = currentJar.getParentFile();
        Path targetPath = currentJar.toPath();

        // Стратегия 1: попробовать Files.copy вместо Files.move
        // (на некоторых системах copy работает, когда move — нет)
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
        // (админ переименует перед рестартом)
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
