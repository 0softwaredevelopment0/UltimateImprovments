package com.ultimateimprovments.mechanics.features.collapse;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * Слушатель событий блоков для системы обрушения.
 * <p>
 * Обрабатывает постановку (отслеживание блока + правило 0% липкости),
 * ломание и взрывы (очистка отслеживания).
 */
public class BlockCollapseListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        BlockCollapseManager m = BlockCollapseManager.getInstance();
        if (m != null) {
            m.onBlockPlaced(e.getBlockPlaced());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        BlockCollapseManager m = BlockCollapseManager.getInstance();
        if (m != null) {
            m.onBlockBroken(e.getBlock());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        BlockCollapseManager m = BlockCollapseManager.getInstance();
        if (m != null) {
            m.onBlocksDestroyed(e.blockList());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        BlockCollapseManager m = BlockCollapseManager.getInstance();
        if (m != null) {
            m.onBlocksDestroyed(e.blockList());
        }
    }
}
