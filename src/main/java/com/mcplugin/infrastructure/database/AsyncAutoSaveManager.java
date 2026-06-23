package com.mcplugin.infrastructure.database;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.energy.transfer.cable.CableNetwork;
import com.mcplugin.energy.generation.reactor.ReactorManager;
import com.mcplugin.mechanics.environment.magnet.MagnetManager;
import com.mcplugin.mechanics.environment.radiation.RadiationManager;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * AsyncAutoSaveManager — автоматическое сохранение всех систем в БД каждые 5 минут.
 * <p>
 * Запускается асинхронно (Async) чтобы не лагать сервер.
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
        instance.runTaskTimerAsynchronously(plugin, SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);
        plugin.getLogger().info("[AutoSave] Async auto-save started (every 5 minutes)");
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
            Main.getInstance().getLogger().warning("[AutoSave] CableNetwork save error: " + e.getMessage());
        }

        try {
            ReactorManager.saveAll();
            Main.getInstance().getLogger().finer("[AutoSave] Reactor saved.");
        } catch (Exception e) {
            Main.getInstance().getLogger().warning("[AutoSave] Reactor save error: " + e.getMessage());
        }

        try {
            RadiationManager.saveAll();
            Main.getInstance().getLogger().finer("[AutoSave] Radiation saved.");
        } catch (Exception e) {
            Main.getInstance().getLogger().warning("[AutoSave] Radiation save error: " + e.getMessage());
        }

        try {
            MagnetManager.saveAll();
            Main.getInstance().getLogger().finer("[AutoSave] Magnet saved.");
        } catch (Exception e) {
            Main.getInstance().getLogger().warning("[AutoSave] Magnet save error: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        // Async save — не блокирует основной серверный поток
        Main plugin = Main.getInstance();
        if (plugin == null) return;

        plugin.getLogger().fine("[AutoSave] Starting async auto-save...");

        saveWithRetry("CableNetwork", () -> {
            CableNetwork.save();
            return null;
        }, plugin);

        saveWithRetry("Reactor", () -> {
            ReactorManager.saveAll();
            return null;
        }, plugin);

        saveWithRetry("Radiation", () -> {
            RadiationManager.saveAll();
            return null;
        }, plugin);

        saveWithRetry("Magnet", () -> {
            MagnetManager.saveAll();
            return null;
        }, plugin);

        plugin.getLogger().fine("[AutoSave] Async auto-save complete.");
    }

    /**
     * Выполняет сохранение с повторными попытками при SQLITE_BUSY.
     * SQLite в WAL mode поддерживает конкурентные чтения, но записи
     * могут блокироваться. busy_timeout=5000 помогает, но не всегда.
     */
    private void saveWithRetry(String name, java.util.concurrent.Callable<Void> task, Main plugin) {
        int maxRetries = 5;
        int retryDelay = 1000; // 1 секунда между попытками

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                task.call();
                return; // Успех
            } catch (Exception e) {
                String msg = e.getMessage();
                boolean isBusy = msg != null && (msg.contains("BUSY") || msg.contains("locked") || msg.contains("timeout"));
                
                if (isBusy && attempt < maxRetries) {
                    plugin.getLogger().warning("[AutoSave] " + name + " busy (attempt " + attempt + "/" + maxRetries + "), retrying in " + retryDelay + "ms...");
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                } else {
                    plugin.getLogger().warning("[AutoSave] " + name + " save error: " + msg);
                    return;
                }
            }
        }
    }
}
