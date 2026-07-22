package com.ultimateimprovements.mechanics.security.anticheat.world;

import com.ultimateimprovements.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovements.mechanics.security.anticheat.core.*;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Liquids — размещение жидких блоков в impossiblе местах (bucket dumps through walls etc).
 * Детекция: игрок размещает жидкий блок на расстоянии, превышающем нормальный reach.
 */
public class LiquidsCheck extends AbstractCheck {

    private double maxDistance;

    public LiquidsCheck() {
        super("Liquids", CheckCategory.WORLD);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxDistance = getConfigDouble("max_distance", 6.0);
    }

    @Override
    public void onReload() {
        loadConfig();
        maxDistance = getConfigDouble("max_distance", 6.0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;

        Block block = e.getBlockPlaced();
        Material mat = block.getType();

        // Only check liquid-related placements (lava/water/obsidian/cobblestone from interaction)
        if (mat != Material.WATER && mat != Material.LAVA
                && mat != Material.OBSIDIAN && mat != Material.COBBLESTONE
                && mat != Material.STONE) {
            return;
        }

        double distance = player.getEyeLocation().distance(block.getLocation().add(0.5, 0.5, 0.5));
        if (distance > maxDistance) {
            CheckResult result = flag(player, 2.0,
                    "Liquids: placed " + mat + " at " + String.format("%.2f", distance) + " blocks away");
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }
    }
}
