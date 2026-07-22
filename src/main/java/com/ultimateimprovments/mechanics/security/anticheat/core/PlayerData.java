package com.ultimateimprovments.mechanics.security.anticheat.core;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Данные игрока для античита.
 * <p>
 * Хранит историю позиций, кликов, ротации, скорости и Violation Level для каждой проверки.
 * Потокобезопасен — используется из main thread и async.
 */
public class PlayerData {

    private final UUID uuid;
    private final String name;

    // ── Position history (последние 20 позиций — ~1 секунда) ──
    private final Deque<Location> positionHistory = new ConcurrentLinkedDeque<>();
    private volatile Location lastLocation;
    private volatile Location lastGroundLocation;
    private volatile boolean wasOnGround;
    private volatile long lastMoveTime;

    // ── Rotation ──
    private volatile float lastYaw;
    private volatile float lastPitch;
    private volatile long lastRotationTime;
    private final Deque<Float> yawHistory = new ConcurrentLinkedDeque<>();

    // ── Combat (CPS, attacks) ──
    private final Deque<Long> clickTimes = new ConcurrentLinkedDeque<>();
    private final AtomicInteger attacksThisSecond = new AtomicInteger(0);
    private volatile long lastAttackTime;
    private volatile double lastAttackDistance;

    // ── Velocity / Knockback ──
    private volatile Vector pendingVelocity;
    private volatile long velocityTime;

    // ── Violation Levels per check ──
    private final ConcurrentHashMap<String, Double> violationLevels = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastFlagTime = new ConcurrentHashMap<>();

    // ── Misc state ──
    private volatile long lastBlockPlaceTime;
    private volatile long lastBlockBreakTime;
    private volatile int blocksPlacedThisSecond;
    private volatile int blocksBrokenThisSecond;
    private volatile long secondResetTime;

    // ── Exemption ──
    private volatile boolean exempted;

    public PlayerData(Player player) {
        this.uuid = player.getUniqueId();
        this.name = player.getName();
        this.lastLocation = player.getLocation();
        this.lastGroundLocation = player.getLocation();
        this.lastYaw = player.getLocation().getYaw();
        this.lastPitch = player.getLocation().getPitch();
    }

    // =========================
    // POSITION
    // =========================

    public void updatePosition(Location newLoc, boolean onGround) {
        if (lastLocation != null) {
            positionHistory.addLast(lastLocation.clone());
            while (positionHistory.size() > 20) positionHistory.pollFirst();
        }
        lastLocation = newLoc.clone();
        wasOnGround = onGround;
        if (onGround) lastGroundLocation = newLoc.clone();
        lastMoveTime = System.currentTimeMillis();
    }

    public Location getLastLocation() { return lastLocation; }
    public Location getLastGroundLocation() { return lastGroundLocation; }
    public boolean wasOnGround() { return wasOnGround; }
    public Deque<Location> getPositionHistory() { return positionHistory; }

    // =========================
    // ROTATION
    // =========================

    public void updateRotation(float yaw, float pitch) {
        yawHistory.addLast(yaw);
        while (yawHistory.size() > 20) yawHistory.pollFirst();
        lastYaw = yaw;
        lastPitch = pitch;
        lastRotationTime = System.currentTimeMillis();
    }

    public float getLastYaw() { return lastYaw; }
    public float getLastPitch() { return lastPitch; }
    public Deque<Float> getYawHistory() { return yawHistory; }

    // =========================
    // COMBAT / CPS
    // =========================

    public void registerClick() {
        long now = System.currentTimeMillis();
        clickTimes.addLast(now);
        // Remove clicks older than 1 second
        while (!clickTimes.isEmpty() && now - clickTimes.peekFirst() > 1000) {
            clickTimes.pollFirst();
        }
    }

    public int getCps() {
        long now = System.currentTimeMillis();
        while (!clickTimes.isEmpty() && now - clickTimes.peekFirst() > 1000) {
            clickTimes.pollFirst();
        }
        return clickTimes.size();
    }

    public void registerAttack(double distance) {
        attacksThisSecond.incrementAndGet();
        lastAttackTime = System.currentTimeMillis();
        lastAttackDistance = distance;
    }

    public int getAttacksThisSecond() { return attacksThisSecond.get(); }
    public void resetAttacksThisSecond() { attacksThisSecond.set(0); }
    public long getLastAttackTime() { return lastAttackTime; }
    public double getLastAttackDistance() { return lastAttackDistance; }

    // =========================
    // VELOCITY
    // =========================

    public void setPendingVelocity(Vector velocity) {
        this.pendingVelocity = velocity;
        this.velocityTime = System.currentTimeMillis();
    }

    public Vector getPendingVelocity() { return pendingVelocity; }
    public long getVelocityTime() { return velocityTime; }
    public void clearPendingVelocity() { pendingVelocity = null; }

    // =========================
    // BLOCK ACTIONS
    // =========================

    public void registerBlockPlace() {
        resetSecondIfNeeded();
        blocksPlacedThisSecond++;
        lastBlockPlaceTime = System.currentTimeMillis();
    }

    public void registerBlockBreak() {
        resetSecondIfNeeded();
        blocksBrokenThisSecond++;
        lastBlockBreakTime = System.currentTimeMillis();
    }

    public int getBlocksPlacedThisSecond() { resetSecondIfNeeded(); return blocksPlacedThisSecond; }
    public int getBlocksBrokenThisSecond() { resetSecondIfNeeded(); return blocksBrokenThisSecond; }
    public long getLastBlockPlaceTime() { return lastBlockPlaceTime; }
    public long getLastBlockBreakTime() { return lastBlockBreakTime; }

    private void resetSecondIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - secondResetTime > 1000) {
            blocksPlacedThisSecond = 0;
            blocksBrokenThisSecond = 0;
            attacksThisSecond.set(0);
            secondResetTime = now;
        }
    }

    // =========================
    // VIOLATION LEVELS
    // =========================

    public double getVl(String checkName) {
        return violationLevels.getOrDefault(checkName, 0.0);
    }

    public void addVl(String checkName, double amount) {
        violationLevels.merge(checkName, amount, Double::sum);
        lastFlagTime.put(checkName, System.currentTimeMillis());
    }

    public void setVl(String checkName, double vl) {
        violationLevels.put(checkName, vl);
        lastFlagTime.put(checkName, System.currentTimeMillis());
    }

    public void decayVl(String checkName, double decayAmount) {
        violationLevels.compute(checkName, (key, val) -> {
            double current = val == null ? 0.0 : val;
            return Math.max(0, current - decayAmount);
        });
    }

    public long getLastFlagTime(String checkName) {
        return lastFlagTime.getOrDefault(checkName, 0L);
    }

    public ConcurrentHashMap<String, Double> getAllVl() { return violationLevels; }

    // =========================
    // EXEMPTION
    // =========================

    public boolean isExempted() { return exempted; }
    public void setExempted(boolean exempted) { this.exempted = exempted; }

    // =========================
    // IDENTITY
    // =========================

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
}
