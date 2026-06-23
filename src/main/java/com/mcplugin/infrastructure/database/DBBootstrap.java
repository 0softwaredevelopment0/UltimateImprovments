package com.mcplugin.infrastructure.database;

import com.mcplugin.infrastructure.core.Main;

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

            plugin.getLogger().info(
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

            plugin.getLogger().info(
                    "[DB] SQLite connection closed."
            );

        } catch (Exception e) {

            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "[DB] SQLite shutdown failed", e);
        }
    }
}