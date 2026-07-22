package com.ultimateimprovments.combat.weapons.plasma.projectile;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class PlasmaEntityCollisions {

    private PlasmaEntityCollisions() {}

    public static CollisionResult handleEntityCollision(
            Entity entity,
            Player owner,
            Location projectileLocation,
            Vector direction,
            double speed
    ) {
        PlasmaEffects.entityHit(entity);
        Vector hitNormal = PlasmaPhysics.entityHitNormal(entity, projectileLocation);

        if (!(entity instanceof LivingEntity le)) {
            return new CollisionResult(CollisionType.RICOCHET, speed, hitNormal, null);
        }

        double hp = le.getHealth();

        if (speed < hp) {
            le.damage(speed, owner);
            double newSpeed = speed * PlasmaConstants.RICOCHET_SPEED_MULTIPLIER;
            return new CollisionResult(CollisionType.RICOCHET, newSpeed, hitNormal, null);
        }

        le.damage(999999, owner);

        double newSpeed = Math.max(0, speed - hp);
        Vector moveForward = direction.clone().multiply(0.7);

        PlasmaEffects.penetration(entity);

        return new CollisionResult(CollisionType.PENETRATION, newSpeed, null, moveForward);
    }
}
