package com.mcplugin.infrastructure.blacklist;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.database.DatabaseManager;
import com.mcplugin.infrastructure.util.MessageUtil;
import com.mcplugin.infrastructure.util.ConsoleLogger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * 📋 BlacklistManager — чёрный список MC-Plugin.
 * <p>
 * Может быть включён одновременно с вайтлистом.
 * Игроки из блэклиста не могут зайти на сервер в любом случае.
 * <p>
 * Команды: /mp blacklist on/off/add/list/remove
 */
public class BlacklistManager implements Listener {

    private static boolean enabled = false;

    // =========================
    // INIT
    // =========================
    public static void init(Main plugin) {
        load();
        plugin.getServer().getPluginManager().registerEvents(new BlacklistManager(), plugin);
    }

    // =========================
    // LOAD
    // =========================
    public static void load() {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "SELECT value FROM blacklist_meta WHERE key = ?")) {
            st.setString(1, "enabled");
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    enabled = Boolean.parseBoolean(rs.getString("value"));
                }
            }

            int count = 0;
            try (PreparedStatement cnt = con.prepareStatement("SELECT COUNT(*) FROM blacklist");
                 ResultSet rs = cnt.executeQuery()) {
                if (rs.next()) count = rs.getInt(1);
            }

            ConsoleLogger.info("[Blacklist] Loaded " + count + " players, enabled=" + enabled);
        } catch (Exception e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[Blacklist] Failed to load", e);
        }
    }

    // =========================
    // GETTERS
    // =========================
    public static boolean isEnabled() {
        return enabled;
    }

    public static List<String> getBlacklistNames() {
        List<String> result = new ArrayList<>();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "SELECT player_name FROM blacklist ORDER BY player_name");
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                result.add(rs.getString("player_name"));
            }
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[Blacklist] Failed to list", e);
        }
        return result;
    }

    // =========================
    // ADD / REMOVE
    // =========================
    public static boolean add(String playerName) {
        if (playerName == null || playerName.isBlank()) return false;
        String lower = playerName.toLowerCase().trim();

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "INSERT OR IGNORE INTO blacklist (player_name) VALUES (?)")) {
            st.setString(1, lower);
            int rows = st.executeUpdate();
            if (rows > 0) {
                ConsoleLogger.info("[Blacklist] Added: " + lower);

                // Если игрок онлайн — кикаем
                @SuppressWarnings("deprecation")
                Player online = org.bukkit.Bukkit.getPlayerExact(playerName);
                if (online != null && online.isOnline()) {
                    online.kickPlayer(MessageUtil.legacy(
                            "<red>⛔ You have been blacklisted from this server!</red>"
                    ));
                }
                return true;
            }
            return false;
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[Blacklist] Failed to add: " + lower, e);
            return false;
        }
    }

    public static boolean remove(String playerName) {
        if (playerName == null || playerName.isBlank()) return false;
        String lower = playerName.toLowerCase().trim();

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "DELETE FROM blacklist WHERE player_name = ?")) {
            st.setString(1, lower);
            int rows = st.executeUpdate();
            if (rows > 0) {
                ConsoleLogger.info("[Blacklist] Removed: " + lower);
                return true;
            }
            return false;
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[Blacklist] Failed to remove: " + lower, e);
            return false;
        }
    }

    // =========================
    // TOGGLE
    // =========================
    public static boolean setEnabled(boolean val) {
        if (enabled == val) return false;
        enabled = val;

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "INSERT OR REPLACE INTO blacklist_meta (key, value) VALUES (?, ?)")) {
            st.setString(1, "enabled");
            st.setString(2, String.valueOf(val));
            st.executeUpdate();
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[Blacklist] Failed to save state", e);
        }
        return true;
    }

    // =========================
    // CHECK
    // =========================
    public static boolean isBlacklisted(String playerName) {
        String lower = playerName.toLowerCase().trim();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "SELECT 1 FROM blacklist WHERE player_name = ?")) {
            st.setString(1, lower);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.FINE, "[Blacklist] Check error for: " + lower, e);
            return false;
        }
    }

    // =========================
    // JOIN EVENT
    // =========================
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerLogin(PlayerLoginEvent e) {
        if (!enabled) return;

        String playerName = e.getPlayer().getName();
        if (isBlacklisted(playerName)) {
            e.disallow(PlayerLoginEvent.Result.KICK_BANNED, MessageUtil.legacy(
                    "<red>⛔ You are blacklisted from this server!</red>"
            ));
        }
    }
}
