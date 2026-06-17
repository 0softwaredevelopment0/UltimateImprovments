package com.mcplugin.module;

import com.mcplugin.Main;
import com.mcplugin.auth.AuthManager;
import com.mcplugin.cable.CableNetwork;
import com.mcplugin.commands.PowerManager;
import com.mcplugin.core1.ReactorManager;
import com.mcplugin.crafting.*;
import com.mcplugin.energy.crafting.EnergyWorkbenchManager;
import com.mcplugin.features.FeaturesManager;
import com.mcplugin.main.CommandRegistrar;
import com.mcplugin.main.TaskManager;
import com.mcplugin.radiation.RadiationManager;
import com.mcplugin.server.EmergencyEntitiesKill;
import com.mcplugin.server.PacketGuard;
import com.mcplugin.server.RedstoneGuard;
import com.mcplugin.server.ServerOverloadWarning;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль ядра — все ключевые системы плагина.
 * Essential — без них плагин бесполезен.
 */
public class CoreSystemsModule extends PluginModule {

    public CoreSystemsModule() {
        super("CoreSystems", true);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;

        // =========================
        // TASK MANAGER & COMMANDS
        // =========================
        TaskManager.init(main);
        CommandRegistrar.init(main);

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
        // CRAFTING LISTENERS
        // =========================
        MultimeterCraftListener.init();
        PlasmaCannonCraftListener.init();
        ShokerCraftListener.init();
        AntimatterCraftListener.init();
        HealthMeterCraftListener.init();
        EntityLocatorCraftListener.init();
        DosimeterCraftListener.init();
        LeadShieldCraftListener.init();
        RecipeRegistry.init();

        // =========================
        // POWER MANAGER
        // =========================
        PowerManager.init();

        // =========================
        // FEATURES
        // =========================
        FeaturesManager.init(main);

        // =========================
        // AUTH
        // =========================
        AuthManager.init();

        // =========================
        // REDSTONE GUARD
        // =========================
        RedstoneGuard.init(main);

        // =========================
        // PACKET GUARD
        // =========================
        PacketGuard.init(main);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        // Core systems save is handled by AutoSaveModule
    }

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        RedstoneGuard.reload();
        EmergencyEntitiesKill.reload();
        ServerOverloadWarning.reload();
        PowerManager.reloadConfig();
        FeaturesManager.reloadConfig();
    }
}
