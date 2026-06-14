package com.mcplugin.cp;

import com.mcplugin.Main;
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
            Main.getInstance().getLogger().warning(
                    "[CodePanel] Cleanup task error: " + e.getMessage()
            );
        }
    }
}
