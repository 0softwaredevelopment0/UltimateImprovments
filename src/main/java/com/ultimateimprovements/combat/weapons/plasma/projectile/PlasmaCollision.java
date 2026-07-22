package com.ultimateimprovements.combat.weapons.plasma.projectile;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class PlasmaCollision {

    private PlasmaCollision() {}

    /**
     * Проверяет, защищен ли владелец от собственного снаряда
     */
    public static boolean isOwnerProtected(
            Entity entity,
            Player owner,
            int life
    ) {
        return entity instanceof Player p
                && p.getUniqueId().equals(owner.getUniqueId())
                && life <= PlasmaConstants.OWNER_IMMUNITY_TICKS;
    }
}