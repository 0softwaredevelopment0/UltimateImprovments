package com.ultimateimprovments.mechanics.benchmark;

import com.ultimateimprovments.database.StateStore;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code block} load — chain block updates.
 * <p>
 * Builds a stone platform above the anchor and toggles a full layer of redstone
 * dust on top of it between {@code REDSTONE_WIRE} and {@code AIR} every cycle.
 * Each placement/removal recalculates the connections of the neighbouring dust,
 * which is exactly the cascading {@code neighborChanged} traffic this type is
 * meant to measure.
 * <p>
 * The original blocks of both layers are snapshotted before the test and
 * restored by {@link #stop()}; the affected chunks are pinned with plugin chunk
 * tickets for the whole run so the region can never be saved in its modified
 * state and then disappear from memory before the restore.
 * <p>
 * The snapshot is additionally persisted to the database (namespace
 * {@code stresstest}, key {@code block_region}) BEFORE the first block is
 * modified: if the server crashes (kill -9) mid-run, the modified platform can
 * end up in the world save where no PDC marker can identify it — the next
 * startup reads the record and restores the region
 * ({@link #restoreLeftoverRegion()}), mirroring the entity PDC sweep.
 */
public final class BlockLoad implements StressLoad {

    /** Vertical gap between the anchor and the platform (keeps the test off the player). */
    private static final int HEIGHT_OFFSET = 8;

    /** DB namespace of the leftover-region record. */
    private static final String DB_NAMESPACE = "stresstest";
    /** DB key of the leftover-region record. */
    private static final String DB_KEY = "block_region";

    private record Snapshot(int x, int y, int z, BlockData data) {}

    private final StressPower power;
    private final List<Snapshot> snapshot = new ArrayList<>();
    private final List<int[]> pinnedChunks = new ArrayList<>();

    private World world;
    private int x0;
    private int y0;
    private int z0;
    private int side;
    private boolean dustPlaced;
    private long work;
    private boolean started;

    public BlockLoad(StressPower power) {
        this.power = power;
    }

    @Override
    public void start(Location anchor) throws Exception {
        if (anchor == null || anchor.getWorld() == null) {
            throw new IllegalStateException("no world to run the block test in");
        }
        world = anchor.getWorld();
        side = sideFor(power);
        x0 = anchor.getBlockX() - side / 2;
        z0 = anchor.getBlockZ() - side / 2;
        y0 = anchor.getBlockY() + HEIGHT_OFFSET;
        y0 = Math.max(world.getMinHeight() + 1, Math.min(y0, world.getMaxHeight() - 2));

        // 1. Snapshot both layers BEFORE touching anything.
        for (int dx = 0; dx < side; dx++) {
            for (int dz = 0; dz < side; dz++) {
                for (int layer = 0; layer < 2; layer++) {
                    Block block = world.getBlockAt(x0 + dx, y0 + layer, z0 + dz);
                    snapshot.add(new Snapshot(block.getX(), block.getY(), block.getZ(), block.getBlockData()));
                }
            }
        }

        // 2. Persist the snapshot to the DB BEFORE the first modification —
        //    the crash-recovery record (restored at the next startup).
        persistRegionRecord();

        // 3. Pin the affected chunks so the modified state always stays in memory.
        Plugin owner = StressTestManager.ownerPlugin();
        int minCx = x0 >> 4;
        int maxCx = (x0 + side - 1) >> 4;
        int minCz = z0 >> 4;
        int maxCz = (z0 + side - 1) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                world.addPluginChunkTicket(cx, cz, owner);
                pinnedChunks.add(new int[]{cx, cz});
            }
        }

        // 4. Build the platform, then lay the initial dust layer.
        for (int dx = 0; dx < side; dx++) {
            for (int dz = 0; dz < side; dz++) {
                world.getBlockAt(x0 + dx, y0, z0 + dz).setType(Material.STONE, false);
            }
        }
        for (int dx = 0; dx < side; dx++) {
            for (int dz = 0; dz < side; dz++) {
                world.getBlockAt(x0 + dx, y0 + 1, z0 + dz).setType(Material.REDSTONE_WIRE, false);
            }
        }
        dustPlaced = true;
        started = true;
        ConsoleLogger.info("[StressTest] block region: " + side + "x" + side + " at "
                + world.getName() + " " + x0 + "," + y0 + "," + z0 + " (chunks pinned: "
                + pinnedChunks.size() + ")");
    }

    @Override
    public void tick() {
        if (!started || world == null) return;
        Material target = dustPlaced ? Material.AIR : Material.REDSTONE_WIRE;
        for (int dx = 0; dx < side; dx++) {
            for (int dz = 0; dz < side; dz++) {
                // applyPhysics = true: the update cascades to the neighbours
                world.getBlockAt(x0 + dx, y0 + 1, z0 + dz).setType(target, true);
                work++;
            }
        }
        dustPlaced = !dustPlaced;
    }

    @Override
    public void stop() {
        if (world == null) {
            reset();
            return;
        }
        // Restore the snapshot while the chunks are still pinned (loaded).
        // applyPhysics = false: the snapshot IS the exact original state, and
        // physics updates here would only cause one final neighbor-update
        // storm across the whole region right after the load stopped.
        int failures = 0;
        for (Snapshot saved : snapshot) {
            Block block = world.getBlockAt(saved.x(), saved.y(), saved.z());
            try {
                if (!block.getBlockData().matches(saved.data())) {
                    block.setBlockData(saved.data(), false);
                }
            } catch (Throwable t) {
                failures++;
                ConsoleLogger.warn("[StressTest] Failed to restore block " + saved.x() + ","
                        + saved.y() + "," + saved.z() + ": " + t.getMessage());
            }
        }
        if (failures == 0) {
            clearRegionRecord();
        } else {
            // The DB record stays, so the next startup retries the restore.
            ConsoleLogger.warn("[StressTest] " + failures + " block(s) failed to restore"
                    + " — the DB record is kept and retried at the next startup.");
        }
        Plugin owner = StressTestManager.ownerPlugin();
        for (int[] chunk : pinnedChunks) {
            try {
                world.removePluginChunkTicket(chunk[0], chunk[1], owner);
            } catch (Throwable ignored) {
                // The world may already be closing during a full shutdown.
            }
        }
        reset();
    }

    @Override
    public long work() {
        return work;
    }

    private void reset() {
        snapshot.clear();
        pinnedChunks.clear();
        started = false;
        dustPlaced = false;
        world = null;
    }

    // ============================================================
    // CRASH-RECOVERY RECORD (DB)
    // ============================================================

    /**
     * Writes the region + full block snapshot to the DB (namespace
     * {@code stresstest}, key {@code block_region}). Must be called BEFORE
     * the first block is modified: a crash after this point leaves a record
     * the next startup can restore from; a crash before it means nothing was
     * modified yet, so there is nothing to restore.
     * <p>
     * Format: first line {@code world;x0;y0;z0;side}, then one line per
     * block {@code x,y,z=block_data_as_string}.
     */
    private void persistRegionRecord() {
        StringBuilder sb = new StringBuilder(64 + snapshot.size() * 48);
        sb.append(world.getName()).append(';')
                .append(x0).append(';').append(y0).append(';').append(z0).append(';')
                .append(side).append('\n');
        for (Snapshot saved : snapshot) {
            sb.append(saved.x()).append(',').append(saved.y()).append(',').append(saved.z())
                    .append('=').append(saved.data().getAsString()).append('\n');
        }
        StateStore.put(DB_NAMESPACE, DB_KEY, sb.toString());
    }

    /** Deletes the crash-recovery record (after a successful restore). */
    private static void clearRegionRecord() {
        StateStore.remove(DB_NAMESPACE, DB_KEY);
    }

    /**
     * Restores a leftover region from the DB record left by a crashed run.
     * Called once at startup (together with the entity PDC sweep). Blocks
     * that already match the recorded data are skipped, so re-restoring an
     * untouched world is cheap.
     *
     * @return true when a record existed and was processed
     */
    static boolean restoreLeftoverRegion() {
        String payload = StateStore.get(DB_NAMESPACE, DB_KEY);
        if (payload == null || payload.isBlank()) {
            return false;
        }
        String[] lines = payload.split("\n");
        String[] header = lines[0].split(";");
        if (header.length < 5) {
            ConsoleLogger.warn("[StressTest] Leftover block-region record is malformed — dropping it.");
            clearRegionRecord();
            return true;
        }
        World regionWorld = Bukkit.getWorld(header[0]);
        if (regionWorld == null) {
            ConsoleLogger.warn("[StressTest] Leftover block-region record references unknown world '"
                    + header[0] + "' — dropping it.");
            clearRegionRecord();
            return true;
        }
        int restored = 0;
        int failed = 0;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String[] xyz = line.substring(0, eq).split(",");
            if (xyz.length < 3) continue;
            try {
                Block block = regionWorld.getBlockAt(
                        Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
                BlockData data = Bukkit.createBlockData(line.substring(eq + 1));
                if (!block.getBlockData().matches(data)) {
                    block.setBlockData(data, false);
                }
                restored++;
            } catch (Throwable t) {
                failed++;
                ConsoleLogger.warn("[StressTest] Failed to restore leftover block " + line + ": " + t.getMessage());
            }
        }
        if (failed == 0) {
            clearRegionRecord();
            ConsoleLogger.info("[StressTest] Restored " + restored
                    + " block(s) of a region left by a crashed stress test (DB record cleared).");
        } else {
            ConsoleLogger.warn("[StressTest] Leftover block-region restore had " + failed
                    + " failure(s) — the DB record is kept and retried at the next startup.");
        }
        return true;
    }

    /** Region edge length (blocks) for a power level. */
    private static int sideFor(StressPower power) {
        return switch (power) {
            case MINIMAL -> 4;
            case LOW -> 8;
            case MODERATE -> 12;
            case HIGH -> 16;
            case MAX -> 24;
        };
    }
}
