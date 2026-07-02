package com.mcplugin.mechanics.security.anticheat.world;

import com.mcplugin.mechanics.security.anticheat.AntiCheatManager;
import com.mcplugin.mechanics.security.anticheat.core.*;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Scaffold — авто-установка блоков под собой при движении/падении.
 * Детекция: блок ставится под игроком, при этом игрок смотрит вверх или в сторону,
 * и скорость установки слишком высокая.
 */
public class ScaffoldCheck extends AbstractCheck {

    private double maxPlaceSpeedMs;
    private boolean requireLookDown;

    private final java.util.concurrent.ConcurrentHashMap<java.util.UUID, Long> lastPlaceTimes
            = new java.util.concurrent.ConcurrentHashMap<>();

    public ScaffoldCheck() {
        super("Scaffold", CheckCategory.WORLD);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxPlaceSpeedMs = getConfigInt("max_place_speed_ms", 50);
        requireLookDown = getConfigBoolean("require_look_down", false);
    }

    @Override
    public void onReload() {
        loadConfig();
        maxPlaceSpeedMs = getConfigInt("max_place_speed_ms", 50);
        requireLookDown = getConfigBoolean("require_look_down", false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;

        Block placed = e.getBlockPlaced();
        Block below = player.getLocation().getBlock().getRelative(BlockFace.DOWN);

        // Block placed directly below player
        if (placed.getX() == below.getX() && placed.getZ() == below.getZ()
                && Math.abs(placed.getY() - below.getY()) <= 1) {

            // Check pitch — scaffold usually places while looking straight down
            // but some cheats bypass by placing while looking forward
            float pitch = player.getLocation().getPitch();
            boolean lookingDown = pitch > 60.0f;

            if (requireLookDown && !lookingDown) {
                // Flag: placed below without looking down
                CheckResult result = flag(player, 2.5,
                        "Scaffold: placed below without looking down (pitch=" + String.format("%.1f", pitch) + ")");
                AntiCheatManager.getInstance().handleResult(player, this, result);
            }

            // Check placement speed
            long now = System.currentTimeMillis();
            Long last = lastPlaceTimes.get(player.getUniqueId());
            if (last != null && now - last < maxPlaceSpeedMs) {
                CheckResult result = flag(player, 2.0,
                        "Scaffold: " + (now - last) + "ms between places (min: " + maxPlaceSpeedMs + "ms)");
                AntiCheatManager.getInstance().handleResult(player, this, result);
            }
            lastPlaceTimes.put(player.getUniqueId(), now);
        }
    }
}
