package com.mcplugin.mechanics.security.anticheat.movement;

import com.mcplugin.mechanics.security.anticheat.AntiCheatManager;
import com.mcplugin.mechanics.security.anticheat.core.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Timer — клиент отправляет пакеты движения быстрее серверного тикрейта.
 * Детекция: подсчёт пакетов PlayerMove за секунду, превышение лимита.
 */
public class TimerCheck extends AbstractCheck {

    private int maxPacketsPerSecond;
    private long windowMs;

    private final ConcurrentHashMap<java.util.UUID, WindowCounter> counters = new ConcurrentHashMap<>();

    public TimerCheck() {
        super("Timer", CheckCategory.MOVEMENT);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxPacketsPerSecond = getConfigInt("max_packets_per_second", 60);
        windowMs = getConfigInt("window_ms", 1000);
    }

    @Override
    public void onReload() {
        loadConfig();
        maxPacketsPerSecond = getConfigInt("max_packets_per_second", 60);
        windowMs = getConfigInt("window_ms", 1000);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;

        WindowCounter counter = counters.computeIfAbsent(player.getUniqueId(), k -> new WindowCounter());
        int count = counter.incrementAndCount(windowMs);

        if (count > maxPacketsPerSecond) {
            CheckResult result = flag(player, 2.0,
                    "Timer: " + count + " move packets/sec (max: " + maxPacketsPerSecond + ")");
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }
    }

    private static class WindowCounter {
        private long windowStart = System.currentTimeMillis();
        private int count = 0;

        int incrementAndCount(long windowMs) {
            long now = System.currentTimeMillis();
            if (now - windowStart > windowMs) {
                windowStart = now;
                count = 0;
            }
            count++;
            return count;
        }
    }
}
