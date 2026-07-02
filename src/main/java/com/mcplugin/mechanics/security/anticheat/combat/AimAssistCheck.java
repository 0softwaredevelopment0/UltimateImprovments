package com.mcplugin.mechanics.security.anticheat.combat;

import com.mcplugin.mechanics.security.anticheat.AntiCheatManager;
import com.mcplugin.mechanics.security.anticheat.core.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Deque;

/**
 * AimAssist — плавная помощь прицеливания.
 * Детекция: аномально ровные микро-изменения yaw перед атакой.
 */
public class AimAssistCheck extends AbstractCheck {

    private double minConsistency;

    public AimAssistCheck() {
        super("AimAssist", CheckCategory.COMBAT);
    }

    @Override
    public void onInit() {
        loadConfig();
        minConsistency = getConfigDouble("min_consistency", 0.85);
    }

    @Override
    public void onReload() { loadConfig(); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player player)) return;
        if (!isEnabled() || isExempted(player)) return;

        PlayerData data = AntiCheatManager.getInstance().getOrCreatePlayerData(player);
        Deque<Float> yawHistory = data.getYawHistory();

        if (yawHistory.size() < 5) return;

        // Check if yaw changes are suspiciously smooth (robotic)
        Float[] yaws = yawHistory.toArray(new Float[0]);
        int consistent = 0;
        for (int i = 1; i < yaws.length; i++) {
            float diff = Math.abs(yaws[i] - yaws[i - 1]);
            if (diff > 0.1f && diff < 2.0f) consistent++;
        }
        double consistency = (double) consistent / (yaws.length - 1);

        if (consistency > minConsistency) {
            CheckResult result = flag(player, 1.5,
                    "Aim consistency: " + String.format("%.2f", consistency));
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }
    }
}
