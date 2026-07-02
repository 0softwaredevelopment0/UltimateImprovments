package com.mcplugin.mechanics.security.anticheat.movement;

import com.mcplugin.mechanics.security.anticheat.AntiCheatManager;
import com.mcplugin.mechanics.security.anticheat.core.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Jesus / WaterWalk — хождение по воде без Frost Walker.
 * Детекция: игрок стоит на поверхности воды, не тонет, без соответствующих эффектов.
 */
public class JesusCheck extends AbstractCheck {

    private int minWaterWalkTicks;
    private final ConcurrentHashMap<UUID, Integer> waterWalkCounters = new ConcurrentHashMap<>();

    public JesusCheck() {
        super("Jesus", CheckCategory.MOVEMENT);
    }

    @Override
    public void onInit() {
        loadConfig();
        minWaterWalkTicks = getConfigInt("min_water_walk_ticks", 15);
    }

    @Override
    public void onReload() {
        loadConfig();
        minWaterWalkTicks = getConfigInt("min_water_walk_ticks", 15);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;

        Location loc = player.getLocation();
        Block feet = loc.getBlock();
        Block below = loc.clone().subtract(0, 0.1, 0).getBlock();

        boolean onWater = (feet.getType() == Material.WATER || feet.getType() == Material.BUBBLE_COLUMN)
                && (below.getType() == Material.WATER || below.getType() == Material.BUBBLE_COLUMN);

        if (!onWater) {
            waterWalkCounters.put(player.getUniqueId(), 0);
            return;
        }

        // Check if player is not sinking (Y stable or moving horizontally on water)
        double yDelta = e.getTo().getY() - e.getFrom().getY();
        boolean onGround = player.isOnGround();

        // Frost Walker enchantment check
        if (player.getInventory().getBoots() != null
                && player.getInventory().getBoots().getEnchantments().containsKey(org.bukkit.enchantments.Enchantment.FROST_WALKER)) {
            waterWalkCounters.put(player.getUniqueId(), 0);
            return;
        }

        int waterWalkCount = waterWalkCounters.getOrDefault(player.getUniqueId(), 0);
        if (onGround || Math.abs(yDelta) < 0.02) {
            waterWalkCount++;
            waterWalkCounters.put(player.getUniqueId(), waterWalkCount);
            if (waterWalkCount >= minWaterWalkTicks) {
                CheckResult result = flag(player, 2.5,
                        "WaterWalk: standing on water for " + waterWalkCount + " ticks");
                AntiCheatManager.getInstance().handleResult(player, this, result);
            }
        } else {
            waterWalkCount = Math.max(0, waterWalkCount - 1);
            waterWalkCounters.put(player.getUniqueId(), waterWalkCount);
        }

        PlayerData data = AntiCheatManager.getInstance().getOrCreatePlayerData(player);
        data.updatePosition(e.getTo(), onGround);
    }
}
