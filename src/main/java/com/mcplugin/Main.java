package com.mcplugin;

import com.mcplugin.cable.CableNetwork;
import com.mcplugin.core1.ReactorListener;
import com.mcplugin.core1.ReactorManager;
import com.mcplugin.features.FeaturesManager;
import com.mcplugin.features.integrity.IntegrityCombineListener;
import com.mcplugin.features.integrity.IntegrityListener;
import com.mcplugin.features.magnet.MagnetEventListener;
import com.mcplugin.features.magnet.MagnetManager;
import com.mcplugin.main.CommandRegistrar;
import com.mcplugin.main.DatapackInstaller;
import com.mcplugin.main.TaskManager;
import com.mcplugin.radiation.RadiationManager;
import com.mcplugin.commands.PowerManager;
import com.mcplugin.crafting.*;
import com.mcplugin.database.*;
import com.mcplugin.energy.crafting.*;
import com.mcplugin.guns.plasmacannon.GunListener;
import com.mcplugin.guns.shoker.ShokerListener;
import com.mcplugin.listeners.*;
import com.mcplugin.server.*;
import com.mcplugin.auth.*;
import com.mcplugin.cp.CodePanelGUIListener;
import com.mcplugin.listeners.VoidProtectionListener;

import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

    private static Main instance;

    public static Main getInstance() {
        return instance;
    }

    public java.io.File getPluginFile() {
        return getFile();
    }

    @Override
    public void onEnable() {

        instance = this;

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        saveDefaultConfig();
        reloadConfig();

        // =========================
        // 🔑 PDC KEYS INIT
        // =========================
        Keys.init(this);

        // =========================
        // SQLITE INIT
        // =========================
        try {
            DatabaseManager.connect();
            DatabaseInit.init();
            getLogger().info("[SQLITE] Database initialized successfully.");
        } catch (Exception e) {
            getLogger().severe("[SQLITE] Init failed: " + e.getMessage());
            e.printStackTrace();
        }

        // =========================
        // DATAPACK INSTALL
        // =========================
        DatapackInstaller.init(this);
        DatapackInstaller.getInstance().install(this);

        // =========================
        // CORE SYSTEMS INIT
        // =========================
        try {
            TaskManager.init(this);
            CommandRegistrar.init(this);

            CableNetwork.init();
            EnergyWorkbenchManager.init();
            ReactorManager.init();
            RadiationManager.init();

            MultimeterCraftListener.init();
            PlasmaCannonCraftListener.init();
            ShokerCraftListener.init();
            AntimatterCraftListener.init();
            HealthMeterCraftListener.init();
            EntityLocatorCraftListener.init();
            DosimeterCraftListener.init();
            LeadShieldCraftListener.init();
            RecipeRegistry.init();

            PowerManager.init();
            FeaturesManager.init(this);
            AuthManager.init();
            getLogger().info("[CORE] Systems initialized.");

        } catch (Exception e) {
            getLogger().severe("[CORE] Init failed: " + e.getMessage());
            e.printStackTrace();
        }

        // =========================
        // LISTENERS
        // =========================
        var pm = getServer().getPluginManager();

        pm.registerEvents(new BlockPlaceListener(), this);
        pm.registerEvents(new BlockBreakListener(), this);

        pm.registerEvents(new MultimeterListener(), this);

        pm.registerEvents(new MultimeterCraftListener(), this);
        pm.registerEvents(new PlasmaCannonCraftListener(), this);
        pm.registerEvents(new ShokerCraftListener(), this);
        pm.registerEvents(new AntimatterCraftListener(), this);
        pm.registerEvents(new HealthMeterCraftListener(), this);
        pm.registerEvents(new EntityLocatorCraftListener(), this);
        pm.registerEvents(new DosimeterCraftListener(), this);
        pm.registerEvents(new LeadShieldCraftListener(), this);

        pm.registerEvents(new EnergyCraftingListener(), this);

        pm.registerEvents(new PluginHideListener(), this);
        pm.registerEvents(new PowerInterceptListener(), this);
        pm.registerEvents(new ChatFilterManager(), this);
        pm.registerEvents(new ServerBrandListener(), this);

        pm.registerEvents(new ShokerListener(), this);
        pm.registerEvents(new GunListener(), this);
        pm.registerEvents(new ReactorListener(), this);
        pm.registerEvents(new ShulkerBulletListener(), this);
        pm.registerEvents(FishingListener.getInstance(), this);

        RedstoneGuard.init(this);
        pm.registerEvents(new RedstoneGuardListener(), this);

        pm.registerEvents(new AuthListener(), this);

        pm.registerEvents(new MagnetEventListener(), this);
        pm.registerEvents(new IntegrityListener(), this);
        pm.registerEvents(new IntegrityCombineListener(), this);
        pm.registerEvents(new CodePanelGUIListener(), this);
        pm.registerEvents(new VoidProtectionListener(), this);

        // =========================
        // TASKS & COMMANDS
        // =========================
        TaskManager.getInstance().startAll(this);
        CommandRegistrar.getInstance().registerAll(this);

        getLogger().info("[PLUGIN] Plugin enabled!");
    }

    @Override
    public void onDisable() {

        TaskManager.getInstance().stopAll();

        try {
            CableNetwork.save();
        } catch (Exception e) {
            getLogger().severe("Cable save failed");
        }

        try {
            ReactorManager.saveAll();
        } catch (Exception e) {
            getLogger().severe("Reactor save failed");
        }

        try {
            RadiationManager.saveAll();
        } catch (Exception e) {
            getLogger().severe("Radiation save failed");
        }

        try {
            MagnetManager.saveAll();
        } catch (Exception e) {
            getLogger().severe("Magnet save failed");
        }

        try {
            DatabaseManager.close();
        } catch (Exception e) {
            getLogger().severe("DB close failed");
        }

        getLogger().info("[PLUGIN] Disabled");
    }
}