package com.ultimateimprovments.whitelist;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.ConsoleLogger;
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
 * 🛡 OP Whitelist — operator whitelist (stored in SQLite).
 * <p>
 * If {@code enabled = true} and a player has OP but is not whitelisted —
 * OP is removed instantly.
 * <p>
 * Supports commands:
 * <ul>
 *   <li>{@code /ui opwhitelist add <nick>}</li>
 *   <li>{@code /ui opwhitelist remove <nick>}</li>
 *   <li>{@code /ui opwhitelist list}</li>
 *   <li>{@code /ui opwhitelist on}</li>
 *   <li>{@code /ui opwhitelist off}</li>
 * </ul>
 * <p>
 * Data is stored in SQLite (tables {@code op_whitelist} + {@code op_whitelist_meta}).
 */
public class OpWhitelistManager implements Listener {

    private static boolean enabled = false;

    // ════════════════════════════════════════
    // INIT
    // ════════════════════════════════════════
    public static void init(Main plugin) {
        load();
        plugin.getServer().getPluginManager().registerEvents(new OpWhitelistManager(), plugin);
        // ⚠ Periodic check moved to AccessListCheckTask (configurable interval)
    }

    public static void shutdown() {
        // Data is already persisted in the DB — nothing to do
    }

    // ════════════════════════════════════════
    // LOAD from SQLite
    // ════════════════════════════════════════
    public static void load() {
        try (Connection con = DatabaseManager.getConnection()) {
            // Migrate from the old JSON file (if it exists and the DB is empty)
            migrateFromJson(con);

            // Load the enabled flag
            try (PreparedStatement st = con.prepareStatement(
                    "SELECT value FROM op_whitelist_meta WHERE key = ?")) {
                st.setString(1, "enabled");
                try (ResultSet rs = st.executeQuery()) {
                    if (rs.next()) {
                        enabled = Boolean.parseBoolean(rs.getString("value"));
                    }
                }
            }

            // Count the records for the log
            int count = 0;
            try (PreparedStatement st = con.prepareStatement(
                    "SELECT COUNT(*) FROM op_whitelist");
                 ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    count = rs.getInt(1);
                }
            }

            ConsoleLogger.info("[OpWhitelist] Loaded " + count + " players from SQLite, enabled=" + enabled);
        } catch (Exception e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[OpWhitelist] Failed to load from DB", e);
        }
    }

    /**
     * Migrates data from the old op-whitelist.json into SQLite, if the JSON exists
     * and the tables are empty. After the migration the JSON file is deleted.
     */
    private static void migrateFromJson(Connection con) {
        java.io.File jsonFile = new java.io.File(Main.getInstance().getDataFolder(), "op-whitelist.json");
        if (!jsonFile.exists()) return;

        // Check whether the DB already has data
        try (PreparedStatement st = con.prepareStatement("SELECT COUNT(*) FROM op_whitelist");
             ResultSet rs = st.executeQuery()) {
            if (rs.next() && rs.getInt(1) > 0) {
                // DB already populated — delete the JSON and exit
                jsonFile.delete();
                return;
            }
        } catch (Exception ignored) {}

        try {
            // Parse the JSON manually (without Gson, as before)
            String json = java.nio.file.Files.readString(jsonFile.toPath()).trim();
            if (json.isEmpty() || json.equals("{}")) {
                jsonFile.delete();
                return;
            }

            // Parse enabled
            boolean jsonEnabled = false;
            int enIdx = json.indexOf("\"enabled\"");
            if (enIdx >= 0) {
                int colonIdx = json.indexOf(':', enIdx);
                if (colonIdx >= 0) {
                    String rest = json.substring(colonIdx + 1).trim();
                    jsonEnabled = rest.startsWith("true");
                }
            }

            // Save enabled into the meta table
            try (PreparedStatement st = con.prepareStatement(
                    "INSERT OR REPLACE INTO op_whitelist_meta (key, value) VALUES (?, ?)")) {
                st.setString(1, "enabled");
                st.setString(2, String.valueOf(jsonEnabled));
                st.executeUpdate();
            }

            // Parse names and import them
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
                    ConsoleLogger.info("[OpWhitelist] Migrated " + imported + " players from op-whitelist.json to SQLite");
                }
            }

            // Delete the JSON file after a successful migration
            jsonFile.delete();
            ConsoleLogger.info("[OpWhitelist] Deleted old op-whitelist.json");
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
     * Returns the sorted list of names from the whitelist (from the DB).
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
                ConsoleLogger.info("[OpWhitelist] Added: " + lower);
                return true;
            }
            return false; // already present
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
                ConsoleLogger.info("[OpWhitelist] Removed: " + lower);
                return true;
            }
            return false; // not found
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
            // Instant check of all online players on enable (AccessListCheckTask picks it up from here)
            for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                checkAndDeop(p);
            }
        }
        return true;
    }

    // ════════════════════════════════════════
    // CHECK HELPERS
    // ════════════════════════════════════════
    public static boolean isWhitelisted(String playerName) {
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
    // JOIN EVENT — check on join
    // ════════════════════════════════════════
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        if (!enabled) return;
        checkAndDeop(e.getPlayer());
    }

    // ════════════════════════════════════════
    // CHECK + DEOP
    // ════════════════════════════════════════
    private static void checkAndDeop(Player player) {
        if (player == null || !player.isOnline()) return;
        if (!player.isOp()) return;

        if (isWhitelisted(player.getName())) return;

        // Player is OP but not whitelisted — remove OP
        player.setOp(false);
        player.sendMessage(MessageUtil.parse(
                "<red>⛔</red> <white>Your operator status has been removed — you are not in the OP whitelist.</white>"
        ));
        ConsoleLogger.info("[OpWhitelist] Removed OP from " + player.getName() + " (not whitelisted)");
    }
}
