package com.ultimateimprovments.module;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.mechanics.features.world.MinecartSpeedManager;
import org.bukkit.plugin.java.JavaPlugin;

public class MinecartSpeedModule extends PluginModule {

    public MinecartSpeedModule() { super("MinecartSpeed", "mechanics/features/minecart_speed", false); }

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
