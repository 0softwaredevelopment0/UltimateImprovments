package com.mcplugin.energy.storage.battery;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.LocationUtil;
import com.mcplugin.infrastructure.util.MessageUtil;
import com.mcplugin.energy.transfer.cable.CableNetwork;
import com.mcplugin.energy.transfer.cable.CableNode;

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
 * Соединяет соседние блоки WAXED_COPPER_GRATE через flood-fill (как магнит).
 * Каждый блок = +1000 энергии к максимальной ёмкости.
 * Режим переключается SHIFT+ПКМ пустой рукой: Зарядка / Зарядка+Разрядка / Разрядка.
 * Hot-изменение ёмкости при добавлении/ломании блоков.
 * Сборка через SHIFT+ПКМ по рамке на любом блоке WAXED_COPPER_GRATE.
 */
public class BatteryManager implements Listener {

    // ════════════════════════════════════════
    // РЕЖИМЫ БАТАРЕИ
    // ════════════════════════════════════════
    public enum BatteryMode {
        /** Только принимает энергию (от генераторов) */
        CHARGE,
        /** И принимает, и отдаёт */
        CHARGE_DISCHARGE,
        /** Только отдаёт энергию (потребителям) */
        DISCHARGE
    }

    private static BatteryManager instance;
    private static final Map<Long, BatteryCluster> locationToCluster = new HashMap<>();
    private static final Map<Integer, BatteryCluster> clustersById = new HashMap<>();
    private static int nextId = 1;

    // ════════════════════════════════════════
    // КООРДИНАТНЫЙ КЛЮЧ (как в MagnetManager)
    // ════════════════════════════════════════
    public static final int COORD_OFFSET = 33554432;
    public static final int Y_OFFSET = 64;

    public static long toKey(int x, int y, int z) {
        return ((long)(x + COORD_OFFSET) << 38)
             | ((long)(z + COORD_OFFSET) << 12)
             | ((y + Y_OFFSET) & 0xFFFL);
    }
    public static long toKey(Location loc) {
        return toKey(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
    public static int getX(long key) {
        return (int)((key >>> 38) & 0x3FFFFFFL) - COORD_OFFSET;
    }
    public static int getZ(long key) {
        return (int)((key >>> 12) & 0x3FFFFFFL) - COORD_OFFSET;
    }
    public static int getY(long key) {
        return (int)(key & 0xFFFL) - Y_OFFSET;
    }

    // ════════════════════════════════════════
    // BATTERY CLUSTER
    // ════════════════════════════════════════
    public static class BatteryCluster {
        public int id;
        public World world;
        public Set<Long> blockKeys = new HashSet<>();
        public Location center;
        public int capacity; // blockKeys.size() * 1000
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

        boolean contains(Location loc) {
            return blockKeys.contains(toKey(loc));
        }

        /**
         * Можно ли заряжать эту батарею (принимать энергию).
         */
        public boolean canCharge() {
            return mode == BatteryMode.CHARGE || mode == BatteryMode.CHARGE_DISCHARGE;
        }

        /**
         * Можно ли разряжать эту батарею (отдавать энергию).
         */
        public boolean canDischarge() {
            return mode == BatteryMode.DISCHARGE || mode == BatteryMode.CHARGE_DISCHARGE;
        }

        /**
         * Переключает режим в циклическом порядке: CHARGE → CHARGE_DISCHARGE → DISCHARGE → CHARGE
         */
        void cycleMode() {
            switch (mode) {
                case CHARGE -> mode = BatteryMode.CHARGE_DISCHARGE;
                case CHARGE_DISCHARGE -> mode = BatteryMode.DISCHARGE;
                case DISCHARGE -> mode = BatteryMode.CHARGE;
            }
        }

        /**
         * @return человекочитаемое название режима (MiniMessage compatible)
         */
        String getModeDisplay() {
            return switch (mode) {
                case CHARGE -> "<aqua>Зарядка</aqua>";
                case CHARGE_DISCHARGE -> "<light_purple>Зарядка и разрядка</light_purple>";
                case DISCHARGE -> "<red>Разрядка</red>";
            };
        }
    }

    // ════════════════════════════════════════
    // INIT
    // ════════════════════════════════════════
    public static void init() {
        instance = new BatteryManager();
        Bukkit.getPluginManager().registerEvents(instance, Main.getInstance());
        BatteryPersistence.loadAll();
        Main.getInstance().getLogger().info("[BatteryMulti] Manager initialized.");
    }

    public static BatteryManager getInstance() {
        return instance;
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
                if (world.getType(nx, ny, nz) == Material.WAXED_COPPER_GRATE) {
                    visited.add(nk);
                    queue.addLast(new int[]{nx, ny, nz});
                }
            }
        }
        return visited;
    }

    /**
     * Удаляет рамку с указанной позиции (блок, на котором рамка висит).
     * Ищет ItemFrame, прикреплённый к любой грани блока.
     */
    private static void removeFrameAt(Location blockLoc, Player player) {
        World w = blockLoc.getWorld();
        if (w == null) return;
        for (ItemFrame frame : w.getEntitiesByClass(ItemFrame.class)) {
            if (!frame.isValid() || frame.isDead()) continue;
            Location floc = frame.getLocation();
            double dx = Math.abs(floc.getX() - (blockLoc.getBlockX() + 0.5));
            double dy = Math.abs(floc.getY() - (blockLoc.getBlockY() + 0.5));
            double dz = Math.abs(floc.getZ() - (blockLoc.getBlockZ() + 0.5));
            // Рамка крепится к блоку — её центр смещён от центра блока в сторону грани
            // Допуск ±0.6 покрывает все возможные положения
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
    // ASSEMBLE
    // ════════════════════════════════════════
    public static void assemble(Location loc, Player player) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return;
        long key = toKey(loc);
        if (locationToCluster.containsKey(key)) {
            if (player != null) player.sendMessage("§eБатарея уже собрана на этом месте!");
            return;
        }

        Set<Long> connected = floodFillFast(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        if (connected.isEmpty()) {
            if (player != null) player.sendMessage("§4❌ §cНет блоков WAXED_COPPER_GRATE рядом!");
            return;
        }

        BatteryCluster cluster = new BatteryCluster();
        cluster.id = nextId++;
        cluster.world = loc.getWorld();
        cluster.blockKeys = new HashSet<>(connected);
        cluster.recalculateCenter();

        for (long bk : connected) locationToCluster.put(bk, cluster);
        clustersById.put(cluster.id, cluster);

        // Удаляем рамку
        removeFrameAt(loc, player);

        // Эффекты
        if (cluster.center != null) {
            World w = cluster.center.getWorld();
            if (w != null) {
                w.spawnParticle(Particle.END_ROD, cluster.center.clone().add(0.5, 0.5, 0.5), 30, 0.5, 0.5, 0.5, 0.1);
                w.playSound(cluster.center, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
            }
        }

        if (player != null) {
            player.sendMessage("§a✔ §fБатарея собрана!");
            player.sendMessage("§8┃ §7Блоков: §f" + cluster.blockKeys.size() + " §7| Ёмкость: §f" + cluster.capacity + " §7энергии");
            player.sendMessage("§8┃ §7Подайте редстоун на любой блок для разрядки");
        }

        Main.getInstance().getLogger().info("[BatteryMulti] Assembled cluster #" + cluster.id + " with " + connected.size() + " blocks");
    }

    // ════════════════════════════════════════
    // DISASSEMBLE
    // ════════════════════════════════════════
    public static void disassemble(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return;
        BatteryCluster cluster = locationToCluster.get(toKey(loc));
        if (cluster == null) return;

        for (long bk : cluster.blockKeys) locationToCluster.remove(bk);
        clustersById.remove(cluster.id);
        BatteryPersistence.deleteCluster(cluster.id);

        Main.getInstance().getLogger().info("[BatteryMulti] Disassembled cluster #" + cluster.id);
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
        } else {
            Iterator<BatteryCluster> it = adj.iterator();
            BatteryCluster primary = it.next();
            while (it.hasNext()) {
                BatteryCluster other = it.next();
                for (long bk : other.blockKeys) {
                    locationToCluster.put(bk, primary);
                    primary.addBlock(bk);
                }
                clustersById.remove(other.id);
            }
            primary.addBlock(key);
            locationToCluster.put(key, primary);
        }
    }

    // ════════════════════════════════════════
    // BLOCK BROKEN (hot shrink)
    // ════════════════════════════════════════
    public static void onBlockBroken(Location loc, Player player) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return;
        long key = toKey(loc);
        BatteryCluster cluster = locationToCluster.get(key);
        if (cluster == null) return;

        // Полная разборка всего кластера при разрушении любого блока
        for (long bk : cluster.blockKeys) {
            locationToCluster.remove(bk);
        }
        clustersById.remove(cluster.id);
        cluster.blockKeys.clear();
        BatteryPersistence.deleteCluster(cluster.id);

        if (cluster.center != null && cluster.center.getWorld() != null) {
            Main.getInstance().getLogger().info("[BatteryMulti] Disassembled cluster #" + cluster.id
                    + " due to block break at " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ());
        }

        if (player != null) {
            player.sendMessage("§e❕ Батарея разобрана (разрушен блок)");
        }
    }

    // ════════════════════════════════════════
    // SHIFT+ПКМ ПУСТОЙ РУКОЙ — ПЕРЕКЛЮЧЕНИЕ РЕЖИМА
    // ════════════════════════════════════════
    @EventHandler
    public void onModeSwitch(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!e.getPlayer().isSneaking()) return;
        if (e.getClickedBlock() == null || e.getClickedBlock().getType() != Material.WAXED_COPPER_GRATE) return;

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
        Main.getInstance().getLogger().info("[BatteryMulti] Cluster #" + cluster.id
                + " mode changed to " + cluster.mode + " by " + player.getName());
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

    public static Collection<BatteryCluster> getAllClusters() {
        return clustersById.values();
    }

    public static int getClusterCount() { return clustersById.size(); }

    // ════════════════════════════════════════
    // GET CHARGE PERCENTAGE (0.0 - 100.0)
    // Суммирует энергию всех CableNode блоков кластера / capacity × 100
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
    // PARTICLE TICK — спавн частиц в центре каждого кластера
    // 0% заряда = 0 частиц, 100% = 10 частиц
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

                // Анти-фантом: если первый блок кластера уже не WAXED_COPPER_GRATE — разбираем
                if (cluster.world.getType(fx, fy, fz) != Material.WAXED_COPPER_GRATE) {
                    toRemove.add(cluster.id);
                    continue;
                }

                double pct = getChargePercentage(cluster);
                int count = Math.max(1, (int) Math.round(pct / 10.0)); // 0%→1 (min), 100%→10

                Location center = cluster.center.clone().add(0.5, 0.5, 0.5);
                cluster.world.spawnParticle(Particle.END_ROD, center, count, 0.3, 0.3, 0.3, 0.01);
                cluster.world.spawnParticle(Particle.ELECTRIC_SPARK, center, count / 2, 0.3, 0.3, 0.3, 0);
            } catch (Exception e) {
                Main.getInstance().getLogger().warning("[BatteryMulti] Tick error: " + e.getMessage());
            }
        }

        // Очищаем фантомные кластеры
        for (int id : toRemove) {
            BatteryCluster cluster = clustersById.get(id);
            if (cluster != null) {
                for (long bk : cluster.blockKeys) {
                    locationToCluster.remove(bk);
                }
                clustersById.remove(id);
                BatteryPersistence.deleteCluster(id);
                Main.getInstance().getLogger().info("[BatteryMulti] Removed phantom cluster #" + id);
            }
        }
    }

    // ════════════════════════════════════════
    // SAVE / LOAD (delegated)
    // ════════════════════════════════════════
    public static void saveAll() {
        BatteryPersistence.saveAll();
    }

    // ════════════════════════════════════════
    // INTERNAL (for persistence)
    // ════════════════════════════════════════
    static Map<Long, BatteryCluster> getLocationMap() { return locationToCluster; }
    static Map<Integer, BatteryCluster> getClustersById() { return clustersById; }
    static void setNextId(int id) { nextId = id; }

    public static void clearAll() {
        locationToCluster.clear();
        clustersById.clear();
    }
}
