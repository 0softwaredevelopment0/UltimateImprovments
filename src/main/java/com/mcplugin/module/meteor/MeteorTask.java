package com.mcplugin.module.meteor;

import com.mcplugin.util.ConsoleLogger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Marker;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * MeteorTask — вертикальное падение метеора (Marker entity).
 *
 * <p>Marker спавнится высоко в небе и телепортируется вниз каждый тик.
 * При ударе о землю: реальный взрыв + на месте падения спавнится
 * структура метеора из указанных блоков с рудой в центре.</p>
 */
public class MeteorTask extends BukkitRunnable {

    // ──────────── Fields ────────────
    private final World world;
    private final double startY, targetX, targetY, targetZ;
    private final int totalTicks;
    private int tick;
    private double currentY;

    private final int sphereRadius;
    private final List<BlockData> shellBlocks;
    private final Map<Material, Double> coreOres;

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
        this.shellBlocks = shellBlocks;
        this.coreOres = coreOres;
        this.random = new Random();

        // ── Создаём Marker на стартовой высоте ──
        Location startLoc = new Location(world, targetX, startY, targetZ);
        this.marker = (Marker) world.spawnEntity(startLoc, EntityType.MARKER);
        this.marker.setPersistent(false);
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

        // ── Позиция ──
        double progress = (double) tick / totalTicks;
        // Линейная интерполяция от startY к targetY — гарантированно достигает земли
        currentY = lerp(startY, targetY, progress);

        Location loc = new Location(world, targetX, currentY, targetZ);

        // ── Телепортируем Marker ──
        if (marker != null) {
            marker.teleport(loc);
        }

        // ── Партиклы ──
        double intensity = 0.5 + progress * 2.0;

        // Огненная аура
        world.spawnParticle(Particle.FLAME, loc, (int) (6 * intensity),
                0.8, 0.5, 0.8, 0.02);

        // Дым
        world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, loc, (int) (4 * intensity),
                0.5, 0.3, 0.5, 0.03);

        // Искры
        world.spawnParticle(Particle.LAVA, loc, (int) (2 * intensity),
                1.0, 0.5, 1.0, 0);

        // Trail сверху (дым остаётся выше)
        Location trailLoc = loc.clone().add(0, 1.0, 0);
        world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, trailLoc, (int) (3 * intensity),
                0.3, 0.5, 0.3, 0.05);

        // Яркая вспышка в последней трети
        if (progress > 0.7 && random.nextDouble() < 0.2) {
            world.spawnParticle(Particle.FLASH, loc, 1, 0, 0, 0, 0);
        }

        // ── Звук ──
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

        // Удаляем Marker
        removeMarker();

        Location impactLoc = new Location(world, targetX, targetY, targetZ);

        // ── Взрыв (sphereRadius * 10) ──
        float explosionPower = sphereRadius * 10.0f;
        world.createExplosion(impactLoc, explosionPower, true);

        // ── Партиклы взрыва ──
        world.spawnParticle(Particle.EXPLOSION_EMITTER, impactLoc, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.FLAME, impactLoc, 60, 4, 2, 4, 0.15);
        world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, impactLoc, 80, 4, 2, 4, 0.08);
        world.spawnParticle(Particle.LAVA, impactLoc, 30, 1, 0.5, 1, 0);
        world.spawnParticle(Particle.FLASH, impactLoc, 1, 0, 0, 0, 0);

        // Восходящий столб дыма
        for (int i = 0; i < 5; i++) {
            double offsetY = i * 1.5;
            Location smokeLoc = impactLoc.clone().add(0, offsetY, 0);
            world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, smokeLoc,
                    10 + i * 5, 1.5 - i * 0.2, 0.3, 1.5 - i * 0.2, 0.05);
            world.spawnParticle(Particle.FLAME, smokeLoc, 5, 0.5, 0.3, 0.5, 0.02);
        }

        // ── Звуки ──
        world.playSound(impactLoc, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.6f);
        world.playSound(impactLoc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 2.0f, 0.4f);
        world.playSound(impactLoc, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 2.0f, 0.8f);

        // ── Спавним структуру метеора (сфера из блоков + центральная руда) ──
        spawnMeteorStructure(impactLoc);

        // ── Кратер ──
        spawnCrater(impactLoc);

        ConsoleLogger.info("[Meteor] Impact at " + impactLoc.getBlockX() + " " + impactLoc.getBlockY() + " " + impactLoc.getBlockZ()
                + " in " + world.getName());

        if (onComplete != null) onComplete.run();
        cancel();
    }

    /**
     * Спавнит сферическую структуру метеора из shellBlocks + случайная руда в центре.
     */
    private void spawnMeteorStructure(Location center) {
        // Выбираем центральную руду по шансам
        Material coreOre = pickCoreOre();

        int r = sphereRadius;

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist > r + 0.5) continue;

                    Block b = center.getBlock().getRelative(dx, dy, dz);

                    // Не заменяем воздух/жидкости если блок уже был твёрдым?
                    // Метеор ЗАМЕНЯET существующие блоки (сила удара)

                    // Центр — руда
                    if (dist <= 0.5) {
                        b.setType(coreOre);
                        continue;
                    }

                    // Оболочка — случайный блок из display_blocks
                    BlockData blockData = shellBlocks.get(random.nextInt(shellBlocks.size()));
                    b.setType(blockData.getMaterial());
                }
            }
        }
    }

    /**
     * Выбирает руду из core_ores по шансам (weighted random).
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
     * Создаёт кратер вокруг места падения.
     */
    private void spawnCrater(Location impactLoc) {
        int craterR = sphereRadius * 2;

        for (int dx = -craterR; dx <= craterR; dx++) {
            for (int dz = -craterR; dz <= craterR; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > craterR + 0.5) continue;

                Block b = world.getBlockAt(impactLoc.clone().add(dx, 0, dz));
                Block above = b.getRelative(BlockFace.UP);

                // Центр: magma + fire
                if (dist <= craterR * 0.35) {
                    if (!b.isEmpty()) {
                        if (random.nextDouble() < 0.5) b.setType(Material.MAGMA_BLOCK);
                        else if (random.nextDouble() < 0.3) b.setType(Material.OBSIDIAN);
                    }
                    if (above.isEmpty() && random.nextDouble() < 0.6) {
                        above.setType(Material.FIRE);
                    }
                }
                // Среднее кольцо: blackstone
                else if (dist <= craterR * 0.7) {
                    if (random.nextDouble() < 0.25 && !b.isEmpty()) {
                        b.setType(Material.BLACKSTONE);
                    }
                }
                // Внешнее кольцо: fire
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
    }

    private void removeMarker() {
        if (marker != null && marker.isValid()) {
            marker.remove();
            marker = null;
        }
    }

    // ──────────── Helpers ────────────

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
