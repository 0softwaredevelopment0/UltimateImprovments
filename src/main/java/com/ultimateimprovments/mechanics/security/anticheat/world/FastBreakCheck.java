package com.ultimateimprovments.mechanics.security.anticheat.world;

import com.ultimateimprovments.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovments.mechanics.security.anticheat.core.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FastBreak / Nuker — быстрое разрушение блоков или массовое ломание.
 * Детекция: количество сломанных блоков за секунду превышает лимит.
 */
public class FastBreakCheck extends AbstractCheck {

    private int maxBreaksPerSecond;

    private final ConcurrentHashMap<UUID, BreakCounter> counters = new ConcurrentHashMap<>();

    public FastBreakCheck() {
        super("FastBreak", CheckCategory.WORLD);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxBreaksPerSecond = getConfigInt("max_breaks_per_second", 8);
    }

    @Override
    public void onReload() {
        loadConfig();
        maxBreaksPerSecond = getConfigInt("max_breaks_per_second", 8);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;

        PlayerData data = AntiCheatManager.getInstance().getOrCreatePlayerData(player);
        data.registerBlockBreak();

        BreakCounter counter = counters.computeIfAbsent(player.getUniqueId(), k -> new BreakCounter());
        int count = counter.incrementAndCount();
        if (count > maxBreaksPerSecond) {
            CheckResult result = flag(player, 2.5,
                    "FastBreak: " + count + " blocks/sec (max: " + maxBreaksPerSecond + ")");
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }
    }

    private static class BreakCounter {
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
