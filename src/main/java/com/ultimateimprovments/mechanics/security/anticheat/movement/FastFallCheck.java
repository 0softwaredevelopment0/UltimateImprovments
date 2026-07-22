package com.ultimateimprovments.mechanics.security.anticheat.movement;

import com.ultimateimprovments.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovments.mechanics.security.anticheat.core.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * FastFall — ускоренное падение (быстрее гравитации).
 * Детекция: Y-скорость падения превышает максимальную (-0.4 за тик без модификаторов).
 */
public class FastFallCheck extends AbstractCheck {

    private double maxFallSpeed;

    public FastFallCheck() {
        super("FastFall", CheckCategory.MOVEMENT);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxFallSpeed = getConfigDouble("max_fall_speed", -3.92);
    }

    @Override
    public void onReload() {
        loadConfig();
        maxFallSpeed = getConfigDouble("max_fall_speed", -3.92);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;
        if (player.isOnGround()) return;

        double yDelta = e.getTo().getY() - e.getFrom().getY();

        // Falling faster than max terminal velocity
        if (yDelta < maxFallSpeed) {
            CheckResult result = flag(player, 3.0,
                    "FastFall: YΔ=" + String.format("%.3f", yDelta) + " (max: " + String.format("%.3f", maxFallSpeed) + ")");
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }

        PlayerData data = AntiCheatManager.getInstance().getOrCreatePlayerData(player);
        data.updatePosition(e.getTo(), player.isOnGround());
    }
}
