package com.ultimateimprovments.core;

import com.ultimateimprovments.module.ModuleManager;
import com.ultimateimprovments.module.SimpleModules;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public class UIOther extends JavaPlugin implements com.ultimateimprovments.core.SoftReloadable {

    private static UIOther instance;

    public static UIOther getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        runStartup();
    }

    @Override
    public void onDisable() {
        ConsoleLogger.info("[UI-Other] Disabling...");
        HandlerList.unregisterAll(this);
        runStaticShutdown();
        ModuleManager mm = ModuleManager.getInstance();
        if (mm != null) mm.shutdownAll();
        ConsoleLogger.success("[UI-Other] Disabled!");
        instance = null;
    }

    /**
     * In-place reload (soft /ui reload): private cleanup + the same startup
     * path as onEnable. Never disables the plugin — on Paper that would close
     * the JAR and re-enabling does not reopen it ("zip file closed" zombie).
     * Re-registering this addon's modules REPLACES the old instances in the
     * shared ModuleManager (see ModuleManager.register) — the shared manager
     * itself is NOT shut down here (that is owned by the reload coordinator
     * for the full cycle and by the real disable otherwise).
     */
    @Override
    public void softReload() {
        ConsoleLogger.info("[UI-Other] Soft reload (in place)...");
        HandlerList.unregisterAll(this);
        runStaticShutdown();
        runStartup();
    }

    /**
     * Stops the addon-private static systems whose guards survive a plugin
     * cycle. Called by both the real disable and the in-place reload — the
     * start() methods of these systems no-op or double-schedule otherwise.
     */
    private void runStaticShutdown() {
        com.ultimateimprovments.command.vote.VoteManager.shutdown();
        // Reset periodic-task guards, otherwise start() would no-op after a
        // re-enable (running flag survives the plugin cycle).
        com.ultimateimprovments.space.SpaceOxygenListener.stop();
        com.ultimateimprovments.space.SpaceRadiationListener.stop();
        // Stop the dimension-driven gravity task (see SpaceGravityListener).
        com.ultimateimprovments.space.SpaceGravityListener.stop();
        // Cancel still-running rocket lifts; the launching map is static and
        // survived re-enables.
        com.ultimateimprovments.space.SpaceRocketManager.shutdown();
    }

    private void runStartup() {
        instance = this;

        // Single config lives in UI-Core (Main.getInstance().getConfig());
        // UI-Other does not ship its own config.yml.
        ConsoleLogger.info("");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("  UI-Other v" + getPluginMeta().getVersion());
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
        // UI-Items (chunk loader) splits exp bottles — the helper stays here in ui-other.
        com.ultimateimprovments.core.hooks.CoreHooks.setExpBottleUser(
                com.ultimateimprovments.command.subcommands.ExpSplitSubcommand::useBottle);
        initPostModuleSystems();

        // Report module statistics to the addon registry (fed to /ui addon status).
        reportModuleStats(mm);

        ConsoleLogger.success("[UI-Other] All features enabled!");
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
        SimpleModules.registerFeatures(mm);
        SimpleModules.registerEconomy(mm);
        // Custom enchantments are registered by the UI-Enchant addon (EnchantModules).
        SimpleModules.registerProtection(mm);
        SimpleModules.registerUtility(mm);
        SimpleModules.registerBotProtection(mm);
        SimpleModules.registerDisplay(mm);
        SimpleModules.registerMOTD(mm);
        SimpleModules.registerBackground(mm);
        SimpleModules.registerStructureIntegrity(mm);
        // Crafting, item tools, particle and omniscanner live in the UI-Items addon.
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
        getServer().getPluginManager().registerEvents(
                new com.ultimateimprovments.listener.LuckPermsCommandBlocker(), this);

        com.ultimateimprovments.server.AccessListCheckTask.start(main);

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

        // ── Per-player state cleanup on quit ──
        // Several static per-UUID maps (cooldowns, reply targets, code-panel
        // sessions) had no quit handler and grew slowly over time. A single
        // central listener drops all of them in one place.
        getServer().getPluginManager().registerEvents(
                new com.ultimateimprovments.listener.PlayerQuitCleanupListener(), this);

        com.ultimateimprovments.structure.StructureChunkListener.scheduleDelayedRebuild(main);

        ConsoleLogger.info("[UI-Other] Post-module systems ready.");
    }
}
