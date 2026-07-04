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
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Изменяет скорость ходьбы игрока в зависимости от блока под ним.
 * <p>
 * Настройка в config.yml → block_friction:
 * <pre>
 * block_friction:
 *   BLUE_ICE: 1.1
 *   PACKED_ICE: 0.9
 * </pre>
 * <p>
 * Значение friction — множитель относительно стандартного трения (0.6).
 * 1.1 = быстрее, 0.5 = медленнее.
 */
public class BlockFrictionListener implements Listener {

    /** Карта блок → friction (загружается из config.yml) */
    private static Map<Material, Double> frictionMap = new HashMap<>();

    /** Текущие игроки на кастомных блоках: UUID → тип блока */
    private final Map<UUID, Material> trackedPlayers = new HashMap<>();

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
            double friction = cfg.getDouble(key, 0.6);
            frictionMap.put(mat, friction);
            ConsoleLogger.info("[BlockFriction] " + mat.name() + " → friction=" + friction);
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (frictionMap.isEmpty()) return;

        // Only process when player changes block position (not just rotation)
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Block below the player's feet (what they're standing on)
        Block ground = to.getBlock().getRelative(BlockFace.DOWN);
        Material groundType = ground.getType();

        Material prevType = trackedPlayers.get(uuid);

        // Same block type — no change needed
        if (groundType == prevType) return;

        Double friction = frictionMap.get(groundType);

        if (friction != null) {
            // Entering (or switching to) a custom friction block
            trackedPlayers.put(uuid, groundType);
            float newSpeed = (float) (0.2f * friction / 0.6);
            newSpeed = Math.max(0f, Math.min(1f, newSpeed)); // clamp 0..1
            player.setWalkSpeed(newSpeed);
        } else if (prevType != null) {
            // Leaving a custom friction block — restore default speed
            trackedPlayers.remove(uuid);
            player.setWalkSpeed(0.2f);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        trackedPlayers.remove(event.getPlayer().getUniqueId());
        // Walk speed resets on rejoin automatically
    }

    // =========================
    // UTILITY
    // =========================

    public static boolean hasFriction(Material material) {
        return frictionMap.containsKey(material);
    }

    public static double getFriction(Material material) {
        return frictionMap.getOrDefault(material, 0.6);
    }
}
