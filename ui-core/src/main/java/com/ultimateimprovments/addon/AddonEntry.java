package com.ultimateimprovments.addon;

import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One discovered UI addon with full load statistics.
 * <p>
 * An addon is any plugin whose plugin.yml declares {@code addon-for: UI-Core}
 * (or lists it among {@code addon-for: [...]}). The Core owns the addon
 * lifecycle: addons depend on Core ({@code depend: [UI-Core]}), while Core
 * treats them as optional extensions and only observes them.
 * <p>
 * The entry is intentionally open-ended: there is no fixed set of addons —
 * third-party addons may be dropped into the plugins folder and they show up
 * here automatically. The counters below are fed by the addon itself
 * (via {@code AddonRegistry.reportModules(...)}) or fall back to the plugin's
 * ModuleManager entries.
 */
public final class AddonEntry {

    private final String pluginName;
    private volatile boolean installed;
    private volatile boolean manuallyDisabled;

    /** Load error reported by the addon (a whole-addon or module-level failure). */
    private final List<String> loadErrors = new CopyOnWriteArrayList<>();

    /** Module statistics: loaded / total (a crashed module counts as not loaded). */
    private volatile int modulesLoaded;
    private volatile int modulesTotal;

    private AddonEntry(String pluginName, boolean installed) {
        this.pluginName = pluginName;
        this.installed = installed;
    }

    /** Creates an entry for a currently loaded plugin. */
    public static AddonEntry loaded(Plugin plugin) {
        return new AddonEntry(plugin.getName(), true);
    }

    /** Creates an entry for a plugin.yml marker without a loaded plugin. */
    public static AddonEntry missing(String pluginName) {
        AddonEntry e = new AddonEntry(pluginName, false);
        e.loadErrors.add("plugin is not installed");
        return e;
    }

    // ── Basic state ──

    /** Plugin name from plugin.yml (e.g. "UI-Chat"). */
    public String getPluginName() {
        return pluginName;
    }

    /** Whether the plugin is currently loaded (enabled or not). */
    public boolean isInstalled() {
        return installed;
    }

    /** Marks the entry as loaded/unloaded (used by the registry refresh). */
    void setInstalled(boolean installed) {
        this.installed = installed;
    }

    /** Whether the addon was switched off by an admin via /ui addon disable. */
    public boolean isManuallyDisabled() {
        return manuallyDisabled;
    }

    /** Marks the addon as manually disabled/enabled (used by /ui addon lifecycle). */
    public void setManuallyDisabled(boolean manuallyDisabled) {
        this.manuallyDisabled = manuallyDisabled;
    }

    // ── Load errors ──

    /** Reports a load error (whole addon or a single module). */
    public void reportLoadError(String error) {
        if (error != null && !error.isBlank()) loadErrors.add(error);
    }

    /** Clears all recorded load errors (e.g. after a successful restart). */
    public void clearLoadErrors() {
        loadErrors.clear();
    }

    /** @return an immutable snapshot of the recorded load errors. */
    public List<String> getLoadErrors() {
        return List.copyOf(loadErrors);
    }

    /** @return number of recorded load errors. */
    public int getErrorCount() {
        return loadErrors.size();
    }

    // ── Module statistics ──

    /** Updates the module counters (called on addon enable and by /ui addon status). */
    public void setModuleStats(int loaded, int total) {
        this.modulesLoaded = Math.max(0, loaded);
        this.modulesTotal = Math.max(loaded, total);
    }

    public int getModulesLoaded() {
        return modulesLoaded;
    }

    public int getModulesTotal() {
        return modulesTotal;
    }

    /** Modules that failed to load (total − loaded). */
    public int getModulesFailed() {
        return Math.max(0, modulesTotal - modulesLoaded);
    }

    @Override
    public String toString() {
        return pluginName;
    }
}
