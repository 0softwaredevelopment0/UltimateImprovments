package com.ultimateimprovments.mechanics.security.anticheat.misc;

import com.ultimateimprovments.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovments.mechanics.security.anticheat.core.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityRegainHealthEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Regen / FastHeal — ускоренная регенерация здоровья.
 * Детекция: игрок восстанавливает здоровье быстрее ванильного лимита.
 */
public class RegenCheck extends AbstractCheck {

    private long minRegenIntervalMs;
    private double minHealAmount;

    private final ConcurrentHashMap<UUID, Long> lastRegenTimes = new ConcurrentHashMap<>();

    public RegenCheck() {
        super("Regen", CheckCategory.MISC);
    }

    @Override
    public void onInit() {
        loadConfig();
        minRegenIntervalMs = getConfigInt("min_regen_interval_ms", 2500);
        minHealAmount = getConfigDouble("min_heal_amount", 1.0);
    }

    @Override
    public void onReload() {
        loadConfig();
        minRegenIntervalMs = getConfigInt("min_regen_interval_ms", 2500);
        minHealAmount = getConfigDouble("min_heal_amount", 1.0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        if (!isEnabled() || isExempted(player)) return;
        if (e.getAmount() < minHealAmount) return;

        // Only check natural regen and satiation, not potions/beacons/magic
        if (e.getRegainReason() != EntityRegainHealthEvent.RegainReason.SATIATED
                && e.getRegainReason() != EntityRegainHealthEvent.RegainReason.REGEN) {
            return;
        }

        long now = System.currentTimeMillis();
        Long last = lastRegenTimes.get(player.getUniqueId());

        if (last != null && now - last < minRegenIntervalMs) {
            CheckResult result = flag(player, 3.0,
                    "FastHeal: " + (now - last) + "ms since last regen (min: " + minRegenIntervalMs + "ms)");
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }

        lastRegenTimes.put(player.getUniqueId(), now);
    }
}
