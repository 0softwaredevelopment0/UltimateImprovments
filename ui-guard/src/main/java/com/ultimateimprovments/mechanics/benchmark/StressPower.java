package com.ultimateimprovments.mechanics.benchmark;

import java.util.Locale;

/**
 * Load multipliers for {@code /ui stresstest start <type> <power>}.
 * <p>
 * Each {@link StressTestType} has its own base work amount per cycle; the
 * multiplier scales it, so {@code minimal} is a light smoke test and
 * {@code max} is deliberately able to push the server into a tick stall.
 */
public enum StressPower {

    MINIMAL("minimal", 1),
    LOW("low", 2),
    MODERATE("moderate", 4),
    HIGH("high", 8),
    MAX("max", 16);

    private final String id;
    private final int multiplier;

    StressPower(String id, int multiplier) {
        this.id = id;
        this.multiplier = multiplier;
    }

    /** Canonical CLI name of the power level (lowercase). */
    public String id() {
        return id;
    }

    /** Load multiplier applied to the type's base work amount. */
    public int multiplier() {
        return multiplier;
    }

    /**
     * Parses a CLI power name (case-insensitive).
     *
     * @return the power level, or {@code null} when unknown
     */
    public static StressPower parse(String raw) {
        if (raw == null) return null;
        String value = raw.toLowerCase(Locale.ROOT).trim();
        for (StressPower power : values()) {
            if (power.id.equals(value)) return power;
        }
        return null;
    }

    /** All canonical power names, for tab-completion. */
    public static String[] ids() {
        StressPower[] powers = values();
        String[] ids = new String[powers.length];
        for (int i = 0; i < powers.length; i++) {
            ids[i] = powers[i].id;
        }
        return ids;
    }
}
