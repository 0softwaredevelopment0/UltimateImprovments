package com.ultimateimprovements.mechanics.security.anticheat.misc;

import com.ultimateimprovements.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovements.mechanics.security.anticheat.core.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AutoLoot — автоматический лут контейнеров (мгновенный перенос предметов).
 * Детекция: слишком быстрый перенос предметов из сундука в инвентарь.
 */
public class AutoLootCheck extends AbstractCheck {

    private int maxClicksPerSecond;
    private final ConcurrentHashMap<UUID, ClickCounter> counters = new ConcurrentHashMap<>();

    public AutoLootCheck() {
        super("AutoLoot", CheckCategory.MISC);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxClicksPerSecond = getConfigInt("max_clicks_per_second", 20);
    }

    @Override
    public void onReload() {
        loadConfig();
        maxClicksPerSecond = getConfigInt("max_clicks_per_second", 20);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!isEnabled() || isExempted(player)) return;
        // Only check SHIFT clicks (quick loot) and hotbar swaps
        if (e.getAction() != org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY
                && e.getAction() != org.bukkit.event.inventory.InventoryAction.HOTBAR_SWAP) {
            return;
        }

        ClickCounter counter = counters.computeIfAbsent(player.getUniqueId(), k -> new ClickCounter());
        int count = counter.incrementAndCount();
        if (count > maxClicksPerSecond) {
            CheckResult result = flag(player, 1.5,
                    "AutoLoot: " + count + " quick-transfers/sec (max: " + maxClicksPerSecond + ")");
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }
    }

    private static class ClickCounter {
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
