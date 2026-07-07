package com.mcplugin.module;

import com.mcplugin.core.Main;
import com.mcplugin.database.AsyncAutoSaveManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль автосохранения — периодически сохраняет состояние систем в БД.
 * Неessential — если не работает, данные просто не сохраняются автоматически.
 */
public class AutoSaveModule extends PluginModule {

    public AutoSaveModule() {
        super("AutoSave", "infrastructure/database", false);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        AsyncAutoSaveManager.init((Main) plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        AsyncAutoSaveManager.shutdown();

        // Сохраняем все системы синхронно при выключении
        AsyncAutoSaveManager.saveAllNow();
    }
}
