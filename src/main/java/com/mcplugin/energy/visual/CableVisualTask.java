package com.mcplugin.energy.visual;

import com.mcplugin.Main;
import com.mcplugin.cable.CableNetwork;
import com.mcplugin.cable.CableNode;
import com.mcplugin.cable.NodeType;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.LightningRod;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;

/**
 * Cable visual effects:
 * - Cables blink (10 ticks off / 10 ticks on) ONLY when energy is flowing through them.
 * - Batteries have electric spark particles when charged.
 * - No more smoke/lava/overload — cables don't store energy.
 */
public class CableVisualTask extends BukkitRunnable {

    private final Random random = new Random();
    private int globalTick = 0;

    @Override
    public void run() {

        globalTick++;

        FileConfiguration cfg = Main.getInstance().getConfig();

        boolean blinkEnabled = cfg.getBoolean("energy.cable.blink.enabled", true);
        int offTicks = cfg.getInt("energy.cable.blink.off_ticks", 10);
        int onTicks = cfg.getInt("energy.cable.blink.on_ticks", 10);
        int cycleLength = offTicks + onTicks;

        boolean batteryVisuals = cfg.getBoolean("energy.battery.visual.particles_enabled", true);

        for (CableNode node : CableNetwork.getAllNodes()) {

            Location loc = node.getLocation();
            World world = loc.getWorld();
            if (world == null) continue;

            Block block = loc.getBlock();
            Material blockType = block.getType();

            // =========================
            // BATTERY ELECTRICITY SPARKS
            // =========================
            if (node.getType() == NodeType.BATTERY && node.getEnergy() > 0 && batteryVisuals) {
                int batteryMax = cfg.getInt("energy.battery.max_energy", 100000);
                double fill = (double) node.getEnergy() / Math.max(batteryMax, 1);
                int sparkCount = Math.max(1, (int) (fill * 5));
                for (int i = 0; i < sparkCount; i++) {
                    double xOff = loc.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.8;
                    double yOff = loc.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.8;
                    double zOff = loc.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.8;
                    world.spawnParticle(
                            Particle.ELECTRIC_SPARK,
                            xOff, yOff, zOff,
                            0, 0, 0, 0,
                            0.01 + fill * 0.05
                    );
                }
            }

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