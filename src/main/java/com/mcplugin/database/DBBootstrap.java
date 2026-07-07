package com.mcplugin.database;

import com.mcplugin.core.Main;
import com.mcplugin.util.ConsoleLogger;

public class DBBootstrap {

    // =========================
    // INIT SQLITE
    // =========================
    public static void init(Main plugin) {

        try {

            // =========================
            // CONNECT
            // =========================
            DatabaseManager.connect();

            // =========================
            // CREATE TABLES
            // =========================
            DatabaseInit.init();

            ConsoleLogger.info(
                    "[DB] SQLite initialized successfully."
            );

        } catch (Exception e) {

            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "[DB] SQLite initialization failed", e);
        }
    }

    // =========================
    // SHUTDOWN
    // =========================
    public static void shutdown(Main plugin) {

        try {

            DatabaseManager.close();

            ConsoleLogger.info(
                    "[DB] SQLite connection closed."
            );

        } catch (Exception e) {

            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "[DB] SQLite shutdown failed", e);
        }
    }
}