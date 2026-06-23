package com.mcplugin.energy.consumption.light;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.LocationUtil;
import com.mcplugin.energy.transfer.cable.CableNetwork;
import com.mcplugin.energy.transfer.cable.CableNode;

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
 * Соединяет соседние REDSTONE_LAMP через flood-fill (как магнит).
 * Каждый блок = +1 энергия/тик потребления когда включена.
 * Включается если: есть редстоун-сигнал НА ЛЮБОМ блоке структуры И есть энергия в кабелях.
 * Async-queued зажигание — обновления собираются в очередь и применяются пачками.
 * Сборка через SHIFT+ПКМ по рамке на любом REDSTONE_LAMP.
 */
public class LightManager {

    private static LightManager instance;
    private static final Map<Long, LightCluster> locationToCluster = new HashMap<>();
    private static final Map<Integer, LightCluster> clustersById = new HashMap<>();
    private static int nextId = 1;

    // ════════════════════════════════════════
    // КООРДИНАТНЫЙ КЛЮЧ
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
    // LIGHT CLUSTER
    // ════════════════════════════════════════
    public static class LightCluster {
        public int id;
        public World world;
        public Set<Long> blockKeys = new HashSet<>();
        public Location center;
        public int power; // blockKeys.size()
        public boolean lit; // текущее состояние

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

        boolean contains(Location loc) {
            return blockKeys.contains(toKey(loc));
        }

        /**
         * Есть ли редстоун-сигнал хотя бы на одном блоке кластера?
         */
        boolean isAnyBlockPowered() {
            for (long key : blockKeys) {
                Block block = world.getBlockAt(getX(key), getY(key), getZ(key));
                if (block.isBlockPowered() || block.isBlockIndirectlyPowered()) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Есть ли энергия в кабелях рядом (в радиусе 1 от любого блока)?
         */
        boolean hasNearbyCableEnergy() {
            for (long key : blockKeys) {
                int bx = getX(key), by = getY(key), bz = getZ(key);
                Location bl = new Location(world, bx, by, bz);
                for (Location n : getNeighbors(bl)) {
                    CableNode node = CableNetwork.getNode(n);
                    if (node != null && node.getEnergy() > 0) return true;
                }
            }
            return false;
        }

        private List<Location> getNeighbors(Location loc) {
            List<Location> list = new ArrayList<>(6);
            int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
            list.add(new Location(world, x+1, y, z));
            list.add(new Location(world, x-1, y, z));
            list.add(new Location(world, x, y+1, z));
            list.add(new Location(world, x, y-1, z));
            list.add(new Location(world, x, y, z+1));
            list.add(new Location(world, x, y, z-1));
            return list;
        }
    }

    // ════════════════════════════════════════
    // ASYNC LIGHTING QUEUE
    // ════════════════════════════════════════
    private static final Deque<Runnable> lightingQueue = new ArrayDeque<>();
    private static boolean queueProcessing = false;

    /**
     * Добавляет задачу в очередь асинхронного зажигания.
     * Очередь обрабатывается на следующем тике пачками.
     */
    public static void queueLightingUpdate(Runnable task) {
        synchronized (lightingQueue) {
            lightingQueue.addLast(task);
        }
    }

    /**
     * Обрабатывает очередь зажигания (вызывается из тика).
     */
    public static void processLightingQueue() {
        if (queueProcessing) return;
        queueProcessing = true;
        try {
            int batchSize = Math.min(50, lightingQueue.size());
            for (int i = 0; i < batchSize && !lightingQueue.isEmpty(); i++) {
                Runnable task;
                synchronized (lightingQueue) {
                    task = lightingQueue.pollFirst();
                }
                if (task != null) {
                    try {
                        task.run();
                    } catch (Exception e) {
                        Main.getInstance().getLogger().warning("[Light] Lighting task error: " + e.getMessage());
                    }
                }
            }
        } finally {
            queueProcessing = false;
        }
    }

    // ════════════════════════════════════════
    // INIT
    // ════════════════════════════════════════
    public static void init() {
        instance = new LightManager();
        LightPersistence.loadAll();
        Main.getInstance().getLogger().info("[LightMulti] Manager initialized.");
    }

    public static LightManager getInstance() {
        return instance;
    }

    /**
     * Удаляет рамку, прикреплённую к блоку.
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
                if (world.getType(nx, ny, nz) == Material.REDSTONE_LAMP) {
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
            if (player != null) player.sendMessage("§eЛампочка уже собрана на этом месте!");
            return;
        }

        Set<Long> connected = floodFillFast(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        if (connected.isEmpty()) {
            if (player != null) player.sendMessage("§4❌ §cНет блоков REDSTONE_LAMP рядом!");
            return;
        }

        LightCluster cluster = new LightCluster();
        cluster.id = nextId++;
        cluster.world = loc.getWorld();
        cluster.blockKeys = new HashSet<>(connected);
        cluster.recalculateCenter();
        cluster.lit = false;

        for (long bk : connected) locationToCluster.put(bk, cluster);
        clustersById.put(cluster.id, cluster);

        // Удаляем рамку
        removeFrameAt(loc, player);

        if (player != null) {
            player.sendMessage("§a✔ §fЛампочка собрана!");
            player.sendMessage("§8┃ §7Блоков: §f" + cluster.blockKeys.size() + " §7| Потребление: §f" + cluster.power + " §7энергии/тик");
            player.sendMessage("§8┃ §7Подайте редстоун + энергию для зажигания");
        }

        Main.getInstance().getLogger().info("[LightMulti] Assembled cluster #" + cluster.id + " with " + connected.size() + " lamps");
    }

    // ════════════════════════════════════════
    // DISASSEMBLE
    // ════════════════════════════════════════
    public static void disassemble(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return;
        LightCluster cluster = locationToCluster.get(toKey(loc));
        if (cluster == null) return;

        // Выключаем все лампы
        if (cluster.lit) setBlocksLit(cluster, false);
        for (long bk : cluster.blockKeys) locationToCluster.remove(bk);
        clustersById.remove(cluster.id);
        LightPersistence.deleteCluster(cluster.id);
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
            // Если кластер горел — новый блок тоже зажигаем
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
                }
                clustersById.remove(other.id);
            }
            primary.addBlock(key);
            locationToCluster.put(key, primary);
        }
    }

    public static void onBlockBroken(Location loc, Player player) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return;
        long key = toKey(loc);
        LightCluster cluster = locationToCluster.get(key);
        if (cluster == null) return;

        locationToCluster.remove(key);
        // Если кластер горел — выключаем конкретный блок
        if (cluster.lit) setBlockLit(loc, false);
        cluster.removeBlock(key);

        if (cluster.blockKeys.isEmpty()) {
            clustersById.remove(cluster.id);
            LightPersistence.deleteCluster(cluster.id);
            if (player != null) {
                player.sendMessage("§e❕ Лампочка полностью разобрана (удалено из БД)");
            }
            return;
        }

        long anyKey = cluster.blockKeys.iterator().next();
        Set<Long> newKeys = floodFillFast(cluster.world, getX(anyKey), getY(anyKey), getZ(anyKey));

        for (long oldKey : new HashSet<>(cluster.blockKeys)) {
            if (!newKeys.contains(oldKey)) locationToCluster.remove(oldKey);
        }
        cluster.blockKeys = newKeys;
        for (long nk : newKeys) locationToCluster.put(nk, cluster);
        cluster.recalculateCenter();

        if (player != null) {
            player.sendMessage("§7❕ Блок лампочки разрушен, осталось: §f" + cluster.power);
        }
    }

    // ════════════════════════════════════════
    // TICK — проверка состояния и зажигание
    // ════════════════════════════════════════
    public static void tick() {
        for (LightCluster cluster : clustersById.values()) {
            try {
                if (cluster.world == null || cluster.blockKeys.isEmpty()) continue;

                long firstKey = cluster.blockKeys.iterator().next();
                if (!cluster.world.isChunkLoaded(getX(firstKey) >> 4, getZ(firstKey) >> 4)) continue;

                boolean shouldBeLit = cluster.isAnyBlockPowered() && cluster.hasNearbyCableEnergy();
                final LightCluster cLocal = cluster;

                if (shouldBeLit != cluster.lit) {
                    // Async-queued зажигание
                    final boolean newState = shouldBeLit;
                    queueLightingUpdate(() -> {
                        cLocal.lit = newState;
                        setBlocksLit(cLocal, newState);
                    });
                }

                // Потребление энергии
                if (cluster.lit) {
                    int consumption = cluster.power; // 1 энергия/блок/тик
                    int remaining = consumption;
                    for (long key : cluster.blockKeys) {
                        if (remaining <= 0) break;
                        int bx = getX(key), by = getY(key), bz = getZ(key);
                        Location bl = new Location(cluster.world, bx, by, bz);
                        for (Location n : getNeighborLocations(cluster.world, bx, by, bz)) {
                            CableNode node = CableNetwork.getNode(n);
                            if (node != null && node.getEnergy() > 0) {
                                int take = Math.min(remaining, node.getEnergy());
                                node.removeEnergy(take);
                                remaining -= take;
                                if (remaining <= 0) break;
                            }
                        }
                        if (remaining > 0) {
                            // Нет энергии — выключаем
                            cLocal.lit = false;
                            queueLightingUpdate(() -> setBlocksLit(cLocal, false));
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                Main.getInstance().getLogger().warning("[LightMulti] Tick error: " + e.getMessage());
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
        Block block = loc.getBlock();
        setBlockLitState(block, lit);
    }

    private static void setBlockLitState(Block block, boolean lit) {
        if (block.getType() != Material.REDSTONE_LAMP) return;
        try {
            BlockData data = block.getBlockData();
            if (data instanceof Lightable lightable) {
                if (lightable.isLit() != lit) {
                    lightable.setLit(lit);
                    block.setBlockData(lightable, false);
                }
            }
        } catch (Exception ignored) {}
    }

    private static List<Location> getNeighborLocations(World world, int x, int y, int z) {
        List<Location> list = new ArrayList<>(6);
        list.add(new Location(world, x+1, y, z));
        list.add(new Location(world, x-1, y, z));
        list.add(new Location(world, x, y+1, z));
        list.add(new Location(world, x, y-1, z));
        list.add(new Location(world, x, y, z+1));
        list.add(new Location(world, x, y, z-1));
        return list;
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

    public static Collection<LightCluster> getAllClusters() {
        return clustersById.values();
    }

    public static int getClusterCount() { return clustersById.size(); }

    // ════════════════════════════════════════
    // PERSISTENCE
    // ════════════════════════════════════════
    public static void saveAll() {
        LightPersistence.saveAll();
    }

    static Map<Long, LightCluster> getLocationMap() { return locationToCluster; }
    static Map<Integer, LightCluster> getClustersById() { return clustersById; }
    static void setNextId(int id) { nextId = id; }

    public static void clearAll() {
        locationToCluster.clear();
        clustersById.clear();
        lightingQueue.clear();
    }
}
