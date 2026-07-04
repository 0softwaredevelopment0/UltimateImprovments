package com.mcplugin.infrastructure.core;

import com.mcplugin.infrastructure.config.ConfigGuideManager;
import com.mcplugin.infrastructure.config.ConfigIntegrityValidator;
import com.mcplugin.infrastructure.config.MessagesManager;
import com.mcplugin.infrastructure.maintenance.MaintenanceManager;
import com.mcplugin.infrastructure.modules.*;
import com.mcplugin.infrastructure.report.ReportManager;
import com.mcplugin.infrastructure.opwhitelist.OpWhitelistManager;
import com.mcplugin.infrastructure.listeners.OpCommandBlocker;
import com.mcplugin.infrastructure.listeners.WhitelistCommandBlocker;
import com.mcplugin.infrastructure.util.AuthCommandLogFilter;
import com.mcplugin.infrastructure.util.ConsoleLogger;
import com.mcplugin.infrastructure.util.FileLogger;
import com.mcplugin.infrastructure.util.PlaceholderResolver;
import com.mcplugin.mechanics.security.check.CheckListener;
import com.mcplugin.mechanics.security.check.CheckManager;
import com.mcplugin.infrastructure.structure.StructureChunkListener;
import com.mcplugin.infrastructure.structure.StructureChunkTracker;

/**
 * PluginStartup — матрёшка инициализации MC-Plugin.
 * <p>
 * Вызывается из {@link Main#onEnable()}.
 * Разбивает запуск на логические фазы: инфраструктура → модули → пост-модули → финиш.
 * Каждая фаза — отдельный метод, что даёт читаемую древовидную структуру.
 */
public class PluginStartup {

    private final Main plugin;

    public PluginStartup(Main plugin) {
        this.plugin = plugin;
    }

    // ==========================================================================
    // 🚀 STARTUP — корень матрёшки
    // ==========================================================================

    public void startupPlugin() {
        // ConsoleLogger init FIRST — before any log calls
        ConsoleLogger.init();

        // Auth command log filter — hide passwords from server console
        AuthCommandLogFilter.register();

        ConsoleLogger.info("");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("  MC-Plugin — Starting up...");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("");

        initInfrastructure();
        initModuleSystem();
        initPostModuleSystems();
        finishStartup();
    }

    // ==========================================================================
    // 🏗 ФАЗА 1: ИНФРАСТРУКТУРА
    // ==========================================================================

    private void initInfrastructure() {
        FileLogger.ensureDirectory(plugin.getDataFolder(), "DataFolder");
        loadConfigFile();

        ConfigIntegrityValidator.validate(plugin);

        MessagesManager.init(plugin);
        ConfigGuideManager.init(plugin);

        PlaceholderResolver.init();

        Keys.init(plugin);

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

        try {
            plugin.reloadConfig();
        } catch (Exception e) {
            ConsoleLogger.warn("[Config] Failed to load config.yml: " + e.getMessage());
            ConsoleLogger.warn("[Config] Deleting broken config.yml and recreating from JAR...");
            if (configFile.exists()) configFile.delete();
            plugin.saveDefaultConfig();
            plugin.reloadConfig();
            ConsoleLogger.info("[Config] Recreated config.yml from JAR resources.");
        }
    }

    // ==========================================================================
    // 🧩 ФАЗА 2: МОДУЛИ
    // ==========================================================================

    private void initModuleSystem() {
        ModuleManager.init(plugin);
        var mm = ModuleManager.getInstance();

        // ── System ──
        mm.register(new VersionCheckModule());
        mm.register(new DatabaseModule());
        mm.register(new DatapackModule());

        // ── Core + Power ──
        mm.register(new CoreModule());
        mm.register(new PowerModule());

        // ── Energy ──
        mm.register(new CableModule());
        mm.register(new GeneratorBasicModule());
        mm.register(new ReactorModule());
        mm.register(new FurnaceModule());
        mm.register(new AssemblerModule());
        mm.register(new WorkbenchModule());
        mm.register(new BatteryModule());
        mm.register(new BatteryMultiModule());
        mm.register(new LightModule());

        // ── Mechanics ──
        mm.register(new RadiationModule());
        mm.register(new LightningModule());

        // ── Crafting + Auth ──
        mm.register(new CraftingModule());
        mm.register(new AuthModule());

        // ── Features ──
        mm.register(new AttributesModule());
        mm.register(new BeaconModule());
        mm.register(new BlockDmgModule());
        mm.register(new BoostedCobwebModule());
        mm.register(new DragonEggModule());
        mm.register(new EntityLocatorModule());
        mm.register(new ItemKillModule());
        mm.register(new MagnetModule());
        mm.register(new ModeProtectModule());
        mm.register(new TerracotaSpeedModule());
        mm.register(new WaypointModule());
        mm.register(new IntegrityModule());
        mm.register(new AntimatterModule());
        mm.register(new UnbreakableBreakerModule());
        mm.register(new DeathBellModule());
        mm.register(new EnderChestModule());
        mm.register(new GlassBreakModule());
        mm.register(new ShieldSlownessModule());
        mm.register(new CreativeItemValidatorModule());
        mm.register(new ContainerTriggerModule());
        mm.register(new VanishModule());
        mm.register(new NotesModule());
        mm.register(new MinecartSpeedModule());
        mm.register(new WirelessRedstoneModule());

        // ── Protection ──
        mm.register(new RedstoneGuardModule());
        mm.register(new PacketGuardModule());
        mm.register(new ProxyServerModule());

        // ── Utility ──
        mm.register(new ChatFilterModule());
        mm.register(new ChatModule());
        mm.register(new VoidProtectionModule());
        mm.register(new BotProtectionModule());

        // ── Display ──
        mm.register(new MOTDModule());
        mm.register(new TabModule());
        mm.register(new ScoreboardModule());
        mm.register(new BelowNameModule());
        mm.register(new BossBarModule());

        // ── Maintenance ──
        MaintenanceManager.init();

        // ── Background ──
        mm.register(new TasksModule());
        mm.register(new AutoSaveModule());
        mm.register(new UpdateModule());
        mm.register(new LeashModule());
        mm.register(new ElytraBoostModule());

        // ── Meteor ──
        mm.register(new com.mcplugin.infrastructure.modules.meteor.MeteorModule());

        // ── Economy ──
        mm.register(new EconomyModule());

        // ── Particle Accelerator ──
        mm.register(new ParticleModule());

        // ── Security ──
        mm.register(new PunishModule());
        mm.register(new AntiCheatModule());
        mm.register(new StructureIntegrityModule());

        // Init all modules (each in try-catch, failures don't break others)
        mm.initAll();

        ConsoleLogger.info("[Init] Module system ready.");
    }

    // ==========================================================================
    // 🔗 ФАЗА 3: ПОСТ-МОДУЛЬНЫЕ СИСТЕМЫ
    // ==========================================================================

    private void initPostModuleSystems() {
        // Structure chunk listener & tracker (after DB init from modules)
        plugin.getServer().getPluginManager().registerEvents(new StructureChunkListener(), plugin);
        StructureChunkListener.scanAll();
        StructureChunkTracker.load();
        StructureChunkTracker.loadTrackedChunks();

        // OP whitelist
        OpWhitelistManager.init(plugin);

        // Custom whitelist
        com.mcplugin.infrastructure.whitelist.WhitelistManager.init(plugin);

        // Block vanilla commands
        plugin.getServer().getPluginManager().registerEvents(new WhitelistCommandBlocker(), plugin);
        plugin.getServer().getPluginManager().registerEvents(new OpCommandBlocker(), plugin);

        // Periodic access list check
        com.mcplugin.infrastructure.server.AccessListCheckTask.start(plugin);

        // Blacklist
        com.mcplugin.infrastructure.blacklist.BlacklistManager.init(plugin);

        // Reports
        ReportManager.init();

        // Anti-cheat check system
        CheckManager.init();
        plugin.getServer().getPluginManager().registerEvents(new CheckListener(), plugin);

        // Commands
        CommandRegistrar.getInstance().registerAll(plugin);

        ConsoleLogger.info("[Init] Post-module systems ready.");
    }

    // ==========================================================================
    // 🎯 ФАЗА 4: ФИНИШ
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
        ConsoleLogger.raw("<gradient:#00AAFF:#FF55FF> __  __  _____      _____  _     _    _  _____ _____ _   _ </gradient>");
        ConsoleLogger.raw("<gradient:#00AAFF:#FF55FF>|  \\/  |/ ____|    |  __ \\| |   | |  | |/ ____|_   _| \\ | |</gradient>");
        ConsoleLogger.raw("<gradient:#00AAFF:#FF55FF>| \\  / | |   ______| |__) | |   | |  | | |  __  | | |  \\| |</gradient>");
        ConsoleLogger.raw("<gradient:#00AAFF:#FF55FF>| |\\/| | |  |______|  ___/| |   | |  | | | |_ | | | | . ` |</gradient>");
        ConsoleLogger.raw("<gradient:#00AAFF:#FF55FF>| |  | | |____     | |    | |___| |__| | |__| |_| |_| |\\  |</gradient>");
        ConsoleLogger.raw("<gradient:#00AAFF:#FF55FF>|_|  |_|\\_____|    |_|    |______\\____/ \\_____|_____|_| \\_|</gradient>");
        ConsoleLogger.info("");
        ConsoleLogger.raw("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>");
        ConsoleLogger.raw("<gray>  Version: <white>" + plugin.getDescription().getVersion() + "</white>  |  Server: <white>" + plugin.getServer().getName() + " " + plugin.getServer().getVersion() + "</white></gray>");
        ConsoleLogger.raw("<gray>  Authors: <white>" + String.join(", ", plugin.getDescription().getAuthors()) + "</white></gray>");
        ConsoleLogger.raw("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>");
        ConsoleLogger.info("");
    }
}
