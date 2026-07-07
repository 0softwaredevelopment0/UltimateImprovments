package com.mcplugin.mechanics.security.auth;

import com.mcplugin.core.Main;
import com.mcplugin.database.DatabaseManager;
import com.mcplugin.util.ConsoleLogger;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 2FA система — подтверждение входа через кнопки в Telegram.
 * <p>
 * Поток работы:
 * 1. Игрок включает 2FA: /mp auth 2fa setup &lt;telegram_chat_id&gt;
 * 2. При входе (после пароля): плагин отправляет HTTP-запрос на бота
 * 3. Бот присылает inline кнопки ✅ Подтвердить / ❌ Отклонить
 * 4. Игрок нажимает кнопку в Telegram
 * 5. Плагин периодически проверяет статус подтверждения
 * 6. Если подтверждено — вход разрешён
 * <p>
 * Требует запущенного bot.py (HTTP-сервер на порту 3000).
 */
public class Auth2FA {

    private static Auth2FA instance;
    private static boolean tableChecked = false;

    // Хранилище ожидающих подтверждений: uuid → ConfirmationData
    private final Map<UUID, ConfirmationData> pendingConfirmations = new ConcurrentHashMap<>();

    // =========================
    // INIT
    // =========================
    public static void init() {
        instance = new Auth2FA();
        initTable();
        ConsoleLogger.info("[Auth2FA] Initialized (button-based confirmation).");
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
            ConsoleLogger.warn("[Auth2FA] DB init failed: " + e.getMessage());
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
            ConsoleLogger.warn("[Auth2FA] Get chat ID failed: " + e.getMessage());
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
            ConsoleLogger.warn("[Auth2FA] Save 2FA settings failed: " + e.getMessage());
        }
    }

    public static void remove(UUID uuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM auth_2fa WHERE uuid = ?")) {

            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.warn("[Auth2FA] Remove 2FA failed: " + e.getMessage());
        }
    }

    // =========================
    // SEND CONFIRMATION REQUEST TO BOT
    // =========================
    public String sendConfirmation(UUID uuid, String playerName, String playerIp) {
        String chatId = getChatId(uuid);
        if (chatId == null || chatId.isEmpty()) return null;

        String requestId = UUID.randomUUID().toString();

        // Сохраняем в память
        pendingConfirmations.put(uuid, new ConfirmationData(requestId, chatId, System.currentTimeMillis()));

        // Отправляем асинхронно
        sendRequestToBot(uuid, requestId, chatId, playerName, playerIp);

        return requestId;
    }

    // =========================
    // CHECK CONFIRMATION STATUS (PULLING)
    // =========================
    public String checkConfirmation(UUID uuid) {
        ConfirmationData data = pendingConfirmations.get(uuid);
        if (data == null) return "not_found";

        // Проверка срока (5 минут)
        if (System.currentTimeMillis() - data.createdAt > 300_000) {
            pendingConfirmations.remove(uuid);
            return "timeout";
        }

        // Если уже знаем результат (получен через callback в будущем)
        if (data.result != null) {
            pendingConfirmations.remove(uuid);
            return data.result;
        }

        // Опрашиваем бота
        return pollBotStatus(data.requestId, uuid);
    }

    // =========================
    // HTTP: отправить запрос боту
    // =========================
    private void sendRequestToBot(UUID uuid, String requestId, String chatId, String playerName, String playerIp) {
        String botUrl = getBotUrl();
        if (botUrl == null || botUrl.isEmpty()) {
            ConsoleLogger.warn("[Auth2FA] No bot.url configured — can't send confirmation request!");
            return;
        }

        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(), () -> {
            try {
                String json = "{\"chat_id\":\"" + escapeJson(chatId)
                        + "\",\"player\":\"" + escapeJson(playerName)
                        + "\",\"ip\":\"" + escapeJson(playerIp)
                        + "\",\"request_id\":\"" + escapeJson(requestId) + "\"}";

                URI uri = new URI(botUrl + "/confirm-request");
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }

                int responseCode = conn.getResponseCode();
                StringBuilder resp = new StringBuilder();
                try (InputStream is = conn.getInputStream()) {
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = is.read(buf)) != -1) {
                        resp.append(new String(buf, 0, len, StandardCharsets.UTF_8));
                    }
                } catch (Exception ignored) {}
                try (InputStream es = conn.getErrorStream()) {
                    if (es != null) {
                        byte[] buf = new byte[1024];
                        int len;
                        while ((len = es.read(buf)) != -1) {
                            resp.append(new String(buf, 0, len, StandardCharsets.UTF_8));
                        }
                    }
                } catch (Exception ignored) {}
                conn.disconnect();

                if (responseCode == 200) {
                    ConsoleLogger.info(
                            "[Auth2FA] Confirmation sent to " + playerName + " via Telegram (chat: " + chatId + ")");
                } else {
                    ConsoleLogger.warn(
                            "[Auth2FA] Bot returned HTTP " + responseCode + ": " + resp);
                }

            } catch (Exception e) {
                ConsoleLogger.warn(
                        "[Auth2FA] Failed to send confirmation: " + e.getMessage());
            }
        });
    }

    // =========================
    // HTTP: опросить статус
    // =========================
    private String pollBotStatus(String requestId, UUID uuid) {
        String botUrl = getBotUrl();
        if (botUrl == null || botUrl.isEmpty()) return "error";

        // Синхронный HTTP GET (вызывается с async потока)
        try {
            URI uri = new URI(botUrl + "/check-confirm?request_id=" + requestId);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            int responseCode = conn.getResponseCode();
            StringBuilder resp = new StringBuilder();
            try (InputStream is = conn.getInputStream()) {
                byte[] buf = new byte[1024];
                int len;
                while ((len = is.read(buf)) != -1) {
                    resp.append(new String(buf, 0, len, StandardCharsets.UTF_8));
                }
            } catch (Exception ignored) {}
            conn.disconnect();

            if (responseCode == 200) {
                // Парсим JSON вручную (без зависимостей)
                String body = resp.toString();
                if (body.contains("\"status\":\"approved\"")) {
                    return "approved";
                } else if (body.contains("\"status\":\"denied\"")) {
                    return "denied";
                } else if (body.contains("\"status\":\"timeout\"")) {
                    pendingConfirmations.remove(uuid);
                    return "timeout";
                } else if (body.contains("\"status\":\"not_found\"")) {
                    pendingConfirmations.remove(uuid);
                    return "not_found";
                }
            }
        } catch (Exception e) {
            String msg = e.getMessage();
            ConsoleLogger.warn(
                    "[Auth2FA] Poll failed for " + requestId + ": " + msg
                    + " — cancelling 2FA for " + uuid);
            pendingConfirmations.remove(uuid);

            // Если бот недоступен (Connection refused) — логиним игрока без 2FA
            if (msg != null && msg.contains("Connection refused")) {
                return "bot_down";
            }
            return "error";
        }
        return "pending";
    }

    // =========================
    // HTTP: получить URL бота
    // =========================
    private String getBotUrl() {
        try {
            return Main.getInstance().getConfig().getString("auth.2fa.bot_url", "");
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

    // =========================
    // GETTERS
    // =========================
    public boolean hasPendingConfirmation(UUID uuid) {
        ConfirmationData data = pendingConfirmations.get(uuid);
        if (data == null) return false;
        if (System.currentTimeMillis() - data.createdAt > 300_000) {
            pendingConfirmations.remove(uuid);
            return false;
        }
        return true;
    }

    public void clearPending(UUID uuid) {
        pendingConfirmations.remove(uuid);
    }

    // =========================
    // INNER CLASS
    // =========================
    private static class ConfirmationData {
        final String requestId;
        final String chatId;
        final long createdAt;
        String result; // null, "approved", "denied"

        ConfirmationData(String requestId, String chatId, long createdAt) {
            this.requestId = requestId;
            this.chatId = chatId;
            this.createdAt = createdAt;
        }
    }
}
