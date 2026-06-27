package com.mcplugin.infrastructure.opwhitelist;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.database.DatabaseManager;
import com.mcplugin.infrastructure.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;

/**
 * 🛡 OP Whitelist — белый список операторов (хранится в SQLite).
 * <p>
 * Если {@code enabled = true} и игрок имеет OP, но не в вайтлисте —
 * OP мгновенно снимается.
 * <p>
 * Поддерживает команды:
 * <ul>
 *   <li>{@code /mp opwhitelist add <ник>}</li>
 *   <li>{@code /mp opwhitelist remove <ник>}</li>
 *   <li>{@code /mp opwhitelist list}</li>
 *   <li>{@code /mp opwhitelist on}</li>
 *   <li>{@code /mp opwhitelist off}</li>
 * </ul>
 * <p>
 * Данные хранятся в SQLite (таблица {@code op_whitelist} + {@code op_whitelist_meta}).
 */
public class OpWhitelistManager implements Listener {

    private static boolean enabled = false;
    private static int taskId = -1;

    // ════════════════════════════════════════
    // INIT
    // ════════════════════════════════════════
    public static void init(Main plugin) {
        load();
        plugin.getServer().getPluginManager().registerEvents(new OpWhitelistManager(), plugin);
        // Проверка OP каждые 3 секунды
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, OpWhitelistManager::checkAllOnline, 60L, 60L).getTaskId();
    }

    public static void shutdown() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        // Данные уже сохранены в БД — ничего делать не нужно
    }

    // ════════════════════════════════════════
    // LOAD из SQLite
    // ════════════════════════════════════════
    public static void load() {
        try (Connection con = DatabaseManager.getConnection()) {
            // Миграция из старого JSON-файла (если существует и БД пуста)
            migrateFromJson(con);

            // Загружаем enabled-флаг
            try (PreparedStatement st = con.prepareStatement(
                    "SELECT value FROM op_whitelist_meta WHERE key = ?")) {
                st.setString(1, "enabled");
                try (ResultSet rs = st.executeQuery()) {
                    if (rs.next()) {
                        enabled = Boolean.parseBoolean(rs.getString("value"));
                    }
                }
            }

            // Считаем количество записей для лога
            int count = 0;
            try (PreparedStatement st = con.prepareStatement(
                    "SELECT COUNT(*) FROM op_whitelist");
                 ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    count = rs.getInt(1);
                }
            }

            Main.getInstance().getLogger().info("[OpWhitelist] Loaded " + count + " players from SQLite, enabled=" + enabled);
        } catch (Exception e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[OpWhitelist] Failed to load from DB", e);
        }
    }

    /**
     * Мигрирует данные из старого op-whitelist.json в SQLite, если JSON существует
     * и таблицы пусты. После миграции JSON-файл удаляется.
     */
    private static void migrateFromJson(Connection con) {
        java.io.File jsonFile = new java.io.File(Main.getInstance().getDataFolder(), "op-whitelist.json");
        if (!jsonFile.exists()) return;

        // Проверяем, есть ли уже данные в БД
        try (PreparedStatement st = con.prepareStatement("SELECT COUNT(*) FROM op_whitelist");
             ResultSet rs = st.executeQuery()) {
            if (rs.next() && rs.getInt(1) > 0) {
                // БД уже заполнена — удаляем JSON и выходим
                jsonFile.delete();
                return;
            }
        } catch (Exception ignored) {}

        try {
            // Парсим JSON вручную (без Gson, как было раньше)
            String json = java.nio.file.Files.readString(jsonFile.toPath()).trim();
            if (json.isEmpty() || json.equals("{}")) {
                jsonFile.delete();
                return;
            }

            // Парсим enabled
            boolean jsonEnabled = false;
            int enIdx = json.indexOf("\"enabled\"");
            if (enIdx >= 0) {
                int colonIdx = json.indexOf(':', enIdx);
                if (colonIdx >= 0) {
                    String rest = json.substring(colonIdx + 1).trim();
                    jsonEnabled = rest.startsWith("true");
                }
            }

            // Сохраняем enabled в мета-таблицу
            try (PreparedStatement st = con.prepareStatement(
                    "INSERT OR REPLACE INTO op_whitelist_meta (key, value) VALUES (?, ?)")) {
                st.setString(1, "enabled");
                st.setString(2, String.valueOf(jsonEnabled));
                st.executeUpdate();
            }

            // Парсим names и импортируем
            int namesIdx = json.indexOf("\"names\"");
            if (namesIdx >= 0) {
                int arrStart = json.indexOf('[', namesIdx);
                int arrEnd = json.indexOf(']', arrStart);
                if (arrStart >= 0 && arrEnd > arrStart) {
                    String arr = json.substring(arrStart + 1, arrEnd);
                    String[] parts = arr.split(",");
                    int imported = 0;
                    try (PreparedStatement st = con.prepareStatement(
                            "INSERT OR IGNORE INTO op_whitelist (player_name) VALUES (?)")) {
                        for (String p : parts) {
                            p = p.trim().replaceAll("^\"|\"$", "").toLowerCase();
                            if (!p.isEmpty()) {
                                st.setString(1, p);
                                st.addBatch();
                                imported++;
                            }
                        }
                        st.executeBatch();
                    }
                    Main.getInstance().getLogger().info("[OpWhitelist] Migrated " + imported + " players from op-whitelist.json to SQLite");
                }
            }

            // Удаляем JSON-файл после успешной миграции
            jsonFile.delete();
            Main.getInstance().getLogger().info("[OpWhitelist] Deleted old op-whitelist.json");
        } catch (Exception e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[OpWhitelist] Failed to migrate from JSON", e);
        }
    }

    // ════════════════════════════════════════
    // GETTERS
    // ════════════════════════════════════════
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Возвращает отсортированный список имён из whitelist (из БД).
     */
    public static List<String> getWhitelistNames() {
        List<String> result = new ArrayList<>();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "SELECT player_name FROM op_whitelist ORDER BY player_name");
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                result.add(rs.getString("player_name"));
            }
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[OpWhitelist] Failed to list players", e);
        }
        return result;
    }

    // ════════════════════════════════════════
    // ADD / REMOVE
    // ════════════════════════════════════════
    public static boolean add(String playerName) {
        if (playerName == null || playerName.isBlank()) return false;
        String lower = playerName.toLowerCase().trim();

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "INSERT OR IGNORE INTO op_whitelist (player_name) VALUES (?)")) {
            st.setString(1, lower);
            int rows = st.executeUpdate();
            if (rows > 0) {
                Main.getInstance().getLogger().info("[OpWhitelist] Added: " + lower);
                return true;
            }
            return false; // уже есть
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[OpWhitelist] Failed to add: " + lower, e);
            return false;
        }
    }

    public static boolean remove(String playerName) {
        if (playerName == null || playerName.isBlank()) return false;
        String lower = playerName.toLowerCase().trim();

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "DELETE FROM op_whitelist WHERE player_name = ?")) {
            st.setString(1, lower);
            int rows = st.executeUpdate();
            if (rows > 0) {
                Main.getInstance().getLogger().info("[OpWhitelist] Removed: " + lower);
                return true;
            }
            return false; // не найден
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[OpWhitelist] Failed to remove: " + lower, e);
            return false;
        }
    }

    // ════════════════════════════════════════
    // TOGGLE
    // ════════════════════════════════════════
    public static boolean setEnabled(boolean val) {
        if (enabled == val) return false;
        enabled = val;

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "INSERT OR REPLACE INTO op_whitelist_meta (key, value) VALUES (?, ?)")) {
            st.setString(1, "enabled");
            st.setString(2, String.valueOf(val));
            st.executeUpdate();
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[OpWhitelist] Failed to save enabled state", e);
        }

        if (enabled) {
            checkAllOnline();
        }
        return true;
    }

    // ════════════════════════════════════════
    // CHECK HELPERS
    // ════════════════════════════════════════
    private static boolean isWhitelisted(String playerName) {
        String lower = playerName.toLowerCase().trim();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "SELECT 1 FROM op_whitelist WHERE player_name = ?")) {
            st.setString(1, lower);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.FINE, "[OpWhitelist] Check error for: " + lower, e);
            return false;
        }
    }

    // ════════════════════════════════════════
    // JOIN EVENT — проверка при входе
    // ════════════════════════════════════════
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        if (!enabled) return;
        checkAndDeop(e.getPlayer());
    }

    // ════════════════════════════════════════
    // CHECK ALL ONLINE — периодическая проверка
    // ════════════════════════════════════════
    public static void checkAllOnline() {
        if (!enabled) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkAndDeop(player);
        }
    }

    // ════════════════════════════════════════
    // CHECK + DEOP
    // ════════════════════════════════════════
    private static void checkAndDeop(Player player) {
        if (player == null || !player.isOnline()) return;
        if (!player.isOp()) return;

        if (isWhitelisted(player.getName())) return;

        // Игрок OP, но не в вайтлисте — снимаем OP
        player.setOp(false);
        player.sendMessage(MessageUtil.parse(
                "<red>⛔</red> <white>Your operator status has been removed — you are not in the OP whitelist.</white>"
        ));
        Main.getInstance().getLogger().info("[OpWhitelist] Removed OP from " + player.getName() + " (not whitelisted)");
    }
}
