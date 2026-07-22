package com.ultimateimprovements.core;

import com.ultimateimprovements.config.ConfigGuideManager;
import com.ultimateimprovements.config.ConfigIntegrityValidator;
import com.ultimateimprovements.config.MessagesManager;
import com.ultimateimprovements.maintenance.MaintenanceManager;
import com.ultimateimprovements.mechanics.features.omniscanner.OmniscannerModule;
import com.ultimateimprovements.module.*;
import com.ultimateimprovements.report.ReportManager;
import com.ultimateimprovements.whitelist.OpWhitelistManager;
import com.ultimateimprovements.listener.LuckPermsCommandBlocker;
import com.ultimateimprovements.listener.OpCommandBlocker;
import com.ultimateimprovements.listener.WhitelistCommandBlocker;
import com.ultimateimprovements.util.AuthCommandLogFilter;
import com.ultimateimprovements.util.ConsoleLogger;
import com.ultimateimprovements.util.FileLogger;
import com.ultimateimprovements.util.PlaceholderResolver;
import com.ultimateimprovements.mechanics.security.check.CheckListener;
import com.ultimateimprovements.mechanics.security.check.CheckManager;
import com.ultimateimprovements.structure.StructureChunkListener;
import com.ultimateimprovements.structure.StructureChunkTracker;

/**
 * PluginStartup — матрёшка инициализации UltimateImprovements.
 * <p>
 * Вызывается из {@link Main#onEnable()}.
 * Разбивает запуск на логические фазы: инфраструктура → модули → пост-модули → финиш.
 * Каждая фаза — отдельный метод, что даёт читаемую древовидную структуру.
 */
public class PluginStartup {

    private final Main plugin;
    private static boolean startupPerformed = false;

    public PluginStartup(Main plugin) {
        this.plugin = plugin;
    }

    // ==========================================================================
    // 🚀 STARTUP — корень матрёшки
    // ==========================================================================

    public void startupPlugin() {
        // Guard: предотвращает двойной startup (напр. от PlugMan enable без disable)
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

        ConsoleLogger.info("");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("  UltimateImprovements — Starting up...");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("");

        // Проверка Java-версии: предупреждаем, но НЕ отключаем плагин.
        // Модули с несовместимыми классами сами отвалятся (без стектрейсов — см. PluginModule).
        checkJavaVersion();

        initInfrastructure();
        initModuleSystem();
        initPostModuleSystems();
        finishStartup();
    }

    /**
     * Проверяет Java-версию и печатает предупреждение если классы плагина
     * несовместимы с текущей Java. НЕ отключает плагин — только предупреждает.
     * <p>
     * Вместо хардкода номера версии — реально пробует загрузить один тестовый класс.
     * Если Paper не может сконвертировать class (IllegalArgumentException),
     * печатает одно понятное сообщение вместо 60+ 'Fatal error' от Paper.
     */
    private void checkJavaVersion() {
        try {
            this.plugin.getClass().getClassLoader().loadClass(
                    "com.ultimateimprovements.core.DatapackInstaller");
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
            // Класс обязан быть — но если нет, просто продолжаем
        }
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

        // PlaceholderAPI hook — регистрируем UIPlaceholderExpansion
        // ТОЛЬКО если PAPI установлен; иначе только внутренний резолвер работает.
        if (PlaceholderResolver.isPapiAvailable()) {
            try {
                com.ultimateimprovements.hook.UIPlaceholderExpansion expansion =
                        new com.ultimateimprovements.hook.UIPlaceholderExpansion();
                if (expansion.register()) {
                    ConsoleLogger.info("[PlaceholderAPI] UltimateImprovements expansion registered (" +
                            expansion.getIdentifier() + " — " +
                            PlaceholderResolver.getBuiltinNames().size() + " placeholders)");
                } else {
                    ConsoleLogger.warn("[PlaceholderAPI] Could not register UltimateImprovements expansion");
                }
            } catch (Throwable t) {
                ConsoleLogger.warn("[PlaceholderAPI] Registration failed: " + t.getMessage());
            }
        } else {
            ConsoleLogger.info("[PlaceholderAPI] PlaceholderAPI не обнаружен — плейсхолдеры работают только внутри плагина");
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

        // Пытаемся авто-обнаружить модули через сканирование JAR
        ConsoleLogger.info("[Init] Scanning for modules...");
        ModuleScanner.autoRegister(mm, plugin, "com/ultimateimprovements/module");

        // Если авто-сканирование не нашло модулей — используем ручную регистрацию
        if (mm.getModules().isEmpty()) {
            ConsoleLogger.info("[Init] Auto-scan found no modules, using manual registration.");
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
        }

        // Init all modules (each in try-catch, failures don't break others)
        mm.initAll();

        ConsoleLogger.info("[Init] Module system ready.");
    }

    private void registerSystemModules(ModuleManager mm) {
        mm.register(new VersionCheckModule());
        mm.register(new DatabaseModule());
        mm.register(new DatapackModule());
        mm.register(new CoreModule());
        mm.register(new PowerModule());
    }

    private void registerEnergyModules(ModuleManager mm) {
        mm.register(new CableModule());
        mm.register(new GeneratorBasicModule());
        mm.register(new ReactorModule());
        mm.register(new FurnaceModule());
        mm.register(new AssemblerModule());
        mm.register(new WorkbenchModule());
        mm.register(new BatteryModule());
        mm.register(new BatteryMultiModule());
        mm.register(new LightModule());
    }

    private void registerMechanicsModules(ModuleManager mm) {
        mm.register(new RadiationModule());
        mm.register(new LightningModule());
        mm.register(new CraftingModule());
        mm.register(new AuthModule());
    }

    private void registerFeatureModules(ModuleManager mm) {
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
        mm.register(new com.ultimateimprovements.module.meteor.MeteorModule());
        mm.register(new EconomyModule());
        mm.register(new AOEEnchantmentModule());
    }

    private void registerProtectionModules(ModuleManager mm) {
        mm.register(new RedstoneGuardModule());
        mm.register(new PacketGuardModule());
        mm.register(new ProxyServerModule());
        mm.register(new com.ultimateimprovements.mechanics.protection.ProtectionModule());
    }

    private void registerUtilityModules(ModuleManager mm) {
        mm.register(new ChatFilterModule());
        mm.register(new ChatModule());
        mm.register(new VoidProtectionModule());
        mm.register(new BotProtectionModule());
    }

    private void registerDisplayModules(ModuleManager mm) {
        mm.register(new MOTDModule());
        mm.register(new TabModule());
        mm.register(new ScoreboardModule());
        mm.register(new BelowNameModule());
        mm.register(new BossBarModule());
    }

    private void registerBackgroundModules(ModuleManager mm) {
        mm.register(new TasksModule());
        mm.register(new AutoSaveModule());
        mm.register(new UpdateModule());
        mm.register(new LeashModule());
        mm.register(new ElytraBoostModule());
    }

    private void registerAdminModules(ModuleManager mm) {
        mm.register(new ParticleModule());
        mm.register(new OmniscannerModule());
    }

    private void registerSecurityModules(ModuleManager mm) {
        mm.register(new PunishModule());
        mm.register(new AntiCheatModule());
        mm.register(new StructureIntegrityModule());
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
        com.ultimateimprovements.whitelist.WhitelistManager.init(plugin);

        // Block vanilla commands
        plugin.getServer().getPluginManager().registerEvents(new WhitelistCommandBlocker(), plugin);
        plugin.getServer().getPluginManager().registerEvents(new OpCommandBlocker(), plugin);

        // Warn on LuckPerms wildcard (*) permission grants
        plugin.getServer().getPluginManager().registerEvents(new LuckPermsCommandBlocker(), plugin);

        // Periodic access list check
        com.ultimateimprovements.server.AccessListCheckTask.start(plugin);

        // Blacklist
        com.ultimateimprovements.whitelist.BlacklistManager.init(plugin);

        // Reports
        ReportManager.init();

        // Anti-cheat check system
        CheckManager.init();
        plugin.getServer().getPluginManager().registerEvents(new CheckListener(), plugin);

        // Commands — регистрация Bukkit-команд через CommandMap
        CommandRegistrar.getInstance().registerAll(plugin);

        // SubCommand registry — инициализация диспетчера /mp
        com.ultimateimprovements.command.PluginReloadCommand.init();

        ConsoleLogger.info("[Init] Post-module systems ready.");
    }

    /**
     * Сбрасывает флаг startupPerformed. Вызывается из PluginShutdown
     * чтобы при следующем startup (например после /mp reload) guard не сработал.
     */
    public static void resetStartupFlag() {
        startupPerformed = false;
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
