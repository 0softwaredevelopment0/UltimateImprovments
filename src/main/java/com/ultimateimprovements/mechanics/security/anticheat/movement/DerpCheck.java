package com.ultimateimprovements.mechanics.security.anticheat.movement;

import com.ultimateimprovements.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovements.mechanics.security.anticheat.core.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Derp / NoHead — невозможная ротация головы.
 * Детекция: pitch вне диапазона [-90, 90], резкие смены pitch/yaw.
 */
public class DerpCheck extends AbstractCheck {

    private float minPitch;
    private float maxPitch;
    private float maxYawDelta;

    public DerpCheck() {
        super("Derp", CheckCategory.MOVEMENT);
    }

    @Override
    public void onInit() {
        loadConfig();
        minPitch = (float) getConfigDouble("min_pitch", -90.0);
        maxPitch = (float) getConfigDouble("max_pitch", 90.0);
        maxYawDelta = (float) getConfigDouble("max_yaw_delta", 180.0);
    }

    @Override
    public void onReload() {
        loadConfig();
        minPitch = (float) getConfigDouble("min_pitch", -90.0);
        maxPitch = (float) getConfigDouble("max_pitch", 90.0);
        maxYawDelta = (float) getConfigDouble("max_yaw_delta", 180.0);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;

        float pitch = e.getTo().getPitch();
        float yaw = e.getTo().getYaw();

        PlayerData data = AntiCheatManager.getInstance().getOrCreatePlayerData(player);

        // Impossible pitch
        if (pitch < minPitch || pitch > maxPitch) {
            CheckResult result = flag(player, 4.0,
                    "Derp: pitch=" + pitch + " (valid: [" + minPitch + ", " + maxPitch + "])");
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }

        // Rapid yaw flip
        float yawDelta = Math.abs(yaw - data.getLastYaw());
        if (yawDelta > 180) yawDelta = 360 - yawDelta;
        if (yawDelta > maxYawDelta) {
            CheckResult result = flag(player, 2.0,
                    "Derp: yawΔ=" + String.format("%.1f", yawDelta) + "° (max: " + maxYawDelta + ")");
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }

        data.updateRotation(yaw, pitch);
        data.updatePosition(e.getTo(), player.isOnGround());
    }
}
