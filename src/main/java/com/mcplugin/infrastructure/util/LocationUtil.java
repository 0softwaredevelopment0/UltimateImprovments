package com.mcplugin.infrastructure.util;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

public class LocationUtil {

    // =========================
    // NORMALIZE (SAFE)
    // =========================
    public static Location normalize(Location loc) {

        if (loc == null || loc.getWorld() == null) {
            return null;
        }

        return new Location(
                loc.getWorld(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ()
        );
    }

    // =========================
    // NEIGHBORS
    // =========================
    public static List<Location> getNeighbors(Location loc) {

        List<Location> list = new ArrayList<>(6);

        if (loc == null || loc.getWorld() == null) {
            return list;
        }

        list.add(new Location(loc.getWorld(), loc.getBlockX() + 1, loc.getBlockY(), loc.getBlockZ()));
        list.add(new Location(loc.getWorld(), loc.getBlockX() - 1, loc.getBlockY(), loc.getBlockZ()));

        list.add(new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY() + 1, loc.getBlockZ()));
        list.add(new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY() - 1, loc.getBlockZ()));

        list.add(new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ() + 1));
        list.add(new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ() - 1));

        return list;
    }

    // =========================
    // CORE FIX: REAL NETWORK RULE
    // =========================
    public static boolean isConnected(Location from, Location to) {

        if (from == null || to == null) {
            return false;
        }

        Location a = normalize(from);
        Location b = normalize(to);

        if (a == null || b == null) {
            return false;
        }

        // distance must be exactly 1 block
        int dx = Math.abs(a.getBlockX() - b.getBlockX());
        int dy = Math.abs(a.getBlockY() - b.getBlockY());
        int dz = Math.abs(a.getBlockZ() - b.getBlockZ());

        return (dx + dy + dz) == 1;
    }

    // =========================
    // STRICT TWO-WAY CHECK
    // =========================
    public static boolean isFullyConnected(Location a, Location b) {

        return isConnected(a, b);
    }
}