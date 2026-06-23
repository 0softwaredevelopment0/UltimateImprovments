package com.mcplugin.infrastructure.modules;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.mechanics.features.world.AntimatterManager;
import org.bukkit.plugin.java.JavaPlugin;

public class AntimatterModule extends PluginModule {

    public AntimatterModule() { super("Antimatter", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        AntimatterManager.init((Main) plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        AntimatterManager.reloadConfig();
    }
}
