package com.mcplugin.module;

import com.mcplugin.Main;
import com.mcplugin.cable.CableNetwork;
import com.mcplugin.core1.ReactorManager;
import com.mcplugin.energy.crafting.EnergyWorkbenchManager;
import com.mcplugin.features.lightning.LightningManager;
import com.mcplugin.radiation.RadiationManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль механик — кабели, энергия, реактор, радиация.
 * Essential — без них основные механики не работают.
 */
public class MechanicsModule extends PluginModule {

    public MechanicsModule() {
        super("Mechanics", true);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        // =========================
        // CABLE NETWORK
        // =========================
        CableNetwork.init();

        // =========================
        // ENERGY WORKBENCH
        // =========================
        EnergyWorkbenchManager.init();

        // =========================
        // REACTOR
        // =========================
        ReactorManager.init();

        // =========================
        // RADIATION
        // =========================
        RadiationManager.init();

        // =========================
        // LIGHTNING STRUCTURE
        // =========================
        LightningManager.init();
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        // Save handled by AutoSaveModule
    }
}
