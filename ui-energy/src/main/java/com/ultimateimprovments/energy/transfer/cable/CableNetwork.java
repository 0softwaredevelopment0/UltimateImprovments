package com.ultimateimprovments.energy.transfer.cable;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.energy.storage.battery.BatteryManager;
import com.ultimateimprovments.structure.StructureMarker;
import com.ultimateimprovments.util.LocationUtil;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CableNetwork {

    // Internal storage keyed by world UID -> (block key -> CableNode)
    private static final Map<String, Map<Long, CableNode>> nodesByWorld = new ConcurrentHashMap<>();

    // Flowing tracking (world UID -> (block key -> tick of the last mark)).
    // Marks auto-expire (see FLOW_HOLD_TICKS) instead of being force-cleared,
    // so visuals stay correct no matter when the producers ran last.
    private static final Map<String, Map<Long, Long>> flowingByWorld = new ConcurrentHashMap<>();

    /** How long a flow mark stays "hot" after the last transfer (ticks). */
    private static final long FLOW_HOLD_TICKS = 50;

    // =========================
    // INIT
    // =========================
    public static void init() {
        nodesByWorld.clear();
        flowingByWorld.clear();
        rebuildFromMarkers();
    }

    // =========================
    // REBUILD FROM MARKERS
    // =========================
    public static void rebuildFromMarkers() {
        for (Map.Entry<String, StructureMarker.StructureData> entry : StructureMarker.getAllEntries()) {
            if (!"cable".equals(entry.getValue().type())) continue;

            String fk = entry.getKey();
            String worldUid = StructureMarker.parseWorldUid(fk);
            int x = StructureMarker.parseX(fk), y = StructureMarker.parseY(fk), z = StructureMarker.parseZ(fk);

            World world = null;
            for (World w : Main.getInstance().getServer().getWorlds()) {
                if (w.getUID().toString().equals(worldUid)) {
                    world = w;
                    break;
                }
            }
            if (world == null) continue;
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
            Block block = world.getBlockAt(x, y, z);
            if (!CableBlock.isCable(block)) continue;

            long key = LocationUtil.toKey(x, y, z);
            Map<Long, CableNode> worldNodes = nodesByWorld.computeIfAbsent(worldUid, k -> new ConcurrentHashMap<>());
            if (worldNodes.containsKey(key)) continue;

            CableNode node = new CableNode(new Location(world, x, y, z));
            worldNodes.put(key, node);
        }

        // Auto-connect neighbors
        for (Map.Entry<String, Map<Long, CableNode>> worldEntry : nodesByWorld.entrySet()) {
            for (CableNode node : worldEntry.getValue().values()) {
                for (long nearKey : LocationUtil.getNeighborKeys(node.getKey())) {
                    CableNode neighbor = worldEntry.getValue().get(nearKey);
                    if (neighbor == null) continue;
                    if (node.getType() == NodeType.BATTERY && neighbor.getType() == NodeType.BATTERY
                            && !sameBatteryCluster(node, neighbor)) continue;
                    if (!LocationUtil.isFullyConnected(node.getKey(), nearKey)) continue;
                    node.connectKey(nearKey);
                    neighbor.connectKey(node.getKey());
                }
            }
        }
    }

    // =========================
    // ADD NODE
    // =========================
    public static void addNode(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return;

        long key = LocationUtil.toKey(loc);
        String worldUid = loc.getWorld().getUID().toString();
        Map<Long, CableNode> worldNodes = nodesByWorld.computeIfAbsent(worldUid, k -> new ConcurrentHashMap<>());
        if (worldNodes.containsKey(key)) return;

        CableNode node = new CableNode(loc);
        worldNodes.put(key, node);

        // Auto-connect to existing neighbors
        autoConnectNode(worldUid, node);

        StructureMarker.place(loc, "cable", UUID.randomUUID());
    }

    // =========================
    // ENSURE NODE
    // =========================
    public static void ensureNode(Location loc, NodeType type) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return;

        long key = LocationUtil.toKey(loc);
        String worldUid = loc.getWorld().getUID().toString();
        Map<Long, CableNode> worldNodes = nodesByWorld.computeIfAbsent(worldUid, k -> new ConcurrentHashMap<>());
        if (worldNodes.containsKey(key)) return;

        CableNode node = new CableNode(loc);
        if (type != null) node.setType(type);
        if (type == NodeType.BATTERY) {
            // Capacity immediately at creation — otherwise the node can't
            // accept energy until the first balancer pass clamps it in.
            node.setMaxEnergy(EnergyFlow.batteryCapacity());
        }
        worldNodes.put(key, node);

        // Auto-connect to existing neighbors
        autoConnectNode(worldUid, node);
    }

    // =========================
    // AUTO-CONNECT NODE TO EXISTING NEIGHBORS
    // =========================
    private static void autoConnectNode(String worldUid, CableNode node) {
        Map<Long, CableNode> worldNodes = nodesByWorld.get(worldUid);
        if (worldNodes == null) return;

        for (long nearKey : LocationUtil.getNeighborKeys(node.getKey())) {
            CableNode neighbor = worldNodes.get(nearKey);
            if (neighbor == null) continue;
            if (node.getType() == NodeType.BATTERY && neighbor.getType() == NodeType.BATTERY
                    && !sameBatteryCluster(node, neighbor)) continue;
            if (!LocationUtil.isFullyConnected(node.getKey(), nearKey)) continue;
            node.connectKey(nearKey);
            neighbor.connectKey(node.getKey());
        }
    }

    /**
     * Batteries never route through each other — except inside ONE assembled
     * battery cluster. The copper-grate blocks of a single multi-block battery
     * ARE connected, so the whole structure behaves as one storage: capacity is
     * {@code per-block capacity × blocks} and any block can be reached through
     * any other block of the same cluster.
     */
    private static boolean sameBatteryCluster(CableNode a, CableNode b) {
        if (a == null || b == null) return false;
        BatteryManager.BatteryCluster ca = BatteryManager.getCluster(a.getLocation());
        if (ca == null || ca.uuid == null) return false;
        BatteryManager.BatteryCluster cb = BatteryManager.getCluster(b.getLocation());
        return cb != null && ca.uuid.equals(cb.uuid);
    }

    // =========================
    // RECONNECT — re-runs the neighbour auto-connect for an existing node
    // (used after two battery clusters merge, when the same-cluster rule
    // suddenly allows block-to-block links)
    // =========================
    public static void reconnect(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null || loc.getWorld() == null) return;

        CableNode node = getNode(loc);
        if (node != null) {
            autoConnectNode(loc.getWorld().getUID().toString(), node);
        }
    }

    // =========================
    // REMOVE NODE
    // =========================
    public static void removeNode(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return;

        long key = LocationUtil.toKey(loc);
        String worldUid = loc.getWorld().getUID().toString();
        Map<Long, CableNode> worldNodes = nodesByWorld.get(worldUid);
        if (worldNodes == null) return;

        CableNode node = worldNodes.remove(key);
        if (node != null) {
            for (long connKey : node.getConnectionKeys()) {
                CableNode other = worldNodes.get(connKey);
                if (other != null) other.disconnectKey(key);
            }
        }

        StructureMarker.removeAt(loc);
    }

    // =========================
    // GET / EXISTS
    // =========================
    
    /**
     * Fast iterator over all nodes without creating an intermediate list.
     * Use instead of getAllNodes() when only traversal is needed.
     */
    public static void forEachNode(java.util.function.Consumer<CableNode> action) {
        for (Map<Long, CableNode> worldNodes : nodesByWorld.values()) {
            for (CableNode node : worldNodes.values()) {
                action.accept(node);
            }
        }
    }
    
    public static CableNode getNode(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return null;

        long key = LocationUtil.toKey(loc);
        String worldUid = loc.getWorld().getUID().toString();
        Map<Long, CableNode> worldNodes = nodesByWorld.get(worldUid);
        return worldNodes != null ? worldNodes.get(key) : null;
    }

    public static CableNode getNodeByKey(String worldUid, long key) {
        Map<Long, CableNode> worldNodes = nodesByWorld.get(worldUid);
        return worldNodes != null ? worldNodes.get(key) : null;
    }

    public static boolean exists(Location loc) {
        return getNode(loc) != null;
    }

    public static Collection<CableNode> getAllNodes() {
        List<CableNode> all = new ArrayList<>();
        for (Map<Long, CableNode> worldNodes : nodesByWorld.values()) {
            all.addAll(worldNodes.values());
        }
        return all;
    }

    public static Collection<CableNode> getWorldNodes(String worldUid) {
        Map<Long, CableNode> worldNodes = nodesByWorld.get(worldUid);
        return worldNodes != null ? worldNodes.values() : Collections.emptyList();
    }

    // =========================
    // FLOWING TRACKING (auto-expiring marks)
    // =========================
    public static void markFlowing(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return;
        long key = LocationUtil.toKey(loc);
        String worldUid = loc.getWorld().getUID().toString();
        flowingByWorld.computeIfAbsent(worldUid, k -> new ConcurrentHashMap<>())
                .put(key, currentTick());
    }

    public static void markFlowingKey(String worldUid, long key) {
        if (worldUid == null || key == 0) return;
        flowingByWorld.computeIfAbsent(worldUid, k -> new ConcurrentHashMap<>())
                .put(key, currentTick());
    }

    public static boolean isFlowing(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return false;
        return isFlowingKey(loc.getWorld().getUID().toString(), LocationUtil.toKey(loc));
    }

    public static boolean isFlowingKey(String worldUid, long key) {
        if (worldUid == null || key == 0) return false;
        Map<Long, Long> flowing = flowingByWorld.get(worldUid);
        if (flowing == null) return false;
        Long marked = flowing.get(key);
        return marked != null && currentTick() - marked <= FLOW_HOLD_TICKS;
    }

    /** Drops expired flow marks (called from the visual pass to bound memory). */
    public static void pruneFlowing() {
        long now = currentTick();
        for (Map<Long, Long> flowing : flowingByWorld.values()) {
            flowing.values().removeIf(marked -> now - marked > FLOW_HOLD_TICKS);
        }
    }

    public static void clearFlowing() {
        flowingByWorld.clear();
    }

    private static long currentTick() {
        try {
            return org.bukkit.Bukkit.getCurrentTick();
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            return System.currentTimeMillis() / 50L; // fallback, non-Paper
        }
    }

    // =========================
    // SAVE / DELETE — no-op (nodes are persisted immediately in structure_markers;
    // node energy is persisted separately by EnergyPersistence)
    // =========================
    public static void save() { /* no-op */ }
}
