package com.mcplugin.infrastructure.modules;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.core.TaskManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль фоновых задач (BatteryDrainTask, CableLossTask, ReactorTask и т.д.).
 * Неessential — если задачи не запустятся, основные механики не работают,
 * но плагин не упадёт полностью.
 */
public class TasksModule extends PluginModule {

    public TasksModule() {
        super("Tasks", true);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        TaskManager.getInstance().startAll((Main) plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        TaskManager.getInstance().stopAll();
    }
}
