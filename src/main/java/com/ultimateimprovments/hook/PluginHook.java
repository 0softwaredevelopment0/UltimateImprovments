package com.ultimateimprovments.hook;

import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;

/**
 * Utility for safe hooks to softdepend plugins.
 * <p>
 * Lets you check whether a plugin is present via {@code Bukkit.getPluginManager().getPlugin()}
 * BEFORE classes depending on that plugin are loaded.
 * This prevents {@link NoClassDefFoundError} when a softdepend plugin is missing.
 * <p>
 * Example:
 * <pre>{@code
 * if (PluginHook.check("Vault", "Economy")) {
 *     new VaultIntegration(plugin);  // safe — Vault is present
 * }
 * }</pre>
 */
public final class PluginHook {

    private PluginHook() {}

    /**
     * Checks whether the plugin with the given name is loaded.
     * <p>
     * If the plugin is not found — logs a clean message without a stack trace
     * and returns {@code false}. The hook should be skipped.
     *
     * @param pluginName  the plugin name (from plugin.yml, e.g. "Vault")
     * @param featureName the feature/hook name for the console message
     * @return {@code true} if the plugin is loaded, {@code false} otherwise
     */
    public static boolean check(String pluginName, String featureName) {
        if (Bukkit.getPluginManager().getPlugin(pluginName) == null) {
            ConsoleLogger.info("[Hook:" + featureName + "] " + pluginName
                    + " not found — " + featureName + " hook disabled.");
            return false;
        }
        return true;
    }

    /**
     * Checks whether the plugin is loaded and returns {@code false} if not.
     * Simplified version without a custom feature name — uses the plugin name.
     */
    public static boolean check(String pluginName) {
        return check(pluginName, pluginName);
    }
}
