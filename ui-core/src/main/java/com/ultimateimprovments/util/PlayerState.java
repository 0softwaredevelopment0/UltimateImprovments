package com.ultimateimprovments.util;

import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.Base64;

/**
 * Snapshots and restores a player's movement/attribute state.
 * <p>
 * Freeze systems (auth, anti-cheat check, leash, ...) historically set
 * {@code walkSpeed=0}/{@code flySpeed=0}/ADVENTURE and either hard-coded the
 * restore (turning a creative player into survival) or forgot it entirely,
 * leaving players unable to move. This helper captures the full pre-freeze
 * state so it can always be restored exactly.
 * <p>
 * The snapshot can be serialized to a compact string (Base64 of its
 * pipe-separated fields) so freeze systems can persist it to the database and
 * survive server restarts — restoring the REAL pre-freeze state instead of
 * accidentally re-freezing the player (the "immortal/zero speed forever" bug).
 */
public final class PlayerState {

    private final GameMode gameMode;
    private final float walkSpeed;
    private final float flySpeed;
    private final boolean allowFlight;
    private final boolean flying;
    private final boolean invulnerable;
    private final float saturation; // food saturation
    private final int foodLevel;

    private PlayerState(Player player) {
        this.gameMode = player.getGameMode();
        this.walkSpeed = player.getWalkSpeed();
        this.flySpeed = player.getFlySpeed();
        this.allowFlight = player.getAllowFlight();
        this.flying = player.isFlying();
        this.invulnerable = player.isInvulnerable();
        this.saturation = player.getSaturation();
        this.foodLevel = player.getFoodLevel();
    }

    private PlayerState(GameMode gameMode, float walkSpeed, float flySpeed,
                        boolean allowFlight, boolean flying, boolean invulnerable,
                        float saturation, int foodLevel) {
        this.gameMode = gameMode;
        this.walkSpeed = walkSpeed;
        this.flySpeed = flySpeed;
        this.allowFlight = allowFlight;
        this.flying = flying;
        this.invulnerable = invulnerable;
        this.saturation = saturation;
        this.foodLevel = foodLevel;
    }

    /** Captures the current state of the player. */
    public static PlayerState capture(Player player) {
        return new PlayerState(player);
    }

    /** Applies a full freeze (no movement, no flight). */
    public static void freeze(Player player) {
        player.setGameMode(GameMode.ADVENTURE);
        player.setWalkSpeed(0.0f);
        player.setFlySpeed(0.0f);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setInvulnerable(true);
    }

    /**
     * Restores the captured state. Safe to call if {@code saved} is null (falls back to survival defaults).
     * Also persists the restored state to player.dat, so the unfreeze survives
     * a crash/restart — the player never relogs into a frozen body.
     */
    public static void restore(Player player, PlayerState saved) {
        if (saved == null) {
            player.setGameMode(GameMode.SURVIVAL);
            player.setWalkSpeed(0.2f);
            player.setFlySpeed(0.1f);
            player.setAllowFlight(false);
            player.setFlying(false);
            // CRITICAL: the fallback MUST clear invulnerability. The old code
            // left whatever the player had — with the freeze always setting
            // invulnerable=true, any path that lost the snapshot left the
            // suspect permanently immortal after the check.
            player.setInvulnerable(false);
        } else {
            player.setGameMode(saved.gameMode);
            player.setWalkSpeed(saved.walkSpeed);
            player.setFlySpeed(saved.flySpeed);
            player.setAllowFlight(saved.allowFlight);
            player.setFlying(saved.flying);
            player.setInvulnerable(saved.invulnerable);
            player.setFoodLevel(saved.foodLevel);
            player.setSaturation(saved.saturation);
        }
        // Persist to player.dat so a crash right after the restore doesn't
        // resurrect the frozen state on the next login.
        try {
            player.saveData();
        } catch (Throwable t) {
            ConsoleLogger.warn("[PlayerState] saveData after restore failed: " + t.getMessage());
        }
    }

    /** Restores only the walk speed (used by leash when only speed was changed). */
    public static void restoreWalkSpeed(Player player, PlayerState saved) {
        player.setWalkSpeed(saved != null ? saved.walkSpeed : 0.2f);
    }

    // =========================
    // SERIALIZATION — persist a snapshot to the DB across restarts
    // =========================

    /**
     * Serializes this snapshot to a compact Base64 string (safe for DB TEXT).
     * Format: gameMode|walk|fly|allowFlight|flying|invulnerable|saturation|food.
     */
    public String serialize() {
        String raw = gameMode.name() + "|" + walkSpeed + "|" + flySpeed + "|"
                + allowFlight + "|" + flying + "|" + invulnerable + "|"
                + saturation + "|" + foodLevel;
        return Base64.getEncoder().encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Deserializes a snapshot produced by {@link #serialize()}, or null if invalid. */
    public static PlayerState deserialize(String data) {
        if (data == null || data.isBlank()) return null;
        try {
            String raw = new String(Base64.getDecoder().decode(data), java.nio.charset.StandardCharsets.UTF_8);
            String[] p = raw.split("\\|");
            if (p.length < 8) return null;
            return new PlayerState(
                    GameMode.valueOf(p[0]),
                    Float.parseFloat(p[1]),
                    Float.parseFloat(p[2]),
                    Boolean.parseBoolean(p[3]),
                    Boolean.parseBoolean(p[4]),
                    Boolean.parseBoolean(p[5]),
                    Float.parseFloat(p[6]),
                    Integer.parseInt(p[7]));
        } catch (Exception e) {
            ConsoleLogger.warn("[PlayerState] Failed to deserialize snapshot: " + e.getMessage());
            return null;
        }
    }

    public GameMode getGameMode() { return gameMode; }
    public float getWalkSpeed() { return walkSpeed; }
    public boolean isInvulnerable() { return invulnerable; }
}