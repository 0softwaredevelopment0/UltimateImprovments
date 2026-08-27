package com.ultimateimprovments.mechanics.features.movement;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
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
 * Custom block friction — modifies the player's horizontal velocity.
 * <p>
 * <b>Minecraft mechanics:</b><br>
 * Every tick the server multiplies the horizontal velocity by {@code friction * 0.91}.<br>
 * Default (0.6): {@code vel *= 0.546} — exponential deceleration.<br>
 * Custom (1.1):  {@code vel *= 1.001} — exponential acceleration!<br>
 * Custom (0.9):  {@code vel *= 0.819} — deceleration faster than default.
 * <p>
 * <b>How it works:</b><br>
 * The server has already applied the default friction (0.6 * 0.91). Our corrector
 * multiplies the velocity by {@code customFriction / 0.6}, so the resulting
 * friction per tick becomes {@code customFriction * 0.91}.
 * <p>
 * <b>💡 Exponential acceleration:</b><br>
 * Values {@code > 1.0} give {@code vel *= > 0.546} — the larger the value,
 * the faster the acceleration. Example: {@code 1.1 → vel *= 1.001} (slow acceleration),
 * {@code 10000 → vel *= 9100} (instant burst).
 * <p>
 * Configured in config.yml → block_friction:
 * <pre>
 * block_friction:
 *   BLUE_ICE: 10000  # exponential acceleration
 *   PACKED_ICE: 0.9  # soft deceleration
 *   SOUL_SAND: 0.4   # strong deceleration
 * </pre>
 */
public class BlockFrictionListener implements Listener {

    /** Block → friction map (loaded from config.yml) */
    private static Map<Material, Double> frictionMap = new HashMap<>();

    /** DEFAULT friction for most blocks in Minecraft */
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

            // Show the effect: the exponent per tick
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
     * On every player movement over a block with custom friction
     * corrects the horizontal velocity.
     * <p>
     * Correction formula:
     * {@code vel *= customFriction / DEFAULT_FRICTION}
     * <p>
     * Why: the server already multiplied vel by {@code 0.6 * 0.91} (DEFAULT).
     * We need the result to be {@code customFriction * 0.91}.
     * {@code vel * 0.6*0.91 * (custom/0.6) = vel * custom*0.91} ✓
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (frictionMap.isEmpty()) return;

        // Skip rotation-only (no position change)
        Location to = event.getTo();
        if (to == null) return;
        Location from = event.getFrom();
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();

        // The block UNDER the player's feet (what they stand on)
        Block ground = to.getBlock().getRelative(BlockFace.DOWN);
        Double friction = frictionMap.get(ground.getType());
        if (friction == null) return;

        // Velocity multiplier: customFriction / 0.6
        // A value > 1.0 gives exponential ACCELERATION (speed grows every tick).
        double multiplier = friction / DEFAULT_FRICTION;

        // Take the current velocity and multiply the horizontal part
        Vector vel = player.getVelocity();
        double newX = vel.getX() * multiplier;
        double newZ = vel.getZ() * multiplier;

        // Limit: no faster than 50 m/s (creative/flying mode)
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
