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

        // Single config lives in UI-Core (Main.getInstance().getConfig());
        // UI-Other does not ship its own config.yml.
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
        com.ultimateimprovments.module.SimpleModules.initPostCoreSubsystems();
        initPostModuleSystems();

        // Report module statistics to the addon registry (fed to /ui addon status).
        reportModuleStats(mm);

        ConsoleLogger.success("[UI-Other] All features enabled!");
    }

    @Override
    public void onDisable() {
        ConsoleLogger.info("[UI-Other] Disabling...");
        HandlerList.unregisterAll(this);
        com.ultimateimprovments.command.vote.VoteManager.shutdown();
        com.ultimateimprovments.op.OpManager.shutdown();
        ModuleManager mm = ModuleManager.getInstance();
        if (mm != null) mm.shutdownAll();
        // Unfreeze any players still under an anti-cheat check before the plugin
        // is disabled/reloaded — otherwise they'd be stuck with 0 walk speed.
        com.ultimateimprovments.mechanics.security.check.CheckManager.shutdown();
        // Reset periodic-task guards, otherwise start() would no-op after a
        // re-enable (running flag survives the plugin cycle).
        com.ultimateimprovments.space.SpaceOxygenListener.stop();
        com.ultimateimprovments.space.SpaceRadiationListener.stop();
        // Stop the dimension-driven gravity task (see SpaceGravityListener).
        com.ultimateimprovments.space.SpaceGravityListener.stop();
        // Cancel still-running rocket lifts; the launching map is static and
        // survived re-enables.
        com.ultimateimprovments.space.SpaceRocketManager.shutdown();
        ConsoleLogger.success("[UI-Other] Disabled!");
    }

    /**
     * Feeds this addon's module counters + failures into AddonRegistry so
     * {@code /ui addon status UI-Other} and the load-error counters stay truthful.
     */
    private void reportModuleStats(ModuleManager mm) {
        try {
            var modules = mm.getModules();
            int total = modules.size();
            int loaded = 0;
            for (var m : modules) {
                if (m.isEnabled()) {
                    loaded++;
                } else {
                    com.ultimateimprovments.addon.AddonRegistry.reportError(getName(),
                            "module " + m.getName() + " failed: " + m.getDisableReason());
                }
            }
            com.ultimateimprovments.addon.AddonRegistry.reportModules(getName(), loaded, total);
        } catch (Throwable t) {
            ConsoleLogger.warn("[UI-Other] Module stats report failed: " + t.getMessage());
        }
    }

    private void registerAllModules(ModuleManager mm) {
        // Must run first: initializes TaskManager, CommandRegistrar and the
        // general listeners that many other modules below depend on.
        SimpleModules.registerCoreModules(mm);
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
        SimpleModules.registerCurseTrioEnchantments(mm);
        SimpleModules.registerAttackAoeEnchantment(mm);
        SimpleModules.registerItemStealingEnchantment(mm);
        SimpleModules.registerRepairingEnchantment(mm);
        SimpleModules.registerContainerStealingEnchantment(mm);
        SimpleModules.registerEnchantTableBridge(mm);
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
                new com.ultimateimprovments.structure.StructureChunkListener(), this);

        getServer().getPluginManager().registerEvents(
                new com.ultimateimprovments.listener.WhitelistCommandBlocker(), this);
        getServer().getPluginManager().registerEvents(
                new com.ultimateimprovments.listener.OpCommandBlocker(), this);
        com.ultimateimprovments.op.OpManager.init();
        getServer().getPluginManager().registerEvents(
                new com.ultimateimprovments.listener.LuckPermsCommandBlocker(), this);

        com.ultimateimprovments.server.AccessListCheckTask.start(main);
        com.ultimateimprovments.mechanics.security.check.CheckManager.init();
        getServer().getPluginManager().registerEvents(
                new com.ultimateimprovments.mechanics.security.check.CheckListener(), this);


        com.ultimateimprovments.space.SpaceManager.createTable();
        com.ultimateimprovments.space.SpaceManager.init(main);
        com.ultimateimprovments.space.SpaceGravityListener.reloadConfig();
        com.ultimateimprovments.space.SpaceGravityListener.start(main);
        getServer().getPluginManager().registerEvents(
                new com.ultimateimprovments.space.SpaceRocketManager(), this);
        com.ultimateimprovments.space.SpaceRocketManager.registerRecipe(main);
        getServer().getPluginManager().registerEvents(
                new com.ultimateimprovments.space.SpaceOxygenListener(), this);
        com.ultimateimprovments.space.SpaceOxygenListener.start(main);
        getServer().getPluginManager().registerEvents(
                new com.ultimateimprovments.space.SpaceRadiationListener(), this);
        com.ultimateimprovments.space.SpaceRadiationListener.start(main);

        CommandRegistrar.getInstance().registerAll(main);
        com.ultimateimprovments.command.PluginReloadCommand.init();
        getServer().getPluginManager().registerEvents(
                new com.ultimateimprovments.command.SuicideDeathListener(), this);

        // ── Dialog handlers (PlayerCustomClickEvent) ──
        // Dialog screens are opened by commands and modules (getpos, sharepos,
        // askcords, chgdim, auth, codepane, sudo); without these listeners the
        // dialog buttons (submit/cancel) would never fire.
        // Registered under `this` (UI-Other) so onDisable can remove them —
        // registering under UI-Core would double them on a re-enable.
        com.ultimateimprovments.command.AskPosDialogHandler.register(this);
        com.ultimateimprovments.command.GetPosDialogHandler.register(this);
        com.ultimateimprovments.command.SharePosDialogHandler.register(this);
        com.ultimateimprovments.command.ChgDimDialogHandler.register(this);
        com.ultimateimprovments.mechanics.security.auth.AuthDialogHandler.register(this);
        com.ultimateimprovments.mechanics.security.codepanel.CodePanelDialogHandler.register(this);
        com.ultimateimprovments.mechanics.security.sudo.SudoDialogHandler.register(this);

        // ── Per-player state cleanup on quit ──
        // Several static per-UUID maps (cooldowns, reply targets, code-panel
        // sessions) had no quit handler and grew slowly over time. A single
        // central listener drops all of them in one place.
        getServer().getPluginManager().registerEvents(
                new com.ultimateimprovments.listener.PlayerQuitCleanupListener(), this);

        // ── Maintenance mode ──
        // /ui maint reads MaintenanceManager.getInstance(); without init() the
        // instance is null and every /ui maint invocation threw an NPE.
        com.ultimateimprovments.maintenance.MaintenanceManager.init();

        com.ultimateimprovments.structure.StructureChunkListener.scheduleDelayedRebuild(main);

        ConsoleLogger.info("[UI-Other] Post-module systems ready.");
    }
}