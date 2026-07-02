package com.mcplugin.mechanics.security.anticheat.movement;

import com.mcplugin.mechanics.security.anticheat.AntiCheatManager;
import com.mcplugin.mechanics.security.anticheat.core.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AllJumps — прыжки без отрыва от земли или аномальные прыжки.
 * Детекция: Y-скорость вверх без прыжка, повторные прыжки в воздухе.
 */
public class AllJumpsCheck extends AbstractCheck {

    private double minJumpVelocity;
    private int maxAirJumps;
    private final ConcurrentHashMap<UUID, Integer> airJumpCounters = new ConcurrentHashMap<>();

    public AllJumpsCheck() {
        super("AllJumps", CheckCategory.MOVEMENT);
    }

    @Override
    public void onInit() {
        loadConfig();
        minJumpVelocity = getConfigDouble("min_jump_velocity", 0.42);
        maxAirJumps = getConfigInt("max_air_jumps", 0);
    }

    @Override
    public void onReload() {
        loadConfig();
        minJumpVelocity = getConfigDouble("min_jump_velocity", 0.42);
        maxAirJumps = getConfigInt("max_air_jumps", 0);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;

        boolean onGround = player.isOnGround();
        double yDelta = e.getTo().getY() - e.getFrom().getY();

        if (onGround) {
            airJumpCounters.put(player.getUniqueId(), 0);
            return;
        }

        // Upward movement in air = air jump
        if (yDelta > minJumpVelocity) {
            int airJumpCount = airJumpCounters.merge(player.getUniqueId(), 1, Integer::sum);
            if (airJumpCount > maxAirJumps) {
                CheckResult result = flag(player, 3.0,
                        "AirJump #" + airJumpCount + " (YΔ=" + String.format("%.3f", yDelta) + ")");
                AntiCheatManager.getInstance().handleResult(player, this, result);
            }
        }

        PlayerData data = AntiCheatManager.getInstance().getOrCreatePlayerData(player);
        data.updatePosition(e.getTo(), onGround);
    }
}
