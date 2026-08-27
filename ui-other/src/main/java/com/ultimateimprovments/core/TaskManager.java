package com.ultimateimprovments.core;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.energy.generation.reactor.ReactorTask;
import com.ultimateimprovments.energy.storage.battery.BatteryDrainTask;
import com.ultimateimprovments.energy.transfer.cable.CableLossTask;
import com.ultimateimprovments.energy.EnergyBalancerTask;
import com.ultimateimprovments.energy.generation.basic.GeneratorTask;
import com.ultimateimprovments.energy.transfer.cable.CableVisualTask;
import com.ultimateimprovments.mechanics.security.codepanel.CodePanelCleanupTask;
import com.ultimateimprovments.combat.weapons.plasma.PlasmaProjectileTask;
import com.ultimateimprovments.energy.consumption.light.LightManager;
import com.ultimateimprovments.mechanics.environment.radiation.RadiationTask;
import com.ultimateimprovments.mechanics.environment.sunburn.SunburnTask;
import com.ultimateimprovments.listener.FishingListener;
import com.ultimateimprovments.server.EmergencyEntitiesKill;
import com.ultimateimprovments.server.RedstoneGuardTask;
import com.ultimateimprovments.server.ServerOverloadWarning;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class TaskManager {

    private static TaskManager instance;

    private BukkitTask generatorTask;
    private BukkitTask cableLossTask;
    private BukkitTask batteryTask;
    private BukkitTask balancerTask;
    private BukkitTask cableVisualTask;
    private BukkitTask overloadTask;
    private BukkitTask redstoneGuardTask;
    private BukkitTask overloadWarningTask;

    private BukkitTask gunTask;
    private BukkitTask reactorTask;
    private BukkitTask lightTask;
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

    // =========================
    // START / STOP
    // =========================
    public void startAll(Main plugin) {
        if (tasksStarted) return;
        tasksStarted = true;

        // GeneratorTask now managed by GeneratorBasicModule
        cableLossTask = new CableLossTask().runTaskTimer(plugin, 0L, 100L);
        // BatteryDrainTask now managed by BatteryModule (individually toggleable)
        balancerTask = new EnergyBalancerTask().runTaskTimer(plugin, 0L, 1L);
        cableVisualTask = new CableVisualTask().runTaskTimer(plugin, 0L, 2L);
        overloadTask = new EmergencyEntitiesKill().runTaskTimer(plugin, 20L, 20L);
        redstoneGuardTask = new RedstoneGuardTask().runTaskTimer(plugin, 1L, 1L);
        overloadWarningTask = new ServerOverloadWarning().runTaskTimer(plugin, 20L, 20L);

        gunTask = new PlasmaProjectileTask().runTaskTimer(plugin, 1L, 1L);
        // ReactorTask now managed by ReactorModule
        radiationTask = new RadiationTask().runTaskTimer(plugin, 20L, 1L);
        sunburnTask = new SunburnTask().runTaskTimer(plugin, 0L, 1L);
        // FishingListener — singleton. On cancel() we reset its internal task
        // via resetBukkitRunnableTask(), so .runTaskTimer() won't fail with "Already scheduled".
        fishingTask = FishingListener.getInstance().runTaskTimer(plugin, 1L, 1L);
        codePanelCleanupTask = new CodePanelCleanupTask().runTaskTimer(plugin, 200L, 400L);

        ConsoleLogger.info("[TASKS] Started.");
    }

    public void stopAll() {
        cancelAll();
        tasksStarted = false;
    }

    // =========================
    // PER-TASK START / STOP (for hot-toggle)
    // =========================
    public void startBatteryTask(Main plugin) {
        if (batteryTask != null) return;
        batteryTask = new BatteryDrainTask().runTaskTimer(plugin, 0L, 1L);
    }

    public void stopBatteryTask() {
        if (batteryTask != null) { batteryTask.cancel(); batteryTask = null; }
    }

    public void startGeneratorTask(Main plugin) {
        if (generatorTask != null) return;
        generatorTask = new GeneratorTask().runTaskTimer(plugin, 0L, 1L);
    }

    public void stopGeneratorTask() {
        if (generatorTask != null) { generatorTask.cancel(); generatorTask = null; }
    }

    public void startReactorTask(Main plugin) {
        if (reactorTask != null) return;
        reactorTask = new ReactorTask().runTaskTimer(plugin, 1L, 1L);
    }

    public void stopReactorTask() {
        if (reactorTask != null) { reactorTask.cancel(); reactorTask = null; }
    }

    public void startLightTask(Main plugin) {
        if (lightTask != null) return;
        lightTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            LightManager.tick();
        }, 0L, 1L);
    }

    public void stopLightTask() {
        if (lightTask != null) { lightTask.cancel(); lightTask = null; }
    }

    public void startCableVisualTask(Main plugin) {
        if (cableVisualTask != null) return;
        cableVisualTask = new CableVisualTask().runTaskTimer(plugin, 0L, 2L);
    }

    public void stopCableVisualTask() {
        if (cableVisualTask != null) { cableVisualTask.cancel(); cableVisualTask = null; }
    }

    private void cancelAll() {
        if (generatorTask != null) generatorTask.cancel();
        if (cableLossTask != null) cableLossTask.cancel();
        if (batteryTask != null) batteryTask.cancel();
        if (balancerTask != null) balancerTask.cancel();
        if (cableVisualTask != null) cableVisualTask.cancel();
        if (overloadTask != null) overloadTask.cancel();
        if (redstoneGuardTask != null) redstoneGuardTask.cancel();
        if (overloadWarningTask != null) overloadWarningTask.cancel();
        if (gunTask != null) gunTask.cancel();
        if (reactorTask != null) reactorTask.cancel();
        if (radiationTask != null) radiationTask.cancel();
        if (sunburnTask != null) sunburnTask.cancel();
        if (fishingTask != null) {
            fishingTask.cancel();
            // Reset the internal BukkitRunnable task after cancel(),
            // so a repeated .runTaskTimer() won't fail with "Already scheduled"
            resetBukkitRunnableTask(FishingListener.getInstance());
        }
        if (codePanelCleanupTask != null) codePanelCleanupTask.cancel();
        if (lightTask != null) lightTask.cancel();
    }

    /**
     * Resets the internal task BukkitRunnable field via reflection.
     * BukkitRunnable.checkNotYetScheduled() fails if task != null even after cancel().
     * This is a fix for singletons (e.g. FishingListener) on /ui reload.
     * <p>
     * Public — also called from PluginShutdown after a global cancelTasks(),
     * so a repeated runTaskTimer() is guaranteed not to fail with "Already scheduled".
     */
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
