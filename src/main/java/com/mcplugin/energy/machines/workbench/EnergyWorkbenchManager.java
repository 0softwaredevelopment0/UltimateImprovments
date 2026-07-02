package com.mcplugin.energy.machines.workbench;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.structure.StructureMarker;
import com.mcplugin.infrastructure.util.LocationUtil;
import com.mcplugin.infrastructure.util.ConsoleLogger;
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

public class EnergyWorkbenchManager {

    // =========================
    // CONSTANTS
    // =========================
    private static final int BUFFER_MAX = 100;

    // =========================
    // CACHE
    // =========================
    private static final Set<Location> workbenches = new HashSet<>();
    private static final Map<Location, Integer> energyBuffer = new HashMap<>();
    private static final Map<UUID, Location> playerWorkbench = new HashMap<>();

    // =========================
    // INIT — rebuild from Marker entities
    // =========================
    public static void init() {

        workbenches.clear();
        energyBuffer.clear();
        playerWorkbench.clear();
        scanFromMarkers();
    }

    // =========================
    // SCAN FROM MARKER ENTITIES
    // =========================
    public static void scanFromMarkers() {
        workbenches.clear();

        for (Map.Entry<String, StructureMarker.StructureData> entry : StructureMarker.getAllEntries()) {
            String type = entry.getValue().type();
            if (!"workbench".equals(type) && !"assembler".equals(type)) continue;

            String fk = entry.getKey();
            String worldUid = StructureMarker.parseWorldUid(fk);
            int x = StructureMarker.parseX(fk), y = StructureMarker.parseY(fk), z = StructureMarker.parseZ(fk);

            // Находим мир по UUID
            for (World world : Main.getInstance().getServer().getWorlds()) {
                if (!world.getUID().toString().equals(worldUid)) continue;
                Location loc = LocationUtil.normalize(new Location(world, x, y, z));
                if (loc != null) {
                    workbenches.add(loc);
                }
                break;
            }
        }

        ConsoleLogger.info(
                "[EnergyWorkbenchManager] Loaded " + workbenches.size() + " workbenches from Marker entities"
        );
    }

    // =========================
    // ADD (+ Marker entity)
    // =========================
    public static void add(Location loc) {

        if (loc == null) return;

        loc = LocationUtil.normalize(loc);

        if (workbenches.contains(loc)) return;

        workbenches.add(loc);
        StructureMarker.place(loc, "workbench", UUID.randomUUID());
    }

    // =========================
    // REMOVE (+ Marker cleanup)
    // =========================
    public static void remove(Location loc) {

        if (loc == null) return;

        loc = LocationUtil.normalize(loc);

        workbenches.remove(loc);
        energyBuffer.remove(loc);
        StructureMarker.removeAt(loc);
    }

    // =========================
    // EXISTS
    // =========================
    public static boolean exists(Location loc) {

        if (loc == null) return false;

        return workbenches.contains(LocationUtil.normalize(loc));
    }

    // =========================
    // GET ALL
    // =========================
    public static Set<Location> getAll() {
        return workbenches;
    }

    // =========================
    // PLAYER WORKBENCH TRACKING
    // =========================
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

    // =========================
    // BUFFER API
    // =========================
    /**
     * @return текущий уровень буфера (0..BUFFER_MAX)
     */
    public static int getBufferEnergy(Location loc) {
        if (loc == null) return 0;
        return energyBuffer.getOrDefault(LocationUtil.normalize(loc), 0);
    }

    /**
     * Хватает ли энергии в буфере для крафта?
     */
    public static boolean hasBufferEnergy(Location loc, int amount) {
        return getBufferEnergy(loc) >= amount;
    }

    /**
     * Потратить энергию из буфера (после крафта)
     */
    public static void consumeBufferEnergy(Location loc, int amount) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return;
        int current = energyBuffer.getOrDefault(loc, 0);
        if (current >= amount) {
            energyBuffer.put(loc, current - amount);
        }
    }

    /**
     * Пополнить буфер энергией (вызывается извне, если нужно принудительно)
     */
    public static void addBufferEnergy(Location loc, int amount) {
        loc = LocationUtil.normalize(loc);
        if (loc == null || amount <= 0) return;
        int current = energyBuffer.getOrDefault(loc, 0);
        int newLevel = Math.min(current + amount, BUFFER_MAX);
        energyBuffer.put(loc, newLevel);
    }

    // =========================
    // CHARGE ALL BUFFERS (тиковый таймер)
    // Для каждого зарегистрированного workbench:
    //   1. Если буфер полон — пропускаем
    //   2. Ищем кабель рядом (6 сторон)
    //   3. BFS по сети — забираем энергию из батарей в буфер
    // =========================
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
                    energyBuffer.put(loc, current + pulled);
                }
            } catch (Exception ignored) {}
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

        Set<Location> visited = new HashSet<>();
        Queue<CableNode> queue = new LinkedList<>();
        queue.add(start);
        visited.add(start.getLocation());

        int remaining = amount;

        while (!queue.isEmpty() && remaining > 0) {
            CableNode node = queue.poll();
            if (node == null) continue;

            // Уважаем режим батареи: берём только из DISCHARGE/CHARGE_DISCHARGE
            BatteryManager.BatteryCluster bc = BatteryManager.getCluster(node.getLocation());
            if (bc != null && !bc.canDischarge()) continue;

            int energy = node.getEnergy();
            if (energy > 0) {
                int take = Math.min(energy, remaining);
                node.removeEnergy(take);
                remaining -= take;
            }

            if (remaining <= 0) break;

            for (Location conn : node.getConnections()) {
                if (visited.contains(conn)) continue;
                CableNode next = CableNetwork.getNode(conn);
                if (next == null) continue;
                visited.add(conn);
                queue.add(next);
            }
        }

        return amount - remaining;
    }

    // =========================
    // MAINTAIN LOCK — блокируем авто-крафт по редстоуну (тиковый таймер)
    // Crafter.setTriggered(true) предотвращает ванильный авто-крафт при редстоун-сигнале.
    // Вызывать периодически, т.к. triggered сбрасывается блоком после импульса.
    // =========================
    public static void maintainLocks() {
        for (Location loc : workbenches) {
            try {
                if (loc.getBlock().getType() != Material.CRAFTER) continue;
                if (loc.getBlock().getBlockData() instanceof Crafter crafter) {
                    if (!crafter.isTriggered()) {
                        crafter.setTriggered(true);
                        loc.getBlock().setBlockData(crafter, false);
                    }
                }
                // Устанавливаем кастомное имя, если его нет (для уже существующих блоков)
                var state = loc.getBlock().getState();
                if (state instanceof org.bukkit.block.Crafter crafterState) {
                    if (crafterState.customName() == null) {
                        crafterState.customName(Component.text("Item assembler"));
                        crafterState.update();
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    // =========================
    // PHYSICS LISTENER — блокирует авто-крафт ДО того, как блок обработает соседний сигнал
    // BlockPhysicsEvent срабатывает при любом обновлении соседних блоков (включая редстоун).
    // Если CRAFTER получает соседнее обновление — сразу ставим triggered = true,
    // чтобы блок НЕ запустил крафт.
    // =========================
    public static class RedstoneListener implements Listener {

        @EventHandler
        public void onBlockPhysics(BlockPhysicsEvent e) {
            if (e.getBlock().getType() != Material.CRAFTER) return;

            Location loc = LocationUtil.normalize(e.getBlock().getLocation());
            if (!workbenches.contains(loc)) return;

            // Форсируем triggered = true ДО того, как CRAFTER обработает сигнал
            if (e.getBlock().getBlockData() instanceof Crafter crafter) {
                if (!crafter.isTriggered()) {
                    crafter.setTriggered(true);
                    e.getBlock().setBlockData(crafter, false);
                }
            }
        }
    }
}