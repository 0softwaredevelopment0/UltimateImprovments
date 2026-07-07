package com.mcplugin.command.subcommands;

import com.mcplugin.core.Main;
import com.mcplugin.util.ConsoleLogger;
import com.mcplugin.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /mp rtp — случайная телепортация с гибкой настройкой.
 * <p>
 * Выключено по умолчанию ({@code rtp.enabled: false}).
 * Конфиг позволяет задать радиус, форму области (квадрат/круг),
 * чёрный/белый списки блоков, лимиты высоты, избегание воздуха/пустоты,
 * кулдаун и список миров.
 */
public final class RtpSubcommand {

    private RtpSubcommand() {}

    /** Карта кулдаунов: UUID игрока → unix-миллисекунда когда можно снова использовать. */
    private static final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    /**
     * /mp rtp [player]
     * <p>
     * Если аргумент передан и у отправителя есть permission {@code mcplugin.command.rtp.other},
     * телепортирует указанного игрока. Иначе — телепортирует отправителя (если он игрок).
     */
    public static boolean execute(CommandSender sender, String[] args) {
        // Определяем цель
        Player target;
        boolean isSelf;

        if (args.length > 1) {
            // /mp rtp <player>
            if (!sender.hasPermission("mcplugin.command.rtp.other")) {
                sender.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to teleport other players!</red>"));
                return true;
            }
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(MessageUtil.parse("<red>❌ Player</red> <yellow>" + args[1] + "</yellow> <red>not found!</red>"));
                return true;
            }
            isSelf = sender.equals(target);
        } else {
            // /mp rtp
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MessageUtil.parse("<red>❌ Only players can use this command!</red>"));
                return true;
            }
            target = player;
            isSelf = true;
        }

        // Проверка: включена ли команда
        FileConfiguration cfg = Main.getInstance().getConfig();
        if (!cfg.getBoolean("rtp.enabled", false)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Random teleport is disabled on this server.</red>"));
            return true;
        }

        // Проверка: мир
        World world = target.getWorld();
        List<String> allowedWorlds = cfg.getStringList("rtp.worlds");
        if (!allowedWorlds.isEmpty() && !allowedWorlds.contains(world.getName())) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Random teleport is not allowed in this world.</red>"));
            return true;
        }

        // Проверка: кулдаун
        UUID uuid = target.getUniqueId();
        long now = System.currentTimeMillis();
        int cooldownSeconds = cfg.getInt("rtp.cooldown_seconds", 60);
        if (cooldownSeconds > 0 && !sender.hasPermission("mcplugin.command.rtp.bypasscooldown")) {
            Long nextUse = cooldowns.get(uuid);
            if (nextUse != null && now < nextUse) {
                long remaining = (nextUse - now + 999) / 1000;
                sender.sendMessage(MessageUtil.parse(
                        "<red>❌ Please wait </red><yellow>" + remaining + "</yellow><red> seconds before using RTP again.</red>"
                ));
                return true;
            }
        }

        // Параметры RTP
        RtpConfig config = loadConfig(cfg);

        // Поиск подходящей локации
        Location location = findSafeLocation(world, config);

        if (location == null) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Could not find a safe location to teleport. Try again.</red>"));
            return true;
        }

        // Телепортация
        boolean success = target.teleport(location);
        if (!success) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Teleport failed! Try again.</red>"));
            return true;
        }

        // Устанавливаем кулдаун
        if (cooldownSeconds > 0 && !sender.hasPermission("mcplugin.command.rtp.bypasscooldown")) {
            cooldowns.put(uuid, now + cooldownSeconds * 1000L);
        }

        // Сообщение
        String coords = "X: " + location.getBlockX() + " Y: " + location.getBlockY() + " Z: " + location.getBlockZ();
        sender.sendMessage(MessageUtil.parse(
                "<green>✔</green> <white>Random teleport complete!</white>"
        ));
        if (!isSelf) {
            sender.sendMessage(MessageUtil.parse(
                    "<gray>Teleported </gray><yellow>" + target.getName() + "</yellow> <gray>→</gray> <white>" + coords + "</white>"
            ));
        }
        target.sendMessage(MessageUtil.parse(
                "<green>✔</green> <white>You have been randomly teleported to:</white> <yellow>" + coords + "</yellow>"
        ));
        ConsoleLogger.info("[RTP] " + target.getName() + " teleported to " + coords + " in world " + world.getName());

        return true;
    }

    // ========================================================================
    // CONFIG
    // ========================================================================

    private record RtpConfig(
            int radiusMin,
            int radiusMax,
            boolean isCircle,
            int heightMin,
            int heightMax,
            int maxAttempts,
            int safeSearchRadius,
            Set<String> avoidBlocks,
            Set<String> onlySurface,
            boolean avoidVoid,
            boolean avoidLiquid
    ) {}

    private static RtpConfig loadConfig(FileConfiguration cfg) {
        String shape = cfg.getString("rtp.shape", "square").toLowerCase();

        return new RtpConfig(
                cfg.getInt("rtp.radius.min", 500),
                cfg.getInt("rtp.radius.max", 10000),
                shape.equals("circle"),
                cfg.getInt("rtp.height.min", 0),
                cfg.getInt("rtp.height.max", 255),
                cfg.getInt("rtp.max_attempts", 50),
                cfg.getInt("rtp.safe_search_radius", 5),
                new HashSet<>(cfg.getStringList("rtp.avoid_blocks")),
                new HashSet<>(cfg.getStringList("rtp.only_surface")),
                cfg.getBoolean("rtp.avoid.void", true),
                cfg.getBoolean("rtp.avoid.liquid", true)
        );
    }

    // ========================================================================
    // LOCATION SEARCH
    // ========================================================================

    /**
     * Пытается найти безопасную точку для телепортации.
     * Генерирует случайные координаты в пределах настроек и проверяет их.
     *
     * @return {@link Location} или {@code null}, если за {@code maxAttempts} не удалось найти.
     */
    private static Location findSafeLocation(World world, RtpConfig config) {
        Random random = new Random();

        for (int i = 0; i < config.maxAttempts(); i++) {
            int x = generateCoordinate(random, config.radiusMin(), config.radiusMax(), config.isCircle());
            int z = generateCoordinate(random, config.radiusMin(), config.radiusMax(), config.isCircle());

            // Ищем поверхность (Y)
            int surfaceY = findSurfaceY(world, x, z, config);
            if (surfaceY == -1) continue;

            // Проверяем, что Y в лимитах
            if (surfaceY < config.heightMin() || surfaceY > config.heightMax()) continue;

            // Проверяем блок поверхности
            Block surfaceBlock = world.getBlockAt(x, surfaceY, z);
            Material surfaceType = surfaceBlock.getType();

            // Проверка только для specified blocks
            if (!config.onlySurface().isEmpty() && !config.onlySurface().contains(surfaceType.name())) continue;

            // Проверка блоков для избегания
            if (config.avoidBlocks().contains(surfaceType.name())) continue;

            // Проверка жидкости (isSafeLocation делает более полную проверку ниже)
            if (config.avoidLiquid() && surfaceBlock.isLiquid()) continue;

            // Проверяем безопасность места (достаточно места для игрока)
            if (!isSafeLocation(world, x, surfaceY, z, config)) continue;

            // Локация найдена
            return new Location(world, x + 0.5, surfaceY + 1, z + 0.5,
                    random.nextFloat() * 360.0f, 0.0f);
        }

        return null;
    }

    /**
     * Генерирует случайную координату с учётом фигуры.
     * <p>
     * Для квадрата — равномерно от -radius до radius.
     * Для круга — с отбрасыванием точек за пределами радиуса (внешний цикл делает retry).
     */
    private static int generateCoordinate(Random random, int min, int max, boolean isCircle) {
        if (isCircle) {
            // Для круга используем расстояние max как радиус
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = min + random.nextDouble() * (max - min);
            return (int) Math.round(Math.cos(angle) * distance);
        } else {
            // Квадрат: независимые X и Z
            int coord;
            do {
                coord = random.nextInt(max * 2 + 1) - max;
            } while (Math.abs(coord) < min && min > 0);
            return coord;
        }
    }

    /**
     * Находит поверхность земли (первый твёрдый блок сверху вниз).
     *
     * @return Y блока поверхности, или -1 если не найдено / пустота.
     */
    private static int findSurfaceY(World world, int x, int z, RtpConfig config) {
        int maxY = Math.min(config.heightMax(), world.getMaxHeight() - 1);
        int minY = Math.max(config.heightMin(), world.getMinHeight());

        // Идём сверху вниз, ищем первый твёрдый блок
        for (int y = maxY; y >= minY; y--) {
            Block block = world.getBlockAt(x, y, z);

            // Проверка пустоты снизу (если avoidVoid и мы внизу мира)
            if (config.avoidVoid() && y <= world.getMinHeight() + 1) return -1;

            // Пропускаем воздух и прозрачные/непроходимые блоки
            if (block.isEmpty() || block.isLiquid() || !block.getType().isSolid()) continue;

            // Нашли твёрдый блок — это поверхность
            return y;
        }

        return -1; // не нашли поверхность
    }

    /**
     * Проверяет, достаточно ли места для игрока над поверхностью.
     * <p>
     * Игрок занимает 1 блок в ширину и 2 блока в высоту (Y+1, Y+2 должны быть воздухом).
     * Дополнительно проверяет {@code safeSearchRadius} соседних блоков на проходимость.
     */
    private static boolean isSafeLocation(World world, int x, int surfaceY, int z, RtpConfig config) {
        int radius = config.safeSearchRadius();

        // Основная проверка: достаточно ли места для игрока над поверхностью
        Block headBlock1 = world.getBlockAt(x, surfaceY + 1, z);
        Block headBlock2 = world.getBlockAt(x, surfaceY + 2, z);

        // Над поверхностью должно быть не менее 2 блоков воздуха
        if (!headBlock1.isEmpty() || !headBlock2.isEmpty()) {
            return false;
        }

        // Проверяем соседние блоки на проходимость (чтобы не застрять)
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) continue;

                Block neighborSurface = world.getBlockAt(x + dx, surfaceY, z + dz);
                if (!neighborSurface.getType().isSolid() && !neighborSurface.isEmpty()) {
                    // Полублоки, на которые можно встать — ок
                    // Лёд, стекло и т.д. — ок
                    if (neighborSurface.getType().isOccluding()) return false;
                }

                // Проверяем соседние блоки над поверхностью (могут быть не пусты — стена)
                for (int dy = 1; dy <= 2; dy++) {
                    Block neighbor = world.getBlockAt(x + dx, surfaceY + dy, z + dz);
                    if (neighbor.getType().isSolid() && neighbor.getType().isOccluding()) {
                        // Если полностью закрывает проход — небезопасно
                        return false;
                    }
                }
            }
        }

        // Проверяем, нет ли лавы/магмы под ногами на поверхности
        Block surfaceBlock = world.getBlockAt(x, surfaceY, z);
        if (config.avoidLiquid()) {
            if (surfaceBlock.isLiquid()) return false;

            // Проверяем под поверхностью на наличие жидкостей
            for (int dy = 1; dy <= 3; dy++) {
                Block below = world.getBlockAt(x, surfaceY - dy, z);
                if (below.isLiquid()) return false;
            }
        }

        return true;
    }
}
