package com.ultimateimprovments.energy.storage.battery;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.structure.StructureMarker;
import com.ultimateimprovments.util.LocationUtil;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.Materials;
import com.ultimateimprovments.energy.transfer.cable.CableNetwork;
import com.ultimateimprovments.energy.transfer.cable.CableNode;
import com.ultimateimprovments.energy.transfer.cable.EnergyFlow;
import com.ultimateimprovments.energy.transfer.cable.NodeType;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ⚡ Multiblock battery (WAXED_COPPER_GRATE + item frame)
 * <p>
 * A single copper grate is an inert decorative/building block — it is NOT a
 * battery and gets no network node. The battery is an assembled multi-block
 * structure: put a one-shot item frame on any grate and every copper grate
 * connected to it becomes ONE adaptive battery (the same frame-triggered
 * assembly as the magnet). Capacity is
 * {@code energy.battery.max_energy × blocks} (100 000 ⚡ per block by default),
 * one cluster id and one operating mode for the whole structure.
 * <p>
 * Modes: a redstone pulse on <b>any</b> block of the cluster cycles
 * Charge → Charge &amp; Discharge → Discharge (rising edge, so a lever held on
 * does not keep flipping). The mode is persisted in {@code battery_modes} and
 * survives a restart.
 * <p>
 * Storage — SQLite structure registry ({@code structure_markers}, type "battery"):
 * every block gets a row with its cluster UUID, written immediately on place.
 * <p>
 * When any block is destroyed — the whole cluster is disassembled, all rows removed.
 * On startup / chunk load — the cache is rebuilt from the registry.
 */
public class BatteryManager implements Listener {

    /** Cluster blocks re-read for a redstone rising edge per server tick. */
    private static final int REDSTONE_SCAN_PER_TICK = 8;

    /** Radius (blocks) of the mode-change chat feedback. */
    private static final double MODE_NOTIFY_RADIUS = 8.0;


    // ════════════════════════════════════════
    // BATTERY MODES
    // ════════════════════════════════════════
    public enum BatteryMode {
        CHARGE, CHARGE_DISCHARGE, DISCHARGE
    }

    private static BatteryManager instance;
    private static final Map<Long, BatteryCluster> locationToCluster = new ConcurrentHashMap<>();
    private static final Map<Integer, BatteryCluster> clustersById = new ConcurrentHashMap<>();
    private static int nextId = 1;

    // ════════════════════════════════════════
    // COORDINATE KEY (via StructureMarker)
    // ════════════════════════════════════════
    public static long toKey(int x, int y, int z) { return StructureMarker.toKey(x, y, z); }
    public static long toKey(Location loc) { return StructureMarker.toKey(loc); }
    public static int getX(long key) { return StructureMarker.getX(key); }
    public static int getZ(long key) { return StructureMarker.getZ(key); }
    public static int getY(long key) { return StructureMarker.getY(key); }

    // ════════════════════════════════════════
    // BATTERY CLUSTER
    // ════════════════════════════════════════
    public static class BatteryCluster {
        public int id;
        public UUID uuid;
        public World world;
        public Set<Long> blockKeys = new HashSet<>();
        public Location center;
        public int capacity;
        public BatteryMode mode = BatteryMode.CHARGE_DISCHARGE;

        /** Last observed redstone state (rising-edge detection, never persisted). */
        boolean powered;
        /** Rotating redstone scan cursor (block index inside scanOrder). */
        int scanCursor;
        /** Blocks scanned since the last "powered" hit — a full pass clears powered. */
        int scanProgress;
        /** Stable iteration order for the rotating redstone scan. */
        final List<Long> scanOrder = new ArrayList<>();

        private long sumX, sumY, sumZ;

        void addBlock(long key) {
            if (blockKeys.add(key)) {
                scanOrder.add(key);
                sumX += getX(key);
                sumY += getY(key);
                sumZ += getZ(key);
                updateCenterFromSums();
            }
        }

        void removeBlock(long key) {
            if (blockKeys.remove(key)) {
                scanOrder.remove(Long.valueOf(key));
                sumX -= getX(key);
                sumY -= getY(key);
                sumZ -= getZ(key);
                if (!blockKeys.isEmpty()) {
                    updateCenterFromSums();
                } else {
                    center = null;
                    capacity = 0;
                }
            }
        }

        void recalculateCenter() {
            if (blockKeys.isEmpty()) return;
            sumX = 0; sumY = 0; sumZ = 0;
            scanOrder.clear();
            for (long key : blockKeys) {
                sumX += getX(key);
                sumY += getY(key);
                sumZ += getZ(key);
                scanOrder.add(key);
            }
            updateCenterFromSums();
        }

        private void updateCenterFromSums() {
            int size = blockKeys.size();
            if (size == 0) { center = null; capacity = 0; return; }
            center = new Location(world,
                    (int) Math.round((double) sumX / size),
                    (int) Math.round((double) sumY / size),
                    (int) Math.round((double) sumZ / size));
            // Cluster capacity = blocks × per-block capacity (config) —
            // the same limit CableNode enforces per block, so the charge
            // percentage is consistent now (previously blocks × 1000 hardcode
            // contradicted energy.battery.max_energy = 100000).
            capacity = size * EnergyFlow.batteryCapacity();
        }

        boolean contains(Location loc) { return blockKeys.contains(toKey(loc)); }

        public boolean canCharge() {
            return mode == BatteryMode.CHARGE || mode == BatteryMode.CHARGE_DISCHARGE;
        }

        public boolean canDischarge() {
            return mode == BatteryMode.DISCHARGE || mode == BatteryMode.CHARGE_DISCHARGE;
        }

        void cycleMode() {
            switch (mode) {
                case CHARGE -> mode = BatteryMode.CHARGE_DISCHARGE;
                case CHARGE_DISCHARGE -> mode = BatteryMode.DISCHARGE;
                case DISCHARGE -> mode = BatteryMode.CHARGE;
            }
        }

        public String getModeDisplay() {
            return switch (mode) {
                case CHARGE -> "<aqua>Charge</aqua>";
                case CHARGE_DISCHARGE -> "<light_purple>Charge & Discharge</light_purple>";
                case DISCHARGE -> "<red>Discharge</red>";
            };
        }
    }

    // ════════════════════════════════════════
    // INIT — scan all loaded chunks for Markers
    // ════════════════════════════════════════
    public static void init() {
        instance = new BatteryManager();
        Bukkit.getPluginManager().registerEvents(instance, Main.getInstance());

        // Persisted operating modes (redstone-cycled) must be back before the
        // clusters go live, otherwise every restart resets them.
        initModeTable();

        // Rebuild clusters from registry entries in loaded chunks
        rebuildFromMarkers();

        ConsoleLogger.info("[BatteryMulti] Manager initialized with " + clustersById.size() + " clusters (Marker-based)");
    }

    public static BatteryManager getInstance() { return instance; }

    /**
     * Scans all loaded chunks for "battery" Markers,
     * groups them by UUID, finds the world from worldUid in StructureData
     * and rebuilds the clusters. After creation — cleans up orphaned Markers.
     */
    public static void rebuildFromMarkers() {
        locationToCluster.clear();
        clustersById.clear();
        nextId = 1;

        // Group Markers by UUID + remember the world from StructureData
        Map<UUID, Set<Long>> markerGroups = new HashMap<>();
        Map<UUID, World> foundWorlds = new HashMap<>();

        for (Map.Entry<String, StructureMarker.StructureData> entry : StructureMarker.getAllEntries()) {
            if (!"battery".equals(entry.getValue().type())) continue;

            UUID uuid = entry.getValue().uuid();
            String fk = entry.getKey();
            long posKey = StructureMarker.toKey(
                StructureMarker.parseX(fk),
                StructureMarker.parseY(fk),
                StructureMarker.parseZ(fk)
            );
            markerGroups.computeIfAbsent(uuid, k -> new HashSet<>()).add(posKey);

            // Find the world by worldUid (from StructureData — not by iterating worlds!)
            if (!foundWorlds.containsKey(uuid)) {
                String worldUid = entry.getValue().worldUid();
                if (worldUid != null) {
                    for (World w : Bukkit.getServer().getWorlds()) {
                        if (w.getUID().toString().equals(worldUid)) {
                            foundWorlds.put(uuid, w);
                            break;
                        }
                    }
                }
            }
        }

        // Create clusters from the groups
        Map<UUID, BatteryMode> persistedModes = loadModes();
        Set<UUID> usedUuids = new HashSet<>();
        for (Map.Entry<UUID, Set<Long>> group : markerGroups.entrySet()) {
            if (group.getValue().isEmpty()) continue;
            World world = foundWorlds.get(group.getKey());
            if (world == null) continue;

            BatteryCluster cluster = new BatteryCluster();
            cluster.id = nextId++;
            cluster.uuid = group.getKey();
            cluster.world = world;
            cluster.blockKeys = new HashSet<>(group.getValue());
            cluster.recalculateCenter();
            BatteryMode persisted = persistedModes.get(cluster.uuid);
            if (persisted != null) cluster.mode = persisted;
            // Baseline, not a rising edge: a lever already ON at startup must
            // not cycle the restored mode.
            cluster.powered = anyBlockPowered(cluster);

            for (long key : group.getValue()) {
                locationToCluster.put(key, cluster);
                // Create a CableNode with BATTERY type (without a "cable" marker)
                Location blockLoc = new Location(world, getX(key), getY(key), getZ(key));
                CableNetwork.ensureNode(blockLoc, NodeType.BATTERY);
            }
            clustersById.put(cluster.id, cluster);
            usedUuids.add(cluster.uuid);
        }

        // Clean up orphaned Markers (that did not end up in any cluster)
        StructureMarker.purgeOrphaned("battery", usedUuids);
    }

    // ════════════════════════════════════════
    // FLOOD-FILL
    // ════════════════════════════════════════
    private static final int[][] DIR = {
        {1,0,0}, {-1,0,0}, {0,1,0}, {0,-1,0}, {0,0,1}, {0,0,-1}
    };

    private static Set<Long> floodFillFast(World world, int sx, int sy, int sz) {
        if (world == null) return new HashSet<>(0);
        if (!world.isChunkLoaded(sx >> 4, sz >> 4)) return new HashSet<>(0);

        Set<Long> visited = new HashSet<>();
        Deque<int[]> queue = new ArrayDeque<>();
        visited.add(toKey(sx, sy, sz));
        queue.add(new int[]{sx, sy, sz});
        while (!queue.isEmpty()) {
            int[] pos = queue.pollFirst();
            int x = pos[0], y = pos[1], z = pos[2];
            for (int[] d : DIR) {
                int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                long nk = toKey(nx, ny, nz);
                if (visited.contains(nk)) continue;
                if (!world.isChunkLoaded(nx >> 4, nz >> 4)) continue;
                if (world.getType(nx, ny, nz) == Materials.WAXED_COPPER_GRATE) {
                    visited.add(nk);
                    queue.addLast(new int[]{nx, ny, nz});
                }
            }
        }
        return visited;
    }

    private static void removeFrameAt(Location blockLoc, Player player) {
        World w = blockLoc.getWorld();
        if (w == null) return;
        for (ItemFrame frame : w.getEntitiesByClass(ItemFrame.class)) {
            if (!frame.isValid() || frame.isDead()) continue;
            Location floc = frame.getLocation();
            double dx = Math.abs(floc.getX() - (blockLoc.getBlockX() + 0.5));
            double dy = Math.abs(floc.getY() - (blockLoc.getBlockY() + 0.5));
            double dz = Math.abs(floc.getZ() - (blockLoc.getBlockZ() + 0.5));
            if (dx <= 0.6 && dy <= 0.6 && dz <= 0.6) {
                if (player != null) {
                    w.dropItemNaturally(frame.getLocation(), new ItemStack(Material.ITEM_FRAME));
                }
                frame.remove();
                break;
            }
        }
    }

    // ════════════════════════════════════════
    // ASSEMBLE — create a Marker on each block
    // ════════════════════════════════════════
    public static void assemble(Location loc, Player player) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return;
        long key = toKey(loc);
        if (locationToCluster.containsKey(key)) {
            if (player != null) player.sendMessage(com.ultimateimprovments.util.MessageUtil.parse("<yellow>Battery already assembled here!"));
            return;
        }

        Set<Long> connected = floodFillFast(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        if (connected.isEmpty()) {
            if (player != null) player.sendMessage(com.ultimateimprovments.util.MessageUtil.parse("<dark_red>❌ <red>No WAXED_COPPER_GRATE blocks nearby!"));
            return;
        }

        UUID uuid = UUID.randomUUID();
        BatteryCluster cluster = new BatteryCluster();
        cluster.id = nextId++;
        cluster.uuid = uuid;
        cluster.world = loc.getWorld();
        cluster.blockKeys = new HashSet<>(connected);
        cluster.recalculateCenter();
        cluster.powered = anyBlockPowered(cluster);
        saveMode(uuid, cluster.mode);

        for (long bk : connected) {
            locationToCluster.put(bk, cluster);
            // Create a CableNode with BATTERY type
            Location blockLoc = new Location(cluster.world, getX(bk), getY(bk), getZ(bk));
            CableNetwork.ensureNode(blockLoc, NodeType.BATTERY);
        }
        clustersById.put(cluster.id, cluster);

        // One batched registry write for the whole structure.
        List<Location> blockLocations = new ArrayList<>(connected.size());
        for (long bk : connected) {
            blockLocations.add(new Location(cluster.world, getX(bk), getY(bk), getZ(bk)));
        }
        StructureMarker.placeAll(blockLocations, "battery", uuid);

        removeFrameAt(loc, player);

        if (cluster.center != null) {
            World w = cluster.center.getWorld();
            if (w != null) {
                w.spawnParticle(Particle.END_ROD, cluster.center.clone().add(0.5, 0.5, 0.5), 30, 0.5, 0.5, 0.5, 0.1);
                w.playSound(cluster.center, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
            }
        }

        if (player != null) {
            player.sendMessage(com.ultimateimprovments.util.MessageUtil.parse("<green>✔ <white>Battery assembled!"));
            player.sendMessage(com.ultimateimprovments.util.MessageUtil.parse("<dark_gray>┃ <gray>Blocks: <white>" + cluster.blockKeys.size() + " <gray>| Capacity: <white>" + cluster.capacity + " <gray>energy"));
            player.sendMessage(com.ultimateimprovments.util.MessageUtil.parse("<dark_gray>┃ <gray>Mode: <white>" + cluster.getModeDisplay() + " <gray>— a redstone pulse on any block cycles Charge → Charge & Discharge → Discharge"));
        }

        ConsoleLogger.info("[BatteryMulti] Assembled cluster #" + cluster.id + " UUID=" + uuid + " with " + connected.size() + " blocks");
    }

    // ════════════════════════════════════════
    // DISASSEMBLE
    // ════════════════════════════════════════
    public static void disassemble(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return;
        BatteryCluster cluster = locationToCluster.get(toKey(loc));
        if (cluster == null) return;

        for (long bk : cluster.blockKeys) {
            locationToCluster.remove(bk);
            // Remove the CableNode for this block
            Location blockLoc = new Location(cluster.world, getX(bk), getY(bk), getZ(bk));
            CableNetwork.removeNode(blockLoc);
        }
        clustersById.remove(cluster.id);

        // Remove all Markers of the cluster
        if (cluster.uuid != null) {
            StructureMarker.removeAllByUuid(cluster.world, cluster.uuid);
            deleteMode(cluster.uuid);
        }

        ConsoleLogger.info("[BatteryMulti] Disassembled cluster #" + cluster.id);
    }

    // ════════════════════════════════════════
    // BLOCK PLACED (hot expand)
    // ════════════════════════════════════════
    public static void onBlockPlaced(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return;
        long key = toKey(loc);
        if (locationToCluster.containsKey(key)) return;

        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();
        long[] neighborKeys = {
            toKey(bx+1,by,bz), toKey(bx-1,by,bz),
            toKey(bx,by+1,bz), toKey(bx,by-1,bz),
            toKey(bx,by,bz+1), toKey(bx,by,bz-1)
        };

        Set<BatteryCluster> adj = new LinkedHashSet<>();
        for (long nk : neighborKeys) {
            BatteryCluster c = locationToCluster.get(nk);
            if (c != null) adj.add(c);
        }
        if (adj.isEmpty()) return;

        if (adj.size() == 1) {
            BatteryCluster c = adj.iterator().next();
            c.addBlock(key);
            locationToCluster.put(key, c);
            // New Marker on the added block
            if (c.uuid != null) {
                StructureMarker.place(loc, "battery", c.uuid);
            }
            // Create a CableNode with BATTERY type
            CableNetwork.ensureNode(loc, NodeType.BATTERY);
        } else {
            Iterator<BatteryCluster> it = adj.iterator();
            BatteryCluster primary = it.next();
            while (it.hasNext()) {
                BatteryCluster other = it.next();
                for (long bk : other.blockKeys) {
                    locationToCluster.put(bk, primary);
                    primary.addBlock(bk);
                    // Update the Marker — change the UUID to primary's
                    Location blockLoc = new Location(primary.world, getX(bk), getY(bk), getZ(bk));
                    StructureMarker.removeAt(blockLoc);
                    if (primary.uuid != null) {
                        StructureMarker.place(blockLoc, "battery", primary.uuid);
                    }
                }
                // The merged cluster's blocks may now link to each other.
                for (long bk : other.blockKeys) {
                    CableNetwork.reconnect(new Location(primary.world, getX(bk), getY(bk), getZ(bk)));
                }
                clustersById.remove(other.id);
                deleteMode(other.uuid);
            }
            primary.addBlock(key);
            locationToCluster.put(key, primary);
            if (primary.uuid != null) {
                StructureMarker.place(loc, "battery", primary.uuid);
            }
            // Create a CableNode with BATTERY type
            CableNetwork.ensureNode(loc, NodeType.BATTERY);
        }
    }

    // ════════════════════════════════════════
    // BLOCK BROKEN — disassemble the whole cluster
    // ════════════════════════════════════════
    public static void onBlockBroken(Location loc, Player player) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return;
        long key = toKey(loc);
        BatteryCluster cluster = locationToCluster.get(key);
        if (cluster == null) return;

        // Remove all Markers of the cluster from the world
        if (cluster.uuid != null) {
            StructureMarker.removeAllByUuid(cluster.world, cluster.uuid);
            deleteMode(cluster.uuid);
        }

        // Clear the cache and CableNodes
        for (long bk : cluster.blockKeys) {
            locationToCluster.remove(bk);
            CableNetwork.removeNode(new Location(cluster.world, getX(bk), getY(bk), getZ(bk)));
        }
        clustersById.remove(cluster.id);
        cluster.blockKeys.clear();

        ConsoleLogger.info("[BatteryMulti] Disassembled cluster #" + cluster.id
                + " due to block break at " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ());

        if (player != null) {
            player.sendMessage(com.ultimateimprovments.util.MessageUtil.parse("<yellow>❕ Battery disassembled (block broken)"));
        }
    }

    // ════════════════════════════════════════
    // SHIFT+RMB WITH EMPTY HAND — MODE SWITCH
    // ════════════════════════════════════════
    @EventHandler(ignoreCancelled = true)
    public void onModeSwitch(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!e.getPlayer().isSneaking()) return;
        if (e.getClickedBlock() == null || e.getClickedBlock().getType() != Materials.WAXED_COPPER_GRATE) return;

        Player player = e.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != Material.AIR) return;

        Location loc = LocationUtil.normalize(e.getClickedBlock().getLocation());
        if (loc == null) return;

        BatteryCluster cluster = locationToCluster.get(toKey(loc));
        if (cluster == null) return;

        cluster.cycleMode();
        e.setCancelled(true);

        saveMode(cluster.uuid, cluster.mode);
        player.sendMessage(MessageUtil.parse("<aqua>Режим батареи: </aqua><white>" + cluster.getModeDisplay() + "</white>"));
    }

    // ════════════════════════════════════════
    // QUERIES
    // ════════════════════════════════════════
    public static boolean isActive(Location loc) {
        loc = LocationUtil.normalize(loc);
        return loc != null && locationToCluster.containsKey(toKey(loc));
    }

    public static int getTotalCapacity(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return 0;
        BatteryCluster c = locationToCluster.get(toKey(loc));
        return c != null ? c.capacity : 0;
    }

    public static BatteryMode getMode(Location loc) {
        loc = LocationUtil.normalize(loc);
        BatteryCluster c = loc != null ? locationToCluster.get(toKey(loc)) : null;
        return c != null ? c.mode : BatteryMode.CHARGE_DISCHARGE;
    }

    public static BatteryCluster getCluster(Location loc) {
        loc = LocationUtil.normalize(loc);
        return loc != null ? locationToCluster.get(toKey(loc)) : null;
    }

    public static Collection<BatteryCluster> getAllClusters() { return clustersById.values(); }
    public static int getClusterCount() { return clustersById.size(); }

    // ════════════════════════════════════════
    // GET CHARGE PERCENTAGE
    // ════════════════════════════════════════
    public static double getChargePercentage(BatteryCluster cluster) {
        if (cluster == null || cluster.capacity <= 0) return 0.0;
        int totalEnergy = 0;
        for (long key : cluster.blockKeys) {
            Location bl = new Location(cluster.world, getX(key), getY(key), getZ(key));
            CableNode node = CableNetwork.getNode(bl);
            if (node != null) {
                totalEnergy += node.getEnergy();
            }
        }
        return Math.min(100.0, (double) totalEnergy / cluster.capacity * 100.0);
    }

    // ════════════════════════════════════════
    // REDSTONE MODE CONTROL — a pulse on ANY block cycles the mode
    // ════════════════════════════════════════

    /**
     * Rotating redstone scan of one cluster. A rising edge (unpowered → powered
     * on any block of the structure) cycles the whole battery through
     * Charge → Charge &amp; Discharge → Discharge. Only
     * {@value #REDSTONE_SCAN_PER_TICK} blocks are read per tick, so even a large
     * adaptive battery costs almost nothing; a full pass clears the latched
     * state, so the next pulse is recognized. Chunks that are not loaded are
     * skipped instead of being sync-loaded.
     */
    private static void scanRedstone(BatteryCluster cluster) {
        int size = cluster.scanOrder.size();
        if (size <= 0) return;

        int step = Math.min(REDSTONE_SCAN_PER_TICK, size);
        int start = cluster.scanCursor % size;
        boolean found = false;
        for (int i = 0; i < step; i++) {
            long key = cluster.scanOrder.get((start + i) % size);
            if (isPoweredAt(cluster.world, getX(key), getY(key), getZ(key))) {
                found = true;
                break;
            }
        }
        cluster.scanCursor = (start + step) % size;

        if (found) {
            cluster.scanProgress = 0;
            if (!cluster.powered) {
                cluster.powered = true;
                cluster.cycleMode();
                saveMode(cluster.uuid, cluster.mode);
                notifyMode(cluster);
            }
            return;
        }

        // Nothing found: after a full sweep the structure counts as unpowered,
        // so the next signal is a fresh rising edge.
        cluster.scanProgress += step;
        if (cluster.scanProgress >= size) {
            cluster.scanProgress = 0;
            cluster.powered = false;
        }
    }

    /** Full (non-rotating) power check — only used for the startup baseline. */
    private static boolean anyBlockPowered(BatteryCluster cluster) {
        if (cluster == null || cluster.world == null) return false;
        for (long key : cluster.blockKeys) {
            if (isPoweredAt(cluster.world, getX(key), getY(key), getZ(key))) return true;
        }
        return false;
    }

    private static boolean isPoweredAt(World world, int x, int y, int z) {
        if (world == null) return false;
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;
        Block block = world.getBlockAt(x, y, z);
        return block.isBlockPowered() || block.isBlockIndirectlyPowered();
    }

    /** Chat feedback for the cluster's players (8-block radius, no spam). */
    private static void notifyMode(BatteryCluster cluster) {
        if (cluster.center == null || cluster.world == null) return;
        Location center = cluster.center;
        double radiusSq = MODE_NOTIFY_RADIUS * MODE_NOTIFY_RADIUS;

        for (Player player : cluster.world.getPlayers()) {
            if (player.getLocation().distanceSquared(center) <= radiusSq) {
                player.sendMessage(MessageUtil.parse("<aqua>Battery mode: </aqua><white>"
                        + cluster.getModeDisplay() + "</white>"));
            }
        }
    }

    // ════════════════════════════════════════
    // MODE PERSISTENCE — battery_modes(uuid, mode)
    // ════════════════════════════════════════

    private static void initModeTable() {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS battery_modes ("
                             + "uuid TEXT PRIMARY KEY, mode TEXT NOT NULL)")) {
            st.executeUpdate();
        } catch (SQLException e) {
            ConsoleLogger.warn("[BatteryMulti] Failed to create battery_modes: " + e.getMessage());
        }
    }

    private static Map<UUID, BatteryMode> loadModes() {
        Map<UUID, BatteryMode> modes = new HashMap<>();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement("SELECT uuid, mode FROM battery_modes");
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                try {
                    modes.put(UUID.fromString(rs.getString("uuid")),
                            BatteryMode.valueOf(rs.getString("mode")));
                } catch (IllegalArgumentException ignored) {
                    // corrupt row — skip, the battery falls back to the default
                }
            }
        } catch (SQLException e) {
            ConsoleLogger.warn("[BatteryMulti] Failed to load battery modes: " + e.getMessage());
        }
        return modes;
    }

    private static void saveMode(UUID uuid, BatteryMode mode) {
        if (uuid == null || mode == null) return;
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "INSERT OR REPLACE INTO battery_modes (uuid, mode) VALUES (?, ?)")) {
            st.setString(1, uuid.toString());
            st.setString(2, mode.name());
            st.executeUpdate();
        } catch (SQLException e) {
            ConsoleLogger.warn("[BatteryMulti] Failed to save battery mode: " + e.getMessage());
        }
    }

    private static void deleteMode(UUID uuid) {
        if (uuid == null) return;
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "DELETE FROM battery_modes WHERE uuid = ?")) {
            st.setString(1, uuid.toString());
            st.executeUpdate();
        } catch (SQLException e) {
            ConsoleLogger.warn("[BatteryMulti] Failed to delete battery mode: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════
    // TICK — particles + anti-phantom
    // ════════════════════════════════════════
    public static void tick() {
        List<Integer> toRemove = new ArrayList<>();

        for (BatteryCluster cluster : clustersById.values()) {
            try {
                if (cluster.world == null || cluster.center == null || cluster.capacity <= 0) continue;
                if (cluster.blockKeys.isEmpty()) {
                    toRemove.add(cluster.id);
                    continue;
                }

                long firstKey = cluster.blockKeys.iterator().next();
                int fx = getX(firstKey), fy = getY(firstKey), fz = getZ(firstKey);
                if (!cluster.world.isChunkLoaded(fx >> 4, fz >> 4)) continue;

                // Anti-phantom: check the Marker and the block
                if (cluster.world.getType(fx, fy, fz) != Materials.WAXED_COPPER_GRATE) {
                    toRemove.add(cluster.id);
                    continue;
                }

                // Redstone mode control (rotating scan, rising edge only).
                scanRedstone(cluster);

                double pct = getChargePercentage(cluster);
                int count = Math.max(1, (int) Math.round(pct / 10.0));

                Location center = cluster.center.clone().add(0.5, 0.5, 0.5);
                cluster.world.spawnParticle(Particle.END_ROD, center, count, 0.3, 0.3, 0.3, 0.01);
                cluster.world.spawnParticle(Particle.ELECTRIC_SPARK, center, count / 2, 0.3, 0.3, 0.3, 0);
            } catch (Exception e) {
                ConsoleLogger.warn("[BatteryMulti] Tick error: " + e.getMessage());
            }
        }

        // Clean up phantom clusters
        for (int id : toRemove) {
            BatteryCluster cluster = clustersById.get(id);
            if (cluster != null) {
                for (long bk : cluster.blockKeys) {
                    locationToCluster.remove(bk);
                    CableNetwork.removeNode(new Location(cluster.world, getX(bk), getY(bk), getZ(bk)));
                }
                clustersById.remove(id);
                // Remove Markers
                if (cluster.uuid != null) {
                    StructureMarker.removeAllByUuid(cluster.world, cluster.uuid);
                    deleteMode(cluster.uuid);
                }
                ConsoleLogger.info("[BatteryMulti] Removed phantom cluster #" + id);
            }
        }
    }

    // ════════════════════════════════════════
    // SHUTDOWN — unregister listener + drop caches (soft /ui reload)
    // ════════════════════════════════════════
    public static void shutdown() {
        if (instance != null) {
            org.bukkit.event.HandlerList.unregisterAll(instance);
            instance = null;
        }
        clearAll();
    }

    // ════════════════════════════════════════
    // SAVE / LOAD — no longer needed (rows persist in structure_markers immediately)
    // ════════════════════════════════════════
    public static void saveAll() { /* no-op */ }

    // ════════════════════════════════════════
    // INTERNAL
    // ════════════════════════════════════════
    static Map<Long, BatteryCluster> getLocationMap() { return locationToCluster; }
    static Map<Integer, BatteryCluster> getClustersById() { return clustersById; }

    public static void clearAll() {
        locationToCluster.clear();
        clustersById.clear();
    }
}
