package com.ultimateimprovments.energy.consumption.light;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.structure.StructureMarker;
import com.ultimateimprovments.util.LocationUtil;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.Materials;
import com.ultimateimprovments.energy.transfer.cable.CableNode;
import com.ultimateimprovments.energy.transfer.cable.CableNetwork;
import com.ultimateimprovments.energy.storage.battery.BatteryManager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 💡 Мультиблочная лампочка (REDSTONE_LAMP)
 * <p>
 * Хранение — Marker entities (не SQLite).
 * Каждый блок получает Marker с PDC: structure_type="light", structure_id=UUID.
 * При разрушении любого блока — весь кластер разбирается.
 */
public class LightManager {

    private static LightManager instance;
    private static final Map<Long, LightCluster> locationToCluster = new ConcurrentHashMap<>();
    private static final Map<Integer, LightCluster> clustersById = new ConcurrentHashMap<>();
    private static int nextId = 1;

    // ════════════════════════════════════════
    // КООРДИНАТНЫЙ КЛЮЧ
    // ════════════════════════════════════════
    public static long toKey(int x, int y, int z) { return StructureMarker.toKey(x, y, z); }
    public static long toKey(Location loc) { return StructureMarker.toKey(loc); }
    public static int getX(long key) { return StructureMarker.getX(key); }
    public static int getZ(long key) { return StructureMarker.getZ(key); }
    public static int getY(long key) { return StructureMarker.getY(key); }

    // ════════════════════════════════════════
    // LIGHT CLUSTER
    // ════════════════════════════════════════
    public static class LightCluster {
        public int id;
        public UUID uuid;
        public World world;
        public Set<Long> blockKeys = new HashSet<>();
        public Location center;
        public int power;
        public int buffer;
        public boolean lit;

        int getBufferCapacity() { return power * 2; }
        boolean isBufferFull() { return buffer >= getBufferCapacity(); }

        private long sumX, sumY, sumZ;

        void addBlock(long key) {
            if (blockKeys.add(key)) {
                sumX += getX(key);
                sumY += getY(key);
                sumZ += getZ(key);
                power = blockKeys.size();
                updateCenterFromSums();
            }
        }

        void removeBlock(long key) {
            if (blockKeys.remove(key)) {
                sumX -= getX(key);
                sumY -= getY(key);
                sumZ -= getZ(key);
                if (!blockKeys.isEmpty()) {
                    power = blockKeys.size();
                    updateCenterFromSums();
                } else {
                    center = null;
                    power = 0;
                }
            }
        }

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
            if (size == 0) { center = null; power = 0; return; }
            center = new Location(world,
                    (int) Math.round((double) sumX / size),
                    (int) Math.round((double) sumY / size),
                    (int) Math.round((double) sumZ / size));
            power = size;
        }

        boolean contains(Location loc) { return blockKeys.contains(toKey(loc)); }

        boolean isAnyBlockPowered() {
            for (long key : blockKeys) {
                Block block = world.getBlockAt(getX(key), getY(key), getZ(key));
                if (block.isBlockPowered()) return true;
            }
            return false;
        }
    }

    // ════════════════════════════════════════
    // ASYNC LIGHTING QUEUE
    // ════════════════════════════════════════
    private static final Deque<Runnable> lightingQueue = new ArrayDeque<>();
    private static boolean queueProcessing = false;

    public static void queueLightingUpdate(Runnable task) {
        synchronized (lightingQueue) { lightingQueue.addLast(task); }
    }

    public static void processLightingQueue() {
        if (queueProcessing) return;
        queueProcessing = true;
        try {
            int batchSize = Math.min(50, lightingQueue.size());
            for (int i = 0; i < batchSize && !lightingQueue.isEmpty(); i++) {
                Runnable task;
                synchronized (lightingQueue) { task = lightingQueue.pollFirst(); }
                if (task != null) {
                    try { task.run(); } catch (Exception e) {
                    ConsoleLogger.warn("[Light] Lighting task error: " + e.getMessage());
                }
                }
            }
        } finally { queueProcessing = false; }
    }

    // ════════════════════════════════════════
    // INIT
    // ════════════════════════════════════════
    public static void init() {
        instance = new LightManager();

        // Восстанавливаем кластеры из Marker'ов
        rebuildFromMarkers();

        ConsoleLogger.info("[LightMulti] Manager initialized with " + clustersById.size() + " clusters (Marker-based)");
    }

    public static LightManager getInstance() { return instance; }

    public static void rebuildFromMarkers() {
        locationToCluster.clear();
        clustersById.clear();
        nextId = 1;

        Map<UUID, Set<Long>> markerGroups = new HashMap<>();
        Map<UUID, World> foundWorlds = new HashMap<>();

        for (Map.Entry<String, StructureMarker.StructureData> entry : StructureMarker.getAllEntries()) {
            if (!"light".equals(entry.getValue().type())) continue;

            UUID uuid = entry.getValue().uuid();
            String fk = entry.getKey();
            long posKey = StructureMarker.toKey(
                StructureMarker.parseX(fk),
                StructureMarker.parseY(fk),
                StructureMarker.parseZ(fk)
            );
            markerGroups.computeIfAbsent(uuid, k -> new HashSet<>()).add(posKey);

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

        Set<UUID> usedUuids = new HashSet<>();
        for (Map.Entry<UUID, Set<Long>> group : markerGroups.entrySet()) {
            if (group.getValue().isEmpty()) continue;
            World world = foundWorlds.get(group.getKey());
            if (world == null) continue;

            LightCluster cluster = new LightCluster();
            cluster.id = nextId++;
            cluster.uuid = group.getKey();
            cluster.world = world;
            cluster.blockKeys = new HashSet<>(group.getValue());
            cluster.recalculateCenter();
            cluster.lit = false;
            cluster.buffer = 0;

            for (long key : group.getValue()) locationToCluster.put(key, cluster);
            clustersById.put(cluster.id, cluster);
            usedUuids.add(cluster.uuid);
        }

        StructureMarker.purgeOrphaned(usedUuids);
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
                if (player != null) w.dropItemNaturally(frame.getLocation(), new ItemStack(Material.ITEM_FRAME));
                frame.remove();
                break;
            }
        }
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
                if (world.getType(nx, ny, nz) == Materials.WAXED_COPPER_BULB) {
                    visited.add(nk);
                    queue.addLast(new int[]{nx, ny, nz});
                }
            }
        }
        return visited;
    }

    // ════════════════════════════════════════
    // ASSEMBLE
    // ════════════════════════════════════════
    public static void assemble(Location loc, Player player) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return;
        long key = toKey(loc);
        if (locationToCluster.containsKey(key)) {
            if (player != null) player.sendMessage("§eLight cluster already assembled here!");
            return;
        }

        Set<Long> connected = floodFillFast(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        if (connected.isEmpty()) {
            if (player != null) player.sendMessage("§4❌ §cNo WAXED_COPPER_BULB blocks nearby!");
            return;
        }

        UUID uuid = UUID.randomUUID();
        LightCluster cluster = new LightCluster();
        cluster.id = nextId++;
        cluster.uuid = uuid;
        cluster.world = loc.getWorld();
        cluster.blockKeys = new HashSet<>(connected);
        cluster.recalculateCenter();
        cluster.lit = false;
        cluster.buffer = 0;

        for (long bk : connected) {
            locationToCluster.put(bk, cluster);
            Location blockLoc = new Location(cluster.world, getX(bk), getY(bk), getZ(bk));
            StructureMarker.place(blockLoc, "light", uuid);
        }
        clustersById.put(cluster.id, cluster);

        removeFrameAt(loc, player);

        if (player != null) {
            player.sendMessage("§a✔ §fLight assembled! (Marker-based)");
            player.sendMessage("§8┃ §7Blocks: §f" + cluster.blockKeys.size() + " §7| Consumption: §f" + cluster.power + " §7energy/tick");
            player.sendMessage("§8┃ §7Buffer: §f" + cluster.getBufferCapacity() + " ⚡ §7(redstone + buffer for lighting)");
        }

        ConsoleLogger.info("[LightMulti] Assembled cluster #" + cluster.id + " UUID=" + uuid + " with " + connected.size() + " lamps");
    }

    // ════════════════════════════════════════
    // DISASSEMBLE
    // ════════════════════════════════════════
    public static void disassemble(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return;
        LightCluster cluster = locationToCluster.get(toKey(loc));
        if (cluster == null) return;

        if (cluster.lit) setBlocksLit(cluster, false);
        for (long bk : cluster.blockKeys) locationToCluster.remove(bk);
        clustersById.remove(cluster.id);

        if (cluster.uuid != null) {
            StructureMarker.removeAllByUuid(cluster.world, cluster.uuid);
        }
    }

    // ════════════════════════════════════════
    // BLOCK PLACED / BROKEN
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
        Set<LightCluster> adj = new LinkedHashSet<>();
        for (long nk : neighborKeys) {
            LightCluster c = locationToCluster.get(nk);
            if (c != null) adj.add(c);
        }
        if (adj.isEmpty()) return;

        if (adj.size() == 1) {
            LightCluster c = adj.iterator().next();
            c.addBlock(key);
            locationToCluster.put(key, c);
            if (c.uuid != null) StructureMarker.place(loc, "light", c.uuid);
            if (c.lit) setBlockLit(loc, true);
        } else {
            Iterator<LightCluster> it = adj.iterator();
            LightCluster primary = it.next();
            while (it.hasNext()) {
                LightCluster other = it.next();
                if (other.lit) setBlocksLit(other, false);
                for (long bk : other.blockKeys) {
                    locationToCluster.put(bk, primary);
                    primary.addBlock(bk);
                    Location blockLoc = new Location(primary.world, getX(bk), getY(bk), getZ(bk));
                    StructureMarker.removeAt(blockLoc);
                    if (primary.uuid != null) StructureMarker.place(blockLoc, "light", primary.uuid);
                }
                clustersById.remove(other.id);
            }
            primary.addBlock(key);
            locationToCluster.put(key, primary);
            if (primary.uuid != null) StructureMarker.place(loc, "light", primary.uuid);
        }
    }

    public static void onBlockBroken(Location loc, Player player) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return;
        LightCluster cluster = locationToCluster.get(toKey(loc));
        if (cluster == null) return;

        if (cluster.lit) setBlocksLit(cluster, false);
        if (cluster.uuid != null) StructureMarker.removeAllByUuid(cluster.world, cluster.uuid);

        for (long bk : cluster.blockKeys) locationToCluster.remove(bk);
        clustersById.remove(cluster.id);
        cluster.blockKeys.clear();

        if (player != null) player.sendMessage("§e❕ Light disassembled (block broken)");
    }

    // ════════════════════════════════════════
    // CHARGE BUFFER FROM CABLE NETWORK
    // ════════════════════════════════════════
    private static void chargeClusterBuffer(LightCluster cluster) {
        if (cluster == null || cluster.buffer >= cluster.getBufferCapacity()) return;

        // Ищем первый блок кластера, рядом с которым есть кабель
        for (long key : cluster.blockKeys) {
            Location blockLoc = new Location(cluster.world, getX(key), getY(key), getZ(key));
            CableNode start = findAdjacentCableNode(blockLoc);
            if (start == null) continue;

            int needed = cluster.getBufferCapacity() - cluster.buffer;
            int pulled = pullEnergyFromNetwork(start, needed);
            if (pulled > 0) {
                cluster.buffer += pulled;
            }
            break; // Одна точка входа за тик
        }
    }

    private static CableNode findAdjacentCableNode(Location loc) {
        for (Location near : LocationUtil.getNeighbors(loc)) {
            CableNode node = CableNetwork.getNode(near);
            if (node != null) return node;
        }
        return null;
    }

    private static int pullEnergyFromNetwork(CableNode start, int amount) {
        if (start == null || amount <= 0) return 0;

        String worldUid = start.getWorld().getUID().toString();
        Set<Long> visited = new HashSet<>();
        Queue<CableNode> queue = new LinkedList<>();
        queue.add(start);
        visited.add(start.getKey());

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

            for (long connKey : node.getConnectionKeys()) {
                if (visited.contains(connKey)) continue;
                CableNode next = CableNetwork.getNodeByKey(worldUid, connKey);
                if (next == null) continue;
                visited.add(connKey);
                queue.add(next);
            }
        }

        return amount - remaining;
    }

    // ════════════════════════════════════════
    // TICK
    // ════════════════════════════════════════
    public static void tick() {
        List<Integer> toRemove = new ArrayList<>();

        for (LightCluster cluster : clustersById.values()) {
            try {
                if (cluster.world == null || cluster.blockKeys.isEmpty()) continue;

                long firstKey = cluster.blockKeys.iterator().next();
                if (!cluster.world.isChunkLoaded(getX(firstKey) >> 4, getZ(firstKey) >> 4)) continue;

                // Анти-фантом: проверяем, что блок всё ещё WAXED_COPPER_BULB
                if (cluster.world.getType(getX(firstKey), getY(firstKey), getZ(firstKey)) != Materials.WAXED_COPPER_BULB) {
                    toRemove.add(cluster.id);
                    continue;
                }

                boolean hasRedstone = cluster.isAnyBlockPowered();

                // Если есть редстоун и буфер не полон — заряжаем от сети
                if (hasRedstone && !cluster.isBufferFull()) {
                    chargeClusterBuffer(cluster);
                }

                // Лампочка горит ТОЛЬКО если редстоун И буфер полон
                boolean shouldBeLit = hasRedstone && cluster.isBufferFull();

                if (shouldBeLit != cluster.lit) {
                    final boolean newState = shouldBeLit;
                    queueLightingUpdate(() -> {
                        cluster.lit = newState;
                        setBlocksLit(cluster, newState);
                    });
                }

                // Если горим — потребляем энергию из буфера
                if (cluster.lit) {
                    cluster.buffer = Math.max(0, cluster.buffer - cluster.power);
                }
            } catch (Exception e) {
                ConsoleLogger.warn("[LightMulti] Tick error: " + e.getMessage());
            }
        }

        // Очищаем фантомные кластеры
        for (int id : toRemove) {
            LightCluster cluster = clustersById.get(id);
            if (cluster != null) {
                if (cluster.uuid != null) {
                    StructureMarker.removeAllByUuid(cluster.world, cluster.uuid);
                }
                for (long bk : cluster.blockKeys) locationToCluster.remove(bk);
                clustersById.remove(id);
                cluster.blockKeys.clear();
                ConsoleLogger.info("[LightMulti] Removed phantom cluster #" + id);
            }
        }

        processLightingQueue();
    }

    // ════════════════════════════════════════
    // SET BLOCKS LIT/UNLIT
    // ════════════════════════════════════════
    private static void setBlocksLit(LightCluster cluster, boolean lit) {
        for (long key : cluster.blockKeys) {
            Block block = cluster.world.getBlockAt(getX(key), getY(key), getZ(key));
            setBlockLitState(block, lit);
        }
    }

    private static void setBlockLit(Location loc, boolean lit) {
        setBlockLitState(loc.getBlock(), lit);
    }

    private static void setBlockLitState(Block block, boolean lit) {
        if (block.getType() != Materials.WAXED_COPPER_BULB) return;
        try {
            BlockData data = block.getBlockData();
            if (data instanceof Lightable lightable) {
                if (lightable.isLit() != lit) {
                    lightable.setLit(lit);
                    block.setBlockData(lightable, false);
                }
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[Light] setBlockLitState error: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════
    // QUERIES
    // ════════════════════════════════════════
    public static boolean isActive(Location loc) {
        loc = LocationUtil.normalize(loc);
        return loc != null && locationToCluster.containsKey(toKey(loc));
    }

    public static boolean isLit(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return false;
        LightCluster c = locationToCluster.get(toKey(loc));
        return c != null && c.lit;
    }

    public static LightCluster getCluster(Location loc) {
        loc = LocationUtil.normalize(loc);
        return loc != null ? locationToCluster.get(toKey(loc)) : null;
    }

    public static Collection<LightCluster> getAllClusters() { return clustersById.values(); }
    public static int getClusterCount() { return clustersById.size(); }

    // ════════════════════════════════════════
    // SAVE — no-op (Marker'ы сами сохраняются)
    // ════════════════════════════════════════
    public static void saveAll() { /* no-op */ }

    static Map<Long, LightCluster> getLocationMap() { return locationToCluster; }
    static Map<Integer, LightCluster> getClustersById() { return clustersById; }

    public static void clearAll() {
        locationToCluster.clear();
        clustersById.clear();
        lightingQueue.clear();
    }
}
