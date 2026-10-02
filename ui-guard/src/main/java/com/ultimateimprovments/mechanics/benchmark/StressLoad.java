package com.ultimateimprovments.mechanics.benchmark;

import org.bukkit.Location;

/**
 * One load generator of a stress-test run. Lifecycle (all on the main thread):
 * <ol>
 *   <li>{@link #start(Location)} — prepare (snapshot, validate, build); may
 *       throw, the manager then reports the failure and rolls back;</li>
 *   <li>{@link #tick()} — one load cycle, called every
 *       {@code stresstest.interval_ticks};</li>
 *   <li>{@link #stop()} — clean up and restore everything the test touched;
 *       must be idempotent and safe to call even when {@link #start(Location)}
 *       never ran.</li>
 * </ol>
 * {@link #work()} reports the amount of load actually produced, so the final
 * benchmark report stays meaningful even when part of the load was rejected
 * (spawn caps, unloaded chunks, ...).
 */
public interface StressLoad {

    /** Prepares the generator around the given anchor location. */
    void start(Location anchor) throws Exception;

    /** Produces one load cycle. */
    void tick() throws Exception;

    /** Stops the generator and restores every world change it made. */
    void stop();

    /** @return load units produced since the run started */
    long work();
}
