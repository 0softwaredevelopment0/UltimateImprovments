package com.mcplugin.features.magnet;

import com.mcplugin.Main;
import com.mcplugin.database.DatabaseManager;
import com.mcplugin.util.LocationUtil;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

/**
 * Плавное притяжение металлических предметов, игроков и мобов
 * к активным магнитам (группа соединённых LODESTONE).
 *
 * Сила притяжения = количество LODESTONE-блоков в связной группе.
 * Центр притяжения = геометрический центр (центроид) фигуры.
 * Динамически перестраивается при изменении структуры.
 */
public class MagnetManager extends BukkitRunnable {

    private static MagnetManager instance;

    // =========================
    // ⚙ КОНФИГУРАЦИЯ (читается из config.yml / features.magnet)
    // =========================
    private static boolean enabled = true;

    // Радиус
    private static int minRadius = 3;
    private static int maxRadius = 15;

    // Мощность (без максимума — sqrt-масштабирование)
    private static int intervalTicks = 1;

    // Сила
    private static double forceBase = 0.15;
    private static double forceDistanceMultiplier = 0.35;
    private static double forceMax = 0.45;
    private static double forceDistanceFloor = 0.05;
    private static double forceMaxSpeed = 0.6;
    private static double itemYBoost = 0.05;

    // =========================
    // 🧲 MAGNET CLUSTER
    // =========================
    static class MagnetCluster {
        int id;
        World world;
        Set<Location> blocks = new HashSet<>();
        Location center; // центроид (округлённый)
        int power; // = blocks.size()

        void recalculateCenter() {
            if (blocks.isEmpty()) return;
            double avgX = 0, avgY = 0, avgZ = 0;
            for (Location loc : blocks) {
                avgX += loc.getBlockX();
                avgY += loc.getBlockY();
                avgZ += loc.getBlockZ();
            }
            int size = blocks.size();
            center = new Location(world,
                    (int) Math.round(avgX / size),
                    (int) Math.round(avgY / size),
                    (int) Math.round(avgZ / size));
            power = size;
        }

        boolean contains(Location loc) {
            return blocks.contains(LocationUtil.normalize(loc));
        }
    }

    // =========================
    // DATA
    // =========================
    // Быстрый поиск: блок → его кластер
    private static final Map<Location, MagnetCluster> locationToCluster = new HashMap<>();
    // Кластеры по ID
    private static final Map<Integer, MagnetCluster> clustersById = new HashMap<>();
    // Счётчик для ID
    private static int nextId = 1;

    // =========================
    // INIT
    // =========================
    public static void init(Main plugin) {
        instance = new MagnetManager();
        reloadConfig();
        loadAll();
        instance.runTaskTimer(plugin, 20L, intervalTicks);
    }

    public static void reloadConfig() {
        var cfg = Main.getInstance().getConfig().getConfigurationSection("features.magnet");
        if (cfg == null) return;
        enabled = cfg.getBoolean("enabled", true);

        // Радиус
        var radiusCfg = cfg.getConfigurationSection("radius");
        if (radiusCfg != null) {
            minRadius = radiusCfg.getInt("min", 3);
            maxRadius = radiusCfg.getInt("max", 15);
        }

        // Мощность (без max_power — бесконечный рост с sqrt-масштабированием)
        intervalTicks = cfg.getInt("interval_ticks", 1);

        // Сила
        var forceCfg = cfg.getConfigurationSection("force");
        if (forceCfg != null) {
            forceBase = forceCfg.getDouble("base", 0.15);
            forceDistanceMultiplier = forceCfg.getDouble("distance_multiplier", 0.35);
            forceMax = forceCfg.getDouble("max", 0.45);
            forceDistanceFloor = forceCfg.getDouble("distance_floor", 0.05);
            forceMaxSpeed = forceCfg.getDouble("max_speed", 0.6);
        }

        // Подъём предметов
        itemYBoost = cfg.getDouble("item_y_boost", 0.05);
    }

    // =========================
    // ACTIVATE (сборка магнита)
    // =========================
    public static void activate(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return;
        if (locationToCluster.containsKey(loc)) {
            return; // уже часть активного магнита
        }

        // Flood-fill: все соединённые LODESTONE
        Set<Location> connected = floodFill(loc);
        if (connected.isEmpty()) return;

        // Создаём кластер
        MagnetCluster cluster = new MagnetCluster();
        cluster.id = nextId++;
        cluster.world = loc.getWorld();
        cluster.blocks = new HashSet<>(connected);
        cluster.recalculateCenter();

        // Регистрируем
        for (Location blockLoc : connected) {
            locationToCluster.put(blockLoc, cluster);
        }
        clustersById.put(cluster.id, cluster);

        // DB
        saveCluster(cluster);

        // Эффекты
        addParticleEffect(cluster.center);

        Main.getInstance().getLogger().info(
                "[Magnet] Activated cluster #" + cluster.id
                        + " with " + connected.size() + " blocks"
                        + " at center " + cluster.center
        );
    }

    // =========================
    // DEACTIVATE (полное отключение кластера)
    // =========================
    public static void deactivate(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return;

        MagnetCluster cluster = locationToCluster.get(loc);
        if (cluster == null) return;

        deactivateCluster(cluster);
    }

    private static void deactivateCluster(MagnetCluster cluster) {
        // Удаляем из lookup
        for (Location blockLoc : cluster.blocks) {
            locationToCluster.remove(blockLoc);
        }
        clustersById.remove(cluster.id);

        // Удаляем из БД
        deleteCluster(cluster.id);

        if (cluster.center != null && cluster.center.getWorld() != null) {
            addParticleEffect(cluster.center);
        }

        Main.getInstance().getLogger().info(
                "[Magnet] Deactivated cluster #" + cluster.id
                        + " (" + cluster.power + " blocks)"
        );
    }

    // =========================
    // БЛОК РАЗРУШЕН (динамический пересчёт)
    // =========================
    public static boolean onBlockBroken(Location loc, Player breaker) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return false;

        MagnetCluster cluster = locationToCluster.get(loc);
        if (cluster == null) return false;

        // Удаляем этот блок из lookup
        locationToCluster.remove(loc);
        cluster.blocks.remove(loc);

        if (cluster.blocks.isEmpty()) {
            // Последний блок — полностью отключаем
            deactivateCluster(cluster);
            if (breaker != null) {
                breaker.sendMessage("§4⚠ §cМагнит полностью разрушен и деактивирован!");
            }
            return true;
        }

        // Пересчитываем связную компоненту (flood-fill от любого оставшегося блока)
        Location anyBlock = cluster.blocks.iterator().next();
        Set<Location> newBlocks = floodFill(anyBlock);

        // Удаляем из lookup блоки, которые больше не в связной компоненте
        Set<Location> oldBlocks = new HashSet<>(cluster.blocks);
        for (Location oldLoc : oldBlocks) {
            if (!newBlocks.contains(oldLoc)) {
                locationToCluster.remove(oldLoc);
            }
        }

        // Обновляем кластер
        cluster.blocks = newBlocks;
        for (Location newLoc : newBlocks) {
            locationToCluster.put(newLoc, cluster);
        }
        cluster.recalculateCenter();

        // Сохраняем
        saveCluster(cluster);

        if (breaker != null) {
            breaker.sendMessage("§e⚡ §7Магнит перестроен! Блоков: §f" + cluster.blocks.size()
                    + " §7| Центр смещён");
        }

        Main.getInstance().getLogger().info(
                "[Magnet] Cluster #" + cluster.id + " recalculated: "
                        + cluster.blocks.size() + " blocks, center at " + cluster.center
        );
        return false;
    }

    // =========================
    // БЛОК ПОСТАВЛЕН (динамическое расширение)
    // =========================
    public static void onBlockPlaced(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return;
        if (locationToCluster.containsKey(loc)) return; // уже отслеживается

        // Проверяем: рядом с каким кластером появился блок?
        MagnetCluster found = null;
        for (Location neighbor : LocationUtil.getNeighbors(loc)) {
            MagnetCluster c = locationToCluster.get(neighbor);
            if (c != null) {
                found = c;
                break;
            }
        }

        if (found == null) return; // не рядом с магнитом — не наш

        // Полный пересчёт flood-fill от любого старого блока
        Location anyBlock = found.blocks.iterator().next();
        Set<Location> newBlocks = floodFill(anyBlock);

        // Снимаем старые регистрации
        for (Location oldLoc : found.blocks) {
            locationToCluster.remove(oldLoc);
        }

        // Обновляем
        found.blocks = newBlocks;
        for (Location newLoc : newBlocks) {
            locationToCluster.put(newLoc, found);
        }
        found.recalculateCenter();

        saveCluster(found);

        Main.getInstance().getLogger().info(
                "[Magnet] Cluster #" + found.id + " expanded: "
                        + found.blocks.size() + " blocks, center at " + found.center
        );
    }

    // =========================
    // FLOOD-FILL (BFS)
    // =========================
    private static Set<Location> floodFill(Location start) {
        Set<Location> visited = new HashSet<>();
        Queue<Location> queue = new LinkedList<>();
        Location startNorm = LocationUtil.normalize(start);
        if (startNorm == null) return visited;

        visited.add(startNorm);
        queue.add(startNorm);

        while (!queue.isEmpty()) {
            Location current = queue.poll();
            for (Location neighbor : LocationUtil.getNeighbors(current)) {
                if (visited.contains(neighbor)) continue;
                if (neighbor.getBlock().getType() == Material.LODESTONE) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        return visited;
    }

    // =========================
    // QUERIES
    // =========================
    public static boolean isActive(Location loc) {
        loc = LocationUtil.normalize(loc);
        return loc != null && locationToCluster.containsKey(loc);
    }

    public static boolean isActiveAt(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return false;
        // Прямое попадание
        if (locationToCluster.containsKey(loc)) return true;
        // Рядом с любым блоком магнита
        for (Location neighbor : LocationUtil.getNeighbors(loc)) {
            if (locationToCluster.containsKey(neighbor)) return true;
        }
        return false;
    }

    public static int getMagnetPower(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return 1;
        MagnetCluster cluster = locationToCluster.get(loc);
        return cluster != null ? cluster.blocks.size() : 1;
    }

    public static Location getMagnetCenter(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return null;
        MagnetCluster cluster = locationToCluster.get(loc);
        return cluster != null ? cluster.center.clone() : null;
    }

    // =========================
    // ДИНАМИЧЕСКИЙ РАДИУС (без максимума — sqrt-масштабирование)
    // От minRadius (3) при 1 блоке, растёт с sqrt(power) без ограничения
    // =========================
    public static int getMagnetRadius(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return minRadius;
        MagnetCluster cluster = locationToCluster.get(loc);
        return cluster != null ? getClusterRadius(cluster.blocks.size()) : minRadius;
    }

    private static int getClusterRadius(int power) {
        // sqrt-масштабирование: при 20 блоках даёт maxRadius (15)
        double t = Math.sqrt((double) power / 20.0);
        return (int) Math.round(minRadius + (maxRadius - minRadius) * t);
    }

    public static int getMaxRadius() {
        return maxRadius;
    }

    public static int getMinRadius() {
        return minRadius;
    }

    // max_power удалён — сила и радиус растут по sqrt без ограничения

    public static double getForceBase() {
        return forceBase;
    }

    public static double getForceDistanceMultiplier() {
        return forceDistanceMultiplier;
    }

    public static double getForceMax() {
        return forceMax;
    }

    public static double getForceDistanceFloor() {
        return forceDistanceFloor;
    }

    public static double getForceMaxSpeed() {
        return forceMaxSpeed;
    }

    public static double getItemYBoost() {
        return itemYBoost;
    }

    public static int getClusterCount() {
        return clustersById.size();
    }

    public static Collection<MagnetCluster> getClusters() {
        return clustersById.values();
    }

    // =========================
    // ПАРТИКЛЫ ПРИ АКТИВАЦИИ
    // =========================
    public static void addParticleEffect(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        World world = loc.getWorld();
        world.spawnParticle(Particle.END_ROD, loc.clone().add(0.5, 0.5, 0.5), 30, 0.5, 0.5, 0.5, 0.1);
        world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
    }

    // =========================
    // RUN (каждый тик)
    // =========================
    @Override
    public void run() {
        if (!enabled || clustersById.isEmpty()) return;

        List<Integer> toRemove = new ArrayList<>();

        for (MagnetCluster cluster : clustersById.values()) {
            World world = cluster.world;
            if (world == null) {
                toRemove.add(cluster.id);
                continue;
            }

            // Быстрая проверка: хотя бы один блок кластера всё ещё существует
            if (cluster.blocks.isEmpty()
                    || cluster.blocks.iterator().next().getBlock().getType() != Material.LODESTONE) {
                toRemove.add(cluster.id);
                continue;
            }

            Location center = cluster.center.clone().add(0.5, 0.5, 0.5);

            // Партиклы — интенсивность зависит от размера
            int particleCount = Math.min(cluster.blocks.size(), 8);
            world.spawnParticle(Particle.END_ROD, center, particleCount, 0.3, 0.3, 0.3, 0);
            world.spawnParticle(Particle.ELECTRIC_SPARK, center,
                    Math.max(1, particleCount / 2), 0.3, 0.3, 0.3, 0);

            // У больших магнитов — партиклы на каждом блоке
            if (cluster.blocks.size() >= 5) {
                int count = 0;
                for (Location blockLoc : cluster.blocks) {
                    if (count++ > 10) break; // не спамим для >10 блоков
                    Location bp = blockLoc.clone().add(0.5, 0.5, 0.5);
                    world.spawnParticle(Particle.ELECTRIC_SPARK, bp, 1, 0.2, 0.2, 0.2, 0);
                }
            }

            int power = cluster.blocks.size();
            int clusterRadius = getClusterRadius(power);

            // Собираем сущности в динамическом радиусе от ЦЕНТРА фигуры
            Collection<Entity> nearby = world.getNearbyEntities(center, clusterRadius, clusterRadius, clusterRadius);

            for (Entity entity : nearby) {
                if (entity.equals(instance)) continue;
                if (!shouldAttract(entity)) continue;
                applyMagneticForce(entity, center, power, clusterRadius);
            }
        }

        // Удаляем невалидные
        for (int id : toRemove) {
            MagnetCluster cluster = clustersById.get(id);
            if (cluster != null) deactivateCluster(cluster);
        }
    }

    // =========================
    // SHOULD ATTRACT
    // =========================
    private boolean shouldAttract(Entity entity) {
        if (entity == null || entity.isDead()) return false;

        if (entity instanceof Item item) {
            return isMetallic(item.getItemStack());
        }

        if (entity instanceof Player player) {
            return hasMetallicItem(player);
        }

        if (entity instanceof Mob mob) {
            return hasMetallicEquipment(mob);
        }

        return false;
    }

    private boolean hasMetallicItem(Player player) {
        if (isMetallic(player.getInventory().getItemInMainHand())) return true;
        if (isMetallic(player.getInventory().getItemInOffHand())) return true;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (isMetallic(armor)) return true;
        }
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (isMetallic(item)) return true;
        }
        return false;
    }

    private boolean hasMetallicEquipment(Mob mob) {
        EntityEquipment eq = mob.getEquipment();
        if (eq == null) return false;
        if (isMetallic(eq.getItemInMainHand())) return true;
        if (isMetallic(eq.getItemInOffHand())) return true;
        if (isMetallic(eq.getHelmet())) return true;
        if (isMetallic(eq.getChestplate())) return true;
        if (isMetallic(eq.getLeggings())) return true;
        if (isMetallic(eq.getBoots())) return true;
        return false;
    }

    private boolean isMetallic(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;

        Material mat = item.getType();
        String name = mat.name();

        if (name.contains("IRON")) return true;
        if (name.startsWith("GOLD_") || name.equals("GOLDEN_SWORD") || name.equals("GOLDEN_SHOVEL")
                || name.equals("GOLDEN_PICKAXE") || name.equals("GOLDEN_AXE") || name.equals("GOLDEN_HOE")
                || name.equals("GOLDEN_HELMET") || name.equals("GOLDEN_CHESTPLATE")
                || name.equals("GOLDEN_LEGGINGS") || name.equals("GOLDEN_BOOTS")
                || name.equals("GOLDEN_HORSE_ARMOR") || name.equals("GOLD_BLOCK")
                || name.equals("GOLD_INGOT") || name.equals("GOLD_NUGGET")
                || name.equals("RAW_GOLD") || name.equals("RAW_GOLD_BLOCK")) return true;
        if (name.contains("NETHERITE")) return true;
        if (name.contains("COPPER")) return true;
        if (name.contains("CHAINMAIL")) return true;
        if (mat == Material.BUCKET || mat == Material.WATER_BUCKET || mat == Material.LAVA_BUCKET
                || mat == Material.MILK_BUCKET || mat == Material.COD_BUCKET
                || mat == Material.SALMON_BUCKET || mat == Material.PUFFERFISH_BUCKET
                || mat == Material.TROPICAL_FISH_BUCKET || mat == Material.AXOLOTL_BUCKET
                || mat == Material.TADPOLE_BUCKET) return true;
        if (mat == Material.SHEARS) return true;
        if (mat == Material.COMPASS) return true;
        if (mat == Material.RECOVERY_COMPASS) return true;
        if (name.contains("MINECART")) return true;
        if (name.contains("ANVIL")) return true;
        if (mat == Material.CAULDRON) return true;
        if (mat == Material.HOPPER) return true;
        if (mat == Material.RAIL || mat == Material.POWERED_RAIL || mat == Material.DETECTOR_RAIL
                || mat == Material.ACTIVATOR_RAIL) return true;
        if (mat == Material.PISTON || mat == Material.STICKY_PISTON) return true;
        if (mat == Material.STONECUTTER) return true;
        if (mat == Material.GRINDSTONE) return true;
        if (mat == Material.LANTERN || mat == Material.SOUL_LANTERN) return true;
        if (mat == Material.NAUTILUS_SHELL) return true;
        if (mat == Material.HEAVY_CORE) return true;

        return false;
    }

    // =========================
    // ПРИМЕНЕНИЕ СИЛЫ
    // power = количество блоков в кластере (1..∞)
    // clusterRadius = динамический радиус (3..15)
    // Масштабирование: power / 20.0, макс 1.0
    //
    // Для игроков и мобов: сила добавляется к текущей скорости (add),
    // чтобы игрок мог противодействовать движением WASD.
    // Для предметов: скорость задаётся напрямую (set).
    // =========================
    private void applyMagneticForce(Entity entity, Location magnetCenter, int power, int clusterRadius) {
        Location entityLoc = entity.getLocation();

        Vector direction = magnetCenter.toVector().subtract(entityLoc.toVector());

        double distance = direction.length();
        if (distance < 0.5) return;

        direction.normalize();

        // Множитель: sqrt-масштабирование — без жёсткого потолка
        // 1 блок: ~0.22 | 20 блоков: 1.0 | 100 блоков: ~2.24 | и т.д.
        double powerMultiplier = Math.sqrt((double) power / 20.0);

        double baseForce = forceBase * powerMultiplier;
        double distanceFactor = Math.max(forceDistanceFloor, 1.0 - (distance / clusterRadius));
        double force = baseForce + (distanceFactor * forceDistanceMultiplier * powerMultiplier);

        force = Math.min(force, forceMax * powerMultiplier);

        Vector forceVector = direction.multiply(force);

        if (entity instanceof Item item) {
            // Предметы: прямой setVelocity с подъёмом по Y
            forceVector.setY(forceVector.getY() + itemYBoost * powerMultiplier);

            double maxSpeed = forceMaxSpeed * powerMultiplier;
            if (forceVector.length() > maxSpeed) {
                forceVector.normalize().multiply(maxSpeed);
            }

            entity.setVelocity(forceVector);
        } else {
            // Игроки и мобы: добавляем силу к текущей скорости,
            // чтобы они могли противодействовать движением
            Vector newVel = entity.getVelocity().add(forceVector);

            double maxSpeed = forceMaxSpeed * powerMultiplier;
            if (newVel.length() > maxSpeed) {
                newVel.normalize().multiply(maxSpeed);
            }

            entity.setVelocity(newVel);
        }
    }

    // =========================
    // 💾 БАЗА ДАННЫХ
    // =========================

    public static void saveAll() {
        for (MagnetCluster cluster : clustersById.values()) {
            saveCluster(cluster);
        }
        Main.getInstance().getLogger().info("[Magnet] Saved " + clustersById.size() + " clusters to DB");
    }

    private static void saveCluster(MagnetCluster cluster) {
        Connection con = DatabaseManager.getConnection();
        try {
            PreparedStatement ps = con.prepareStatement("""
                INSERT OR REPLACE INTO magnets
                (id, world, center_x, center_y, center_z, block_count, active)
                VALUES (?, ?, ?, ?, ?, ?, 1)
            """);
            ps.setInt(1, cluster.id);
            ps.setString(2, cluster.world.getName());
            ps.setInt(3, cluster.center.getBlockX());
            ps.setInt(4, cluster.center.getBlockY());
            ps.setInt(5, cluster.center.getBlockZ());
            ps.setInt(6, cluster.blocks.size());
            ps.executeUpdate();
            ps.close();

            // Удаляем старые блоки кластера
            PreparedStatement del = con.prepareStatement(
                "DELETE FROM magnet_blocks WHERE magnet_id = ?"
            );
            del.setInt(1, cluster.id);
            del.executeUpdate();
            del.close();

            // Вставляем текущие
            PreparedStatement ins = con.prepareStatement("""
                INSERT INTO magnet_blocks (magnet_id, x, y, z)
                VALUES (?, ?, ?, ?)
            """);
            for (Location blockLoc : cluster.blocks) {
                ins.setInt(1, cluster.id);
                ins.setInt(2, blockLoc.getBlockX());
                ins.setInt(3, blockLoc.getBlockY());
                ins.setInt(4, blockLoc.getBlockZ());
                ins.addBatch();
            }
            ins.executeBatch();
            ins.close();

        } catch (Exception e) {
            Main.getInstance().getLogger().severe("[Magnet] DB save error: " + e.getMessage());
        }
    }

    private static void deleteCluster(int id) {
        Connection con = DatabaseManager.getConnection();
        try {
            PreparedStatement delBlocks = con.prepareStatement(
                "DELETE FROM magnet_blocks WHERE magnet_id = ?"
            );
            delBlocks.setInt(1, id);
            delBlocks.executeUpdate();
            delBlocks.close();

            PreparedStatement delMagnet = con.prepareStatement(
                "DELETE FROM magnets WHERE id = ?"
            );
            delMagnet.setInt(1, id);
            delMagnet.executeUpdate();
            delMagnet.close();

        } catch (Exception e) {
            Main.getInstance().getLogger().severe("[Magnet] DB delete error: " + e.getMessage());
        }
    }

    private static void loadAll() {
        locationToCluster.clear();
        clustersById.clear();

        Connection con = DatabaseManager.getConnection();
        try {
            PreparedStatement ps = con.prepareStatement(
                "SELECT * FROM magnets WHERE active = 1"
            );
            ResultSet rs = ps.executeQuery();

            int maxId = 0;

            while (rs.next()) {
                int id = rs.getInt("id");
                String worldName = rs.getString("world");
                World world = Main.getInstance().getServer().getWorld(worldName);
                if (world == null) continue;

                MagnetCluster cluster = new MagnetCluster();
                cluster.id = id;
                cluster.world = world;
                cluster.center = new Location(
                        world,
                        rs.getInt("center_x"),
                        rs.getInt("center_y"),
                        rs.getInt("center_z")
                );

                // Загружаем блоки
                PreparedStatement psb = con.prepareStatement(
                    "SELECT x, y, z FROM magnet_blocks WHERE magnet_id = ?"
                );
                psb.setInt(1, id);
                ResultSet rsb = psb.executeQuery();

                while (rsb.next()) {
                    Location blockLoc = new Location(
                            world,
                            rsb.getInt("x"),
                            rsb.getInt("y"),
                            rsb.getInt("z")
                    );
                    cluster.blocks.add(blockLoc);
                    locationToCluster.put(blockLoc, cluster);
                }
                rsb.close();
                psb.close();

                cluster.power = cluster.blocks.size();
                clustersById.put(id, cluster);

                if (id > maxId) maxId = id;
            }

            rs.close();
            ps.close();

            nextId = maxId + 1;

            Main.getInstance().getLogger().info(
                    "[Magnet] Loaded " + clustersById.size()
                            + " magnet clusters (" + locationToCluster.size() + " blocks) from DB"
            );

        } catch (Exception e) {
            Main.getInstance().getLogger().severe("[Magnet] DB load error: " + e.getMessage());
        }
    }
}
