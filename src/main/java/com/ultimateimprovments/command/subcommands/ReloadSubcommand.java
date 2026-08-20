package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;

import com.ultimateimprovments.config.ConfigCrashSalvage;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.core.PluginShutdown;
import com.ultimateimprovments.core.PluginStartup;
import com.ultimateimprovments.structure.StructureChunkTracker;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * /ui reload — asynchronous plugin reload.
 * <p>
 * Phase 1 (async): data saving.
 * Phase 2 (sync): shutdown + reloadConfig + startup.
 */
public final class ReloadSubcommand {

    private ReloadSubcommand() {}

    private static boolean reloadInProgress = false;

    public static boolean execute(CommandSender sender) {
        if (sender instanceof Player player && !player.hasPermission("ui.command.reload")) {
            CommandErrors.noPermission(player);
            return true;
        }

        if (reloadInProgress) {
            sender.sendMessage(MessageUtil.parse("<yellow>Reload already in progress, please wait..."));
            return true;
        }
        reloadInProgress = true;

        sender.sendMessage(MessageUtil.parse("<yellow>Reloading UltimateImprovments asynchronously..."));
        Main plugin = Main.getInstance();

        new BukkitRunnable() {
            @Override
            public void run() {
                long start = System.currentTimeMillis();

                try {
                    ConsoleLogger.info("[Reload] Saving persistent data (async)...");
                    StructureChunkTracker.save();
                } catch (Exception e) {
                    ConsoleLogger.warn("[Reload] Async save warning: " + e.getMessage());
                }

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        try {
                            ConsoleLogger.info("[Reload] Shutting down modules (sync)...");
                            new PluginShutdown(plugin).shutdownPlugin();

                            ConsoleLogger.info("[Reload] Reloading config...");
                            // Битый YAML: reloadConfig() в Paper-26 молча глотает ошибку парсинга,
                            // поэтому спасаем файл заранее — «синтаксический краш → игнор секции»:
                            // удаляются только сломанные секции (с бэкапом), остальное сохраняется,
                            // а дефолты удалённых секций допишет ConfigRepairManager при старте.
                            ConfigCrashSalvage.salvage(plugin);
                            plugin.reloadConfig();

                            ConsoleLogger.info("[Reload] Starting up modules (sync)...");
                            new PluginStartup(plugin).startupPlugin();

                            long time = System.currentTimeMillis() - start;
                            sender.sendMessage(MessageUtil.parse("<dark_green>✔ <green>Success: <gray>Reload complete."));
                            sender.sendMessage(MessageUtil.parse("<dark_green>✔ <green>Success: <gray>Reload time: <yellow>" + time + "ms"));
                            ConsoleLogger.info("[ULTIMATEIMPROVMENTS] Reload complete in " + time + "ms");
                        } catch (Exception e) {
                            sender.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Error: <gray>Reload failed! Check console."));
                            ConsoleLogger.error("[ULTIMATEIMPROVMENTS] Reload failed: " + e.getMessage());
                            e.printStackTrace();
                        } finally {
                            reloadInProgress = false;
                        }
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
        return true;
    }
}
