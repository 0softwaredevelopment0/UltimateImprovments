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
 * Speed — accelerated movement.
 * <p>
 * Detection:
 * 1. Distance per tick exceeds the maximum (walk/sprint/ice/roads).
 * 2. Sprint-jump WITHOUT gaining height — the "ground-speed" hack.
 *    If the player sprints and sends a jump (yDelta > 0), but the jump
 *    is SO small that it gives no height — that's a hack that speeds
 *    the player up 2.5× without a real jump.
 */
public class SpeedCheck extends AbstractCheck {

    private double maxSpeedGround;
    private double maxSpeedAir;
    private double minJumpHeight;
    private double sprintJumpSpeedMul;

    // Last yDelta values for ground-speed detection
    private final ConcurrentHashMap<UUID, Double> lastYDelta = new ConcurrentHashMap<>();

    public SpeedCheck() {
        super("Speed", CheckCategory.MOVEMENT);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxSpeedGround = getConfigDouble("max_speed_ground", 0.4);
        maxSpeedAir = getConfigDouble("max_speed_air", 0.4);
        minJumpHeight = getConfigDouble("min_jump_height", 0.05);
        sprintJumpSpeedMul = getConfigDouble("sprint_jump_speed_multiplier", 1.3);
    }

    @Override
    public void onReload() { loadConfig(); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;

        double xDelta = e.getTo().getX() - e.getFrom().getX();
        double yDelta = e.getTo().getY() - e.getFrom().getY();
        double zDelta = e.getTo().getZ() - e.getFrom().getZ();
        double horizontalDist = Math.sqrt(xDelta * xDelta + zDelta * zDelta);

        boolean onGround = player.isOnGround();
        double maxSpeed = onGround ? maxSpeedGround : maxSpeedAir;

        // ── Sprint-jump without height (ground-speed hack) ──
        // Hack: the player sends a jump (yDelta > 0) but the jump is so small
        // that it's not a real jump (vanilla jump = 0.42).
        // Result: the server grants the sprint-jump speed boost
        // without an actual jump, speeding the player up 2.5× faster than normal.
        if (player.isSprinting() && yDelta > 0 && yDelta < minJumpHeight) {
            // Fake jump detected — deny the sprint boost
            // Flag for ground-speed
            double vl = Math.min(3.0, (minJumpHeight - yDelta) * 20.0);
            CheckResult result = flag(player, vl,
                    "Ground-Speed: YΔ=" + String.format("%.3f", yDelta)
                    + " (min jump: " + String.format("%.2f", minJumpHeight) + ")");
            AntiCheatManager.getInstance().handleResult(player, this, result);
            return;
        }

        // Sprinting increases speed (only for real jumps)
        if (player.isSprinting()) maxSpeed *= sprintJumpSpeedMul;

        if (horizontalDist > maxSpeed) {
            double exceed = horizontalDist - maxSpeed;
            double vl = Math.min(5.0, exceed * 10.0);
            CheckResult result = flag(player, vl,
                    "Speed: " + String.format("%.3f", horizontalDist) + " (max: " + String.format("%.3f", maxSpeed) + ")");
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }

        PlayerData data = AntiCheatManager.getInstance().getOrCreatePlayerData(player);
        data.updatePosition(e.getTo(), onGround);
        lastYDelta.put(player.getUniqueId(), yDelta);
    }
}
