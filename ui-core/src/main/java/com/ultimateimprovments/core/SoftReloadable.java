package com.ultimateimprovments.core;

/**
 * Implemented by every UI-* addon main class that supports an in-place
 * (soft) reload: {@code /ui reload} re-runs the addon's startup logic
 * WITHOUT {@code PluginManager.disablePlugin}/{@code enablePlugin}.
 * <p>
 * Why not disable/enable: on Paper, disabling a plugin closes its JAR
 * (plugin classloader) and re-enabling the same instance does NOT reopen
 * it — any class not yet loaded then throws
 * {@code IllegalStateException: zip file closed}, leaving the addon a zombie
 * (broken commands, LuckPerms tab-complete errors, etc.).
 * <p>
 * Implementations must be re-runnable: {@link #softReload()} runs the
 * addon's private shutdown cleanup (listeners, tasks, static guards — but
 * NOT the shared {@code ModuleManager.shutdownAll()}, which is owned by the
 * reload coordinator / real disable) followed by the same startup logic as
 * {@code onEnable}.
 */
public interface SoftReloadable {

    /** Restarts this addon's systems in place (no plugin disable/enable). */
    void softReload();
}
