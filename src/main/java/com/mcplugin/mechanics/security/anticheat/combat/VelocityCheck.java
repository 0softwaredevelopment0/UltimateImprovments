package com.mcplugin.mechanics.security.anticheat.combat;

import com.mcplugin.mechanics.security.anticheat.AntiCheatManager;
import com.mcplugin.mechanics.security.anticheat.core.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.util.Vector;

/**
 * Velocity / AntiKnockback — игрок игнорирует полученный knockback.
 * Детекция: сервер отправляет velocity, но игрок не двигается в ожидаемом направлении.
 */
public class VelocityCheck extends AbstractCheck {

    private double minVelocityThreshold;
    private double minExpectedMovement;

    public VelocityCheck() {
        super("Velocity", CheckCategory.COMBAT);
    }

    @Override
    public void onInit() {
        loadConfig();
        minVelocityThreshold = getConfigDouble("min_velocity_threshold", 0.1);
        minExpectedMovement = getConfigDouble("min_expected_movement", 0.05);
    }

    @Override
    public void onReload() { loadConfig(); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVelocity(PlayerVelocityEvent e) {
        if (!isEnabled() || isExempted(e.getPlayer())) return;

        Player player = e.getPlayer();
        Vector vel = e.getVelocity();
        if (vel.length() < minVelocityThreshold) return;

        PlayerData data = AntiCheatManager.getInstance().getOrCreatePlayerData(player);
        data.setPendingVelocity(vel.clone());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        // Knockback is applied after attack — velocity event fires
        if (!(e.getEntity() instanceof Player player)) return;
        if (!isEnabled() || isExempted(player)) return;

        PlayerData data = AntiCheatManager.getInstance().getOrCreatePlayerData(player);
        Vector pending = data.getPendingVelocity();

        if (pending != null && pending.length() > minVelocityThreshold) {
            // Check on next tick if player moved in expected direction
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                    com.mcplugin.core.Main.getInstance(), () -> {
                        if (!player.isOnline()) return;
                        PlayerData d = AntiCheatManager.getInstance().getOrCreatePlayerData(player);
                        Vector actual = player.getVelocity();
                        double expectedLen = pending.length();
                        double actualLen = actual.length();

                        if (actualLen < expectedLen * minExpectedMovement) {
                            CheckResult result = flag(player, 2.0,
                                    "Velocity reduced: expected=" + String.format("%.2f", expectedLen)
                                            + " actual=" + String.format("%.2f", actualLen));
                            AntiCheatManager.getInstance().handleResult(player, this, result);
                        }
                        d.clearPendingVelocity();
                    }, 1L);
        }
    }
}
