package com.mcplugin.infrastructure.modules;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.mechanics.features.blocks.BoostedCobwebManager;
import org.bukkit.plugin.java.JavaPlugin;

public class BoostedCobwebModule extends PluginModule {

    public BoostedCobwebModule() { super("BoostedCobweb", "mechanics/features/cobweb_boost", false); }

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
