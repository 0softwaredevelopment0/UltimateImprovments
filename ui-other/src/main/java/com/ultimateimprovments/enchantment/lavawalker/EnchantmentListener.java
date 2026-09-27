package com.ultimateimprovments.enchantment.lavawalker;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Listener: Lava Walker enchantment — Frost Walker for lava.
 * <p>
 * While a player WEARS boots with the Lava Walker charm, lava under their feet
 * temporarily turns into OBSIDIAN. Radius = charm level (level 1 → 1×1,
 * level 2 → 3×3, ...), hard-capped at {@link Enchantment#MAX_RADIUS} (16 → 31×31)
 * so extreme levels cannot stall the server.
 * <p>
 * Two candidate layers are converted per pass (both only where the block is LAVA):
 * the layer directly below the feet (walking on the crust extends it ahead — the
 * Frost Walker behavior) and the feet layer itself (swimming/wading in the lava
 * crusts it under you, so you bob up onto solid ground).
 * <p>
 * Melt behavior (frosted-ice style): every created obsidian block is registered
 * with a random melt delay of {@link #MELT_MIN_TICKS}–{@link #MELT_MAX_TICKS}
 * (20–45 s). A task running every {@link #MELT_SWEEP_TICKS} ticks reverts due
 * blocks to their EXACT original lava state (a source stays a source, a flow
 * keeps its level — the charm never multiplies lava sources) — except the
 * block(s) a player is currently standing on/in. The restored block gets a
 * forced physics update, so the lava starts flowing right away instead of
 * hanging frozen until an unrelated block update.
 * Only blocks THIS charm created are tracked; natural and player-placed
 * obsidian are never touched. The registry is in-memory: after a server restart
 * the timers are gone and the obsidian simply stays (a normal solid block).
 * <p>
 * No fire protection is granted (user's explicit choice): the very first step
 * into fresh lava can light the player on fire until the crust forms. Sneaking
 * disables the conversion (same UX as the Attack AoE sneak toggle). Conversion
 * runs one tick after the move event and never requires onGround — otherwise a
 * player swimming across a lava lake could never bootstrap the crust.
 */
public class EnchantmentListener implements Listener {

    /** Minimum melt delay in ticks (20 seconds). */
    private static final int MELT_MIN_TICKS = 400;

    /** Maximum melt delay in ticks (45 seconds). */
    private static final int MELT_MAX_TICKS = 900;

    /** Melt-sweep interval in ticks (1 second). */
    private static final long MELT_SWEEP_TICKS = 20L;

    /** Minimum horizontal movement (blocks) between conversion passes. */
    private static final double MOVE_THRESHOLD = 0.4;

    /** Retry delay for blocks whose melt is postponed (player standing on them). */
    private static final long MELT_RETRY_TICKS = 100L;

    /** Last position that triggered a conversion, per player (throttle). */
    private static final Map<UUID, Location> LAST_POS = new ConcurrentHashMap<>();

    /** Melt registry: created block position → entry. Written by conversion, drained by the sweep.
     *  Package-visible: {@link MeltStore} loads it at start and snapshots it for autosave. */
    static final Map<BlockPos, MeltEntry> MELTING = new ConcurrentHashMap<>();

    /** Blocks a player is currently standing on/in — the melt sweep skips those. */
    private static final Map<UUID, Set<BlockPos>> STANDING_ON = new ConcurrentHashMap<>();

    /** Immutable block position key (world name + coordinates). Package-visible for {@link MeltStore}. */
    record BlockPos(String world, int x, int y, int z) {
        static BlockPos of(Block block) {
            return new BlockPos(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        }

        /** Resolves back to the live block, or null when the world is unloaded. */
        Block toBlock() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : w.getBlockAt(x, y, z);
        }
    }

    /** Melt registry value: due tick + the captured EXACT original lava BlockData.
     *  Package-visible for {@link MeltStore}. */
    record MeltEntry(long dueTick, BlockData lavaData) {}

    // ─────────────────────────────────────────────────────────────
    //  EVENTS
    // ─────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) return;

        // Rotation-only movement must not trigger conversion passes.
        Location from = event.getFrom();
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        if (dx * dx + dz * dz < MOVE_THRESHOLD * MOVE_THRESHOLD) return;

        Player player = event.getPlayer();

        ItemStack boots = player.getInventory().getBoots();
        if (boots == null || boots.getType() == Material.AIR) return;
        int level = Enchantment.getLevel(boots);
        if (level <= 0) return;

        // Sneak disables the conversion (precise movement near lava).
        if (player.isSneaking()) return;

        // Throttle to one pass per MOVE_THRESHOLD blocks of travel.
        UUID uuid = player.getUniqueId();
        Location last = LAST_POS.get(uuid);
        if (last != null
                && last.getWorld() == to.getWorld()
                && to.distanceSquared(last) < MOVE_THRESHOLD * MOVE_THRESHOLD) {
            return;
        }
        LAST_POS.put(uuid, to.clone());

        // Heavy work happens next tick, never inline in the move event.
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> convertAround(player, to));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        LAST_POS.remove(uuid);
        STANDING_ON.remove(uuid);
    }

    // ─────────────────────────────────────────────────────────────
    //  CONVERSION
    // ─────────────────────────────────────────────────────────────

    /**
     * Converts lava around the player's feet into temporary obsidian.
     * Scheduled one tick after the move event that triggered it.
     */
    private static void convertAround(Player player, Location center) {
        if (!player.isOnline()) return;

        World world = center.getWorld();
        if (world == null) return;

        // Re-read the charm level next tick: boots may have been unequipped meanwhile.
        ItemStack boots = player.getInventory().getBoots();
        int level = boots == null ? 0 : Enchantment.getLevel(boots);
        if (level <= 0) return;

        int radius = Math.min(level, Enchantment.MAX_RADIUS) - 1; // level 1 → 1×1, 2 → 3×3, 16 → 31×31

        Block feet = center.getBlock();
        int feetX = feet.getX();
        int feetY = feet.getY();
        int feetZ = feet.getZ();

        // Shield both center-column layers from melting while the player is there.
        STANDING_ON.put(player.getUniqueId(), Set.of(
                new BlockPos(world.getName(), feetX, feetY - 1, feetZ),
                new BlockPos(world.getName(), feetX, feetY, feetZ)));

        convertLayer(world, feetX, feetZ, feetY - 1, radius);
        convertLayer(world, feetX, feetZ, feetY, radius);
    }

    /** Converts every LAVA block of a horizontal square layer into registered melting obsidian. */
    private static void convertLayer(World world, int centerX, int centerZ, int y, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Block target = world.getBlockAt(centerX + dx, y, centerZ + dz);
                if (target.getType() != Material.LAVA) continue;

                // Capture the EXACT lava state (source vs flow/fall level) so the
                // melt restores it verbatim instead of always a full source.
                BlockData lavaData = target.getBlockData();

                target.setType(Material.OBSIDIAN, false);
                MELTING.put(BlockPos.of(target), new MeltEntry(now() + meltDelay(), lavaData));
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  MELT SWEEP
    // ─────────────────────────────────────────────────────────────

    /** Reverts due obsidian blocks back to lava. Runs every {@link #MELT_SWEEP_TICKS} ticks. */
    private static void meltTick() {
        if (MELTING.isEmpty()) return;
        long now = now();

        for (Map.Entry<BlockPos, MeltEntry> entry : MELTING.entrySet()) {
            MeltEntry melt = entry.getValue();
            if (melt.dueTick() > now) continue;

            BlockPos pos = entry.getKey();

            World world = Bukkit.getWorld(pos.world());
            if (world == null) continue; // world not (yet) loaded — keep the entry, retry next sweep

            Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
            if (block.getType() != Material.OBSIDIAN) {           // replaced meanwhile → nothing to melt
                MELTING.remove(pos); // safe on ConcurrentHashMap during iteration
                continue;
            }

            if (isStandingOn(pos)) {
                MELTING.put(pos, new MeltEntry(now + MELT_RETRY_TICKS, melt.lavaData())); // player on it — retry in 5 s
                continue;
            }

            MELTING.remove(pos);

            // Restore the ORIGINAL lava state: a source stays a source, a flow
            // level 1-7 comes back as that same flow — no free new sources.
            // applyPhysics = true is REQUIRED: it notifies the neighbors and
            // schedules the liquid tick, so the lava immediately re-evaluates
            // its flow. Without the update the restored lava can sit frozen
            // (visibly still) until something touches it.
            block.setBlockData(melt.lavaData(), true);
        }
    }

    /** True when any online player is currently standing on/in the given block. */
    private static boolean isStandingOn(BlockPos pos) {
        for (Set<BlockPos> positions : STANDING_ON.values()) {
            if (positions.contains(pos)) return true;
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────
    //  REGISTRATION
    // ─────────────────────────────────────────────────────────────

    /** Registers the listener, restores persisted melts and starts the melt sweep + autosave. */
    public static void register(Main plugin) {
        Bukkit.getPluginManager().registerEvents(new EnchantmentListener(), plugin);
        MeltStore.load();
        Bukkit.getScheduler().runTaskTimer(plugin, EnchantmentListener::meltTick,
                MELT_SWEEP_TICKS, MELT_SWEEP_TICKS);
        MeltStore.startAutosave(plugin);
        ConsoleLogger.info("[LavaWalker] Listener registered (melt sweep every "
                + (MELT_SWEEP_TICKS / 20.0) + "s, melt delay 20-45s, radius cap "
                + Enchantment.MAX_RADIUS + ", melts persist in SQLite).");
    }

    /** Final synchronous save of the melt registry (async tasks do not survive disable). */
    public static void shutdown() {
        MeltStore.saveNow();
    }

    /** Random melt delay between MELT_MIN_TICKS and MELT_MAX_TICKS. */
    private static long meltDelay() {
        return MELT_MIN_TICKS + ThreadLocalRandom.current().nextLong(MELT_MAX_TICKS - MELT_MIN_TICKS + 1);
    }

    /** Current server tick (for melt deadlines). Package-visible for {@link MeltStore} tick↔millis conversion. */
    static long now() {
        return Bukkit.getCurrentTick();
    }
}
