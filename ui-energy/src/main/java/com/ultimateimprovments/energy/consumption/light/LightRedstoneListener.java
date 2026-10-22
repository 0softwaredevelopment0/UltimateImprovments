package com.ultimateimprovments.energy.consumption.light;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;

/**
 * Immediate reaction to redstone changes around light structures.
 * <p>
 * The energy accounting of {@link LightManager} runs once per second, which is
 * fine for the buffer, but the "every junction must be powered" rule has to
 * react at once: a structure that loses its signal must go dark immediately,
 * and a redstone change can also flip a copper bulb's vanilla {@code lit} state
 * behind the manager — the manager re-asserts its own state right away.
 */
public class LightRedstoneListener implements Listener {

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onRedstoneChange(BlockRedstoneEvent event) {
        if (event.getBlock() == null || event.getBlock().getWorld() == null) return;
        LightManager.onRedstoneUpdate(event.getBlock().getLocation());
    }
}
