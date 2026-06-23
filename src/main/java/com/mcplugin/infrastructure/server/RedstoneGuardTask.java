package com.mcplugin.infrastructure.server;

import org.bukkit.scheduler.BukkitRunnable;

public class RedstoneGuardTask extends BukkitRunnable {

    @Override
    public void run() {
        RedstoneGuard guard = RedstoneGuard.getInstance();
        if (guard != null) {
            guard.tick();
        }
    }
}
