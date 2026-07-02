package com.mcplugin.mechanics.security.anticheat.combat;

import com.mcplugin.mechanics.security.anticheat.AntiCheatManager;
import com.mcplugin.mechanics.security.anticheat.core.*;
import org.bukkit.GameMode;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ArrowDMG — детекция модифицированных пакетов стрел.
 * <p>
 * Хак: подменяет пакет разгона стрелы, увеличивая её скорость
 * в 2-3+ раза (arrow velocity / arrow damage hack).
 * <p>
 * Детекция:
 * 1. {@link EntityShootBowEvent} — проверяет velocity выпущенной стрелы.
 *    В ванилле: лук на полном заряде даёт velocity ≈ 1.5-3.0.
 *    С чарой Power до 4 добавляет множитель, но не скорость.
 *    Хак может установить velocity > 4.0 → флаг.
 * 2. {@link ProjectileLaunchEvent} — перекрёстная проверка для crossbow.
 * 3. Multi-shot арбалеты (Piercing IV) — 3 стрелы, каждая проверяется отдельно.
 * <p>
 * Exemption: игроки в CREATIVE могут стрелять с любой скоростью.
 */
public class ArrowDMGCheck extends AbstractCheck {

    private double maxArrowSpeed;
    private double maxArrowSpeedCreative;
    private double maxCrossbowSpeed;

    public ArrowDMGCheck() {
        super("ArrowDMG", CheckCategory.COMBAT);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxArrowSpeed = getConfigDouble("max_arrow_speed", 3.2);
        maxArrowSpeedCreative = getConfigDouble("max_arrow_speed_creative", 6.0);
        maxCrossbowSpeed = getConfigDouble("max_crossbow_speed", 3.5);
    }

    @Override
    public void onReload() { loadConfig(); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        if (!isEnabled() || isExempted(player)) return;

        var projectile = e.getProjectile();
        if (!(projectile instanceof AbstractArrow arrow)) return;

        // ── Get arrow velocity ──
        org.bukkit.util.Vector vel = arrow.getVelocity();
        double speed = vel.length();

        // Determine max speed based on weapon type
        double maxSpeed = maxArrowSpeed;
        boolean isCrossbow = e.getBow() != null
                && e.getBow().getType() == org.bukkit.Material.CROSSBOW;
        if (isCrossbow) {
            maxSpeed = maxCrossbowSpeed;
        }

        // Creative mode
        if (player.getGameMode() == GameMode.CREATIVE) {
            maxSpeed = maxArrowSpeedCreative;
        }

        // ── Check velocity ──
        if (speed > maxSpeed) {
            double exceed = speed - maxSpeed;
            double vl = Math.min(10.0, exceed * 5.0);

            String weapon = isCrossbow ? "Crossbow" : "Bow";
            CheckResult result = flag(player, vl,
                    "Arrow speed: " + String.format("%.2f", speed)
                    + " (max: " + String.format("%.2f", maxSpeed)
                    + ", weapon: " + weapon + ")");
            AntiCheatManager.getInstance().handleResult(player, this, result);

            // Cancel the event to prevent the arrow from flying abnormally
            e.setCancelled(true);
        }

        // Register attack data
        PlayerData data = AntiCheatManager.getInstance().getOrCreatePlayerData(player);
        data.registerAttack(speed);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent e) {
        var entity = e.getEntity();
        if (!(entity.getShooter() instanceof Player player)) return;
        if (!isEnabled() || isExempted(player)) return;

        // Only check arrow-like projectiles
        if (!(entity instanceof AbstractArrow arrow)) return;

        // Skip if already processed by EntityShootBowEvent
        // (arrows, crossbow bolts are already checked there)
        // This handles edge cases and direct projectile launches
        org.bukkit.util.Vector vel = arrow.getVelocity();
        double speed = vel.length();

        double maxSpeed = maxArrowSpeed;
        if (player.getGameMode() == GameMode.CREATIVE) {
            maxSpeed = maxArrowSpeedCreative;
        }

        if (speed > maxSpeed) {
            double vl = Math.min(5.0, (speed - maxSpeed) * 3.0);
            CheckResult result = flag(player, vl,
                    "Projectile speed: " + String.format("%.2f", speed)
                    + " (max: " + String.format("%.2f", maxSpeed) + ")");
            AntiCheatManager.getInstance().handleResult(player, this, result);
            e.setCancelled(true);
        }
    }
}
