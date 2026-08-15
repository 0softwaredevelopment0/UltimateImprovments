package com.ultimateimprovments.mechanics.security.anticheat.misc;

import com.ultimateimprovments.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovments.mechanics.security.anticheat.core.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AutoRespawn — instant respawn (0ms delay after death).
 * Detection: time between death and respawn is below the minimum (human reaction).
 */
public class AutoRespawnCheck extends AbstractCheck {

    private long minRespawnDelayMs;
    private final ConcurrentHashMap<UUID, Long> deathTimes = new ConcurrentHashMap<>();

    public AutoRespawnCheck() {
        super("AutoRespawn", CheckCategory.MISC);
    }

    @Override
    public void onInit() {
        loadConfig();
        minRespawnDelayMs = getConfigInt("min_respawn_delay_ms", 100);
    }

    @Override
    public void onReload() {
        loadConfig();
        minRespawnDelayMs = getConfigInt("min_respawn_delay_ms", 100);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        deathTimes.put(e.getEntity().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;

        Long deathTime = deathTimes.remove(player.getUniqueId());
        if (deathTime == null) return;

        long elapsed = System.currentTimeMillis() - deathTime;
        if (elapsed < minRespawnDelayMs) {
            CheckResult result = flag(player, 1.5,
                    "AutoRespawn: " + elapsed + "ms after death (min: " + minRespawnDelayMs + "ms)");
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }
    }
}
