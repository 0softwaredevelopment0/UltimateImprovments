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
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
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
import java.util.Locale;
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
 * <p>
 * Every timing and visual can be tuned in {@code configs/UI-Items.toml} under
 * {@code [concrete_bucket]} — see {@link #reloadConfig()}.
 */
public class ConcreteBucketManager extends BukkitRunnable implements Listener {

    private static ConcreteBucketManager instance;
    private static Main plugin;

    // ── Settings — configs/UI-Items.toml → [concrete_bucket] (see reloadConfig) ──
    private static boolean enabled = true;
    private static long delayMs = 60_000; // 60 seconds
    private static Material hardenMaterial = Material.GRAY_CONCRETE;
    private static Biome tintBiome = Biome.PALE_GARDEN;
    // Hint particle on every still-liquid block — shows which blocks will harden
    private static Particle hintParticle = Particle.CRIT;
    private static int hintCount = 1;
    private static double hintOffset = 0.25;
    // Hint particle interval (ticks). Half a second keeps the marks continuous
    private static long tickInterval = 10L;

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
        // In-place reload (soft /ui reload): drop the old task, the DB keeps the
        // live pours and loadFromDatabase() below restores them
        if (instance != null) shutdown();

        reloadConfig();

        instance = new ConcreteBucketManager();
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
        loadFromDatabase();
        instance.runTaskTimer(plugin, 20L, tickInterval);
        ConsoleLogger.info("[ConcreteBucket] Initialized");
    }

    /**
     * Reads the {@code [concrete_bucket]} section of {@code configs/UI-Items.toml}.
     * Invalid names fall back to the defaults instead of breaking the mechanic.
     */
    public static void reloadConfig() {
        Main main = Main.getInstance();
        if (main == null) return;
        FileConfiguration cfg = main.getConfig();

        enabled = cfg.getBoolean("concrete_bucket.enabled", true);
        delayMs = Math.max(1L, cfg.getLong("concrete_bucket.harden_delay_seconds", 60L)) * 1000L;
        hardenMaterial = resolveMaterial(
                cfg.getString("concrete_bucket.harden_block"), Material.GRAY_CONCRETE);
        String tintName = cfg.getString("concrete_bucket.tint_biome", "PALE_GARDEN");
        tintBiome = resolveBiome("minecraft:" + tintName.trim().toLowerCase(Locale.ROOT), Biome.PALE_GARDEN);
        hintParticle = resolveParticle(cfg.getString("concrete_bucket.particle"), Particle.CRIT);
        hintCount = Math.max(1, cfg.getInt("concrete_bucket.particle_count", 1));
        hintOffset = Math.max(0.0, cfg.getDouble("concrete_bucket.particle_offset", 0.25));
        tickInterval = Math.max(1L, cfg.getLong("concrete_bucket.particle_interval_ticks", 10L));
    }

    /** Hardening delay in whole seconds (used by the craft listener's item lore). */
    public static long delaySeconds() {
        return Math.max(1L, delayMs / 1000L);
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
        // Feature off: the bucket behaves like a plain water bucket
        if (!enabled) return;

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
            pendingBiomes.put(waterLoc, tintBiome);
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
    // 🪣 FILL — liquid cement cannot be scooped up like water
    // =========================
    // The cement renders as water, so vanilla happily fills a bucket from it.
    // It is not water: the click must do nothing at all.
    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        Block clicked = e.getBlockClicked();
        if (clicked == null) return;

        // The clicked block is the water in almost every case; the relative one
        // covers clicking a block whose neighbour face holds the water.
        if (isCement(clicked) || isCement(clicked.getRelative(e.getBlockFace()))) {
            e.setCancelled(true);
        }
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
            if (now - group.startTime >= delayMs) {
                harden(group);
            }
        }

        // ════════════════════════════════════════
        // Hint particles on the still-liquid blocks
        // ════════════════════════════════════════
        // Runs after harden(): hardened blocks already left trackedWater, so the
        // marks vanish exactly when the block turns into concrete.
        if (enabled) {
            for (Location loc : trackedWater.keySet()) {
                spawnHintParticles(loc);
            }
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
                block.setType(hardenMaterial);
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
                hintParticle,
                center,
                hintCount,
                hintOffset,
                hintOffset,
                hintOffset,
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
        pendingBiomes.put(loc, tintBiome);
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

    /** A tracked (still liquid) cement block — water that is not really water. */
    private static boolean isCement(Block block) {
        return block != null
                && block.getType() == Material.WATER
                && trackedWater.containsKey(block.getLocation());
    }

    // =========================
    // 🔄 BIOME HANDLING
    // =========================
    private static void applyBiome(Location loc, Biome biome) {
        if (biome == null || loc.getWorld() == null) return;
        loc.getWorld().setBiome(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), biome);
    }

    private static void restoreBiome(Location loc, Biome biome) {
        if (biome == null || biome == tintBiome) return;
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

    private static Biome resolveBiome(String key, Biome def) {
        Biome biome = resolveBiome(key);
        return biome != null ? biome : def;
    }

    private static Material resolveMaterial(String name, Material def) {
        if (name == null || name.isBlank()) return def;
        Material material = Material.matchMaterial(name.trim());
        if (material == null) {
            ConsoleLogger.warn("[ConcreteBucket] Unknown block '" + name + "' in config — using " + def.name());
            return def;
        }
        return material;
    }

    private static Particle resolveParticle(String name, Particle def) {
        if (name == null || name.isBlank()) return def;
        try {
            return Particle.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            ConsoleLogger.warn("[ConcreteBucket] Unknown particle '" + name + "' in config — using " + def.name());
            return def;
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
