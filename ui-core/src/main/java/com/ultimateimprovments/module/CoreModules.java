package com.ultimateimprovments.module;

import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.database.DatabaseInit;
import com.ultimateimprovments.database.PlayerSettingsDB;
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
                PlayerSettingsDB.init();
                ConsoleLogger.info("[SQLITE] Database initialized.");
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                try { DatabaseManager.close(); }
                catch (Exception e) { ConsoleLogger.warn("[DB] Close: " + e.getMessage()); }
            }
        });

        // NOTE: named "CoreInfra" (not "Core") to avoid colliding with the
        // real "Core" module registered by UI-Other's SimpleModules.registerCoreModules(),
        // which owns TaskManager/CommandRegistrar/general listeners. ModuleManager
        // silently skips duplicate module names, so a name collision here previously
        // caused that real Core module's init logic to never run.
        mm.register(new PluginModule("CoreInfra", "infrastructure/core", true) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                ConsoleLogger.info("[Core] Infrastructure initialized.");
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {}
        });
    }
}