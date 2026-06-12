package com.mcplugin.main;

import com.mcplugin.Main;
import com.mcplugin.core1.ReactorTask;
import com.mcplugin.energy.BatteryDrainTask;
import com.mcplugin.energy.CableLossTask;
import com.mcplugin.energy.EnergyBalancerTask;
import com.mcplugin.energy.GeneratorTask;
import com.mcplugin.energy.visual.CableVisualTask;
import com.mcplugin.guns.plasmacannon.PlasmaProjectileTask;
import com.mcplugin.radiation.RadiationTask;
import com.mcplugin.server.EmergencyEntitiesKill;
import com.mcplugin.server.RedstoneGuardTask;

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

    private BukkitTask gunTask;
    private BukkitTask reactorTask;
    private BukkitTask radiationTask;

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

        generatorTask = new GeneratorTask().runTaskTimer(plugin, 0L, 1L);
        cableLossTask = new CableLossTask().runTaskTimer(plugin, 0L, 100L);
        batteryTask = new BatteryDrainTask().runTaskTimer(plugin, 0L, 1L);
        balancerTask = new EnergyBalancerTask().runTaskTimer(plugin, 0L, 1L);
        cableVisualTask = new CableVisualTask().runTaskTimer(plugin, 0L, 2L);
        overloadTask = new EmergencyEntitiesKill().runTaskTimer(plugin, 20L, 20L);
        redstoneGuardTask = new RedstoneGuardTask().runTaskTimer(plugin, 1L, 1L);

        gunTask = new PlasmaProjectileTask().runTaskTimer(plugin, 1L, 1L);
        reactorTask = new ReactorTask().runTaskTimer(plugin, 1L, 1L);
        radiationTask = new RadiationTask().runTaskTimer(plugin, 20L, 1L);

        plugin.getLogger().info("[TASKS] Started.");
    }

    public void stopAll() {
        if (generatorTask != null) generatorTask.cancel();
        if (cableLossTask != null) cableLossTask.cancel();
        if (batteryTask != null) batteryTask.cancel();
        if (balancerTask != null) balancerTask.cancel();
        if (cableVisualTask != null) cableVisualTask.cancel();
        if (overloadTask != null) overloadTask.cancel();
        if (redstoneGuardTask != null) redstoneGuardTask.cancel();
        if (gunTask != null) gunTask.cancel();
        if (reactorTask != null) reactorTask.cancel();
        if (radiationTask != null) radiationTask.cancel();

        tasksStarted = false;
    }
}
