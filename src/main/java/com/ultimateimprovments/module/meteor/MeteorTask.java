package com.ultimateimprovments.module.meteor;

import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Marker;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * MeteorTask — vertical meteor fall (Marker entity).
 *
 * <p>The Marker spawns high in the sky and is teleported down every tick.
 * On ground impact: a real explosion + a meteor structure made of the specified
 * blocks with ore in the center spawns at the impact point.</p>
 */
public class MeteorTask extends BukkitRunnable {

    // ──────────── Fields ────────────
    private final World world;
    private final double startY, targetX, targetY, targetZ;
    private final int totalTicks;
    private int tick;
    private double currentY;

    private final int sphereRadius;
    private final float explosionPower;
    private final List<BlockData> shellBlocks;
    private final Map<Material, Double> coreOres;
    private final Material coreOre;

    private final List<BlockDisplay> displays = new ArrayList<>();
    private final List<Vector> displayOffsets = new ArrayList<>();

    private Marker marker;
    private boolean impactDone = false;

    private final Random random;

    private Runnable onComplete;

    // ──────────── Constructor ────────────
    public MeteorTask(World world,
                      double startY,
                      double targetX, double targetY, double targetZ,
                      int totalTicks,
                      int sphereRadius,
                      float explosionPower,
                      List<BlockData> shellBlocks,
                      Map<Material, Double> coreOres) {
        this.world = world;
        this.startY = startY;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
        this.currentY = startY;
        this.totalTicks = totalTicks;
        this.sphereRadius = sphereRadius;
        this.explosionPower = explosionPower;
        this.shellBlocks = shellBlocks;
        this.coreOres = coreOres;
        this.random = new Random();
        // Pick the core ore once so the falling visual matches what actually lands.
        this.coreOre = pickCoreOre();

        // ── Create the Marker at the start height ──
        Location startLoc = new Location(world, targetX, startY, targetZ);
        this.marker = (Marker) world.spawnEntity(startLoc, EntityType.MARKER);
        this.marker.setPersistent(false);

        // ── Spawn the falling block visualizers (a glowing sphere of blocks) ──
        spawnBlockVisualizers(startLoc);
    }

    // ──────────── Main loop ────────────
    @Override
    public void run() {
        if (impactDone) {
            cancel();
            return;
        }

        if (tick >= totalTicks) {
            try {
                impact();
            } catch (Exception e) {
                ConsoleLogger.warn("[Meteor] Exception during impact: " + e.getMessage());
                removeMarker();
                if (onComplete != null) onComplete.run();
                cancel();
            }
            return;
        }

        try {
            tickAnimation();
        } catch (Exception e) {
            ConsoleLogger.warn("[Meteor] Exception during animation tick " + tick + ": " + e.getMessage());
            safeCleanup();
            if (onComplete != null) onComplete.run();
            cancel();
        }
    }

    private void tickAnimation() {
        tick++;

        // ── Position ──
        double progress = (double) tick / totalTicks;
        // Linear interpolation from startY to targetY — guaranteed to reach the ground
        currentY = lerp(startY, targetY, progress);

        Location loc = new Location(world, targetX, currentY, targetZ);

        // ── Teleport the Marker ──
        if (marker != null) {
            marker.teleport(loc);
        }

        // ── Move the block visualizers with the marker ──
        moveDisplays(loc);

        // ── Particles ──
        double intensity = 0.5 + progress * 2.0;
        // Spread the fire across the whole visible sphere, not just the center
        double fireSpread = sphereRadius + 0.6;

        // Fire aura
        world.spawnParticle(Particle.FLAME, loc, (int) (6 * intensity),
                fireSpread, fireSpread * 0.6, fireSpread, 0.02);

        // Smoke
        world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, loc, (int) (4 * intensity),
                fireSpread, fireSpread * 0.4, fireSpread, 0.03);

        // Sparks
        world.spawnParticle(Particle.LAVA, loc, (int) (2 * intensity),
                fireSpread, fireSpread * 0.6, fireSpread, 0);

        // Trail above (smoke stays higher)
        Location trailLoc = loc.clone().add(0, 1.0, 0);
        world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, trailLoc, (int) (3 * intensity),
                0.3, 0.5, 0.3, 0.05);

        // Bright flash in the last third
        if (progress > 0.7 && random.nextDouble() < 0.2) {
            world.spawnParticle(Particle.FLASH, loc, 1, 0, 0, 0, 0);
        }

        // ── Sound ──
        float volume = 0.3f + (float) progress * 0.7f;
        float pitch = 0.3f + (float) progress * 0.6f;
        world.playSound(loc, Sound.ENTITY_GHAST_SHOOT, volume * 0.5f, pitch);

        if (progress > 0.6) {
            float rumblePitch = 0.1f + (float) (1.0 - progress) * 0.3f;
            world.playSound(loc, Sound.BLOCK_LAVA_AMBIENT, volume * 0.3f, rumblePitch);
        }
        if (progress > 0.8) {
            world.playSound(loc, Sound.ENTITY_CREEPER_PRIMED, 0.4f, 0.5f);
        }
    }

    // ──────────── Impact ────────────
    private void impact() {
        impactDone = true;

        // Remove the Marker and the falling visualizers (real blocks replace them)
        removeMarker();
        removeDisplays();

        Location impactLoc = new Location(world, targetX, targetY, targetZ);

        // ── Explosion (configurable power, TNT = 4.0) ──
        world.createExplosion(impactLoc, explosionPower, true);

        // ── Explosion particles ──
        world.spawnParticle(Particle.EXPLOSION_EMITTER, impactLoc, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.FLAME, impactLoc, 60, 4, 2, 4, 0.15);
        world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, impactLoc, 80, 4, 2, 4, 0.08);
        world.spawnParticle(Particle.LAVA, impactLoc, 30, 1, 0.5, 1, 0);
        world.spawnParticle(Particle.FLASH, impactLoc, 1, 0, 0, 0, 0);

        // Rising smoke column
        for (int i = 0; i < 5; i++) {
            double offsetY = i * 1.5;
            Location smokeLoc = impactLoc.clone().add(0, offsetY, 0);
            world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, smokeLoc,
                    10 + i * 5, 1.5 - i * 0.2, 0.3, 1.5 - i * 0.2, 0.05);
            world.spawnParticle(Particle.FLAME, smokeLoc, 5, 0.5, 0.3, 0.5, 0.02);
        }

        // ── Sounds ──
        world.playSound(impactLoc, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.6f);
        world.playSound(impactLoc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 2.0f, 0.4f);
        world.playSound(impactLoc, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 2.0f, 0.8f);

        // ── Spawn the meteor structure (block sphere + central ore) ──
        spawnMeteorStructure(impactLoc);

        // ── Crater ──
        spawnCrater(impactLoc);

        ConsoleLogger.info("[Meteor] Impact at " + impactLoc.getBlockX() + " " + impactLoc.getBlockY() + " " + impactLoc.getBlockZ()
                + " in " + world.getName());

        if (onComplete != null) onComplete.run();
        cancel();
    }

    /**
     * Spawns the spherical meteor structure from shellBlocks + a random ore in the center.
     */
    private void spawnMeteorStructure(Location center) {
        // Use the ore already picked at spawn so the landing matches the visual
        Material coreOre = this.coreOre;

        int r = sphereRadius;

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist > r + 0.5) continue;

                    Block b = center.getBlock().getRelative(dx, dy, dz);

                    // Don't replace air/liquids if the block was already solid?
                    // The meteor REPLACES existing blocks (impact force)

                    // Center — ore
                    if (dist <= 0.5) {
                        b.setType(coreOre);
                        continue;
                    }

                    // Shell — a random block from display_blocks
                    BlockData blockData = shellBlocks.get(random.nextInt(shellBlocks.size()));
                    b.setType(blockData.getMaterial());
                }
            }
        }
    }

    /**
     * Picks the ore from core_ores by chances (weighted random).
     */
    private Material pickCoreOre() {
        if (coreOres == null || coreOres.isEmpty()) {
            return Material.DEEPSLATE_DIAMOND_ORE;
        }

        double totalWeight = 0;
        for (double w : coreOres.values()) {
            totalWeight += w;
        }

        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0;

        for (Map.Entry<Material, Double> entry : coreOres.entrySet()) {
            cumulative += entry.getValue();
            if (roll <= cumulative) {
                return entry.getKey();
            }
        }

        // Fallback
        return coreOres.keySet().iterator().next();
    }

    /**
     * Creates a crater around the impact point.
     */
    private void spawnCrater(Location impactLoc) {
        int craterR = sphereRadius * 2;

        for (int dx = -craterR; dx <= craterR; dx++) {
            for (int dz = -craterR; dz <= craterR; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > craterR + 0.5) continue;

                Block b = world.getBlockAt(impactLoc.clone().add(dx, 0, dz));
                Block above = b.getRelative(BlockFace.UP);

                // Center: magma + fire
                if (dist <= craterR * 0.35) {
                    if (!b.isEmpty()) {
                        if (random.nextDouble() < 0.5) b.setType(Material.MAGMA_BLOCK);
                        else if (random.nextDouble() < 0.3) b.setType(Material.OBSIDIAN);
                    }
                    if (above.isEmpty() && random.nextDouble() < 0.6) {
                        above.setType(Material.FIRE);
                    }
                }
                // Middle ring: blackstone
                else if (dist <= craterR * 0.7) {
                    if (random.nextDouble() < 0.25 && !b.isEmpty()) {
                        b.setType(Material.BLACKSTONE);
                    }
                }
                // Outer ring: fire
                else if (dist <= craterR) {
                    if (above.isEmpty() && random.nextDouble() < 0.15) {
                        above.setType(Material.FIRE);
                    }
                }
            }
        }
    }

    // ──────────── Cleanup ────────────

    public void setOnComplete(Runnable onComplete) {
        this.onComplete = onComplete;
    }

    private void safeCleanup() {
        removeMarker();
        removeDisplays();
    }

    /**
     * Removes the Marker entity without running the impact logic.
     * Called from {@code MeteorModule.onDisable()} so markers are not leaked
     * on reload/disable (cancelling the task alone does not despawn the entity).
     */
    public void cleanup() {
        removeMarker();
        removeDisplays();
    }

    private void removeMarker() {
        if (marker != null && marker.isValid()) {
            marker.remove();
            marker = null;
        }
    }

    /**
     * Removes all block visualizer entities.
     */
    private void removeDisplays() {
        for (BlockDisplay display : displays) {
            if (display.isValid()) {
                display.remove();
            }
        }
        displays.clear();
        displayOffsets.clear();
    }

    /**
     * Spawns a glowing sphere of BlockDisplay entities that visualize the
     * falling meteor (shell blocks + the central ore).
     */
    private void spawnBlockVisualizers(Location center) {
        int r = sphereRadius;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist > r + 0.5) continue;

                    BlockData data = dist <= 0.5
                            ? coreOre.createBlockData()
                            : shellBlocks.get(random.nextInt(shellBlocks.size()));

                    Location loc = center.clone().add(dx, dy, dz);
                    BlockDisplay display = world.spawn(loc, BlockDisplay.class, d -> d.setBlock(data));
                    display.setPersistent(false);
                    // Fullbright — makes the rock look molten/burning
                    display.setBrightness(new Display.Brightness(15, 15));

                    displays.add(display);
                    displayOffsets.add(new Vector(dx, dy, dz));
                }
            }
        }
    }

    /**
     * Teleports the block visualizers so they follow the marker each tick.
     */
    private void moveDisplays(Location center) {
        for (int i = 0; i < displays.size(); i++) {
            BlockDisplay display = displays.get(i);
            if (!display.isValid()) continue;
            Vector offset = displayOffsets.get(i);
            display.teleport(new Location(world,
                    center.getX() + offset.getX(),
                    center.getY() + offset.getY(),
                    center.getZ() + offset.getZ()));
        }
    }

    // ──────────── Helpers ────────────

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
