package com.ultimateimprovments.mechanics.security.anticheat.combat;

import com.ultimateimprovments.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovments.mechanics.security.anticheat.core.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Criticals — принудительные криты без прыжка/падения.
 * Детекция: урон выше ожидаемого критического, но игрок на земле без падения.
 */
public class CriticalsCheck extends AbstractCheck {

    private boolean checkJumpCriticals;

    public CriticalsCheck() {
        super("Criticals", CheckCategory.COMBAT);
    }

    @Override
    public void onInit() {
        loadConfig();
        checkJumpCriticals = getConfigBoolean("check_jump_criticals", true);
    }

    @Override
    public void onReload() {
        loadConfig();
        checkJumpCriticals = getConfigBoolean("check_jump_criticals", true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player player)) return;
        if (!isEnabled() || isExempted(player)) return;
        if (!(e.getEntity() instanceof LivingEntity)) return;

        if (!checkJumpCriticals) return;

        // In vanilla, criticals happen when player is in air AND falling (fallDistance > 0)
        // AND not on ground. If on ground with no fall distance, no crits should occur.
        PlayerData data = AntiCheatManager.getInstance().getOrCreatePlayerData(player);

        if (data.wasOnGround() && player.getFallDistance() <= 0) {
            // Calculate expected base damage vs actual damage
            AttributeInstance attr = player.getAttribute(Attribute.ATTACK_DAMAGE);
            if (attr == null) return;
            double baseDamage = attr.getValue();
            double critDamage = baseDamage * 1.5; // critical hit = 50% bonus

            // If actual damage is close to crit damage but player was on ground → hack
            double actualDamage = e.getFinalDamage();
            if (actualDamage >= critDamage * 0.95 && actualDamage > baseDamage * 1.2) {
                CheckResult result = flag(player, 2.0,
                        "Critical hit on ground: base=" + String.format("%.1f", baseDamage)
                                + " actual=" + String.format("%.1f", actualDamage)
                                + " crit~" + String.format("%.1f", critDamage));
                AntiCheatManager.getInstance().handleResult(player, this, result);
            }
        }
    }
}
