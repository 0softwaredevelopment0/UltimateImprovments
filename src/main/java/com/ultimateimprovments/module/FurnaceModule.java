package com.ultimateimprovments.module;

import com.ultimateimprovments.energy.machines.furnace.ElectricFurnaceManager;
import org.bukkit.plugin.java.JavaPlugin;

public class FurnaceModule extends PluginModule {

    public FurnaceModule() { super("Electric Furnace", "energy/machines/furnace", true); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        ElectricFurnaceManager.init();
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        ElectricFurnaceManager.shutdown();
    }
}
