package com.ultimateimprovments.maintenance;

import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.util.Broadcast;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Maintenance mode manager.
 * <p>
 * When maintenance is enabled, only the whitelist can join the server.
 * All settings and the whitelist are stored in SQLite (maintenance_whitelist + maintenance_meta),
 * except the kick_message and message texts — those live in
 * {@code config.yml#messages.maintenance.*} (or fall back to {@code #messages_en.*}).
 */
public class MaintenanceManager implements Listener {

    private static MaintenanceManager instance;

    private boolean maintenanceMode = false;
    private BukkitRunnable scheduledTask = null;

    private MaintenanceManager() {}

    public static void init() {
        instance = new MaintenanceManager();
        Bukkit.getPluginManager().registerEvents(instance, Main.getInstance());
        instance.loadFromDb();
    }

    public static MaintenanceManager getInstance() {
        return instance;
    }

    /**
     * Checks whether the maintenance feature is enabled in config.yml.
     * Disables the whole system: /ui maint will be unavailable, joins are not blocked.
     */
    public boolean isFeatureEnabled() {
        return Main.getInstance().getConfig().getBoolean("maintenance.enabled", true);
    }

    // =========================
    // DATABASE
    // =========================

    /**
     * Loads the enabled flag from the DB.
     * On the first run (no DB record) — status = false (maintenance off).
     * The config.yml "maintenance.enabled" flag now does NOT affect the status —
     * it only enables/disables the feature itself (checked in isFeatureEnabled()).
     */
    public void loadFromDb() {
        try (Connection con = DatabaseManager.getConnection()) {
            try (PreparedStatement st = con.prepareStatement(
                     "SELECT value FROM maintenance_meta WHERE key = ?")) {
                st.setString(1, "enabled");
                try (ResultSet rs = st.executeQuery()) {
                    if (rs.next()) {
                        maintenanceMode = Boolean.parseBoolean(rs.getString("value"));
                    } else {
                        // First run — start with the status off.
                        // Previously this migrated from config.yml; now
                        // config.yml only toggles the feature on/off, and the status = false.
                        maintenanceMode = false;
                        try (PreparedStatement ins = con.prepareStatement(
                                "INSERT OR REPLACE INTO maintenance_meta (key, value) VALUES (?, ?)")) {
                            ins.setString(1, "enabled");
                            ins.setString(2, "false");
                            ins.executeUpdate();
                        }
                    }
                }
            }
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[Maintenance] Failed to load state from DB", e);
        }
    }

    /**
     * Saves the enabled flag to the DB.
     */
    private void saveStateToDb() {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "INSERT OR REPLACE INTO maintenance_meta (key, value) VALUES (?, ?)")) {
            st.setString(1, "enabled");
            st.setString(2, String.valueOf(maintenanceMode));
            st.executeUpdate();
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[Maintenance] Failed to save state to DB", e);
        }
    }

    // =========================
    // MAINTENANCE STATE
    // =========================

    public boolean isMaintenanceMode() {
        return maintenanceMode;
    }

    /**
     * Enables maintenance mode immediately.
     */
    public void enable() {
        cancelScheduled();
        maintenanceMode = true;
        saveStateToDb();
        kickNonWhitelisted();
        broadcastMaintenance(true, null);
    }

    /**
     * Enables maintenance mode after a delay.
     */
    public void enableLater(long delayTicks) {
        cancelScheduled();
        String timeStr = formatTime(delayTicks);
        broadcastScheduled(true, timeStr);
        scheduledTask = new BukkitRunnable() {
            @Override
            public void run() {
                enable();
            }
        };
        scheduledTask.runTaskLater(Main.getInstance(), delayTicks);
    }

    /**
     * Disables maintenance mode immediately.
     */
    public void disable() {
        cancelScheduled();
        maintenanceMode = false;
        saveStateToDb();
        broadcastMaintenance(false, null);
    }

    /**
     * Disables maintenance mode after a delay.
     */
    public void disableLater(long delayTicks) {
        cancelScheduled();
        String timeStr = formatTime(delayTicks);
        broadcastScheduled(false, timeStr);
        scheduledTask = new BukkitRunnable() {
            @Override
            public void run() {
                disable();
            }
        };
        scheduledTask.runTaskLater(Main.getInstance(), delayTicks);
    }

    public void cancelScheduled() {
        if (scheduledTask != null) {
            scheduledTask.cancel();
            scheduledTask = null;
        }
    }

    public boolean hasScheduledTask() {
        return scheduledTask != null;
    }

    // =========================
    // WHITELIST — SQLite
    // =========================

    public List<String> getWhitelistNames() {
        List<String> names = new ArrayList<>();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "SELECT player_name FROM maintenance_whitelist ORDER BY player_name");
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                names.add(rs.getString("player_name"));
            }
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[Maintenance] Failed to list whitelist", e);
        }
        return names;
    }

    public boolean isWhitelisted(UUID uuid) {
        // Check by the player's name
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        if (name == null) return false;
        return isWhitelisted(name);
    }

    public boolean isWhitelisted(String playerName) {
        String lower = playerName.toLowerCase().trim();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "SELECT 1 FROM maintenance_whitelist WHERE player_name = ?")) {
            st.setString(1, lower);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.FINE, "[Maintenance] Check error for: " + lower, e);
            return false;
        }
    }

    /**
     * Adds a player to the whitelist.
     */
    public boolean addWhitelist(String playerName) {
        if (playerName == null || playerName.isBlank()) return false;
        String lower = playerName.toLowerCase().trim();

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "INSERT OR IGNORE INTO maintenance_whitelist (player_name) VALUES (?)")) {
            st.setString(1, lower);
            int rows = st.executeUpdate();
            if (rows > 0) {
                ConsoleLogger.info("[Maintenance] Added to whitelist: " + lower);
                return true;
            }
            return false; // already present
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[Maintenance] Failed to add: " + lower, e);
            return false;
        }
    }

    /**
     * Removes a player from the whitelist.
     */
    public boolean removeWhitelist(String playerName) {
        if (playerName == null || playerName.isBlank()) return false;
        String lower = playerName.toLowerCase().trim();

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "DELETE FROM maintenance_whitelist WHERE player_name = ?")) {
            st.setString(1, lower);
            int rows = st.executeUpdate();
            if (rows > 0) {
                ConsoleLogger.info("[Maintenance] Removed from whitelist: " + lower);
                return true;
            }
            return false; // not found
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[Maintenance] Failed to remove: " + lower, e);
            return false;
        }
    }

    // =========================
    // KICK LOGIC
    // =========================

    private void kickNonWhitelisted() {
        String kickMessage = MessageUtil.legacy(
                MessagesManager.getString("maintenance.kick_message",
                        "<red>⛏ Server is currently under maintenance!</red>\\n<gray>Please come back later.</gray>")
        );
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isWhitelisted(player.getUniqueId())) {
                player.kickPlayer(kickMessage);
            }
        }
    }

    // =========================
    // LOGIN LISTENER
    // =========================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        // Check: is the maintenance feature enabled in config.yml
        if (!isFeatureEnabled() || !maintenanceMode) return;

        Player player = event.getPlayer();
        if (isWhitelisted(player.getUniqueId())) return;

        String kickMessage = MessageUtil.legacy(
                MessagesManager.getString("maintenance.kick_message",
                        "<red>⛏ Server is currently under maintenance!</red>\\n<gray>Please come back later.</gray>")
        );
        event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, kickMessage);
    }

    // =========================
    // BROADCASTS — from config.yml:messages.maintenance.* (with a messages_en.* fallback)
    // =========================

    private void broadcastMaintenance(boolean enabled, String timeStr) {
        String key = enabled ? "maintenance.messages.enabled" : "maintenance.messages.disabled";
        String def = enabled
                ? "<red>⛏</red> <white>Maintenance mode </white><green>ENABLED</green>"
                : "<green>✔</green> <white>Maintenance mode </white><red>DISABLED</red>";

        String msg = MessagesManager.getString(key, def);
        Broadcast.send(msg);
    }

    private void broadcastScheduled(boolean enable, String timeStr) {
        String key = enable ? "maintenance.messages.scheduled_enable" : "maintenance.messages.scheduled_disable";
        String def = enable
                ? "<yellow>⏰</yellow> <white>Maintenance will be enabled in </white><yellow>%time%</yellow>"
                : "<yellow>⏰</yellow> <white>Maintenance will be disabled in </white><yellow>%time%</yellow>";

        String msg = MessagesManager.getString(key, def).replace("%time%", timeStr);
        Broadcast.send(msg);
    }

    // =========================
    // TIME HELPERS
    // =========================

    private static String formatTime(long ticks) {
        long totalSeconds = ticks / 20;
        if (totalSeconds < 60) {
            return totalSeconds + "s";
        }
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        if (minutes < 60) {
            return minutes + "m " + seconds + "s";
        }
        long hours = minutes / 60;
        minutes = minutes % 60;
        if (hours < 24) {
            return hours + "h " + minutes + "m";
        }
        long days = hours / 24;
        hours = hours % 24;
        return days + "d " + hours + "h";
    }

    public static long parseTimeToTicks(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return -1;
        String lower = timeStr.toLowerCase().trim();
        try {
            if (lower.endsWith("s")) {
                long secs = Long.parseLong(lower.substring(0, lower.length() - 1));
                return secs * 20;
            } else if (lower.endsWith("m")) {
                long mins = Long.parseLong(lower.substring(0, lower.length() - 1));
                return mins * 20 * 60;
            } else if (lower.endsWith("h")) {
                long hours = Long.parseLong(lower.substring(0, lower.length() - 1));
                return hours * 20 * 60 * 60;
            } else if (lower.endsWith("d")) {
                long days = Long.parseLong(lower.substring(0, lower.length() - 1));
                return days * 20 * 60 * 60 * 24;
            }
        } catch (NumberFormatException e) {
            return -1;
        }
        return -1;
    }
}
