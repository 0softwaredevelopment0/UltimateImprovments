package com.ultimateimprovements.mechanics.security.anticheat.world;

import com.ultimateimprovements.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovements.mechanics.security.anticheat.core.*;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tower — быстрое строительство вверх (установка блоков под собой).
 * Детекция: последовательная установка блоков на одной XZ с увеличением Y.
 */
public class TowerCheck extends AbstractCheck {

    private int maxBlocksPerSecond;
    private double maxYPerBlock;

    private final ConcurrentHashMap<UUID, TowerTracker> trackers = new ConcurrentHashMap<>();

    public TowerCheck() {
        super("Tower", CheckCategory.WORLD);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxBlocksPerSecond = getConfigInt("max_blocks_per_second", 5);
        maxYPerBlock = getConfigDouble("max_y_per_block", 1.0);
    }

    @Override
    public void onReload() {
        loadConfig();
        maxBlocksPerSecond = getConfigInt("max_blocks_per_second", 5);
        maxYPerBlock = getConfigDouble("max_y_per_block", 1.0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;

        Block placed = e.getBlockPlaced();
        Location playerLoc = player.getLocation();

        // Block placed at same XZ as player, below feet
        boolean sameXZ = placed.getX() == playerLoc.getBlockX()
                && placed.getZ() == playerLoc.getBlockZ();
        boolean below = placed.getY() <= playerLoc.getBlockY();

        if (!sameXZ || !below) {
            TowerTracker t = trackers.get(player.getUniqueId());
            if (t != null) t.reset();
            return;
        }

        TowerTracker tracker = trackers.computeIfAbsent(player.getUniqueId(), k -> new TowerTracker());
        int count = tracker.incrementAndCount(placed.getY());

        if (count > maxBlocksPerSecond) {
            CheckResult result = flag(player, 2.5,
                    "Tower: " + count + " vertical blocks/sec (max: " + maxBlocksPerSecond + ")");
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }
    }

    private static class TowerTracker {
        private long windowStart = System.currentTimeMillis();
        private int count = 0;
        private int lastY = Integer.MIN_VALUE;

        int incrementAndCount(int y) {
            long now = System.currentTimeMillis();
            if (now - windowStart > 1000) {
                windowStart = now;
                count = 0;
            }
            // Only count if Y increases (towering up)
            if (y > lastY) {
                count++;
            }
            lastY = y;
            return count;
        }

        void reset() {
            count = 0;
            lastY = Integer.MIN_VALUE;
        }
    }
}
