package com.mcplugin.infrastructure.modules;

import com.mcplugin.energy.storage.battery.BatteryManager;
import org.bukkit.plugin.java.JavaPlugin;

public class BatteryMultiModule extends PluginModule {

    public BatteryMultiModule() { super("Battery Multi", "energy/storage/battery", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        BatteryManager.init();
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        BatteryManager.saveAll();
    }
}
