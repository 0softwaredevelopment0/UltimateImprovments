package com.mcplugin;

import com.mcplugin.config.ConfigIntegrityValidator;
import com.mcplugin.main.CommandRegistrar;
import com.mcplugin.module.*;

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

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        saveDefaultConfig();
        reloadConfig();

        // =========================
        // ПРОВЕРКА ЦЕЛОСТНОСТИ КОНФИГА
        // Если чего-то не хватает → compromised-config.yml + свежий config.yml
        // =========================
        ConfigIntegrityValidator.validate(this);

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

        // РЕГИСТРИРУЕМ МОДУЛИ
        mm.register(new VersionCheckModule());
        mm.register(new DatabaseModule());
        mm.register(new DatapackModule());
        mm.register(new CoreModule());
        mm.register(new MechanicsModule());
        mm.register(new CraftingModule());
        mm.register(new AuthModule());
        mm.register(new ProtectionModule());
        mm.register(new ListenersModule());
        mm.register(new TasksModule());
        mm.register(new AutoSaveModule());
        mm.register(new UpdateModule());
        mm.register(new LeashModule());

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