package com.mcplugin.combat.weapons.plasma;

import com.mcplugin.combat.weapons.core.ProjectileManager;
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