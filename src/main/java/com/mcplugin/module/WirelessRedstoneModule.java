package com.mcplugin.module;

import com.mcplugin.core.Main;
import com.mcplugin.mechanics.features.world.WirelessRedstoneManager;
import org.bukkit.plugin.java.JavaPlugin;

public class WirelessRedstoneModule extends PluginModule {

    public WirelessRedstoneModule() {
        super("WirelessRedstone", "mechanics/features/wireless_redstone", false);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        WirelessRedstoneManager.init((Main) plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        WirelessRedstoneManager.restoreAllPowerBlocks();
    }

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        WirelessRedstoneManager.reloadConfig();
    }
}
