package com.ultimateimprovments.module;

import com.ultimateimprovments.util.ConsoleLogger;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Base abstraction of a plugin module.
 * <p>
 * Each module is independent: if {@link #onInit} throws an exception,
 * the module is considered disabled, but the other modules keep working.
 * <p>
 * {@code essential = true} — the module is critical for the plugin,
 * {@code essential = false} — the module can be disabled without losing core functionality.
 */
public abstract class PluginModule {

    private final String name;
    private final String modulePath;
    private boolean enabled = false;
    private final boolean essential;
    private String disableReason = null;

    public PluginModule(String name, String modulePath, boolean essential) {
        this.name = name;
        this.modulePath = modulePath;
        this.essential = essential;
    }

    /** Backward-compatible constructor without path */
    public PluginModule(String name, boolean essential) {
        this(name, name.toLowerCase().replace(" ", "_"), essential);
    }

    // =========================
    // GETTERS
    // =========================

    public String getName() { return name; }
    public String getModulePath() { return modulePath; }
    public boolean isEnabled() { return enabled; }
    public boolean isEssential() { return essential; }
    public String getDisableReason() { return disableReason; }

    // =========================
    // LIFECYCLE
    // =========================

    /**
     * Initializes the module. On error the module is disabled,
     * but the exception is NOT rethrown — the plugin keeps working.
     *
     * @return true if the module was initialized successfully
     */
    public boolean initialize(JavaPlugin plugin) {
        if (enabled) return true;
        try {
            onInit(plugin);
            enabled = true;
            disableReason = null;
            return true;
        } catch (Throwable t) {
            enabled = false;
            String msg = t.getMessage() != null ? t.getMessage() : "";
            disableReason = msg.isEmpty() ? t.getClass().getSimpleName() : msg;

            // Detect a Java version mismatch error (Paper cannot convert the class)
            if (msg.contains("major version") || msg.contains("Unsupported class file")) {
                ConsoleLogger.error("[Module:" + name + "] \u2717 Java version mismatch!");
                ConsoleLogger.error("[Module:" + name + "]   Update your Java Runtime to fix this issue.");
            } else {
                ConsoleLogger.error("[Module:" + name + "] \u2717 FAILED: " + disableReason);
            }
            return false;
        }
    }

    /**
     * Disables the module. Disable errors are logged, not rethrown.
     */
    public boolean disable(JavaPlugin plugin) {
        if (!enabled) return true;
        try {
            onDisable(plugin);
        } catch (Throwable t) {
            ConsoleLogger.warn("[Module:" + name + "] Shutdown error: " + t.getMessage());
            enabled = false;
            return false;
        }
        enabled = false;
        return true;
    }

    /**
     * Reloads the module configuration (if supported).
     */
    public void reloadConfig(JavaPlugin plugin) {
        if (!enabled) return;
        try {
            onReloadConfig(plugin);
        } catch (Throwable t) {
            ConsoleLogger.warn("[Module:" + name + "] ReloadConfig error: " + t.getMessage());
        }
    }

    // =========================
    // ABSTRACT / OVERRIDE POINTS
    // =========================

    /** Run the module initialization. */
    protected abstract void onInit(JavaPlugin plugin) throws Exception;

    /** Run the module shutdown. */
    protected abstract void onDisable(JavaPlugin plugin);

    /** Reload the config (no-op by default). */
    protected void onReloadConfig(JavaPlugin plugin) {}
}
