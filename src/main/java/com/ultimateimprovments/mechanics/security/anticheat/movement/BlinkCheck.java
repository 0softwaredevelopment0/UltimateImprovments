package com.ultimateimprovments.mechanics.security.anticheat.movement;

import com.ultimateimprovments.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovments.mechanics.security.anticheat.core.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blink — игрок задерживает пакеты движения, затем отправляет их все разом.
 * Детекция: длительная пауза в пакетах движения, затем резкий скачок.
 */
public class BlinkCheck extends AbstractCheck {

    private long maxSilenceMs;
    private double minJumpDistance;

    private final ConcurrentHashMap<UUID, Long> lastMoveTime = new ConcurrentHashMap<>();

    public BlinkCheck() {
        super("Blink", CheckCategory.MOVEMENT);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxSilenceMs = getConfigInt("max_silence_ms", 2000);
        minJumpDistance = getConfigDouble("min_jump_distance", 3.0);
    }

    @Override
    public void onReload() {
        loadConfig();
        maxSilenceMs = getConfigInt("max_silence_ms", 2000);
        minJumpDistance = getConfigDouble("min_jump_distance", 3.0);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastTime = lastMoveTime.get(uuid);

        if (lastTime != null) {
            long silence = now - lastTime;
            if (silence > maxSilenceMs) {
                double distance = e.getFrom().distance(e.getTo());
                if (distance > minJumpDistance) {
                    CheckResult result = flag(player, 3.0,
                            "Blink: " + silence + "ms silence, then " + String.format("%.2f", distance) + " block jump");
                    AntiCheatManager.getInstance().handleResult(player, this, result);
                }
            }
        }

        lastMoveTime.put(uuid, now);

        PlayerData data = AntiCheatManager.getInstance().getOrCreatePlayerData(player);
        data.updatePosition(e.getTo(), player.isOnGround());
    }
}
