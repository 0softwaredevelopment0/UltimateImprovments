package com.mcplugin.mechanics.security.anticheat.combat;

import com.mcplugin.mechanics.security.anticheat.AntiCheatManager;
import com.mcplugin.mechanics.security.anticheat.core.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Deque;

/**
 * KillAura — автоматически атакует ближайших entities.
 * <p>
 * Детекция:
 * - Резкие повороты головы (snap) перед атакой
 * - Атака нескольких целей одновременно
 * - Угол атаки вне поля зрения игрока
 */
public class KillAuraCheck extends AbstractCheck {

    private double maxAngleSnap;
    private double maxAttackAngle;
    private boolean checkMultiTarget;

    public KillAuraCheck() {
        super("KillAura", CheckCategory.COMBAT);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxAngleSnap = getConfigDouble("max_angle_snap", 90.0);
        maxAttackAngle = getConfigDouble("max_attack_angle", 180.0);
        checkMultiTarget = getConfigBoolean("check_multi_target", true);
    }

    @Override
    public void onReload() {
        loadConfig();
        maxAngleSnap = getConfigDouble("max_angle_snap", 90.0);
        maxAttackAngle = getConfigDouble("max_attack_angle", 180.0);
        checkMultiTarget = getConfigBoolean("check_multi_target", true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player player)) return;
        if (!isEnabled() || isExempted(player)) return;

        PlayerData data = AntiCheatManager.getInstance().getOrCreatePlayerData(player);
        Entity target = e.getEntity();

        // Check rotation snap — was there a huge yaw change right before attack?
        Deque<Float> yawHistory = data.getYawHistory();
        if (yawHistory.size() >= 2) {
            // Get second-to-last yaw via iterator
            java.util.Iterator<Float> it = yawHistory.descendingIterator();
            it.next(); // skip last
            float prevYaw = it.hasNext() ? it.next() : data.getLastYaw();
            float currentYaw = data.getLastYaw();
            double yawDelta = Math.abs(angleDiff(currentYaw, prevYaw));

            if (yawDelta > maxAngleSnap) {
                CheckResult result = flag(player, 2.0,
                        "Rotation snap " + String.format("%.1f", yawDelta) + "° before attack");
                AntiCheatManager.getInstance().handleResult(player, this, result);
            }
        }

        // Check attack angle — is the target outside the player's field of view?
        if (target instanceof LivingEntity) {
            double angle = getAngleToEntity(player, (LivingEntity) target);
            if (angle > maxAttackAngle) {
                CheckResult result = flag(player, 3.0,
                        "Attack outside FOV: " + String.format("%.1f", angle) + "°");
                AntiCheatManager.getInstance().handleResult(player, this, result);
            }
        }

        // Check multi-target — attacking different entities in rapid succession
        if (checkMultiTarget) {
            int attacks = data.getAttacksThisSecond();
            if (attacks > 3) {
                CheckResult result = flag(player, 2.0,
                        "Multi-target: " + attacks + " attacks/sec");
                AntiCheatManager.getInstance().handleResult(player, this, result);
            }
            data.registerAttack(0);
        }
    }

    private double angleDiff(float a, float b) {
        float diff = Math.abs(a - b) % 360;
        if (diff > 180) diff = 360 - diff;
        return diff;
    }

    private double getAngleToEntity(Player player, LivingEntity target) {
        org.bukkit.Location eye = player.getEyeLocation();
        org.bukkit.util.Vector direction = eye.getDirection();
        org.bukkit.util.Vector toTarget = target.getLocation().toVector().subtract(eye.toVector()).normalize();
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, direction.dot(toTarget)))));
    }
}
