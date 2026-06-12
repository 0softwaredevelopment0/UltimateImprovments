package com.mcplugin.energy.visual;

import com.mcplugin.Main;
import com.mcplugin.cable.CableNetwork;
import com.mcplugin.cable.CableNode;
import com.mcplugin.cable.NodeType;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.LightningRod;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;

public class CableVisualTask extends BukkitRunnable {

    private final Random random = new Random();
    private int globalTick = 0;

    @Override
    public void run() {

        globalTick++;

        FileConfiguration cfg = Main.getInstance().getConfig();

        int smokeThreshold =
                cfg.getInt(
                        "energy.cable.visual.smoke_threshold",
                        4000
                );

        int lavaThreshold =
                cfg.getInt(
                        "energy.cable.visual.lava_threshold",
                        4700
                );

        int maxCableEnergy =
                cfg.getInt(
                        "energy.cable.max_energy",
                        5000
                );

        boolean batteryVisuals =
                cfg.getBoolean(
                        "energy.battery.visual.particles_enabled",
                        true
                );

        // =========================
        // OVERLOAD SOUND SETTINGS
        // =========================
        boolean soundEnabled =
                cfg.getBoolean(
                        "energy.cable.sound.enabled",
                        true
                );

        int smokeInterval =
                cfg.getInt(
                        "energy.cable.sound.smoke_interval",
                        15
                );

        int lavaInterval =
                cfg.getInt(
                        "energy.cable.sound.lava_interval",
                        8
                );

        int overloadInterval =
                cfg.getInt(
                        "energy.cable.sound.overload_interval",
                        3
                );

        String smokeSoundName =
                cfg.getString(
                        "energy.cable.sound.smoke.sound",
                        "BLOCK_FIRE_AMBIENT"
                );

        double smokeVolume =
                cfg.getDouble(
                        "energy.cable.sound.smoke.volume",
                        0.15
                );

        double smokePitchMin =
                cfg.getDouble(
                        "energy.cable.sound.smoke.pitch_min",
                        0.6
                );

        double smokePitchMax =
                cfg.getDouble(
                        "energy.cable.sound.smoke.pitch_max",
                        0.9
                );

        String lavaSoundName =
                cfg.getString(
                        "energy.cable.sound.lava.sound",
                        "BLOCK_FIRE_AMBIENT"
                );

        double lavaVolume =
                cfg.getDouble(
                        "energy.cable.sound.lava.volume",
                        0.4
                );

        double lavaPitchMin =
                cfg.getDouble(
                        "energy.cable.sound.lava.pitch_min",
                        0.9
                );

        double lavaPitchMax =
                cfg.getDouble(
                        "energy.cable.sound.lava.pitch_max",
                        1.3
                );

        String overloadSoundName =
                cfg.getString(
                        "energy.cable.sound.overload.sound",
                        "ENTITY_CREEPER_PRIMED"
                );

        double overloadVolume =
                cfg.getDouble(
                        "energy.cable.sound.overload.volume",
                        1.0
                );

        double overloadPitch =
                cfg.getDouble(
                        "energy.cable.sound.overload.pitch",
                        1.2
                );

        // =========================
        // RESOLVE SOUND KEYS (string-based namespaced, no enum)
        // =========================
        String smokeSoundKey = resolveSoundKey(smokeSoundName, "block.fire.ambient");
        String lavaSoundKey = resolveSoundKey(lavaSoundName, "block.fire.ambient");
        String overloadSoundKey = resolveSoundKey(overloadSoundName, "entity.creeper.primed");

        for (CableNode node : CableNetwork.getAllNodes()) {

            Location loc = node.getLocation();
            World world = loc.getWorld();
            if (world == null) continue;

            Block block = loc.getBlock();
            Material blockType = block.getType();

            int energy = node.getEnergy();

            // =========================
            // BATTERY ELECTRICITY SPARKS
            // =========================
            if (node.getType() == NodeType.BATTERY && energy > 0 && batteryVisuals) {
                    int batteryMax = cfg.getInt("energy.battery.max_energy", 10000);
                    double fill = (double) energy / Math.max(batteryMax, 1);
                    // More sparks at higher charge: 1–5 sparks, each tick
                    int sparkCount = Math.max(1, (int) (fill * 5));
                    // Random offset around the block
                    for (int i = 0; i < sparkCount; i++) {
                        double xOff = loc.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.8;
                        double yOff = loc.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.8;
                        double zOff = loc.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.8;
                        world.spawnParticle(
                                Particle.ELECTRIC_SPARK,
                                xOff, yOff, zOff,
                                0,  // count=0 for single particle with offset
                                0, 0, 0,
                                0.01 + fill * 0.05  // speed increases with charge
                        );
                    }
                }

            // =========================
            // CABLE VISUAL — only for lightning rod cables
            // =========================
            if (blockType != Material.WAXED_LIGHTNING_ROD) continue;

            BlockData raw = block.getBlockData();
            if (!(raw instanceof LightningRod data)) continue;

            int maxEnergy = node.getMaxEnergy();

            // =========================
            // PER-NODE SOUND PHASE OFFSET (0..interval-1)
            // Spreads sounds across nodes so they don't all fire at once
            // =========================
            int nodePhase = Math.abs(node.hashCode()) % 1000;

            // =========================
            // OVERLOAD STAGE: LAVA PARTICLES + PANIC SOUND
            // Before explosion (energy >= maxEnergy), spawn lava particles
            // =========================
            if (maxEnergy > 0 && energy >= maxEnergy) {
                // Lava particles pop upward before exploding
                for (int i = 0; i < 3; i++) {
                    world.spawnParticle(
                            Particle.LAVA,
                            loc.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
                            loc.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
                            loc.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
                            0, 0, 0.08, 0, 0.06
                    );
                }
                // Panic sound (e.g. creeper hiss) — rapid interval
                if (soundEnabled
                        && (globalTick + nodePhase) % overloadInterval == 0) {
                    world.playSound(
                            loc,
                            "minecraft:" + overloadSoundKey,
                            SoundCategory.HOSTILE,
                            (float) overloadVolume,
                            (float) overloadPitch
                    );
                }
            }

            // =========================
            // PRE-FIRE STAGE: LAVA + SMOKE + CRACKLE SOUND
            // (energy >= lavaThreshold but < maxEnergy)
            // =========================
            else if (energy >= lavaThreshold) {
                // Mix of smoke and lava particles — intensity increases with energy
                double panicLevel = (double) (energy - lavaThreshold)
                        / Math.max(maxEnergy - lavaThreshold, 1);
                int smokeCount = Math.max(1, (int) (1 + panicLevel * 3));
                int lavaCount = Math.max(1, (int) (1 + panicLevel * 2));

                for (int i = 0; i < smokeCount; i++) {
                    world.spawnParticle(
                            Particle.LARGE_SMOKE,
                            loc.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.5,
                            loc.getY() + 0.5 + random.nextDouble() * 0.3,
                            loc.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.5,
                            0, 0, 0.05, 0, 0.02
                    );
                }
                for (int i = 0; i < lavaCount; i++) {
                    world.spawnParticle(
                            Particle.LAVA,
                            loc.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
                            loc.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
                            loc.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
                            0, 0, 0, 0, 0.04
                    );
                }
                // Lava crackle sound — pitch rises with panicLevel
                if (soundEnabled
                        && (globalTick + nodePhase) % lavaInterval == 0) {
                    double pitch = lavaPitchMin
                            + (lavaPitchMax - lavaPitchMin) * panicLevel;
                    world.playSound(
                            loc,
                            "minecraft:" + lavaSoundKey,
                            SoundCategory.BLOCKS,
                            (float) lavaVolume,
                            (float) pitch
                    );
                }
            }

            // =========================
            // SMOKE STAGE + QUIET HUMM
            // (energy >= smokeThreshold but < lavaThreshold)
            // =========================
            else if (energy >= smokeThreshold) {
                // Smoke particles rising from the cable
                double severity = (double) (energy - smokeThreshold)
                        / Math.max(lavaThreshold - smokeThreshold, 1);
                int smokeCount = Math.max(1, (int) (1 + severity * 2));

                for (int i = 0; i < smokeCount; i++) {
                    world.spawnParticle(
                            Particle.SMOKE,
                            loc.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4,
                            loc.getY() + 0.5 + random.nextDouble() * 0.2,
                            loc.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4,
                            0, 0, 0.03, 0, 0.01
                    );
                }
                // Quiet humming sound — pitch rises with severity
                if (soundEnabled
                        && (globalTick + nodePhase) % smokeInterval == 0) {
                    double pitch = smokePitchMin
                            + (smokePitchMax - smokePitchMin) * severity;
                    world.playSound(
                            loc,
                            "minecraft:" + smokeSoundKey,
                            SoundCategory.BLOCKS,
                            (float) smokeVolume,
                            (float) pitch
                    );
                }
            }

            // =========================
            // OFF STATE (block data)
            // =========================
            if (energy <= 0) {
                if (data.isPowered()) {
                    data.setPowered(false);
                    block.setBlockData(data, false);
                }
                continue;
            }

            // =========================
            // NORMALIZED ENERGY LEVEL
            // =========================
            double level = Math.min(energy, maxEnergy) / (double) Math.max(maxEnergy, 1);

            boolean powered;

            if (maxEnergy > 0 && energy >= maxEnergy) {
                powered = true;
            } else {
                double frequency = 0.005 + (level * level * level) * 0.8;
                double phaseOffset = (Math.abs(node.hashCode()) % 1000) / 1000.0;
                double phase = (globalTick * frequency + phaseOffset) % 1.0;
                double dutyCycle = 0.2 + level * 0.4;
                powered = phase < dutyCycle;

                if (powered && level > 0.5 && random.nextDouble() < 0.05) {
                    powered = false;
                }
                if (!powered && random.nextDouble() < 0.01) {
                    powered = true;
                }
            }

            if (data.isPowered() != powered) {
                data.setPowered(powered);
                block.setBlockData(data, false);
            }
        }
    }

    // =========================
    // RESOLVE SOUND KEY (string-based namespaced key, no enum)
    // Handles both old Bukkit enum style and modern namespaced key format.
    // Returns just the path portion (e.g. "block.fire.ambient").
    // =========================
    private String resolveSoundKey(String name, String fallbackKey) {
        if (name == null || name.isEmpty()) {
            return fallbackKey;
        }
        // Strip "minecraft:" prefix if present
        String clean = name.contains(":") ? name.substring(name.lastIndexOf(':') + 1) : name;
        // Convert old Bukkit enum-style names to Minecraft key format
        // e.g. "BLOCK_FIRE_AMBIENT" -> "block.fire.ambient"
        clean = clean.toLowerCase().replace("_", ".");
        // Validate: ensure the path contains only valid Minecraft key characters
        if (clean.isEmpty() || !clean.matches("[a-z0-9._/-]+")) {
            Main.getInstance().getLogger().warning(
                    "[CableVisual] Invalid sound key: \"" + name + "\", using fallback"
            );
            return fallbackKey;
        }
        return clean;
    }
}