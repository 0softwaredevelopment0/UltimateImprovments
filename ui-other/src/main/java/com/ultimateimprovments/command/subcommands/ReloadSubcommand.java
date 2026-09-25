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
                            ConsoleLogger.info("[Reload] Shutting down plugins (sync)...");
                            // The whole UI-* family must go through a real disable/enable
                            // cycle: feature listeners and tasks are registered under
                            // different plugin handles (UI-Core, UI-Other, UI-MBS, ...),
                            // so touching UI-Core alone leaves stale registrations and
                            // dead modules after the reload.
                            java.util.List<org.bukkit.plugin.Plugin> family = new java.util.ArrayList<>();
                            for (org.bukkit.plugin.Plugin p : org.bukkit.Bukkit.getPluginManager().getPlugins()) {
                                if (p != plugin && p.getName().startsWith("UI-")) family.add(p);
                            }
                            // getPlugins() is dependency (load) order: disable dependents first
                            for (int i = family.size() - 1; i >= 0; i--) {
                                org.bukkit.Bukkit.getPluginManager().disablePlugin(family.get(i));
                            }
                            new PluginShutdown(plugin).shutdownPlugin();

                            ConsoleLogger.info("[Reload] Reloading config...");
                            // Composite per-addon backend: AddonConfigManager.init() salvages broken
                            // TOML lines, repairs missing keys from the bundled fragments and rebuilds
                            // the CompositeConfig routing (configs/UI-<Addon>.toml files).
                            plugin.reloadConfig();

                            ConsoleLogger.info("[Reload] Starting up plugins (sync)...");
                            // Clear the JAR file caches of the disabled UI-* classloaders.
                            // After /ui updatejar or /ui swapjar the on-disk JAR was replaced;
                            // if the old (cached) jar handle is reopened — e.g. while reading
                            // plugin.yml — the JVM throws a fatal ZipError from the cached
                            // central directory. Dropping the caches makes the JVM re-read
                            // the fresh JAR from disk.
                            com.ultimateimprovments.core.PluginStartup.clearJarFileCaches();
                            new PluginStartup(plugin).startupPlugin();
                            // Re-enable in load order (dependencies before dependents)
                            for (org.bukkit.plugin.Plugin p : family) {
                                org.bukkit.Bukkit.getPluginManager().enablePlugin(p);
                            }

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
