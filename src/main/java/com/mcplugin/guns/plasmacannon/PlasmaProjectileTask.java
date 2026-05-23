package com.mcplugin.guns.plasmacannon;

import com.mcplugin.guns.projectile.ProjectileManager;
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