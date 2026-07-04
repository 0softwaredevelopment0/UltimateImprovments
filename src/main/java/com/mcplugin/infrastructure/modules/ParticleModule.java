package com.mcplugin.infrastructure.modules;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.ConsoleLogger;
import com.mcplugin.mechanics.crafting.*;
import com.mcplugin.mechanics.particle.ParticleAcceleratorManager;
import com.mcplugin.mechanics.particle.ParticleMovementTask;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class ParticleModule extends PluginModule {

    private BukkitTask movementTask;

    public ParticleModule() {
        super("ParticleAccelerator", "mechanics/particle", false);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;

        ParticleAcceleratorManager.init(main);

        // Movement task every tick
        movementTask = new ParticleMovementTask().runTaskTimer(main, 20L, 1L);

        // Register crafting recipes (Item Assembler only)
        ParticleRingCraftListener.init();
        main.getServer().getPluginManager().registerEvents(new ParticleRingCraftListener(), main);

        ParticleEngineCraftListener.init();
        main.getServer().getPluginManager().registerEvents(new ParticleEngineCraftListener(), main);

        ParticleSensorCraftListener.init();
        main.getServer().getPluginManager().registerEvents(new ParticleSensorCraftListener(), main);

        ParticleInjectorCraftListener.init();
        main.getServer().getPluginManager().registerEvents(new ParticleInjectorCraftListener(), main);

        ConsoleLogger.info("[ParticleModule] ✔ Particle accelerator system initialized.");
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        if (movementTask != null) {
            movementTask.cancel();
            movementTask = null;
        }
        ParticleAcceleratorManager.shutdown();
    }
}
