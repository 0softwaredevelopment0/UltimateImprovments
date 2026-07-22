package com.ultimateimprovements.mechanics.environment.magnet;

import org.bukkit.Location;

/**
 * Tracks active magnet structures.
 * Delegates to MagnetManager for the actual tracking.
 */
public class MagnetStructure {

    // =========================
    // IS ACTIVE
    // =========================
    public static boolean isActive(Location loc) {
        return MagnetManager.isActiveAt(loc);
    }
}
