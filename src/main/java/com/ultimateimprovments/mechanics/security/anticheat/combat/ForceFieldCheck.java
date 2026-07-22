package com.ultimateimprovments.mechanics.security.anticheat.combat;

import com.ultimateimprovments.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovments.mechanics.security.anticheat.core.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * ForceField — атака entities вокруг игрока без поворота.
 * Детекция: атака цели, находящейся позади игрока.
 */
public class ForceFieldCheck extends AbstractCheck {

    private double maxAttackAngle;

    public ForceFieldCheck() {
        super("ForceField", CheckCategory.COMBAT);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxAttackAngle = getConfigDouble("max_attack_angle", 90.0);
    }

    @Override
    public void onReload() { loadConfig(); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player player)) return;
        if (!isEnabled() || isExempted(player)) return;
        if (!(e.getEntity() instanceof LivingEntity target)) return;

        double angle = getAngleToEntity(player, target);
        if (angle > maxAttackAngle) {
            CheckResult result = flag(player, 3.0,
                    "Attack behind: " + String.format("%.1f", angle) + "° from FOV");
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }
    }

    private double getAngleToEntity(Player player, LivingEntity target) {
        org.bukkit.Location eye = player.getEyeLocation();
        org.bukkit.util.Vector direction = eye.getDirection();
        org.bukkit.util.Vector toTarget = target.getLocation().toVector().subtract(eye.toVector()).normalize();
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, direction.dot(toTarget)))));
    }
}
