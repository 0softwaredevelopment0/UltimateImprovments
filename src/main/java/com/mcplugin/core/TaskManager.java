package com.mcplugin.core;

import com.mcplugin.core.Main;
import com.mcplugin.energy.generation.reactor.ReactorTask;
import com.mcplugin.energy.storage.battery.BatteryDrainTask;
import com.mcplugin.energy.transfer.cable.CableLossTask;
import com.mcplugin.energy.EnergyBalancerTask;
import com.mcplugin.energy.generation.basic.GeneratorTask;
import com.mcplugin.energy.transfer.cable.CableVisualTask;
import com.mcplugin.mechanics.security.codepanel.CodePanelCleanupTask;
import com.mcplugin.combat.weapons.plasma.PlasmaProjectileTask;
import com.mcplugin.energy.consumption.light.LightManager;
import com.mcplugin.mechanics.environment.radiation.RadiationTask;
import com.mcplugin.listener.FishingListener;
import com.mcplugin.server.EmergencyEntitiesKill;
import com.mcplugin.server.RedstoneGuardTask;
import com.mcplugin.server.ServerOverloadWarning;
import com.mcplugin.util.ConsoleLogger;
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
        // FishingListener — singleton. При cancel() мы сбрасываем его internal task
        // через resetBukkitRunnableTask(), поэтому .runTaskTimer() не упадёт с "Already scheduled".
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
        if (fishingTask != null) {
            fishingTask.cancel();
            // Сбрасываем internal task BukkitRunnable после cancel(),
            // чтобы повторный .runTaskTimer() не упал с "Already scheduled"
            resetBukkitRunnableTask(FishingListener.getInstance());
        }
        if (codePanelCleanupTask != null) codePanelCleanupTask.cancel();
        if (lightTask != null) lightTask.cancel();
    }

    /**
     * Сбрасывает внутреннее поле task BukkitRunnable через рефлексию.
     * BukkitRunnable.checkNotYetScheduled() падает если task != null даже после cancel().
     * Это фикс для singleton'ов (например FishingListener) при /mp reload.
     */
    private static void resetBukkitRunnableTask(BukkitRunnable runnable) {
        try {
            java.lang.reflect.Field taskField = BukkitRunnable.class.getDeclaredField("task");
            taskField.setAccessible(true);
            taskField.set(runnable, null);
        } catch (Exception e) {
            ConsoleLogger.warn("[TASKS] Failed to reset BukkitRunnable task: " + e.getMessage());
        }
    }
}
