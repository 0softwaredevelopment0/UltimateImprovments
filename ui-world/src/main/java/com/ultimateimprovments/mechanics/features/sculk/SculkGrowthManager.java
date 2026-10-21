package com.ultimateimprovments.mechanics.features.sculk;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.database.StateStore;
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
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
 * <p>
 * <b>World filter.</b> The whitelist and the blacklist can be enabled
 * independently and at the same time: the whitelist (when on) restricts growth
 * to its worlds, then the blacklist (when on) removes worlds from the
 * remaining set. Catalysts in filtered-out worlds are not tracked at all, so
 * they cost nothing; the registry is rebuilt when the config is reloaded.
 * <p>
 * <b>Runtime switch.</b> {@code /ui sculk stop|start} flips the growth on and
 * off and persists the choice in the {@code ui_state} table
 * ({@code sculk/growth_enabled}), so it survives restarts. The config
 * {@code [sculk] enabled} key stays the master switch: no command can override
 * it.
 */
public final class SculkGrowthManager extends BukkitRunnable {

    /** {@code ui_state} namespace/key of the persisted start/stop switch. */
    private static final String STATE_NAMESPACE = "sculk";
    private static final String STATE_GROWTH_ENABLED = "growth_enabled";

    private static SculkGrowthManager instance;

    private final Main plugin;

    // ===== CONFIG =====
    /** Master switch: {@code [sculk] enabled}. No command can override it. */
    private boolean configEnabled = true;
    /** Runtime switch persisted in the DB ({@code /ui sculk start|stop}). */
    private boolean growthEnabled = true;
    /** Pulse cooldown; 20 ticks = one second. */
    private int intervalTicks = 20;
    /** Charge added to the catalyst's vanilla spreader per pulse (= spread strength). */
    private int chargePerPulse = 10;
    /** TPS guard: how many catalysts may be bloomed in one pulse. */
    private int maxCatalystsPerPulse = 32;
    /** TPS guard: pulses are skipped while the average tick time is above this. */
    private double maxMspt = 50.0;

    // ===== WORLD FILTER (whitelist and blacklist are independent) =====
    private boolean whitelistEnabled = false;
    private boolean blacklistEnabled = false;
    private Set<String> whitelist = Set.of();
    private Set<String> blacklist = Set.of();

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
        // The runtime switch lives in the DB; the config value is only the
        // fallback for a fresh installation (no row yet).
        instance.growthEnabled = instance.loadPersistedToggle(instance.configEnabled);
        instance.discoverLoadedChunks();
        instance.startTimer();
        ConsoleLogger.info("[SCULK] Growth " + (instance.isRunning() ? "enabled" : "disabled")
                + " (interval=" + instance.intervalTicks + "t, charge=" + instance.chargePerPulse
                + ", catalysts=" + instance.known.size()
                + ", max/pulse=" + instance.maxCatalystsPerPulse + ")");
    }

    public static SculkGrowthManager getInstance() {
        return instance;
    }

    public static void reload() {
        if (instance == null) return;
        int previousInterval = instance.intervalTicks;
        instance.reloadConfig();
        // World filters may have changed: drop the registry and re-scan the
        // loaded chunks, so newly allowed worlds are picked up immediately.
        instance.known.clear();
        instance.queue.clear();
        instance.discoverLoadedChunks();
        if (instance.task != null && previousInterval != instance.intervalTicks) {
            instance.startTimer();
        }
        ConsoleLogger.info("[SCULK] Config reloaded (config=" + instance.configEnabled
                + ", growth=" + instance.growthEnabled
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
            configEnabled = false;
            ConsoleLogger.warn("[SCULK] Config section 'sculk' not found — growth DISABLED."
                    + " Add it to configs/UI-World.toml to enable the mechanic.");
            return;
        }
        configEnabled = sec.getBoolean("enabled", true);
        intervalTicks = Math.max(1, sec.getInt("interval_ticks", 20));
        chargePerPulse = Math.max(1, sec.getInt("charge_per_pulse", 10));
        maxCatalystsPerPulse = Math.max(1, sec.getInt("max_catalysts_per_pulse", 32));
        maxMspt = Math.max(1.0, sec.getDouble("max_mspt", 50.0));

        whitelistEnabled = sec.getBoolean("worlds.whitelist_enabled", false);
        blacklistEnabled = sec.getBoolean("worlds.blacklist_enabled", false);
        whitelist = normalizeWorlds(sec.getStringList("worlds.whitelist"));
        blacklist = normalizeWorlds(sec.getStringList("worlds.blacklist"));
    }

    /** Lower-cases and de-duplicates configured world names (order preserved). */
    private static Set<String> normalizeWorlds(List<String> names) {
        if (names == null || names.isEmpty()) return Set.of();
        Set<String> out = new LinkedHashSet<>();
        for (String name : names) {
            if (name != null && !name.isBlank()) {
                out.add(name.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Collections.unmodifiableSet(out);
    }

    /** Reads the persisted runtime switch; the config master switch is the fallback. */
    private boolean loadPersistedToggle(boolean fallback) {
        if (!DatabaseManager.isConnected()) return fallback;
        String raw = StateStore.get(STATE_NAMESPACE, STATE_GROWTH_ENABLED);
        return raw == null ? fallback : Boolean.parseBoolean(raw);
    }

    /** @return true when the switch was written to the database. */
    private boolean persistToggle() {
        if (!DatabaseManager.isConnected()) {
            ConsoleLogger.warn("[SCULK] Database is not connected — the start/stop state is not persisted.");
            return false;
        }
        StateStore.put(STATE_NAMESPACE, STATE_GROWTH_ENABLED, String.valueOf(growthEnabled));
        return true;
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
        if (!isWorldAllowed(world.getName())) return;
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
    // STATE / STATUS (commands)
    // =========================

    /** Config master switch ({@code [sculk] enabled}). */
    public boolean isConfigEnabled() { return configEnabled; }

    /** Runtime switch persisted in the DB ({@code /ui sculk start|stop}). */
    public boolean isGrowthEnabled() { return growthEnabled; }

    /** Effective state: the config master switch AND the runtime switch. */
    public boolean isRunning() { return configEnabled && growthEnabled; }

    /**
     * {@code /ui sculk start|stop}: flips the runtime switch and persists it in
     * the {@code ui_state} table, so the choice survives a restart.
     *
     * @return {@code true} when the switch was written to the database,
     *         {@code false} when the DB is unavailable (the in-memory switch
     *         still changes)
     */
    public boolean setGrowthEnabled(boolean value) {
        if (growthEnabled == value) return true;
        growthEnabled = value;
        boolean persisted = persistToggle();
        ConsoleLogger.info("[SCULK] Growth " + (value ? "started" : "stopped") + " by command"
                + (persisted ? " (persisted)." : " — DB unavailable, not persisted."));
        return persisted;
    }

    /** How many catalysts are registered for the pulse. */
    public int trackedCatalysts() { return known.size(); }

    /**
     * How many tracked catalysts would actually bloom right now: the world is
     * allowed by the filter, its chunk is loaded and the block is still a
     * sculk catalyst. Read-only — safe to call for {@code /ui sculk status}.
     */
    public int activeCatalysts() {
        int active = 0;
        for (CatalystKey key : known) {
            World world = Bukkit.getWorld(key.world());
            if (world == null || !isWorldAllowed(world.getName())) continue;
            int x = LocationUtil.getX(key.pos());
            int y = LocationUtil.getY(key.pos());
            int z = LocationUtil.getZ(key.pos());
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
            if (world.getBlockAt(x, y, z).getType() == Material.SCULK_CATALYST) {
                active++;
            }
        }
        return active;
    }

    /**
     * Whether the world passes the filter. The whitelist (when enabled)
     * restricts the allowed set, then the blacklist (when enabled) removes
     * worlds from it — both can be active at once.
     */
    public boolean isWorldAllowed(String worldName) {
        if (worldName == null) return false;
        String name = worldName.toLowerCase(Locale.ROOT);
        if (whitelistEnabled && !whitelist.contains(name)) return false;
        return !(blacklistEnabled && blacklist.contains(name));
    }

    public int getIntervalTicks() { return intervalTicks; }
    public int getChargePerPulse() { return chargePerPulse; }
    public int getMaxCatalystsPerPulse() { return maxCatalystsPerPulse; }
    public double getMaxMspt() { return maxMspt; }
    public boolean isWhitelistEnabled() { return whitelistEnabled; }
    public boolean isBlacklistEnabled() { return blacklistEnabled; }
    public Set<String> getWhitelist() { return whitelist; }
    public Set<String> getBlacklist() { return blacklist; }

    // =========================
    // PULSE — vanilla bloom per catalyst
    // =========================

    @Override
    public void run() {
        if (!isRunning() || queue.isEmpty()) return;
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
            if (!isWorldAllowed(world.getName())) {
                // Config may have changed without a rebuild — keep the entry
                // (the next reload re-discovers) but never bloom it.
                queue.addLast(key);
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
