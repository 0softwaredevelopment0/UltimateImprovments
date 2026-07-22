package com.ultimateimprovments.mechanics.security.anticheat.movement;

import com.ultimateimprovments.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovments.mechanics.security.anticheat.core.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Clip — прохождение сквозь блоки (noclip/phase).
 * Детекция: телепортация через сплошные блоки за один тик.
 */
public class ClipCheck extends AbstractCheck {

    private double maxClipDistance;
    private boolean checkPassableBlocks;

    public ClipCheck() {
        super("Clip", CheckCategory.MOVEMENT);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxClipDistance = getConfigDouble("max_clip_distance", 10.0);
        checkPassableBlocks = getConfigBoolean("check_passable_blocks", true);
    }

    @Override
    public void onReload() {
        loadConfig();
        maxClipDistance = getConfigDouble("max_clip_distance", 10.0);
        checkPassableBlocks = getConfigBoolean("check_passable_blocks", true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;

        Location from = e.getFrom();
        Location to = e.getTo();

        // Large instantaneous teleport — check if there are solid blocks in between
        double distance = from.distance(to);
        if (distance > maxClipDistance) return; // Likely a legitimate teleport

        // Check if the player's new position is inside a solid block
        if (checkPassableBlocks) {
            Block feet = to.getBlock();
            Block head = to.clone().add(0, 1, 0).getBlock();
            if (isSolid(feet) && isSolid(head)) {
                CheckResult result = flag(player, 5.0,
                        "Clipped into solid blocks at " + to.getBlockX() + "," + to.getBlockY() + "," + to.getBlockZ());
                AntiCheatManager.getInstance().handleResult(player, this, result);
            }
        }

        PlayerData data = AntiCheatManager.getInstance().getOrCreatePlayerData(player);
        data.updatePosition(to, player.isOnGround());
    }

    private boolean isSolid(Block block) {
        Material mat = block.getType();
        return mat.isSolid() && mat.isOccluding();
    }
}
