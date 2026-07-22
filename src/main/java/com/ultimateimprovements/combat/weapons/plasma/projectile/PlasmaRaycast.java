package com.ultimateimprovements.combat.weapons.plasma.projectile;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;

/**
 * Луч от снаряда до конца шага — первое попадание в блок за тик.
 */
public final class PlasmaRaycast {

    private PlasmaRaycast() {}

    public record BlockHit(Block block, Location hitPoint, BlockFace face, double distance) {

        public Vector faceNormal() {
            return face.getDirection();
        }
    }

    public static BlockHit cast(Location start, Vector direction, double maxDistance) {
        if (start.getWorld() == null || maxDistance <= 0) {
            return null;
        }

        if (!PlasmaPhysics.isAir(start)) {
            return null;
        }

        Vector dir = direction.clone();
        if (dir.lengthSquared() < 1.0E-8) {
            return null;
        }
        dir.normalize();

        int blockSteps = Math.max(1, (int) Math.ceil(maxDistance));

        BlockIterator iterator = new BlockIterator(
                start.getWorld(),
                start.toVector(),
                dir,
                0.0,
                blockSteps
        );

        Block previousAir = start.getBlock();

        while (iterator.hasNext()) {
            Block block = iterator.next();

            if (block.getType().isAir()) {
                previousAir = block;
                continue;
            }

            BlockFace face = block.getFace(previousAir);
            if (face == null) {
                face = faceFromIncomingDirection(dir);
            }

            Location hitPoint = hitPointOnFace(block, face);

            return new BlockHit(
                    block,
                    hitPoint,
                    face,
                    start.distance(hitPoint)
            );
        }

        return null;
    }

    static BlockFace faceFromIncomingDirection(Vector direction) {
        double ax = Math.abs(direction.getX());
        double ay = Math.abs(direction.getY());
        double az = Math.abs(direction.getZ());

        if (ax >= ay && ax >= az) {
            return direction.getX() > 0 ? BlockFace.WEST : BlockFace.EAST;
        }
        if (ay >= az) {
            return direction.getY() > 0 ? BlockFace.DOWN : BlockFace.UP;
        }
        return direction.getZ() > 0 ? BlockFace.NORTH : BlockFace.SOUTH;
    }

    private static Location hitPointOnFace(Block block, BlockFace face) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        return center.subtract(face.getDirection().clone().multiply(0.5));
    }
}
