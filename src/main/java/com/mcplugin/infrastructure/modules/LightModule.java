package com.mcplugin.infrastructure.modules;

import com.mcplugin.energy.consumption.light.LightManager;
import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.core.TaskManager;
import org.bukkit.plugin.java.JavaPlugin;

public class LightModule extends PluginModule {

    public LightModule() { super("Light Multi", "energy/consumption/light", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        LightManager.init();
        TaskManager.getInstance().startLightTask((Main) plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        TaskManager.getInstance().stopLightTask();
        LightManager.saveAll();
    }
}
