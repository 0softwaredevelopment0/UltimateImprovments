package com.ultimateimprovments.core;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Filter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Main — entry point of UltimateImprovments.
 * <p>
 * Initialization is split into independent modules ({@link PluginModule}).
 * Each module is handled in a try-catch: if one module fails,
 * the others keep working.
 * <p>
 * On startup the integrity of config.yml is checked — if keys are missing,
 * the config is renamed to compromised-config.yml and a fresh one is created.
 */
public class Main extends JavaPlugin {

    private static Main instance;
    private Filter originalLogFilter;

    public static Main getInstance() {
        return instance;
    }

    public java.io.File getPluginFile() {
        return getFile();
    }

    @Override
    public void onEnable() {
        instance = this;

        // Suppress Paper's 'Fatal error trying to convert...' — this message
        // is printed by Paper for EVERY class with an unsupported Java version
        // and carries no useful information (cannot be fixed, only by updating Java).
        suppressPaperConversionErrors();

        new PluginStartup(this).startupPlugin();
    }

    @Override
    public void onDisable() {
        // Restore the original filter on shutdown
        if (originalLogFilter != null) {
            getLogger().setFilter(originalLogFilter);
        }
        new PluginShutdown(this).shutdownPlugin();
    }

    /**
     * Sets a filter on the plugin logger, suppressing
     * Paper messages about incompatible class file versions.
     * <p>
     * Paper PluginClassLoader.findClass() logs 'Fatal error trying to convert'
     * for every class with an unsupported Java version. This is meaningless spam —
     * the player cannot fix it except by updating Java.
     * We write a clean warning ourselves in checkJavaVersion().
     */
    private void suppressPaperConversionErrors() {
        Logger logger = getLogger();
        originalLogFilter = logger.getFilter();
        logger.setFilter(new Filter() {
            @Override
            public boolean isLoggable(LogRecord record) {
                if (record == null || record.getMessage() == null) return true;
                String msg = record.getMessage();
                // Suppress 'Fatal error trying to convert' from Paper PluginClassLoader
                if (msg.contains("Fatal error trying to convert")) return false;
                // Suppress the technical ASM message about an unsupported version
                if (msg.contains("Unsupported class file major version")) return false;
                return true;
            }
        });
    }
}