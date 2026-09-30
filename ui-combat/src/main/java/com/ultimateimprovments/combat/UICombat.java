package com.ultimateimprovments.combat;

import com.ultimateimprovments.combat.turret.TurretListener;
import com.ultimateimprovments.combat.turret.TurretManager;
import com.ultimateimprovments.combat.weapons.blazing.BlazingSwordListener;
import com.ultimateimprovments.combat.weapons.electrictrident.ElectricTridentListener;
import com.ultimateimprovments.combat.weapons.plasma.GunListener;
import com.ultimateimprovments.combat.weapons.plasma.PlasmaProjectileTask;
import com.ultimateimprovments.combat.weapons.shoker.ShokerListener;
import com.ultimateimprovments.core.Main;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class UICombat extends JavaPlugin implements com.ultimateimprovments.core.SoftReloadable {

    private static UICombat instance;
    private BukkitTask gunTask;
    private BukkitTask turretTask;

    @Override
    public void onEnable() {
        runStartup();
    }

    @Override
    public void onDisable() {
        runShutdown();
        getLogger().info("UI-Combat disabled!");
        instance = null;
    }

    /**
     * In-place reload (soft /ui reload): full private shutdown + startup path.
     * Never disables the plugin — on Paper that would close the JAR and
     * re-enabling does not reopen it ("zip file closed" zombie).
     */
    @Override
    public void softReload() {
        getLogger().info("UI-Combat soft reload (in place)...");
        runShutdown();
        runStartup();
    }

    private void runStartup() {
        instance = this;
        Main main = Main.getInstance();
        if (main == null) {
            getLogger().severe("UI-Core not loaded! UI-Combat cannot start.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        var pm = getServer().getPluginManager();

        // Register weapon listeners
        pm.registerEvents(new ShokerListener(), main);
        pm.registerEvents(new GunListener(), main);
        pm.registerEvents(new BlazingSwordListener(), main);
        pm.registerEvents(new ElectricTridentListener(), main);

        // Initialize turret system
        TurretManager.init();
        pm.registerEvents(new TurretListener(), main);

        // Turret beam sweep at 2 Hz (vanilla i-frames cap the damage rate anyway).
        turretTask = Bukkit.getScheduler().runTaskTimer(main,
                () -> TurretManager.getInstance().tick(), 10L, 10L);

        // Start plasma projectile cleanup task
        gunTask = new PlasmaProjectileTask().runTaskTimer(main, 1L, 1L);

        getLogger().info("UI-Combat enabled!");
    }

    private void runShutdown() {
        if (turretTask != null) {
            turretTask.cancel();
            turretTask = null;
        }
        if (gunTask != null) {
            gunTask.cancel();
            gunTask = null;
        }
        TurretManager.shutdown();
        org.bukkit.event.HandlerList.unregisterAll(this);
    }

    public static UICombat getInstance() {
        return instance;
    }
}
