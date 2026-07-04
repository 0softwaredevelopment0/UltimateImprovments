package com.mcplugin.infrastructure.modules.meteor;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.modules.PluginModule;
import com.mcplugin.infrastructure.util.ConsoleLogger;
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
import java.util.List;
import java.util.Random;

/**
 * MeteorModule — управляет метеоритным дождём.
 * <p>
 * По таймеру (интервал X-X минут, по умолч. 1 час) выбирает случайного
 * игрока в настроенном мире, находит верхний блок поверхности рядом с ним
 * и запускает {@link MeteorTask} — анимацию падения метеора под углом.
 * <p>
 * Команда {@code /mp meteor spawn <count>} форсирует спавн метеоров.
 */
public class MeteorModule extends PluginModule {

    private static MeteorModule instance;

    private BukkitTask scheduler;    // таймер, проверяющий интервал
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

        // Планируем первый метеор через случайное время в пределах интервала
        rescheduleNext(plugin);

        ConsoleLogger.info("[Meteor] Module initialized.");
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        // Останавливаем таймер
        if (scheduler != null) { scheduler.cancel(); scheduler = null; }

        // Очищаем все активные метеоры
        for (MeteorTask task : activeMeteors) {
            try {
                task.cancel();
                task.cleanRemove();
            } catch (Exception ignored) {}
        }
        activeMeteors.clear();

        ConsoleLogger.info("[Meteor] Module disabled.");
    }

    // ========================================================================
    // SCHEDULING
    // ========================================================================

    /**
     * Планирует следующий спавн метеора через случайный интервал (min..max минут).
     */
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
    // METEO Спавн
    // ========================================================================

    /**
     * Спавнит один случайный метеор: выбирает игрока, находит точку,
     * запускает анимацию.
     */
    public void spawnRandomMeteor() {
        var cfg = Main.getInstance().getConfig();
        if (!cfg.getBoolean("meteor.enabled", false)) return;

        String worldName = cfg.getString("meteor.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            ConsoleLogger.warn("[Meteor] World '" + worldName + "' not found!");
            return;
        }

        // Выбираем случайного игрока в этом мире
        List<Player> players = world.getPlayers();
        if (players.isEmpty()) {
            ConsoleLogger.info("[Meteor] No players online in '" + worldName + "', skipping.");
            return;
        }
        Player target = players.get(random.nextInt(players.size()));

        // Ищем поверхность рядом с игроком (50-150 блоков)
        Location playerLoc = target.getLocation();
        int spawnMeteorsCount = cfg.getInt("meteor.count_per_spawn", 1);
        for (int i = 0; i < spawnMeteorsCount; i++) {
            spawnMeteorNear(world, playerLoc.getBlockX(), playerLoc.getBlockZ());
        }
    }

    /**
     * Спавнит N метеоров форсированно (из команды /mp meteor spawn).
     */
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

    /**
     * Спавнит один метеор рядом с указанными XZ-координатами.
     */
    private void spawnMeteorNear(World world, int centerX, int centerZ) {
        var cfg = Main.getInstance().getConfig();

        // Радиус от игрока
        int spawnRadiusMin = cfg.getInt("meteor.spawn_radius.min", 30);
        int spawnRadiusMax = cfg.getInt("meteor.spawn_radius.max", 120);

        // Генерируем случайную XZ точку
        int dx = spawnRadiusMin + random.nextInt(spawnRadiusMax - spawnRadiusMin + 1);
        int dz = spawnRadiusMin + random.nextInt(spawnRadiusMax - spawnRadiusMin + 1);
        if (random.nextBoolean()) dx = -dx;
        if (random.nextBoolean()) dz = -dz;
        int targetX = centerX + dx;
        int targetZ = centerZ + dz;

        // Находим поверхность (первый твёрдый блок сверху вниз)
        int heightMax = cfg.getInt("meteor.height.max", world.getMaxHeight() - 1);
        int heightMin = cfg.getInt("meteor.height.min", world.getMinHeight());

        int targetY = findSurfaceY(world, targetX, targetZ, heightMax, heightMin);
        if (targetY < 0) {
            // Не нашли поверхность — пробуем другое место
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

        // Параметры анимации
        int fallDuration = cfg.getInt("meteor.fall_duration_ticks", 60);
        double sphereRadiusMin = cfg.getDouble("meteor.sphere_radius.min", 1.0);
        double sphereRadiusMax = cfg.getDouble("meteor.sphere_radius.max", 3.0);
        double sphereRadius = sphereRadiusMin + random.nextDouble() * (sphereRadiusMax - sphereRadiusMin);

        // Блоки для визуализации сферы
        List<String> blockNames = cfg.getStringList("meteor.display_blocks");
        if (blockNames.isEmpty()) {
            blockNames = List.of("CRACKED_DEEPSLATE_BRICKS", "CRACKED_DEEPSLATE_TILES", "CHISELED_DEEPSLATE");
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

        double explosionRadius = cfg.getDouble("meteor.explosion_radius", 3.0);
        boolean explosionFire = cfg.getBoolean("meteor.explosion_set_fire", true);
        String oreName = cfg.getString("meteor.ore_block", "DEEPSLATE_DIAMOND_ORE");
        Material oreMaterial;
        try {
            oreMaterial = Material.valueOf(oreName.toUpperCase());
        } catch (IllegalArgumentException e) {
            oreMaterial = Material.DEEPSLATE_DIAMOND_ORE;
        }

        // Стартовая позиция метеора (под углом к цели)
        double horizontalOffset = 10 + random.nextDouble() * 30; // 10-40 блоков в стороне
        double angle = random.nextDouble() * 2 * Math.PI;
        double startX = targetX + Math.cos(angle) * horizontalOffset;
        double startZ = targetZ + Math.sin(angle) * horizontalOffset;
        double startY = targetY + 80 + random.nextDouble() * 40; // 80-120 блоков вверх

        // Запускаем анимацию
        MeteorTask task = new MeteorTask(
                world,
                startX, startY, startZ,
                targetX + 0.5, targetY + 1.0, targetZ + 0.5,
                fallDuration,
                sphereRadius,
                blockDataList,
                explosionRadius, explosionFire,
                oreMaterial
        );
        task.setOnComplete(() -> activeMeteors.remove(task));
        activeMeteors.add(task);
        task.runTaskTimer(Main.getInstance(), 1L, 1L);

        ConsoleLogger.info("[Meteor] Spawned meteor → (" + targetX + ", " + targetY + ", " + targetZ + ") radius="
                + String.format("%.1f", sphereRadius));
    }

    /**
     * Находит верхний твёрдый блок (поверхность) по XZ.
     */
    private int findSurfaceY(World world, int x, int z, int maxY, int minY) {
        int top = Math.min(maxY, world.getMaxHeight() - 1);
        int bottom = Math.max(minY, world.getMinHeight());

        for (int y = top; y >= bottom; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (block.isEmpty() || block.isLiquid()) continue;
            // Твёрдый блок + над ним воздух = поверхность
            if (block.getType().isSolid()) {
                Block above = world.getBlockAt(x, y + 1, z);
                if (above.isEmpty() || !above.getType().isSolid()) {
                    return y;
                }
            }
        }
        return -1;
    }

    /**
     * Удаляет завершённый метеор из активного списка.
     */
    public void removeMeteor(MeteorTask task) {
        activeMeteors.remove(task);
    }
}
