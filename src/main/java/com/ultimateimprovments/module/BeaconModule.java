package com.ultimateimprovments.module;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.mechanics.features.world.BeaconManager;
import org.bukkit.plugin.java.JavaPlugin;

public class BeaconModule extends PluginModule {

    public BeaconModule() { super("Beacon", "mechanics/features/beacon", false); }

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
