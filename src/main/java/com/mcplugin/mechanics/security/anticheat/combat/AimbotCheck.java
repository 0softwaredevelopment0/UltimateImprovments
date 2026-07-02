package com.mcplugin.mechanics.security.anticheat.combat;

import com.mcplugin.mechanics.security.anticheat.AntiCheatManager;
import com.mcplugin.mechanics.security.anticheat.core.*;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Deque;
import java.util.LinkedList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aimbot — автоматическое прицеливание на цель.
 * <p>
 * Три метода детекции:
 * 1. <b>Perfect aim</b> — угол до цели < 3° (слишком идеально)
 * 2. <b>Fast rotation</b> — скорость поворота камеры выше человеческого лимита (> 30°/тик)
 * 3. <b>Movement tracking</b> — если цель ДВИГАЕТСЯ и игрок ДВИГАЕТСЯ, а угол прицела
 *    статистически слишком стабилен (std dev < 0.5°) — это аимбот, человек так не может.
 */
public class AimbotCheck extends AbstractCheck {

    private double maxAngleForPerfect;
    private double maxRotationSpeed;
    private double maxTrackingStdDev;
    private int minTrackingSamples;

    // Angle history per player for tracking analysis
    private final ConcurrentHashMap<UUID, Deque<AttackSample>> attackHistory = new ConcurrentHashMap<>();

    // Store last-known target position for movement detection
    private final ConcurrentHashMap<UUID, Location> lastTargetPositions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Location> lastAttackerPositions = new ConcurrentHashMap<>();

    public AimbotCheck() {
        super("Aimbot", CheckCategory.COMBAT);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxAngleForPerfect = getConfigDouble("max_angle_for_perfect", 3.0);
        maxRotationSpeed = getConfigDouble("max_rotation_speed", 30.0);
        maxTrackingStdDev = getConfigDouble("max_tracking_stddev", 0.5);
        minTrackingSamples = getConfigInt("min_tracking_samples", 5);
    }

    @Override
    public void onReload() {
        loadConfig();
        maxAngleForPerfect = getConfigDouble("max_angle_for_perfect", 3.0);
        maxRotationSpeed = getConfigDouble("max_rotation_speed", 30.0);
        maxTrackingStdDev = getConfigDouble("max_tracking_stddev", 0.5);
        minTrackingSamples = getConfigInt("min_tracking_samples", 5);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player player)) return;
        if (!isEnabled() || isExempted(player)) return;
        if (!(e.getEntity() instanceof LivingEntity target)) return;

        PlayerData data = AntiCheatManager.getInstance().getOrCreatePlayerData(player);
        UUID uuid = player.getUniqueId();

        double angle = getAngleToEntity(player, target);
        Location eyeLoc = player.getEyeLocation();

        // ── Determine if both are moving ──
        Location prevTargetPos = lastTargetPositions.get(uuid);
        Location prevAttackerPos = lastAttackerPositions.get(uuid);
        Location currTargetPos = target.getLocation();
        Location currAttackerPos = player.getLocation();

        boolean targetMoving = prevTargetPos != null
                && prevTargetPos.distanceSquared(currTargetPos) > 0.01;
        boolean attackerMoving = prevAttackerPos != null
                && prevAttackerPos.distanceSquared(currAttackerPos) > 0.01;

        lastTargetPositions.put(uuid, currTargetPos.clone());
        lastAttackerPositions.put(uuid, currAttackerPos.clone());

        // ── 1. Perfect aim check ──
        if (angle < maxAngleForPerfect) {
            CheckResult result = flag(player, 1.0,
                    "Perfect aim: " + String.format("%.2f", angle) + "°");
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }

        // ── 2. Fast rotation check (yaw only — pitch history отсутствует) ──
        Deque<Float> yawHistory = data.getYawHistory();
        if (yawHistory.size() >= 2) {
            var it = yawHistory.descendingIterator();
            it.next(); // skip current
            float prevYaw = it.next();
            float curYaw = data.getLastYaw();
            float yawDelta = (float) angleDiff(curYaw, prevYaw);

            if (yawDelta > maxRotationSpeed) {
                CheckResult result = flag(player, 2.0,
                        "Fast rotation: " + String.format("%.1f", yawDelta) + "°/tick");
                AntiCheatManager.getInstance().handleResult(player, this, result);
            }
        }

        // ── 3. Movement tracking consistency (both moving) ──
        if (targetMoving && attackerMoving) {
            Deque<AttackSample> samples = attackHistory.computeIfAbsent(uuid, k -> new LinkedList<>());
            samples.addLast(new AttackSample(angle, System.currentTimeMillis()));
            while (samples.size() > 20) samples.pollFirst();

            if (samples.size() >= minTrackingSamples) {
                double stdDev = computeStdDev(samples);
                if (stdDev < maxTrackingStdDev) {
                    CheckResult result = flag(player, 3.0,
                            "Tracking consistency: stdDev=" + String.format("%.2f", stdDev)
                            + "° over " + samples.size() + " hits (both moving)");
                    AntiCheatManager.getInstance().handleResult(player, this, result);
                }
            }
        } else {
            // Not both moving — reset history to avoid stale data skewing stats
            attackHistory.remove(uuid);
        }
    }

    /**
     * Угол между направлением взгляда игрока и вектором до цели.
     */
    private double getAngleToEntity(Player player, LivingEntity target) {
        Location eye = player.getEyeLocation();
        var direction = eye.getDirection();
        var toTarget = target.getLocation().toVector().subtract(eye.toVector()).normalize();
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, direction.dot(toTarget)))));
    }

    /**
     * Разница углов с учётом перехода через 360°/0°.
     */
    private static double angleDiff(float a, float b) {
        float diff = Math.abs(a - b) % 360;
        if (diff > 180) diff = 360 - diff;
        return diff;
    }

    /**
     * Стандартное отклонение углов в истории атак.
     */
    private static double computeStdDev(Deque<AttackSample> samples) {
        if (samples.isEmpty()) return 0;
        double mean = 0;
        for (AttackSample s : samples) mean += s.angle;
        mean /= samples.size();
        double variance = 0;
        for (AttackSample s : samples) {
            double diff = s.angle - mean;
            variance += diff * diff;
        }
        variance /= samples.size();
        return Math.sqrt(variance);
    }

    /**
     * Снимок атаки: угол до цели + время.
     */
    private static record AttackSample(double angle, long timestamp) {}
}
