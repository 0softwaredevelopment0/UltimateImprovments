package com.ultimateimprovments.mechanics.features.world;

import com.ultimateimprovments.core.Keys;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 🧊 Manages the "Concrete Bucket" mechanic.
 * <p>
 * When a Concrete Bucket is poured:
 * <ol>
 *   <li>The water is tinted gray by setting the biome to {@code PALE_GARDEN}</li>
 *   <li>The original biome is saved for restoration</li>
 *   <li>Every block the water flows into joins the same pour group and is tinted too</li>
 *   <li>After 60 seconds the <b>whole</b> pour group turns into concrete at once,
 *       each hardened block emitting a crit particle burst</li>
 *   <li>If water disappears earlier → the biome is restored</li>
 * </ol>
 */
public class ConcreteBucketManager extends BukkitRunnable implements Listener {

    private static ConcreteBucketManager instance;
    private static Main plugin;
    private static final long CONCRETE_DELAY_MS = 60_000; // 60 seconds

    // Crit burst spawned on every block that hardens into concrete
    private static final Particle HARDEN_PARTICLE = Particle.CRIT;
    private static final int HARDEN_PARTICLE_COUNT = 1;
    private static final double HARDEN_PARTICLE_OFFSET = 0.25;

    // Tracked water blocks: location -> {originalBiome, groupId}
    private static final Map<Location, TrackedWater> trackedWater = new HashMap<>();
    // One pour = one group; the whole group hardens at the same moment
    private static final Map<UUID, PourGroup> groups = new HashMap<>();
    // Pending biome sets (the water appears a tick after the pour/flow event)
    private static final Map<Location, Biome> pendingBiomes = new HashMap<>();

    private record TrackedWater(Biome originalBiome, UUID groupId) {}

    private static final class PourGroup {
        private final UUID id = UUID.randomUUID();
        private final long startTime = System.currentTimeMillis();
        private final Set<Location> blocks = new HashSet<>();
    }

    // =========================
    // INIT
    // =========================
    public static void init(Main plugin) {
        ConcreteBucketManager.plugin = plugin;
        instance = new ConcreteBucketManager();
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
        // Once per second is enough for tinting and hardening
        instance.runTaskTimer(plugin, 20L, 20L);
        ConsoleLogger.info("[ConcreteBucket] Initialized");
    }

    /**
     * Checks whether an item is a Concrete Bucket.
     */
    private static boolean isConcreteBucket(ItemStack item) {
        if (item == null || item.getType() != Material.WATER_BUCKET) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(Keys.CONCRETE_BUCKET, PersistentDataType.BYTE);
    }

    // =========================
    // 🪣 POURING — the player uses the bucket
    // =========================
    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (e.getBucket() != Material.WATER_BUCKET) return;

        // Check the PDC on the item the player used
        if (!isConcreteBucket(e.getItemStack())
                && !isConcreteBucket(e.getPlayer().getInventory().getItemInMainHand())
                && !isConcreteBucket(e.getPlayer().getInventory().getItemInOffHand())) {
            return;
        }

        // Position where the water is placed (blockClicked + blockFace)
        Block clicked = e.getBlockClicked();
        if (clicked == null) return;

        Block waterBlock = clicked.getRelative(e.getBlockFace());
        Location waterLoc = waterBlock.getLocation();
        if (waterLoc.getWorld() == null) return;

        // Already tracked (second pour into the same puddle): keep the original
        // group, so both pours harden together and the tinted biome is not
        // mistaken for the "original" one.
        if (trackedWater.containsKey(waterLoc)) {
            pendingBiomes.put(waterLoc, Biome.PALE_GARDEN);
            return;
        }

        PourGroup group = new PourGroup();
        groups.put(group.id, group);
        trackBlock(waterLoc, group);

        // 🪣 Paper itself replaces WATER_BUCKET with a plain BUCKET after this event.
        // The PDC is lost on replacement, so the bucket becomes a normal one.
    }

    // =========================
    // 💧 WATER FLOW — every block the water reaches joins the pour group
    // =========================
    @EventHandler(ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent e) {
        TrackedWater from = trackedWater.get(e.getBlock().getLocation());
        if (from == null) return;

        PourGroup group = groups.get(from.groupId());
        if (group == null) return;

        // BlockFromToEvent is fired right before the destination is replaced, so
        // the destination is still air here — a type check would reject every flow.
        // The destination joins the group and is tinted; if it never turns into
        // water the ticking task drops it again and restores the biome.
        trackBlock(e.getToBlock().getLocation(), group);
    }

    // =========================
    // 🔥 WATER DISAPPEARANCE — the block stopped being water
    // =========================
    @EventHandler(ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent e) {
        if (e.getBlock().getType() == Material.WATER) return;
        untrack(e.getBlock().getLocation());
    }

    // =========================
    // 🔨 BLOCK BROKEN
    // =========================
    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        untrack(e.getBlock().getLocation());
    }

    // =========================
    // ⏱ TICK — deferred biomes + hardening
    // =========================
    @Override
    public void run() {
        // ════════════════════════════════════════
        // Apply the deferred biome tints
        // ════════════════════════════════════════
        if (!pendingBiomes.isEmpty()) {
            Iterator<Map.Entry<Location, Biome>> pit = pendingBiomes.entrySet().iterator();
            while (pit.hasNext()) {
                Map.Entry<Location, Biome> entry = pit.next();
                Location loc = entry.getKey();
                if (isWater(loc)) {
                    // World.setBiome() sends an update to the client (unlike Block.setBiome())
                    loc.getWorld().setBiome(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), entry.getValue());
                    pit.remove();
                }
                // If the water hasn't appeared yet — wait for the next tick.
                // Entries that never become water are removed by untrack() below.
            }
        }

        if (trackedWater.isEmpty()) return;

        // ════════════════════════════════════════
        // Drop blocks that are no longer water (evaporated, replaced, ...)
        // ════════════════════════════════════════
        List<Location> vanished = null;
        for (Location loc : trackedWater.keySet()) {
            if (!isWater(loc)) {
                if (vanished == null) vanished = new ArrayList<>();
                vanished.add(loc);
            }
        }
        if (vanished != null) {
            vanished.forEach(ConcreteBucketManager::untrack);
        }

        if (groups.isEmpty()) return;

        // ════════════════════════════════════════
        // Harden whole pour groups whose timer ran out
        // ════════════════════════════════════════
        long now = System.currentTimeMillis();
        for (PourGroup group : new ArrayList<>(groups.values())) {
            if (now - group.startTime >= CONCRETE_DELAY_MS) {
                harden(group);
            }
        }
    }

    // =========================
    // 🧱 HARDENING — water -> concrete, with crit particles
    // =========================
    private static void harden(PourGroup group) {
        for (Location loc : group.blocks) {
            Block block = loc.getBlock();
            if (block.getType() == Material.WATER) {
                block.setType(Material.GRAY_CONCRETE);
                spawnHardenParticles(block.getLocation());
            }
            trackedWater.remove(loc);
            pendingBiomes.remove(loc);
            // Do NOT restore the biome — the concrete keeps the tinted look
        }
        group.blocks.clear();
        groups.remove(group.id);
    }

    private static void spawnHardenParticles(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;
        Location center = loc.clone().add(0.5, 0.5, 0.5);
        world.spawnParticle(
                HARDEN_PARTICLE,
                center,
                HARDEN_PARTICLE_COUNT,
                HARDEN_PARTICLE_OFFSET,
                HARDEN_PARTICLE_OFFSET,
                HARDEN_PARTICLE_OFFSET,
                0.0
        );
    }

    // =========================
    // 🧾 TRACKING HELPERS
    // =========================
    /**
     * Starts tracking a block as poured water and tints it gray.
     * <p>
     * The block is still air at event time — the water itself appears on the next
     * tick, so the ticking task drops entries that never became water.
     */
    private static void trackBlock(Location loc, PourGroup group) {
        World world = loc.getWorld();
        if (world == null || trackedWater.containsKey(loc)) return;

        Biome originalBiome = world.getBiome(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        trackedWater.put(loc, new TrackedWater(originalBiome, group.id));
        group.blocks.add(loc);
        pendingBiomes.put(loc, Biome.PALE_GARDEN);
    }

    private static void untrack(Location loc) {
        TrackedWater data = trackedWater.remove(loc);
        if (data == null) return;

        PourGroup group = groups.get(data.groupId());
        if (group != null) {
            group.blocks.remove(loc);
            if (group.blocks.isEmpty()) groups.remove(group.id);
        }

        pendingBiomes.remove(loc);
        restoreBiome(loc, data.originalBiome());
    }

    private static boolean isWater(Location loc) {
        return loc.getBlock().getType() == Material.WATER;
    }

    // =========================
    // 🔄 BIOME RESTORATION
    // =========================
    private static void restoreBiome(Location loc, Biome biome) {
        if (biome != null && biome != Biome.PALE_GARDEN && loc.getWorld() != null) {
            loc.getWorld().setBiome(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), biome);
        }
    }

    // =========================
    // 🧹 SHUTDOWN
    // =========================
    public static void shutdown() {
        if (instance != null) {
            instance.cancel();
            instance = null;
        }
        trackedWater.clear();
        groups.clear();
        pendingBiomes.clear();
    }
}
