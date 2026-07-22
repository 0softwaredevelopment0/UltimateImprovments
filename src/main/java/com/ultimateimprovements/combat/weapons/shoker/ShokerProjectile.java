package com.ultimateimprovements.combat.weapons.shoker;

import com.ultimateimprovements.combat.weapons.core.BaseProjectile;
import com.ultimateimprovements.combat.weapons.core.ProjectileManager;
import com.ultimateimprovements.combat.weapons.core.ProjectileType;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ShokerProjectile implements BaseProjectile {

    private static final double SPEED = 1.2;

    private final Player shooter;

    private Location loc;
    private Vector dir;

    private boolean hitBlock = false;
    private int blockFreezeTicks = 0;

    private final Set<UUID> hitEntities = new HashSet<>();

    private boolean dead = false;

    public ShokerProjectile(Player shooter) {

        this.shooter = shooter;
        this.loc = shooter.getEyeLocation().clone();
        this.dir = shooter.getEyeLocation().getDirection().normalize();
    }

    // =========================
    // SPAWN METHOD (FIX)
    // =========================
    public static void spawn(Player shooter) {
        ProjectileManager.add(new ShokerProjectile(shooter));
    }

    @Override
    public ProjectileType getType() {
        return ProjectileType.SHOCKER;
    }

    @Override
    public boolean isDead() {
        return dead;
    }

    @Override
    public void tick() {

        if (dead) return;

        // =========================
        // FREEZE AFTER BLOCK HIT
        // =========================
        if (hitBlock) {

            if (++blockFreezeTicks >= 20) {
                dead = true;
            }

            return;
        }

        // =========================
        // MOVE BEAM
        // =========================
        loc.add(dir.clone().multiply(SPEED));

        // =========================
        // BLOCK HIT
        // =========================
        if (!loc.getBlock().getType().isAir()) {

            hitBlock = true;

            playHit(loc);
            return;
        }

        // =========================
        // ENTITY HIT
        // =========================
        for (Entity e : loc.getWorld().getNearbyEntities(loc, 0.6, 0.6, 0.6)) {

            if (e instanceof Player p && p.equals(shooter)) continue;
            if (hitEntities.contains(e.getUniqueId())) continue;

            hitEntities.add(e.getUniqueId());

            if (e instanceof LivingEntity le) {

                le.damage(6, shooter);

                le.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS,
                        60,
                        255,
                        true,
                        false
                ));

                le.addPotionEffect(new PotionEffect(
                        PotionEffectType.LEVITATION,
                        20,
                        0,
                        true,
                        false
                ));

                loc.getWorld().spawnParticle(
                        Particle.ELECTRIC_SPARK,
                        loc,
                        25, 0.2, 0.2, 0.2, 0
                );

                loc.getWorld().playSound(
                        loc,
                        Sound.BLOCK_BEACON_ACTIVATE,
                        1f,
                        2f
                );
            }
        }

        // =========================
        // TRAIL
        // =========================
        loc.getWorld().spawnParticle(
                Particle.ELECTRIC_SPARK,
                loc,
                5, 0, 0, 0, 0
        );
    }

    private void playHit(Location loc) {

        loc.getWorld().spawnParticle(
                Particle.EXPLOSION,
                loc,
                1
        );

        loc.getWorld().playSound(
                loc,
                Sound.ENTITY_PLAYER_ATTACK_NODAMAGE,
                1f,
                2f
        );
    }
}