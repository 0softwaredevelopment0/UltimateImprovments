package com.ultimateimprovments.combat.weapons.plasma.projectile;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

public class PlasmaBlockCollisions {

    private PlasmaBlockCollisions() {}

    public static CollisionResult handleBlockCollision(
            Block block,
            Vector direction,
            double speed,
            BlockFace hitFace
    ) {
        float hardness = block.getType().getHardness();
        Vector faceNormal = hitFace != null
                ? hitFace.getDirection()
                : PlasmaPhysics.incomingFaceNormal(direction);

        if (hardness < 0f) {
            PlasmaEffects.unbreakable(block);
            return new CollisionResult(CollisionType.RICOCHET, speed, faceNormal, null);
        }

        if (speed >= hardness) {
            PlasmaEffects.blockBreak(block);
            block.breakNaturally();

            double newSpeed = Math.max(0, speed - hardness);
            return new CollisionResult(CollisionType.PENETRATION, newSpeed, null, null);
        }

        return new CollisionResult(CollisionType.RICOCHET, speed, faceNormal, null);
    }
}
