package com.ultimateimprovments.energy;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.energy.consumption.light.LightManager;
import com.ultimateimprovments.energy.generation.basic.GeneratorManager;
import com.ultimateimprovments.energy.generation.basic.GeneratorTask;
import com.ultimateimprovments.energy.generation.reactor.ReactorListener;
import com.ultimateimprovments.energy.generation.reactor.ReactorManager;
import com.ultimateimprovments.energy.generation.reactor.ReactorTask;
import com.ultimateimprovments.energy.machines.furnace.ElectricFurnaceManager;
import com.ultimateimprovments.energy.storage.battery.BatteryDrainTask;
import com.ultimateimprovments.energy.storage.battery.BatteryManager;
import com.ultimateimprovments.energy.transfer.cable.CableLossTask;
import com.ultimateimprovments.energy.transfer.cable.CableNetwork;
import com.ultimateimprovments.energy.transfer.cable.CableVisualTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class UIEnergy extends JavaPlugin implements com.ultimateimprovments.core.SoftReloadable {

    private static UIEnergy instance;

    private BukkitTask cableLossTask;
    private BukkitTask balancerTask;
    private BukkitTask cableVisualTask;
    private BukkitTask batteryTask;
    private BukkitTask generatorTask;
    private BukkitTask reactorTask;
    private BukkitTask lightTask;
    private BukkitTask batteryMultiTickTask;
    private ReactorListener reactorListener;

    @Override
    public void onEnable() {
        runStartup();
    }

    @Override
    public void onDisable() {
        runShutdown();
        getLogger().info("UI-Energy disabled!");
        instance = null;
    }

    /**
     * In-place reload (soft /ui reload): full private shutdown + startup path.
     * Never disables the plugin — on Paper that would close the JAR and
     * re-enabling does not reopen it ("zip file closed" zombie).
     */
    @Override
    public void softReload() {
        getLogger().info("UI-Energy soft reload (in place)...");
        runShutdown();
        runStartup();
    }

    private void runStartup() {
        instance = this;
        Main main = Main.getInstance();
        if (main == null) {
            getLogger().severe("UI-Core not loaded! UI-Energy cannot start.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        CableNetwork.init();

        GeneratorManager.init();
        generatorTask = new GeneratorTask().runTaskTimer(main, 0L, 1L);

        ReactorManager.init();
        reactorListener = new ReactorListener();
        getServer().getPluginManager().registerEvents(reactorListener, main);
        reactorTask = new ReactorTask().runTaskTimer(main, 1L, 1L);

        ElectricFurnaceManager.init();

        batteryTask = new BatteryDrainTask().runTaskTimer(main, 0L, 1L);
        BatteryManager.init();
        batteryMultiTickTask = Bukkit.getScheduler().runTaskTimer(main, BatteryManager::tick, 0L, 1L);

        cableLossTask = new CableLossTask().runTaskTimer(main, 0L, 100L);
        balancerTask = new EnergyBalancerTask().runTaskTimer(main, 0L, 1L);
        cableVisualTask = new CableVisualTask().runTaskTimer(main, 0L, 2L);

        LightManager.init();
        lightTask = Bukkit.getScheduler().runTaskTimer(main, LightManager::tick, 0L, 1L);

        // Bridge for UI-MBS structure mechanics (lightning cooking energy cost)
        com.ultimateimprovments.mbs.api.MbsEnergy.register(new MbsEnergyBridge());

        getLogger().info("UI-Energy enabled!");
    }

    private void runShutdown() {
        if (cableLossTask != null) { cableLossTask.cancel(); cableLossTask = null; }
        if (balancerTask != null) { balancerTask.cancel(); balancerTask = null; }
        if (cableVisualTask != null) { cableVisualTask.cancel(); cableVisualTask = null; }
        if (batteryTask != null) { batteryTask.cancel(); batteryTask = null; }
        if (generatorTask != null) { generatorTask.cancel(); generatorTask = null; }
        if (reactorTask != null) { reactorTask.cancel(); reactorTask = null; }
        if (lightTask != null) { lightTask.cancel(); lightTask = null; }
        if (batteryMultiTickTask != null) { batteryMultiTickTask.cancel(); batteryMultiTickTask = null; }
        if (reactorListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(reactorListener);
            reactorListener = null;
        }
        GeneratorManager.shutdown();
        ReactorManager.shutdown();
        ElectricFurnaceManager.shutdown();
        com.ultimateimprovments.mbs.api.MbsEnergy.unregister();
        org.bukkit.event.HandlerList.unregisterAll(this);
    }

    public static UIEnergy getInstance() {
        return instance;
    }
}
