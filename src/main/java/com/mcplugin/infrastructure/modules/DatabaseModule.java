package com.mcplugin.infrastructure.modules;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.commands.vote.VoteManager;
import com.mcplugin.infrastructure.database.DatabaseInit;
import com.mcplugin.infrastructure.database.DatabaseManager;
import com.mcplugin.infrastructure.util.ConsoleLogger;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль базы данных.
 * Essential — без БД плагин не работает.
 */
public class DatabaseModule extends PluginModule {

    public DatabaseModule() {
        super("Database", "infrastructure/database", true); // essential
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        // =========================
        // SQLITE INIT
        // =========================
        DatabaseManager.connect();
        DatabaseInit.init();
        ConsoleLogger.info("[SQLITE] Database initialized successfully.");

        // =========================
        // 🗳 VOTE MANAGER (загрузить голосования из БД)
        // =========================
        VoteManager.init();

    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        // Отменяем все таймеры голосований
        VoteManager.shutdown();
        try {
            DatabaseManager.close();
        } catch (Exception e) {
            ConsoleLogger.warn("[DatabaseModule] Close error: " + e.getMessage());
        }
    }
}
