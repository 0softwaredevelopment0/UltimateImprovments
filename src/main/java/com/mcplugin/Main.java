package com.mcplugin;

import com.mcplugin.main.CommandRegistrar;
import com.mcplugin.module.*;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main — точка входа MC-Plugin.
 * <p>
 * Инициализация разбита на независимые модули ({@link PluginModule}).
 * Каждый модуль обрабатывается в try-catch: если один модуль падает,
 * остальные продолжают работу, а в консоль пишется стектрейс.
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
        // MODULE MANAGER
        // =========================
        ModuleManager.init(this);
        var mm = ModuleManager.getInstance();

        // РЕГИСТРИРУЕМ МОДУЛИ
        mm.register(new VersionCheckModule());
        mm.register(new DatabaseModule());
        mm.register(new CoreSystemsModule());
        mm.register(new ListenersModule());
        mm.register(new TasksModule());
        mm.register(new AutoSaveModule());
        mm.register(new UpdateModule());

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