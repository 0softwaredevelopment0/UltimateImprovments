package com.mcplugin.server;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;

public class RedstoneGuardListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onRedstone(BlockRedstoneEvent event) {
        RedstoneGuard guard = RedstoneGuard.getInstance();
        if (guard == null || !guard.isEnabled()) {
            return;
        }

        guard.recordIteration(event.getBlock().getChunk());

        if (guard.isChunkBlocked(event.getBlock().getChunk())) {
            event.setNewCurrent(0);
        }
    }
}
