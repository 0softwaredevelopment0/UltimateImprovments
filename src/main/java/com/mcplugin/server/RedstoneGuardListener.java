package com.mcplugin.server;

import org.bukkit.Chunk;
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

        Chunk chunk = event.getBlock().getChunk();

        if (guard.isChunkBlocked(chunk)) {
            event.setNewCurrent(0);
            return; // Don't count iterations for blocked chunks
        }

        guard.recordIteration(chunk);
    }
}
