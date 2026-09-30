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
 * <b>Soft reload — plugins are NEVER disabled or re-enabled.</b> On Paper,
 * {@code disablePlugin} closes the plugin's JAR (classloader) and
 * {@code enablePlugin} does NOT reopen it: any class not yet loaded then throws
 * {@code IllegalStateException: zip file closed}, leaving the addon a zombie
 * (broken commands, LuckPerms tab-complete errors). The old disable→enable cycle
 * is what killed the whole family after every reload.
 * <p>
 * Instead the cycle is: sweep every listener/task owned by ANY family plugin
 * handle → stop all modules (they share the singleton {@code ModuleManager} in
 * ui-core) → reload config → core startup → re-run every addon's startup logic
 * in place via {@link SoftReloadable#softReload()}. Plugin enable states and
 * classloaders stay untouched, so nothing can turn into a zombie.
 */
public final class PluginReloadCoordinator {

    private PluginReloadCoordinator() {}

    /**
     * Runs the sync phase. Must be invoked on the main thread from a task scheduled
     * under the CORE plugin handle.
     *
     * @param plugin     the UI-Core plugin instance
     * @param sender     receiver of the result messages
     * @param startMillis timestamp when the whole reload started (for the timing report)
     */
    public static void runSyncPhase(Main plugin, CommandSender sender, long startMillis) {
        // Family snapshot (dependency load order), core excluded.
        List<Plugin> family = new ArrayList<>();
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            if (p != plugin && p.getName().startsWith("UI-")) family.add(p);
        }

        try {
            ConsoleLogger.info("[Reload] Soft shutdown (plugins stay enabled, classloaders stay open)...");

            // 1) Global sweep: every listener and task owned by ANY family plugin
            //    handle, core included. Some addons register listeners under the
            //    UI-Core handle (e.g. PunishJoinListener), so sweeping only the
            //    owning addon would leave stale registrations behind.
            for (Plugin p : family) {
                org.bukkit.event.HandlerList.unregisterAll(p);
                Bukkit.getScheduler().cancelTasks(p);
            }

            // 2) Core subsystems + ALL family modules. Feature modules of every
            //    addon register into the singleton ModuleManager in ui-core, so
            //    this one shutdown covers the whole family.
            new PluginShutdown(plugin).shutdownPlugin();

            ConsoleLogger.info("[Reload] Reloading config...");
            // Composite per-addon backend: AddonConfigManager.init() salvages broken
            // TOML lines, repairs missing keys from the bundled fragments and rebuilds
            // the CompositeConfig routing (configs/UI-<Addon>.toml files).
            plugin.reloadConfig();

            ConsoleLogger.info("[Reload] Starting up (in place)...");
            PluginStartup.clearJarFileCaches();
            new PluginStartup(plugin).startupPlugin();

            // 3) Re-run every enabled addon's startup logic in place. Plugins that
            //    are (intentionally) disabled or predate the SoftReloadable
            //    interface are skipped, not force-enabled.
            int reloaded = 0;
            int skipped = 0;
            List<String> failed = new ArrayList<>();
            for (Plugin p : family) {
                if (!p.isEnabled() || !(p instanceof SoftReloadable s)) {
                    skipped++;
                    continue;
                }
                try {
                    s.softReload();
                    reloaded++;
                } catch (Exception e) {
                    failed.add(p.getName());
                    ConsoleLogger.error("[Reload] " + p.getName() + " soft startup failed: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            long time = System.currentTimeMillis() - startMillis;
            sender.sendMessage(MessageUtil.parse("<dark_green>✔ <green>Success: <gray>Reload complete."));
            sender.sendMessage(MessageUtil.parse(
                    "<dark_green>✔ <green>Success: <gray>Addons reloaded: <yellow>" + reloaded
                            + "</yellow><gray>, skipped: " + skipped
                            + (failed.isEmpty() ? "" : ", </gray><red>failed: " + String.join(", ", failed) + "</red>")
                            + ", <yellow>" + time + "ms"));
            ConsoleLogger.info("[ULTIMATEIMPROVMENTS] Reload complete in " + time + "ms"
                    + " (soft: " + reloaded + " addon(s) restarted in place)");
        } catch (Exception e) {
            sender.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Error: <gray>Reload failed! Check console."));
            ConsoleLogger.error("[ULTIMATEIMPROVMENTS] Reload failed: " + e.getMessage());
            e.printStackTrace();
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>⚠ <gray>Some systems may be in a partial state — a server restart is recommended."));
        }
    }

    /**
     * Core-only reload for {@code /ui reload UI-Core} (or {@code core}): restarts
     * just the core subsystems (listeners/tasks unregister, module shutdown,
     * reloadConfig, startup) WITHOUT disabling the other family plugins —
     * {@code PluginShutdown}/{@code PluginStartup} only touch UI-Core-owned state.
     * Runs under the UI-Core handle on the main thread, same recovery rules.
     */
    public static void runCoreOnlyPhase(Main plugin, CommandSender sender, long startMillis) {
        try {
            ConsoleLogger.info("[Reload] Restarting core subsystems (core-only reload)...");
            new PluginShutdown(plugin).shutdownPlugin();

            ConsoleLogger.info("[Reload] Reloading config...");
            plugin.reloadConfig();

            PluginStartup.clearJarFileCaches();
            new PluginStartup(plugin).startupPlugin();

            long time = System.currentTimeMillis() - startMillis;
            sender.sendMessage(MessageUtil.parse("<dark_green>✔ <green>Success: <gray>UI-Core reloaded (addons untouched)."));
            sender.sendMessage(MessageUtil.parse("<dark_green>✔ <green>Success: <gray>Reload time: <yellow>" + time + "ms"));
            ConsoleLogger.info("[ULTIMATEIMPROVMENTS] Core-only reload complete in " + time + "ms");
        } catch (Exception e) {
            sender.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Error: <gray>Core reload failed! Check console."));
            ConsoleLogger.error("[ULTIMATEIMPROVMENTS] Core-only reload failed: " + e.getMessage());
            e.printStackTrace();
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>⚠ <gray>Core subsystems may be in a partial state — a server restart is recommended."));
        }
    }
}
