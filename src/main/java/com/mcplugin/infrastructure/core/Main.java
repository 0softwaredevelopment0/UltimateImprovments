package com.mcplugin.infrastructure.core;

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
        new PluginStartup(this).startupPlugin();
    }

    @Override
    public void onDisable() {
        new PluginShutdown(this).shutdownPlugin();
    }
}