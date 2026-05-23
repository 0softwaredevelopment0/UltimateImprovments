package com.mcplugin.guns.projectile;

import com.mcplugin.guns.projectile.BaseProjectile;
import com.mcplugin.guns.projectile.ProjectileType;

public class ShokerProjectile implements BaseProjectile {

    private boolean dead = false;

    @Override
    public ProjectileType getType() {
        return ProjectileType.SHOCKER;
    }

    @Override
    public void tick() {
        // beam logic here
    }

    @Override
    public boolean isDead() {
        return dead;
    }

    public void kill() {
        dead = true;
    }
}