package com.ultimateimprovments.mechanics.features.sculk;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.SculkCatalyst;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * ♾ Infinitely growing sculk — the plugin keeps every sculk catalyst blooming.
 * <p>
 * A vanilla sculk catalyst only blooms when a mob dies nearby (the death's XP
 * becomes "charge" for the catalyst's {@code SculkSpreader}). This module fires
 * the bloom on its own cooldown through Paper's
 * {@link SculkCatalyst#bloom(Block, int)} API, so the vanilla spread never runs
 * out of charge and keeps converting blocks forever — the sculk patch grows
 * like a virus.
 * <p>
 * <b>Vanilla rules only.</b> The bloom is the real game mechanic, not an
 * emulation: it replaces blocks from the {@code #minecraft:sculk_replaceable}
 * tag (stone, deepslate, dirt, gravel, sand, ...), grows sculk veins over
 * surfaces, occasionally places sculk shriekers and plays the vanilla sound and
 * particles. Ores, containers, tile entities and builds made of other materials
 * are untouched because vanilla does not replace them either.
 * <p>
 * <b>Catalyst discovery.</b> Catalysts are not hard-coded: they are collected
 * from loaded chunks ({@link Chunk#getTileEntities()} only, so chunk scanning is
 * cheap) on chunk load, on player placement and once at startup for chunks that
 * were already loaded. A catalyst that disappears (broken, exploded, world
 * unloaded) is dropped on the next validation pass.
 * <p>
 * <b>Performance.</b> Only loaded chunks are processed and the plugin never
 * force-loads a chunk to reach a catalyst; at most
 * {@code max_catalysts_per_pulse} catalysts are bloomed per pulse in a
 * round-robin, and pulses are skipped while the server's average tick time is
 * above {@code max_mspt}. The vanilla bloom itself may touch the neighbouring
 * chunk of a loaded one, exactly as a mob death near a chunk border would.
 */
public final class SculkGrowthManager extends BukkitRunnable {

    private static SculkGrowthManager instance;

    private final Main plugin;

    // ===== CONFIG =====
    private boolean enabled = true;
    /** Pulse cooldown; 20 ticks = one second. */
    private int intervalTicks = 20;
    /** Charge added to the catalyst's vanilla spreader per pulse (= spread strength). */
    private int chargePerPulse = 10;
    /** TPS guard: how many catalysts may be bloomed in one pulse. */
    private int maxCatalystsPerPulse = 32;
    /** TPS guard: pulses are skipped while the average tick time is above this. */
    private double maxMspt = 50.0;

    // ===== STATE (main thread only) =====
    /** Every known catalyst; a stale entry is dropped on validation. */
    private final Set<CatalystKey> known = new HashSet<>();
    /** Round-robin order of the known catalysts. */
    private final Deque<CatalystKey> queue = new ArrayDeque<>();

    private BukkitTask task;

    private SculkGrowthManager(Main plugin) {
        this.plugin = plugin;
    }

    // =========================
    // INIT / SHUTDOWN / RELOAD
    // =========================

    public static void init(Main plugin) {
        if (instance != null) {
            instance.shutdown();
        }
        instance = new SculkGrowthManager(plugin);
        instance.reloadConfig();
        instance.discoverLoadedChunks();
        instance.startTimer();
        ConsoleLogger.info("[SCULK] Growth enabled (interval=" + instance.intervalTicks + "t, charge="
                + instance.chargePerPulse + ", catalysts=" + instance.known.size()
                + ", max/pulse=" + instance.maxCatalystsPerPulse + ")");
    }

    public static SculkGrowthManager getInstance() {
        return instance;
    }

    public static void reload() {
        if (instance == null) return;
        int previousInterval = instance.intervalTicks;
        boolean wasEnabled = instance.enabled;
        instance.reloadConfig();
        // The scheduler period is fixed when the task starts — restart it when
        // the interval changed, or when the module was just switched on.
        if (instance.task != null && (previousInterval != instance.intervalTicks || (!wasEnabled && instance.enabled))) {
            instance.startTimer();
        }
        ConsoleLogger.info("[SCULK] Config reloaded (enabled=" + instance.enabled
                + ", interval=" + instance.intervalTicks + "t, charge=" + instance.chargePerPulse + ")");
    }

    public static void shutdown() {
        if (instance == null) return;
        instance.stopTimer();
        instance.known.clear();
        instance.queue.clear();
        instance = null;
        ConsoleLogger.info("[SCULK] Growth disabled.");
    }

    private void reloadConfig() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("sculk");
        if (sec == null) {
            enabled = false;
            ConsoleLogger.warn("[SCULK] Config section 'sculk' not found — growth DISABLED."
                    + " Add it to configs/UI-World.toml to enable the mechanic.");
            return;
        }
        enabled = sec.getBoolean("enabled", true);
        intervalTicks = Math.max(1, sec.getInt("interval_ticks", 20));
        chargePerPulse = Math.max(1, sec.getInt("charge_per_pulse", 10));
        maxCatalystsPerPulse = Math.max(1, sec.getInt("max_catalysts_per_pulse", 32));
        maxMspt = Math.max(1.0, sec.getDouble("max_mspt", 50.0));
    }

    private void startTimer() {
        stopTimer();
        task = runTaskTimer(plugin, intervalTicks, intervalTicks);
    }

    private void stopTimer() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    // =========================
    // CATALYST TRACKING
    // =========================

    /** Registers a catalyst that was just placed by a player. */
    public void track(Block block) {
        track(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    /** Removes a catalyst that was broken, so it is not validated again. */
    public void untrack(Block block) {
        CatalystKey key = new CatalystKey(block.getWorld().getName(),
                LocationUtil.toKey(block.getX(), block.getY(), block.getZ()));
        known.remove(key);
    }

    private void track(World world, int x, int y, int z) {
        CatalystKey key = new CatalystKey(world.getName(), LocationUtil.toKey(x, y, z));
        if (known.add(key)) {
            queue.addLast(key);
        }
    }

    /**
     * Collects catalysts from a chunk's tile entities. Sculk catalysts are
     * block entities, so this does not walk the whole 16×16×height column.
     */
    public void scanChunk(Chunk chunk) {
        if (chunk == null) return;
        for (BlockState state : chunk.getTileEntities()) {
            if (state.getType() == Material.SCULK_CATALYST) {
                track(chunk.getWorld(), state.getX(), state.getY(), state.getZ());
            }
        }
    }

    /** Startup pass for chunks that were loaded before this module initialized. */
    private void discoverLoadedChunks() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                scanChunk(chunk);
            }
        }
        if (!known.isEmpty()) {
            ConsoleLogger.info("[SCULK] Discovered " + known.size() + " catalyst(s) in loaded chunks.");
        }
    }

    // =========================
    // PULSE — vanilla bloom per catalyst
    // =========================

    @Override
    public void run() {
        if (!enabled || queue.isEmpty()) return;
        if (Bukkit.getAverageTickTime() > maxMspt) return;

        // Scan at most one full rotation so a world full of unloaded chunks
        // cannot starve the catalysts that ARE loaded.
        int scansLeft = queue.size();
        int bloomed = 0;

        while (bloomed < maxCatalystsPerPulse && scansLeft-- > 0) {
            CatalystKey key = queue.pollFirst();
            if (key == null) break;
            if (!known.contains(key)) continue; // removed while queued

            World world = Bukkit.getWorld(key.world());
            if (world == null) {
                known.remove(key); // world unloaded
                continue;
            }

            int x = LocationUtil.getX(key.pos());
            int y = LocationUtil.getY(key.pos());
            int z = LocationUtil.getZ(key.pos());

            // Never force-load: unloaded catalysts wait for their chunk (or the
            // next chunk load re-discovers them).
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                queue.addLast(key);
                continue;
            }

            Block block = world.getBlockAt(x, y, z);
            if (block.getType() != Material.SCULK_CATALYST) {
                known.remove(key); // broken / replaced
                continue;
            }

            try {
                if (block.getState() instanceof SculkCatalyst catalyst) {
                    catalyst.bloom(block, chargePerPulse);
                }
            } catch (Throwable t) {
                ConsoleLogger.warn("[SCULK] Bloom failed at " + world.getName() + " "
                        + x + " " + y + " " + z + ": " + t.getMessage());
            }

            queue.addLast(key);
            bloomed++;
        }
    }

    /** World name + packed block key of one tracked catalyst. */
    private record CatalystKey(String world, long pos) {}
}
