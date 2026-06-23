package com.mcplugin.energy.transfer.cable;

import org.bukkit.scheduler.BukkitRunnable;

/**
 * Cable tick task — no-op now.
 * Cables no longer store or transfer energy between themselves.
 * Energy flows directly from generators to batteries and from batteries to consumers.
 *
 * @deprecated No longer scheduled. Kept for reference only.
 */
@Deprecated
public class CableTickTask extends BukkitRunnable {

    @Override
    public void run() {
        // Cables don't store energy — nothing to flow between cables.
        // Energy transmission is handled directly by generators/consumers
        // through BFS pathfinding over the cable network.
    }
}