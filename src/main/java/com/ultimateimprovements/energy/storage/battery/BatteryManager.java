package com.ultimateimprovements.energy.storage.battery;

import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.structure.StructureMarker;
import com.ultimateimprovements.util.LocationUtil;
import com.ultimateimprovements.util.MessageUtil;
import com.ultimateimprovements.util.ConsoleLogger;
import com.ultimateimprovements.util.Materials;
import com.ultimateimprovements.energy.transfer.cable.CableNetwork;
import com.ultimateimprovements.energy.transfer.cable.CableNode;
import com.ultimateimprovements.energy.transfer.cable.NodeType;

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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ⚡ Мультиблочная батарея (WAXED_COPPER_GRATE)
 * <p>
 * Хранение — Marker entities (не SQLite). Каждый блок получает Marker с PDC:
 *   structure_type="battery", structure_id=UUID
 * <p>
 * При разрушении любого блока — весь кластер разбирается, все Markers удаляются.
 * При загрузке чанка — Markers сканируются и кэш восстанавливается.
 */
public class BatteryManager implements Listener {

    // ════════════════════════════════════════
    // РЕЖИМЫ БАТАРЕИ
    // ════════════════════════════════════════
    public enum BatteryMode {
        CHARGE, CHARGE_DISCHARGE, DISCHARGE
    }

    private static BatteryManager instance;
    private static final Map<Long, BatteryCluster> locationToCluster = new ConcurrentHashMap<>();
    private static final Map<Integer, BatteryCluster> clustersById = new ConcurrentHashMap<>();
    private static int nextId = 1;

    // ════════════════════════════════════════
    // КООРДИНАТНЫЙ КЛЮЧ (через StructureMarker)
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

        private long sumX, sumY, sumZ;

        void addBlock(long key) {
            if (blockKeys.add(key)) {
                sumX += getX(key);
                sumY += getY(key);
                sumZ += getZ(key);
                capacity = blockKeys.size() * 1000;
                updateCenterFromSums();
            }
        }

        void removeBlock(long key) {
            if (blockKeys.remove(key)) {
                sumX -= getX(key);
                sumY -= getY(key);
                sumZ -= getZ(key);
                if (!blockKeys.isEmpty()) {
                    capacity = blockKeys.size() * 1000;
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
            for (long key : blockKeys) {
                sumX += getX(key);
                sumY += getY(key);
                sumZ += getZ(key);
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
            capacity = size * 1000;
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

        String getModeDisplay() {
            return switch (mode) {
                case CHARGE -> "<aqua>Charge</aqua>";
                case CHARGE_DISCHARGE -> "<light_purple>Charge & Discharge</light_purple>";
                case DISCHARGE -> "<red>Discharge</red>";
            };
        }
    }

    // ════════════════════════════════════════
    // INIT — сканируем все загруженные чанки на Marker'ы
    // ════════════════════════════════════════
    public static void init() {
        instance = new BatteryManager();
        Bukkit.getPluginManager().registerEvents(instance, Main.getInstance());

        // Восстанавливаем кластеры из Marker'ов в загруженных чанках
        rebuildFromMarkers();

        ConsoleLogger.info("[BatteryMulti] Manager initialized with " + clustersById.size() + " clusters (Marker-based)");
    }

    public static BatteryManager getInstance() { return instance; }

    /**
     * Сканирует все загруженные чанки на Marker'ы типа "battery",
     * группирует по UUID, находит мир из worldUid в StructureData
     * и восстанавливает кластеры. После создания — чистит orphaned Marker'ы.
     */
    public static void rebuildFromMarkers() {
        locationToCluster.clear();
        clustersById.clear();
        nextId = 1;

        // Группируем Marker'ы по UUID + запоминаем мир из StructureData
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

            // Находим мир по worldUid (из StructureData — не из перебора миров!)
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

        // Создаём кластеры из групп
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

            for (long key : group.getValue()) {
                locationToCluster.put(key, cluster);
                // Создаём CableNode с типом BATTERY (без "cable" маркера)
                Location blockLoc = new Location(world, getX(key), getY(key), getZ(key));
                CableNetwork.ensureNode(blockLoc, NodeType.BATTERY);
            }
            clustersById.put(cluster.id, cluster);
            usedUuids.add(cluster.uuid);
        }

        // Чистим orphaned Marker'ы (которые не попали ни в один кластер)
        StructureMarker.purgeOrphaned(usedUuids);
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
    // ASSEMBLE — создаём Marker на каждом блоке
    // ════════════════════════════════════════
    public static void assemble(Location loc, Player player) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return;
        long key = toKey(loc);
        if (locationToCluster.containsKey(key)) {
            if (player != null) player.sendMessage("§eBattery already assembled here!");
            return;
        }

        Set<Long> connected = floodFillFast(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        if (connected.isEmpty()) {
            if (player != null) player.sendMessage("§4❌ §cNo WAXED_COPPER_GRATE blocks nearby!");
            return;
        }

        UUID uuid = UUID.randomUUID();
        BatteryCluster cluster = new BatteryCluster();
        cluster.id = nextId++;
        cluster.uuid = uuid;
        cluster.world = loc.getWorld();
        cluster.blockKeys = new HashSet<>(connected);
        cluster.recalculateCenter();

        for (long bk : connected) {
            locationToCluster.put(bk, cluster);
            // Создаём Marker entity на каждом блоке
            Location blockLoc = new Location(cluster.world, getX(bk), getY(bk), getZ(bk));
            StructureMarker.place(blockLoc, "battery", uuid);
            // Создаём CableNode с типом BATTERY
            CableNetwork.ensureNode(blockLoc, NodeType.BATTERY);
        }
        clustersById.put(cluster.id, cluster);

        removeFrameAt(loc, player);

        if (cluster.center != null) {
            World w = cluster.center.getWorld();
            if (w != null) {
                w.spawnParticle(Particle.END_ROD, cluster.center.clone().add(0.5, 0.5, 0.5), 30, 0.5, 0.5, 0.5, 0.1);
                w.playSound(cluster.center, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
            }
        }

        if (player != null) {
            player.sendMessage("§a✔ §fBattery assembled! (Marker-based)");
            player.sendMessage("§8┃ §7Blocks: §f" + cluster.blockKeys.size() + " §7| Capacity: §f" + cluster.capacity + " §7energy");
            player.sendMessage("§8┃ §7Apply redstone to any block to discharge");
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
            // Удаляем CableNode для этого блока
            Location blockLoc = new Location(cluster.world, getX(bk), getY(bk), getZ(bk));
            CableNetwork.removeNode(blockLoc);
        }
        clustersById.remove(cluster.id);

        // Удаляем все Marker'ы кластера
        if (cluster.uuid != null) {
            StructureMarker.removeAllByUuid(cluster.world, cluster.uuid);
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
            // Новый Marker на добавленном блоке
            if (c.uuid != null) {
                StructureMarker.place(loc, "battery", c.uuid);
            }
            // Создаём CableNode с типом BATTERY
            CableNetwork.ensureNode(loc, NodeType.BATTERY);
        } else {
            Iterator<BatteryCluster> it = adj.iterator();
            BatteryCluster primary = it.next();
            while (it.hasNext()) {
                BatteryCluster other = it.next();
                for (long bk : other.blockKeys) {
                    locationToCluster.put(bk, primary);
                    primary.addBlock(bk);
                    // Обновляем Marker — меняем UUID на primary
                    Location blockLoc = new Location(primary.world, getX(bk), getY(bk), getZ(bk));
                    StructureMarker.removeAt(blockLoc);
                    if (primary.uuid != null) {
                        StructureMarker.place(blockLoc, "battery", primary.uuid);
                    }
                }
                clustersById.remove(other.id);
            }
            primary.addBlock(key);
            locationToCluster.put(key, primary);
            if (primary.uuid != null) {
                StructureMarker.place(loc, "battery", primary.uuid);
            }
            // Создаём CableNode с типом BATTERY
            CableNetwork.ensureNode(loc, NodeType.BATTERY);
        }
    }

    // ════════════════════════════════════════
    // BLOCK BROKEN — разбираем весь кластер
    // ════════════════════════════════════════
    public static void onBlockBroken(Location loc, Player player) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return;
        long key = toKey(loc);
        BatteryCluster cluster = locationToCluster.get(key);
        if (cluster == null) return;

        // Удаляем все Marker'ы кластера из мира
        if (cluster.uuid != null) {
            StructureMarker.removeAllByUuid(cluster.world, cluster.uuid);
        }

        // Очищаем кэш и CableNode
        for (long bk : cluster.blockKeys) {
            locationToCluster.remove(bk);
            CableNetwork.removeNode(new Location(cluster.world, getX(bk), getY(bk), getZ(bk)));
        }
        clustersById.remove(cluster.id);
        cluster.blockKeys.clear();

        ConsoleLogger.info("[BatteryMulti] Disassembled cluster #" + cluster.id
                + " due to block break at " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ());

        if (player != null) {
            player.sendMessage("§e❕ Battery disassembled (block broken)");
        }
    }

    // ════════════════════════════════════════
    // SHIFT+ПКМ ПУСТОЙ РУКОЙ — ПЕРЕКЛЮЧЕНИЕ РЕЖИМА
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
    // TICK — частицы + анти-фантом
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

                // Анти-фантом: проверяем Marker и блок
                if (cluster.world.getType(fx, fy, fz) != Materials.WAXED_COPPER_GRATE) {
                    toRemove.add(cluster.id);
                    continue;
                }

                double pct = getChargePercentage(cluster);
                int count = Math.max(1, (int) Math.round(pct / 10.0));

                Location center = cluster.center.clone().add(0.5, 0.5, 0.5);
                cluster.world.spawnParticle(Particle.END_ROD, center, count, 0.3, 0.3, 0.3, 0.01);
                cluster.world.spawnParticle(Particle.ELECTRIC_SPARK, center, count / 2, 0.3, 0.3, 0.3, 0);
            } catch (Exception e) {
                ConsoleLogger.warn("[BatteryMulti] Tick error: " + e.getMessage());
            }
        }

        // Очищаем фантомные кластеры
        for (int id : toRemove) {
            BatteryCluster cluster = clustersById.get(id);
            if (cluster != null) {
                for (long bk : cluster.blockKeys) {
                    locationToCluster.remove(bk);
                    CableNetwork.removeNode(new Location(cluster.world, getX(bk), getY(bk), getZ(bk)));
                }
                clustersById.remove(id);
                // Удаляем Marker'ы
                if (cluster.uuid != null) {
                    StructureMarker.removeAllByUuid(cluster.world, cluster.uuid);
                }
                ConsoleLogger.info("[BatteryMulti] Removed phantom cluster #" + id);
            }
        }
    }

    // ════════════════════════════════════════
    // SAVE / LOAD — больше не нужны (Marker'ы сами сохраняются)
    // ════════════════════════════════════════
    public static void saveAll() { /* no-op: Marker entities persist in world files */ }

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
