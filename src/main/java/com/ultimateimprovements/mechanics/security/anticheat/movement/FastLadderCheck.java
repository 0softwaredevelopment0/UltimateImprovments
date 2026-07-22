package com.ultimateimprovements.mechanics.security.anticheat.movement;

import com.ultimateimprovements.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovements.mechanics.security.anticheat.core.*;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * FastLadder / FastClimb — ускоренный подъём по лестницам/лианам.
 * Детекция: скорость подъёма по climbable блокам превышает ванильный лимит.
 */
public class FastLadderCheck extends AbstractCheck {

    private double maxClimbSpeedUp;
    private double maxClimbSpeedDown;

    public FastLadderCheck() {
        super("FastLadder", CheckCategory.MOVEMENT);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxClimbSpeedUp = getConfigDouble("max_climb_speed_up", 0.25);
        maxClimbSpeedDown = getConfigDouble("max_climb_speed_down", -0.15);
    }

    @Override
    public void onReload() {
        loadConfig();
        maxClimbSpeedUp = getConfigDouble("max_climb_speed_up", 0.25);
        maxClimbSpeedDown = getConfigDouble("max_climb_speed_down", -0.15);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;

        Block feet = player.getLocation().getBlock();
        if (feet.getType() != Material.LADDER && feet.getType() != Material.VINE
                && feet.getType() != Material.SCAFFOLDING
                && feet.getType() != Material.TWISTING_VINES
                && feet.getType() != Material.WEEPING_VINES) {
            return;
        }

        double yDelta = e.getTo().getY() - e.getFrom().getY();

        if (yDelta > maxClimbSpeedUp) {
            CheckResult result = flag(player, 2.5,
                    "FastLadder up: YΔ=" + String.format("%.3f", yDelta) + " (max: " + maxClimbSpeedUp + ")");
            AntiCheatManager.getInstance().handleResult(player, this, result);
        } else if (yDelta < maxClimbSpeedDown) {
            CheckResult result = flag(player, 2.5,
                    "FastLadder down: YΔ=" + String.format("%.3f", yDelta) + " (max: " + maxClimbSpeedDown + ")");
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }

        PlayerData data = AntiCheatManager.getInstance().getOrCreatePlayerData(player);
        data.updatePosition(e.getTo(), player.isOnGround());
    }
}
