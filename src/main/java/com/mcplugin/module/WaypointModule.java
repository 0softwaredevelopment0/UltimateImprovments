package com.mcplugin.module;

import com.mcplugin.Main;
import com.mcplugin.features.waypoint.WaypointManager;
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
