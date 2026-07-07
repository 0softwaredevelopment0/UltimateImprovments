package com.mcplugin.energy.transfer.cable;

import com.mcplugin.core.Main;
import com.mcplugin.util.Materials;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.LightningRod;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

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

        CableNetwork.forEachNode(node -> {
            if (node.getType() != NodeType.CABLE) return;

            Location loc = node.getLocation();
            if (loc == null || loc.getWorld() == null) return;

            boolean isFlowing = CableNetwork.isFlowing(loc);
            Block block = loc.getBlock();
            if (block.getType() != Materials.WAXED_LIGHTNING_ROD) return;
            BlockData raw = block.getBlockData();
            if (!(raw instanceof LightningRod data)) return;

            if (!isFlowing) {
                if (data.isPowered()) {
                    data.setPowered(false);
                    block.setBlockData(data, false);
                }
                return;
            }

            if (!blinkEnabled) {
                if (!data.isPowered()) {
                    data.setPowered(true);
                    block.setBlockData(data, false);
                }
                return;
            }

            int cyclePos = globalTick % cycleLength;
            boolean powered = cyclePos >= offTicks;
            if (data.isPowered() != powered) {
                data.setPowered(powered);
                block.setBlockData(data, false);
            }
        });

        CableNetwork.clearFlowing();
    }
}
