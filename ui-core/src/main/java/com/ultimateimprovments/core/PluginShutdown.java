package com.ultimateimprovments.core;

import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.module.ModuleManager;
import com.ultimateimprovments.util.ConsoleLogger;

/**
 * PluginShutdown — core-only shutdown.
 * Feature cleanup is handled by UI-Other.
 */
public class PluginShutdown {

    private final Main plugin;

    public PluginShutdown(Main plugin) {
        this.plugin = plugin;
    }

    public void shutdownPlugin() {
        ConsoleLogger.info("[Shutdown] UI-Core shutting down...");

        // Shutdown all core modules (reverse order)
        ModuleManager mm = ModuleManager.getInstance();
        if (mm != null) {
            mm.shutdownAll();
        }

        // Close database
        try { DatabaseManager.close(); }
        catch (Exception e) { ConsoleLogger.warn("[DB] Close: " + e.getMessage()); }

        PluginStartup.resetStartupFlag();
        ConsoleLogger.success("[PLUGIN] UI-Core disabled.");
    }
}
