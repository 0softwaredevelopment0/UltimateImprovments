package com.ultimateimprovments.combat.weapons.plasma;

import com.ultimateimprovments.combat.weapons.core.ProjectileManager;
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