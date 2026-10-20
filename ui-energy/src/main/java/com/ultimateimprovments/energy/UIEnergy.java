package com.ultimateimprovments.energy;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.energy.consumption.light.LightManager;
import com.ultimateimprovments.energy.generation.basic.GeneratorManager;
import com.ultimateimprovments.energy.generation.basic.GeneratorTask;
import com.ultimateimprovments.energy.generation.nuclear.NuclearReactorManager;
import com.ultimateimprovments.energy.generation.nuclear.NuclearReactorTask;
import com.ultimateimprovments.energy.generation.reactor.ReactorListener;
import com.ultimateimprovments.energy.generation.reactor.ReactorManager;
import com.ultimateimprovments.energy.generation.reactor.ReactorTask;
import com.ultimateimprovments.energy.machines.furnace.ElectricFurnaceManager;
import com.ultimateimprovments.energy.storage.battery.BatteryManager;
import com.ultimateimprovments.energy.transfer.cable.CableNetwork;
import com.ultimateimprovments.energy.transfer.cable.CableVisualTask;
import com.ultimateimprovments.energy.transfer.cable.EnergyPersistence;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class UIEnergy extends JavaPlugin implements com.ultimateimprovments.core.SoftReloadable {

    private static UIEnergy instance;

    private BukkitTask generatorTask;
    private BukkitTask reactorTask;
    private BukkitTask nuclearReactorTask;
    private BukkitTask balancerTask;
    private BukkitTask cableVisualTask;
    private BukkitTask persistenceTask;
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
        EnergyPersistence.init();

        GeneratorManager.init();
        // Every 40 ticks (2s): the per-second energy rates inside are scaled
        // by the period, so generators produce exactly as much as before.
        generatorTask = new GeneratorTask().runTaskTimer(main, 0L, 40L);

        ReactorManager.init();
        reactorListener = new ReactorListener();
        getServer().getPluginManager().registerEvents(reactorListener, main);
        reactorTask = new ReactorTask().runTaskTimer(main, 1L, 1L);

        // Compact nuclear reactors (safe energy generators, 3×3×3 structure).
        NuclearReactorManager.init();
        nuclearReactorTask = new NuclearReactorTask().runTaskTimer(main, 20L, 20L);

        ElectricFurnaceManager.init();

        // Batteries first (their nodes must exist), then the energy restore.
        BatteryManager.init();
        EnergyPersistence.load();
        batteryMultiTickTask = Bukkit.getScheduler().runTaskTimer(main, BatteryManager::tick, 0L, 1L);

        // The single battery-balancing pass (the old BatteryDrainTask was
        // merged into it — it was a second balancer with conflicting rules).
        // Every 40 ticks (2s): amounts are scaled by the period.
        balancerTask = new EnergyBalancerTask().runTaskTimer(main, 0L, 40L);
        // Real-tick blink: off/on_ticks are actual ticks now (5-tick pass).
        cableVisualTask = new CableVisualTask().runTaskTimer(main, 0L, 5L);
        // Periodic energy flush (batteries keep their charge across restarts).
        persistenceTask = Bukkit.getScheduler().runTaskTimer(main, EnergyPersistence::saveAll, 200L, 200L);

        LightManager.init();
        lightTask = Bukkit.getScheduler().runTaskTimer(main, LightManager::tick, 0L, 1L);

        // Bridge for UI-MBS structure mechanics (lightning cooking energy cost)
        com.ultimateimprovments.mbs.api.MbsEnergy.register(new MbsEnergyBridge());

        getLogger().info("UI-Energy enabled!");
    }

    private void runShutdown() {
        if (generatorTask != null) { generatorTask.cancel(); generatorTask = null; }
        if (reactorTask != null) { reactorTask.cancel(); reactorTask = null; }
        if (nuclearReactorTask != null) { nuclearReactorTask.cancel(); nuclearReactorTask = null; }
        if (balancerTask != null) { balancerTask.cancel(); balancerTask = null; }
        if (cableVisualTask != null) { cableVisualTask.cancel(); cableVisualTask = null; }
        if (persistenceTask != null) { persistenceTask.cancel(); persistenceTask = null; }
        if (lightTask != null) { lightTask.cancel(); lightTask = null; }
        if (batteryMultiTickTask != null) { batteryMultiTickTask.cancel(); batteryMultiTickTask = null; }
        if (reactorListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(reactorListener);
            reactorListener = null;
        }
        // Flush the battery charge before the managers drop their nodes.
        EnergyPersistence.saveAll();
        GeneratorManager.shutdown();
        ReactorManager.shutdown();
        NuclearReactorManager.shutdown();
        ElectricFurnaceManager.shutdown();
        BatteryManager.shutdown();
        com.ultimateimprovments.mbs.api.MbsEnergy.unregister();
        org.bukkit.event.HandlerList.unregisterAll(this);
    }

    public static UIEnergy getInstance() {
        return instance;
    }
}
