package com.ultimateimprovments.module.meteor;

import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Color;
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
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
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
 *
 * <p>Everything the players actually see comes from the BlockDisplay sphere and
 * from particles/sounds sent by hand: the meteor falls ~80-120 blocks above the
 * ground and up to ~120 blocks away horizontally, far outside the default
 * ranges of display entities (64 blocks) and particle broadcasts (32 blocks).</p>
 */
public class MeteorTask extends BukkitRunnable {

    /**
     * Client-side render range of the falling sphere, in multiples of 64 blocks
     * ({@code view_range * 64}). Display entities default to 1.0, i.e. 64 blocks,
     * which culled the meteor for the whole descent — it only popped in right
     * before the impact. 4.0 covers the tallest fall plus the widest spawn
     * offset (~256 blocks).
     */
    private static final float VIEW_RANGE = 4.0f;

    /** Squared distance within which players receive the trail particles and sounds. */
    private static final double EFFECT_RADIUS_SQR = Math.pow(VIEW_RANGE * 64.0, 2);

    // ──────────── Fields ────────────
    private final Plugin plugin;
    private final World world;
    private final double startY, targetX, targetY, targetZ;
    private final int totalTicks;
    private int tick;
    private double currentY;

    /** Chunk of the fall/impact column, kept loaded by a plugin ticket while alive. */
    private final int chunkX, chunkZ;
    private boolean chunkTicketHeld;

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
    public MeteorTask(Plugin plugin,
                      World world,
                      double startY,
                      double targetX, double targetY, double targetZ,
                      int totalTicks,
                      int sphereRadius,
                      float explosionPower,
                      List<BlockData> shellBlocks,
                      Map<Material, Double> coreOres) {
        this.plugin = plugin;
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
        this.chunkX = ((int) Math.floor(targetX)) >> 4;
        this.chunkZ = ((int) Math.floor(targetZ)) >> 4;
        // Pick the core ore once so the falling visual matches what actually lands.
        this.coreOre = pickCoreOre();

        // Keep the fall column loaded and ticking for the meteor's short lifetime.
        // Without the ticket the chunk may unload when no player is close enough:
        // the displays freeze mid-air and the impact cleanup misses them.
        this.chunkTicketHeld = world.addPluginChunkTicket(chunkX, chunkZ, plugin);

        try {
            // ── Create the Marker at the start height ──
            Location startLoc = new Location(world, targetX, startY, targetZ);
            this.marker = (Marker) world.spawnEntity(startLoc, EntityType.MARKER);
            this.marker.setPersistent(false);

            // ── Spawn the falling block visualizers (a glowing sphere of blocks) ──
            spawnBlockVisualizers(startLoc);
        } catch (RuntimeException e) {
            // A partially constructed task must not leak entities or a chunk ticket.
            safeCleanup();
            throw new IllegalStateException("Could not spawn the falling meteor entities: "
                    + e.getMessage(), e);
        }
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
                // Marker AND every BlockDisplay must go — the display entities are
                // not persistent, so a half-finished impact used to leak a hovering
                // block sphere in the sky.
                safeCleanup();
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

        // World#spawnParticle/playSound only reach ~32/16 blocks, so the fall is
        // broadcast to the players who can actually see the meteor instead.
        List<Player> viewers = viewersNear(loc);

        // ── Particles ──
        double intensity = 0.5 + progress * 2.0;
        // Spread the fire across the whole visible sphere, not just the center
        double fireSpread = sphereRadius + 0.6;

        // Fire aura
        particle(viewers, Particle.FLAME, loc, (int) (6 * intensity),
                fireSpread, fireSpread * 0.6, fireSpread, 0.02);

        // Smoke
        particle(viewers, Particle.CAMPFIRE_SIGNAL_SMOKE, loc, (int) (4 * intensity),
                fireSpread, fireSpread * 0.4, fireSpread, 0.03);

        // Sparks
        particle(viewers, Particle.LAVA, loc, (int) (2 * intensity),
                fireSpread, fireSpread * 0.6, fireSpread, 0);

        // Trail above (smoke stays higher)
        Location trailLoc = loc.clone().add(0, 1.0, 0);
        particle(viewers, Particle.CAMPFIRE_SIGNAL_SMOKE, trailLoc, (int) (3 * intensity),
                0.3, 0.5, 0.3, 0.05);

        // Bright flash in the last third.
        // FLASH carries Color data (org.bukkit.Color) — spawning it without the
        // data object throws "missing required data class org.bukkit.Color" and
        // used to abort the whole fall at ~70% before the impact.
        if (progress > 0.7 && random.nextDouble() < 0.2) {
            particle(viewers, Particle.FLASH, loc, 1, 0, 0, 0, 0, Color.WHITE);
        }

        // ── Sound ──
        float volume = 0.3f + (float) progress * 0.7f;
        float pitch = 0.3f + (float) progress * 0.6f;
        sound(viewers, Sound.ENTITY_GHAST_SHOOT, loc, volume * 0.5f, pitch);

        if (progress > 0.6) {
            float rumblePitch = 0.1f + (float) (1.0 - progress) * 0.3f;
            sound(viewers, Sound.BLOCK_LAVA_AMBIENT, loc, volume * 0.3f, rumblePitch);
        }
        if (progress > 0.8) {
            sound(viewers, Sound.ENTITY_CREEPER_PRIMED, loc, 0.4f, 0.5f);
        }
    }

    // ──────────── Impact ────────────
    private void impact() {
        impactDone = true;

        // Remove the Marker and the falling visualizers (real blocks replace them)
        removeMarker();
        removeDisplays();

        Location impactLoc = new Location(world, targetX, targetY, targetZ);

        // Same reason as the fall: the impact point is up to ~120 blocks away
        // from the players watching, so particles/sounds go out per player.
        List<Player> viewers = viewersNear(impactLoc);

        // ── Explosion (configurable power, TNT = 4.0) ──
        world.createExplosion(impactLoc, explosionPower, true);

        // ── Explosion particles ──
        particle(viewers, Particle.EXPLOSION_EMITTER, impactLoc, 1, 0, 0, 0, 0);
        particle(viewers, Particle.FLAME, impactLoc, 60, 4, 2, 4, 0.15);
        particle(viewers, Particle.CAMPFIRE_SIGNAL_SMOKE, impactLoc, 80, 4, 2, 4, 0.08);
        particle(viewers, Particle.LAVA, impactLoc, 30, 1, 0.5, 1, 0);
        particle(viewers, Particle.FLASH, impactLoc, 1, 0, 0, 0, 0, Color.WHITE);

        // Rising smoke column
        for (int i = 0; i < 5; i++) {
            double offsetY = i * 1.5;
            Location smokeLoc = impactLoc.clone().add(0, offsetY, 0);
            particle(viewers, Particle.CAMPFIRE_SIGNAL_SMOKE, smokeLoc,
                    10 + i * 5, 1.5 - i * 0.2, 0.3, 1.5 - i * 0.2, 0.05);
            particle(viewers, Particle.FLAME, smokeLoc, 5, 0.5, 0.3, 0.5, 0.02);
        }

        // ── Sounds ──
        sound(viewers, Sound.ENTITY_GENERIC_EXPLODE, impactLoc, 3.0f, 0.6f);
        sound(viewers, Sound.ENTITY_FIREWORK_ROCKET_BLAST, impactLoc, 2.0f, 0.4f);
        sound(viewers, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, impactLoc, 2.0f, 0.8f);

        // ── Crater first, meteorite last ──
        // The crater decoration (magma/obsidian/fire) must not overwrite the
        // meteorite blocks and the central ore, so it is carved before the
        // structure is placed.
        spawnCrater(impactLoc);

        // ── Spawn the meteor structure (block sphere + central ore) ──
        spawnMeteorStructure(impactLoc);

        // The chunk ticket is only needed while the meteor is on screen.
        releaseChunkTicket();

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
                    if (!isInsideWorld(b)) continue;

                    // The meteor REPLACES existing blocks (impact force).

                    // Center — ore
                    if (dist <= 0.5) {
                        setBlockDataSafely(b, coreOre.createBlockData());
                        continue;
                    }

                    // Shell — a random block from display_blocks
                    setBlockDataSafely(b, shellBlocks.get(random.nextInt(shellBlocks.size())));
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
     * <p>
     * The impact point is the first air block above the surface (see
     * {@code MeteorModule}), so the crater slice lives one block below it — the
     * real ground the explosion hit, not empty air.
     */
    private void spawnCrater(Location impactLoc) {
        int craterR = sphereRadius * 2;
        int groundY = impactLoc.getBlockY() - 1;

        for (int dx = -craterR; dx <= craterR; dx++) {
            for (int dz = -craterR; dz <= craterR; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > craterR + 0.5) continue;

                // The explosion already removed the top layer, so find the new
                // crater bottom of this column instead of decorating empty air.
                Block b = firstSolidBelow(
                        world.getBlockAt(impactLoc.getBlockX() + dx, groundY, impactLoc.getBlockZ() + dz), 5);
                if (b == null) continue;
                Block above = b.getRelative(BlockFace.UP);

                // Center: magma + fire
                if (dist <= craterR * 0.35) {
                    if (!b.isEmpty()) {
                        if (random.nextDouble() < 0.5) setTypeSafely(b, Material.MAGMA_BLOCK);
                        else if (random.nextDouble() < 0.3) setTypeSafely(b, Material.OBSIDIAN);
                    }
                    if (above.isEmpty() && random.nextDouble() < 0.6) {
                        setTypeSafely(above, Material.FIRE);
                    }
                }
                // Middle ring: blackstone
                else if (dist <= craterR * 0.7) {
                    if (random.nextDouble() < 0.25 && !b.isEmpty()) {
                        setTypeSafely(b, Material.BLACKSTONE);
                    }
                }
                // Outer ring: fire
                else if (dist <= craterR) {
                    if (above.isEmpty() && random.nextDouble() < 0.15) {
                        setTypeSafely(above, Material.FIRE);
                    }
                }
            }
        }
    }

    // ──────────── Cleanup ────────────

    public void setOnComplete(Runnable onComplete) {
        this.onComplete = onComplete;
    }

    /** @return number of BlockDisplay entities rendering the falling sphere. */
    public int displayCount() {
        return displays.size();
    }

    private void safeCleanup() {
        removeMarker();
        removeDisplays();
        releaseChunkTicket();
    }

    /**
     * Removes the Marker entity without running the impact logic.
     * Called from {@code MeteorModule.onDisable()} so markers are not leaked
     * on reload/disable (cancelling the task alone does not despawn the entity).
     */
    public void cleanup() {
        removeMarker();
        removeDisplays();
        releaseChunkTicket();
    }

    /** Releases the plugin chunk ticket that kept the fall column loaded. */
    private void releaseChunkTicket() {
        if (!chunkTicketHeld) return;
        chunkTicketHeld = false;
        try {
            world.removePluginChunkTicket(chunkX, chunkZ, plugin);
        } catch (Exception ignored) {
            // The world may already be shutting down.
        }
    }

    private void removeMarker() {
        if (marker != null && marker.isValid()) {
            marker.remove();
        }
        marker = null;
    }

    /**
     * Removes all block visualizer entities.
     */
    private void removeDisplays() {
        for (BlockDisplay display : displays) {
            // No isValid() gate: a display in a not-yet-ticked chunk reports invalid
            // even though the entity exists, and skipping it leaked the whole sphere.
            try {
                display.remove();
            } catch (Exception ignored) {
                // Already despawned (chunk unload) — nothing to do.
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
                    // Render far enough: the default 1.0 (=64 blocks) culled the
                    // whole sphere while it was still high above the players.
                    display.setViewRange(VIEW_RANGE);

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

    /**
     * @return players in this meteor's world close enough to perceive the fall.
     *         {@code World#spawnParticle} broadcasts only within 32 blocks and
     *         sounds fade out over {@code volume * 16} blocks, both far inside
     *         the ~200-block fall column, so the visuals are delivered manually.
     */
    private List<Player> viewersNear(Location center) {
        List<Player> viewers = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(center) <= EFFECT_RADIUS_SQR) {
                viewers.add(player);
            }
        }
        return viewers;
    }

    /** Sends a particle to every viewer (the world-wide broadcast stops at 32 blocks). */
    private void particle(List<Player> viewers, Particle particle, Location loc, int count,
                          double offsetX, double offsetY, double offsetZ, double extra) {
        for (Player viewer : viewers) {
            viewer.spawnParticle(particle, loc, count, offsetX, offsetY, offsetZ, extra);
        }
    }

    /** Same as {@link #particle} but for particles that require a data object (e.g. FLASH). */
    private <T> void particle(List<Player> viewers, Particle particle, Location loc, int count,
                              double offsetX, double offsetY, double offsetZ, double extra, T data) {
        for (Player viewer : viewers) {
            viewer.spawnParticle(particle, loc, count, offsetX, offsetY, offsetZ, extra, data);
        }
    }

    /**
     * Plays a sound for every viewer.
     * <p>
     * The client fades a sound out linearly over {@code volume * 16} blocks, so
     * the packet volume is raised by the listener's distance: without it the fall
     * was completely silent for anyone farther than ~16 blocks.
     */
    private void sound(List<Player> viewers, Sound sound, Location loc, float volume, float pitch) {
        for (Player viewer : viewers) {
            double distance = Math.sqrt(Math.max(0.0, viewer.getLocation().distanceSquared(loc)));
            viewer.playSound(loc, sound, volume + (float) (distance / 16.0), pitch);
        }
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /**
     * @return true when the block is inside this world's build limits. A meteor
     *         near the build limit would otherwise throw from {@code setType}.
     */
    private boolean isInsideWorld(Block block) {
        return block.getY() >= world.getMinHeight() && block.getY() < world.getMaxHeight();
    }

    private void setTypeSafely(Block block, Material material) {
        if (material == null || !isInsideWorld(block)) return;
        block.setType(material, false);
    }

    private void setBlockDataSafely(Block block, BlockData data) {
        if (data == null || !isInsideWorld(block)) return;
        block.setBlockData(data, false);
    }

    /**
     * @return the first solid block at or below {@code start} within {@code depth}
     *         blocks, or {@code null} when the column is empty (void/crater hole).
     */
    private Block firstSolidBelow(Block start, int depth) {
        Block block = start;
        for (int i = 0; i <= depth; i++) {
            if (!isInsideWorld(block)) return null;
            if (!block.isEmpty() && !block.isLiquid() && block.getType().isSolid()) return block;
            block = block.getRelative(BlockFace.DOWN);
        }
        return null;
    }
}
