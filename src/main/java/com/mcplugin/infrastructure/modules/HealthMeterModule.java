package com.mcplugin.infrastructure.modules;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.mechanics.features.player.HealthMeterManager;
import org.bukkit.plugin.java.JavaPlugin;

public class HealthMeterModule extends PluginModule {

    public HealthMeterModule() { super("HealthMeter", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        HealthMeterManager.init((Main) plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        HealthMeterManager.reloadConfig();
    }
}
