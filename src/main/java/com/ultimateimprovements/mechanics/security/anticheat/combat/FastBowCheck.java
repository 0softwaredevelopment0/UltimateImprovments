package com.ultimateimprovements.mechanics.security.anticheat.combat;

import com.ultimateimprovements.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovements.mechanics.security.anticheat.core.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityShootBowEvent;

/**
 * FastBow — стрельба из лука быстрее ванильного лимита.
 * Детекция: интервал между выстрелами < минимального времени натяжения.
 */
public class FastBowCheck extends AbstractCheck {

    private long minBowIntervalMs;

    public FastBowCheck() {
        super("FastBow", CheckCategory.COMBAT);
    }

    @Override
    public void onInit() {
        loadConfig();
        minBowIntervalMs = getConfigInt("min_bow_interval_ms", 1100);
    }

    @Override
    public void onReload() { loadConfig(); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        if (!isEnabled() || isExempted(player)) return;

        PlayerData data = AntiCheatManager.getInstance().getOrCreatePlayerData(player);
        long now = System.currentTimeMillis();
        long lastShot = data.getLastAttackTime();

        if (lastShot > 0 && (now - lastShot) < minBowIntervalMs) {
            CheckResult result = flag(player, 3.0,
                    "Bow interval: " + (now - lastShot) + "ms (min: " + minBowIntervalMs + "ms)");
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }
        data.registerAttack(0);
    }
}
