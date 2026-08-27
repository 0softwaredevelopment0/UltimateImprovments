package com.ultimateimprovments.mechanics.security.anticheat.combat;

import com.ultimateimprovments.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovments.mechanics.security.anticheat.core.*;
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
 * Aimbot — automatic aim at a target.
 * <p>
 * Three detection methods:
 * 1. <b>Perfect aim</b> — angle to the target < 3° (too perfect)
 * 2. <b>Fast rotation</b> — camera rotation speed above the human limit (> 30°/tick)
 * 3. <b>Movement tracking</b> — if the target MOVES and the player MOVES, but the aim
 *    angle is statistically too stable (std dev < 0.5°) — that's an aimbot, a human can't do that.
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
        // Cross-world damage (portals, Multiverse worlds) — geometry/angle checks are meaningless here
        if (!player.getWorld().equals(target.getWorld())) return;

        PlayerData data = AntiCheatManager.getInstance().getOrCreatePlayerData(player);
        UUID uuid = player.getUniqueId();

        double angle = getAngleToEntity(player, target);
        Location eyeLoc = player.getEyeLocation();

        // ── Determine if both are moving ──
        Location prevTargetPos = lastTargetPositions.get(uuid);
        Location prevAttackerPos = lastAttackerPositions.get(uuid);
        Location currTargetPos = target.getLocation();
        Location currAttackerPos = player.getLocation();

        boolean targetMoving = hasMoved(prevTargetPos, currTargetPos);
        boolean attackerMoving = hasMoved(prevAttackerPos, currAttackerPos);

        lastTargetPositions.put(uuid, currTargetPos.clone());
        lastAttackerPositions.put(uuid, currAttackerPos.clone());

        // ── 1. Perfect aim check ──
        if (angle < maxAngleForPerfect) {
            CheckResult result = flag(player, 1.0,
                    "Perfect aim: " + String.format("%.2f", angle) + "°");
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }

        // ── 2. Fast rotation check (yaw only — no pitch history) ──
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
     * True if the entity moved between two ticks — worlds must match, otherwise
     * distanceSquared throws IllegalArgumentException.
     */
    private static boolean hasMoved(Location from, Location to) {
        if (from == null || to == null) return false;
        if (!from.getWorld().equals(to.getWorld())) return false;
        return from.distanceSquared(to) > 0.01;
    }

    /**
     * Angle between the player's look direction and the vector to the target.
     */
    private double getAngleToEntity(Player player, LivingEntity target) {
        Location eye = player.getEyeLocation();
        var direction = eye.getDirection();
        var toTarget = target.getLocation().toVector().subtract(eye.toVector()).normalize();
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, direction.dot(toTarget)))));
    }

    /**
     * Angle difference accounting for the 360°/0° wrap.
     */
    private static double angleDiff(float a, float b) {
        float diff = Math.abs(a - b) % 360;
        if (diff > 180) diff = 360 - diff;
        return diff;
    }

    /**
     * Standard deviation of angles in the attack history.
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
     * Attack snapshot: angle to the target + time.
     */
    private static record AttackSample(double angle, long timestamp) {}
}
