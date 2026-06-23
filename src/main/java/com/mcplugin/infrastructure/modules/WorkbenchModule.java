package com.mcplugin.infrastructure.modules;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.energy.machines.workbench.EnergyCraftingListener;
import com.mcplugin.energy.machines.workbench.EnergyWorkbenchManager;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public class WorkbenchModule extends PluginModule {

    private EnergyCraftingListener craftingListener;

    public WorkbenchModule() { super("Energy Workbench", "energy/machines/workbench", true); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;
        EnergyWorkbenchManager.init();
        craftingListener = new EnergyCraftingListener();
        main.getServer().getPluginManager().registerEvents(craftingListener, main);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        if (craftingListener != null) {
            HandlerList.unregisterAll(craftingListener);
            craftingListener = null;
        }
    }
}
