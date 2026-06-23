package com.mcplugin.infrastructure.modules;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.core.TaskManager;
import org.bukkit.plugin.java.JavaPlugin;

public class BatteryModule extends PluginModule {

    public BatteryModule() { super("Battery Drain", "energy/storage/battery", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        TaskManager.getInstance().startBatteryTask((Main) plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        TaskManager.getInstance().stopBatteryTask();
    }
}
