package com.mcplugin.module;

import com.mcplugin.radiation.RadiationManager;
import org.bukkit.plugin.java.JavaPlugin;

public class RadiationModule extends PluginModule {

    public RadiationModule() { super("Radiation", true); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        RadiationManager.init();
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}
}
