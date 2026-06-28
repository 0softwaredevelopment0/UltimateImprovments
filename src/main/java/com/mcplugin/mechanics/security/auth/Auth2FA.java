package com.mcplugin.mechanics.security.auth;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.database.DatabaseManager;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 2FA система — генерация и проверка кодов через Telegram Bot API.
 * <p>
 * Поток работы:
 * 1. Игрок включает 2FA: /mp auth 2fa setup &lt;telegram_chat_id&gt;
 * 2. При входе (после пароля): генерируется 6-значный код
 * 3. Код отправляется напрямую через Telegram Bot API (config: auth.2fa.bot_token)
 * 4. Игрок вводит: /mp auth 2fa &lt;code&gt;
 * 5. Если код верный — вход разрешён
 * <p>
 * Не требует запуска отдельного бота — плагин сам вызывает
 * https://api.telegram.org/bot&lt;token&gt;/sendMessage
 */
public class Auth2FA {

    private static Auth2FA instance;
    private static boolean tableChecked = false;

    // Хранилище ожидающих кодов: uuid → codeData
    private final Map<UUID, CodeData> pendingCodes = new ConcurrentHashMap<>();
    private final Random random = new Random();

    // =========================
    // INIT
    // =========================
    public static void init() {
        instance = new Auth2FA();
        initTable();
        Main.getInstance().getLogger().info("[Auth2FA] Initialized.");
    }

    public static Auth2FA getInstance() {
        return instance;
    }

    // =========================
    // DB TABLE
    // =========================
    private static void initTable() {
        if (tableChecked) return;
        try (Connection con = DatabaseManager.getConnection();
             var st = con.createStatement()) {

            st.execute("""
                CREATE TABLE IF NOT EXISTS auth_2fa (
                    uuid TEXT PRIMARY KEY,
                    telegram_chat_id TEXT NOT NULL,
                    enabled INTEGER DEFAULT 1
                );
            """);

            tableChecked = true;
        } catch (Exception e) {
            Main.getInstance().getLogger().warning("[Auth2FA] DB init failed: " + e.getMessage());
        }
    }

    // =========================
    // GET/SET 2FA SETTINGS
    // =========================
    public static boolean isEnabled(UUID uuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT enabled FROM auth_2fa WHERE uuid = ?")) {

            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt("enabled") == 1;
            }
        } catch (Exception e) {
            return false;
        }
    }

    public static String getChatId(UUID uuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT telegram_chat_id FROM auth_2fa WHERE uuid = ?")) {

            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("telegram_chat_id");
            }
        } catch (Exception e) {
            Main.getInstance().getLogger().warning("[Auth2FA] Get chat ID failed: " + e.getMessage());
        }
        return null;
    }

    public static void setEnabled(UUID uuid, String chatId, boolean enabled) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT OR REPLACE INTO auth_2fa (uuid, telegram_chat_id, enabled) VALUES (?, ?, ?)")) {

            ps.setString(1, uuid.toString());
            ps.setString(2, chatId);
            ps.setInt(3, enabled ? 1 : 0);
            ps.executeUpdate();
        } catch (Exception e) {
            Main.getInstance().getLogger().warning("[Auth2FA] Save 2FA settings failed: " + e.getMessage());
        }
    }

    public static void remove(UUID uuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM auth_2fa WHERE uuid = ?")) {

            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (Exception e) {
            Main.getInstance().getLogger().warning("[Auth2FA] Remove 2FA failed: " + e.getMessage());
        }
    }

    // =========================
    // GENERATE CODE
    // =========================
    public String generateCode(UUID uuid) {
        // 6-значный код
        int code = 100000 + random.nextInt(900000);
        String codeStr = String.valueOf(code);

        // Получаем chat_id
        String chatId = getChatId(uuid);

        // Сохраняем в память с временем создания
        pendingCodes.put(uuid, new CodeData(codeStr, chatId, System.currentTimeMillis()));

        // Отправляем код через HTTP
        sendCode(uuid, codeStr, chatId);

        return codeStr;
    }

    // =========================
    // VERIFY CODE
    // =========================
    public boolean verifyCode(UUID uuid, String code) {
        CodeData data = pendingCodes.get(uuid);
        if (data == null) return false;

        // Проверка срока действия (5 минут)
        if (System.currentTimeMillis() - data.createdAt > 300_000) {
            pendingCodes.remove(uuid);
            return false;
        }

        if (!data.code.equals(code)) return false;

        // Код верный — удаляем из памяти
        pendingCodes.remove(uuid);
        return true;
    }

    // =========================
    // SEND CODE VIA TELEGRAM BOT API (прямой вызов)
    // =========================
    private void sendCode(UUID uuid, String code, String chatId) {
        String token = getBotToken();
        if (token == null || token.isEmpty()) {
            // Если токен не настроен — логируем код в консоль
            String playerName = org.bukkit.Bukkit.getPlayer(uuid) != null
                    ? org.bukkit.Bukkit.getPlayer(uuid).getName() : uuid.toString();
            Main.getInstance().getLogger().info(
                    "[Auth2FA] Code for " + playerName + " (chat: " + chatId + "): " + code);
            Main.getInstance().getLogger().warning("[Auth2FA] Set auth.2fa.bot_token in config.yml to send via Telegram!");
            return;
        }

        // Отправляем асинхронно в отдельном потоке (не блокируем сервер)
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(), () -> {
            try {
                String playerName = org.bukkit.Bukkit.getOfflinePlayer(uuid).getName();
                if (playerName == null) playerName = uuid.toString();

                // Формируем текст сообщения
                String text = "🔐 <b>Код подтверждения</b>\n\n"
                        + "Игрок: <code>" + escapeHtml(playerName) + "</code>\n"
                        + "Код: <b><code>" + escapeHtml(code) + "</code></b>\n\n"
                        + "Никому не сообщайте этот код!";

                String json = "{\"chat_id\":" + escapeJson(chatId)
                        + ",\"text\":\"" + escapeJson(text)
                        + "\",\"parse_mode\":\"HTML\"}";

                URI uri = new URI("https://api.telegram.org/bot" + token + "/sendMessage");
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }

                int responseCode = conn.getResponseCode();

                // Читаем ответ для отладки
                StringBuilder responseBody = new StringBuilder();
                try (java.io.InputStream is = conn.getInputStream()) {
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = is.read(buf)) != -1) {
                        responseBody.append(new String(buf, 0, len, StandardCharsets.UTF_8));
                    }
                } catch (Exception ignored) {}
                try (java.io.InputStream es = conn.getErrorStream()) {
                    if (es != null) {
                        byte[] buf = new byte[1024];
                        int len;
                        while ((len = es.read(buf)) != -1) {
                            responseBody.append(new String(buf, 0, len, StandardCharsets.UTF_8));
                        }
                    }
                } catch (Exception ignored) {}

                conn.disconnect();

                if (responseCode == 200) {
                    Main.getInstance().getLogger().info(
                            "[Auth2FA] Code sent to " + playerName + " via Telegram (chat: " + chatId + ")");
                } else {
                    Main.getInstance().getLogger().warning(
                            "[Auth2FA] Telegram API returned HTTP " + responseCode
                                    + " for " + playerName + ": " + responseBody);
                }

            } catch (Exception e) {
                Main.getInstance().getLogger().warning(
                        "[Auth2FA] Telegram send failed: " + e.getMessage());
            }
        });
    }

    // =========================
    // GET BOT TOKEN FROM CONFIG
    // =========================
    private String getBotToken() {
        try {
            return Main.getInstance().getConfig().getString("auth.2fa.bot_token", "");
        } catch (Exception e) {
            return "";
        }
    }

    // =========================
    // UTILITY
    // =========================
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    // =========================
    // GETTERS
    // =========================
    public boolean hasPendingCode(UUID uuid) {
        CodeData data = pendingCodes.get(uuid);
        if (data == null) return false;
        // Auto-clean expired
        if (System.currentTimeMillis() - data.createdAt > 300_000) {
            pendingCodes.remove(uuid);
            return false;
        }
        return true;
    }

    public void clearPendingCode(UUID uuid) {
        pendingCodes.remove(uuid);
    }

    // =========================
    // INNER CLASS
    // =========================
    private static class CodeData {
        final String code;
        final String chatId;
        final long createdAt;

        CodeData(String code, String chatId, long createdAt) {
            this.code = code;
            this.chatId = chatId;
            this.createdAt = createdAt;
        }
    }
}
