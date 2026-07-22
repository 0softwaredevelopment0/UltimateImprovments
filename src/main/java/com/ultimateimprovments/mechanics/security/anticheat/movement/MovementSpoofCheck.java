package com.ultimateimprovments.mechanics.security.anticheat.movement;

import com.ultimateimprovments.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovments.mechanics.security.anticheat.core.*;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MovementSpoof — фейковые пакеты движения (микро-движения для обхода AFK).
 * Детекция: очень маленькие перемещения, повторяющиеся с одинаковой амплитудой.
 */
public class MovementSpoofCheck extends AbstractCheck {

    private double minMicroDistance;
    private int minRepeatCount;

    private final ConcurrentHashMap<UUID, MicroTracker> trackers = new ConcurrentHashMap<>();

    public MovementSpoofCheck() {
        super("MovementSpoof", CheckCategory.MOVEMENT);
    }

    @Override
    public void onInit() {
        loadConfig();
        minMicroDistance = getConfigDouble("min_micro_distance", 0.001);
        minRepeatCount = getConfigInt("min_repeat_count", 20);
    }

    @Override
    public void onReload() {
        loadConfig();
        minMicroDistance = getConfigDouble("min_micro_distance", 0.001);
        minRepeatCount = getConfigInt("min_repeat_count", 20);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;

        Location from = e.getFrom();
        Location to = e.getTo();
        double dist = from.distanceSquared(to);

        MicroTracker tracker = trackers.computeIfAbsent(player.getUniqueId(), k -> new MicroTracker());

        // Micro-movement: very tiny distance
        if (dist > 0 && dist < minMicroDistance * minMicroDistance) {
            int count = tracker.increment();
            if (count >= minRepeatCount) {
                CheckResult result = flag(player, 1.5,
                        "MovementSpoof: " + count + " micro-movements (Δ=" + String.format("%.5f", Math.sqrt(dist)) + ")");
                AntiCheatManager.getInstance().handleResult(player, this, result);
            }
        } else {
            tracker.reset();
        }

        PlayerData data = AntiCheatManager.getInstance().getOrCreatePlayerData(player);
        data.updatePosition(to, player.isOnGround());
    }

    private static class MicroTracker {
        private int count = 0;

        int increment() { return ++count; }
        void reset() { count = 0; }
    }
}
