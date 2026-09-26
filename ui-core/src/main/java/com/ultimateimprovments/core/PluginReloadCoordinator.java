package com.ultimateimprovments.core;

import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes the synchronous phase of {@code /ui reload} from the CORE plugin's classloader.
 * <p>
 * <b>Why this lives in ui-core:</b> the reload cycle disables every other {@code UI-*}
 * plugin — including the addon whose subcommand started the reload. Disabling a plugin
 * closes its JAR, so ANY class not yet loaded from that JAR throws
 * {@code IllegalStateException: zip file closed} and aborts the reload midway, leaving
 * the whole family disabled. UI-Core itself is never disabled in this cycle
 * ({@code p != plugin}), so running the whole phase here guarantees the executing
 * classloader stays open.
 * <p>
 * Sequence: disable family (reverse load order) → shutdown core subsystems →
 * reload config → reset JarFile caches (post-updatejar safety) → startup core →
 * enable family (load order). On any failure the family plugins are re-enabled
 * so the server is not left half-dead.
 */
public final class PluginReloadCoordinator {

    private PluginReloadCoordinator() {}

    /**
     * Runs the sync phase. Must be invoked on the main thread from a task scheduled
     * under the CORE plugin handle (so disabling family plugins cannot cancel it).
     *
     * @param plugin     the UI-Core plugin instance (never disabled in this cycle)
     * @param sender     receiver of the result messages
     * @param startMillis timestamp when the whole reload started (for the timing report)
     */
    public static void runSyncPhase(Main plugin, CommandSender sender, long startMillis) {
        // Family snapshot in dependency (load) order.
        List<Plugin> family = new ArrayList<>();
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            if (p != plugin && p.getName().startsWith("UI-")) family.add(p);
        }

        try {
            ConsoleLogger.info("[Reload] Shutting down plugins (sync)...");
            // The whole UI-* family must go through a real disable/enable cycle:
            // feature listeners and tasks are registered under different plugin
            // handles (UI-Core, UI-Other, UI-MBS, ...), so touching UI-Core alone
            // leaves stale registrations and dead modules after the reload.
            // Disable dependents first (reverse load order).
            for (int i = family.size() - 1; i >= 0; i--) {
                Bukkit.getPluginManager().disablePlugin(family.get(i));
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
            // if the old (cached) jar handle is reopened the JVM can throw a fatal
            // ZipError from the stale central directory. Dropping the caches makes
            // the JVM re-read the fresh JAR from disk.
            PluginStartup.clearJarFileCaches();
            new PluginStartup(plugin).startupPlugin();

            // Re-enable in load order (dependencies before dependents).
            for (Plugin p : family) {
                Bukkit.getPluginManager().enablePlugin(p);
            }

            long time = System.currentTimeMillis() - startMillis;
            sender.sendMessage(MessageUtil.parse("<dark_green>✔ <green>Success: <gray>Reload complete."));
            sender.sendMessage(MessageUtil.parse("<dark_green>✔ <green>Success: <gray>Reload time: <yellow>" + time + "ms"));
            ConsoleLogger.info("[ULTIMATEIMPROVMENTS] Reload complete in " + time + "ms");
        } catch (Exception e) {
            sender.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Error: <gray>Reload failed! Check console."));
            ConsoleLogger.error("[ULTIMATEIMPROVMENTS] Reload failed: " + e.getMessage());
            e.printStackTrace();

            // Recovery: never leave the family half-disabled — re-enable everything
            // that is still off so listeners/tasks come back online.
            int revived = 0;
            for (Plugin p : family) {
                if (!p.isEnabled()) {
                    try {
                        Bukkit.getPluginManager().enablePlugin(p);
                        revived++;
                    } catch (Exception reEx) {
                        ConsoleLogger.error("[Reload] Could not re-enable " + p.getName() + ": " + reEx.getMessage());
                    }
                }
            }
            if (revived > 0) {
                ConsoleLogger.warn("[Reload] Recovery: re-enabled " + revived + " plugin(s) after the failed reload.");
            }
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>⚠ <gray>Some systems may be in a partial state — a server restart is recommended."));
        }
    }
}
