package com.ultimateimprovments.module;

import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.database.DatabaseInit;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.plugin.java.JavaPlugin;

public final class CoreModules {

    private CoreModules() {}

    public static void registerAll(ModuleManager mm) {
        mm.register(new PluginModule("Database", "infrastructure/database", true) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                DatabaseManager.connect();
                DatabaseInit.init();
                ConsoleLogger.info("[SQLITE] Database initialized.");
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                try { DatabaseManager.close(); }
                catch (Exception e) { ConsoleLogger.warn("[DB] Close: " + e.getMessage()); }
            }
        });

        mm.register(new PluginModule("Core", "infrastructure/core", true) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                ConsoleLogger.info("[Core] Infrastructure initialized.");
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {}
        });
    }
}
