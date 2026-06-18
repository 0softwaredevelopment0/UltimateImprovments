package com.mcplugin.module;

import com.mcplugin.Main;
import com.mcplugin.main.DatapackInstaller;
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
        plugin.getLogger().info("[DatapackModule] ✓ Datapack installed.");
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        // Nothing to clean up
    }
}
