package com.mcplugin.mechanics.features.movement;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.ConsoleLogger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

/**
 * Кастомное трение блоков — модификация горизонтальной velocity игрока.
 * <p>
 * <b>Механика Minecraft:</b><br>
 * Каждый тик сервер умножает горизонтальную velocity на {@code friction * 0.91}.<br>
 * Default (0.6): {@code vel *= 0.546} — экспоненциальное замедление.<br>
 * Custom (1.1):  {@code vel *= 1.001} — экспоненциальное ускорение!<br>
 * Custom (0.9):  {@code vel *= 0.819} — замедление быстрее default.
 * <p>
 * <b>Как работает:</b><br>
 * Сервер уже применил default friction (0.6 * 0.91). Наш корректор
 * умножает velocity на {@code customFriction / 0.6}, чтобы итоговое
 * трение за тик стало {@code customFriction * 0.91}.
 * <p>
 * <b>💡 Экспоненциальное ускорение:</b><br>
 * Значения {@code > 1.0} дают {@code vel *= > 0.546} — чем больше значение,
 * тем быстрее разгон. Пример: {@code 1.1 → vel *= 1.001} (медленный разгон),
 * {@code 10000 → vel *= 9100} (мгновенный рывок).
 * <p>
 * Настройка в config.yml → block_friction:
 * <pre>
 * block_friction:
 *   BLUE_ICE: 10000  # экспоненциальное ускорение
 *   PACKED_ICE: 0.9  # мягкое замедление
 *   SOUL_SAND: 0.4   # сильное замедление
 * </pre>
 */
public class BlockFrictionListener implements Listener {

    /** Карта блок → friction (загружается из config.yml) */
    private static Map<Material, Double> frictionMap = new HashMap<>();

    /** DEFAULT friction для большинства блоков в Minecraft */
    private static final double DEFAULT_FRICTION = 0.6;

    // =========================
    // INIT / RELOAD
    // =========================

    public static void init() {
        loadConfig();
    }

    public static void reloadConfig() {
        loadConfig();
    }

    private static void loadConfig() {
        frictionMap.clear();

        var cfg = Main.getInstance().getConfig().getConfigurationSection("block_friction");
        if (cfg == null) {
            ConsoleLogger.info("[BlockFriction] No config section 'block_friction' found — disabled.");
            return;
        }

        for (String key : cfg.getKeys(false)) {
            Material mat = Material.getMaterial(key.toUpperCase());
            if (mat == null) {
                ConsoleLogger.warn("[BlockFriction] Unknown material: " + key);
                continue;
            }
            double friction = cfg.getDouble(key, DEFAULT_FRICTION);
            frictionMap.put(mat, friction);

            // Показываем эффект: экспонента за тик
            double effect = friction * 0.91;
            String direction = effect > 1.0 ? "🔼 ACCEL" : effect < 1.0 ? "🔽 DECEL" : "➡ NEUTRAL";
            ConsoleLogger.info("[BlockFriction] " + mat.name()
                    + " → friction=" + friction
                    + " (vel×" + String.format("%.4f", effect) + "/tick " + direction + ")");
        }

        if (frictionMap.isEmpty()) {
            ConsoleLogger.info("[BlockFriction] No blocks configured — disabled.");
        } else {
            ConsoleLogger.info("[BlockFriction] Loaded " + frictionMap.size() + " block friction(s).");
        }
    }

    // =========================
    // EVENTS
    // =========================

    /**
     * При каждом движении игрока по блоку с кастомным friction
     * корректируем горизонтальную velocity.
     * <p>
     * Формула коррекции:
     * {@code vel *= customFriction / DEFAULT_FRICTION}
     * <p>
     * Почему: сервер уже умножил vel на {@code 0.6 * 0.91} (DEFAULT).
     * Нам нужно, чтобы итог был {@code customFriction * 0.91}.
     * {@code vel * 0.6*0.91 * (custom/0.6) = vel * custom*0.91} ✓
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (frictionMap.isEmpty()) return;

        // Пропускаем rotation-only (без смены позиции)
        Location to = event.getTo();
        if (to == null) return;
        Location from = event.getFrom();
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();

        // Блок ПОД ногами игрока (на чём стоит)
        Block ground = to.getBlock().getRelative(BlockFace.DOWN);
        Double friction = frictionMap.get(ground.getType());
        if (friction == null) return;

        // Множитель velocity: customFriction / 0.6
        // Значение > 1.0 даёт экспоненциальное УСКОРЕНИЕ (скорость растёт каждый тик).
        double multiplier = friction / DEFAULT_FRICTION;

        // Берём текущую velocity и умножаем горизонталь
        Vector vel = player.getVelocity();
        double newX = vel.getX() * multiplier;
        double newZ = vel.getZ() * multiplier;

        // Лимит: не быстрее 50 м/с (режим креатива/лога)
        double speed = Math.sqrt(newX * newX + newZ * newZ);
        double maxSpeed = 50.0;
        if (speed > maxSpeed) {
            double scale = maxSpeed / speed;
            newX *= scale;
            newZ *= scale;
        }

        vel.setX(newX);
        vel.setZ(newZ);
        player.setVelocity(vel);
    }

    // =========================
    // UTILITY
    // =========================

    public static boolean hasFriction(Material material) {
        return frictionMap.containsKey(material);
    }

    public static double getFriction(Material material) {
        return frictionMap.getOrDefault(material, DEFAULT_FRICTION);
    }
}
