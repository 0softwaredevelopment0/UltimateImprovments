package com.ultimateimprovments.server;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.whitelist.OpBlacklistManager;
import com.ultimateimprovments.whitelist.OpWhitelistManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 🔄 OpListsCheckTask — ONE periodic check for BOTH operator lists:
 * the OP whitelist (OP without an entry → deop) and the OP blacklist
 * (OP with an entry → deop).
 * <p>
 * Runs with the shared interval from the config:
 * {@code op_lists.check_interval_ticks} (0 disables the periodic check —
 * join/add/enable checks still work). Started by UI-Punish; a soft /ui reload
 * restarts it via the startup path.
 * <p>
 * The plain (join) whitelist/blacklist kicks stay in {@link AccessListCheckTask}.
 */
public class OpListsCheckTask extends BukkitRunnable {

    private static int taskId = -1;

    /**
     * Starts the periodic task with the shared interval from the config.
     *
     * @param plugin the plugin instance
     */
    public static void start(Main plugin) {
        stop();

        int interval = plugin.getConfig().getInt("op_lists.check_interval_ticks", 20);
        if (interval <= 0) {
            ConsoleLogger.info("[OpListsCheck] Periodic check disabled (op_lists.check_interval_ticks <= 0).");
            taskId = -1;
            return;
        }

        taskId = new OpListsCheckTask().runTaskTimer(plugin, interval, interval).getTaskId();
        ConsoleLogger.info("[OpListsCheck] Started with interval " + interval + " ticks.");
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
        boolean whitelistEnabled = OpWhitelistManager.isEnabled();
        boolean blacklistEnabled = OpBlacklistManager.isEnabled();

        if (!whitelistEnabled && !blacklistEnabled) {
            return; // both OP lists disabled — nothing to check
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isOp()) continue;

            // OP whitelist: deop when the player has no entry (no-op when disabled)
            if (whitelistEnabled) {
                OpWhitelistManager.checkAndDeop(player);
            }

            // OP blacklist: deop when the player is listed (no-op when disabled,
            // also skipped when the whitelist check above just removed their OP)
            if (blacklistEnabled) {
                OpBlacklistManager.checkAndDeop(player);
            }
        }
    }
}
