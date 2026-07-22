package com.ultimateimprovements.combat.weapons.plasma;

import com.ultimateimprovements.combat.weapons.core.ProjectileManager;
import org.bukkit.scheduler.BukkitRunnable;

public class PlasmaProjectileTask extends BukkitRunnable {

    @Override
    public void run() {

        // =========================
        // CENTRAL PROJECTILE ENGINE
        // =========================
        ProjectileManager.tickAll();
    }
}