package com.ultimateimprovments.mechanics.security.anticheat.movement;

import com.ultimateimprovments.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovments.mechanics.security.anticheat.core.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FastEat — мгновенное поедание еды (быстрее ванильных 32 тиков).
 * Детекция: время между началом и окончанием поедания меньше минимума.
 */
public class FastEatCheck extends AbstractCheck {

    private long minEatTimeMs;
    private final ConcurrentHashMap<UUID, Long> eatStartTimes = new ConcurrentHashMap<>();

    public FastEatCheck() {
        super("FastEat", CheckCategory.MOVEMENT);
    }

    @Override
    public void onInit() {
        loadConfig();
        minEatTimeMs = getConfigInt("min_eat_time_ms", 1500);
    }

    @Override
    public void onReload() {
        loadConfig();
        minEatTimeMs = getConfigInt("min_eat_time_ms", 1500);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;

        ItemStack item = e.getItem();
        if (!item.getType().isEdible() && item.getType() != Material.POTION
                && item.getType() != Material.MILK_BUCKET) {
            return;
        }

        long now = System.currentTimeMillis();
        Long startTime = eatStartTimes.get(player.getUniqueId());

        if (startTime != null) {
            long elapsed = now - startTime;
            if (elapsed < minEatTimeMs) {
                CheckResult result = flag(player, 3.0,
                        "FastEat: " + elapsed + "ms (min: " + minEatTimeMs + "ms)");
                AntiCheatManager.getInstance().handleResult(player, this, result);
            }
            eatStartTimes.remove(player.getUniqueId());
        } else {
            // Start eating — track time
            eatStartTimes.put(player.getUniqueId(), now);
        }
    }
}
