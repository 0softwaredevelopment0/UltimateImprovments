package com.mcplugin.mechanics.security.anticheat.world;

import com.mcplugin.mechanics.security.anticheat.AntiCheatManager;
import com.mcplugin.mechanics.security.anticheat.core.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FastPlace — установка блоков быстрее ванильного лимита.
 * Детекция: количество блоков, установленных за секунду, превышает максимум.
 */
public class FastPlaceCheck extends AbstractCheck {

    private int maxBlocksPerSecond;

    private final ConcurrentHashMap<UUID, PlaceCounter> counters = new ConcurrentHashMap<>();

    public FastPlaceCheck() {
        super("FastPlace", CheckCategory.WORLD);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxBlocksPerSecond = getConfigInt("max_blocks_per_second", 8);
    }

    @Override
    public void onReload() {
        loadConfig();
        maxBlocksPerSecond = getConfigInt("max_blocks_per_second", 8);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;

        PlayerData data = AntiCheatManager.getInstance().getOrCreatePlayerData(player);
        data.registerBlockPlace();

        PlaceCounter counter = counters.computeIfAbsent(player.getUniqueId(), k -> new PlaceCounter());
        int count = counter.incrementAndCount();
        if (count > maxBlocksPerSecond) {
            CheckResult result = flag(player, 2.0,
                    "FastPlace: " + count + " blocks/sec (max: " + maxBlocksPerSecond + ")");
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }
    }

    private static class PlaceCounter {
        private long windowStart = System.currentTimeMillis();
        private int count = 0;

        int incrementAndCount() {
            long now = System.currentTimeMillis();
            if (now - windowStart > 1000) {
                windowStart = now;
                count = 0;
            }
            count++;
            return count;
        }
    }
}
