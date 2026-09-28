package com.ultimateimprovments.mechanics.features.world;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global lock for timed advancement challenges: only one active challenge
 * per player across all challenge types (woodcutter, ender pearl, ...).
 */
public final class TimedChallengeLock {

    private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();

    private TimedChallengeLock() {}

    /** Tries to claim the challenge slot for a player. Returns false if already busy. */
    public static boolean tryAcquire(UUID uuid) {
        return ACTIVE.add(uuid);
    }

    /** Releases the challenge slot for a player. */
    public static void release(UUID uuid) {
        ACTIVE.remove(uuid);
    }
}
