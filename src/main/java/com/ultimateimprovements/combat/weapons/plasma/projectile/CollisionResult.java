package com.ultimateimprovements.combat.weapons.plasma.projectile;

import org.bukkit.util.Vector;

public class CollisionResult {
    public final CollisionType type;
    public final double newSpeed;
    public final Vector hitNormal;
    public final Vector moveVector;

    public CollisionResult(CollisionType type, double newSpeed, Vector hitNormal, Vector moveVector) {
        this.type = type;
        this.newSpeed = newSpeed;
        this.hitNormal = hitNormal;
        this.moveVector = moveVector;
    }
}