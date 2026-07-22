package com.ultimateimprovements.mechanics.security.anticheat.combat;

import com.ultimateimprovements.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovements.mechanics.security.anticheat.core.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * BackTrack — атака с предыдущей позиции (позиционный десинк).
 * Детекция: атака цели, которая находится далеко от текущей позиции игрока.
 */
public class BackTrackCheck extends AbstractCheck {

    private double maxDistance;

    public BackTrackCheck() {
        super("BackTrack", CheckCategory.COMBAT);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxDistance = getConfigDouble("max_distance", 6.0);
    }

    @Override
    public void onReload() { loadConfig(); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player player)) return;
        if (!isEnabled() || isExempted(player)) return;

        PlayerData data = AntiCheatManager.getInstance().getOrCreatePlayerData(player);

        // Compare current position with position history
        if (data.getPositionHistory().size() >= 2) {
            org.bukkit.Location current = player.getLocation();
            org.bukkit.Location historical = data.getPositionHistory().peekFirst();
            if (historical != null) {
                double dist = current.distance(historical);
                if (dist > maxDistance) {
                    CheckResult result = flag(player, 2.0,
                            "Position desync: " + String.format("%.2f", dist) + " blocks");
                    AntiCheatManager.getInstance().handleResult(player, this, result);
                }
            }
        }
    }
}
