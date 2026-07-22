package com.ultimateimprovments.mechanics.security.anticheat.movement;

import com.ultimateimprovments.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovments.mechanics.security.anticheat.core.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * EntityMovement — базовая проверка движения на entity (лошадь, лодка, вагонетка).
 * Детекция: скорость передвижения на entity превышает максимальную для данного типа.
 */
public class EntityMovementCheck extends AbstractCheck {

    private double maxEntitySpeed;

    public EntityMovementCheck() {
        super("EntityMovement", CheckCategory.MOVEMENT);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxEntitySpeed = getConfigDouble("max_entity_speed", 1.0);
    }

    @Override
    public void onReload() {
        loadConfig();
        maxEntitySpeed = getConfigDouble("max_entity_speed", 1.0);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;
        if (!player.isInsideVehicle()) return;

        double xDelta = e.getTo().getX() - e.getFrom().getX();
        double zDelta = e.getTo().getZ() - e.getFrom().getZ();
        double speed = Math.sqrt(xDelta * xDelta + zDelta * zDelta);

        if (speed > maxEntitySpeed) {
            CheckResult result = flag(player, 2.0,
                    "Entity speed: " + String.format("%.3f", speed) + " (max: " + maxEntitySpeed + ")");
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }

        PlayerData data = AntiCheatManager.getInstance().getOrCreatePlayerData(player);
        data.updatePosition(e.getTo(), player.isOnGround());
    }
}
