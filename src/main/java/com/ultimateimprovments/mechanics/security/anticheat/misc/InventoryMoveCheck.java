package com.ultimateimprovments.mechanics.security.anticheat.misc;

import com.ultimateimprovments.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovments.mechanics.security.anticheat.core.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * InventoryMove — движение с открытым инвентарём (невозможно в survival).
 * Детекция: игрок перемещается, при этом его инвентарь открыт.
 */
public class InventoryMoveCheck extends AbstractCheck {

    private double minMoveDistance;
    private final Set<UUID> openInventoryPlayers = ConcurrentHashMap.newKeySet();

    public InventoryMoveCheck() {
        super("InventoryMove", CheckCategory.MISC);
    }

    @Override
    public void onInit() {
        loadConfig();
        minMoveDistance = getConfigDouble("min_move_distance", 0.1);
    }

    @Override
    public void onReload() {
        loadConfig();
        minMoveDistance = getConfigDouble("min_move_distance", 0.1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (e.getPlayer() instanceof Player player) {
            openInventoryPlayers.add(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player player) {
            openInventoryPlayers.remove(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;
        if (!openInventoryPlayers.contains(player.getUniqueId())) return;

        double xDelta = e.getTo().getX() - e.getFrom().getX();
        double zDelta = e.getTo().getZ() - e.getFrom().getZ();
        double horizontal = Math.sqrt(xDelta * xDelta + zDelta * zDelta);

        if (horizontal > minMoveDistance) {
            CheckResult result = flag(player, 1.0,
                    "InventoryMove: moved " + String.format("%.3f", horizontal) + " with open inventory");
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }
    }
}
