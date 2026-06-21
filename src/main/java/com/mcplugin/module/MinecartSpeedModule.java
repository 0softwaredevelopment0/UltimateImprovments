package com.mcplugin.module;

import com.mcplugin.Main;
import com.mcplugin.features.minecartspeed.MinecartSpeedManager;
import org.bukkit.plugin.java.JavaPlugin;

public class MinecartSpeedModule extends PluginModule {

    public MinecartSpeedModule() { super("MinecartSpeed", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        MinecartSpeedManager.init((Main) plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        MinecartSpeedManager.shutdown();
    }

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        MinecartSpeedManager.reloadConfig();
    }
}
