package com.ultimateimprovments.core;

import com.ultimateimprovments.config.ConfigCrashSalvage;
import com.ultimateimprovments.config.ConfigIntegrityValidator;
import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.maintenance.MaintenanceManager;
import com.ultimateimprovments.mechanics.features.omniscanner.OmniscannerModule;
import com.ultimateimprovments.module.*;
import com.ultimateimprovments.report.ReportManager;
import com.ultimateimprovments.whitelist.OpWhitelistManager;
import com.ultimateimprovments.listener.LuckPermsCommandBlocker;
import com.ultimateimprovments.listener.OpCommandBlocker;
import com.ultimateimprovments.listener.WhitelistCommandBlocker;
import com.ultimateimprovments.util.AuthCommandLogFilter;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.FileLogger;
import com.ultimateimprovments.util.PlaceholderResolver;
import com.ultimateimprovments.mechanics.security.check.CheckListener;
import com.ultimateimprovments.mechanics.security.check.CheckManager;
import com.ultimateimprovments.structure.StructureChunkListener;
import com.ultimateimprovments.structure.StructureChunkTracker;
import com.ultimateimprovments.structure.StructureMarker;

/**
 * PluginStartup — the initialization nesting doll of UltimateImprovments.
 * <p>
 * Called from {@link Main#onEnable()}.
 * Splits startup into logical phases: infrastructure → modules → post-modules → finish.
 * Each phase is a separate method, giving a readable tree structure.
 */
public class PluginStartup {

    private final Main plugin;
    private static boolean startupPerformed = false;

    public PluginStartup(Main plugin) {
        this.plugin = plugin;
    }

    // ==========================================================================
    // 🚀 STARTUP — root of the matryoshka
    // ==========================================================================

    public void startupPlugin() {
        // Guard: prevents double startup (e.g. from PlugMan enable without disable)
        if (startupPerformed) {
            ConsoleLogger.warn("[Startup] Already performed! Doing full reset first...");
            try {
                new PluginShutdown(plugin).shutdownPlugin();
            } catch (Exception e) {
                ConsoleLogger.warn("[Startup] Reset shutdown warning: " + e.getMessage());
            }
        }
        startupPerformed = true;

        // ConsoleLogger init FIRST — before any log calls
        ConsoleLogger.init();

        // Auth command log filter — hide passwords from server console
        AuthCommandLogFilter.register();

        // Auth Dialog handler — registered as early as possible to not miss events
        com.ultimateimprovments.mechanics.security.auth.AuthDialogHandler.register();

        // Sudo Dialog handler — registered as early as possible to not miss events
        com.ultimateimprovments.mechanics.security.sudo.SudoDialogHandler.register();

        // ChgDim Dialog handler — registered as early as possible to not miss events
        com.ultimateimprovments.command.ChgDimDialogHandler.register();

        // AskPos Dialog handler — coordinate request dialogs (/ui askpos)
        com.ultimateimprovments.command.AskPosDialogHandler.register();

        // GetPos Dialog handler — coordinate lookup dialogs (/ui getpos)
        com.ultimateimprovments.command.GetPosDialogHandler.register();

        // SharePos Dialog handler — coordinate sharing confirmation dialogs (/ui sharepos)
        com.ultimateimprovments.command.SharePosDialogHandler.register();

        // Code Panel Dialog handler — registered as early as possible to not miss events
        com.ultimateimprovments.mechanics.security.codepanel.CodePanelDialogHandler.register();

        ConsoleLogger.info("");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("  UltimateImprovments — Starting up...");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("");

        // Java version check: warn, but do NOT disable the plugin.
        // Modules with incompatible classes will fail on their own (no stacktraces — see PluginModule).
        checkJavaVersion();

        initInfrastructure();
        initModuleSystem();
        initPostModuleSystems();
        finishStartup();
    }

    /**
     * Checks the Java version and prints a warning if the plugin classes are
     * incompatible with the current Java. Does NOT disable the plugin — only warns.
     * <p>
     * Instead of hardcoding a version number — actually tries to load one test class.
     * If Paper cannot convert the class (IllegalArgumentException),
     * prints one clear message instead of 60+ 'Fatal error' lines from Paper.
     */
    private void checkJavaVersion() {
        try {
            this.plugin.getClass().getClassLoader().loadClass(
                    "com.ultimateimprovments.core.DatapackInstaller");
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("major version") || msg.contains("Unsupported class file")) {
                ConsoleLogger.warn("");
                ConsoleLogger.warn("============================================");
                ConsoleLogger.warn("  Java version may be incompatible!");
                ConsoleLogger.warn("  Server Java: " + Runtime.version());
                ConsoleLogger.warn("  Some modules may fail to load.");
                ConsoleLogger.warn("  Update your Java Runtime if needed.");
                ConsoleLogger.warn("============================================");
                ConsoleLogger.warn("");
            }
        } catch (ClassNotFoundException ignored) {
            // The class must exist — but if not, just continue
        }
    }

    // ==========================================================================
    // 🏗 PHASE 1: INFRASTRUCTURE
    // ==========================================================================

    private void initInfrastructure() {
        // Plugin permissions (canonical list in Permissions) — registered before
        // all modules so any code can rely on them.
        Permissions.registerAll();

        FileLogger.ensureDirectory(plugin.getDataFolder(), "DataFolder");
        loadConfigFile();

        ConfigIntegrityValidator.validate(plugin);

        // Datapack module toggles (datapack.modules.*) — read before any
        // module registration / datapack install so disabled parts are skipped.
        DatapackModules.init(plugin);

        MessagesManager.init(plugin);

        PlaceholderResolver.init();

        // PlaceholderAPI hook — register UIPlaceholderExpansion
        // ONLY if PAPI is installed; otherwise only the internal resolver works.
        if (PlaceholderResolver.isPapiAvailable()) {
            try {
                com.ultimateimprovments.hook.UIPlaceholderExpansion expansion =
                        new com.ultimateimprovments.hook.UIPlaceholderExpansion();
                if (expansion.register()) {
                    ConsoleLogger.info("[PlaceholderAPI] UltimateImprovments expansion registered (" +
                            expansion.getIdentifier() + " — " +
                            PlaceholderResolver.getBuiltinNames().size() + " placeholders)");
                } else {
                    ConsoleLogger.warn("[PlaceholderAPI] Could not register UltimateImprovments expansion");
                }
            } catch (Throwable t) {
                ConsoleLogger.warn("[PlaceholderAPI] Registration failed: " + t.getMessage());
            }
        } else {
            ConsoleLogger.info("[PlaceholderAPI] PlaceholderAPI not found — placeholders work only inside the plugin");
        }

        Keys.init(plugin);
        MaintenanceManager.init();

        ConsoleLogger.info("[Init] Infrastructure ready.");
    }

    private void loadConfigFile() {
        java.io.File configFile = new java.io.File(plugin.getDataFolder(), "config.yml");
        boolean configExisted = configFile.exists();
        plugin.saveDefaultConfig();

        if (!configExisted && configFile.exists()) {
            ConsoleLogger.info("[Config] Created new file: config.yml");
        } else if (configExisted) {
            ConsoleLogger.info("[Config] File exists: config.yml");
        }

        // Paper-26: reloadConfig() НЕ бросает на битом YAML — loadConfiguration молча
        // глотает ошибку парсинга (логирует и возвращает пустой конфиг + дефолты из JAR).
        // Поэтому парсинг проверяем САМИ, до reloadConfig:
        // «синтаксический краш → игнор секции» — ConfigCrashSalvage удаляет только
        // сломанные секции (с бэкапом в config-broken/), остальные настройки сохраняются,
        // а ConfigRepairManager (вызывается следом) допишет дефолты удалённых секций.
        if (!ConfigCrashSalvage.salvage(plugin)) {
            // Запасной вариант: сломанную часть не удалось локализовать —
            // пересоздаём файл из JAR (старое поведение), с бэкапом оригинала.
            ConsoleLogger.warn("[Config] Could not isolate the broken part — recreating config.yml from JAR...");
            ConfigCrashSalvage.backupWholeFile(plugin);
            if (configFile.exists()) configFile.delete();
            plugin.saveDefaultConfig();
        }

        try {
            plugin.reloadConfig();
        } catch (Exception e) {
            // Страховка на случай другого сбоя загрузки.
            ConsoleLogger.warn("[Config] Failed to load config.yml: " + e.getMessage());
            ConsoleLogger.warn("[Config] Deleting broken config.yml and recreating from JAR...");
            if (configFile.exists()) configFile.delete();
            plugin.saveDefaultConfig();
            plugin.reloadConfig();
            ConsoleLogger.info("[Config] Recreated config.yml from JAR resources.");
        }
    }

    // ==========================================================================
    // 🧩 PHASE 2: MODULES
    // ==========================================================================

    private void initModuleSystem() {
        ModuleManager.init(plugin);
        var mm = ModuleManager.getInstance();

        // Modules are registered manually — this is the full and ordered list.
        // Auto-scanning (ModuleScanner) is not used: its path was previously written
        // with old spelling and never found modules; and if it did work it would skip
        // modules outside the com/ultimateimprovments/module package
        // (OmniscannerModule, ProtectionModule).
        registerSystemModules(mm);
        registerEnergyModules(mm);
        registerMechanicsModules(mm);
        registerFeatureModules(mm);
        registerProtectionModules(mm);
        registerUtilityModules(mm);
        registerDisplayModules(mm);
        registerBackgroundModules(mm);
        registerAdminModules(mm);
        registerSecurityModules(mm);

        // Init all modules (each in try-catch, failures don't break others)
        mm.initAll();

        ConsoleLogger.info("[Init] Module system ready.");
    }

    private void registerSystemModules(ModuleManager mm) {
        mm.register(new VersionCheckModule());
        // Database, Core
        SimpleModules.registerCoreModules(mm);
        // Datapack, Power
        SimpleModules.registerSystem(mm);
    }

    private void registerEnergyModules(ModuleManager mm) {
        // Cable, Basic Generator, Reactor, Electric Furnace, Battery Drain, Battery Multi, Light Multi
        SimpleModules.registerEnergy(mm);
        // Custom recipe gating (Crafter-only crafting)
        SimpleModules.registerEnergyMachines(mm);
    }

    private void registerMechanicsModules(ModuleManager mm) {
        // Radiation, Lightning, Auth
        SimpleModules.registerMechanics(mm);
        // Crafting
        SimpleModules.registerCrafting(mm);
        // Sudo
        SimpleModules.registerSudo(mm);
    }

    private void registerFeatureModules(ModuleManager mm) {
        // Attributes, Beacon, BlockDmg, BlockCollapse, BoostedCobweb, DragonEgg, EntityLocator,
        // Magnet, ModeProtect, TerracotaSpeed, Waypoint, Integrity, Antimatter, UnbreakableBreaker,
        // DeathBell, EnderChest, GlassBreak, ShieldSlowness, CreativeItemValidator,
        // ContainerTrigger, Vanish, Notes, MinecartSpeed, WirelessRedstone
        SimpleModules.registerFeatures(mm);
        mm.register(new com.ultimateimprovments.module.meteor.MeteorModule());
        // Economy
        SimpleModules.registerEconomy(mm);
        // AOEEnchantment
        SimpleModules.registerAOEEnchantment(mm);
        // AutoSmeltEnchantment
        SimpleModules.registerAutoSmeltEnchantment(mm);
        // VeinMinerEnchantment
        SimpleModules.registerVeinMinerEnchantment(mm);
        // TreeCapitatorEnchantment
        SimpleModules.registerTreeCapitatorEnchantment(mm);
        // FlightEnchantment
        SimpleModules.registerFlightEnchantment(mm);
        // MagnetEnchantment
        SimpleModules.registerMagnetEnchantment(mm);
        // IgnitingEnchantment
        SimpleModules.registerIgnitingEnchantment(mm);
        // LevitationEnchantment
        SimpleModules.registerLevitationEnchantment(mm);
        // SelfDestructEnchantment
        SimpleModules.registerSelfDestructEnchantment(mm);
        // DegradationEnchantment
        SimpleModules.registerDegradationEnchantment(mm);
        // AttackAoeEnchantment
        SimpleModules.registerAttackAoeEnchantment(mm);
        // ItemStealingEnchantment
        SimpleModules.registerItemStealingEnchantment(mm);
        // RepairingEnchantment
        SimpleModules.registerRepairingEnchantment(mm);
        // Turret — end crystal turrets
        SimpleModules.registerTurret(mm);
    }

    private void registerProtectionModules(ModuleManager mm) {
        // RedstoneGuard, PacketGuard, ProxyServer
        SimpleModules.registerProtection(mm);
        mm.register(new com.ultimateimprovments.mechanics.protection.ProtectionModule());
    }

    private void registerUtilityModules(ModuleManager mm) {
        // ChatFilter, Chat, VoidProtection
        SimpleModules.registerUtility(mm);
        // BotProtection
        SimpleModules.registerBotProtection(mm);
    }

    private void registerDisplayModules(ModuleManager mm) {
        // Tab, Scoreboard, BossBar
        SimpleModules.registerDisplay(mm);
        // MOTD
        SimpleModules.registerMOTD(mm);
    }

    private void registerBackgroundModules(ModuleManager mm) {
        // Tasks, AutoSave, UpdateChecker, Leash, ElytraBoost, AutoBroadcast
        SimpleModules.registerBackground(mm);
    }

    private void registerAdminModules(ModuleManager mm) {
        // ParticleAccelerator
        SimpleModules.registerParticle(mm);
        mm.register(new OmniscannerModule());
    }

    private void registerSecurityModules(ModuleManager mm) {
        // Punish
        SimpleModules.registerPunish(mm);
        mm.register(new AntiCheatModule());
        // StructureIntegrity
        SimpleModules.registerStructureIntegrity(mm);
    }

    // ==========================================================================
    // 🔗 PHASE 3: POST-MODULE SYSTEMS
    // ==========================================================================

    private void initPostModuleSystems() {
        // Structure chunk listener & tracker (after DB init from modules)
        plugin.getServer().getPluginManager().registerEvents(new StructureChunkListener(), plugin);
        // Structure markers: the source of truth is SQLite (instead of in-world Marker entities).
        // 1) load all structure data from the DB into the cache
        StructureMarker.loadFromDatabase();
        // 2) read the chunks holding structures and force-load them
        StructureChunkTracker.load();
        StructureChunkTracker.loadTrackedChunks();
        // 3) one-time migration of legacy Marker entities into the DB (first run after update)
        StructureMarker.migrateLegacyMarkers();

        // OP whitelist
        OpWhitelistManager.init(plugin);

        // Custom whitelist
        com.ultimateimprovments.whitelist.WhitelistManager.init(plugin);

        // Block vanilla commands
        plugin.getServer().getPluginManager().registerEvents(new WhitelistCommandBlocker(), plugin);
        plugin.getServer().getPluginManager().registerEvents(new OpCommandBlocker(), plugin);

        // Warn on LuckPerms wildcard (*) permission grants
        plugin.getServer().getPluginManager().registerEvents(new LuckPermsCommandBlocker(), plugin);

        // Periodic access list check
        com.ultimateimprovments.server.AccessListCheckTask.start(plugin);

        // Blacklist
        com.ultimateimprovments.whitelist.BlacklistManager.init(plugin);

        // Reports
        ReportManager.init();

        // Anti-cheat check system
        CheckManager.init();
        plugin.getServer().getPluginManager().registerEvents(new CheckListener(), plugin);

        // CmdLog — /ui cmdlog <on|off>: player command logging to chat (state in DB)
        com.ultimateimprovments.chat.CmdLogger.init(plugin);

        // Clan friendly fire — /ui clan edit selfpvp <on|off> (state in DB, default off)
        com.ultimateimprovments.command.clan.ClanFriendlyFireListener.init(plugin);

        // OJM — override join/leave messages (config section ojm)
        com.ultimateimprovments.chat.OjmManager.init(plugin);

        // 🚀 SPACE DIMENSION
        com.ultimateimprovments.space.SpaceManager.createTable();
        com.ultimateimprovments.space.SpaceManager.init(plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new com.ultimateimprovments.space.SpaceGravityListener(), plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new com.ultimateimprovments.space.SpaceRocketManager(), plugin);
        com.ultimateimprovments.space.SpaceOxygenListener.start(plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new com.ultimateimprovments.space.SpaceRadiationListener(), plugin);
        com.ultimateimprovments.space.SpaceRadiationListener.start(plugin);

        // Commands — register Bukkit commands via CommandMap
        CommandRegistrar.getInstance().registerAll(plugin);

        // SubCommand registry — initialize the /ultimateimprovments dispatcher
        com.ultimateimprovments.command.PluginReloadCommand.init();

        // Custom suicide death message
        plugin.getServer().getPluginManager().registerEvents(
                new com.ultimateimprovments.command.SuicideDeathListener(), plugin);

        ConsoleLogger.info("[Init] Post-module systems ready.");
    }

    /**
     * Resets the startupPerformed flag. Called from PluginShutdown
     * so the guard does not trigger on the next startup (e.g. after /ui reload).
     */
    public static void resetStartupFlag() {
        startupPerformed = false;
    }

    // ==========================================================================
    // 🎯 PHASE 4: FINISH
    // ==========================================================================

    private void finishStartup() {
        // Delayed structure rebuild (after chunks load)
        StructureChunkListener.scheduleDelayedRebuild(plugin);

        // ASCII banner
        printBanner();

        ConsoleLogger.success("[PLUGIN] Plugin enabled!");
    }

    private void printBanner() {
        ConsoleLogger.info("");
        ConsoleLogger.info("==================================================");
        ConsoleLogger.info("  UltimateImprovments v" + plugin.getDescription().getVersion());
        ConsoleLogger.info("  Server: " + plugin.getServer().getName() + " " + plugin.getServer().getVersion());
        ConsoleLogger.info("  Authors: " + String.join(", ", plugin.getDescription().getAuthors()));
        ConsoleLogger.raw("<white>  Status: </white><green>ENABLED</green>");
        ConsoleLogger.info("==================================================");
        ConsoleLogger.info("");
    }
}
