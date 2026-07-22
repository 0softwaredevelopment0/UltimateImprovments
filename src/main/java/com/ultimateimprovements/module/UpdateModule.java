package com.ultimateimprovements.module;

import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.mechanics.features.updater.UpdateChecker;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль проверки обновлений — асинхронно проверяет GitHub Releases.
 * Неessential — обновления можно проверить вручную.
 */
public class UpdateModule extends PluginModule {

    public UpdateModule() {
        super("UpdateChecker", false);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        UpdateChecker.checkAsync();
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        // Nothing to clean up — async task handles itself
    }
}
