package com.ultimateimprovments.database;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.energy.transfer.cable.CableNetwork;
import com.ultimateimprovments.energy.generation.reactor.ReactorManager;
import com.ultimateimprovments.mechanics.environment.magnet.MagnetManager;
import com.ultimateimprovments.mechanics.environment.radiation.RadiationManager;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * AutoSaveManager — автоматическое сохранение всех систем в БД каждые 5 минут.
 * <p>
 * Запускается синхронно на главном серверном потоке.
 * Сохраняет: CableNetwork, ReactorManager, MagnetManager, RadiationManager.
 */
public class AsyncAutoSaveManager extends BukkitRunnable {

    private static AsyncAutoSaveManager instance;

    private static final long SAVE_INTERVAL_TICKS = 6000L; // 5 минут (6000 тиков)

    /**
     * Запускает автоматическое сохранение.
     */
    public static void init(Main plugin) {
        if (instance != null) {
            instance.cancel();
        }
        instance = new AsyncAutoSaveManager();
        // Запускаем с задержкой 5 минут, потом каждые 5 минут
        // Синхронно — предотвращает data race при чтении ReactorManager/RadiationManager из async потока
        instance.runTaskTimer(plugin, SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);
        ConsoleLogger.info("[AutoSave] Auto-save started (every 5 minutes)");
    }

    /**
     * Останавливает автоматическое сохранение.
     */
    public static void shutdown() {
        if (instance != null) {
            instance.cancel();
            instance = null;
        }
    }

    /**
     * Выполняет синхронное сохранение всех систем (вызывается при onDisable).
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
    }

    @Override
    public void run() {
        Main plugin = Main.getInstance();
        if (plugin == null) return;

        plugin.getLogger().fine("[AutoSave] Starting auto-save...");

        // Все save() методы теперь либо no-op (CableNetwork, MagnetManager),
        // либо используют SQLite с busy_timeout=5000 (ReactorManager, RadiationManager).
        // На синхронном потоке BUSY практически невозможен — нет конкурентных писателей.
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

        plugin.getLogger().fine("[AutoSave] Auto-save complete.");
    }
}
