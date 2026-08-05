package com.ultimateimprovments.structure;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.energy.consumption.light.LightManager;
import com.ultimateimprovments.energy.generation.basic.GeneratorManager;
import com.ultimateimprovments.energy.machines.assembler.AssemblerManager;
import com.ultimateimprovments.energy.machines.workbench.EnergyWorkbenchManager;
import com.ultimateimprovments.energy.storage.battery.BatteryManager;
import com.ultimateimprovments.energy.transfer.cable.CableNetwork;
import com.ultimateimprovments.mechanics.environment.lightning.LightningManager;
import com.ultimateimprovments.mechanics.environment.magnet.MagnetManager;
import com.ultimateimprovments.mechanics.particle.ParticleAcceleratorManager;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * World-load listener — rebuilds managers from the {@link StructureMarker} cache.
 * <p>
 * Previously chunks were scanned for Marker entities to restore the cache — that
 * mechanism is FULLY replaced by storing structure data in SQLite
 * ({@link StructureMarker#loadFromDatabase()} at startup). Now the cache is always
 * complete, and ChunkLoadEvent is not needed: manager rebuilds happen from the cache.
 */
public class StructureChunkListener implements Listener {

    // ════════════════════════════════════════
    // WORLD LOAD — when a new world loads, rebuild the managers
    // (and clean up that world's legacy Markers if any remain)
    // ════════════════════════════════════════
    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        // Remove outdated Marker entities (idempotent; writes nothing to the DB
        // if data already exists — only removes entities)
        StructureMarker.migrateLegacyMarkers();
        rebuildAllManagers();
    }

    // ════════════════════════════════════════
    // CHUNK LOAD — nothing is scanned anymore: structure data lives in the DB,
    // the cache is complete from the moment the plugin loads.
    // ════════════════════════════════════════
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        // no-op: Marker scanning was removed in favor of SQLite persistence
    }

    // ════════════════════════════════════════
    // SCHEDULE DELAYED REBUILDS
    // At server startup only the spawn chunks are loaded. Managers are rebuilt
    // from the cache (already loaded from the DB), but the delayed passes cover
    // late worlds (Multiverse etc.):
    //    5 sec — most worlds
    //   30 sec — chunks farther from spawn, multi-worlds
    //  120 sec — very late worlds (Multiverse etc.)
    // ════════════════════════════════════════
    public static void scheduleDelayedRebuild(Plugin plugin) {
        // 5 seconds
        Bukkit.getScheduler().runTaskLater(plugin, () -> rebuildAllManagers(), 100L);

        // 30 seconds
        Bukkit.getScheduler().runTaskLater(plugin, () -> rebuildAllManagers(), 600L);

        // 120 seconds
        Bukkit.getScheduler().runTaskLater(plugin, () -> rebuildAllManagers(), 2400L);
    }

    // ════════════════════════════════════════
    // REBUILD ALL MANAGERS
    // Rebuilds all managers from the StructureMarker cache (populated from the DB).
    // ════════════════════════════════════════
    private static void rebuildAllManagers() {
        CableNetwork.rebuildFromMarkers();
        BatteryManager.rebuildFromMarkers();
        LightManager.rebuildFromMarkers();
        LightningManager.rebuildFromMarkers();
        EnergyWorkbenchManager.scanFromMarkers();
        AssemblerManager.scanExistingAssemblers();
        GeneratorManager.scanExistingGenerators();
        ParticleAcceleratorManager.scanExistingAccelerators();
        MagnetManager.rebuildFromMarkers();
    }
}
