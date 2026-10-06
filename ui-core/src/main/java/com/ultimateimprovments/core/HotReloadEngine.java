package com.ultimateimprovments.core;

import com.ultimateimprovments.util.ConsoleLogger;

import org.bukkit.Bukkit;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Soft hot-reload engine: a plugin goes through a REAL lifecycle cycle —
 * {@code onDisable} → {@code reloadConfig} → {@code onEnable} — on the SAME
 * instance, with its classloader kept open.
 * <p>
 * Why not a fresh load from the JAR (the old approach): on Paper/Purpur 26.3
 * runtime plugin registration is hard-blocked for paper-plugins —
 * {@code SingularRuntimePluginProviderStorage.register} throws
 * {@code IllegalStateException: Cannot register paper plugins during runtime!}
 * for every plugin packaged with a {@code paper-plugin.yml} (the whole UI
 * family), so {@code PluginManager.loadPlugin()} can never work again.
 * Plain {@code disablePlugin → enablePlugin} is equally dead: this server
 * version CLOSES the plugin classloader inside {@code disablePlugin}
 * ({@code ConfiguredPluginClassLoader.close()}), and re-enabling the same
 * instance then hits a {@code zip file closed} zombie on any not-yet-loaded
 * class.
 * <p>
 * The engine therefore replicates the disable sequence manually —
 * {@link PluginDisableEvent} + {@code setEnabled(false)} (a real
 * {@code onDisable}) — and STOPS there, skipping the classloader close. The
 * plugin never leaves the PluginManager registries, its classloader stays
 * open, and {@code enablePlugin} runs a real {@code onEnable}. Configs are
 * re-read via {@link JavaPlugin#reloadConfig()} between the two phases.
 * Note that code updates still require a server restart: classes already
 * loaded stay cached (and a closed classloader cannot be reopened).
 * <p>
 * UI-Core itself is NEVER hot-reloaded: this engine lives in its classloader
 * and every addon joins it (join-classpath). The core restarts its subsystems
 * in place via {@link PluginShutdown}/{@link PluginStartup} instead.
 */
public final class HotReloadEngine {

    private HotReloadEngine() {}

    /**
     * Soft-reloads one plugin: real {@code onDisable} (classloader kept
     * open), config re-read, real {@code onEnable}. Returns the same
     * instance.
     *
     * @param plugin    the currently loaded plugin instance
     * @param logPrefix short label for the log lines (e.g. the command name)
     */
    public static Plugin hotReload(Plugin plugin, String logPrefix) throws Exception {
        if (!(plugin instanceof JavaPlugin jp)) {
            throw new IllegalArgumentException("Not a JavaPlugin: " + plugin.getName());
        }

        softDisable(jp, logPrefix);

        ConsoleLogger.info("[HotReload] [" + logPrefix + "] Reloading config of " + plugin.getName() + "...");
        jp.reloadConfig();

        ConsoleLogger.info("[HotReload] [" + logPrefix + "] onEnable " + plugin.getName()
                + " v" + plugin.getPluginMeta().getVersion() + "...");
        Bukkit.getPluginManager().enablePlugin(jp);
        return jp;
    }

    /**
     * Batch phase 1: real {@code onDisable} for every plugin in the list
     * (reverse load order), WITHOUT the classloader close that
     * {@link PluginManager#disablePlugin(Plugin)} performs on this server
     * version.
     */
    public static void unloadAll(List<Plugin> plugins) {
        for (int i = plugins.size() - 1; i >= 0; i--) {
            Plugin p = plugins.get(i);
            try {
                if (p instanceof JavaPlugin jp) {
                    softDisable(jp, "reload all");
                }
            } catch (Throwable t) {
                ConsoleLogger.error("[HotReload] Disable failed for " + p.getName() + ": " + t.getMessage());
                t.printStackTrace();
            }
        }
    }

    /**
     * Batch phase 2: re-reads configs and enables (real {@code onEnable})
     * every plugin in the given (dependency/load) order. A plugin that fails
     * is logged and skipped — its dependents may then fail too and are
     * reported the same way.
     *
     * @return the enabled instances (same objects, same order), failures excluded
     */
    public static List<Plugin> loadAllInOrder(List<Plugin> previousOrder) {
        List<Plugin> fresh = new ArrayList<>();
        for (Plugin p : previousOrder) {
            try {
                if (!(p instanceof JavaPlugin jp)) {
                    throw new IllegalArgumentException("Not a JavaPlugin: " + p.getName());
                }
                jp.reloadConfig();
                Bukkit.getPluginManager().enablePlugin(jp);
                fresh.add(jp);
                ConsoleLogger.info("[HotReload] Enabled " + p.getName()
                        + " v" + p.getPluginMeta().getVersion());
            } catch (Throwable t) {
                ConsoleLogger.error("[HotReload] " + p.getName() + " failed to reload: " + t.getMessage());
                t.printStackTrace();
            }
        }
        return fresh;
    }

    /**
     * Real {@code onDisable} without the Paper classloader close: fires
     * {@link PluginDisableEvent} and flips the instance to disabled (which
     * runs {@code onDisable()}) — exactly what
     * {@code PluginManager.disablePlugin} does, minus
     * {@code ConfiguredPluginClassLoader.close()} and the classloader-storage
     * unregister. Public so family commands can disable an addon while
     * keeping it enable-able (a vanilla disable would close its JAR).
     */
    public static void softDisable(JavaPlugin plugin, String logPrefix) {
        if (!plugin.isEnabled()) {
            return;
        }
        ConsoleLogger.info("[HotReload] [" + logPrefix + "] onDisable " + plugin.getName()
                + " (soft — classloader kept open)...");
        Bukkit.getPluginManager().callEvent(new PluginDisableEvent(plugin));
        plugin.setEnabled(false);
    }
}
