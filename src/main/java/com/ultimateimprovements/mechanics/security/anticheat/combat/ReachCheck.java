package com.ultimateimprovements.mechanics.security.anticheat.combat;

import com.ultimateimprovements.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovements.mechanics.security.anticheat.core.*;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;

/**
 * Reach — увеличенная дистанция атаки.
 * <p>
 * Детекция: вычисляет 3D дистанцию от глаз игрока до БЛИЖАЙШЕЙ ТОЧКИ
 * bounding box цели. Ванильный лимит (survival): 3.0 блока.
 * <p>
 * Использует {@link Entity#getBoundingBox()} для точного расчёта
 * дистанции до хитбокса, а не до центра сущности — это критически важно
 * для больших сущностей (зомби, лошади), где центр на 1+ блок дальше края.
 */
public class ReachCheck extends AbstractCheck {

    private double maxReach;
    private double maxReachCreative;
    private double pingCompensation;

    public ReachCheck() {
        super("Reach", CheckCategory.COMBAT);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxReach = getConfigDouble("max_reach", 3.01);
        maxReachCreative = getConfigDouble("max_reach_creative", 5.0);
        pingCompensation = getConfigDouble("ping_compensation", 0.5);
    }

    @Override
    public void onReload() {
        loadConfig();
        maxReach = getConfigDouble("max_reach", 3.01);
        maxReachCreative = getConfigDouble("max_reach_creative", 5.0);
        pingCompensation = getConfigDouble("ping_compensation", 0.5);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player player)) return;
        if (!isEnabled() || isExempted(player)) return;

        Entity target = e.getEntity();
        PlayerData data = AntiCheatManager.getInstance().getOrCreatePlayerData(player);

        // ── Точный расчёт дистанции до bounding box ──
        // Берём ближайшую точку на хитбоксе цели к глазу игрока
        Vector eyePos = player.getEyeLocation().toVector();
        var box = target.getBoundingBox();
        double closestX = clamp(eyePos.getX(), box.getMinX(), box.getMaxX());
        double closestY = clamp(eyePos.getY(), box.getMinY(), box.getMaxY());
        double closestZ = clamp(eyePos.getZ(), box.getMinZ(), box.getMaxZ());
        double distance = eyePos.distance(new Vector(closestX, closestY, closestZ));

        // Apply ping compensation
        double pingAdjust = Math.min(player.getPing() / 1000.0 * 0.1, pingCompensation);
        double effectiveMax = maxReach + pingAdjust;

        // Creative mode gets more reach
        if (player.getGameMode() == GameMode.CREATIVE) {
            effectiveMax = maxReachCreative;
        }

        data.registerAttack(distance);

        if (distance > effectiveMax) {
            double exceed = distance - effectiveMax;
            double vl = Math.min(5.0, exceed * 3.0);
            CheckResult result = flag(player, vl,
                    "Reach: " + String.format("%.2f", distance) + " blocks (max: " + String.format("%.2f", effectiveMax) + ")");
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }
    }

    private static double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(val, max));
    }
}
