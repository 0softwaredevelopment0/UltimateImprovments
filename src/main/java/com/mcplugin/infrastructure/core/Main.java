package com.mcplugin.infrastructure.core;

import com.mcplugin.infrastructure.config.ConfigIntegrityValidator;
import com.mcplugin.infrastructure.config.MessagesManager;
import com.mcplugin.infrastructure.core.CommandRegistrar;
import com.mcplugin.infrastructure.modules.*;
import com.mcplugin.infrastructure.structure.StructureChunkListener;
import com.mcplugin.infrastructure.util.FileLogger;

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
        reloadConfig();

        // =========================
        // ПРОВЕРКА ЦЕЛОСТНОСТИ КОНФИГА
        // Если чего-то не хватает → compromised-config.yml + свежий config.yml
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

        // Protection
        mm.register(new RedstoneGuardModule());
        mm.register(new PacketGuardModule());

        // Utility modules
        mm.register(new ChatFilterModule());
        mm.register(new VoidProtectionModule());

        // Background tasks & updates
        mm.register(new TasksModule());
        mm.register(new AutoSaveModule());
        mm.register(new UpdateModule());
        mm.register(new LeashModule());
        mm.register(new ElytraBoostModule());

        // =========================
        // REGISTER STRUCTURE CHUNK LISTENER
        // Сканирует все загруженные чанки на Marker'ы структур ДО инициализации модулей,
        // чтобы rebuildFromMarkers() в BatteryManager/LightManager увидел кэш.
        // =========================
        getServer().getPluginManager().registerEvents(new StructureChunkListener(), this);
        StructureChunkListener.scanAll();

        // =========================
        // INIT ALL MODULES
        // Каждый модуль инициализируется в try-catch.
        // Если модуль упал — он отключается, но плагин продолжает работу.
        // =========================
        mm.initAll();

        // =========================
        // REGISTER COMMANDS
        // =========================
        CommandRegistrar.getInstance().registerAll(this);

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

        getLogger().info("[PLUGIN] Disabled");
    }
}