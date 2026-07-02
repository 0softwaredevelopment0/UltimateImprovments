package com.mcplugin.mechanics.security.codepanel;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.ConsoleLogger;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Периодическая задача очистки просроченных ключей кодовой панели из БД.
 * Запускается раз в 20 секунд (400 тиков).
 */
public class CodePanelCleanupTask extends BukkitRunnable {

    @Override
    public void run() {
        if (Main.getInstance() == null) return;
        try {
            CodePanelDatabase.cleanupExpiredKeys();
        } catch (Exception e) {
            ConsoleLogger.warn(
                    "[CodePanel] Cleanup task error: " + e.getMessage()
            );
        }
    }
}
