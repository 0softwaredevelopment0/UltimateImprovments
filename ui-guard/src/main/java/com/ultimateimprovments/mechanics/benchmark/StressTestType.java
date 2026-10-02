package com.ultimateimprovments.mechanics.benchmark;

import java.util.Locale;

/**
 * Load generators available to {@code /ui stresstest start <type> <power>}.
 * <p>
 * Every type measures a different subsystem of the server, so a full benchmark
 * run is normally done as four separate runs (one per type) at the same power.
 */
public enum StressTestType {

    /** Repeated entity spawning — AI ticking, entity tracking, chunk entity lists. */
    ENTITY("entity"),

    /** Chain block updates: a redstone-dust layer toggled on/off over a platform. */
    BLOCK("block"),

    /** Synchronous chunk loads and unload requests outside the view distance. */
    CHUNK("chunk"),

    /** Spam of iterations over every loaded entity plus spatial queries. */
    SELECTOR("selector");

    private final String id;

    StressTestType(String id) {
        this.id = id;
    }

    /** Canonical CLI name of the type (lowercase). */
    public String id() {
        return id;
    }

    /**
     * Parses a CLI type name (case-insensitive).
     *
     * @return the type, or {@code null} when unknown
     */
    public static StressTestType parse(String raw) {
        if (raw == null) return null;
        String value = raw.toLowerCase(Locale.ROOT).trim();
        for (StressTestType type : values()) {
            if (type.id.equals(value)) return type;
        }
        return null;
    }

    /** All canonical type names, for tab-completion. */
    public static String[] ids() {
        StressTestType[] types = values();
        String[] ids = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            ids[i] = types[i].id;
        }
        return ids;
    }
}
