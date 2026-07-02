package com.mcplugin.mechanics.security.anticheat.world;

import com.mcplugin.mechanics.security.anticheat.AntiCheatManager;
import com.mcplugin.mechanics.security.anticheat.core.*;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BedBreaker (BedF@cker) — массовое разрушение кроватей в Незере/Энде (взрывы).
 * Детекция: игрок ломает кровати в не-overworld измерениях слишком быстро.
 */
public class BedBreakerCheck extends AbstractCheck {

    private int maxBedsPerSecond;

    private final ConcurrentHashMap<UUID, BedCounter> counters = new ConcurrentHashMap<>();

    public BedBreakerCheck() {
        super("BedBreaker", CheckCategory.WORLD);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxBedsPerSecond = getConfigInt("max_beds_per_second", 2);
    }

    @Override
    public void onReload() {
        loadConfig();
        maxBedsPerSecond = getConfigInt("max_beds_per_second", 2);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;

        Material mat = e.getBlock().getType();
        // Check if it's a bed (all bed variants end with _BED)
        if (!mat.name().endsWith("_BED")) {
            return;
        }

        // Only flag in Nether or End (where beds explode)
        World.Environment env = e.getBlock().getWorld().getEnvironment();
        if (env == World.Environment.NORMAL) return;

        BedCounter counter = counters.computeIfAbsent(player.getUniqueId(), k -> new BedCounter());
        int count = counter.incrementAndCount();
        if (count > maxBedsPerSecond) {
            CheckResult result = flag(player, 4.0,
                    "BedBreaker: " + count + " beds/sec in " + env + " (max: " + maxBedsPerSecond + ")");
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }
    }

    private static class BedCounter {
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
