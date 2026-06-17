package com.mcplugin.module;

import com.mcplugin.Main;
import com.mcplugin.database.DatabaseInit;
import com.mcplugin.database.DatabaseManager;
import com.mcplugin.main.DatapackInstaller;
import com.mcplugin.server.Log4jInstaller;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль базы данных + установка датапака + log4j.
 * Essential — без БД плагин не работает.
 */
public class DatabaseModule extends PluginModule {

    public DatabaseModule() {
        super("Database", true); // essential
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        // =========================
        // SQLITE INIT
        // =========================
        DatabaseManager.connect();
        DatabaseInit.init();
        plugin.getLogger().info("[SQLITE] Database initialized successfully.");

        // =========================
        // DATAPACK INSTALL
        // =========================
        DatapackInstaller.init((Main) plugin);
        DatapackInstaller.getInstance().install((Main) plugin);

        // =========================
        // LOG4J INSTALL
        // =========================
        Log4jInstaller.init((Main) plugin);

        // =========================
        // PDC KEYS
        // =========================
        com.mcplugin.Keys.init((Main) plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        try {
            DatabaseManager.close();
        } catch (Exception e) {
            plugin.getLogger().warning("[DatabaseModule] Close error: " + e.getMessage());
        }
    }
}
