package com.ultimateimprovments.core;

import com.ultimateimprovments.module.ModuleManager;
import com.ultimateimprovments.module.SimpleModules;
import com.ultimateimprovments.module.meteor.MeteorModule;
import com.ultimateimprovments.mechanics.features.omniscanner.OmniscannerModule;
import com.ultimateimprovments.mechanics.protection.ProtectionModule;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public class UIOther extends JavaPlugin {

    private static UIOther instance;

    public static UIOther getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        // Save own config.yml (plugins/UI-Other/config.yml)
        saveDefaultConfig();

        ConsoleLogger.info("");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("  UI-Other v" + getDescription().getVersion());
        ConsoleLogger.info("===========================================");

        ModuleManager mm = ModuleManager.getInstance();
        if (mm == null) {
            ConsoleLogger.error("[UI-Other] UI-Core not loaded! Disabling...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        registerAllModules(mm);
        mm.initAll();
        initPostModuleSystems();

        ConsoleLogger.success("[UI-Other] All features enabled!");
    }

    @Override
    public void onDisable() {
        ConsoleLogger.info("[UI-Other] Disabling...");
        HandlerList.unregisterAll(this);
        ModuleManager mm = ModuleManager.getInstance();
        if (mm != null) mm.shutdownAll();
        // Unfreeze any players still under an anti-cheat check before the plugin
        // is disabled/reloaded — otherwise they'd be stuck with 0 walk speed.
        com.ultimateimprovments.mechanics.security.check.CheckManager.shutdown();
        ConsoleLogger.success("[UI-Other] Disabled!");
    }

    private void registerAllModules(ModuleManager mm) {
        SimpleModules.registerMechanics(mm);
        SimpleModules.registerCrafting(mm);
        SimpleModules.registerSudo(mm);
        SimpleModules.registerFeatures(mm);
        mm.register(new MeteorModule());
        SimpleModules.registerEconomy(mm);
        SimpleModules.registerAOEEnchantment(mm);
        SimpleModules.registerAutoSmeltEnchantment(mm);
        SimpleModules.registerVeinMinerEnchantment(mm);
        SimpleModules.registerTreeCapitatorEnchantment(mm);
        SimpleModules.registerFlightEnchantment(mm);
        SimpleModules.registerMagnetEnchantment(mm);
        SimpleModules.registerIgnitingEnchantment(mm);
        SimpleModules.registerLevitationEnchantment(mm);
        SimpleModules.registerSelfDestructEnchantment(mm);
        SimpleModules.registerDegradationEnchantment(mm);
        SimpleModules.registerAttackAoeEnchantment(mm);
        SimpleModules.registerItemStealingEnchantment(mm);
        SimpleModules.registerRepairingEnchantment(mm);
        SimpleModules.registerProtection(mm);
        mm.register(new ProtectionModule());
        SimpleModules.registerUtility(mm);
        SimpleModules.registerBotProtection(mm);
        SimpleModules.registerDisplay(mm);
        SimpleModules.registerMOTD(mm);
        SimpleModules.registerBackground(mm);
        SimpleModules.registerParticle(mm);
        mm.register(new OmniscannerModule());
        SimpleModules.registerStructureIntegrity(mm);
    }

    private void initPostModuleSystems() {
        Main main = Main.getInstance();

        // Structure data (markers, chunk tracking) is owned by UI-MBS —
        // this listener only wires the structure managers back together.
        getServer().getPluginManager().registerEvents(
                new com.ultimateimprovments.structure.StructureChunkListener(), main);

        getServer().getPluginManager().registerEvents(
                new com.ultimateimprovments.listener.WhitelistCommandBlocker(), main);
        getServer().getPluginManager().registerEvents(
                new com.ultimateimprovments.listener.OpCommandBlocker(), main);
        getServer().getPluginManager().registerEvents(
                new com.ultimateimprovments.listener.LuckPermsCommandBlocker(), main);

        com.ultimateimprovments.server.AccessListCheckTask.start(main);
        com.ultimateimprovments.mechanics.security.check.CheckManager.init();
        getServer().getPluginManager().registerEvents(
                new com.ultimateimprovments.mechanics.security.check.CheckListener(), main);


        com.ultimateimprovments.space.SpaceManager.createTable();
        com.ultimateimprovments.space.SpaceManager.init(main);
        getServer().getPluginManager().registerEvents(
                new com.ultimateimprovments.space.SpaceGravityListener(), main);
        getServer().getPluginManager().registerEvents(
                new com.ultimateimprovments.space.SpaceRocketManager(), main);
        com.ultimateimprovments.space.SpaceRocketManager.registerRecipe(main);
        getServer().getPluginManager().registerEvents(
                new com.ultimateimprovments.space.SpaceOxygenListener(), main);
        com.ultimateimprovments.space.SpaceOxygenListener.start(main);
        getServer().getPluginManager().registerEvents(
                new com.ultimateimprovments.space.SpaceRadiationListener(), main);
        com.ultimateimprovments.space.SpaceRadiationListener.start(main);

        CommandRegistrar.getInstance().registerAll(main);
        com.ultimateimprovments.command.PluginReloadCommand.init();
        getServer().getPluginManager().registerEvents(
                new com.ultimateimprovments.command.SuicideDeathListener(), main);

        com.ultimateimprovments.structure.StructureChunkListener.scheduleDelayedRebuild(main);

        ConsoleLogger.info("[UI-Other] Post-module systems ready.");
    }
}
