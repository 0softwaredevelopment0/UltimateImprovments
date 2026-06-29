package com.mcplugin.infrastructure.core;

import com.mcplugin.infrastructure.config.ConfigIntegrityValidator;
import com.mcplugin.infrastructure.config.MessagesManager;
import com.mcplugin.infrastructure.core.CommandRegistrar;
import com.mcplugin.infrastructure.maintenance.MaintenanceManager;
import com.mcplugin.infrastructure.modules.*;
import com.mcplugin.infrastructure.tab.TabManager;
import com.mcplugin.infrastructure.opwhitelist.OpWhitelistManager;
import com.mcplugin.infrastructure.report.ReportManager;
import com.mcplugin.infrastructure.listeners.OpCommandBlocker;
import com.mcplugin.infrastructure.listeners.WhitelistCommandBlocker;
import com.mcplugin.mechanics.security.check.CheckListener;
import com.mcplugin.mechanics.security.check.CheckManager;
import com.mcplugin.infrastructure.structure.StructureChunkListener;
import com.mcplugin.infrastructure.structure.StructureChunkTracker;
import com.mcplugin.infrastructure.util.FileLogger;
import com.mcplugin.infrastructure.util.PlaceholderResolver;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main — точка входа MC-Plugin.
 * <p>
 * Инициализация разбита на независимые модули ({@link PluginModule}).
 * Каждый модуль обрабатывается в try-catch: если один модуль падает,
 * остальные продолжают работу, а в консоль пишется стектрейс.
 * <p>
 * При старте проверяется целостность config.yml — если ключей не хватает,
 * конфиг переименовывается в compromised-config.yml и создаётся свежий.
 */
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

        // =========================
        // DATA FOLDER & CONFIG
        // =========================
        FileLogger.ensureDirectory(getDataFolder(), "DataFolder", getLogger());

        // saveDefaultConfig() saves config.yml from JAR resources if it doesn't exist
        java.io.File configFile = new java.io.File(getDataFolder(), "config.yml");
        boolean configExisted = configFile.exists();
        saveDefaultConfig();
        if (!configExisted && configFile.exists()) {
            getLogger().info("[Config] Created new file: config.yml");
        } else if (configExisted) {
            getLogger().info("[Config] File exists: config.yml");
        }

        // Попытка загрузить конфиг. Если файл повреждён (например, SnakeYAML 2.6
        // не переваривает старые escape-последовательности в двойных кавычках) —
        // удаляем битый файл и создаём свежий из JAR.
        try {
            reloadConfig();
        } catch (Exception e) {
            getLogger().warning("[Config] Failed to load config.yml: " + e.getMessage());
            getLogger().warning("[Config] Deleting broken config.yml and recreating from JAR...");

            // Удаляем битый файл
            if (configFile.exists()) {
                configFile.delete();
            }

            // Пересоздаём из JAR
            saveDefaultConfig();
            reloadConfig();
            getLogger().info("[Config] Recreated config.yml from JAR resources.");
        }

        // =========================
        // ПРОВЕРКА ЦЕЛОСТНОСТИ КОНФИГА
        // ConfigRepairManager найдет недостающие ключи и ДОБАВИТ их в конец файла,
        // ничего не перезаписывая. Существующие значения сохраняются.
        // =========================
        ConfigIntegrityValidator.validate(this);

        // =========================
        // MESSAGES.YML — загружаем ДО модулей, чтобы они могли использовать MessagesManager.
        // init() сам вызывает проверку целостности (через ConfigIntegrityValidator.validateMessages).
        // Если файл повреждён — переименовывается в compromised-messages.yml
        // и создаётся свежий из ресурсов.
        // =========================
        MessagesManager.init(this);

        // =========================
        // 📖 PLUGIN GUIDE — синхронизация plugin-guide.txt
        // =========================
        com.mcplugin.infrastructure.config.ConfigGuideManager.init(this);

        // =========================
        // PLACEHOLDER RESOLVER — проверка PAPI
        // =========================
        PlaceholderResolver.init();

        // =========================
        // PDC KEYS — MUST init BEFORE any module that uses Keys.*
        // Keys.init() was previously in DatabaseModule, but if the DB module
        // failed, all Keys.* would be null, breaking AuthGUI and other systems.
        // =========================
        Keys.init(this);

        // =========================
        // MODULE MANAGER
        // =========================
        ModuleManager.init(this);
        var mm = ModuleManager.getInstance();

        // =========================
        // REGISTER ALL MODULES
        // =========================

        // System modules
        mm.register(new VersionCheckModule());
        mm.register(new DatabaseModule());
        mm.register(new DatapackModule());

        // Core + Power
        mm.register(new CoreModule());
        mm.register(new PowerModule());

        // Energy systems (per-system modules)
        mm.register(new CableModule());
        mm.register(new GeneratorBasicModule());
        mm.register(new ReactorModule());
        mm.register(new FurnaceModule());
        mm.register(new AssemblerModule());
        mm.register(new WorkbenchModule());
        mm.register(new BatteryModule());
        mm.register(new BatteryMultiModule());
        mm.register(new LightModule());

        // Mechanics
        mm.register(new RadiationModule());
        mm.register(new LightningModule());

        // Crafting + Auth
        mm.register(new CraftingModule());
        mm.register(new AuthModule());

        // Features — individual modules for fine-grained control
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

        // Wireless Redstone
        mm.register(new WirelessRedstoneModule());

        // Protection
        mm.register(new RedstoneGuardModule());
        mm.register(new PacketGuardModule());

        // Utility modules
        mm.register(new ChatFilterModule());
        mm.register(new ChatModule());
        mm.register(new VoidProtectionModule());

        // Security
        mm.register(new BotProtectionModule());

        // MOTD
        mm.register(new MOTDModule());

        // Tab & Scoreboard
        mm.register(new TabModule());
        mm.register(new ScoreboardModule());

        // BelowName — текст под ником в мире
        mm.register(new BelowNameModule());

        // BossBar — кастомный боссбар с прогрессией
        mm.register(new BossBarModule());

        // Maintenance
        MaintenanceManager.init();

        // Background tasks & updates
        mm.register(new TasksModule());
        mm.register(new AutoSaveModule());
        mm.register(new UpdateModule());
        mm.register(new LeashModule());
        mm.register(new ElytraBoostModule());

        // PunishModule — система наказаний (бан/мут/кик/варн + вайтлист/блэклист)
        mm.register(new PunishModule());

        // =========================
        // INIT ALL MODULES
        // Каждый модуль инициализируется в try-catch.
        // Если модуль упал — он отключается, но плагин продолжает работу.
        // =========================
        mm.initAll();

        // =========================
        // REGISTER STRUCTURE CHUNK LISTENER + TRACKER
        // ПОСЛЕ mm.initAll() — таблицы БД уже созданы.
        // =========================
        getServer().getPluginManager().registerEvents(new StructureChunkListener(), this);
        StructureChunkListener.scanAll();
        StructureChunkTracker.load();
        StructureChunkTracker.loadTrackedChunks();

        // =========================
        // OP WHITELIST — белый список операторов (ПОСЛЕ initAll, чтобы таблицы БД уже существовали)
        // =========================
        OpWhitelistManager.init(this);

        // =========================
        // 📋 CUSTOM WHITELIST — кастомный вайтлист MC-Plugin (не OP вайтлист)
        // =========================
        com.mcplugin.infrastructure.whitelist.WhitelistManager.init(this);

        // 🚫 Блокировка ванильной команды /whitelist — плагин использует свою (/mp whitelist)
        getServer().getPluginManager().registerEvents(new WhitelistCommandBlocker(), this);

        // 🚫 Блокировка ванильных команд /op и /deop — плагин использует свой (/mp chgop)
        getServer().getPluginManager().registerEvents(new OpCommandBlocker(), this);

        // 🔄 Периодическая проверка whitelist/blacklist/opwhitelist онлайна
        com.mcplugin.infrastructure.server.AccessListCheckTask.start(this);

        // =========================
        // 📋 BLACKLIST — чёрный список
        // =========================
        com.mcplugin.infrastructure.blacklist.BlacklistManager.init(this);

        // =========================
        // REPORTS — система репортов (ПОСЛЕ initAll, чтобы таблицы БД уже существовали)
        // =========================
        ReportManager.init();

        // =========================
        // ✅ CHECK MANAGER — система проверки на читы
        // =========================
        CheckManager.init();
        getServer().getPluginManager().registerEvents(new CheckListener(), this);

        // =========================
        // REGISTER COMMANDS
        // =========================
        CommandRegistrar.getInstance().registerAll(this);

        // =========================
        // ОТЛОЖЕННАЯ ПЕРЕСТРОЙКА СТРУКТУР ИЗ MARKER'ОВ
        // При старте сервера загружены только спавн-чанки, поэтому scanAll()
        // до initAll() находит почти ничего. Через 5 и 30 секунд после старта
        // перестраиваем все структуры из загрузившихся чанков.
        // =========================
        StructureChunkListener.scheduleDelayedRebuild(this);

        getLogger().info("[PLUGIN] Plugin enabled!");
    }

    @Override
    public void onDisable() {

        // =========================
        // SHUTDOWN ALL MODULES (в обратном порядке)
        // =========================
        var mm = ModuleManager.getInstance();
        if (mm != null) {
            mm.shutdownAll();
        }

        // Сохраняем трекер чанков структур
        StructureChunkTracker.save();

        // Сохраняем OP whitelist
        OpWhitelistManager.shutdown();

        // Останавливаем периодическую проверку access control
        com.mcplugin.infrastructure.server.AccessListCheckTask.stop();

        // Сбрасываем флаг регистрации TabManager listener'ов
        TabManager.resetListenerState();

        // Очищаем CheckManager — разморозить всех проверяемых
        CheckManager.shutdown();

        getLogger().info("[PLUGIN] Disabled");
    }
}