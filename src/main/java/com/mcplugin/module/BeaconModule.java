package com.mcplugin.module;

import com.mcplugin.Main;
import com.mcplugin.features.beacon.BeaconManager;
import org.bukkit.plugin.java.JavaPlugin;

public class BeaconModule extends PluginModule {

    public BeaconModule() { super("Beacon", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        BeaconManager.init((Main) plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        BeaconManager.reloadConfig();
    }
}
