package com.ultimateimprovments.mechanics.security.anticheat.movement;

import com.ultimateimprovments.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovments.mechanics.security.anticheat.core.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Step — climbing blocks higher than 0.6 without a jump (step hack).
 * Detection: vertical upward movement > the limit in one tick, without a jump.
 */
public class StepCheck extends AbstractCheck {

    private double maxStepHeight;

    public StepCheck() {
        super("Step", CheckCategory.MOVEMENT);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxStepHeight = getConfigDouble("max_step_height", 0.6);
    }

    @Override
    public void onReload() {
        loadConfig();
        maxStepHeight = getConfigDouble("max_step_height", 0.6);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;

        double yDelta = e.getTo().getY() - e.getFrom().getY();
        boolean wasOnGround = player.isOnGround();
        boolean toGround = e.getTo().getY() % 1 < 0.03;

        // Step = going up while on ground (not a jump)
        if (wasOnGround && toGround && yDelta > maxStepHeight && yDelta < 0.42) {
            CheckResult result = flag(player, 2.0,
                    "Step: YΔ=" + String.format("%.3f", yDelta) + " (max: " + maxStepHeight + ")");
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }

        PlayerData data = AntiCheatManager.getInstance().getOrCreatePlayerData(player);
        data.updatePosition(e.getTo(), player.isOnGround());
    }
}
