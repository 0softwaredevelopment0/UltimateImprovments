package com.ultimateimprovments.whitelist;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * 🛡 OP Blacklist — operators listed here lose their OP automatically.
 * <p>
 * Mirrors {@link OpWhitelistManager} but inverted: any online player who has
 * OP and is on the {@code op_blacklist} list gets OP removed. Checks run on
 * join, on add and periodically with the configured interval.
 * <p>
 * The master switch and the check interval live in the config
 * ({@code [opblacklist]} in UI-Punish.toml); {@code /ui opblacklist on|off}
 * writes the switch back to the config. Names are stored in SQLite
 * (table {@code op_blacklist}).
 * <p>
 * Commands: /ui opblacklist on|off|add|remove|list (+ timed variants).
 */
public class OpBlacklistManager implements Listener {

    private static boolean enabled = true;
    private static int taskId = -1;

    // ════════════════════════════════════════
    // INIT
    // ════════════════════════════════════════
    public static void init(Main plugin) {
        load();
        startTask(plugin);
        plugin.getServer().getPluginManager().registerEvents(new OpBlacklistManager(), plugin);
    }

    public static void shutdown() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    // ════════════════════════════════════════
    // LOAD (config + stats)
    // ════════════════════════════════════════
    public static void load() {
        // Master switch + interval live in the config ([opblacklist] in UI-Punish.toml)
        enabled = Main.getInstance().getConfig().getBoolean("opblacklist.enabled", true);

        int count = 0;
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement("SELECT COUNT(*) FROM op_blacklist");
             ResultSet rs = st.executeQuery()) {
            if (rs.next()) {
                count = rs.getInt(1);
            }
        } catch (Exception e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[OpBlacklist] Failed to count", e);
        }

        ConsoleLogger.info("[OpBlacklist] Loaded " + count + " players from SQLite, enabled=" + enabled);
    }

    // ════════════════════════════════════════
    // PERIODIC CHECK TASK
    // ════════════════════════════════════════
    private static void startTask(Main plugin) {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }

        long intervalSeconds = Main.getInstance().getConfig().getLong("opblacklist.check_interval_seconds", 30);
        if (intervalSeconds <= 0) {
            ConsoleLogger.info("[OpBlacklist] Periodic check disabled (check_interval_seconds <= 0).");
            return;
        }

        long intervalTicks = intervalSeconds * 20L;
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, OpBlacklistManager::sweepOnline,
                intervalTicks, intervalTicks).getTaskId();
        ConsoleLogger.info("[OpBlacklist] Periodic check started with interval " + intervalSeconds + "s.");
    }

    /** Checks every online player with OP against the blacklist. */
    private static void sweepOnline() {
        if (!enabled) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkAndDeop(player);
        }
    }

    // ════════════════════════════════════════
    // GETTERS
    // ════════════════════════════════════════
    public static boolean isEnabled() {
        return enabled;
    }

    public static List<String> getBlacklistNames() {
        List<String> result = new ArrayList<>();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "SELECT player_name FROM op_blacklist ORDER BY player_name");
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                result.add(rs.getString("player_name"));
            }
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[OpBlacklist] Failed to list players", e);
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
                     "INSERT OR IGNORE INTO op_blacklist (player_name) VALUES (?)")) {
            st.setString(1, lower);
            int rows = st.executeUpdate();
            if (rows > 0) {
                ConsoleLogger.info("[OpBlacklist] Added: " + lower);

                // Strip OP immediately if the player is online
                Player online = Bukkit.getPlayerExact(playerName);
                checkAndDeop(online);
                return true;
            }
            return false; // already present
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[OpBlacklist] Failed to add: " + lower, e);
            return false;
        }
    }

    public static boolean remove(String playerName) {
        if (playerName == null || playerName.isBlank()) return false;
        String lower = playerName.toLowerCase().trim();

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "DELETE FROM op_blacklist WHERE player_name = ?")) {
            st.setString(1, lower);
            int rows = st.executeUpdate();
            if (rows > 0) {
                ConsoleLogger.info("[OpBlacklist] Removed: " + lower);
                return true;
            }
            return false; // not found
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[OpBlacklist] Failed to remove: " + lower, e);
            return false;
        }
    }

    // ════════════════════════════════════════
    // TOGGLE (persisted in the config)
    // ════════════════════════════════════════
    public static boolean setEnabled(boolean val) {
        if (enabled == val) return false;
        enabled = val;

        try {
            Main.getInstance().getConfig().set("opblacklist.enabled", val);
            Main.getInstance().saveConfig();
        } catch (Exception e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[OpBlacklist] Failed to save enabled state", e);
        }

        if (enabled) {
            // Instant check of all online players on enable
            sweepOnline();
        }
        return true;
    }

    // ════════════════════════════════════════
    // CHECK
    // ════════════════════════════════════════
    public static boolean isBlacklisted(String playerName) {
        String lower = playerName.toLowerCase().trim();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "SELECT 1 FROM op_blacklist WHERE player_name = ?")) {
            st.setString(1, lower);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.FINE, "[OpBlacklist] Check error for: " + lower, e);
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
    /** Removes OP from the player when they are on the OP blacklist. */
    public static void checkAndDeop(Player player) {
        if (player == null || !player.isOnline()) return;
        if (!enabled) return;
        if (!player.isOp()) return;

        if (!isBlacklisted(player.getName())) return;

        // Player is OP and blacklisted — remove OP
        player.setOp(false);
        player.sendMessage(MessageUtil.parse(
                "<red>⛔</red> <white>Your operator status has been removed — you are in the OP blacklist.</white>"
        ));
        ConsoleLogger.info("[OpBlacklist] Removed OP from " + player.getName() + " (blacklisted)");
    }
}
