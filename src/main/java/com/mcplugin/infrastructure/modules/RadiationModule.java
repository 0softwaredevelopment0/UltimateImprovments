package com.mcplugin.infrastructure.modules;

import com.mcplugin.mechanics.environment.radiation.RadiationManager;
import org.bukkit.plugin.java.JavaPlugin;

public class RadiationModule extends PluginModule {

    public RadiationModule() { super("Radiation", "mechanics/environment/radiation", true); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        RadiationManager.init();
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}
}
