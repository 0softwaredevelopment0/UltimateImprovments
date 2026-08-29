package com.ultimateimprovments.mbs.api;

import org.bukkit.Location;

/**
 * API bridge between UI-MBS and UI-Energy.
 * <p>
 * UI-MBS owns the structure mechanics (e.g. lightning cooking) but must not
 * depend on the energy module. Instead, UI-Energy registers a {@link Consumer}
 * implementation at startup, and structure mechanics consume energy through
 * {@link #consume(Location, int)}. If no consumer is registered (UI-Energy
 * not installed), {@code consume} simply returns {@code false}.
 */
public final class MbsEnergy {

    /**
     * Consumes {@code amount} energy units from the energy network
     * reachable from {@code energyInputLoc} (e.g. a cable connected nearby).
     *
     * @return {@code true} if the energy was available and consumed
     */
    public interface Consumer {
        boolean tryConsume(Location energyInputLoc, int amount);
    }

    private static volatile Consumer consumer;

    private MbsEnergy() {}

    /** Registers the energy consumer (called by UI-Energy on startup). */
    public static void register(Consumer c) {
        consumer = c;
    }

    /** Unregisters the energy consumer (called by UI-Energy on shutdown). */
    public static void unregister() {
        consumer = null;
    }

    /**
     * Attempts to consume {@code amount} energy from the network near
     * {@code energyInputLoc}.
     *
     * @return {@code true} if energy was consumed, {@code false} if unavailable
     */
    public static boolean consume(Location energyInputLoc, int amount) {
        Consumer c = consumer;
        return c != null && c.tryConsume(energyInputLoc, amount);
    }
}
