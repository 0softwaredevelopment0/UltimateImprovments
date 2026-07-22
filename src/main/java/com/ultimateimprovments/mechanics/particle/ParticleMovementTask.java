package com.ultimateimprovments.mechanics.particle;

import com.ultimateimprovments.util.ConsoleLogger;
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

        // Collision detection — ДО движения (по старым позициям)
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

        // Collision detection — ПОСЛЕ движения (новые позиции)
        // Двойная проверка ловит частицы, которые разминулись в процессе перемещения,
        // а также частицы, прилетевшие в один и тот же блок с разных направлений.
        checkCollisions();
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
            // Particles always pass through — never blocked by engine.
            // Если буфер полон (1000) И есть редстоун-сигнал → ускорить.
            if (data.speed < ParticleAcceleratorManager.MAX_SPEED
                    && ParticleAcceleratorManager.canEngineAccelerate(blockLoc)) {
                // Потребляем весь буфер (1000→0) и делаем скачок скорости
                ParticleAcceleratorManager.consumeEngineEnergy(blockLoc);
                data.speed = Math.min(ParticleAcceleratorManager.MAX_SPEED,
                        data.speed + ParticleAcceleratorManager.SPEED_INCREMENT);
                // Visual: мощный электрический разряд
                Location center = blockLoc.clone().add(0.5, 0.5, 0.5);
                blockLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, center, 20, 0.5, 0.5, 0.5, 0);
                blockLoc.getWorld().spawnParticle(Particle.FLASH, center, 1, 0, 0, 0, 0);
                blockLoc.getWorld().playSound(center, org.bukkit.Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 0.5f);
            }
            // Even if no acceleration, particle passes through normally
        } else if (blockType == ParticleAcceleratorManager.SENSOR) {
            // Запоминаем скорость последней пролетевшей частицы (для мультиметра)
            ParticleAcceleratorManager.setSensorLastSpeed(blockLoc, data.speed);
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
    // COLLISION DETECTION — proper swept-segment 3D test
    // =========================
    // Реальный 3D-тест минимального расстояния между двумя отрезками
    // [a.start, a.end] и [b.start, b.end] за один тик. Исправляет баги
    // старого скалярного sweep-test'а:
    //   - ложные коллизии для расходящихся перпендикулярных частиц,
    //   - ложные коллизии для параллельных частиц, плывущих рядом,
    //   - пропуски при “пролёте сквозь друга” в один тик.
    // Алгоритм: Real-Time Collision Detection (Christer Ericke),
    // clamped s,t ∈ [0,1].
    private void checkCollisions() {
        List<ParticleAcceleratorManager.ParticleData> particles = new ArrayList<>(ParticleAcceleratorManager.getActiveParticles());
        if (particles.size() < 2) return;

        // Pre-compute end-of-tick positions for each particle
        Map<UUID, EndState> endById = new HashMap<>();
        for (ParticleAcceleratorManager.ParticleData p : particles) {
            if (p.dead) {
                endById.put(p.id, new EndState(p.location, p.location));
                continue;
            }
            Location target = (p.path != null && p.pathIndex < p.path.size())
                    ? p.path.get(p.pathIndex)
                    : null;
            Location endLoc;
            if (target == null) {
                endLoc = p.location.clone();
            } else {
                double dx = target.getX() + 0.5 - p.location.getX();
                double dy = target.getY() + 0.5 - p.location.getY();
                double dz = target.getZ() + 0.5 - p.location.getZ();
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                double moveAmount = Math.min(p.speed, dist);
                if (dist <= 1e-9 || moveAmount <= 1e-9) {
                    endLoc = p.location.clone();
                } else {
                    double ratio = moveAmount / dist;
                    endLoc = new Location(p.location.getWorld(),
                            p.location.getX() + dx * ratio,
                            p.location.getY() + dy * ratio,
                            p.location.getZ() + dz * ratio);
                }
            }
            endById.put(p.id, new EndState(p.location, endLoc));
        }

        Set<UUID> toRemove = new HashSet<>();

        for (int i = 0; i < particles.size(); i++) {
            ParticleAcceleratorManager.ParticleData a = particles.get(i);
            if (a.dead || toRemove.contains(a.id)) continue;
            EndState ea = endById.get(a.id);
            if (ea == null) continue;

            for (int j = i + 1; j < particles.size(); j++) {
                ParticleAcceleratorManager.ParticleData b = particles.get(j);
                if (b.dead || toRemove.contains(b.id)) continue;
                EndState eb = endById.get(b.id);
                if (eb == null) continue;

                // Дешёвая предварительная проверка: если оба стационарны и далеко,
                // нет смысла считать полный тест.
                if (!ea.moves() && !eb.moves()) {
                    if (ea.start.distance(eb.start) > REACH_DISTANCE * 2.0) continue;
                }

                double minDist = closestDistanceBetweenSegments(
                        ea.start.getX(), ea.start.getY(), ea.start.getZ(),
                        ea.end.getX(),   ea.end.getY(),   ea.end.getZ(),
                        eb.start.getX(), eb.start.getY(), eb.start.getZ(),
                        eb.end.getX(),   eb.end.getY(),   eb.end.getZ());
                if (minDist <= REACH_DISTANCE) {
                    handleCollision(a, b);
                    toRemove.add(a.id);
                    toRemove.add(b.id);
                    break;
                }
            }
        }

        // Remove collided particles
        for (UUID id : toRemove) {
            ParticleAcceleratorManager.removeParticle(id);
        }
    }

    private record EndState(Location start, Location end) {
        boolean moves() {
            double dx = end.getX() - start.getX();
            double dy = end.getY() - start.getY();
            double dz = end.getZ() - start.getZ();
            return (dx * dx + dy * dy + dz * dz) > 1e-12;
        }
    }

    /**
     * Real-Time Collision Detection (Christer Ericke, §5.1.9).
     * Returns squared minimum distance for performance; caller decides comparison.
     */
    private static double closestDistanceBetweenSegments(
            double p1x, double p1y, double p1z,
            double q1x, double q1y, double q1z,
            double p2x, double p2y, double p2z,
            double q2x, double q2y, double q2z) {
        double d1x = q1x - p1x, d1y = q1y - p1y, d1z = q1z - p1z;
        double d2x = q2x - p2x, d2y = q2y - p2y, d2z = q2z - p2z;
        double rx = p1x - p2x, ry = p1y - p2y, rz = p1z - p2z;

        double a = d1x*d1x + d1y*d1y + d1z*d1z;
        double e = d2x*d2x + d2y*d2y + d2z*d2z;
        double f = d2x*rx + d2y*ry + d2z*rz;

        final double EPS = 1e-9;
        double s, t;

        if (a <= EPS && e <= EPS) {
            // both degenerate to points
            return Math.sqrt(rx*rx + ry*ry + rz*rz);
        }
        if (a <= EPS) {
            s = 0.0;
            t = clamp01(f / e);
        } else {
            double c = d1x*rx + d1y*ry + d1z*rz;
            if (e <= EPS) {
                t = 0.0;
                s = clamp01(-c / a);
            } else {
                double b = d1x*d2x + d1y*d2y + d1z*d2z;
                double denom = a * e - b * b;
                if (denom != 0.0) {
                    s = clamp01((b*f - c*e) / denom);
                } else {
                    s = 0.0; // parallel: any s works
                }
                t = (b*s + f) / e;
                if (t < 0.0) {
                    t = 0.0;
                    s = clamp01(-c / a);
                } else if (t > 1.0) {
                    t = 1.0;
                    s = clamp01((b - c) / a);
                }
            }
        }

        double c1x = p1x + d1x * s, c1y = p1y + d1y * s, c1z = p1z + d1z * s;
        double c2x = p2x + d2x * t, c2y = p2y + d2y * t, c2z = p2z + d2z * t;
        double dx = c1x - c2x, dy = c1y - c2y, dz = c1z - c2z;
        return Math.sqrt(dx*dx + dy*dy + dz*dz);
    }

    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
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
