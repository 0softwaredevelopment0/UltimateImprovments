package com.ultimateimprovments.combat.weapons.plasma.projectile;

public class PlasmaConstants {

    // SPEED = ENERGY = DAMAGE
    public static final double MIN_SPEED = 0.1;

    public static final double MAX_SAFE_SPEED = 50.0;

    public static final double INSTABILITY = 0.01;

    /** Direction noise on block ricochet (lower — more stable). */
    public static final double BLOCK_RICOCHET_NOISE = 0.04;

    public static final double RICOCHET_SPEED_MULTIPLIER = 1.1;

    /** Minimum "away from the wall" velocity component after a ricochet. */
    public static final double MIN_OUTWARD_DOT = 0.15;

    /** Ticks without drift after hitting a block. */
    public static final int POST_BLOCK_HIT_STABILITY_TICKS = 5;

    /** Push-out step (fallback safeguard). */
    public static final double ESCAPE_STEP = 0.5;

    public static final double MAX_ESCAPE_DISTANCE = 2.0;

    public static final int MAX_ESCAPE_BLOCKS = 4;

    public static final int STUCK_BLOCK_TICKS = 6;

    public static final int OWNER_IMMUNITY_TICKS = 40;

    public static final int MAX_LIFE = 1200;

    public static double clampSpeed(double speed) {
        return Math.max(MIN_SPEED, speed);
    }

    private PlasmaConstants() {}
}
