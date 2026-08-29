package com.ultimateimprovments.util;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/**
 * Snapshots and restores a player's movement/attribute state.
 * <p>
 * Freeze systems (auth, anti-cheat check, leash, ...) historically set
 * {@code walkSpeed=0}/{@code flySpeed=0}/ADVENTURE and either hard-coded the
 * restore (turning a creative player into survival) or forgot it entirely,
 * leaving players unable to move. This helper captures the full pre-freeze
 * state so it can always be restored exactly.
 */
public final class PlayerState {

    private final GameMode gameMode;
    private final float walkSpeed;
    private final float flySpeed;
    private final boolean allowFlight;
    private final boolean flying;
    private final boolean invulnerable;

    private PlayerState(Player player) {
        this.gameMode = player.getGameMode();
        this.walkSpeed = player.getWalkSpeed();
        this.flySpeed = player.getFlySpeed();
        this.allowFlight = player.getAllowFlight();
        this.flying = player.isFlying();
        this.invulnerable = player.isInvulnerable();
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

    /** Restores the captured state. Safe to call if {@code saved} is null (falls back to survival defaults). */
    public static void restore(Player player, PlayerState saved) {
        if (saved == null) {
            player.setGameMode(GameMode.SURVIVAL);
            player.setWalkSpeed(0.2f);
            player.setFlySpeed(0.1f);
            player.setAllowFlight(false);
            player.setFlying(false);
        } else {
            player.setGameMode(saved.gameMode);
            player.setWalkSpeed(saved.walkSpeed);
            player.setFlySpeed(saved.flySpeed);
            player.setAllowFlight(saved.allowFlight);
            player.setFlying(saved.flying);
        }
        player.setInvulnerable(saved != null && saved.invulnerable);
    }

    /** Restores only the walk speed (used by leash when only speed was changed). */
    public static void restoreWalkSpeed(Player player, PlayerState saved) {
        player.setWalkSpeed(saved != null ? saved.walkSpeed : 0.2f);
    }

    public GameMode getGameMode() { return gameMode; }
    public float getWalkSpeed() { return walkSpeed; }
}