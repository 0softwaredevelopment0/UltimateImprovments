package com.mcplugin.combat.weapons.core;

import com.mcplugin.combat.weapons.core.BaseProjectile;
import com.mcplugin.combat.weapons.core.ProjectileType;

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