package com.ultimateimprovments.core;

import com.ultimateimprovments.config.ConfigGuideManager;
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
 * PluginStartup — матрёшка инициализации UltimateImprovments.
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

        // Auth Dialog handler — регистрируется как можно раньше, чтобы не пропустить события
        com.ultimateimprovments.mechanics.security.auth.AuthDialogHandler.register();

        // Sudo Dialog handler — регистрируется как можно раньше, чтобы не пропустить события
        com.ultimateimprovments.mechanics.security.sudo.SudoDialogHandler.register();

        // ChgDim Dialog handler — регистрируется как можно раньше, чтобы не пропустить события
        com.ultimateimprovments.command.ChgDimDialogHandler.register();

        // AskPos Dialog handler — диалоги запроса координат (/ui askpos)
        com.ultimateimprovments.command.AskPosDialogHandler.register();

        // Code Panel Dialog handler — регистрируется как можно раньше, чтобы не пропустить события
        com.ultimateimprovments.mechanics.security.codepanel.CodePanelDialogHandler.register();

        ConsoleLogger.info("");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("  UltimateImprovments — Starting up...");
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

        // Регистрируем модули вручную — это полный и упорядоченный список.
        // Авто-сканирование (ModuleScanner) не используется: раньше его путь был
        // написан со старой орфографией и никогда не находил модули; а если бы
        // работал — пропустил бы модули вне пакета com/ultimateimprovments/module
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
        // Assembler, Workbench
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
    // 🔗 ФАЗА 3: ПОСТ-МОДУЛЬНЫЕ СИСТЕМЫ
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

        // Commands — регистрация Bukkit-команд через CommandMap
        CommandRegistrar.getInstance().registerAll(plugin);

        // SubCommand registry — инициализация диспетчера /ultimateimprovments
        com.ultimateimprovments.command.PluginReloadCommand.init();

        ConsoleLogger.info("[Init] Post-module systems ready.");
    }

    /**
     * Сбрасывает флаг startupPerformed. Вызывается из PluginShutdown
     * чтобы при следующем startup (например после /ui reload) guard не сработал.
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
        ConsoleLogger.info("==================================================");
        ConsoleLogger.info("  UltimateImprovments v" + plugin.getDescription().getVersion());
        ConsoleLogger.info("  Server: " + plugin.getServer().getName() + " " + plugin.getServer().getVersion());
        ConsoleLogger.info("  Authors: " + String.join(", ", plugin.getDescription().getAuthors()));
        ConsoleLogger.raw("<white>  Status: </white><green>ENABLED</green>");
        ConsoleLogger.info("==================================================");
        ConsoleLogger.info("");
    }
}
