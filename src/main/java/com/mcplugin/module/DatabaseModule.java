package com.mcplugin.module;

import com.mcplugin.Main;
import com.mcplugin.commands.vote.VoteManager;
import com.mcplugin.database.DatabaseInit;
import com.mcplugin.database.DatabaseManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль базы данных.
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
            plugin.getLogger().warning("[DatabaseModule] Close error: " + e.getMessage());
        }
    }
}
