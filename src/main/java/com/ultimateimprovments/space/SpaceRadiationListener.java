package com.ultimateimprovments.space;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.mechanics.environment.radiation.RadiationManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Space dimension radiation — delegates to the global {@link RadiationManager}.
 * <p>
 * When the player dies or quits while in space, radiation is reset
 * (handled by RadiationManager's death/quit listeners, but we also
 * ensure cleanup here for safety).
 */
public class SpaceRadiationListener implements Listener {

    private static boolean running = false;

    public static void start(Main plugin) {
        if (running) return;
        running = true;
        // Radiation addition is handled by RadiationManager.tick()
        // which checks SpaceManager.isInSpace() every second.
    }

    public static void stop() {
        running = false;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        // RadiationManager already resets on death via death_reset config
        // This is a safety net for space-specific cleanup
        if (SpaceManager.isInSpace(event.getEntity())) {
            RadiationManager.resetRadiation(event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (SpaceManager.isInSpace(event.getPlayer())) {
            RadiationManager.resetRadiation(event.getPlayer());
        }
    }
}
