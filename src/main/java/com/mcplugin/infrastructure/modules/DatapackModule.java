package com.mcplugin.infrastructure.modules;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.core.DatapackInstaller;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль установки датапака.
 * Неessential — если датапак не установится, плагин всё равно работает.
 */
public class DatapackModule extends PluginModule {

    public DatapackModule() {
        super("Datapack", false);
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
