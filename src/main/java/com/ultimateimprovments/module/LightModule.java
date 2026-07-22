package com.ultimateimprovments.module;

import com.ultimateimprovments.energy.consumption.light.LightManager;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.core.TaskManager;
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
        // Marker'ы сохраняются в world-файлах, save не нужен
    }
}
