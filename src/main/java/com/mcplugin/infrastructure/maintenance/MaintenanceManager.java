package com.mcplugin.infrastructure.maintenance;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Maintenance mode manager.
 * <p>
 * When maintenance is enabled, only whitelisted players can join the server.
 * Non-whitelisted players are kicked with a configurable message.
 * Supports timed on/off with -time flag.
 */
public class MaintenanceManager implements Listener {

    private static MaintenanceManager instance;

    private boolean maintenanceMode = false;
    private final List<UUID> whitelist = new ArrayList<>();
    private BukkitRunnable scheduledTask = null;

    private MaintenanceManager() {}

    public static void init() {
        instance = new MaintenanceManager();
        Bukkit.getPluginManager().registerEvents(instance, Main.getInstance());
        instance.loadConfig();
    }

    public static MaintenanceManager getInstance() {
        return instance;
    }

    // =========================
    // CONFIG
    // =========================

    /**
     * Loads whitelist and maintenance state from config.yml.
     */
    public void loadConfig() {
        FileConfiguration cfg = Main.getInstance().getConfig();

        // Load maintenance mode state
        maintenanceMode = cfg.getBoolean("maintenance.enabled", false);

        // Load whitelist (stored as string list of player names for readability)
        whitelist.clear();
        List<String> names = cfg.getStringList("maintenance.whitelist");
        for (String name : names) {
            @SuppressWarnings("deprecation")
            UUID uuid = Bukkit.getOfflinePlayer(name).getUniqueId();
            if (!whitelist.contains(uuid)) {
                whitelist.add(uuid);
            }
        }
    }

    /**
     * Saves current whitelist and maintenance state to config.yml.
     */
    public void saveConfig() {
        FileConfiguration cfg = Main.getInstance().getConfig();

        // Save whitelist as player names (human-readable)
        List<String> names = new ArrayList<>();
        for (UUID uuid : whitelist) {
            @SuppressWarnings("deprecation")
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            if (name != null) {
                names.add(name);
            } else {
                names.add(uuid.toString());
            }
        }
        cfg.set("maintenance.whitelist", names);
        cfg.set("maintenance.enabled", maintenanceMode);

        Main.getInstance().saveConfig();
        // Reload to keep in-memory and on-disk in sync
        Main.getInstance().reloadConfig();
        // Re-read our own whitelist from the freshly saved config
        loadConfig();
    }

    // =========================
    // MAINTENANCE STATE
    // =========================

    public boolean isMaintenanceMode() {
        return maintenanceMode;
    }

    /**
     * Enables maintenance mode immediately.
     * Kicks all non-whitelisted players.
     */
    public void enable() {
        cancelScheduled();
        maintenanceMode = true;
        saveConfig();
        kickNonWhitelisted();
        broadcastMaintenance(true, null);
    }

    /**
     * Enables maintenance mode after a delay.
     * @param delayTicks ticks to wait before enabling
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
     * All players can join freely.
     */
    public void disable() {
        cancelScheduled();
        maintenanceMode = false;
        saveConfig();
        broadcastMaintenance(false, null);
    }

    /**
     * Disables maintenance mode after a delay.
     * @param delayTicks ticks to wait before disabling
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

    /**
     * Cancels any pending scheduled maintenance toggle.
     */
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
    // WHITELIST
    // =========================

    public List<String> getWhitelistNames() {
        List<String> names = new ArrayList<>();
        for (UUID uuid : whitelist) {
            @SuppressWarnings("deprecation")
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            if (name != null) {
                names.add(name);
            } else {
                names.add(uuid.toString());
            }
        }
        return names;
    }

    public boolean isWhitelisted(UUID uuid) {
        return whitelist.contains(uuid);
    }

    public boolean isWhitelisted(String playerName) {
        @SuppressWarnings("deprecation")
        UUID uuid = Bukkit.getOfflinePlayer(playerName).getUniqueId();
        return whitelist.contains(uuid);
    }

    /**
     * Adds a player to the whitelist.
     * @param playerName the player's name
     * @return true if added, false if already whitelisted
     */
    public boolean addWhitelist(String playerName) {
        @SuppressWarnings("deprecation")
        UUID uuid = Bukkit.getOfflinePlayer(playerName).getUniqueId();
        if (whitelist.contains(uuid)) {
            return false;
        }
        whitelist.add(uuid);
        saveConfig();
        return true;
    }

    /**
     * Removes a player from the whitelist.
     * @param playerName the player's name
     * @return true if removed, false if not found
     */
    public boolean removeWhitelist(String playerName) {
        @SuppressWarnings("deprecation")
        UUID uuid = Bukkit.getOfflinePlayer(playerName).getUniqueId();
        boolean removed = whitelist.remove(uuid);
        if (removed) {
            saveConfig();
        }
        return removed;
    }

    // =========================
    // KICK LOGIC
    // =========================

    /**
     * Kicks all online players who are not in the whitelist.
     */
    private void kickNonWhitelisted() {
        String kickMessage = MessageUtil.legacy(
                Main.getInstance().getConfig().getString("maintenance.kick_message",
                        "<red>⛏ Server is currently under maintenance!</red>\n<gray>Please come back later.</gray>")
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
        if (!maintenanceMode) return;

        Player player = event.getPlayer();
        if (isWhitelisted(player.getUniqueId())) return;

        String kickMessage = MessageUtil.legacy(
                Main.getInstance().getConfig().getString("maintenance.kick_message",
                        "<red>⛏ Server is currently under maintenance!</red>\n<gray>Please come back later.</gray>")
        );
        event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, kickMessage);
    }

    // =========================
    // BROADCASTS
    // =========================

    private void broadcastMaintenance(boolean enabled, String timeStr) {
        String key = enabled ? "maintenance.messages.enabled" : "maintenance.messages.disabled";
        String def = enabled
                ? "<red>⛏</red> <white>Maintenance mode </white><green>ENABLED</green>"
                : "<green>✔</green> <white>Maintenance mode </white><red>DISABLED</red>";

        String msg = Main.getInstance().getConfig().getString(key, def);
        Bukkit.broadcast(MessageUtil.parse(msg));
    }

    private void broadcastScheduled(boolean enable, String timeStr) {
        String key = enable ? "maintenance.messages.scheduled_enable" : "maintenance.messages.scheduled_disable";
        String def = enable
                ? "<yellow>⏰</yellow> <white>Maintenance will be enabled in </white><yellow>{time}</yellow>"
                : "<yellow>⏰</yellow> <white>Maintenance will be disabled in </white><yellow>{time}</yellow>";

        String msg = Main.getInstance().getConfig().getString(key, def).replace("{time}", timeStr);
        Bukkit.broadcast(MessageUtil.parse(msg));
    }

    /**
     * Formats ticks into a human-readable time string.
     */
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

    /**
     * Parses a -time flag value like "30s", "5m", "2h", "1d" into ticks.
     * @param timeStr the time string (e.g. "30s", "5m", "2h", "1d")
     * @return ticks, or -1 if invalid
     */
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
