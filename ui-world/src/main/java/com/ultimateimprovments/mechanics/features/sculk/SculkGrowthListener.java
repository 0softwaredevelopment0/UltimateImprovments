package com.ultimateimprovments.mechanics.features.sculk;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * Keeps {@link SculkGrowthManager}'s catalyst registry in sync with the world.
 * <p>
 * Discovery happens on chunk load (cheap: only tile entities are inspected) and
 * on player placement. Removals only prune the registry — the pulse validates
 * the block type anyway, so a missed removal is self-healing.
 */
public final class SculkGrowthListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        SculkGrowthManager manager = SculkGrowthManager.getInstance();
        if (manager != null) {
            manager.scanChunk(event.getChunk());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.SCULK_CATALYST) return;
        SculkGrowthManager manager = SculkGrowthManager.getInstance();
        if (manager != null) {
            manager.track(event.getBlockPlaced());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.SCULK_CATALYST) return;
        SculkGrowthManager manager = SculkGrowthManager.getInstance();
        if (manager != null) {
            manager.untrack(event.getBlock());
        }
    }
}
