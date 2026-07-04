package com.mcplugin.infrastructure.modules.meteor;

import com.mcplugin.infrastructure.util.ConsoleLogger;
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
import org.bukkit.util.Transformation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * MeteorTask — анимация падения метеора с высококачественным визуалом.
 *
 * <p>Двухслойная структура:</p>
 * <ul>
 *   <li><b>Внешняя оболочка</b> — тёмные камни (deepslate, blackstone) с масштабом 0.3–0.7</li>
 *   <li><b>Внутреннее ядро</b> — светящиеся блоки (shroomlight, magma, glowstone) с масштабом 0.15–0.3</li>
 *   <li><b>Пламенная корона</b> — netherrack + огонь сверху, красные/оранжевые блоки</li>
 * </ul>
 *
 * <p>Эффекты:</p>
 * <ul>
 *   <li>Максимальная яркость Display (sky=15, block=15) — блоки светятся в темноте</li>
 *   <li>Полноценное 3D-кувыркание (случайная ось вращения, меняется каждые 10 тиков)</li>
 *   <li>Хвост из искр, дыма и огня позади метеора</li>
 *   <li>Огненные частицы, разлетающиеся от поверхности</li>
 *   <li>Звук нарастающего гула с приближением к земле</li>
 *   <li>Эффект дрожи (случайное смещение) в последней трети полёта</li>
 * </ul>
 */
public class MeteorTask extends BukkitRunnable {

    // ──────────── Const ────────────
    private static final BlockData FIRE_DATA = Material.FIRE.createBlockData();
    private static final BlockData SHROOMLIGHT_DATA = Material.SHROOMLIGHT.createBlockData();
    private static final BlockData MAGMA_DATA = Material.MAGMA_BLOCK.createBlockData();
    private static final BlockData GLOWSTONE_DATA = Material.GLOWSTONE.createBlockData();
    private static final BlockData NETHERRACK_DATA = Material.NETHERRACK.createBlockData();

    // ──────────── Fields ────────────
    private final World world;
    private final double startX, startY, startZ;
    private final double targetX, targetY, targetZ;
    private final int totalTicks;
    private int tick;
    private double prevX, prevY, prevZ; // для trail

    private final UUID centerId;
    private final List<UUID> displayIds = new ArrayList<>();
    private final List<double[]> offsets = new ArrayList<>();  // [dx, dy, dz]
    private final List<Float> scales = new ArrayList<>();      // индивидуальный масштаб
    private final List<Integer> blockLayers = new ArrayList<>(); // 0=ядро, 1=оболочка, 2=корона

    private final double sphereRadius;
    private final double explosionRadius;
    private final boolean explosionFire;
    private final Material oreBlock;
    private final List<BlockData> configBlocks;

    private final Random random;

    // 3D rotation state
    private double rotX = 0, rotY = 0, rotZ = 0;
    private double axisX, axisY, axisZ;  // случайная ось
    private int axisChangeTimer = 0;

    private Runnable onComplete;

    // ──────────── Constructor ────────────
    public MeteorTask(World world,
                      double startX, double startY, double startZ,
                      double targetX, double targetY, double targetZ,
                      int totalTicks,
                      double sphereRadius,
                      List<BlockData> configBlocks,
                      double explosionRadius, boolean explosionFire,
                      Material oreBlock) {
        this.world = world;
        this.startX = startX;
        this.startY = startY;
        this.startZ = startZ;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
        this.prevX = startX;
        this.prevY = startY;
        this.prevZ = startZ;
        this.totalTicks = totalTicks;
        this.sphereRadius = sphereRadius;
        this.configBlocks = configBlocks;
        this.explosionRadius = explosionRadius;
        this.explosionFire = explosionFire;
        this.oreBlock = oreBlock;
        this.random = new Random();

        // ── Случайная ось вращения ──
        randomizeAxis();

        // ── Генерируем сферу ──
        int numBlocks = (int) Math.round(12 + sphereRadius * 18);
        Location centerLoc = new Location(world, startX, startY, startZ);

        // Создаём Marker — невидимый центр
        Marker marker = (Marker) world.spawnEntity(centerLoc, EntityType.MARKER);
        marker.setPersistent(false);
        this.centerId = marker.getUniqueId();

        // Создаём BlockDisplay
        for (int i = 0; i < numBlocks; i++) {
            // Случайная точка на сфере
            double theta = random.nextDouble() * 2 * Math.PI;
            double phi = Math.acos(2 * random.nextDouble() - 1);
            double dx = sphereRadius * Math.sin(phi) * Math.cos(theta);
            double dy = sphereRadius * Math.sin(phi) * Math.sin(theta);
            double dz = sphereRadius * Math.cos(phi);
            offsets.add(new double[]{dx, dy, dz});

            // Определяем слой по радиусу от поверхности
            double r = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double normalizedR = r / sphereRadius; // 0..1

            int layer;
            float scale;
            BlockData block;

            if (normalizedR < 0.4) {
                // Внутреннее ядро — светящиеся блоки
                layer = 0;
                scale = 0.15f + random.nextFloat() * 0.2f;
                block = pickCoreBlock();
            } else if (normalizedR < 0.85) {
                // Внешняя оболочка — тёмные камни + случайный блок из конфига
                layer = 1;
                scale = 0.35f + random.nextFloat() * 0.35f;
                if (random.nextDouble() < 0.15) {
                    block = MAGMA_DATA; // прожилки магмы
                } else {
                    block = configBlocks.get(random.nextInt(configBlocks.size()));
                }
            } else {
                // Пламенная корона — огонь сверху
                layer = 2;
                scale = 0.15f + random.nextFloat() * 0.25f;
                block = pickCoronaBlock();
            }

            blockLayers.add(layer);
            scales.add(scale);

            Location blockLoc = centerLoc.clone().add(dx, dy, dz);
            BlockDisplay display = (BlockDisplay) world.spawnEntity(blockLoc, EntityType.BLOCK_DISPLAY);
            display.setPersistent(false);
            display.setInterpolationDuration(0);
            display.setBlock(block);

            // ── Максимальная яркость — блоки светятся даже днём ──
            display.setBrightness(new Display.Brightness(15, 15));

            // ── Трансформация ──
            Transformation t = display.getTransformation();
            t.getScale().set(scale, scale, scale);
            display.setTransformation(t);

            display.setTeleportDuration(1);
            displayIds.add(display.getUniqueId());
        }
    }

    // ──────────── Main loop ────────────
    @Override
    public void run() {
        if (tick >= totalTicks) {
            impact();
            cancel();
            return;
        }
        tick++;

        // ── Позиция ──
        double progress = (double) tick / totalTicks;
        double cx = lerp(startX, targetX, progress);
        double cy = lerp(startY, targetY, progress);
        double cz = lerp(startZ, targetZ, progress);
        Location centerLoc = new Location(world, cx, cy, cz);

        Marker marker = (Marker) world.getEntity(centerId);
        if (marker == null) { cancel(); return; }
        marker.teleport(centerLoc);

        // ── Продвинутое 3D вращение ──
        axisChangeTimer++;
        if (axisChangeTimer > 10) {
            double instability = 1.0 + progress * 2.0; // быстрее к концу
            rotX += axisX * instability;
            rotY += axisY * instability;
            rotZ += axisZ * instability;
            // Меняем ось каждые 10-20 тиков
            if (axisChangeTimer > 10 + random.nextInt(10)) {
                randomizeAxis();
                axisChangeTimer = 0;
            }
        }

        // ── Эффект дрожи в последней трети ──
        double shake = 0;
        if (progress > 0.65) {
            shake = (progress - 0.65) / 0.35 * sphereRadius * 0.15;
        }

        float cosX = (float) Math.cos(rotX);
        float sinX = (float) Math.sin(rotX);
        float cosY = (float) Math.cos(rotY);
        float sinY = (float) Math.sin(rotY);
        float cosZ = (float) Math.cos(rotZ);
        float sinZ = (float) Math.sin(rotZ);

        // Обновляем позиции блоков сферы
        for (int i = 0; i < displayIds.size(); i++) {
            BlockDisplay display = (BlockDisplay) world.getEntity(displayIds.get(i));
            if (display == null) continue;

            double[] off = offsets.get(i);
            int layer = blockLayers.get(i);

            // 3D вращение: матрица XYZ
            float x = (float) off[0];
            float y = (float) off[1];
            float z = (float) off[2];

            // Y
            float tx = x * cosY - z * sinY;
            float tz = x * sinY + z * cosY;
            x = tx; z = tz;
            // X
            float ty = y * cosX - z * sinX;
            tz = y * sinX + z * cosX;
            y = ty; z = tz;
            // Z
            tx = x * cosZ - y * sinZ;
            ty = x * sinZ + y * cosZ;
            x = tx; y = ty;

            // Дрожь
            double sx = shake * (random.nextDouble() - 0.5) * 2;
            double sy = shake * (random.nextDouble() - 0.5) * 2;
            double sz = shake * (random.nextDouble() - 0.5) * 2;

            Location blockLoc = centerLoc.clone().add(x + sx, y + sy, z + sz);
            display.teleport(blockLoc);

            // Пламенная корона мерцает
            if (layer == 2 && random.nextDouble() < 0.1) {
                display.setBlock(pickCoronaBlock());
            }
        }

        // ── Партиклы ──
        spawnParticles(centerLoc, progress, cx, cy, cz);

        // ── Звук ──
        float volume = 0.3f + (float) progress * 0.7f;
        float pitch = 0.3f + (float) progress * 0.6f;
        world.playSound(centerLoc, Sound.ENTITY_GHAST_SHOOT, volume * 0.5f, pitch);

        // Низкий гул в последней трети
        if (progress > 0.6) {
            float rumblePitch = 0.1f + (float) (1.0 - progress) * 0.3f;
            world.playSound(centerLoc, Sound.BLOCK_LAVA_AMBIENT, volume * 0.3f, rumblePitch);
        }
        if (progress > 0.8) {
            world.playSound(centerLoc, Sound.ENTITY_CREEPER_PRIMED, 0.4f, 0.5f);
        }

        prevX = cx;
        prevY = cy;
        prevZ = cz;
    }

    // ──────────── Particles ────────────

    private void spawnParticles(Location loc, double progress, double cx, double cy, double cz) {
        double intensity = 0.5 + progress * 1.5; // больше к концу

        // 1. Огненная аура вокруг метеора
        world.spawnParticle(Particle.FLAME, loc, (int) (8 * intensity),
                sphereRadius * 1.2, sphereRadius * 1.2, sphereRadius * 1.2, 0.02);

        // 2. Дым от поверхности
        world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, loc, (int) (6 * intensity),
                sphereRadius * 1.5, sphereRadius * 0.5, sphereRadius * 1.5, 0.03);

        // 3. Искры, отлетающие от поверхности
        world.spawnParticle(Particle.LAVA, loc, (int) (3 * intensity),
                sphereRadius * 1.8, sphereRadius * 1.8, sphereRadius * 1.8, 0);

        // 4. Trail — дым/огонь позади метеора
        double trailX = prevX + (cx - prevX) * -0.5;
        double trailY = prevY + (cy - prevY) * -0.5;
        double trailZ = prevZ + (cz - prevZ) * -0.5;
        Location trailLoc = new Location(world, trailX, trailY, trailZ);

        world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, trailLoc, (int) (5 * intensity),
                0.8, 0.8, 0.8, 0.05);
        world.spawnParticle(Particle.FLAME, trailLoc, (int) (3 * intensity),
                0.5, 0.5, 0.5, 0.02);

        // 5. Электрические искры (случайно)
        if (random.nextDouble() < 0.3 * intensity) {
            world.spawnParticle(Particle.CRIT, loc, 5,
                    sphereRadius * 1.5, sphereRadius * 1.5, sphereRadius * 1.5, 0.1);
        }

        // 6. Яркая вспышка в последний момент
        if (progress > 0.9) {
            world.spawnParticle(Particle.END_ROD, loc, 2,
                    sphereRadius * 0.5, sphereRadius * 0.5, sphereRadius * 0.5, 0.05);
            world.spawnParticle(Particle.FLASH, loc, 1, 0, 0, 0, 0);
        }
    }

    // ──────────── Impact ────────────

    private void impact() {
        // Удаляем старые entity
        for (UUID id : displayIds) {
            var e = world.getEntity(id);
            if (e != null) e.remove();
        }
        Marker marker = (Marker) world.getEntity(centerId);
        if (marker != null) marker.remove();

        Location impactLoc = new Location(world, targetX, targetY, targetZ);

        // ── Гигантский взрыв ──
        world.createExplosion(impactLoc, (float) explosionRadius, explosionFire);

        // ── Партиклы взрыва ──
        world.spawnParticle(Particle.EXPLOSION_EMITTER, impactLoc, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.FLAME, impactLoc, 60, 4, 2, 4, 0.15);
        world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, impactLoc, 80, 4, 2, 4, 0.08);
        world.spawnParticle(Particle.LAVA, impactLoc, 30, 1, 0.5, 1, 0);
        world.spawnParticle(Particle.CRIT, impactLoc, 40, 3, 1, 3, 0.3);
        world.spawnParticle(Particle.FLASH, impactLoc, 1, 0, 0, 0, 0);

        // Восходящий столб дыма
        for (int i = 0; i < 5; i++) {
            double offsetY = i * 1.5;
            Location smokeLoc = impactLoc.clone().add(0, offsetY, 0);
            world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, smokeLoc,
                    10 + i * 5, 1.5 - i * 0.2, 0.3, 1.5 - i * 0.2, 0.05);
            world.spawnParticle(Particle.FLAME, smokeLoc, 5, 0.5, 0.3, 0.5, 0.02);
        }

        // ── Звуки взрыва ──
        world.playSound(impactLoc, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.6f);
        world.playSound(impactLoc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 2.0f, 0.4f);
        world.playSound(impactLoc, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 2.0f, 0.8f);

        // ── Спавн руды ──
        Block groundBlock = impactLoc.getBlock();
        Block aboveBlock = groundBlock.getRelative(BlockFace.UP);

        if (groundBlock.isEmpty() || groundBlock.isLiquid()) {
            groundBlock.setType(oreBlock);
        } else if (aboveBlock.isEmpty()) {
            aboveBlock.setType(oreBlock);
        } else {
            for (int dy = 1; dy <= 4; dy++) {
                Block candidate = impactLoc.add(0, 1, 0).getBlock();
                if (candidate.isEmpty() || candidate.isLiquid()) {
                    candidate.setType(oreBlock);
                    break;
                }
            }
        }

        // ── Кратер ──
        int craterR = (int) Math.round(sphereRadius * 2.0);
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
                // Внешнее кольцо: fire на поверхности
                else if (dist <= craterR) {
                    if (above.isEmpty() && random.nextDouble() < 0.15) {
                        above.setType(Material.FIRE);
                    }
                }
            }
        }

        ConsoleLogger.info("[Meteor] Impact at " + impactLoc.getBlockX() + " " + impactLoc.getBlockY() + " " + impactLoc.getBlockZ()
                + " in " + world.getName());

        if (onComplete != null) onComplete.run();
    }

    // ──────────── Cleanup ────────────

    public void setOnComplete(Runnable onComplete) {
        this.onComplete = onComplete;
    }

    public void cleanRemove() {
        Marker m = (Marker) world.getEntity(centerId);
        if (m != null) m.remove();
        for (UUID id : displayIds) {
            var e = world.getEntity(id);
            if (e != null) e.remove();
        }
    }

    // ──────────── Block pickers ────────────

    private BlockData pickCoreBlock() {
        double r = random.nextDouble();
        if (r < 0.35) return SHROOMLIGHT_DATA;
        if (r < 0.55) return GLOWSTONE_DATA;
        if (r < 0.75) return MAGMA_DATA;
        if (r < 0.90) return Material.REDSTONE_BLOCK.createBlockData();
        return Material.OCHRE_FROGLIGHT.createBlockData();
    }

    private BlockData pickCoronaBlock() {
        double r = random.nextDouble();
        if (r < 0.30) return FIRE_DATA;
        if (r < 0.55) return NETHERRACK_DATA;
        if (r < 0.75) return MAGMA_DATA;
        if (r < 0.90) return Material.ORANGE_TERRACOTTA.createBlockData();
        return Material.RED_TERRACOTTA.createBlockData();
    }

    private void randomizeAxis() {
        axisX = random.nextDouble() * 2 - 1;
        axisY = random.nextDouble() * 2 - 1;
        axisZ = random.nextDouble() * 2 - 1;
        double len = Math.sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ);
        if (len == 0) len = 1;
        axisX /= len;
        axisY /= len;
        axisZ /= len;
        // Скорость вращения
        double speed = 0.03 + random.nextDouble() * 0.06;
        axisX *= speed;
        axisY *= speed;
        axisZ *= speed;
    }

    // ──────────── Helpers ────────────

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
