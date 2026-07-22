package com.ultimateimprovements.mechanics.security.anticheat.movement;

import com.ultimateimprovements.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovements.mechanics.security.anticheat.core.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Speed — ускоренное передвижение.
 * <p>
 * Детекция:
 * 1. Дистанция за тик превышает максимум (walk/sprint/ice/roads).
 * 2. Спринт-прыжок БЕЗ увеличения высоты — хак "ground-speed".
 *    Если игрок спринтит, шлёт прыжок (yDelta > 0), но прыжок
 *    НАСТОЛЬКО мал, что не даёт высоты — это хак который разгоняет
 *    игрока в 2.5× без реального прыжка.
 */
public class SpeedCheck extends AbstractCheck {

    private double maxSpeedGround;
    private double maxSpeedAir;
    private double minJumpHeight;
    private double sprintJumpSpeedMul;

    // Последние yDelta для детекции ground-speed
    private final ConcurrentHashMap<UUID, Double> lastYDelta = new ConcurrentHashMap<>();

    public SpeedCheck() {
        super("Speed", CheckCategory.MOVEMENT);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxSpeedGround = getConfigDouble("max_speed_ground", 0.4);
        maxSpeedAir = getConfigDouble("max_speed_air", 0.4);
        minJumpHeight = getConfigDouble("min_jump_height", 0.05);
        sprintJumpSpeedMul = getConfigDouble("sprint_jump_speed_multiplier", 1.3);
    }

    @Override
    public void onReload() { loadConfig(); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;

        double xDelta = e.getTo().getX() - e.getFrom().getX();
        double yDelta = e.getTo().getY() - e.getFrom().getY();
        double zDelta = e.getTo().getZ() - e.getFrom().getZ();
        double horizontalDist = Math.sqrt(xDelta * xDelta + zDelta * zDelta);

        boolean onGround = player.isOnGround();
        double maxSpeed = onGround ? maxSpeedGround : maxSpeedAir;

        // ── Спринт-прыжок без высоты (ground-speed hack) ──
        // Хак: игрок шлёт прыжок (yDelta > 0) но прыжок настолько мал,
        // что это не настоящий прыжок (vanilla jump = 0.42).
        // Результат: сервер даёт спринт-прыжковый буст скорости
        // без фактического прыжка, разгоняя в 2.5× быстрее нормы.
        if (player.isSprinting() && yDelta > 0 && yDelta < minJumpHeight) {
            // Fake jump detected — не даём спринт-буст
            // Флагаем за ground-speed
            double vl = Math.min(3.0, (minJumpHeight - yDelta) * 20.0);
            CheckResult result = flag(player, vl,
                    "Ground-Speed: YΔ=" + String.format("%.3f", yDelta)
                    + " (min jump: " + String.format("%.2f", minJumpHeight) + ")");
            AntiCheatManager.getInstance().handleResult(player, this, result);
            return;
        }

        // Sprinting increases speed (только для реальных прыжков)
        if (player.isSprinting()) maxSpeed *= sprintJumpSpeedMul;

        if (horizontalDist > maxSpeed) {
            double exceed = horizontalDist - maxSpeed;
            double vl = Math.min(5.0, exceed * 10.0);
            CheckResult result = flag(player, vl,
                    "Speed: " + String.format("%.3f", horizontalDist) + " (max: " + String.format("%.3f", maxSpeed) + ")");
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }

        PlayerData data = AntiCheatManager.getInstance().getOrCreatePlayerData(player);
        data.updatePosition(e.getTo(), onGround);
        lastYDelta.put(player.getUniqueId(), yDelta);
    }
}
