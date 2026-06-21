package com.mcplugin.module;

import com.mcplugin.Main;
import com.mcplugin.features.boostedcobweb.BoostedCobwebManager;
import org.bukkit.plugin.java.JavaPlugin;

public class BoostedCobwebModule extends PluginModule {

    public BoostedCobwebModule() { super("BoostedCobweb", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        BoostedCobwebManager.init((Main) plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        BoostedCobwebManager.reloadConfig();
    }
}
