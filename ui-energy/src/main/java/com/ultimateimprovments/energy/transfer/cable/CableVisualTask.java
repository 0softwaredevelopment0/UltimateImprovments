package com.ultimateimprovments.energy.transfer.cable;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.Materials;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.LightningRod;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Cable flow visuals. Runs every 5 ticks and drives the powered state of the
 * straight cable (WAXED_LIGHTNING_ROD):
 * <ul>
 *   <li>no flow recorded since the last pass → unpowered;</li>
 *   <li>flow + blink off → steady powered;</li>
 *   <li>flow + blink on → powered during the on-phase of the cycle, where
 *       {@code off_ticks}/{@code on_ticks} are REAL ticks (before the rework
 *       they were counted in task runs, i.e. ×40).</li>
 * </ul>
 */
public class CableVisualTask extends BukkitRunnable {

    @Override
    public void run() {
        FileConfiguration cfg = Main.getInstance().getConfig();
        boolean blinkEnabled = cfg.getBoolean("energy.cable.blink.enabled", true);
        int offTicks = cfg.getInt("energy.cable.blink.off_ticks", 10);
        int onTicks = cfg.getInt("energy.cable.blink.on_ticks", 10);
        int cycleLength = Math.max(1, offTicks + onTicks);
        int cyclePos = Bukkit.getCurrentTick() % cycleLength;
        boolean blinkPhase = cyclePos >= offTicks;

        CableNetwork.forEachNode(node -> {
            if (node.getType() != NodeType.CABLE) return;

            Location loc = node.getLocation();
            if (loc == null || loc.getWorld() == null) return;

            boolean isFlowing = CableNetwork.isFlowing(loc);
            Block block = loc.getBlock();
            if (block.getType() != Materials.WAXED_LIGHTNING_ROD) return;
            BlockData raw = block.getBlockData();
            if (!(raw instanceof LightningRod data)) return;

            boolean powered = isFlowing && (!blinkEnabled || blinkPhase);
            if (data.isPowered() != powered) {
                data.setPowered(powered);
                block.setBlockData(data, false);
            }
        });

        // Flow marks auto-expire — just drop the stale ones to bound memory.
        CableNetwork.pruneFlowing();
    }
}
