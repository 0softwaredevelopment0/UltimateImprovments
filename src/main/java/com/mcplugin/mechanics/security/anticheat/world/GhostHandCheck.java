package com.mcplugin.mechanics.security.anticheat.world;

import com.mcplugin.mechanics.security.anticheat.AntiCheatManager;
import com.mcplugin.mechanics.security.anticheat.core.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * GhostHand — взаимодействие с блоками сквозь стены.
 * Детекция: между игроком и ломаемым блоком есть сплошные блоки.
 */
public class GhostHandCheck extends AbstractCheck {

    private int maxRayDistance;
    private boolean checkOccluding;

    public GhostHandCheck() {
        super("GhostHand", CheckCategory.WORLD);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxRayDistance = getConfigInt("max_ray_distance", 6);
        checkOccluding = getConfigBoolean("check_occluding", true);
    }

    @Override
    public void onReload() {
        loadConfig();
        maxRayDistance = getConfigInt("max_ray_distance", 6);
        checkOccluding = getConfigBoolean("check_occluding", true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;

        Block target = e.getBlock();
        Location eye = player.getEyeLocation();
        Location targetLoc = target.getLocation().add(0.5, 0.5, 0.5);

        // Check if there are occluding blocks between eye and target
        if (checkOccluding) {
            org.bukkit.util.Vector direction = targetLoc.toVector().subtract(eye.toVector());
            double distance = direction.length();
            if (distance > maxRayDistance) return;
            direction.normalize();

            // Ray-trace from eye to target, checking for solid blocks
            for (double d = 0.5; d < distance; d += 0.5) {
                Location check = eye.clone().add(direction.clone().multiply(d));
                Block checkBlock = check.getBlock();
                if (checkBlock.getType().isOccluding() && checkBlock.getType().isSolid()) {
                    CheckResult result = flag(player, 3.0,
                            "GhostHand: " + checkBlock.getType() + " between player and " + target.getType());
                    AntiCheatManager.getInstance().handleResult(player, this, result);
                    return;
                }
            }
        }
    }
}
