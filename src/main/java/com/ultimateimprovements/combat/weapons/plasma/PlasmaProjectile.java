package com.ultimateimprovements.combat.weapons.plasma;

import com.ultimateimprovements.combat.weapons.plasma.projectile.*;
import com.ultimateimprovements.combat.weapons.core.BaseProjectile;
import com.ultimateimprovements.combat.weapons.core.ProjectileManager;
import com.ultimateimprovements.combat.weapons.core.ProjectileType;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Random;

/**
 * Оркестратор: тик, состояние, применение коллизий.
 * Физика блоков — рикошет / пробитие; escape — только запасная защита.
 */
public class PlasmaProjectile implements BaseProjectile {

    private final Player owner;

    private Location loc;
    private Vector dir;

    private int life = 0;
    private boolean dead = false;

    private Location lastAirLoc;
    private Block lastStuckBlock;
    private int stuckBlockTicks = 0;
    private int stabilityTicks = 0;

    private double speed = 0.5;

    private final Random random = new Random();

    public PlasmaProjectile(Player owner) {
        this.owner = owner;
        this.loc = owner.getEyeLocation().clone();
        this.dir = owner.getLocation().getDirection().normalize();
        this.lastAirLoc = loc.clone();
    }

    public static void spawn(Player player) {
        ProjectileManager.add(new PlasmaProjectile(player));
    }

    @Override
    public ProjectileType getType() {
        return ProjectileType.PLASMA;
    }

    @Override
    public boolean isDead() {
        return dead;
    }

    @Override
    public void tick() {
        if (dead) {
            return;
        }

        life++;

        if (life > PlasmaConstants.MAX_LIFE) {
            dead = true;
            return;
        }

        if (speed >= PlasmaConstants.MAX_SAFE_SPEED) {
            PlasmaEffects.explode(loc);
            dead = true;
            return;
        }

        // Запасная защита: только если реально внутри блока
        if (!PlasmaPhysics.isAir(loc)) {
            loc = PlasmaPhysics.escapeToAir(loc, PlasmaPhysics.incomingFaceNormal(dir));
        }

        rememberAirPosition();

        if (tryUnstuckFromWall()) {
            if (!dead) {
                PlasmaEffects.plasmaTrail(loc);
            }
            return;
        }

        // Дрейф только в свободном полёте (не сразу после удара о стену)
        if (stabilityTicks > 0) {
            stabilityTicks--;
        } else {
            PlasmaPhysics.applyInstability(dir, random);
        }

        PlasmaRaycast.BlockHit blockHit = PlasmaRaycast.cast(loc, dir, speed);
        Location stepEnd = loc.clone().add(dir.clone().multiply(speed));

        if (blockHit != null) {
            trackStuckBlock(blockHit.block());

            CollisionResult result = PlasmaBlockCollisions.handleBlockCollision(
                    blockHit.block(),
                    dir,
                    speed,
                    blockHit.face()
            );
            applyBlockCollision(result, blockHit);
            safetyEscapeIfInsideBlock(blockHit.face().getDirection());

            if (!dead) {
                PlasmaEffects.plasmaTrail(loc);
            }
            return;
        }

        clearStuckBlock();

        for (Entity entity : stepEnd.getWorld().getNearbyEntities(stepEnd, 0.6, 0.6, 0.6)) {
            if (PlasmaCollision.isOwnerProtected(entity, owner, life)) {
                continue;
            }

            CollisionResult result = PlasmaEntityCollisions.handleEntityCollision(
                    entity, owner, loc, dir, speed
            );
            applyEntityCollision(result);

            if (dead) {
                return;
            }
        }

        loc = stepEnd;
        rememberAirPosition();
        PlasmaEffects.plasmaTrail(loc);
    }

    private void applyBlockCollision(CollisionResult result, PlasmaRaycast.BlockHit blockHit) {
        speed = PlasmaConstants.clampSpeed(result.newSpeed);
        Vector faceNormal = blockHit.faceNormal();

        switch (result.type) {
            case RICOCHET -> {
                dir = PlasmaPhysics.ricochetOffBlock(dir, faceNormal, random);
                loc = PlasmaPhysics.positionAfterBlockRicochet(blockHit.block(), blockHit.face());
                stabilityTicks = PlasmaConstants.POST_BLOCK_HIT_STABILITY_TICKS;
                PlasmaEffects.ricochet(loc);
            }
            case PENETRATION -> {
                loc = PlasmaPhysics.positionAfterBlockPenetration(
                        blockHit.block(),
                        blockHit.face(),
                        dir
                );
                stabilityTicks = 2;
            }
            case STOP -> dead = true;
        }
    }

    private void applyEntityCollision(CollisionResult result) {
        speed = PlasmaConstants.clampSpeed(result.newSpeed);

        switch (result.type) {
            case RICOCHET -> {
                Vector hitNormal = result.hitNormal != null
                        ? result.hitNormal
                        : PlasmaPhysics.incomingFaceNormal(dir);
                dir = PlasmaPhysics.ricochetOffEntity(dir, hitNormal, random);
                loc.add(dir.clone().multiply(0.2));
                PlasmaEffects.ricochet(loc);
            }
            case PENETRATION -> {
                if (result.moveVector != null) {
                    loc.add(result.moveVector);
                }
            }
            case STOP -> dead = true;
        }
    }

    private void safetyEscapeIfInsideBlock(Vector faceNormal) {
        if (!PlasmaPhysics.isAir(loc)) {
            loc = PlasmaPhysics.escapeToAir(loc, faceNormal);
        }
    }

    private void rememberAirPosition() {
        if (PlasmaPhysics.isAir(loc)) {
            lastAirLoc = loc.clone();
        }
    }

    private void trackStuckBlock(Block block) {
        if (block.equals(lastStuckBlock)) {
            stuckBlockTicks++;
        } else {
            lastStuckBlock = block;
            stuckBlockTicks = 1;
        }
    }

    private void clearStuckBlock() {
        lastStuckBlock = null;
        stuckBlockTicks = 0;
    }

    /** Запасной откат, если основная физика не вывела из цикла у одного блока. */
    private boolean tryUnstuckFromWall() {
        if (stuckBlockTicks < PlasmaConstants.STUCK_BLOCK_TICKS || lastAirLoc == null) {
            return false;
        }

        Vector faceNormal = PlasmaPhysics.incomingFaceNormal(dir);
        loc = lastAirLoc.clone();
        dir = PlasmaPhysics.ricochetOffBlock(dir, faceNormal, random);
        stabilityTicks = PlasmaConstants.POST_BLOCK_HIT_STABILITY_TICKS;
        clearStuckBlock();

        PlasmaEffects.ricochet(loc);
        return true;
    }
}
