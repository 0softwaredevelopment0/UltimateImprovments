package com.mcplugin.infrastructure.modules;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.mechanics.features.world.WaypointManager;
import org.bukkit.plugin.java.JavaPlugin;

public class WaypointModule extends PluginModule {

    public WaypointModule() { super("Waypoint", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        WaypointManager.init((Main) plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        WaypointManager.reloadConfig();
    }
}
