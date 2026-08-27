package com.ultimateimprovments.combat.weapons.plasma.projectile;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

public class PlasmaEffects {

    private PlasmaEffects() {}

    public static void plasmaTrail(Location loc) {

        loc.getWorld().spawnParticle(
                Particle.DUST,
                loc,
                1,
                new Particle.DustOptions(
                        Color.AQUA,
                        2.0f
                )
        );

        loc.getWorld().spawnParticle(
                Particle.ELECTRIC_SPARK,
                loc,
                2,
                0.1,
                0.1,
                0.1,
                0
        );
    }

    public static void ricochet(Location loc) {

        loc.getWorld().playSound(
                loc,
                Sound.BLOCK_AMETHYST_BLOCK_HIT,
                1f,
                1.5f
        );

        loc.getWorld().spawnParticle(
                Particle.ELECTRIC_SPARK,
                loc,
                10
        );
    }

    public static void entityHit(Entity e) {

        e.getWorld().spawnParticle(
                Particle.EXPLOSION,
                e.getLocation(),
                2
        );

        e.getWorld().playSound(
                e.getLocation(),
                Sound.ENTITY_PLAYER_ATTACK_NODAMAGE,
                0.8f,
                2f
        );
    }

    public static void penetration(Entity e) {

        e.getWorld().spawnParticle(
                Particle.ELECTRIC_SPARK,
                e.getLocation(),
                20,
                0.3,
                0.3,
                0.3,
                0.1
        );

        e.getWorld().playSound(
                e.getLocation(),
                Sound.ENTITY_GENERIC_EXPLODE,
                0.7f,
                1.8f
        );
    }

    public static void unbreakable(Block block) {

        block.getWorld().playSound(
                block.getLocation(),
                Sound.BLOCK_ANVIL_PLACE,
                1f,
                0.7f
        );

        block.getWorld().spawnParticle(
                Particle.CRIT,
                block.getLocation().add(
                        0.5,
                        0.5,
                        0.5
                ),
                15
        );
    }

    public static void blockBreak(Block block) {

        block.getWorld().playSound(
                block.getLocation(),
                Sound.BLOCK_STONE_BREAK,
                1f,
                1.2f
        );

        block.getWorld().spawnParticle(
                Particle.BLOCK,
                block.getLocation().add(
                        0.5,
                        0.5,
                        0.5
                ),
                20,
                0.3,
                0.3,
                0.3,
                block.getBlockData()
        );
    }

    public static void explode(Location loc) {

        loc.getWorld().spawnParticle(
                Particle.EXPLOSION_EMITTER,
                loc,
                1
        );

        loc.getWorld().playSound(
                loc,
                Sound.ENTITY_PLAYER_ATTACK_NODAMAGE,
                2f,
                2f
        );
    }
}