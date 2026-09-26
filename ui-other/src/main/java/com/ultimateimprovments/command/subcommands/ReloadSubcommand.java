package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.core.PluginReloadCoordinator;
import com.ultimateimprovments.structure.StructureChunkTracker;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * /ui reload — asynchronous plugin reload.
 * <p>
 * Phase 1 (async, this class): data saving.
 * Phase 2 (sync, {@link PluginReloadCoordinator} in ui-core): shutdown + reloadConfig + startup.
 * <p>
 * The sync phase must NOT run from this addon's classloader: the cycle disables every
 * other UI-* plugin (including this one), which closes its JAR and kills any not-yet-loaded
 * class with {@code zip file closed} — aborting the reload and leaving the family disabled.
 * UI-Core is never disabled in the cycle, so the coordinator runs under its handle.
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
                            PluginReloadCoordinator.runSyncPhase(plugin, sender, start);
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
