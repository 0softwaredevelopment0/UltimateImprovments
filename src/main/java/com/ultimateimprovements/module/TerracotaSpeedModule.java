package com.ultimateimprovements.module;

import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.mechanics.features.blocks.TerracotaSpeedManager;
import org.bukkit.plugin.java.JavaPlugin;

public class TerracotaSpeedModule extends PluginModule {

    public TerracotaSpeedModule() { super("TerracotaSpeed", "mechanics/features/terracotta_speed", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        TerracotaSpeedManager.init((Main) plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        TerracotaSpeedManager.reloadConfig();
    }
}
