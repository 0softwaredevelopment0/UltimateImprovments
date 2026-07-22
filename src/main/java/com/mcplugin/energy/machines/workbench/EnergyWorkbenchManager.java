package com.mcplugin.energy.machines.workbench;

import com.mcplugin.core.Main;
import com.mcplugin.structure.StructureMarker;
import com.mcplugin.util.LocationUtil;
import com.mcplugin.util.ConsoleLogger;
import com.mcplugin.energy.storage.battery.BatteryManager;
import com.mcplugin.energy.transfer.cable.CableNetwork;
import com.mcplugin.energy.transfer.cable.CableNode;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.block.data.type.Crafter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EnergyWorkbenchManager {

    private static final int BUFFER_MAX = 100;

    // Thread-safe storage using ConcurrentHashMap
    private static final Set<Location> workbenches = ConcurrentHashMap.newKeySet();
    private static final Map<Location, Integer> energyBuffer = new ConcurrentHashMap<>();
    private static final Map<UUID, Location> playerWorkbench = new ConcurrentHashMap<>();

    public static void init() {
        workbenches.clear();
        energyBuffer.clear();
        playerWorkbench.clear();
        scanFromMarkers();
    }

    public static void scanFromMarkers() {
        workbenches.clear();

        for (Map.Entry<String, StructureMarker.StructureData> entry : StructureMarker.getAllEntries()) {
            String type = entry.getValue().type();
            if (!"workbench".equals(type) && !"assembler".equals(type) && !"item_creator".equals(type)) continue;

            String fk = entry.getKey();
            String worldUid = StructureMarker.parseWorldUid(fk);
            int x = StructureMarker.parseX(fk), y = StructureMarker.parseY(fk), z = StructureMarker.parseZ(fk);

            for (World world : Main.getInstance().getServer().getWorlds()) {
                if (!world.getUID().toString().equals(worldUid)) continue;
                Location loc = LocationUtil.normalize(new Location(world, x, y, z));
                if (loc != null) {
                    workbenches.add(loc);
                }
                break;
            }
        }
    }

    public static void add(Location loc) {
        if (loc == null) return;
        loc = LocationUtil.normalize(loc);
        if (workbenches.contains(loc)) return;

        workbenches.add(loc);
        StructureMarker.place(loc, "workbench", UUID.randomUUID());
    }

    public static void remove(Location loc) {
        if (loc == null) return;
        loc = LocationUtil.normalize(loc);

        workbenches.remove(loc);
        energyBuffer.remove(loc);
        StructureMarker.removeAt(loc);
    }

    public static boolean exists(Location loc) {
        if (loc == null) return false;
        return workbenches.contains(LocationUtil.normalize(loc));
    }

    public static Set<Location> getAll() {
        return workbenches;
    }

    public static void setPlayerWorkbench(Player player, Location loc) {
        if (player == null || loc == null) return;
        playerWorkbench.put(player.getUniqueId(), LocationUtil.normalize(loc));
    }

    public static Location getPlayerWorkbench(Player player) {
        if (player == null) return null;
        return playerWorkbench.get(player.getUniqueId());
    }

    public static void clearPlayerWorkbench(Player player) {
        if (player != null) {
            playerWorkbench.remove(player.getUniqueId());
        }
    }

    public static int getBufferEnergy(Location loc) {
        if (loc == null) return 0;
        return energyBuffer.getOrDefault(LocationUtil.normalize(loc), 0);
    }

    public static boolean hasBufferEnergy(Location loc, int amount) {
        return getBufferEnergy(loc) >= amount;
    }

    public static void consumeBufferEnergy(Location loc, int amount) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return;
        energyBuffer.computeIfPresent(loc, (k, v) -> v >= amount ? v - amount : v);
    }

    public static void addBufferEnergy(Location loc, int amount) {
        loc = LocationUtil.normalize(loc);
        if (loc == null || amount <= 0) return;
        energyBuffer.merge(loc, amount, (old, val) -> Math.min(old + val, BUFFER_MAX));
    }

    public static void chargeAllBuffers() {
        for (Location loc : workbenches) {
            try {
                if (loc.getBlock().getType() != Material.CRAFTER) continue;

                int current = energyBuffer.getOrDefault(loc, 0);
                if (current >= BUFFER_MAX) continue;

                CableNode start = findAdjacentCableNode(loc);
                if (start == null) continue;

                int needed = BUFFER_MAX - current;
                int pulled = pullEnergyFromNetwork(start, needed);

                if (pulled > 0) {
                    energyBuffer.merge(loc, pulled, (old, val) -> Math.min(old + val, BUFFER_MAX));
                }
            } catch (Exception e) {
                ConsoleLogger.warn("[Workbench] chargeAllBuffers error: " + e.getMessage());
            }
        }
    }

    private static CableNode findAdjacentCableNode(Location workbench) {
        for (Location near : LocationUtil.getNeighbors(workbench)) {
            Location norm = LocationUtil.normalize(near);
            CableNode node = CableNetwork.getNode(norm);
            if (node != null) return node;
        }
        return null;
    }

    private static int pullEnergyFromNetwork(CableNode start, int amount) {
        if (start == null || amount <= 0) return 0;

        Set<Long> visited = new HashSet<>();
        Queue<CableNode> queue = new LinkedList<>();
        queue.add(start);
        visited.add(start.getKey());

        int remaining = amount;

        while (!queue.isEmpty() && remaining > 0) {
            CableNode node = queue.poll();
            if (node == null) continue;

            BatteryManager.BatteryCluster bc = BatteryManager.getCluster(node.getLocation());
            if (bc != null && !bc.canDischarge()) continue;

            int energy = node.getEnergy();
            if (energy > 0) {
                int take = Math.min(energy, remaining);
                node.removeEnergy(take);
                remaining -= take;
            }

            if (remaining <= 0) break;

            for (long connKey : node.getConnectionKeys()) {
                if (visited.contains(connKey)) continue;
                CableNode next = CableNetwork.getNodeByKey(node.getWorld().getUID().toString(), connKey);
                if (next == null) continue;
                visited.add(connKey);
                queue.add(next);
            }
        }

        return amount - remaining;
    }

    public static void maintainLocks() {
        for (Location loc : workbenches) {
            try {
                if (loc.getBlock().getType() != Material.CRAFTER) continue;
                // For item_creator, do NOT force triggered=true — let redstone control it
                var state = loc.getBlock().getState();
                if (state instanceof org.bukkit.block.Crafter crafterState) {
                    if (crafterState.customName() == null) {
                        crafterState.customName(Component.text("Создатель предметов"));
                        crafterState.update();
                    }
                }
            } catch (Exception e) {
                ConsoleLogger.warn("[Workbench] maintainLocks error: " + e.getMessage());
            }
        }
    }

    public static class RedstoneListener implements Listener {
        @EventHandler
        public void onBlockPhysics(BlockPhysicsEvent e) {
            if (e.getBlock().getType() != Material.CRAFTER) return;
            Location loc = LocationUtil.normalize(e.getBlock().getLocation());
            if (!workbenches.contains(loc)) return;

            // For item_creator, don't force triggered state — let redstone pulses control it
        }
    }
}
