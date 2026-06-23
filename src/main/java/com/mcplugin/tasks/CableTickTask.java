package com.mcplugin.tasks;

import org.bukkit.scheduler.BukkitRunnable;

/**
 * Cable tick task — no-op now.
 * Cables no longer store or transfer energy between themselves.
 * Energy flows directly from generators to batteries and from batteries to consumers.
 * Kept for scheduler compatibility; does nothing.
 */
public class CableTickTask extends BukkitRunnable {

    @Override
    public void run() {
        // Cables don't store energy — nothing to flow between cables.
        // Energy transmission is handled directly by generators/consumers
        // through BFS pathfinding over the cable network.
    }
}