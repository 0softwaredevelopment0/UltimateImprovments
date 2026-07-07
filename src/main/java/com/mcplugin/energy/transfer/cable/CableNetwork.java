package com.mcplugin.energy.transfer.cable;

import com.mcplugin.core.Main;
import com.mcplugin.structure.StructureMarker;
import com.mcplugin.util.LocationUtil;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Material;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CableNetwork {

    // Internal storage keyed by world UID -> (block key -> CableNode)
    private static final Map<String, Map<Long, CableNode>> nodesByWorld = new ConcurrentHashMap<>();

    // Flowing tracking (world UID -> Set of block keys)
    private static final Map<String, Set<Long>> flowingByWorld = new ConcurrentHashMap<>();

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
            if (world.getBlockAt(x, y, z).getType() == Material.AIR) continue;

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
                    if (node.getType() == NodeType.BATTERY && neighbor.getType() == NodeType.BATTERY) continue;
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
            if (node.getType() == NodeType.BATTERY && neighbor.getType() == NodeType.BATTERY) continue;
            if (!LocationUtil.isFullyConnected(node.getKey(), nearKey)) continue;
            node.connectKey(nearKey);
            neighbor.connectKey(node.getKey());
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
     * Быстрый итератор по всем узлам без создания промежуточного списка.
     * Использовать вместо getAllNodes() когда нужен только обход.
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
    // FLOWING TRACKING
    // =========================
    public static void markFlowing(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return;
        long key = LocationUtil.toKey(loc);
        String worldUid = loc.getWorld().getUID().toString();
        flowingByWorld.computeIfAbsent(worldUid, k -> ConcurrentHashMap.newKeySet()).add(key);
    }

    public static void markFlowingKey(String worldUid, long key) {
        if (worldUid == null || key == 0) return;
        flowingByWorld.computeIfAbsent(worldUid, k -> ConcurrentHashMap.newKeySet()).add(key);
    }

    public static boolean isFlowing(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return false;
        long key = LocationUtil.toKey(loc);
        String worldUid = loc.getWorld().getUID().toString();
        Set<Long> flowing = flowingByWorld.get(worldUid);
        return flowing != null && flowing.contains(key);
    }

    public static boolean isFlowingKey(String worldUid, long key) {
        if (worldUid == null || key == 0) return false;
        Set<Long> flowing = flowingByWorld.get(worldUid);
        return flowing != null && flowing.contains(key);
    }

    public static void clearFlowing() {
        flowingByWorld.clear();
    }

    // =========================
    // SAVE / DELETE — no-op (Marker entities persist in world files)
    // =========================
    public static void save() { /* no-op */ }
    public static void saveNode(CableNode node) { /* no-op */ }
    public static void deleteNode(Location loc) { /* no-op */ }
}
