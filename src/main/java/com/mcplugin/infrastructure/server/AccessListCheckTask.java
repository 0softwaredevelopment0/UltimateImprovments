package com.mcplugin.infrastructure.server;

import com.mcplugin.infrastructure.blacklist.BlacklistManager;
import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.opwhitelist.OpWhitelistManager;
import com.mcplugin.infrastructure.util.MessageUtil;
import com.mcplugin.infrastructure.whitelist.WhitelistManager;
import com.mcplugin.infrastructure.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 🔄 AccessListCheckTask — периодическая проверка всех онлайн-игроков
 * по whitelist, blacklist и opwhitelist.
 * <p>
 * Запускается с интервалом из config.yml → access_control.check_interval_ticks.
 * При обнаружении нарушителя — кикает или снимает OP.
 * <p>
 * Дублирует логику {@link WhitelistManager#onPlayerLogin},
 * {@link BlacklistManager#onPlayerLogin} и {@link OpWhitelistManager#checkAndDeop}
 * для уже подключённых игроков (например если список изменился через БД напрямую).
 */
public class AccessListCheckTask extends BukkitRunnable {

    private static int taskId = -1;

    /**
     * Запускает периодическую задачу с интервалом из конфига.
     *
     * @param plugin экземпляр плагина
     */
    public static void start(Main plugin) {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }

        int interval = plugin.getConfig().getInt("access_control.check_interval_ticks", 20);
        if (interval <= 0) {
            ConsoleLogger.info("[AccessCheck] Periodic check disabled (interval <= 0).");
            return;
        }

        taskId = new AccessListCheckTask().runTaskTimer(plugin, interval, interval).getTaskId();
        ConsoleLogger.info("[AccessCheck] Started with interval " + interval + " ticks.");
    }

    /**
     * Останавливает задачу.
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
        boolean opWhitelistEnabled = OpWhitelistManager.isEnabled();

        if (!whitelistEnabled && !blacklistEnabled && !opWhitelistEnabled) {
            return; // ничего не включено — нечего проверять
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            String name = player.getName();

            // =========================
            // BLACKLIST CHECK
            // =========================
            if (blacklistEnabled && BlacklistManager.isBlacklisted(name)) {
                player.kickPlayer(MessageUtil.legacy(
                        "<red>⛔ You are blacklisted from this server!</red>"
                ));
                continue; // игрок уже кикнут
            }

            // =========================
            // WHITELIST CHECK
            // =========================
            if (whitelistEnabled && !WhitelistManager.isWhitelisted(name)) {
                player.kickPlayer(MessageUtil.legacy(
                        "<red>⛔ You are not whitelisted on this server!</red>\n" +
                        "<gray>Use the MC-Plugin whitelist system.</gray>"
                ));
                continue;
            }

            // =========================
            // OP WHITELIST CHECK (через OpWhitelistManager)
            // =========================
            if (opWhitelistEnabled && player.isOp()) {
                if (!OpWhitelistManager.isWhitelisted(name)) {
                    player.setOp(false);
                    player.sendMessage(MessageUtil.parse(
                            "<red>⛔</red> <white>Your operator status has been removed — you are not in the OP whitelist.</white>"
                    ));
                    ConsoleLogger.info("[OpWhitelist] Removed OP from " + name + " (not whitelisted)");
                }
            }
        }
    }
}
