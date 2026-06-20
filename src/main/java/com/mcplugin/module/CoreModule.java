package com.mcplugin.module;

import com.mcplugin.Main;
import com.mcplugin.commands.PowerManager;
import com.mcplugin.features.FeaturesManager;
import com.mcplugin.features.minecartspeed.MinecartSpeedManager;
import com.mcplugin.main.CommandRegistrar;
import com.mcplugin.main.TaskManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль ядра — базовые системы: команды, задачи, питание, фичи.
 * Essential — без них плагин бесполезен.
 */
public class CoreModule extends PluginModule {

    public CoreModule() {
        super("Core", true);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;

        // =========================
        // TASK MANAGER & COMMANDS
        // =========================
        TaskManager.init(main);
        CommandRegistrar.init(main);

        // =========================
        // POWER MANAGER
        // =========================
        PowerManager.init();

        // =========================
        // FEATURES
        // =========================
        FeaturesManager.init(main);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        MinecartSpeedManager.shutdown();
    }

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        PowerManager.reloadConfig();
        FeaturesManager.reloadConfig();
    }
}
