package com.ultimateimprovments.addon;

import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AddonRegistry — the Core-side registry of UltimateImprovments addons.
 * <p>
 * Core is the main (mandatory) part; everything else is an addon.
 * An addon is recognized by the marker in its plugin.yml:
 * <pre>
 * addon-for: UI-Core
 * # or a list: addon-for: [UI-Core, Other-Host]
 * </pre>
 * The marker makes the contract explicit — it does not rely on plugin names,
 * so third-party plugins can declare themselves UI addons too (they still need
 * {@code depend: [UI-Core]} to actually load after Core).
 * <p>
 * There is NO fixed set of addons: any number can be dropped into the plugins
 * folder. {@code /ui addon} reports how many loaded and how many failed —
 * never "X of 11".
 */
public final class AddonRegistry {

    /** plugin.yml key that marks a plugin as a UI addon. */
    public static final String MARKER_KEY = "addon-for";

    /** Value the marker must reference to be treated as a UI addon. */
    public static final String HOST_NAME = "UI-Core";

    private static final List<AddonEntry> addons = new ArrayList<>();
    private static boolean discovered = false;

    private AddonRegistry() {}

    /**
     * Scans loaded plugins for the {@code addon-for: UI-Core} marker and
     * records every addon found. Safe to call again — the second call is a
     * refresh: previously missing entries that are now loaded are upgraded,
     * and entries for unloaded plugins are dropped.
     */
    public static synchronized void discover() {
        List<AddonEntry> refreshed = new ArrayList<>();

        // Keep explicit registrations made before Core's discovery ran.
        refreshed.addAll(addons.stream().filter(AddonEntry::isInstalled).toList());

        for (Plugin plugin : org.bukkit.Bukkit.getPluginManager().getPlugins()) {
            if (isUiAddon(getPluginJarFile(plugin))
                    && refreshed.stream().noneMatch(e -> e.getPluginName().equals(plugin.getName()))) {
                refreshed.add(AddonEntry.loaded(plugin));
            }
        }

        addons.clear();
        addons.addAll(refreshed);
        discovered = true;

        ConsoleLogger.info("[Addons] Registered " + addons.size() + " addon(s).");
    }

    /**
     * Whether the given plugin declares itself a UI addon via plugin.yml.
     * Accepts a single string or a list of host names.
     * <p>
     * Preferred source: the raw plugin.yml read directly from the plugin's JAR
     * file by path — arbitrary keys are not exposed by {@code getDescription()},
     * and opening the plugin's own classloader resource can throw a fatal
     * {@code ZipError} when the JAR has been replaced on disk (e.g. by
     * {@code /ui updatejar} or {@code /ui swapjar}) while the old cached jar
     * handle was still open. Path-based reads are always safe; the classloader
     * is only a fallback for exotic deployments (e.g. classes directory).
     */
    public static boolean isUiAddon(Plugin plugin) {
        if (plugin == null) return false;

        java.io.File jarPath = getPluginJarFile(plugin);
        if (jarPath != null && isUiAddon(jarPath)) return true;

        // Fallback: classloader resource (dev runs, shaded deployments)
        java.io.InputStream raw = plugin.getClass().getClassLoader()
                .getResourceAsStream("plugin.yml");
        if (raw == null) return false;
        try (var reader = new java.io.InputStreamReader(raw, java.nio.charset.StandardCharsets.UTF_8)) {
            var yaml = new org.bukkit.configuration.file.YamlConfiguration();
            yaml.load(reader);
            return markerMatches(yaml);
        } catch (Throwable t) {
            // Unreadable JAR / ZipError / broken YAML — not an addon, never propagate
            return false;
        }
    }

    /**
     * Whether the plugin.yml inside the given JAR declares the
     * {@code addon-for: UI-Core} marker. Never throws: a missing, unreadable
     * or replaced (locked) JAR is simply reported as "not an addon".
     */
    public static boolean isUiAddon(java.io.File jarPath) {
        if (jarPath == null || !jarPath.isFile()) return false;
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jarPath)) {
            java.util.zip.ZipEntry entry = zip.getEntry("plugin.yml");
            if (entry == null) return false;
            var yaml = new org.bukkit.configuration.file.YamlConfiguration();
            try (var reader = new java.io.InputStreamReader(zip.getInputStream(entry),
                    java.nio.charset.StandardCharsets.UTF_8)) {
                yaml.load(reader);
            }
            return markerMatches(yaml);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean markerMatches(org.bukkit.configuration.file.YamlConfiguration yaml) {
        if (yaml.isList(MARKER_KEY)) {
            return yaml.getStringList(MARKER_KEY).contains(HOST_NAME);
        }
        return HOST_NAME.equals(yaml.getString(MARKER_KEY));
    }

    /** Best-effort location of the JAR the plugin was loaded from ({@code null} if unknown). */
    private static java.io.File getPluginJarFile(Plugin plugin) {
        try {
            return new java.io.File(plugin.getClass().getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Registers an external addon by name. For third-party addons that want to
     * be listed even if they enable before Core discovery runs.
     */
    public static synchronized void registerExternal(String pluginName) {
        if (addons.stream().anyMatch(e -> e.getPluginName().equals(pluginName))) return;
        Plugin p = org.bukkit.Bukkit.getPluginManager().getPlugin(pluginName);
        addons.add(p != null ? AddonEntry.loaded(p) : AddonEntry.missing(pluginName));
    }

    /**
     * Called by {@link AddonListener} when a plugin with the marker enables.
     * Adds (or upgrades) the entry.
     */
    public static synchronized void onAddonEnabled(Plugin plugin) {
        for (int i = 0; i < addons.size(); i++) {
            if (addons.get(i).getPluginName().equals(plugin.getName())) {
                AddonEntry entry = addons.get(i);
                entry.setInstalled(true);
                entry.setManuallyDisabled(false);
                addons.set(i, entry);
                return;
            }
        }
        addons.add(AddonEntry.loaded(plugin));
        ConsoleLogger.info("[Addons] Discovered addon: " + plugin.getName());
    }

    /**
     * Called by {@link AddonListener} when a marked addon disables.
     * The entry stays listed but is reported as not running.
     */
    public static synchronized void onAddonDisabled(String pluginName) {
        for (int i = 0; i < addons.size(); i++) {
            if (addons.get(i).getPluginName().equals(pluginName)) {
                addons.get(i).setInstalled(false);
                return;
            }
        }
    }

    /** @return the entry for the given addon, or {@code null}. */
    public static synchronized AddonEntry getEntry(String pluginName) {
        for (AddonEntry e : addons) {
            if (e.getPluginName().equalsIgnoreCase(pluginName)) return e;
        }
        return null;
    }

    /**
     * Reporting API for addons: an addon (or the core on its behalf) reports how
     * many of its internal modules loaded. A module that threw during init counts
     * as not loaded; each failure should also be reported via
     * {@link AddonEntry#reportLoadError(String)}.
     * <p>
     * Example inside an addon's onEnable:
     * <pre>
     * int ok = 0; int total = myModules.size();
     * for (Runnable init : myModules) {
     *     try { init.run(); ok++; }
     *     catch (Throwable t) { AddonRegistry.getEntry(getName()).reportLoadError(t.toString()); }
     * }
     * AddonRegistry.reportModules(getName(), ok, total);
     * </pre>
     *
     * @return true if the stats were recorded (addon is known to the registry)
     */
    public static synchronized boolean reportModules(String pluginName, int loaded, int total) {
        AddonEntry e = getEntry(pluginName);
        if (e == null) {
            Plugin p = org.bukkit.Bukkit.getPluginManager().getPlugin(pluginName);
            if (p == null) return false;
            e = AddonEntry.loaded(p);
            addons.add(e);
        }
        e.setModuleStats(loaded, total);
        return true;
    }

    /** Convenience: report a load error for an addon by name. */
    public static synchronized void reportError(String pluginName, String error) {
        AddonEntry e = getEntry(pluginName);
        if (e != null) e.reportLoadError(error);
    }

    /** @return an unmodifiable snapshot of all known addon entries. */
    public static synchronized List<AddonEntry> getAddons() {
        return Collections.unmodifiableList(new ArrayList<>(addons));
    }

    /** @return the count of addons whose plugin is currently loaded and enabled. */
    public static synchronized long getLoadedCount() {
        return addons.stream().filter(AddonEntry::isInstalled).count();
    }

    /** @return total recorded load errors across all addons. */
    public static synchronized int getTotalErrorCount() {
        return addons.stream().mapToInt(AddonEntry::getErrorCount).sum();
    }

    /** Whether discovery already ran at least once (Core startup). */
    public static boolean isDiscovered() {
        return discovered;
    }

    /**
     * Clears the registry. Called by {@code PluginShutdown} so a
     * {@code /ui reload} re-discovers addons from a clean state.
     */
    public static synchronized void clear() {
        addons.clear();
        discovered = false;
    }
}
