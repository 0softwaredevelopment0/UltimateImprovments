package com.mcplugin.infrastructure.modules;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.energy.machines.furnace.ElectricFurnaceManager;
import com.mcplugin.energy.generation.basic.GeneratorManager;
import com.mcplugin.energy.machines.workbench.EnergyCraftingListener;
import com.mcplugin.energy.machines.workbench.EnergyWorkbenchManager;
import org.bukkit.plugin.java.JavaPlugin;

public class EnergyModule extends PluginModule {

    public EnergyModule() { super("Energy", true); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;
        EnergyWorkbenchManager.init();
        main.getServer().getPluginManager().registerEvents(new EnergyCraftingListener(), main);
        ElectricFurnaceManager.init();
        GeneratorManager.init();
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}
}
