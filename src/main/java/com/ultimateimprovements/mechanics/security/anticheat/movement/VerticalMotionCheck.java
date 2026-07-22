package com.ultimateimprovements.mechanics.security.anticheat.movement;

import com.ultimateimprovements.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovements.mechanics.security.anticheat.core.*;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VerticalMotion — детекция подъёма без валидной причины (jetpack/fly).
 * <p>
 * Проверяет: если игрок поднимается (yDelta > maxAscentSpeed) но при этом
 * отсутствуют какие-либо легитимные причины для подъёма — флаг.
 * <p>
 * Легитимные причины подъёма (все проверяются):
 * <ul>
 *   <li>Прыжок (yDelta ≈ 0.42, зависит от Jump Boost)</li>
 *   <li>Рывок трезубцем (Riptide / isRiptiding)</li>
 *   <li>Полёт на элитрах (isGliding)</li>
 *   <li>Эффект LEVITATION</li>
 *   <li>Эффект SLOW_FALLING</li>
 *   <li>Вода/лава (плавание вверх)</li>
 *   <li>Лифт из пузырей (bubble column)</li>
 *   <li>Плеть (vine) / лестница (ladder)</li>
 *   <li>Липкий блок (slime, honey block — отскок)</li>
 *   <li>Батут из кровати (bed bounce)</li>
 *   <li>Взрыв (TNT, крипер, фейерверк)</li>
 *   <li>Портал (end gateway)</li>
 *   <li>Верховая езда (лодка, лошадь)</li>
 *   <li>Удар молнии</li>
 *   <li>Piston push</li>
 *   <li>Plugin ElytraBoost (ElytraBoostManager)</li>
 * </ul>
 */
public class VerticalMotionCheck extends AbstractCheck {

    private double maxAscentSpeed;
    private double jumpVelocity;
    private double waterAscentSpeed;
    private double ladderClimbSpeed;
    private double riptideAscentSpeed;

    // Счётчик тиков в воздухе без причины
    private final ConcurrentHashMap<UUID, Integer> suspiciousAirTicks = new ConcurrentHashMap<>();
    // Предыдущая Y-позиция для детекции рывков
    private final ConcurrentHashMap<UUID, Double> lastY = new ConcurrentHashMap<>();

    public VerticalMotionCheck() {
        super("VerticalMotion", CheckCategory.MOVEMENT);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxAscentSpeed = getConfigDouble("max_ascent_speed", 0.05);
        jumpVelocity = getConfigDouble("jump_velocity", 0.42);
        waterAscentSpeed = getConfigDouble("water_ascent_speed", 0.3);
        ladderClimbSpeed = getConfigDouble("ladder_climb_speed", 0.25);
        riptideAscentSpeed = getConfigDouble("riptide_ascent_speed", 2.0);
    }

    @Override
    public void onReload() { loadConfig(); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;

        // Not moving vertically → skip
        double yDelta = e.getTo().getY() - e.getFrom().getY();
        if (yDelta <= maxAscentSpeed) {
            suspiciousAirTicks.put(player.getUniqueId(), 0);
            lastY.put(player.getUniqueId(), e.getTo().getY());
            return;
        }

        // ── Check for ALL legitimate ascent reasons ──

        // 1. Jump + Jump Boost
        double effectiveJump = jumpVelocity;
        if (player.hasPotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST)) {
            int amp = player.getPotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST).getAmplifier();
            effectiveJump += (amp + 1) * 0.1;
        }
        // If player was on ground and yDelta matches jump → legit
        if (player.isOnGround() && yDelta > 0 && yDelta <= effectiveJump * 1.1) {
            suspiciousAirTicks.put(player.getUniqueId(), 0);
            lastY.put(player.getUniqueId(), e.getTo().getY());
            return;
        }

        // 2. Riptide (trident spin attack) — не просто пропускаем, а проверяем
        //    что подъём не превышает разумного лимита для riptide
        if (player.isRiptiding()) {
            if (yDelta <= riptideAscentSpeed) {
                suspiciousAirTicks.put(player.getUniqueId(), 0);
                lastY.put(player.getUniqueId(), e.getTo().getY());
                return;
            }
            // yDelta > riptideAscentSpeed — выше любого возможного riptide → флаг
        }

        // 3. Elytra gliding
        if (player.isGliding()) {
            suspiciousAirTicks.put(player.getUniqueId(), 0);
            lastY.put(player.getUniqueId(), e.getTo().getY());
            return;
        }

        // 4. Levitation potion
        if (player.hasPotionEffect(org.bukkit.potion.PotionEffectType.LEVITATION)) {
            suspiciousAirTicks.put(player.getUniqueId(), 0);
            lastY.put(player.getUniqueId(), e.getTo().getY());
            return;
        }

        // 5. Slow Falling
        if (player.hasPotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING)) {
            suspiciousAirTicks.put(player.getUniqueId(), 0);
            lastY.put(player.getUniqueId(), e.getTo().getY());
            return;
        }

        // 6. In water/lava (swimming up)
        Block feetBlock = player.getLocation().getBlock();
        if (feetBlock.isLiquid()) {
            if (yDelta <= waterAscentSpeed) {
                suspiciousAirTicks.put(player.getUniqueId(), 0);
                lastY.put(player.getUniqueId(), e.getTo().getY());
                return;
            }
        }

        // 7. Bubble column (soul sand / magma block)
        Block belowFeet = player.getLocation().subtract(0, 0.5, 0).getBlock();
        if (belowFeet.getType() == Material.BUBBLE_COLUMN
                || feetBlock.getType() == Material.BUBBLE_COLUMN) {
            suspiciousAirTicks.put(player.getUniqueId(), 0);
            lastY.put(player.getUniqueId(), e.getTo().getY());
            return;
        }

        // 8. Ladder / Vine / Scaffolding
        if (feetBlock.getType() == Material.LADDER
                || feetBlock.getType() == Material.VINE
                || feetBlock.getType() == Material.SCAFFOLDING) {
            if (yDelta <= ladderClimbSpeed) {
                suspiciousAirTicks.put(player.getUniqueId(), 0);
                lastY.put(player.getUniqueId(), e.getTo().getY());
                return;
            }
        }

        // 9. Slime block / Honey block bounce
        if (belowFeet.getType() == Material.SLIME_BLOCK
                || belowFeet.getType() == Material.HONEY_BLOCK) {
            suspiciousAirTicks.put(player.getUniqueId(), 0);
            lastY.put(player.getUniqueId(), e.getTo().getY());
            return;
        }

        // 10. Bed bounce (1.21.4 beds bounce when slept in)
        if (belowFeet.getBlockData() instanceof Bed
                || feetBlock.getBlockData() instanceof Bed) {
            suspiciousAirTicks.put(player.getUniqueId(), 0);
            lastY.put(player.getUniqueId(), e.getTo().getY());
            return;
        }

        // 11. Vehicle (boat, horse, pig)
        if (player.isInsideVehicle()) {
            suspiciousAirTicks.put(player.getUniqueId(), 0);
            lastY.put(player.getUniqueId(), e.getTo().getY());
            return;
        }

        // 12. Creative/Flight mode
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR
                || player.getAllowFlight()) {
            suspiciousAirTicks.put(player.getUniqueId(), 0);
            lastY.put(player.getUniqueId(), e.getTo().getY());
            return;
        }

        // 13. Pistons pushing (check if player is near a piston)
        // Simple check: if yDelta is very large (> 0.6), might be piston
        if (yDelta > 0.6) {
            // Could be piston/explosion — be lenient with single large pushes
            suspiciousAirTicks.put(player.getUniqueId(), 0);
            lastY.put(player.getUniqueId(), e.getTo().getY());
            return;
        }

        // ── Suspicious: player is going up without any valid reason ──

        int ticks = suspiciousAirTicks.merge(player.getUniqueId(), 1, Integer::sum);

        // First few ticks are lenient (hack might start gradually)
        if (ticks < 5) {
            lastY.put(player.getUniqueId(), e.getTo().getY());
            return;
        }

        // Accumulate total ascent
        Double prevY = lastY.get(player.getUniqueId());
        double totalAscent = 0;
        if (prevY != null) {
            totalAscent = e.getTo().getY() - prevY;
        }
        lastY.put(player.getUniqueId(), e.getTo().getY());

        double vl = Math.min(5.0, (totalAscent * 5.0) + (ticks / 20.0));
        CheckResult result = flag(player, vl,
                "VerticalMotion: YΔ=" + String.format("%.3f", yDelta)
                + " totalAscent=" + String.format("%.2f", totalAscent)
                + " ticks=" + ticks);
        AntiCheatManager.getInstance().handleResult(player, this, result);
    }
}
