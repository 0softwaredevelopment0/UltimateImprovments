package com.ultimateimprovments.server;

import com.ultimateimprovments.whitelist.BlacklistManager;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.whitelist.WhitelistManager;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 🔄 AccessListCheckTask — periodic check of all online players
 * against the join whitelist and blacklist.
 * <p>
 * Runs with the interval from config.yml → access_control.check_interval_ticks.
 * On finding a violator — kicks them.
 * <p>
 * Duplicates the logic of {@link WhitelistManager#onPlayerLogin} and
 * {@link BlacklistManager#onPlayerLogin} for already connected players
 * (e.g. if the list changed directly via the DB).
 * <p>
 * The OP lists (opwhitelist/opblacklist) have their own shared periodic
 * check — {@link OpListsCheckTask} (interval op_lists.check_interval_ticks).
 */
public class AccessListCheckTask extends BukkitRunnable {

    private static int taskId = -1;

    /**
     * Starts the periodic task with the interval from the config.
     *
     * @param plugin the plugin instance
     */
    public static void start(Main plugin) {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }

        int interval = plugin.getConfig().getInt("access_control.check_interval_ticks", 20);
        if (interval <= 0) {
            ConsoleLogger.info("[AccessCheck] Periodic check disabled (interval <= 0).");
            taskId = -1;
            return;
        }

        taskId = new AccessListCheckTask().runTaskTimer(plugin, interval, interval).getTaskId();
        ConsoleLogger.info("[AccessCheck] Started with interval " + interval + " ticks.");
    }

    /**
     * Stops the task.
     */
    public static void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    @Override
    public void run() {
        boolean whitelistEnabled = WhitelistManager.isEnabled();
        boolean blacklistEnabled = BlacklistManager.isEnabled();

        if (!whitelistEnabled && !blacklistEnabled) {
            return; // nothing enabled — nothing to check
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            String name = player.getName();

            // =========================
            // BLACKLIST CHECK
            // =========================
            if (blacklistEnabled && BlacklistManager.isBlacklisted(name)) {
                player.kick(MessageUtil.parse(
                        "<red>⛔ You are blacklisted from this server!</red>"
                ));
                continue; // player already kicked
            }

            // =========================
            // WHITELIST CHECK
            // =========================
            if (whitelistEnabled && !WhitelistManager.isWhitelisted(name)) {
                player.kick(MessageUtil.parse(
                        "<red>⛔ You are not whitelisted on this server!</red>\n" +
                        "<gray>Use the UltimateImprovments whitelist system.</gray>"
                ));
                continue;
            }
        }
    }
}
