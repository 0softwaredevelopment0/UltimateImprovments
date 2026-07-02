package com.mcplugin.mechanics.security.anticheat.movement;

import com.mcplugin.mechanics.security.anticheat.AntiCheatManager;
import com.mcplugin.mechanics.security.anticheat.core.*;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Teleport — аномальная телепортация не от сервера.
 * Детекция: мгновенное перемещение на дистанцию больше лимита без причины.
 */
public class TeleportCheck extends AbstractCheck {

    private double maxHorizontalDistance;
    private double maxVerticalDistance;
    private final java.util.Set<UUID> serverTeleportFlags = ConcurrentHashMap.newKeySet();
    private long teleportBypassTicks = 40; // 2 seconds after server teleport

    public TeleportCheck() {
        super("Teleport", CheckCategory.MOVEMENT);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxHorizontalDistance = getConfigDouble("max_horizontal_distance", 20.0);
        maxVerticalDistance = getConfigDouble("max_vertical_distance", 100.0);
    }

    @Override
    public void onReload() {
        loadConfig();
        maxHorizontalDistance = getConfigDouble("max_horizontal_distance", 20.0);
        maxVerticalDistance = getConfigDouble("max_vertical_distance", 100.0);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;
        // Skip movement check while within the server-teleport bypass window
        // (auto-cleaned after teleportBypassTicks via runTaskLater)
        if (serverTeleportFlags.contains(player.getUniqueId())) {
            return;
        }

        Location from = e.getFrom();
        Location to = e.getTo();

        double xDelta = Math.abs(to.getX() - from.getX());
        double zDelta = Math.abs(to.getZ() - from.getZ());
        double yDelta = Math.abs(to.getY() - from.getY());

        double horizontal = Math.sqrt(xDelta * xDelta + zDelta * zDelta);

        if (horizontal > maxHorizontalDistance) {
            CheckResult result = flag(player, 4.0,
                    "Teleport H: " + String.format("%.2f", horizontal) + " blocks (max: " + maxHorizontalDistance + ")");
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }

        if (yDelta > maxVerticalDistance) {
            CheckResult result = flag(player, 4.0,
                    "Teleport V: " + String.format("%.2f", yDelta) + " blocks (max: " + maxVerticalDistance + ")");
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }

        PlayerData data = AntiCheatManager.getInstance().getOrCreatePlayerData(player);
        data.updatePosition(to, player.isOnGround());
    }

    // Mark a player as server-teleported so the next move events are skipped
    @EventHandler(priority = EventPriority.LOWEST)
    public void onTeleport(org.bukkit.event.player.PlayerTeleportEvent e) {
        if (!isEnabled()) return;
        serverTeleportFlags.add(e.getPlayer().getUniqueId());
        // Auto-clean after bypass window
        org.bukkit.Bukkit.getScheduler().runTaskLater(
                com.mcplugin.infrastructure.core.Main.getInstance(),
                () -> serverTeleportFlags.remove(e.getPlayer().getUniqueId()),
                teleportBypassTicks);
    }
}
