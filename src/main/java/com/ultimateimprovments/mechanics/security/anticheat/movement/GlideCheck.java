package com.ultimateimprovments.mechanics.security.anticheat.movement;

import com.ultimateimprovments.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovments.mechanics.security.anticheat.core.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Glide — медленное падение без парашюта/элитры.
 * Детекция: игрок в воздухе, Y уменьшается медленнее, чем гравитация.
 */
public class GlideCheck extends AbstractCheck {

    private double maxGlideSpeed;
    private int minGlideTicks;
    private final ConcurrentHashMap<UUID, Integer> glideTickCounters = new ConcurrentHashMap<>();

    public GlideCheck() {
        super("Glide", CheckCategory.MOVEMENT);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxGlideSpeed = getConfigDouble("max_glide_speed", -0.05);
        minGlideTicks = getConfigInt("min_glide_ticks", 20);
    }

    @Override
    public void onReload() {
        loadConfig();
        maxGlideSpeed = getConfigDouble("max_glide_speed", -0.05);
        minGlideTicks = getConfigInt("min_glide_ticks", 20);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;

        boolean onGround = player.isOnGround();
        if (onGround) {
            glideTickCounters.put(player.getUniqueId(), 0);
            return;
        }

        double yDelta = e.getTo().getY() - e.getFrom().getY();
        int glideTickCount = glideTickCounters.getOrDefault(player.getUniqueId(), 0);

        // Falling but very slowly (gliding)
        if (yDelta < 0 && yDelta > maxGlideSpeed) {
            glideTickCount = glideTickCount + 1;
            glideTickCounters.put(player.getUniqueId(), glideTickCount);
            if (glideTickCount >= minGlideTicks) {
                CheckResult result = flag(player, 3.0,
                        "Gliding: YΔ=" + String.format("%.3f", yDelta) + " for " + glideTickCount + " ticks");
                AntiCheatManager.getInstance().handleResult(player, this, result);
            }
        } else {
            glideTickCount = Math.max(0, glideTickCount - 1);
            glideTickCounters.put(player.getUniqueId(), glideTickCount);
        }

        PlayerData data = AntiCheatManager.getInstance().getOrCreatePlayerData(player);
        data.updatePosition(e.getTo(), onGround);
    }
}
