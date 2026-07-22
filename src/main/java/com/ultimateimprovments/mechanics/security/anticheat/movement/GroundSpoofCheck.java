package com.ultimateimprovments.mechanics.security.anticheat.movement;

import com.ultimateimprovments.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovments.mechanics.security.anticheat.core.*;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * GroundSpoof — игрок отправляет onGround=true, находясь в воздухе.
 * Детекция: клиент утверждает что на земле, но Y уменьшается (падает).
 */
public class GroundSpoofCheck extends AbstractCheck {

    private double minFallDistance;
    private int minSpoofTicks;

    private final java.util.concurrent.ConcurrentHashMap<java.util.UUID, Integer> spoofTicks
            = new java.util.concurrent.ConcurrentHashMap<>();

    public GroundSpoofCheck() {
        super("GroundSpoof", CheckCategory.MOVEMENT);
    }

    @Override
    public void onInit() {
        loadConfig();
        minFallDistance = getConfigDouble("min_fall_distance", 2.0);
        minSpoofTicks = getConfigInt("min_spoof_ticks", 5);
    }

    @Override
    public void onReload() {
        loadConfig();
        minFallDistance = getConfigDouble("min_fall_distance", 2.0);
        minSpoofTicks = getConfigInt("min_spoof_ticks", 5);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;

        boolean clientOnGround = player.isOnGround();
        double yDelta = e.getTo().getY() - e.getFrom().getY();

        // Server-side ground check: ray-cast down from player position
        Location loc = e.getTo().clone();
        boolean serverGround = false;
        for (double dy = 0; dy < 0.5; dy += 0.1) {
            if (loc.clone().subtract(0, dy, 0).getBlock().getType().isSolid()) {
                serverGround = true;
                break;
            }
        }

        // Client says on ground, server says not on ground, and player is falling
        if (clientOnGround && !serverGround && yDelta < -0.1) {
            int count = spoofTicks.merge(player.getUniqueId(), 1, Integer::sum);
            if (count >= minSpoofTicks) {
                CheckResult result = flag(player, 3.0,
                        "GroundSpoof: client=ground, server=air, YΔ=" + String.format("%.3f", yDelta));
                AntiCheatManager.getInstance().handleResult(player, this, result);
            }
        } else {
            spoofTicks.put(player.getUniqueId(), 0);
        }

        PlayerData data = AntiCheatManager.getInstance().getOrCreatePlayerData(player);
        data.updatePosition(e.getTo(), clientOnGround);
    }
}
