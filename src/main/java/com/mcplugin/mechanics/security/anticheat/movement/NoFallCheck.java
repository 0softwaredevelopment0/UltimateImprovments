package com.mcplugin.mechanics.security.anticheat.movement;

import com.mcplugin.mechanics.security.anticheat.AntiCheatManager;
import com.mcplugin.mechanics.security.anticheat.core.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * NoFall — отключение урона от падения.
 * Детекция: игрок утверждает что на земле, но Y меняется (падает).
 */
public class NoFallCheck extends AbstractCheck {

    private double minFallDistance;

    public NoFallCheck() {
        super("NoFall", CheckCategory.MOVEMENT);
    }

    @Override
    public void onInit() {
        loadConfig();
        minFallDistance = getConfigDouble("min_fall_distance", 3.0);
    }

    @Override
    public void onReload() { loadConfig(); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;

        // If player claims onGround but is falling (Y decreasing significantly)
        boolean clientOnGround = player.isOnGround();
        double yDelta = e.getTo().getY() - e.getFrom().getY();

        if (!clientOnGround) return;

        // Check fall distance — if player has been falling but suddenly reports onGround
        float fallDist = player.getFallDistance();
        if (fallDist > minFallDistance && yDelta < -0.1) {
            CheckResult result = flag(player, 2.0,
                    "NoFall: fallDist=" + String.format("%.1f", fallDist) + " but onGround=true");
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }
    }
}
