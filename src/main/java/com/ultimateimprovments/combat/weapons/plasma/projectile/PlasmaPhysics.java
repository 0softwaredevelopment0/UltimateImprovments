package com.ultimateimprovments.combat.weapons.plasma.projectile;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.util.Random;

public class PlasmaPhysics {

    private PlasmaPhysics() {}

    public static boolean isAir(Location location) {
        return location.getBlock().getType().isAir();
    }

    public static void applyInstability(Vector dir, Random random) {
        Vector drift = new Vector(
                (random.nextDouble() - 0.5) * PlasmaConstants.INSTABILITY,
                (random.nextDouble() - 0.5) * PlasmaConstants.INSTABILITY,
                (random.nextDouble() - 0.5) * PlasmaConstants.INSTABILITY
        );
        dir.add(drift).normalize();
    }

    public static Vector incomingFaceNormal(Vector direction) {
        double ax = Math.abs(direction.getX());
        double ay = Math.abs(direction.getY());
        double az = Math.abs(direction.getZ());

        if (ax >= ay && ax >= az) {
            return new Vector(-Math.signum(direction.getX()), 0, 0);
        }
        if (ay >= az) {
            return new Vector(0, -Math.signum(direction.getY()), 0);
        }
        return new Vector(0, 0, -Math.signum(direction.getZ()));
    }

    public static Vector entityHitNormal(Entity entity, Location projectileLocation) {
        Location center = entity.getLocation().add(0, entity.getHeight() * 0.5, 0);
        Vector normal = projectileLocation.toVector().subtract(center.toVector());
        if (normal.lengthSquared() < 1.0E-6) {
            return incomingFaceNormal(projectileLocation.getDirection());
        }
        return normal.normalize();
    }

    /**
     * Рикошет от блока: отражение + лёгкий шум + гарантия движения от грани.
     */
    public static Vector ricochetOffBlock(Vector direction, Vector faceNormal, Random random) {
        Vector reflected = reflect(direction, faceNormal);

        Vector noise = new Vector(
                (random.nextDouble() - 0.5) * PlasmaConstants.BLOCK_RICOCHET_NOISE,
                (random.nextDouble() - 0.5) * PlasmaConstants.BLOCK_RICOCHET_NOISE,
                (random.nextDouble() - 0.5) * PlasmaConstants.BLOCK_RICOCHET_NOISE
        );

        reflected.add(noise);
        return ensureMovingAwayFromWall(reflected, faceNormal);
    }

    /** Рикошет от сущности (больше шума). */
    public static Vector ricochetOffEntity(Vector direction, Vector hitNormal, Random random) {
        Vector result = reflect(direction, hitNormal);

        Vector noise = new Vector(
                (random.nextDouble() - 0.5) * 0.2,
                (random.nextDouble() - 0.5) * 0.2,
                (random.nextDouble() - 0.5) * 0.2
        );

        return result.add(noise).normalize();
    }

    private static Vector reflect(Vector direction, Vector faceNormal) {
        if (faceNormal == null || faceNormal.lengthSquared() < 1.0E-8) {
            return direction.clone().multiply(-1).normalize();
        }

        Vector normal = faceNormal.clone().normalize();
        double dot = direction.dot(normal);
        return direction.clone()
                .subtract(normal.multiply(2 * dot))
                .normalize();
    }

    /**
     * Нормаль смотрит от грани к снаряду; после отражения скорость должна уходить от стены.
     */
    public static Vector ensureMovingAwayFromWall(Vector direction, Vector faceNormal) {
        Vector n = faceNormal.clone().normalize();
        Vector dir = direction.clone();

        if (dir.lengthSquared() < 1.0E-8) {
            return n;
        }
        dir.normalize();

        if (dir.dot(n) < PlasmaConstants.MIN_OUTWARD_DOT) {
            return reflect(dir, n);
        }

        return dir;
    }

    /** Центр воздушного блока, откуда снаряд влетел в грань. */
    public static Location positionAfterBlockRicochet(Block block, BlockFace hitFace) {
        return centerOfAirCellOnFace(block, hitFace);
    }

    /** Выход с противоположной стороны пробитого блока. */
    public static Location positionAfterBlockPenetration(Block block, BlockFace hitFace, Vector direction) {
        BlockFace exitFace = hitFace.getOppositeFace();
        Location out = block.getLocation()
                .add(0.5, 0.5, 0.5)
                .add(exitFace.getDirection().clone().multiply(0.55));

        if (isAir(out)) {
            return out;
        }

        return out.add(direction.clone().multiply(0.55));
    }

    public static Location centerOfAirCellOnFace(Block block, BlockFace hitFace) {
        for (int distance = 1; distance <= PlasmaConstants.MAX_ESCAPE_BLOCKS; distance++) {
            Block cell = block.getRelative(hitFace, distance);
            Location center = cell.getLocation().add(0.5, 0.5, 0.5);
            if (isAir(center)) {
                return center;
            }
        }
        return block.getRelative(hitFace).getLocation().add(0.5, 0.5, 0.5);
    }

    /** Запасная защита: только если координаты внутри твёрдого блока. */
    public static Location escapeToAir(Location location, Vector preferredOutward) {
        if (isAir(location)) {
            return location;
        }

        Location current = location.clone();
        Vector preferred = preferredOutward != null && preferredOutward.lengthSquared() > 1.0E-8
                ? preferredOutward.clone().normalize()
                : new Vector(0, 1, 0);

        Vector[] directions = {
                preferred,
                new Vector(1, 0, 0),
                new Vector(-1, 0, 0),
                new Vector(0, 1, 0),
                new Vector(0, -1, 0),
                new Vector(0, 0, 1),
                new Vector(0, 0, -1),
        };

        for (Vector outward : directions) {
            for (double step = PlasmaConstants.ESCAPE_STEP;
                 step <= PlasmaConstants.MAX_ESCAPE_DISTANCE;
                 step += PlasmaConstants.ESCAPE_STEP) {

                Location candidate = current.clone().add(outward.clone().multiply(step));
                if (isAir(candidate)) {
                    return candidate;
                }
            }
        }

        return current;
    }
}
