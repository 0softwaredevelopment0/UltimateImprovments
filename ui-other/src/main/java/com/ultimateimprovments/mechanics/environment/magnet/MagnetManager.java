package com.ultimateimprovments.mechanics.environment.magnet;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.LocationUtil;
import com.ultimateimprovments.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.ultimateimprovments.structure.StructureMarker;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.ChunkSnapshot;

public class MagnetManager extends BukkitRunnable {

    private static MagnetManager instance;

    // =========================
    // ⚙ COORDINATE PACKING INTO long KEYS (delegated to StructureMarker)
    // =========================
    public static long toKey(int x, int y, int z) { return StructureMarker.toKey(x, y, z); }
    public static long toKey(Location loc) { return StructureMarker.toKey(loc); }
    public static int getX(long key) { return StructureMarker.getX(key); }
    public static int getZ(long key) { return StructureMarker.getZ(key); }
    public static int getY(long key) { return StructureMarker.getY(key); }

    // =========================
    // 🧲 MAGNET CLUSTER
    // =========================
    public static class MagnetCluster {
        public int id;
        public World world;
        public Set<Long> blockKeys = new HashSet<>();
        public Location center;
        public int power;

        // Running sums for O(1) center recomputation
        private long sumX, sumY, sumZ;

        /**
         * Adds a block to the cluster, updating the center in O(1).
         */
        void addBlock(long key) {
            if (blockKeys.add(key)) {
                sumX += getX(key);
                sumY += getY(key);
                sumZ += getZ(key);
                power = blockKeys.size();
                updateCenterFromSums();
            }
        }

        /**
         * Removes a block from the cluster, updating the center in O(1).
         */
        void removeBlock(long key) {
            if (blockKeys.remove(key)) {
                sumX -= getX(key);
                sumY -= getY(key);
                sumZ -= getZ(key);
                power = blockKeys.size();
                if (!blockKeys.isEmpty()) {
                    updateCenterFromSums();
                } else {
                    center = null;
                }
            }
        }

        /**
         * Full center recomputation (used when loading from the DB).
         */
        void recalculateCenter() {
            if (blockKeys.isEmpty()) return;
            sumX = 0; sumY = 0; sumZ = 0;
            for (long key : blockKeys) {
                sumX += getX(key);
                sumY += getY(key);
                sumZ += getZ(key);
            }
            updateCenterFromSums();
        }

        private void updateCenterFromSums() {
            int size = blockKeys.size();
            if (size == 0) {
                center = null;
                power = 0;
                return;
            }
            center = new Location(world,
                    (int) Math.round((double) sumX / size),
                    (int) Math.round((double) sumY / size),
                    (int) Math.round((double) sumZ / size));
            power = size;
        }

        boolean contains(Location loc) {
            return blockKeys.contains(toKey(loc));
        }
    }

    // =========================
    // DATA
    // =========================
    private static final Map<Long, MagnetCluster> locationToCluster = new HashMap<>();
    private static final Map<Integer, MagnetCluster> clustersById = new HashMap<>();
    private static int nextId = 1;

    // Players whose metallic status needs rechecking (dropped an item)
    private static final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();

    /**
     * Marks a player for metallic status recheck.
     * Called from MagnetEventListener when an item is dropped.
     */
    public static void markPlayerDirty(UUID uuid) {
        dirtyPlayers.add(uuid);
    }

    // =========================
    // INIT — rebuild from Marker entities (no SQLite)
    // =========================
    public static void init(Main plugin) {
        instance = new MagnetManager();
        MagnetConfig.reloadConfig();
        rebuildFromMarkers();
        instance.runTaskTimer(plugin, 20L, MagnetConfig.getIntervalTicks());
    }

    // =========================
    // 🔥 FAST FLOOD-FILL
    // =========================
    private static final int[][] DIR = {
        {1, 0, 0}, {-1, 0, 0},
        {0, 1, 0}, {0, -1, 0},
        {0, 0, 1}, {0, 0, -1}
    };

    private static Set<Long> floodFillFast(World world, int sx, int sy, int sz) {
        if (world == null) return new HashSet<>(0);
        
        // ════════════════════════════════════════
        // 🛡 Check: is the chunk of the starting point loaded?
        // If not — we can't scan the structure.
        // ════════════════════════════════════════
        if (!world.isChunkLoaded(sx >> 4, sz >> 4)) return new HashSet<>(0);
        
        Set<Long> visited = new HashSet<>();
        Deque<int[]> queue = new ArrayDeque<>();
        long startKey = toKey(sx, sy, sz);
        visited.add(startKey);
        queue.add(new int[]{sx, sy, sz});
        while (!queue.isEmpty()) {
            int[] pos = queue.pollFirst();
            int x = pos[0], y = pos[1], z = pos[2];
            for (int[] d : DIR) {
                int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                long nk = toKey(nx, ny, nz);
                if (visited.contains(nk)) continue;
                // ════════════════════════════════════════
                // 🛡 Check: is the neighbor block's chunk loaded?
                // If not — skip (the structure may be
                // incomplete, but that's better than a crash)
                // ════════════════════════════════════════
                if (!world.isChunkLoaded(nx >> 4, nz >> 4)) continue;
                if (world.getType(nx, ny, nz) == Material.LODESTONE) {
                    visited.add(nk);
                    queue.addLast(new int[]{nx, ny, nz});
                }
            }
        }
        return visited;
    }

    private static Set<Long> floodFillFast(Location start) {
        if (start == null || start.getWorld() == null) return new HashSet<>(0);
        return floodFillFast(start.getWorld(), start.getBlockX(), start.getBlockY(), start.getBlockZ());
    }

    // =========================
    // REBUILD FROM MARKERS
    // =========================
    public static void rebuildFromMarkers() {
        locationToCluster.clear();
        clustersById.clear();
        nextId = 1;

        Map<UUID, Set<Long>> markerGroups = new HashMap<>();
        Map<UUID, World> foundWorlds = new HashMap<>();

        for (Map.Entry<String, StructureMarker.StructureData> entry : StructureMarker.getAllEntries()) {
            if (!"magnet".equals(entry.getValue().type())) continue;

            UUID uuid = entry.getValue().uuid();
            String fk = entry.getKey();
            long posKey = toKey(StructureMarker.parseX(fk), StructureMarker.parseY(fk), StructureMarker.parseZ(fk));
            markerGroups.computeIfAbsent(uuid, k -> new HashSet<>()).add(posKey);

            if (!foundWorlds.containsKey(uuid)) {
                String wUid = entry.getValue().worldUid();
                if (wUid != null) {
                    for (World w : Bukkit.getWorlds()) {
                        if (w.getUID().toString().equals(wUid)) {
                            foundWorlds.put(uuid, w);
                            break;
                        }
                    }
                }
            }
        }

        Set<UUID> usedUuids = new HashSet<>();
        for (Map.Entry<UUID, Set<Long>> group : markerGroups.entrySet()) {
            if (group.getValue().isEmpty()) continue;
            World world = foundWorlds.get(group.getKey());
            if (world == null) continue;

            MagnetCluster cluster = new MagnetCluster();
            cluster.id = nextId++;
            cluster.world = world;
            cluster.blockKeys = new HashSet<>(group.getValue());
            cluster.recalculateCenter();

            for (long key : group.getValue()) locationToCluster.put(key, cluster);
            clustersById.put(cluster.id, cluster);
            usedUuids.add(group.getKey());
        }

        StructureMarker.purgeOrphaned(usedUuids);
        // Log suppressed — too spammy on server start
    }

    // =========================
    // ACTIVATE (synchronous)
    // =========================
    public static void activate(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return;
        long key = toKey(loc);
        if (locationToCluster.containsKey(key)) return;

        Set<Long> connected = floodFillFast(loc);
        if (connected.isEmpty()) return;

        UUID uuid = UUID.randomUUID();
        MagnetCluster cluster = new MagnetCluster();
        cluster.id = nextId++;
        cluster.world = loc.getWorld();
        cluster.blockKeys = new HashSet<>(connected);
        cluster.recalculateCenter();

        for (long blockKey : connected) {
            locationToCluster.put(blockKey, cluster);
            Location blockLoc = new Location(cluster.world, getX(blockKey), getY(blockKey), getZ(blockKey));
            StructureMarker.place(blockLoc, "magnet", uuid);
        }
        clustersById.put(cluster.id, cluster);

        addParticleEffect(cluster.center, cluster.blockKeys.size());

        ConsoleLogger.info(
                "[Magnet] Activated cluster #" + cluster.id
                        + " with " + connected.size() + " blocks"
                        + " at center " + cluster.center
        );
    }

    // =========================
    // 🔥 FAST FLOOD-FILL WITH ChunkSnapshot (thread-safe for async)
    // =========================
    private static Set<Long> floodFillFastSnapshots(World world, int sx, int sy, int sz) {
        if (world == null) return new HashSet<>(0);
        if (!world.isChunkLoaded(sx >> 4, sz >> 4)) return new HashSet<>(0);

        Map<Long, ChunkSnapshot> snapshots = new HashMap<>();

        Set<Long> visited = new HashSet<>();
        Deque<int[]> queue = new ArrayDeque<>();
        long startKey = toKey(sx, sy, sz);
        visited.add(startKey);
        queue.add(new int[]{sx, sy, sz});

        while (!queue.isEmpty()) {
            int[] pos = queue.pollFirst();
            int x = pos[0], y = pos[1], z = pos[2];
            for (int[] d : DIR) {
                int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                long nk = toKey(nx, ny, nz);
                if (visited.contains(nk)) continue;
                if (!world.isChunkLoaded(nx >> 4, nz >> 4)) continue;

                // Get or create a ChunkSnapshot — a thread-safe immutable copy
                long chunkKey = ((long)(nx >> 4) << 32) | (nz >> 4) & 0xFFFFFFFFL;
                ChunkSnapshot snap = snapshots.get(chunkKey);
                if (snap == null) {
                    snap = world.getChunkAt(nx >> 4, nz >> 4).getChunkSnapshot(true, false, false);
                    snapshots.put(chunkKey, snap);
                }

                if (snap.getBlockType(nx & 15, ny, nz & 15) == Material.LODESTONE) {
                    visited.add(nk);
                    queue.addLast(new int[]{nx, ny, nz});
                }
            }
        }
        return visited;
    }

    // =========================
    // ACTIVATE ASYNC (for command-driven assembly)
    // Runs the flood-fill asynchronously to avoid freezing the server.
    // Uses ChunkSnapshot for thread-safe block reads.
    // The player gets notified about the start and end of the assembly.
    // =========================
    public static void activateAsync(Location loc, Player player) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Invalid position!"));
            return;
        }
        long key = toKey(loc);
        if (locationToCluster.containsKey(key)) {
            player.sendMessage(MessageUtil.parse("<yellow>Магнит уже активен на этом месте!"));
            return;
        }

        player.sendMessage(MessageUtil.parse("<dark_gray>[<aqua>Magnet<dark_gray>] <gray>Starting structure scan..."));
        player.sendMessage(MessageUtil.parse("<dark_gray>[<aqua>Magnet<dark_gray>] <gray>Please wait. This may take a while"));
        player.sendMessage(MessageUtil.parse("<dark_gray>[<aqua>Magnet<dark_gray>] <gray>with a large number of blocks."));

        World world = loc.getWorld();
        int sx = loc.getBlockX(), sy = loc.getBlockY(), sz = loc.getBlockZ();

        Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(), () -> {
            try {
                Set<Long> connected = floodFillFastSnapshots(world, sx, sy, sz);

                Bukkit.getScheduler().runTask(Main.getInstance(), () ->
                        finishActivation(connected, world, key, player)
                );
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                    player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Error during async scan!"));
                    player.sendMessage(MessageUtil.parse("<gray>Trying sync mode..."));

                    // Fallback: synchronous execution
                    Set<Long> connected = floodFillFast(world, sx, sy, sz);
                    finishActivation(connected, world, key, player);
                });
                ConsoleLogger.error(
                        "[Magnet] Async activation error: " + e.getMessage()
                );
            }
        });
    }

    /**
     * Completes activation: creates the cluster, registers blocks,
     * saves to the DB, shows particles and sends the result to the player.
     */
    private static void finishActivation(Set<Long> connected, World world, long key, Player player) {
        if (connected.isEmpty()) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Magnet not assembled: LODESTONE structure not found!"));
            return;
        }

        if (locationToCluster.containsKey(key)) {
            player.sendMessage(MessageUtil.parse("<yellow>Магнит уже активен на этом месте!"));
            return;
        }

        UUID uuid = UUID.randomUUID();
        MagnetCluster cluster = new MagnetCluster();
        cluster.id = nextId++;
        cluster.world = world;
        cluster.blockKeys = new HashSet<>(connected);
        cluster.recalculateCenter();

        for (long blockKey : connected) {
            locationToCluster.put(blockKey, cluster);
            Location blockLoc = new Location(cluster.world, getX(blockKey), getY(blockKey), getZ(blockKey));
            StructureMarker.place(blockLoc, "magnet", uuid);
        }
        clustersById.put(cluster.id, cluster);

        addParticleEffect(cluster.center, cluster.blockKeys.size());

        int power = cluster.blockKeys.size();
        String powerDesc = getMagnetPowerTierStatic(power);
        int magnetRadius = getClusterRadius(power);

        player.sendMessage(MessageUtil.parse("<green>✔ <white>Magnet assembled!"));
        player.sendMessage(MessageUtil.parse("<dark_gray>┃ <gray>Blocks in structure: <white>" + power + " <gray>pcs"));
        player.sendMessage(MessageUtil.parse("<dark_gray>┃ <gray>Attraction force: " + powerDesc));
        if (cluster.center != null) {
            player.sendMessage(MessageUtil.parse("<dark_gray>┃ <gray>Center: <white>"
                    + cluster.center.getBlockX() + " "
                    + cluster.center.getBlockY() + " "
                    + cluster.center.getBlockZ()));
        }
        player.sendMessage(MessageUtil.parse("<dark_gray>┃ <gray>Radius: <white>" + magnetRadius + " <gray>blocks (min. " + MagnetConfig.getMinRadius() + ")"));

        ConsoleLogger.info(
                "[Magnet] Activated cluster #" + cluster.id
                        + " with " + connected.size() + " blocks"
                        + " at center " + cluster.center
        );
    }

    /**
     * Returns the magnet tier name by power.
     */
    public static String getMagnetPowerTierStatic(int power) {
        if (power >= 10000000) return "<obfuscated>✧ <dark_red>✧✧ ABSOLUTE INFINITY ✧✧ <obfuscated>✧ <dark_gray>(" + power + ")";
        if (power >= 5000000) return "<dark_red>✧✧ INFINITE ABYSS ✧✧ <dark_gray>(" + power + ")";
        if (power >= 2500000) return "<red>✦ COSMIC CATASTROPHE ✦ <dark_gray>(" + power + ")";
        if (power >= 1000000) return "<light_purple>✧ PRIMORDIAL SINGULARITY ✧ <dark_gray>(" + power + ")";
        if (power >= 500000) return "<gold>☠ INCOMPREHENSIBLE ☠ <dark_gray>(" + power + ")";
        if (power >= 250000) return "<dark_aqua>✦ GODLIKE ✦ <dark_gray>(" + power + ")";
        if (power >= 100000) return "<dark_red>✧✧✧ ALL-CRUSHING SINGULARITY ✧✧✧ <dark_gray>(" + power + ")";
        if (power >= 50000) return "<red>☠ ABSOLUTE SINGULARITY ☠ <dark_gray>(" + power + ")";
        if (power >= 25000) return "<gold>⚡ DIVINE SINGULARITY ⚡ <dark_gray>(" + power + ")";
        if (power >= 10000) return "<light_purple>✧✧ UNMATCHED ✧✧ <dark_gray>(" + power + ")";
        if (power >= 5000) return "<dark_purple>✦ TRANSCENDENT ✦ <dark_gray>(" + power + ")";
        if (power >= 2500) return "<blue>⚜ SINGULAR ⚜ <dark_gray>(" + power + ")";
        if (power >= 1000) return "<dark_aqua>✦ INFINITE ✦ <dark_gray>(" + power + ")";
        if (power >= 500) return "<dark_purple>✧✧ ABSOLUTE ✧✧ <dark_gray>(" + power + ")";
        if (power >= 300) return "<dark_purple>☯ COSMIC ☯ <dark_gray>(" + power + ")";
        if (power >= 200) return "<light_purple>✦ TITANIC ✦ <dark_gray>(" + power + ")";
        if (power >= 150) return "<light_purple>◈ LEGENDARY ◈ <dark_gray>(" + power + ")";
        if (power >= 100) return "<red>☆ INCREDIBLE ☆ <dark_gray>(" + power + ")";
        if (power >= 75) return "<red>♦ EXTREME ♦ <dark_gray>(" + power + ")";
        if (power >= 50) return "<gold>★ EXCEPTIONAL ★ <dark_gray>(" + power + ")";
        if (power >= 30) return "<gold>⬆ VERY STRONG ⬆ <dark_gray>(" + power + ")";
        if (power >= 20) return "<yellow>⬆ STRONG ⬆ <dark_gray>(" + power + ")";
        if (power >= 12) return "<yellow>⬆ ABOVE AVERAGE ⬆ <dark_gray>(" + power + ")";
        if (power >= 7) return "<green>➤ AVERAGE ➤ <dark_gray>(" + power + ")";
        if (power >= 4) return "<gray>➤ BELOW AVERAGE ➤ <dark_gray>(" + power + ")";
        if (power >= 2) return "<gray>▸ WEAK ▸ <dark_gray>(" + power + ")";
        return "<gray>▸ VERY WEAK ▸ <dark_gray>(" + power + ")";
    }

    // =========================
    // DEACTIVATE
    // =========================
    public static void deactivate(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return;
        MagnetCluster cluster = locationToCluster.get(toKey(loc));
        if (cluster == null) return;
        deactivateCluster(cluster);
    }

    private static void deactivateCluster(MagnetCluster cluster) {
        for (long blockKey : cluster.blockKeys) {
            locationToCluster.remove(blockKey);
            Location bl = new Location(cluster.world, getX(blockKey), getY(blockKey), getZ(blockKey));
            StructureMarker.removeAt(bl);
        }
        clustersById.remove(cluster.id);
        if (cluster.center != null && cluster.center.getWorld() != null) {
            addParticleEffect(cluster.center, cluster.blockKeys.size());
        }
        ConsoleLogger.info(
                "[Magnet] Deactivated cluster #" + cluster.id
                        + " (" + cluster.power + " blocks)"
        );
    }

    // =========================
    // BLOCK DESTROYED
    // If the cluster is small — recompute synchronously (fast).
    // If large — asynchronously to avoid freezing the server.
    // =========================
    public static boolean onBlockBroken(Location loc, Player breaker) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return false;
        long key = toKey(loc);
        MagnetCluster cluster = locationToCluster.get(key);
        if (cluster == null) return false;

        // Full deactivation of the whole cluster when any block is destroyed
        deactivateCluster(cluster);

        if (breaker != null) {
            breaker.sendMessage(MessageUtil.parse("<dark_red>\u26a0</dark_red> <red>Magnet deactivated (block broken)!</red>"));
        }

        ConsoleLogger.info("[Magnet] Deactivated cluster #" + cluster.id
                + " due to block break at " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ());
        return true;
    }

    // =========================
    // BLOCK PLACED
    // Optimization: instead of a full flood-fill — just add the block
    // to a neighboring cluster (or merge clusters).
    // A full recompute (flood-fill) only runs if the cluster >= ASYNC_THRESHOLD
    // and the block could join two clusters — but even then we just merge them.
    // =========================
    public static void onBlockPlaced(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return;
        long key = toKey(loc);
        if (locationToCluster.containsKey(key)) return;

        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();
        long[] neighborKeys = {
            toKey(bx + 1, by, bz), toKey(bx - 1, by, bz),
            toKey(bx, by + 1, bz), toKey(bx, by - 1, bz),
            toKey(bx, by, bz + 1), toKey(bx, by, bz - 1)
        };

        // Collect all unique neighboring clusters
        Set<MagnetCluster> adjacentClusters = new LinkedHashSet<>();
        for (long nk : neighborKeys) {
            MagnetCluster c = locationToCluster.get(nk);
            if (c != null) adjacentClusters.add(c);
        }

        if (adjacentClusters.isEmpty()) return;

        if (adjacentClusters.size() == 1) {
            MagnetCluster cluster = adjacentClusters.iterator().next();
            cluster.addBlock(key);
            locationToCluster.put(key, cluster);
            // Marker on the new block
            UUID uuid = findUuidFromNeighbor(loc, neighborKeys);
            if (uuid != null) StructureMarker.place(loc, "magnet", uuid);

            ConsoleLogger.info(
                    "[Magnet] Cluster #" + cluster.id + " expanded: "
                            + cluster.blockKeys.size() + " blocks"
            );
        } else {
            Iterator<MagnetCluster> it = adjacentClusters.iterator();
            MagnetCluster primary = it.next();
            UUID primaryUuid = findUuidFromNeighbor(loc, neighborKeys);

            while (it.hasNext()) {
                MagnetCluster other = it.next();
                for (long bk : other.blockKeys) {
                    locationToCluster.put(bk, primary);
                    primary.addBlock(bk);
                    // Update the Marker on the other block — switch its UUID to primary
                    Location bl = new Location(primary.world, getX(bk), getY(bk), getZ(bk));
                    StructureMarker.removeAt(bl);
                    if (primaryUuid != null) StructureMarker.place(bl, "magnet", primaryUuid);
                }
                clustersById.remove(other.id);
            }

            primary.addBlock(key);
            locationToCluster.put(key, primary);
            if (primaryUuid != null) StructureMarker.place(loc, "magnet", primaryUuid);

            ConsoleLogger.info(
                    "[Magnet] Clusters merged into #" + primary.id
                            + ": " + primary.blockKeys.size() + " blocks"
            );
        }
    }

    /** Finds the magnet UUID from a neighbor block's Marker */
    private static UUID findUuidFromNeighbor(Location loc, long[] neighborKeys) {
        for (long nk : neighborKeys) {
            Location bl = new Location(loc.getWorld(), getX(nk), getY(nk), getZ(nk));
            StructureMarker.StructureData data = StructureMarker.getAt(bl);
            if (data != null && "magnet".equals(data.type())) return data.uuid();
        }
        return null;
    }

    // =========================
    // QUERIES
    // =========================
    public static boolean isActive(Location loc) {
        loc = LocationUtil.normalize(loc);
        return loc != null && locationToCluster.containsKey(toKey(loc));
    }

    public static boolean isActiveAt(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return false;
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();
        if (locationToCluster.containsKey(toKey(bx, by, bz))) return true;
        return locationToCluster.containsKey(toKey(bx + 1, by, bz))
            || locationToCluster.containsKey(toKey(bx - 1, by, bz))
            || locationToCluster.containsKey(toKey(bx, by + 1, bz))
            || locationToCluster.containsKey(toKey(bx, by - 1, bz))
            || locationToCluster.containsKey(toKey(bx, by, bz + 1))
            || locationToCluster.containsKey(toKey(bx, by, bz - 1));
    }

    public static int getMagnetPower(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return 1;
        MagnetCluster cluster = locationToCluster.get(toKey(loc));
        return cluster != null ? cluster.blockKeys.size() : 1;
    }

    public static Location getMagnetCenter(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return null;
        MagnetCluster cluster = locationToCluster.get(toKey(loc));
        return cluster != null ? cluster.center.clone() : null;
    }

    // =========================
    // DYNAMIC RADIUS
    // =========================
    public static int getMagnetRadius(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return MagnetConfig.getMinRadius();
        MagnetCluster cluster = locationToCluster.get(toKey(loc));
        return cluster != null ? getClusterRadius(cluster.blockKeys.size()) : MagnetConfig.getMinRadius();
    }

    public static int getClusterRadiusForPower(int power) {
        double t = Math.sqrt((double) power / MagnetConfig.getPowerNormalize());
        return (int) Math.round(MagnetConfig.getMinRadius() + (MagnetConfig.getMaxRadius() - MagnetConfig.getMinRadius()) * t);
    }

    private static int getClusterRadius(int power) {
        return getClusterRadiusForPower(power);
    }

    // =========================
    // INTERNAL ACCESS (for MagnetDatabase)
    // =========================
    public static int getClusterCount() { return clustersById.size(); }
    public static Collection<MagnetCluster> getClusters() { return clustersById.values(); }
    static Map<Long, MagnetCluster> getLocationMapInternal() { return locationToCluster; }
    static Map<Integer, MagnetCluster> getClustersByIdInternal() { return clustersById; }
    static void setNextId(int id) { nextId = id; }

    public static Set<UUID> getDirtyPlayers() { return dirtyPlayers; }

    // =========================
    // ACTIVATION PARTICLES
    // =========================
    public static void addParticleEffect(Location loc) {
        addParticleEffect(loc, 30);
    }

    public static void addParticleEffect(Location loc, int power) {
        if (loc == null || loc.getWorld() == null) return;
        World world = loc.getWorld();
        Location center = loc.clone().add(0.5, 0.5, 0.5);

        int particleCount = Math.min(30 + (int)(Math.sqrt(power) * 1.5), 80);
        world.spawnParticle(Particle.END_ROD, center, particleCount, 0.5, 0.5, 0.5, 0.1);
        world.spawnParticle(Particle.ELECTRIC_SPARK, center,
                Math.max(1, particleCount / 3), 0.5, 0.5, 0.5, 0);

        if (power >= 100) {
            world.spawnParticle(Particle.CRIT, center,
                    Math.min(power / 10, MagnetConfig.getParticleCritMax()), 0.3, 0.3, 0.3, 0);
        }
        if (power >= 1000) {
            world.spawnParticle(Particle.PORTAL, center,
                    Math.min(power / 20, MagnetConfig.getParticlePortalMax()), 0.5, 0.5, 0.5, 0.02);
        }
        if (power >= 10000) {
            world.spawnParticle(Particle.FLASH, center, 1, 0, 0, 0, 0, Color.WHITE);
            world.spawnParticle(Particle.SONIC_BOOM, center, 1, 0.5, 0.5, 0.5, 0);
        }
        if (power >= 100000) {
            world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1, 0, 0, 0, 0);
        }

        world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE,
                Math.min(1.0f + (float)Math.sqrt(power) * 0.05f, 2.0f),
                Math.min(1.5f + (float)Math.sqrt(power) * 0.01f, 2.0f));
    }

    // =========================
    // RUN (every tick)
    // =========================
    @Override
    public void run() {
        if (!MagnetConfig.isEnabled() || clustersById.isEmpty()) return;

        List<Integer> toRemove = new ArrayList<>();

        for (MagnetCluster cluster : clustersById.values()) {
            try {
                World world = cluster.world;
                if (world == null) {
                    toRemove.add(cluster.id);
                    continue;
                }

                if (cluster.blockKeys.isEmpty()) {
                    toRemove.add(cluster.id);
                    continue;
                }

                long firstKey = cluster.blockKeys.iterator().next();
                int fx = getX(firstKey), fy = getY(firstKey), fz = getZ(firstKey);

                // ════════════════════════════════════════
                // 🛡 Check: is the chunk loaded?
                // If the chunk isn't loaded — skip the cluster,
                // BUT do NOT delete it (it may load later).
                // ════════════════════════════════════════
                if (!world.isChunkLoaded(fx >> 4, fz >> 4)) {
                    continue;
                }

                if (world.getType(fx, fy, fz) != Material.LODESTONE) {
                    toRemove.add(cluster.id);
                    continue;
                }

                Location center = cluster.center.clone().add(0.5, 0.5, 0.5);
                int power = cluster.blockKeys.size();

                // ════════════════════════════════════════
                // PARTICLES — config limits
                // ════════════════════════════════════════
                int particleCount = Math.min(8 + (int)(Math.sqrt(power) * 1.5), MagnetConfig.getParticleCenterMax());
                world.spawnParticle(Particle.END_ROD, center, particleCount, 0.5, 0.5, 0.5, 0);
                world.spawnParticle(Particle.ELECTRIC_SPARK, center,
                        Math.max(1, particleCount / 2), 0.5, 0.5, 0.5, 0);

                if (power >= 5) {
                    List<Long> keyList = new ArrayList<>(cluster.blockKeys);
                    int maxBlock = Math.min(keyList.size(), MagnetConfig.getParticleBlocksMax());
                    int step = keyList.size() / maxBlock;
                    if (step == 0) step = 1;
                    for (int i = 0; i < maxBlock; i++) {
                        long k = keyList.get(i * step);
                        Location bp = new Location(world, getX(k) + 0.5, getY(k) + 0.5, getZ(k) + 0.5);
                        world.spawnParticle(Particle.ELECTRIC_SPARK, bp, 1, 0.2, 0.2, 0.2, 0);
                    }
                }

                if (power >= 100) {
                    world.spawnParticle(Particle.CRIT, center,
                            Math.min(power / 10, MagnetConfig.getParticleCritMax()), 0.3, 0.3, 0.3, 0);
                }
                if (power >= 1000) {
                    world.spawnParticle(Particle.PORTAL, center,
                            Math.min(power / 20, MagnetConfig.getParticlePortalMax()), 0.4, 0.4, 0.4, 0.02);
                }
                if (power >= 10000) {
                    world.spawnParticle(Particle.FLASH, center, 1, 0, 0, 0, 0, Color.WHITE);
                    world.spawnParticle(Particle.SONIC_BOOM, center, 1, 0.3, 0.3, 0.3, 0);
                }
                if (power >= 100000) {
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1, 0, 0, 0, 0);
                }

                // ════════════════════════════════════════
                // 🛡 Check: is the center's chunk still loaded?
                // (time may pass between particles and getNearbyEntities,
                // but this can't happen on the main server thread)
                // ════════════════════════════════════════
                int cx = center.getBlockX() >> 4, cz = center.getBlockZ() >> 4;
                if (!world.isChunkLoaded(cx, cz)) continue;

                int clusterRadius = getClusterRadius(power);
                Collection<Entity> nearby = world.getNearbyEntities(center, clusterRadius, clusterRadius, clusterRadius);

                for (Entity entity : nearby) {
                    if (!shouldAttract(entity)) {
                        // If the player is no longer metallic — reset the speed
                        if (entity instanceof Player player && dirtyPlayers.remove(player.getUniqueId())) {
                            // The player just dropped their last metallic item
                            // Reset the speed so they don't keep flying by inertia
                            player.setVelocity(new Vector(0, 0, 0));
                        }
                        // Cleanup: if the player left, remove from dirtyPlayers
                        // (normally cleaned via PlayerQuitEvent, but a safety net doesn't hurt)
                        if (entity instanceof Player && !((Player) entity).isOnline()) {
                            dirtyPlayers.remove(entity.getUniqueId());
                        }
                        continue;
                    }
                    applyMagneticForce(entity, center, power, clusterRadius);
                }
            } catch (Exception e) {
                ConsoleLogger.error(
                        "[Magnet] Error processing cluster #" + cluster.id + ": " + e.getMessage()
                );
                e.printStackTrace();
            }
        }

        for (int id : toRemove) {
            MagnetCluster cluster = clustersById.get(id);
            if (cluster != null) deactivateCluster(cluster);
        }
    }

    // =========================
    // SHOULD ATTRACT
    // =========================
    private boolean shouldAttract(Entity entity) {
        if (entity == null || entity.isDead()) return false;
        if (entity instanceof Item item) {
            return isMetallic(item.getItemStack());
        }
        if (entity instanceof Player player) {
            // Don't attract offline players (they may be unloading)
            if (!player.isOnline()) return false;
            return hasMetallicItem(player);
        }
        if (entity instanceof Mob mob) {
            // Mobs may already be dead when checking equipment
            try {
                return hasMetallicEquipment(mob);
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    private boolean hasMetallicItem(Player player) {
        try {
            if (isMetallic(player.getInventory().getItemInMainHand())) return true;
            if (isMetallic(player.getInventory().getItemInOffHand())) return true;
            for (ItemStack armor : player.getInventory().getArmorContents()) {
                if (isMetallic(armor)) return true;
            }
            for (ItemStack item : player.getInventory().getStorageContents()) {
                if (isMetallic(item)) return true;
            }
        } catch (Exception ignored) {
            // The player may have disconnected during the inventory scan
        }
        return false;
    }

    private boolean hasMetallicEquipment(Mob mob) {
        EntityEquipment eq = mob.getEquipment();
        if (eq == null) return false;
        if (isMetallic(eq.getItemInMainHand())) return true;
        if (isMetallic(eq.getItemInOffHand())) return true;
        if (isMetallic(eq.getHelmet())) return true;
        if (isMetallic(eq.getChestplate())) return true;
        if (isMetallic(eq.getLeggings())) return true;
        if (isMetallic(eq.getBoots())) return true;
        return false;
    }

    private boolean isMetallic(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        Material mat = item.getType();
        String name = mat.name();
        if (name.contains("IRON")) return true;
        if (name.startsWith("GOLD_") || name.equals("GOLDEN_SWORD") || name.equals("GOLDEN_SHOVEL")
                || name.equals("GOLDEN_PICKAXE") || name.equals("GOLDEN_AXE") || name.equals("GOLDEN_HOE")
                || name.equals("GOLDEN_HELMET") || name.equals("GOLDEN_CHESTPLATE")
                || name.equals("GOLDEN_LEGGINGS") || name.equals("GOLDEN_BOOTS")
                || name.equals("GOLDEN_HORSE_ARMOR") || name.equals("GOLD_BLOCK")
                || name.equals("GOLD_INGOT") || name.equals("GOLD_NUGGET")
                || name.equals("RAW_GOLD") || name.equals("RAW_GOLD_BLOCK")) return true;
        if (name.contains("NETHERITE")) return true;
        if (name.contains("COPPER")) return true;
        if (name.contains("CHAINMAIL")) return true;
        if (mat == Material.BUCKET || mat == Material.WATER_BUCKET || mat == Material.LAVA_BUCKET
                || mat == Material.MILK_BUCKET || mat == Material.COD_BUCKET
                || mat == Material.SALMON_BUCKET || mat == Material.PUFFERFISH_BUCKET
                || mat == Material.TROPICAL_FISH_BUCKET || mat == Material.AXOLOTL_BUCKET
                || mat == Material.TADPOLE_BUCKET) return true;
        if (mat == Material.SHEARS) return true;
        if (mat == Material.COMPASS) return true;
        if (mat == Material.RECOVERY_COMPASS) return true;
        if (name.contains("MINECART")) return true;
        if (name.contains("ANVIL")) return true;
        if (mat == Material.CAULDRON) return true;
        if (mat == Material.HOPPER) return true;
        if (mat == Material.RAIL || mat == Material.POWERED_RAIL || mat == Material.DETECTOR_RAIL
                || mat == Material.ACTIVATOR_RAIL) return true;
        if (mat == Material.PISTON || mat == Material.STICKY_PISTON) return true;
        if (mat == Material.STONECUTTER) return true;
        if (mat == Material.GRINDSTONE) return true;
        if (mat == Material.LANTERN || mat == Material.SOUL_LANTERN) return true;
        if (mat == Material.NAUTILUS_SHELL) return true;
        if (mat == Material.HEAVY_CORE) return true;
        return false;
    }

    // =========================
    // FORCE APPLICATION — ALL PARAMETERS FROM CONFIG
    // =========================
    private void applyMagneticForce(Entity entity, Location magnetCenter, int power, int clusterRadius) {
        // ════════════════════════════════════════
        // 🛡 Guard: the entity could die between shouldAttract and the call
        // ════════════════════════════════════════
        if (entity == null || entity.isDead()) return;

        Location entityLoc = entity.getLocation();
        if (entityLoc == null || entityLoc.getWorld() == null) return;

        Vector direction = magnetCenter.toVector().subtract(entityLoc.toVector());
        double distance = direction.length();
        if (distance < 0.5) return;
        direction.normalize();

        // ════════════════════════════════════════
        // 🌀 POWER CURVE: (power / powerNormalize) ^ powerExponent
        //    0.55 = soft start | 0.5 = sqrt | 1.0 = linear
        // ════════════════════════════════════════
        double t = power / MagnetConfig.getPowerNormalize();
        double powerMultiplier = Math.pow(t, MagnetConfig.getPowerExponent());

        // ════════════════════════════════════════
        // 📏 DISTANCE CURVE: smoothstep (smooth) or linear (old)
        // ════════════════════════════════════════
        double nd = distance / clusterRadius;
        if (nd > 1.0) nd = 1.0;

        double distanceFactor;
        if ("linear".equalsIgnoreCase(MagnetConfig.getDistanceCurveType())) {
            // Linear: was the default, with a hard min_factor
            distanceFactor = Math.max(MagnetConfig.getDistanceMinFactor(), 1.0 - nd);
        } else {
            // Smoothstep (default): 3t² - 2t³, derivative = 0 at both ends
            double smoothT = nd * nd * (3.0 - 2.0 * nd);
            distanceFactor = 1.0 - smoothT;
            // If min_factor > 0 — don't let it drop below
            if (distanceFactor < MagnetConfig.getDistanceMinFactor()) distanceFactor = MagnetConfig.getDistanceMinFactor();
        }

        double baseForce = MagnetConfig.getForceBase() * powerMultiplier;
        double proximityForce = distanceFactor * MagnetConfig.getForceDistanceMultiplier() * powerMultiplier;
        double force = baseForce + proximityForce;

        if (force > MagnetConfig.getForceMax() * powerMultiplier) {
            force = MagnetConfig.getForceMax() * powerMultiplier;
        }

        Vector forceVector = direction.multiply(force);

        if (entity instanceof Item) {
            forceVector.setY(forceVector.getY() + MagnetConfig.getItemYBoost() * powerMultiplier);
            double maxSpeed = MagnetConfig.getForceMaxSpeed() * powerMultiplier;
            if (forceVector.length() > maxSpeed) {
                forceVector.normalize().multiply(maxSpeed);
            }
            entity.setVelocity(forceVector);
        } else {
            Vector newVel = entity.getVelocity().add(forceVector);
            double maxSpeed = MagnetConfig.getForceMaxSpeed() * powerMultiplier;
            if (newVel.length() > maxSpeed) {
                newVel.normalize().multiply(maxSpeed);
            }
            entity.setVelocity(newVel);
        }
    }

    // =========================
    // 💾 SAVE — no-op: Marker entities persist in world files
    // =========================
    public static void saveAll() { /* no-op */ }
}
