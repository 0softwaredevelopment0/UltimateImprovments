package com.mcplugin.module;

import com.mcplugin.Main;
import com.mcplugin.energy.ElectricFurnaceManager;
import com.mcplugin.energy.GeneratorManager;
import com.mcplugin.energy.crafting.EnergyCraftingListener;
import com.mcplugin.energy.crafting.EnergyWorkbenchManager;
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
