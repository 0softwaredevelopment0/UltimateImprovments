package com.mcplugin.command.subcommands;

import com.mcplugin.core.Main;
import com.mcplugin.core.PluginShutdown;
import com.mcplugin.core.PluginStartup;
import com.mcplugin.structure.StructureChunkTracker;
import com.mcplugin.util.ConsoleLogger;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * /mp reload — асинхронная перезагрузка плагина.
 * <p>
 * Фаза 1 (async): сохранение данных.
 * Фаза 2 (sync): shutdown + reloadConfig + startup.
 */
public final class ReloadSubcommand {

    private ReloadSubcommand() {}

    private static boolean reloadInProgress = false;

    public static boolean execute(CommandSender sender) {
        if (sender instanceof Player player && !player.hasPermission("mcplugin.command.reload")) {
            player.sendMessage("§4❌ §cError: §7You don't have permission!");
            return true;
        }

        if (reloadInProgress) {
            sender.sendMessage("§eReload already in progress, please wait...");
            return true;
        }
        reloadInProgress = true;

        sender.sendMessage("§eReloading MC-Plugin asynchronously...");
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
                            plugin.reloadConfig();

                            ConsoleLogger.info("[Reload] Starting up modules (sync)...");
                            new PluginStartup(plugin).startupPlugin();

                            long time = System.currentTimeMillis() - start;
                            sender.sendMessage("§2✔ §aSuccess: §7Reload complete.");
                            sender.sendMessage("§2✔ §aSuccess: §7Reload time: §e" + time + "ms");
                            ConsoleLogger.info("[MCPLUGIN] Reload complete in " + time + "ms");
                        } catch (Exception e) {
                            sender.sendMessage("§4❌ §cError: §7Reload failed! Check console.");
                            ConsoleLogger.error("[MCPLUGIN] Reload failed: " + e.getMessage());
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
