package com.mcplugin.energy.transfer.cable;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.structure.StructureMarker;
import com.mcplugin.infrastructure.util.LocationUtil;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Material;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CableNetwork {

    // =========================
    // MEMORY CACHE
    // =========================
    private static final Map<Location, CableNode> nodes = new ConcurrentHashMap<>();

    // =========================
    // FLOWING TRACKING
    // =========================
    private static final Set<Location> flowingCables = ConcurrentHashMap.newKeySet();

    // =========================
    // INIT — rebuild from Marker entities instead of SQLite
    // =========================
    public static void init() {
        nodes.clear();
        rebuildFromMarkers();
    }

    /**
     * Сканирует кэш StructureMarker на type="cable" и type="battery" и создаёт CableNode для каждого.
     * "battery" обрабатывается, т.к. WAXED_COPPER_GRATE уже имеет "battery" маркер от BatteryManager,
     * и "cable" маркер не создаётся из-за позиционного дедупликации в StructureMarker.place().
     * После создания — авто-соединяет соседние кабели.
     */
    private static void rebuildFromMarkers() {
        int count = 0;
        for (Map.Entry<String, StructureMarker.StructureData> entry : StructureMarker.getAllEntries()) {
            String markerType = entry.getValue().type();
            if (!"cable".equals(markerType) && !"battery".equals(markerType)) continue;

            String fk = entry.getKey();
            String worldUid = StructureMarker.parseWorldUid(fk);
            int x = StructureMarker.parseX(fk), y = StructureMarker.parseY(fk), z = StructureMarker.parseZ(fk);

            // Находим мир по UUID
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

            Location loc = LocationUtil.normalize(new Location(world, x, y, z));
            if (nodes.containsKey(loc)) continue;

            CableNode node = new CableNode(loc);

            // Определяем тип: если блок WAXED_COPPER_GRATE — это BATTERY (старый формат)
            if (world.getBlockAt(x, y, z).getType() == Material.WAXED_COPPER_GRATE) {
                node.setType(NodeType.BATTERY);
            }

            nodes.put(loc, node);
            count++;
        }

        // Авто-соединение: каждый узел соединяется с соседями
        for (CableNode node : nodes.values()) {
            Location loc = node.getLocation();
            for (Location near : LocationUtil.getNeighbors(loc)) {
                Location norm = LocationUtil.normalize(near);
                CableNode neighbor = nodes.get(norm);
                if (neighbor == null) continue;

                // Не соединяем BATTERY ↔ BATTERY
                if (node.getType() == NodeType.BATTERY && neighbor.getType() == NodeType.BATTERY) continue;

                if (!LocationUtil.isFullyConnected(loc, norm)) continue;

                node.connect(norm);
                neighbor.connect(loc);
            }
        }

        Main.getInstance().getLogger().info(
                "[CableNetwork] Loaded " + count + " nodes from Marker entities"
        );
    }

    // =========================
    // ADD NODE (+ Marker entity)
    // =========================
    public static void addNode(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (nodes.containsKey(loc)) return;

        CableNode node = new CableNode(loc);
        nodes.put(loc, node);

        // Marker entity для переноса мира без БД
        StructureMarker.place(loc, "cable", UUID.randomUUID());
    }

    // =========================
    // REMOVE NODE (+ Marker cleanup)
    // =========================
    public static void removeNode(Location loc) {
        loc = LocationUtil.normalize(loc);

        CableNode node = nodes.remove(loc);
        if (node != null) {
            for (Location connected : node.getConnections()) {
                CableNode other = nodes.get(connected);
                if (other != null) other.disconnect(loc);
            }
        }

        // Удаляем Marker entity
        StructureMarker.removeAt(loc);
    }

    // =========================
    // GET
    // =========================
    public static CableNode getNode(Location loc) {
        return nodes.get(LocationUtil.normalize(loc));
    }

    public static boolean exists(Location loc) {
        return nodes.containsKey(LocationUtil.normalize(loc));
    }

    public static Collection<CableNode> getAllNodes() {
        return nodes.values();
    }

    // =========================
    // FLOWING TRACKING
    // =========================
    public static void markFlowing(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc != null) flowingCables.add(loc);
    }

    public static boolean isFlowing(Location loc) {
        loc = LocationUtil.normalize(loc);
        return loc != null && flowingCables.contains(loc);
    }

    public static void clearFlowing() {
        flowingCables.clear();
    }

    // =========================
    // SAVE — no-op: Marker entities persist in world files
    // =========================
    public static void save() { /* no-op */ }
    public static void saveNode(CableNode node) { /* no-op */ }
    public static void deleteNode(Location loc) { /* no-op */ }
}