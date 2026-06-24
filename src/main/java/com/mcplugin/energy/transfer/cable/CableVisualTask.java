package com.mcplugin.energy.transfer.cable;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.energy.transfer.cable.CableNetwork;
import com.mcplugin.energy.transfer.cable.CableNode;
import com.mcplugin.energy.transfer.cable.NodeType;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.LightningRod;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Cable visual effects:
 * - Cables blink (10 ticks off / 10 ticks on) ONLY when energy is flowing through them.
 * - Batteries have electric spark particles when charged.
 * - No more smoke/lava/overload — cables don't store energy.
 */
public class CableVisualTask extends BukkitRunnable {

    private int globalTick = 0;

    @Override
    public void run() {

        globalTick++;

        FileConfiguration cfg = Main.getInstance().getConfig();

        boolean blinkEnabled = cfg.getBoolean("energy.cable.blink.enabled", true);
        int offTicks = cfg.getInt("energy.cable.blink.off_ticks", 10);
        int onTicks = cfg.getInt("energy.cable.blink.on_ticks", 10);
        int cycleLength = offTicks + onTicks;

        for (CableNode node : CableNetwork.getAllNodes()) {

            Location loc = node.getLocation();
            World world = loc.getWorld();
            if (world == null) continue;

            Block block = loc.getBlock();
            Material blockType = block.getType();

            // =========================
            // CABLE BLINK — only for lightning rod cables
            // =========================
            if (blockType != Material.WAXED_LIGHTNING_ROD) continue;

            BlockData raw = block.getBlockData();
            if (!(raw instanceof LightningRod data)) continue;

            // Only CABLE nodes (not batteries)
            if (node.getType() != NodeType.CABLE) continue;

            boolean isFlowing = CableNetwork.isFlowing(loc);

            if (!isFlowing) {
                // Not flowing — ensure unpowered
                if (data.isPowered()) {
                    data.setPowered(false);
                    block.setBlockData(data, false);
                }
                continue;
            }

            // Blink: 10 ticks off, 10 ticks on, cycling
            if (!blinkEnabled) {
                // Solid on when flowing
                if (!data.isPowered()) {
                    data.setPowered(true);
                    block.setBlockData(data, false);
                }
                continue;
            }

            int cyclePos = globalTick % cycleLength;
            boolean powered = cyclePos >= offTicks;

            if (data.isPowered() != powered) {
                data.setPowered(powered);
                block.setBlockData(data, false);
            }
        }

        // Clear flowing state for next tick
        CableNetwork.clearFlowing();
    }
}