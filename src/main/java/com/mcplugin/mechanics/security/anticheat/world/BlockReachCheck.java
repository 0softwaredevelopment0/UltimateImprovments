package com.mcplugin.mechanics.security.anticheat.world;

import com.mcplugin.mechanics.security.anticheat.AntiCheatManager;
import com.mcplugin.mechanics.security.anticheat.core.*;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * BlockReach — взаимодействие с блоками дальше ванильного лимита.
 * <p>
 * Ванильный лимит (block_interaction_range):
 * Survival: 4.5 блоков (ray-cast от глаз до поверхности блока)
 * Creative: 5.0 блоков
 * <p>
 * Проверка считает дистанцию от глаз до ЦЕНТРА блока (+0.5 по XYZ),
 * поэтому порог выше номинального (≈5.5 survival / 6.5 creative).
 */
public class BlockReachCheck extends AbstractCheck {

    private double maxReach;
    private double maxReachCreative;
    private double pingCompensation;

    public BlockReachCheck() {
        super("BlockReach", CheckCategory.WORLD);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxReach = getConfigDouble("max_reach", 5.5);
        maxReachCreative = getConfigDouble("max_reach_creative", 6.5);
        pingCompensation = getConfigDouble("ping_compensation", 0.5);
    }

    @Override
    public void onReload() {
        loadConfig();
        maxReach = getConfigDouble("max_reach", 5.5);
        maxReachCreative = getConfigDouble("max_reach_creative", 6.5);
        pingCompensation = getConfigDouble("ping_compensation", 0.5);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;

        Location eye = player.getEyeLocation();
        Location blockLoc = e.getBlock().getLocation().add(0.5, 0.5, 0.5);
        double distance = eye.distance(blockLoc);

        double pingAdjust = Math.min(player.getPing() / 1000.0 * 0.1, pingCompensation);
        double effectiveMax = maxReach + pingAdjust;

        if (player.getGameMode() == GameMode.CREATIVE) {
            effectiveMax = maxReachCreative;
        }

        if (distance > effectiveMax) {
            double vl = Math.min(5.0, (distance - effectiveMax) * 3.0);
            CheckResult result = flag(player, vl,
                    "BlockReach: " + String.format("%.2f", distance) + " (max: " + String.format("%.2f", effectiveMax) + ")");
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }
    }
}
