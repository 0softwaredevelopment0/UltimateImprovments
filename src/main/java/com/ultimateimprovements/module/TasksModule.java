package com.ultimateimprovements.module;

import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.core.TaskManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль фоновых задач (BatteryDrainTask, CableLossTask, ReactorTask и т.д.).
 * Неessential — если задачи не запустятся, основные механики не работают,
 * но плагин не упадёт полностью.
 */
public class TasksModule extends PluginModule {

    public TasksModule() {
        super("Tasks", "infrastructure/core", true);
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
