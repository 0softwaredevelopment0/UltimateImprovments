package com.ultimateimprovments.mechanics.security.codepanel;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Periodic task that cleans expired code panel keys from the DB.
 * Runs every 20 seconds (400 ticks).
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
