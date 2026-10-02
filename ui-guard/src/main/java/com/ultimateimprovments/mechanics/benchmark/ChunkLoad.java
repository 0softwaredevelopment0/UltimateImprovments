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
 * chunks. The number of chunks that actually changed state is what lands in the
 * report, not the size of the candidate list.
 */
public final class ChunkLoad implements StressLoad {

    /** Outermost ring scanned when looking for generated chunks (in chunks). */
    private static final int MAX_SCAN_RING = 34;

    private final StressPower power;
    private final List<int[]> coords = new ArrayList<>();
    private final Set<Long> loadedByUs = new HashSet<>();

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
        wantLoad = false; // every collected chunk starts unloaded -> first cycle loads
    }

    @Override
    public void tick() {
        if (world == null) return;
        for (int[] chunk : coords) {
            boolean loaded = world.isChunkLoaded(chunk[0], chunk[1]);
            if (loaded == wantLoad) continue;
            if (wantLoad) {
                world.getChunkAt(chunk[0], chunk[1], true);
                loadedByUs.add(key(chunk[0], chunk[1]));
            } else {
                world.unloadChunkRequest(chunk[0], chunk[1]);
                loadedByUs.remove(key(chunk[0], chunk[1]));
            }
            work++;
        }
        wantLoad = !wantLoad;
    }

    @Override
    public void stop() {
        if (world != null) {
            for (int[] chunk : coords) {
                if (!loadedByUs.contains(key(chunk[0], chunk[1]))) continue;
                if (world.isChunkLoaded(chunk[0], chunk[1])) {
                    try {
                        world.unloadChunkRequest(chunk[0], chunk[1]);
                    } catch (Throwable ignored) {
                        // The world may already be closing during a full shutdown.
                    }
                }
            }
        }
        coords.clear();
        loadedByUs.clear();
        world = null;
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
