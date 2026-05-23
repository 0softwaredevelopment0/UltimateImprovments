package com.mcplugin.guns.projectile;

public interface BaseProjectile {

    ProjectileType getType();

    void tick();

    boolean isDead();
}