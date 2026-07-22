package com.ultimateimprovements.combat.weapons.plasma.projectile;

public class PlasmaConstants {

    // SPEED = ENERGY = DAMAGE
    public static final double MIN_SPEED = 0.1;

    public static final double MAX_SAFE_SPEED = 50.0;

    public static final double INSTABILITY = 0.01;

    /** Шум направления при рикошете от блока (меньше — стабильнее). */
    public static final double BLOCK_RICOCHET_NOISE = 0.04;

    public static final double RICOCHET_SPEED_MULTIPLIER = 1.1;

    /** Минимальная составляющая скорости «от стены» после рикошета. */
    public static final double MIN_OUTWARD_DOT = 0.15;

    /** Тиков без дрейфа после удара о блок. */
    public static final int POST_BLOCK_HIT_STABILITY_TICKS = 5;

    /** Шаг выталкивания (запасная защита). */
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
