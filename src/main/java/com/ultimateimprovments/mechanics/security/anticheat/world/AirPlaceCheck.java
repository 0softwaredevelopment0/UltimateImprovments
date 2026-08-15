package com.ultimateimprovments.mechanics.security.anticheat.world;

import com.ultimateimprovments.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovments.mechanics.security.anticheat.core.*;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * AirPlace — placing a block without targeting an existing block.
 * <p>
 * In vanilla Minecraft a block can only be placed while aiming at the face of
 * another block. The AirPlace hack places blocks in the air without looking at one.
 * <p>
 * Detection: ray-trace from the player's eyes — if no block is in the crosshair
 * within the vanilla interaction distance (5 blocks) — it's AirPlace.
 */
public class AirPlaceCheck extends AbstractCheck {

    private double maxTraceDistance;

    public AirPlaceCheck() {
        super("AirPlace", CheckCategory.WORLD);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxTraceDistance = getConfigDouble("max_trace_distance", 5.0);
    }

    @Override
    public void onReload() {
        loadConfig();
        maxTraceDistance = getConfigDouble("max_trace_distance", 5.0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;

        // Ray-trace what the player is looking at
        Block targetBlock = player.getTargetBlockExact((int) Math.ceil(maxTraceDistance));

        // If not looking at any block → air place
        if (targetBlock == null || targetBlock.getType() == Material.AIR
                || targetBlock.getType() == Material.CAVE_AIR
                || targetBlock.getType() == Material.VOID_AIR) {
            CheckResult result = flag(player, 3.0,
                    "AirPlace: placed without targeting a block face");
            AntiCheatManager.getInstance().handleResult(player, this, result);
            e.setCancelled(true);
        }
    }
}
