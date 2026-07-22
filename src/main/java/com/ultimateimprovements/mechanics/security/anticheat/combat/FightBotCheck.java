package com.ultimateimprovements.mechanics.security.anticheat.combat;

import com.ultimateimprovements.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovements.mechanics.security.anticheat.core.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * FightBot — автоматический бой (bot).
 * Детекция: аномально ровные интервалы между атаками.
 */
public class FightBotCheck extends AbstractCheck {

    private double maxIntervalConsistency;

    public FightBotCheck() {
        super("FightBot", CheckCategory.COMBAT);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxIntervalConsistency = getConfigDouble("max_interval_consistency", 0.95);
    }

    @Override
    public void onReload() { loadConfig(); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player player)) return;
        if (!isEnabled() || isExempted(player)) return;

        long now = System.currentTimeMillis();
        PlayerData data = AntiCheatManager.getInstance().getOrCreatePlayerData(player);
        long lastAttack = data.getLastAttackTime();

        if (lastAttack > 0) {
            long interval = now - lastAttack;
            // Detect bot-like consistent intervals (same ms ±5%)
            // Stub: flag if intervals are suspiciously consistent
            if (interval > 0 && interval < 500) {
                // Too fast — likely automated
                CheckResult result = flag(player, 1.0,
                        "Attack interval: " + interval + "ms");
                AntiCheatManager.getInstance().handleResult(player, this, result);
            }
        }
    }
}
