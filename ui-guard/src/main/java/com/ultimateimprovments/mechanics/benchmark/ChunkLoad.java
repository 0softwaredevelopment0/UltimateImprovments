package com.ultimateimprovments.mechanics.benchmark;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@code chunk} load — synchronous chunk loads and unload requests.
 * <p>
 * Collects already-generated chunk coordinates around the anchor, preferring a
 * band OUTSIDE the view distance (chunks inside it are kept loaded by players
 * and could never unload, which would silently turn the run into a no-op), then
 * alternates load/unload every cycle: {@code getChunkAt(x, z, true)} for loading
 * and {@code unloadChunkRequest(x, z)} for unloading.
 * <p>
 * Only pre-generated chunks are used, so the test never creates new terrain —
 * it measures the real cost of reading, hydrating and releasing existing
 * chunks. {@code work()} counts only state changes that actually happened:
 * a load counts when the chunk really got loaded, an unload is credited on
 * the next cycle only if the server actually unloaded the chunk (the request
 * is best-effort — chunks held by players or tickets stay loaded).
 * <p>
 * The initial loaded/unloaded state of every collected chunk is snapshotted
 * before the run and restored by {@link #stop()}: chunks that were loaded
 * before the test are re-loaded if the run unloaded them, and chunks the run
 * loaded but that were unloaded initially are unload-requested again.
 */
public final class ChunkLoad implements StressLoad {

    /** Outermost ring scanned when looking for generated chunks (in chunks). */
    private static final int MAX_SCAN_RING = 34;

    private final StressPower power;
    private final List<int[]> coords = new ArrayList<>();
    /** Chunks that were loaded when the run started — restored by stop(). */
    private final Set<Long> initiallyLoaded = new HashSet<>();
    /** Unload requests from the current cycle, verified (and counted) next cycle. */
    private final Set<Long> pendingUnloads = new HashSet<>();

    private World world;
    private boolean wantLoad;
    private long work;

    public ChunkLoad(StressPower power) {
        this.power = power;
    }

    @Override
    public void start(Location anchor) throws Exception {
        if (anchor == null || anchor.getWorld() == null) {
            throw new IllegalStateException("no world to run the chunk test in");
        }
        world = anchor.getWorld();
        int centerX = anchor.getBlockX() >> 4;
        int centerZ = anchor.getBlockZ() >> 4;
        int target = chunkCountFor(power);
        int view = Math.max(2, Bukkit.getViewDistance());

        // Pass 1: outside the view distance — those chunks CAN unload.
        collectRing(centerX, centerZ, view + 1, Math.min(MAX_SCAN_RING, view + 24), target);
        // Pass 2: near the anchor as a fallback (players may hold them loaded,
        // but an empty run would be worse than a partially effective one).
        if (coords.size() < target) {
            collectRing(centerX, centerZ, 0, view, target);
        }

        if (coords.isEmpty()) {
            throw new IllegalStateException("no generated chunks around "
                    + world.getName() + " " + anchor.getBlockX() + "," + anchor.getBlockZ()
                    + " — move somewhere with explored terrain");
        }
        for (int[] chunk : coords) {
            if (world.isChunkLoaded(chunk[0], chunk[1])) {
                initiallyLoaded.add(key(chunk[0], chunk[1]));
            }
        }
        wantLoad = true; // first cycle loads the unloaded chunks
    }

    @Override
    public void tick() {
        if (world == null) return;

        // 1. Credit unload requests of the previous cycle that actually
        //    unloaded (unloadChunkRequest is best-effort).
        pendingUnloads.removeIf(key -> {
            int x = (int) (key >> 32);
            int z = (int) (key & 0xFFFFFFFFL);
            if (!world.isChunkLoaded(x, z)) {
                work++;
            }
            return true;
        });

        // 2. Apply this cycle.
        for (int[] chunk : coords) {
            int x = chunk[0];
            int z = chunk[1];
            boolean loaded = world.isChunkLoaded(x, z);
            if (wantLoad) {
                if (loaded) continue;
                world.getChunkAt(x, z, true);
                if (world.isChunkLoaded(x, z)) {
                    work++;
                }
            } else {
                if (!loaded) continue;
                world.unloadChunkRequest(x, z);
                pendingUnloads.add(key(x, z));
            }
        }
        wantLoad = !wantLoad;
    }

    @Override
    public void stop() {
        if (world != null) {
            // Restore the initial loaded/unloaded state of every chunk we touched.
            for (int[] chunk : coords) {
                int x = chunk[0];
                int z = chunk[1];
                boolean wasLoaded = initiallyLoaded.contains(key(x, z));
                boolean loaded = world.isChunkLoaded(x, z);
                try {
                    if (wasLoaded && !loaded) {
                        // The run unloaded a chunk that was loaded before it.
                        world.getChunkAt(x, z, true);
                    } else if (!wasLoaded && loaded) {
                        // The run loaded a chunk that was unloaded before it.
                        world.unloadChunkRequest(x, z);
                    }
                } catch (Throwable ignored) {
                    // The world may already be closing during a full shutdown.
                }
            }
        }
        coords.clear();
        initiallyLoaded.clear();
        pendingUnloads.clear();
        world = null;
        wantLoad = false;
    }

    @Override
    public long work() {
        return work;
    }

    /**
     * Collects generated chunks ring by ring (closest first) until {@code need}
     * of them are found. Only ring coordinates are visited, so the scan stays
     * cheap even for the outer rings.
     */
    private void collectRing(int centerX, int centerZ, int minRing, int maxRing, int need) {
        Set<Long> seen = new HashSet<>();
        for (int[] chunk : coords) {
            seen.add(key(chunk[0], chunk[1]));
        }
        for (int ring = minRing; ring <= maxRing && coords.size() < need; ring++) {
            for (int x = centerX - ring; x <= centerX + ring && coords.size() < need; x++) {
                for (int z = centerZ - ring; z <= centerZ + ring; z++) {
                    if (Math.max(Math.abs(x - centerX), Math.abs(z - centerZ)) != ring) continue;
                    long k = key(x, z);
                    if (!seen.add(k)) continue;
                    if (!world.isChunkGenerated(x, z)) continue;
                    coords.add(new int[]{x, z});
                    if (coords.size() >= need) break;
                }
            }
        }
    }

    private static long key(int x, int z) {
        return (((long) x) << 32) | (z & 0xFFFFFFFFL);
    }

    /** Number of chunks touched per cycle for a power level: (2r + 1)^2. */
    private static int chunkCountFor(StressPower power) {
        int radius = switch (power) {
            case MINIMAL -> 1;
            case LOW -> 2;
            case MODERATE -> 4;
            case HIGH -> 6;
            case MAX -> 8;
        };
        return (2 * radius + 1) * (2 * radius + 1);
    }
}
