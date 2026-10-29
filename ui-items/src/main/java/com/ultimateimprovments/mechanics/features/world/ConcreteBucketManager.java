package com.ultimateimprovments.mechanics.features.world;

import com.ultimateimprovments.core.Keys;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.mechanics.features.world.ConcreteBucketDatabase.BlockPos;
import com.ultimateimprovments.mechanics.features.world.ConcreteBucketDatabase.StoredWater;
import com.ultimateimprovments.util.ConsoleLogger;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import java.util.LinkedHashSet;
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
 *       each block getting its original biome back</li>
 *   <li>While the cement is still liquid every block that will become concrete
 *       emits crit particles as a hint; they stop the moment it hardens</li>
 *   <li>If water disappears earlier → the biome is restored right away</li>
 * </ol>
 * Pending pours live in SQLite ({@link ConcreteBucketDatabase}) — see
 * {@link #loadFromDatabase()} / {@link #flushDatabase()}.
 */
public class ConcreteBucketManager extends BukkitRunnable implements Listener {

    private static ConcreteBucketManager instance;
    private static Main plugin;
    private static final long CONCRETE_DELAY_MS = 60_000; // 60 seconds

    // Hint burst spawned on every still-liquid block — shows which blocks will harden
    private static final Particle HINT_PARTICLE = Particle.CRIT;
    private static final int HINT_PARTICLE_COUNT = 1;
    private static final double HINT_PARTICLE_OFFSET = 0.25;
    // Hint particle interval (ticks). Half a second keeps the marks continuous
    private static final long TICK_INTERVAL = 10L;

    // Tracked water blocks: location -> {originalBiome, groupId}
    private static final Map<Location, TrackedWater> trackedWater = new HashMap<>();
    // One pour = one group; the whole group hardens at the same moment
    private static final Map<UUID, PourGroup> groups = new HashMap<>();
    // Pending biome sets (the water appears a tick after the pour/flow event)
    private static final Map<Location, Biome> pendingBiomes = new HashMap<>();

    // DB write buffer, flushed once per second (and on shutdown)
    private static final Set<StoredWater> pendingInserts = new LinkedHashSet<>();
    private static final Set<BlockPos> pendingDeletes = new LinkedHashSet<>();

    private record TrackedWater(Biome originalBiome, UUID groupId) {}

    private static final class PourGroup {
        private final UUID id;
        private final long startTime;
        private final Set<Location> blocks = new HashSet<>();

        private PourGroup() {
            this(UUID.randomUUID(), System.currentTimeMillis());
        }

        private PourGroup(UUID id, long startTime) {
            this.id = id;
            this.startTime = startTime;
        }
    }

    // =========================
    // INIT
    // =========================
    public static void init(Main plugin) {
        ConcreteBucketManager.plugin = plugin;
        instance = new ConcreteBucketManager();
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
        loadFromDatabase();
        // Twice per second: tinting, hardening and the liquid-state hint particles
        instance.runTaskTimer(plugin, 20L, TICK_INTERVAL);
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
    // ⏱ TICK — deferred biomes + hardening + DB flush
    // =========================
    @Override
    public void run() {
        tick();
        flushDatabase();
    }

    private void tick() {
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
                    applyBiome(loc, entry.getValue());
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

        // ════════════════════════════════════════
        // Hint particles on the still-liquid blocks
        // ════════════════════════════════════════
        // Runs after harden(): hardened blocks already left trackedWater, so the
        // marks vanish exactly when the block turns into concrete.
        for (Location loc : trackedWater.keySet()) {
            spawnHintParticles(loc);
        }
    }

    // =========================
    // 🧱 HARDENING — water -> concrete, crit particles, biome rollback
    // =========================
    private static void harden(PourGroup group) {
        // Copy: replacing the water triggers block updates that may untrack neighbours
        for (Location loc : new ArrayList<>(group.blocks)) {
            Block block = loc.getBlock();
            if (block.getType() == Material.WATER) {
                block.setType(Material.GRAY_CONCRETE);
            }

            TrackedWater data = trackedWater.remove(loc);
            pendingBiomes.remove(loc);
            markDeleted(loc);

            // The water is gone — give the column its original biome back
            if (data != null) {
                restoreBiome(loc, data.originalBiome());
            }
        }
        group.blocks.clear();
        groups.remove(group.id);
    }

    private static void spawnHintParticles(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;
        Location center = loc.clone().add(0.5, 0.5, 0.5);
        world.spawnParticle(
                HINT_PARTICLE,
                center,
                HINT_PARTICLE_COUNT,
                HINT_PARTICLE_OFFSET,
                HINT_PARTICLE_OFFSET,
                HINT_PARTICLE_OFFSET,
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
        markInserted(loc, originalBiome, group);
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
        markDeleted(loc);
        restoreBiome(loc, data.originalBiome());
    }

    private static boolean isWater(Location loc) {
        return loc.getBlock().getType() == Material.WATER;
    }

    // =========================
    // 🔄 BIOME HANDLING
    // =========================
    private static void applyBiome(Location loc, Biome biome) {
        if (biome == null || loc.getWorld() == null) return;
        loc.getWorld().setBiome(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), biome);
    }

    private static void restoreBiome(Location loc, Biome biome) {
        if (biome == null || biome == Biome.PALE_GARDEN) return;
        applyBiome(loc, biome);
    }

    private static String biomeKey(Biome biome) {
        return biome == null ? "" : biome.getKey().toString();
    }

    private static Biome resolveBiome(String key) {
        if (key == null || key.isEmpty()) return null;
        NamespacedKey namespacedKey = NamespacedKey.fromString(key);
        if (namespacedKey == null) return null;
        try {
            return RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.BIOME)
                    .get(namespacedKey);
        } catch (Exception e) {
            return null;
        }
    }

    // =========================
    // 💾 PERSISTENCE
    // =========================
    private static void markInserted(Location loc, Biome originalBiome, PourGroup group) {
        World world = loc.getWorld();
        if (world == null) return;
        BlockPos pos = new BlockPos(world.getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        pendingDeletes.remove(pos);
        pendingInserts.add(new StoredWater(pos, biomeKey(originalBiome), group.id.toString(), group.startTime));
    }

    private static void markDeleted(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;
        BlockPos pos = new BlockPos(world.getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        pendingInserts.removeIf(row -> row.pos().equals(pos));
        pendingDeletes.add(pos);
    }

    private static void flushDatabase() {
        if (pendingInserts.isEmpty() && pendingDeletes.isEmpty()) return;
        // Keep the buffers if the DB is not reachable — the next tick retries
        if (!ConcreteBucketDatabase.isAvailable()) return;

        List<BlockPos> deletes = new ArrayList<>(pendingDeletes);
        List<StoredWater> inserts = new ArrayList<>(pendingInserts);
        pendingDeletes.clear();
        pendingInserts.clear();

        // Deletes first: a block that was untracked and re-tracked in the same
        // second must end up as the fresh row, not as a deleted one.
        ConcreteBucketDatabase.deleteAll(deletes);
        ConcreteBucketDatabase.insertAll(inserts);
    }

    /**
     * Restores pours that were still liquid when the server stopped: their tint
     * and their remaining hardening time are read back from SQLite.
     */
    private static void loadFromDatabase() {
        List<StoredWater> rows = ConcreteBucketDatabase.loadAll();
        if (rows.isEmpty()) return;

        int restored = 0;
        int skipped = 0;

        for (StoredWater row : rows) {
            World world = Bukkit.getWorld(row.pos().world());
            if (world == null) {
                // World not loaded (yet) — keep the row, the water is still there
                skipped++;
                continue;
            }

            UUID groupId;
            try {
                groupId = UUID.fromString(row.groupId());
            } catch (IllegalArgumentException ex) {
                continue;
            }

            PourGroup group = groups.get(groupId);
            if (group == null) {
                group = new PourGroup(groupId, row.startTime());
                groups.put(group.id, group);
            }

            Location loc = new Location(world, row.pos().x(), row.pos().y(), row.pos().z());
            trackedWater.put(loc, new TrackedWater(resolveBiome(row.originalBiome()), groupId));
            group.blocks.add(loc);
            // The tint is already saved in the world — no pending biome needed
            restored++;
        }

        if (restored > 0) {
            ConsoleLogger.info("[ConcreteBucket] Restored " + restored + " pending water block(s) from DB.");
        }
        if (skipped > 0) {
            ConsoleLogger.warn("[ConcreteBucket] " + skipped + " pending water block(s) belong to unloaded worlds.");
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
        flushDatabase();
        trackedWater.clear();
        groups.clear();
        pendingBiomes.clear();
    }
}
