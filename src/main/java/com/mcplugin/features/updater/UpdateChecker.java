package com.mcplugin.features.updater;

import com.mcplugin.Main;
import com.mcplugin.database.DatabaseManager;

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
 * Auto-updater: при старте сервера проверяет GitHub Releases.
 * <p>
 * Версия плагина привязана к версии Minecraft (26.1.2), поэтому
 * сравнение идёт не по version из plugin.yml, а по тегу GitHub-релиза.
 * <p>
 * Логика:\n * <ol>\n *   <li>Читаем из БД (таблица {@code updater_state}) последний скачанный тег;</li>\n *   <li>Запрашиваем GitHub API {@code /releases/latest} — получаем {@code tag_name};</li>\n *   <li>Если теги РАЗНЫЕ — это новый релиз → скачиваем JAR + заменяем текущий;</li>\n *   <li>После успешной замены сохраняем новый тег в БД.</li>\n * </ol>\n * <p>\n * Вся сетевая работа — в асинхронном потоке, главный поток не блокируется.\n */
public class UpdateChecker {

    // =========================
    // ⚙ КОНФИГУРАЦИЯ
    // =========================
    private static final String GITHUB_OWNER = "Minecraft337";
    private static final String GITHUB_REPO = "MC-Plugin";
    private static final String API_URL = "https://api.github.com/repos/"
            + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";
    private static final String USER_AGENT = "MC-Plugin-Updater";
    private static final int TIMEOUT_SECONDS = 15;

    // =========================
    // СТАТУС (volatile — пишется из async, читается с main)
    // =========================
    public enum UpdateStatus {
        UP_TO_DATE,
        UPDATE_DOWNLOADED,
        UPDATE_FAILED,
        CHECK_FAILED
    }

    private static volatile UpdateStatus status = UpdateStatus.UP_TO_DATE;
    private static volatile String latestTag = "";
    private static volatile String errorMessage = "";

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
    // ОСНОВНАЯ ЛОГИКА
    // =========================
    private static void performCheck(Main plugin) throws Exception {
        File pluginDir = plugin.getDataFolder().getParentFile();
        File currentJar = plugin.getPluginFile();

        // ════════════════════════════════════════
        // 0. Очистка orphaned файлов от предыдущих запусков
        // ════════════════════════════════════════
        cleanupOrphanedFiles(pluginDir, currentJar);

        // ════════════════════════════════════════
        // 1. Читаем последний сохранённый тег из БД
        // ════════════════════════════════════════
        String dbTag = getStoredTag();
        plugin.getLogger().info("[Updater] Last known release tag: "
                + (dbTag.isEmpty() ? "<none>" : dbTag));

        // ════════════════════════════════════════
        // 2. HTTP-запрос к GitHub API
        // ════════════════════════════════════════
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            plugin.getLogger().warning("[Updater] GitHub API returned HTTP "
                    + response.statusCode());
            status = UpdateStatus.CHECK_FAILED;
            return;
        }

        // ════════════════════════════════════════
        // 3. Парсим JSON — получаем tag_name
        // ════════════════════════════════════════
        JsonObject release = JsonParser.parseString(response.body()).getAsJsonObject();
        String githubTag = release.get("tag_name").getAsString();

        plugin.getLogger().info("[Updater] GitHub latest release: " + githubTag);

        // ════════════════════════════════════════
        // 4. Сравниваем теги
        // ════════════════════════════════════════
        if (githubTag.equals(dbTag)) {
            plugin.getLogger().info("[Updater] No new releases (last tag: " + dbTag + ")");
            status = UpdateStatus.UP_TO_DATE;
            latestTag = githubTag;
            return;
        }

        plugin.getLogger().info("[Updater] New release detected: "
                + githubTag + " (was: " + (dbTag.isEmpty() ? "<none>" : dbTag) + ")");

        // ════════════════════════════════════════
        // 5. Поиск JAR-ассета в релизе
        // ════════════════════════════════════════
        String downloadUrl = findJarAsset(release);
        if (downloadUrl == null) {
            plugin.getLogger().warning("[Updater] No JAR asset found in release "
                    + githubTag + " — skipping download");
            status = UpdateStatus.UPDATE_FAILED;
            latestTag = githubTag;
            return;
        }

        latestTag = githubTag;

        // ════════════════════════════════════════
        // 6. Скачивание JAR
        // ════════════════════════════════════════
        File tempFile = new File(pluginDir,
                plugin.getDescription().getName() + ".jar.update");

        plugin.getLogger().info("[Updater] Downloading " + githubTag + "...");

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
            plugin.getLogger().warning("[Updater] Download failed: HTTP "
                    + downloadResponse.statusCode());
            status = UpdateStatus.UPDATE_FAILED;
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

        plugin.getLogger().info("[Updater] Downloaded " + (totalBytes / 1024) + " KB");

        // ════════════════════════════════════════
        // 7. Замена текущего JAR на новый
        // ════════════════════════════════════════
        boolean replaced = replaceJar(plugin, currentJar, tempFile, githubTag);

        if (replaced) {
            // Сохраняем тег в БД — больше не будем качать этот релиз
            saveStoredTag(githubTag);
        }
    }

    // =========================
    // 💾 РАБОТА С БД
    // =========================

    /** Читает последний скачанный тег из таблицы updater_state. */
    private static String getStoredTag() {
        Connection con = DatabaseManager.getConnection();
        if (con == null) return "";

        try (PreparedStatement ps = con.prepareStatement(
                "SELECT value FROM updater_state WHERE key = 'latest_tag'")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("value");
            }
        } catch (Exception e) {
            Main.getInstance().getLogger().fine("[Updater] DB read error: " + e.getMessage());
        }
        return "";
    }

    /** Сохраняет новый тег в таблицу updater_state. */
    private static void saveStoredTag(String tag) {
        Connection con = DatabaseManager.getConnection();
        if (con == null) return;

        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE updater_state SET value = ? WHERE key = 'latest_tag'")) {
            ps.setString(1, tag);
            ps.executeUpdate();
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
    // 📦 ЗАМЕНА JAR-ФАЙЛА
    // =========================
    // =========================
    // 🔍 /mp checkver — РУЧНАЯ ПРОВЕРКА ОБНОВЛЕНИЙ
    // =========================

    /**
     * Выполняет асинхронную проверку GitHub на наличие новых релизов
     * и отправляет результат отправителю команды (игроку или консоли).
     * Если обновление доступно — показывает кликабельную кнопку [Обновить].
     */
    public static void checkOnly(CommandSender sender) {
        Main plugin = Main.getInstance();
        String senderName = sender instanceof Player ? ((Player) sender).getName() : "Console";
        plugin.getLogger().info("[Updater] Manual version check requested by " + senderName);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String dbTag = getStoredTag();
                String pluginVersion = plugin.getDescription().getVersion();

                // HTTP-запрос к GitHub API
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_URL))
                        .header("Accept", "application/vnd.github.v3+json")
                        .header("User-Agent", USER_AGENT)
                        .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage("§4❌ §cGitHub API вернул HTTP " + response.statusCode());
                        sender.sendMessage("§8┃ §7Возможно, превышен лимит запросов или репозиторий недоступен.");
                    });
                    plugin.getLogger().warning("[Updater] Manual check: GitHub API returned HTTP "
                            + response.statusCode());
                    return;
                }

                JsonObject release = JsonParser.parseString(response.body()).getAsJsonObject();
                String githubTag = release.get("tag_name").getAsString();
                String releaseName = release.has("name") && !release.get("name").isJsonNull()
                        ? release.get("name").getAsString()
                        : githubTag;
                String releaseUrl = release.get("html_url").getAsString();
                String releaseBody = release.has("body") && !release.get("body").isJsonNull()
                        ? release.get("body").getAsString()
                        : "";

                boolean isUpdateAvailable = !githubTag.equals(dbTag);
                boolean isFirstRun = dbTag.isEmpty();

                // Отправляем результат на главном потоке
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("");
                    sender.sendMessage("§6═══════════════════════════════════");
                    sender.sendMessage("§6  ✦ §fMC-Plugin §7— Проверка обновлений");
                    sender.sendMessage("§6═══════════════════════════════════");
                    sender.sendMessage("");
                    sender.sendMessage("§7Текущая версия:  §f" + pluginVersion);
                    sender.sendMessage("§7Последний релиз: §f" + githubTag);

                    if (isFirstRun) {
                        sender.sendMessage("");
                        sender.sendMessage("§eℹ §7Это первый запуск проверки обновлений.");
                        sender.sendMessage("§7Релиз §f" + githubTag + "§7 будет загружен автоматически при старте.");
                    } else if (isUpdateAvailable) {
                        sender.sendMessage("");
                        sender.sendMessage("§a✨ Доступно обновление! §f" + releaseName);
                        sender.sendMessage("§7Было: §f" + dbTag + " §7→ Стало: §a" + githubTag);

                        if (!releaseBody.isEmpty()) {
                            // Показываем первые 3 строки changelog'а
                            String[] lines = releaseBody.split("\n");
                            int shown = 0;
                            for (String line : lines) {
                                if (shown >= 5) break;
                                String trimmed = line.trim();
                                if (!trimmed.isEmpty()) {
                                    sender.sendMessage("§8  ┃ §7" + (trimmed.length() > 60
                                            ? trimmed.substring(0, 57) + "..." : trimmed));
                                    shown++;
                                }
                            }
                        }

                        sender.sendMessage("");

                        // Кликабельная кнопка обновления (только для игроков)
                        if (sender instanceof Player) {
                            TextComponent updateButton = new TextComponent("§8  §2[§a✔ Обновить плагин§2]");
                            updateButton.setClickEvent(new ClickEvent(
                                    ClickEvent.Action.RUN_COMMAND,
                                    "/mp checkver update"
                            ));
                            updateButton.setHoverEvent(new HoverEvent(
                                    HoverEvent.Action.SHOW_TEXT,
                                    new ComponentBuilder("§aНажмите чтобы скачать и установить обновление\n")
                                            .append("§7Релиз: §f" + releaseName + "\n")
                                            .append("§7После установки потребуется перезапуск сервера")
                                            .create()
                            ));
                            ((Player) sender).spigot().sendMessage(updateButton);

                            sender.sendMessage("§8  §7или введите §f/mp checkver update");
                        } else {
                            sender.sendMessage("§7Для обновления введите: §f/mp checkver update");
                        }
                    } else {
                        sender.sendMessage("");
                        sender.sendMessage("§2✔ §aУ вас последняя версия плагина!");
                    }

                    sender.sendMessage("");
                    sender.sendMessage("§6═══════════════════════════════════");
                    sender.sendMessage("");
                });

            } catch (java.net.UnknownHostException e) {
                plugin.getLogger().warning("[Updater] Manual check failed: DNS resolution error");
                e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§4❌ §cОшибка соединения с GitHub!");
                    sender.sendMessage("§8┃ §7Не удалось разрешить DNS-имя api.github.com");
                    sender.sendMessage("§8┃ §7Проверьте интернет-соединение сервера.");
                });
            } catch (java.net.http.HttpTimeoutException e) {
                plugin.getLogger().warning("[Updater] Manual check failed: Connection timeout");
                e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§4❌ §cТаймаут соединения с GitHub!");
                    sender.sendMessage("§8┃ §7Сервер GitHub не ответил за " + TIMEOUT_SECONDS + " сек.");
                    sender.sendMessage("§8┃ §7Проверьте интернет-соединение или попробуйте позже.");
                });
            } catch (Exception e) {
                plugin.getLogger().warning("[Updater] Manual check failed: " + e.getMessage());
                e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§4❌ §cОшибка при проверке обновлений!");
                    sender.sendMessage("§8┃ §7" + e.getClass().getSimpleName() + ": " + e.getMessage());
                    sender.sendMessage("§8┃ §7Детали в консоли сервера.");
                });
            }
        });
    }

    // =========================
    // 📥 /mp checkver update — РУЧНАЯ ЗАГРУЗКА ОБНОВЛЕНИЯ
    // =========================

    /**
     * Выполняет асинхронную загрузку и замену JAR из последнего GitHub-релиза.
     * Результат отправляется отправителю команды.
     */
    public static void manualDownload(CommandSender sender) {
        Main plugin = Main.getInstance();
        String senderName = sender instanceof Player ? ((Player) sender).getName() : "Console";
        plugin.getLogger().info("[Updater] Manual update requested by " + senderName);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                File pluginDir = plugin.getDataFolder().getParentFile();
                File currentJar = plugin.getPluginFile();

                if (currentJar == null || !currentJar.exists()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage("§4❌ §cНе удалось найти текущий JAR-файл плагина!");
                    });
                    return;
                }

                // Очистка orphaned файлов
                cleanupOrphanedFiles(pluginDir, currentJar);

                // HTTP-запрос к GitHub API
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_URL))
                        .header("Accept", "application/vnd.github.v3+json")
                        .header("User-Agent", USER_AGENT)
                        .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage("§4❌ §cGitHub API вернул HTTP " + response.statusCode());
                    });
                    return;
                }

                JsonObject release = JsonParser.parseString(response.body()).getAsJsonObject();
                String githubTag = release.get("tag_name").getAsString();

                // Проверяем — не та же ли версия уже установлена
                String dbTag = getStoredTag();
                if (githubTag.equals(dbTag)) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage("§2✔ §aЭта версия уже установлена! (§f" + githubTag + "§a)");
                    });
                    return;
                }

                // Поиск JAR-ассета
                String downloadUrl = findJarAsset(release);
                if (downloadUrl == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage("§4❌ §cВ релизе " + githubTag + " не найден JAR-файл!");
                    });
                    return;
                }

                // Статус: загрузка
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§e⟳ §7Загрузка обновления §f" + githubTag + "§7...");
                });

                // Скачивание JAR
                File tempFile = new File(pluginDir,
                        plugin.getDescription().getName() + ".jar.update");

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
                        sender.sendMessage("§4❌ §cОшибка загрузки: HTTP "
                                + downloadResponse.statusCode());
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
                boolean replaced = replaceJar(plugin, currentJar, tempFile, githubTag);

                if (replaced) {
                    saveStoredTag(githubTag);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage("");
                        sender.sendMessage("§6═══════════════════════════════════");
                        sender.sendMessage("§6  ✦ §aОбновление установлено!");
                        sender.sendMessage("§6═══════════════════════════════════");
                        sender.sendMessage("");
                        sender.sendMessage("§7Релиз:    §f" + githubTag);
                        sender.sendMessage("§7Загружено: §f" + downloadedKB + " KB");
                        sender.sendMessage("");
                        sender.sendMessage("§c⚠ Перезапустите сервер для применения обновления!");
                        sender.sendMessage("");
                    });
                } else {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage("§4❌ §cНе удалось установить обновление!");
                        sender.sendMessage("§8┃ §7Детали в консоли сервера.");
                    });
                }

            } catch (java.net.UnknownHostException e) {
                plugin.getLogger().warning("[Updater] Manual download failed: DNS resolution error");
                e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§4❌ §cОшибка соединения с GitHub!");
                    sender.sendMessage("§8┃ §7Не удалось разрешить DNS-имя. Проверьте интернет.");
                });
            } catch (java.net.http.HttpTimeoutException e) {
                plugin.getLogger().warning("[Updater] Manual download failed: Connection timeout");
                e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§4❌ §cТаймаут соединения с GitHub!");
                    sender.sendMessage("§8┃ §7Проверьте интернет-соединение или попробуйте позже.");
                });
            } catch (Exception e) {
                plugin.getLogger().warning("[Updater] Manual download failed: " + e.getMessage());
                e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§4❌ §cОшибка при загрузке обновления!");
                    sender.sendMessage("§8┃ §7" + e.getClass().getSimpleName() + ": " + e.getMessage());
                    sender.sendMessage("§8┃ §7Детали в консоли сервера.");
                });
            }
        });
    }

    /** @return true если замена прошла успешно */
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
        try {
            Files.move(targetPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("[Updater] Backed up current JAR");
        } catch (Exception e) {
            plugin.getLogger().warning("[Updater] Backup failed (non-critical): " + e.getMessage());
        }

        // ШАГ 2: Перемещаем новый JAR на место текущего
        try {
            Files.move(updatePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            plugin.getLogger().severe("[Updater] Failed to replace JAR: " + e.getMessage());
            try {
                Files.move(backupPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("[Updater] Backup restored");
            } catch (Exception restoreErr) {
                plugin.getLogger().severe("[Updater] Could not restore backup! "
                        + "Manual recovery needed. Backup at: " + backupPath);
            }
            status = UpdateStatus.UPDATE_FAILED;
            errorMessage = "Could not replace JAR file: " + e.getMessage();
            return false;
        }

        // УСПЕХ
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
}
