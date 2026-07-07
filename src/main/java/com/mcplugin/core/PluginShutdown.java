package com.mcplugin.core;

import com.mcplugin.module.ModuleManager;
import com.mcplugin.whitelist.OpWhitelistManager;
import com.mcplugin.display.TabManager;
import com.mcplugin.util.ConsoleLogger;
import com.mcplugin.mechanics.security.check.CheckManager;
import com.mcplugin.structure.StructureChunkTracker;
import org.bukkit.event.HandlerList;

/**
 * PluginShutdown — матрёшка остановки MC-Plugin.
 * <p>
 * Вызывается из {@link Main#onDisable()}.
 * Останавливает модули, сохраняет данные, чистит состояние — в правильном порядке.
 */
public class PluginShutdown {

    private final Main plugin;
    private boolean shutdownPerformed = false;

    public PluginShutdown(Main plugin) {
        this.plugin = plugin;
    }

    // ==========================================================================
    // 🛑 SHUTDOWN — корень матрёшки
    // ==========================================================================

    public void shutdownPlugin() {
        // Guard: предотвращает двойной shutdown (от /mp reload + PlugMan onDisable)
        if (shutdownPerformed) {
            ConsoleLogger.info("[Shutdown] Already performed, skipping.");
            return;
        }
        shutdownPerformed = true;

        ConsoleLogger.info("");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("  MC-Plugin — Shutting down...");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("");

        shutdownModules();
        savePersistentData();
        stopBackgroundTasks();
        cleanupPluginState();

        ConsoleLogger.info("[PLUGIN] Disabled");
    }

    // ==========================================================================
    // 📦 ФАЗА 1: ОТКЛЮЧЕНИЕ МОДУЛЕЙ
    // ==========================================================================

    private void shutdownModules() {
        var mm = ModuleManager.getInstance();
        if (mm != null) {
            mm.shutdownAll();
        }
        ConsoleLogger.info("[Shutdown] Modules shut down.");
    }

    // ==========================================================================
    // 💾 ФАЗА 2: СОХРАНЕНИЕ ДАННЫХ
    // ==========================================================================

    private void savePersistentData() {
        StructureChunkTracker.save();
        ConsoleLogger.info("[Shutdown] Persistent data saved.");
    }

    // ==========================================================================
    // ⏹ ФАЗА 3: ОСТАНОВКА ФОНОВЫХ ЗАДАЧ
    // ==========================================================================

    private void stopBackgroundTasks() {
        com.mcplugin.server.AccessListCheckTask.stop();
        ConsoleLogger.info("[Shutdown] Background tasks stopped.");
    }

    // ==========================================================================
    // 🧹 ФАЗА 4: ОЧИСТКА СОСТОЯНИЯ
    // ==========================================================================

    private void cleanupPluginState() {
        // Unregister ALL event listeners for this plugin — критически важно для /mp reload
        // иначе при повторном registerEvents() старые листенеры остаются и события срабатывают дважды
        HandlerList.unregisterAll(plugin);

        OpWhitelistManager.shutdown();
        TabManager.resetListenerState();
        CheckManager.shutdown();

        // Сбрасываем флаг startup, чтобы при следующем старте guard не сработал ложно
        PluginStartup.resetStartupFlag();

        ConsoleLogger.info("[Shutdown] Plugin state cleaned up.");
    }
}
