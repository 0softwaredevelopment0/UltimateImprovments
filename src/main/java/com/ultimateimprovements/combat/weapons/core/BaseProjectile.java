package com.ultimateimprovements.combat.weapons.core;

public interface BaseProjectile {

    ProjectileType getType();

    void tick();

    boolean isDead();
}