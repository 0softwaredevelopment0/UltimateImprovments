package com.ultimateimprovments.module;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.core.DatapackInstaller;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль установки датапака.
 * Неessential — если датапак не установится, плагин всё равно работает.
 */
public class DatapackModule extends PluginModule {

    public DatapackModule() {
        super("Datapack", "infrastructure/core", false);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        DatapackInstaller.init((Main) plugin);
        DatapackInstaller.getInstance().install((Main) plugin);
        // Success is logged inside DatapackInstaller.install()
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        // Nothing to clean up
    }
}
