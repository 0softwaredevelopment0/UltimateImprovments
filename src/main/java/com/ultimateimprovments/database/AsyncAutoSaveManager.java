package com.ultimateimprovments.database;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.energy.transfer.cable.CableNetwork;
import com.ultimateimprovments.energy.generation.reactor.ReactorManager;
import com.ultimateimprovments.mechanics.environment.magnet.MagnetManager;
import com.ultimateimprovments.mechanics.environment.radiation.RadiationManager;
import com.ultimateimprovments.structure.StructureMarker;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * AutoSaveManager — automatic saving of all systems to the DB every 5 minutes.
 * <p>
 * Runs synchronously on the main server thread.
 * Saves: CableNetwork, ReactorManager, MagnetManager, RadiationManager,
 * and structures (StructureMarker — full re-save of the structure_markers table;
 * the «every 10 minutes» requirement is covered with a margin).
 */
public class AsyncAutoSaveManager extends BukkitRunnable {

    private static AsyncAutoSaveManager instance;

    private static final long SAVE_INTERVAL_TICKS = 6000L; // 5 minutes (6000 ticks)

    /**
     * Starts the automatic saving.
     */
    public static void init(Main plugin) {
        if (instance != null) {
            instance.cancel();
        }
        instance = new AsyncAutoSaveManager();
        // Start with a 5-minute delay, then every 5 minutes
        // Synchronous — prevents data races when reading ReactorManager/RadiationManager from an async thread
        instance.runTaskTimer(plugin, SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);
        ConsoleLogger.info("[AutoSave] Auto-save started (every 5 minutes)");
    }

    /**
     * Stops the automatic saving.
     */
    public static void shutdown() {
        if (instance != null) {
            instance.cancel();
            instance = null;
        }
    }

    /**
     * Performs a synchronous save of all systems (called on onDisable).
     */
    public static void saveAllNow() {
        try {
            CableNetwork.save();
            Main.getInstance().getLogger().finer("[AutoSave] CableNetwork saved.");
        } catch (Exception e) {
            ConsoleLogger.warn("[AutoSave] CableNetwork save error: " + e.getMessage());
        }

        try {
            ReactorManager.saveAll();
            Main.getInstance().getLogger().finer("[AutoSave] Reactor saved.");
        } catch (Exception e) {
            ConsoleLogger.warn("[AutoSave] Reactor save error: " + e.getMessage());
        }

        try {
            RadiationManager.saveAll();
            Main.getInstance().getLogger().finer("[AutoSave] Radiation saved.");
        } catch (Exception e) {
            ConsoleLogger.warn("[AutoSave] Radiation save error: " + e.getMessage());
        }

        try {
            MagnetManager.saveAll();
            Main.getInstance().getLogger().finer("[AutoSave] Magnet saved.");
        } catch (Exception e) {
            ConsoleLogger.warn("[AutoSave] Magnet save error: " + e.getMessage());
        }

        try {
            StructureMarker.saveAll();
            Main.getInstance().getLogger().finer("[AutoSave] Structure markers saved.");
        } catch (Exception e) {
            ConsoleLogger.warn("[AutoSave] Structure markers save error: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        Main plugin = Main.getInstance();
        if (plugin == null) return;

        plugin.getLogger().fine("[AutoSave] Starting auto-save...");

        // All save() methods are now either no-op (CableNetwork, MagnetManager)
        // or use SQLite with busy_timeout=5000 (ReactorManager, RadiationManager).
        // On the synchronous thread BUSY is practically impossible — there are no concurrent writers.
        try { CableNetwork.save(); } catch (Exception e) {
            ConsoleLogger.warn("[AutoSave] CableNetwork error: " + e.getMessage());
        }

        try { ReactorManager.saveAll(); } catch (Exception e) {
            ConsoleLogger.warn("[AutoSave] Reactor error: " + e.getMessage());
        }

        try { RadiationManager.saveAll(); } catch (Exception e) {
            ConsoleLogger.warn("[AutoSave] Radiation error: " + e.getMessage());
        }

        try { MagnetManager.saveAll(); } catch (Exception e) {
            ConsoleLogger.warn("[AutoSave] Magnet error: " + e.getMessage());
        }

        try { StructureMarker.saveAll(); } catch (Exception e) {
            ConsoleLogger.warn("[AutoSave] Structure markers error: " + e.getMessage());
        }

        plugin.getLogger().fine("[AutoSave] Auto-save complete.");
    }
}
