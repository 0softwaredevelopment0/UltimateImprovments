package com.ultimateimprovments.core;

import com.google.common.graph.MutableGraph;
import com.ultimateimprovments.util.ConsoleLogger;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * True hot-reload engine: a plugin goes through a REAL lifecycle cycle —
 * {@code onDisable} → unload → fresh load from its JAR → {@code onEnable} —
 * with a brand-new classloader every time.
 * <p>
 * Why a fresh instance is mandatory: on Paper, {@code disablePlugin} closes the
 * plugin's JAR (classloader) and {@code enablePlugin} does NOT reopen it —
 * re-enabling the same instance leaves a zombie that throws
 * {@code IllegalStateException: zip file closed} on any not-yet-loaded class.
 * The old instance is therefore removed from the PluginManager registries via
 * reflection (the same technique as {@code /ui swapjar}) before the JAR is
 * loaded again from disk.
 * <p>
 * UI-Core itself is NEVER hot-reloaded: this engine lives in its classloader
 * and every addon joins it (join-classpath). The core restarts its subsystems
 * in place via {@link PluginShutdown}/{@link PluginStartup} instead.
 */
public final class HotReloadEngine {

    private HotReloadEngine() {}

    /**
     * Hot-reloads one plugin: real {@code onDisable}, unload, fresh load from
     * the same JAR file, real {@code onEnable}. Returns the fresh instance.
     *
     * @param plugin    the currently loaded plugin instance
     * @param logPrefix short label for the log lines (e.g. the command name)
     */
    public static Plugin hotReload(Plugin plugin, String logPrefix) throws Exception {
        PluginManager pm = Bukkit.getPluginManager();
        if (!(plugin instanceof JavaPlugin jp)) {
            throw new IllegalArgumentException("Not a JavaPlugin: " + plugin.getName());
        }
        File jar = pluginFile(jp);
        if (jar == null || !jar.isFile()) {
            throw new IllegalStateException("Cannot locate the JAR file of " + plugin.getName());
        }

        ConsoleLogger.info("[HotReload] [" + logPrefix + "] onDisable " + plugin.getName() + "...");
        if (plugin.isEnabled()) pm.disablePlugin(plugin);

        ConsoleLogger.info("[HotReload] [" + logPrefix + "] Unloading old instance...");
        removeFromPluginManager(pm, plugin);

        ConsoleLogger.info("[HotReload] [" + logPrefix + "] Loading fresh instance from " + jar.getName() + "...");
        Plugin fresh = pm.loadPlugin(jar);
        if (fresh == null) {
            throw new InvalidPluginException("loadPlugin() returned null");
        }

        ConsoleLogger.info("[HotReload] [" + logPrefix + "] onEnable " + fresh.getName()
                + " v" + fresh.getPluginMeta().getVersion() + "...");
        pm.enablePlugin(fresh);
        return fresh;
    }

    /**
     * Batch phase 1: disables (real {@code onDisable}, reverse load order) and
     * unloads every plugin in the list. Used by {@code /ui reload all} so that
     * cross-addon bridges are released before any addon is loaded back.
     */
    public static void unloadAll(List<Plugin> plugins) {
        PluginManager pm = Bukkit.getPluginManager();
        for (int i = plugins.size() - 1; i >= 0; i--) {
            Plugin p = plugins.get(i);
            try {
                if (p.isEnabled()) pm.disablePlugin(p);
                removeFromPluginManager(pm, p);
            } catch (Throwable t) {
                ConsoleLogger.error("[HotReload] Unload failed for " + p.getName() + ": " + t.getMessage());
                t.printStackTrace();
            }
        }
    }

    /**
     * Batch phase 2: loads and enables plugins from their JARs in the given
     * (dependency/load) order. A plugin that fails to load is logged and
     * skipped — its dependents may then fail too and are reported the same way.
     *
     * @return the fresh enabled instances (same order), failures excluded
     */
    public static List<Plugin> loadAllInOrder(List<Plugin> previousOrder) {
        PluginManager pm = Bukkit.getPluginManager();
        List<Plugin> fresh = new ArrayList<>();
        for (Plugin old : previousOrder) {
            try {
                File jar = pluginFile((JavaPlugin) old);
                Plugin loaded = pm.loadPlugin(jar);
                if (loaded == null) {
                    throw new InvalidPluginException("loadPlugin() returned null");
                }
                pm.enablePlugin(loaded);
                fresh.add(loaded);
                ConsoleLogger.info("[HotReload] Enabled " + loaded.getName()
                        + " v" + loaded.getPluginMeta().getVersion());
            } catch (Throwable t) {
                ConsoleLogger.error("[HotReload] " + old.getName() + " failed to reload: " + t.getMessage());
                t.printStackTrace();
            }
        }
        return fresh;
    }

    // ==========================================================================
    // Reflection: remove the plugin from PluginManager's internal registries
    // (ported from SwapJarSubcommand — proven on Paper 1.21+/26.x)
    // ==========================================================================

    /**
     * Resolves the JAR file a plugin was loaded from.
     * {@code JavaPlugin.getFile()} has protected access in the Paper API, so it
     * is reached reflectively (this is what {@code Main#getPluginFile()} does
     * for UI-Core itself).
     */
    private static File pluginFile(JavaPlugin plugin) throws Exception {
        Method getFile = JavaPlugin.class.getDeclaredMethod("getFile");
        getFile.setAccessible(true);
        return (File) getFile.invoke(plugin);
    }

    /**
     * Removes the plugin from all of PluginManager's internal registries.
     * <p>
     * On modern Paper the PluginManager is {@code PaperPluginManagerImpl}, which
     * delegates plugin storage to {@code PaperPluginInstanceManager} (the
     * {@code instanceManager} field). The old SimplePluginManager
     * {@code plugins}/{@code lookupNames} fields do not exist there.
     */
    private static void removeFromPluginManager(PluginManager pm, Plugin plugin) {
        try {
            String pmClassName = pm.getClass().getName();

            if (pmClassName.equals("io.papermc.paper.plugin.manager.PaperPluginManagerImpl")) {
                removeFromPaperPluginManager(pm, plugin);
            } else {
                removeFromSimplePluginManager(pm, plugin);
            }

            // Safety net: drop any listener registrations that disablePlugin missed.
            HandlerList.unregisterAll(plugin);

            ConsoleLogger.info("[HotReload] " + plugin.getName() + " removed from the PluginManager registries.");
        } catch (Exception e) {
            ConsoleLogger.error("[HotReload] Failed to remove " + plugin.getName()
                    + " from the PluginManager: " + e.getMessage());
        }
    }

    /** Paper 1.21+: PaperPluginManagerImpl → PaperPluginInstanceManager. */
    @SuppressWarnings("unchecked")
    private static void removeFromPaperPluginManager(PluginManager pm, Plugin plugin) throws Exception {
        Field instanceManagerField = pm.getClass().getDeclaredField("instanceManager");
        instanceManagerField.setAccessible(true);
        Object instanceManager = instanceManagerField.get(pm);

        Class<?> imClass = instanceManager.getClass();

        // 1. plugins (List<Plugin>)
        Field pluginsField = imClass.getDeclaredField("plugins");
        pluginsField.setAccessible(true);
        List<Plugin> plugins = (List<Plugin>) pluginsField.get(instanceManager);
        plugins.remove(plugin);

        // 2. lookupNames (Map<String, Plugin>) — drop both exact and lowercase keys
        Field lookupNamesField = imClass.getDeclaredField("lookupNames");
        lookupNamesField.setAccessible(true);
        Map<String, Plugin> lookupNames = (Map<String, Plugin>) lookupNamesField.get(instanceManager);
        lookupNames.remove(plugin.getName());
        lookupNames.remove(plugin.getName().toLowerCase(java.util.Locale.ROOT));

        // 3. dependencyTree: SimpleMetaDependencyTree stores a MutableGraph<String>;
        //    remove() needs a PluginProvider we don't have — go into the graph directly.
        try {
            Field depTreeField = imClass.getDeclaredField("dependencyTree");
            depTreeField.setAccessible(true);
            Object depTree = depTreeField.get(instanceManager);

            Field graphField = null;
            for (Class<?> c = depTree.getClass(); c != null && graphField == null; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (MutableGraph.class.isAssignableFrom(f.getType())) {
                        graphField = f;
                        break;
                    }
                }
            }
            if (graphField != null) {
                graphField.setAccessible(true);
                Object graph = graphField.get(depTree);
                Method removeNode = graph.getClass().getMethod("removeNode", Object.class);
                removeNode.invoke(graph, plugin.getName());
                ConsoleLogger.info("[HotReload] " + plugin.getName() + " removed from the dependency graph.");
            }
        } catch (Exception ignored) {
            // dependencyTree cleanup — not critical if it fails
        }
    }

    /** Legacy Bukkit/Spigot: fields directly in SimplePluginManager. */
    @SuppressWarnings("unchecked")
    private static void removeFromSimplePluginManager(PluginManager pm, Plugin plugin) throws Exception {
        Field pluginsField = pm.getClass().getDeclaredField("plugins");
        pluginsField.setAccessible(true);
        List<Plugin> plugins = (List<Plugin>) pluginsField.get(pm);
        plugins.remove(plugin);

        try {
            Field lookupNamesField = pm.getClass().getDeclaredField("lookupNames");
            lookupNamesField.setAccessible(true);
            Map<String, Plugin> lookupNames = (Map<String, Plugin>) lookupNamesField.get(pm);
            lookupNames.remove(plugin.getName());
            lookupNames.remove(plugin.getName().toLowerCase(java.util.Locale.ROOT));
        } catch (Exception ignored) {}
    }
}
