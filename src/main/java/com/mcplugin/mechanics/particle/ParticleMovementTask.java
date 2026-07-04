package com.mcplugin.mechanics.particle;

import com.mcplugin.infrastructure.util.ConsoleLogger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Moves all active particles along their paths each tick.
 * Handles:
 * - Movement toward next target block
 * - Engine acceleration (speed increase + energy consumption)
 * - Sensor speed logging
 * - END_ROD particle trail spawning
 * - Collision detection between particles
 */
public class ParticleMovementTask extends BukkitRunnable {

    private static final double REACH_DISTANCE = 0.4; // distance to consider a target "reached"

    @Override
    public void run() {
        // Charge engines from cable network
        ParticleAcceleratorManager.chargeEngines();

        // Collision detection (check before moving)
        checkCollisions();

        // Move all particles
        ParticleAcceleratorManager.getActiveParticles()
                .removeIf(data -> {
                    if (data.dead) return true;
                    try {
                        tickParticle(data);
                    } catch (Exception e) {
                        ConsoleLogger.warn("[ParticleAccelerator] Tick error: " + e.getMessage());
                        data.dead = true;
                    }
                    return data.dead;
                });
    }

    // =========================
    // SINGLE PARTICLE TICK
    // =========================
    private void tickParticle(ParticleAcceleratorManager.ParticleData data) {
        if (data.path == null || data.path.isEmpty() || data.pathIndex >= data.path.size()) {
            // End of path — dissipate
            dissipateParticle(data);
            return;
        }

        Location target = data.path.get(data.pathIndex);
        Location current = data.location;
        World world = current.getWorld();
        if (world == null) { data.dead = true; return; }

        // Direction to target
        double dx = target.getX() + 0.5 - current.getX();
        double dy = target.getY() + 0.5 - current.getY();
        double dz = target.getZ() + 0.5 - current.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (distance <= REACH_DISTANCE) {
            // Reached the block — handle interaction
            handleBlockReached(data, target);

            // Advance to next target
            data.pathIndex++;
            if (data.pathIndex >= data.path.size()) {
                // End of path — dissipate after a brief moment
                dissipateParticle(data);
                return;
            }
            // Teleport to center of current block to avoid overshooting next target
            data.location = target.clone().add(0.5, 0.5, 0.5);
        } else {
            // Move toward target
            double moveAmount = Math.min(data.speed, distance);
            double ratio = moveAmount / distance;
            data.location.setX(current.getX() + dx * ratio);
            data.location.setY(current.getY() + dy * ratio);
            data.location.setZ(current.getZ() + dz * ratio);
        }

        // Spawn END_ROD particles
        spawnParticleVisual(data);

        // Update Marker entity position
        if (data.entity != null && !data.entity.isDead()) {
            data.entity.teleport(data.location);
        }
    }

    // =========================
    // BLOCK INTERACTION
    // =========================
    private void handleBlockReached(ParticleAcceleratorManager.ParticleData data, Location blockLoc) {
        Material blockType = blockLoc.getBlock().getType();

        if (blockType == ParticleAcceleratorManager.ENGINE) {
            // Try to consume energy and accelerate
            boolean success = ParticleAcceleratorManager.consumeEngineEnergy(blockLoc);
            if (success && data.speed < ParticleAcceleratorManager.MAX_SPEED) {
                data.speed = Math.min(ParticleAcceleratorManager.MAX_SPEED, data.speed + ParticleAcceleratorManager.SPEED_INCREMENT);
                // Visual: electric spark
                Location center = blockLoc.clone().add(0.5, 0.5, 0.5);
                blockLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, center, 5, 0.2, 0.2, 0.2, 0);
            }
        } else if (blockType == ParticleAcceleratorManager.SENSOR) {
            // Log speed for display — no action needed, just pass through
            double pct = data.speed / ParticleAcceleratorManager.MAX_SPEED * 100.0;
            // Visual: spark on sensor
            Location center = blockLoc.clone().add(0.5, 0.5, 0.5);
            blockLoc.getWorld().spawnParticle(Particle.END_ROD, center, 3, 0.1, 0.1, 0.1, 0.01);
        }
        // Ring/Injector: no special action
    }

    // =========================
    // VISUAL — END_ROD particles along the trail
    // =========================
    private void spawnParticleVisual(ParticleAcceleratorManager.ParticleData data) {
        if (data.speed <= 0) return;
        World world = data.location.getWorld();
        if (world == null) return;

        // Main particle at current position
        world.spawnParticle(Particle.END_ROD, data.location, 2, 0.05, 0.05, 0.05, 0.001);

        // Trail: spawn extra particles behind the particle based on speed
        int trailCount = Math.max(1, (int) Math.round(data.speed * 2));
        for (int i = 1; i <= trailCount; i++) {
            double trailFactor = i * 0.5 / data.speed;
            if (trailFactor > 1.0) break;

            // Calculate position behind the particle (opposite of movement direction)
            if (data.pathIndex < data.path.size()) {
                Location target = data.path.get(data.pathIndex);
                double dx = target.getX() + 0.5 - data.location.getX();
                double dy = target.getY() + 0.5 - data.location.getY();
                double dz = target.getZ() + 0.5 - data.location.getZ();
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist > 0) {
                    Location trailLoc = data.location.clone()
                            .subtract(dx / dist * 0.3 * trailFactor,
                                     dy / dist * 0.3 * trailFactor,
                                     dz / dist * 0.3 * trailFactor);
                    world.spawnParticle(Particle.END_ROD, trailLoc, 1, 0.02, 0.02, 0.02, 0);
                }
            }
        }
    }

    // =========================
    // COLLISION DETECTION
    // =========================
    private void checkCollisions() {
        List<ParticleAcceleratorManager.ParticleData> particles = new ArrayList<>(ParticleAcceleratorManager.getActiveParticles());
        if (particles.size() < 2) return;

        Set<UUID> toRemove = new HashSet<>();

        for (int i = 0; i < particles.size(); i++) {
            ParticleAcceleratorManager.ParticleData a = particles.get(i);
            if (a.dead || toRemove.contains(a.id)) continue;

            for (int j = i + 1; j < particles.size(); j++) {
                ParticleAcceleratorManager.ParticleData b = particles.get(j);
                if (b.dead || toRemove.contains(b.id)) continue;

                // Check distance (center to center)
                double dist = a.location.distance(b.location);
                if (dist > 1.0) continue; // too far

                // Collision detected!
                handleCollision(a, b);
                toRemove.add(a.id);
                toRemove.add(b.id);
                break;
            }
        }

        // Remove collided particles
        for (UUID id : toRemove) {
            ParticleAcceleratorManager.removeParticle(id);
        }
    }

    // =========================
    // COLLISION HANDLING
    // =========================
    private void handleCollision(ParticleAcceleratorManager.ParticleData a, ParticleAcceleratorManager.ParticleData b) {
        Location mid = a.location.clone().add(b.location).multiply(0.5);
        World world = mid.getWorld();
        if (world == null) return;

        // Calculate average speed as % of light speed
        double avgSpeedPct = ((a.speed + b.speed) / 2.0) / ParticleAcceleratorManager.MAX_SPEED * 100.0;

        // Check collision recipes
        Material result = ParticleCollisionHandler.checkRecipe(a.sourceMaterial, b.sourceMaterial, avgSpeedPct);

        // Mini explosion (no block damage)
        world.createExplosion(mid, 0.5f, false, false);
        world.spawnParticle(Particle.END_ROD, mid, 30, 0.5, 0.5, 0.5, 0.05);
        world.spawnParticle(Particle.FLASH, mid, 1, 0, 0, 0, 0);

        if (result != null && result != Material.AIR) {
            // Successful craft
            mid.getWorld().dropItemNaturally(mid, new org.bukkit.inventory.ItemStack(result));
            ConsoleLogger.info("[ParticleAccelerator] Collision craft: "
                    + a.sourceMaterial.name() + " + " + b.sourceMaterial.name()
                    + " @ " + String.format("%.1f", avgSpeedPct) + "% speed → " + result.name());
        } else {
            // Waste collision
            ConsoleLogger.info("[ParticleAccelerator] Waste collision: "
                    + a.sourceMaterial.name() + " + " + b.sourceMaterial.name()
                    + " @ " + String.format("%.1f", avgSpeedPct) + "% speed");
        }
    }

    // =========================
    // DISSIPATE — end of path
    // =========================
    private void dissipateParticle(ParticleAcceleratorManager.ParticleData data) {
        World world = data.location.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.END_ROD, data.location, 15, 0.3, 0.3, 0.3, 0.02);
        }
        data.dead = true;
        if (data.entity != null && !data.entity.isDead()) {
            data.entity.remove();
        }
    }
}
