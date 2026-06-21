package com.mcplugin.module;

import com.mcplugin.Main;
import com.mcplugin.features.enderchest.EnderChestManager;
import org.bukkit.plugin.java.JavaPlugin;

public class EnderChestModule extends PluginModule {

    public EnderChestModule() { super("EnderChest", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        EnderChestManager.init((Main) plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        EnderChestManager.reloadConfig();
    }
}
