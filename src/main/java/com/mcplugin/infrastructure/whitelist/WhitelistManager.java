package com.mcplugin.infrastructure.whitelist;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.database.DatabaseManager;
import com.mcplugin.infrastructure.util.MessageUtil;
import org.bukkit.Bukkit;
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
 * 📋 WhitelistManager — кастомный вайтлист MC-Plugin.
 * <p>
 * Отключает ванильный вайтлист при включении.
 * Работает независимо от OP whitelist.
 * <p>
 * Команды: /mp whitelist on/off/add/list/remove
 */
public class WhitelistManager implements Listener {

    private static boolean enabled = false;

    // =========================
    // INIT
    // =========================
    public static void init(Main plugin) {
        load();
        plugin.getServer().getPluginManager().registerEvents(new WhitelistManager(), plugin);
    }

    // =========================
    // LOAD
    // =========================
    public static void load() {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "SELECT value FROM whitelist_meta WHERE key = ?")) {
            st.setString(1, "enabled");
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    enabled = Boolean.parseBoolean(rs.getString("value"));
                }
            }

            int count = 0;
            try (PreparedStatement cnt = con.prepareStatement("SELECT COUNT(*) FROM whitelist");
                 ResultSet rs = cnt.executeQuery()) {
                if (rs.next()) count = rs.getInt(1);
            }

            Main.getInstance().getLogger().info("[Whitelist] Loaded " + count + " players, enabled=" + enabled);

            // Обновляем ванильный вайтлист
            syncVanillaWhitelist();
        } catch (Exception e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[Whitelist] Failed to load", e);
        }
    }

    // =========================
    // SYNC VANILLA WHITELIST
    // =========================

    /**
     * Если MC-Plugin whitelist включён — отключает ванильный и выводит сообщение.
     */
    private static void syncVanillaWhitelist() {
        if (enabled) {
            Bukkit.setWhitelist(false);
        }
    }

    // =========================
    // GETTERS
    // =========================
    public static boolean isEnabled() {
        return enabled;
    }

    public static List<String> getWhitelistNames() {
        List<String> result = new ArrayList<>();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "SELECT player_name FROM whitelist ORDER BY player_name");
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                result.add(rs.getString("player_name"));
            }
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[Whitelist] Failed to list", e);
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
                     "INSERT OR IGNORE INTO whitelist (player_name) VALUES (?)")) {
            st.setString(1, lower);
            int rows = st.executeUpdate();
            if (rows > 0) {
                Main.getInstance().getLogger().info("[Whitelist] Added: " + lower);
                return true;
            }
            return false;
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[Whitelist] Failed to add: " + lower, e);
            return false;
        }
    }

    public static boolean remove(String playerName) {
        if (playerName == null || playerName.isBlank()) return false;
        String lower = playerName.toLowerCase().trim();

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "DELETE FROM whitelist WHERE player_name = ?")) {
            st.setString(1, lower);
            int rows = st.executeUpdate();
            if (rows > 0) {
                Main.getInstance().getLogger().info("[Whitelist] Removed: " + lower);
                return true;
            }
            return false;
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[Whitelist] Failed to remove: " + lower, e);
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
                     "INSERT OR REPLACE INTO whitelist_meta (key, value) VALUES (?, ?)")) {
            st.setString(1, "enabled");
            st.setString(2, String.valueOf(val));
            st.executeUpdate();
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[Whitelist] Failed to save state", e);
        }

        syncVanillaWhitelist();
        return true;
    }

    // =========================
    // CHECK
    // =========================
    public static boolean isWhitelisted(String playerName) {
        String lower = playerName.toLowerCase().trim();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "SELECT 1 FROM whitelist WHERE player_name = ?")) {
            st.setString(1, lower);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.FINE, "[Whitelist] Check error for: " + lower, e);
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
        if (!isWhitelisted(playerName)) {
            e.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, MessageUtil.legacy(
                    "<red>⛔ You are not whitelisted on this server!</red>\n" +
                    "<gray>Use the MC-Plugin whitelist system.</gray>"
            ));
        }
    }
}
