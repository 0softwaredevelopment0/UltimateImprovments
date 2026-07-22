package com.ultimateimprovements.module.meteor;

import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.module.PluginModule;
import com.ultimateimprovements.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * MeteorModule — управляет метеоритным дождём.
 * <p>
 * По таймеру выбирает случайного игрока в настроенном мире, находит поверхность
 * рядом с ним и запускает {@link MeteorTask} — вертикальное падение метеора.
 */
public class MeteorModule extends PluginModule {

    private static MeteorModule instance;

    private BukkitTask scheduler;
    private final List<MeteorTask> activeMeteors = new ArrayList<>();
    private Random random;

    public MeteorModule() {
        super("Meteor", "modules/meteor", false);
    }

    public static MeteorModule getInstance() {
        return instance;
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        instance = this;
        random = new Random();

        rescheduleNext(plugin);

        ConsoleLogger.info("[Meteor] Module initialized.");
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        if (scheduler != null) { scheduler.cancel(); scheduler = null; }

        for (MeteorTask task : activeMeteors) {
            try {
                task.cancel();
            } catch (Exception ignored) {}
        }
        activeMeteors.clear();

        ConsoleLogger.info("[Meteor] Module disabled.");
    }

    // ========================================================================
    // SCHEDULING
    // ========================================================================

    private void rescheduleNext(JavaPlugin plugin) {
        if (scheduler != null) {
            scheduler.cancel();
        }

        var cfg = Main.getInstance().getConfig();
        if (!cfg.getBoolean("meteor.enabled", false)) return;

        int intervalMin = cfg.getInt("meteor.interval.min", 30);
        int intervalMax = cfg.getInt("meteor.interval.max", 90);
        if (intervalMin <= 0) intervalMin = 1;
        if (intervalMax < intervalMin) intervalMax = intervalMin;

        int minutes = intervalMin + random.nextInt(intervalMax - intervalMin + 1);
        long ticks = minutes * 60L * 20L;

        scheduler = new BukkitRunnable() {
            @Override
            public void run() {
                spawnRandomMeteor();
                rescheduleNext(plugin);
            }
        }.runTaskLater(plugin, ticks);

        ConsoleLogger.info("[Meteor] Next meteor in " + minutes + " minute(s) (" + ticks + " ticks).");
    }

    // ========================================================================
    // SPAWN
    // ========================================================================

    public void spawnRandomMeteor() {
        var cfg = Main.getInstance().getConfig();
        if (!cfg.getBoolean("meteor.enabled", false)) return;

        String worldName = cfg.getString("meteor.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            ConsoleLogger.warn("[Meteor] World '" + worldName + "' not found!");
            return;
        }

        List<Player> players = world.getPlayers();
        if (players.isEmpty()) {
            ConsoleLogger.info("[Meteor] No players online in '" + worldName + "', skipping.");
            return;
        }
        Player target = players.get(random.nextInt(players.size()));

        Location playerLoc = target.getLocation();
        int spawnMeteorsCount = cfg.getInt("meteor.count_per_spawn", 1);
        for (int i = 0; i < spawnMeteorsCount; i++) {
            spawnMeteorNear(world, playerLoc.getBlockX(), playerLoc.getBlockZ());
        }
    }

    public void spawnMeteors(int count) {
        var cfg = Main.getInstance().getConfig();
        if (!cfg.getBoolean("meteor.enabled", false)) {
            ConsoleLogger.warn("[Meteor] Module is disabled in config!");
            return;
        }

        String worldName = cfg.getString("meteor.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            ConsoleLogger.warn("[Meteor] World '" + worldName + "' not found!");
            return;
        }

        List<Player> players = world.getPlayers();
        if (players.isEmpty()) {
            ConsoleLogger.warn("[Meteor] No players online in '" + worldName + "' for force spawn.");
            return;
        }

        for (int i = 0; i < count; i++) {
            Player p = players.get(random.nextInt(players.size()));
            Location loc = p.getLocation();
            spawnMeteorNear(world, loc.getBlockX(), loc.getBlockZ());
        }

        ConsoleLogger.info("[Meteor] Force-spawned " + count + " meteor(s).");
    }

    private void spawnMeteorNear(World world, int centerX, int centerZ) {
        var cfg = Main.getInstance().getConfig();

        int spawnRadiusMin = cfg.getInt("meteor.spawn_radius.min", 30);
        int spawnRadiusMax = cfg.getInt("meteor.spawn_radius.max", 120);

        int dx = spawnRadiusMin + random.nextInt(spawnRadiusMax - spawnRadiusMin + 1);
        int dz = spawnRadiusMin + random.nextInt(spawnRadiusMax - spawnRadiusMin + 1);
        if (random.nextBoolean()) dx = -dx;
        if (random.nextBoolean()) dz = -dz;
        int targetX = centerX + dx;
        int targetZ = centerZ + dz;

        int heightMax = cfg.getInt("meteor.height.max", world.getMaxHeight() - 1);
        int heightMin = cfg.getInt("meteor.height.min", world.getMinHeight());

        int targetY = findSurfaceY(world, targetX, targetZ, heightMax, heightMin);
        if (targetY < 0) {
            for (int attempt = 0; attempt < 5; attempt++) {
                int retryX = centerX + random.nextInt(200) - 100;
                int retryZ = centerZ + random.nextInt(200) - 100;
                targetY = findSurfaceY(world, retryX, retryZ, heightMax, heightMin);
                if (targetY >= 0) {
                    targetX = retryX;
                    targetZ = retryZ;
                    break;
                }
            }
            if (targetY < 0) return;
        }

        // Параметры
        int fallDuration = cfg.getInt("meteor.fall_duration_ticks", 60);
        int sphereRadius = cfg.getInt("meteor.sphere_radius", 2);
        if (sphereRadius < 1) sphereRadius = 1;
        if (sphereRadius > 5) sphereRadius = 5;

        // Блоки оболочки
        List<String> blockNames = cfg.getStringList("meteor.display_blocks");
        if (blockNames.isEmpty()) {
            blockNames = List.of("CRACKED_DEEPSLATE_BRICKS", "CRACKED_DEEPSLATE_TILES", "CHISELED_DEEPSLATE", "MAGMA_BLOCK");
        }
        List<BlockData> blockDataList = new ArrayList<>();
        for (String name : blockNames) {
            try {
                Material mat = Material.valueOf(name.toUpperCase());
                blockDataList.add(mat.createBlockData());
            } catch (IllegalArgumentException e) {
                ConsoleLogger.warn("[Meteor] Invalid display block: " + name);
            }
        }
        if (blockDataList.isEmpty()) {
            blockDataList.add(Material.CRACKED_DEEPSLATE_BRICKS.createBlockData());
        }

        // Центральные руды с шансами
        Map<Material, Double> coreOres = loadCoreOres(cfg);

        // Стартовая высота — вертикально сверху
        int fallHeight = 80 + random.nextInt(40); // 80-120 блоков вверх от поверхности
        double startY = targetY + fallHeight;

        // Запускаем анимацию
        MeteorTask task = new MeteorTask(
                world,
                startY,
                targetX + 0.5, targetY + 1.0, targetZ + 0.5,
                fallDuration,
                sphereRadius,
                blockDataList,
                coreOres
        );
        task.setOnComplete(() -> activeMeteors.remove(task));
        activeMeteors.add(task);
        task.runTaskTimer(Main.getInstance(), 1L, 1L);

        ConsoleLogger.info("[Meteor] Spawned meteor → (" + targetX + ", " + targetY + ", " + targetZ + ") radius=" + sphereRadius);
    }

    /**
     * Загружает список руд с шансами из конфига.
     * Дефолт: все глубинные руды с равными шансами (1.0).
     */
    private Map<Material, Double> loadCoreOres(org.bukkit.configuration.file.FileConfiguration cfg) {
        Map<Material, Double> ores = new HashMap<>();

        if (cfg.contains("meteor.core_ores") && cfg.isConfigurationSection("meteor.core_ores")) {
            var section = cfg.getConfigurationSection("meteor.core_ores");
            for (String key : section.getKeys(false)) {
                try {
                    Material mat = Material.valueOf(key.toUpperCase());
                    double weight = section.getDouble(key, 1.0);
                    if (weight > 0) {
                        ores.put(mat, weight);
                    }
                } catch (IllegalArgumentException e) {
                    ConsoleLogger.warn("[Meteor] Invalid core ore: " + key);
                }
            }
        }

        // Дефолт: все глубинные руды
        if (ores.isEmpty()) {
            ores.put(Material.DEEPSLATE_DIAMOND_ORE, 1.0);
            ores.put(Material.DEEPSLATE_IRON_ORE, 1.0);
            ores.put(Material.DEEPSLATE_GOLD_ORE, 1.0);
            ores.put(Material.DEEPSLATE_EMERALD_ORE, 1.0);
            ores.put(Material.DEEPSLATE_LAPIS_ORE, 1.0);
            ores.put(Material.DEEPSLATE_REDSTONE_ORE, 1.0);
            ores.put(Material.DEEPSLATE_COAL_ORE, 1.0);
            ores.put(Material.DEEPSLATE_COPPER_ORE, 1.0);
        }

        return ores;
    }

    private int findSurfaceY(World world, int x, int z, int maxY, int minY) {
        int top = Math.min(maxY, world.getMaxHeight() - 1);
        int bottom = Math.max(minY, world.getMinHeight());

        for (int y = top; y >= bottom; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (block.isEmpty() || block.isLiquid()) continue;
            if (block.getType().isSolid()) {
                Block above = world.getBlockAt(x, y + 1, z);
                if (above.isEmpty() || !above.getType().isSolid()) {
                    return y;
                }
            }
        }
        return -1;
    }

    public void removeMeteor(MeteorTask task) {
        activeMeteors.remove(task);
    }
}
