package com.mcplugin.module;

import com.mcplugin.Main;
import com.mcplugin.features.itemskill.ItemKillManager;
import org.bukkit.plugin.java.JavaPlugin;

public class ItemKillModule extends PluginModule {

    public ItemKillModule() { super("ItemKill", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        ItemKillManager.init((Main) plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        ItemKillManager.reloadConfig();
    }
}
