package com.ultimateimprovments.space;

import com.ultimateimprovments.core.Main;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Passive radiation in the space dimension:
 * +199 damage every 10 seconds to ALL players in space.
 * <p>
 * This is not affected by armor — it's environment damage.
 * Players need radiation protection items or they'll die quickly.
 */
public class SpaceRadiationListener implements Listener {

    private static final double RADIATION_DAMAGE = 199.0;
    private static final long RADIATION_INTERVAL_TICKS = 200; // 10 seconds
    private static final String RADIATION_DAMAGE_NAME = "space_radiation";

    private static boolean running = false;

    public static void start(Main plugin) {
        if (running) return;
        running = true;

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!SpaceManager.isEnabled() || SpaceManager.getSpaceWorld() == null) {
                    return;
                }
                for (Player player : SpaceManager.getSpaceWorld().getPlayers()) {
                    player.damage(RADIATION_DAMAGE);
                }
            }
        }.runTaskTimer(plugin, RADIATION_INTERVAL_TICKS, RADIATION_INTERVAL_TICKS);
    }

    public static void stop() {
        running = false;
    }
}