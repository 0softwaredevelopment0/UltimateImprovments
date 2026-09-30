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
 * <b>Full hot-reload with real lifecycle events:</b> every addon goes through a
 * real {@code onDisable}, is unloaded from the PluginManager registries, is
 * loaded FRESH from its JAR (brand-new classloader — mandatory, because on
 * Paper {@code disablePlugin} closes the JAR and {@code enablePlugin} does not
 * reopen it, so re-enabling the same instance leaves a
 * {@code zip file closed} zombie) and gets a real {@code onEnable}. See
 * {@link HotReloadEngine}.
 * <p>
 * UI-Core itself is never unloaded (the engine lives in its classloader and
 * every addon joins it) — its subsystems restart in place via
 * {@link PluginShutdown}/{@link PluginStartup}.
 * <p>
 * Sequence: core shutdown → config reload → core startup → addons
 * unload-all (reverse order) → addons load-all (load order).
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
            // 1) Core subsystems restart in place (never unloaded).
            ConsoleLogger.info("[Reload] Restarting core subsystems (UI-Core stays loaded)...");
            new PluginShutdown(plugin).shutdownPlugin();

            ConsoleLogger.info("[Reload] Reloading config...");
            // Composite per-addon backend: AddonConfigManager.init() salvages broken
            // TOML lines, repairs missing keys from the bundled fragments and rebuilds
            // the CompositeConfig routing (configs/UI-<Addon>.toml files).
            plugin.reloadConfig();

            PluginStartup.clearJarFileCaches();
            new PluginStartup(plugin).startupPlugin();

            // 2) Addons: REAL hot-reload (onDisable → unload → fresh load → onEnable).
            ConsoleLogger.info("[Reload] Hot-reloading " + family.size()
                    + " addon(s) with real onDisable/onEnable...");
            HotReloadEngine.unloadAll(family);
            List<Plugin> fresh = HotReloadEngine.loadAllInOrder(family);

            long time = System.currentTimeMillis() - startMillis;
            int failed = family.size() - fresh.size();
            sender.sendMessage(MessageUtil.parse("<dark_green>✔ <green>Success: <gray>Reload complete."));
            sender.sendMessage(MessageUtil.parse(
                    "<dark_green>✔ <green>Success: <gray>Addons hot-reloaded: <yellow>" + fresh.size()
                            + "/" + family.size()
                            + (failed > 0 ? "</yellow><gray>, failed: </gray><red>" + failed
                                    + " (see console)</red>" : "")
                            + ", <yellow>" + time + "ms"));
            ConsoleLogger.info("[ULTIMATEIMPROVMENTS] Reload complete in " + time + "ms ("
                    + fresh.size() + "/" + family.size() + " addons hot-reloaded)");
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
