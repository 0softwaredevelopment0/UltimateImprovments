package com.ultimateimprovments.core;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.mechanics.security.codepanel.CodePanelCleanupTask;
import com.ultimateimprovments.mechanics.environment.radiation.RadiationTask;
import com.ultimateimprovments.mechanics.environment.sunburn.SunburnTask;
import com.ultimateimprovments.listener.FishingListener;
import com.ultimateimprovments.server.EmergencyEntitiesKill;
import com.ultimateimprovments.server.RedstoneGuardTask;
import com.ultimateimprovments.server.ServerOverloadWarning;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class TaskManager {

    private static TaskManager instance;

    private BukkitTask overloadTask;
    private BukkitTask redstoneGuardTask;
    private BukkitTask overloadWarningTask;
    private BukkitTask radiationTask;
    private BukkitTask sunburnTask;
    private BukkitTask fishingTask;
    private BukkitTask codePanelCleanupTask;

    private boolean tasksStarted = false;

    public static void init(Main plugin) {
        instance = new TaskManager();
    }

    public static TaskManager getInstance() {
        return instance;
    }

    public void startAll(Main plugin) {
        if (tasksStarted) return;
        tasksStarted = true;

        overloadTask = new EmergencyEntitiesKill().runTaskTimer(plugin, 20L, 20L);
        redstoneGuardTask = new RedstoneGuardTask().runTaskTimer(plugin, 1L, 1L);
        overloadWarningTask = new ServerOverloadWarning().runTaskTimer(plugin, 20L, 20L);
        radiationTask = new RadiationTask().runTaskTimer(plugin, 20L, 1L);
        sunburnTask = new SunburnTask().runTaskTimer(plugin, 0L, 1L);
        fishingTask = FishingListener.getInstance().runTaskTimer(plugin, 1L, 1L);
        codePanelCleanupTask = new CodePanelCleanupTask().runTaskTimer(plugin, 200L, 400L);

        ConsoleLogger.info("[TASKS] Started.");
    }

    public void stopAll() {
        cancelAll();
        tasksStarted = false;
    }

    private void cancelAll() {
        if (overloadTask != null) overloadTask.cancel();
        if (redstoneGuardTask != null) redstoneGuardTask.cancel();
        if (overloadWarningTask != null) overloadWarningTask.cancel();
        if (radiationTask != null) radiationTask.cancel();
        if (sunburnTask != null) sunburnTask.cancel();
        if (fishingTask != null) {
            fishingTask.cancel();
            resetBukkitRunnableTask(FishingListener.getInstance());
        }
        if (codePanelCleanupTask != null) codePanelCleanupTask.cancel();
    }

    public static void resetBukkitRunnableTask(BukkitRunnable runnable) {
        if (runnable == null) return;
        try {
            java.lang.reflect.Field taskField = BukkitRunnable.class.getDeclaredField("task");
            taskField.setAccessible(true);
            taskField.set(runnable, null);
        } catch (Exception e) {
            ConsoleLogger.warn("[TASKS] Failed to reset BukkitRunnable task: " + e.getMessage());
        }
    }
}
