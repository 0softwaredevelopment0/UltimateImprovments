package com.ultimateimprovements.util;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

public class LocationUtil {

    private LocationUtil() {}

    // =========================
    // LONG KEY ENCODING (world-independent block key)
    // Pack x,y,z (signed 21-bit each) into a single long.
    // Supports ranges: -1,048,576 to 1,048,575 on each axis.
    // =========================
    public static long toKey(int x, int y, int z) {
        return ((long) x & 0x1FFFFF) << 42
             | ((long) y & 0x1FFFFF) << 21
             | ((long) z & 0x1FFFFF);
    }

    public static long toKey(Location loc) {
        if (loc == null) return 0;
        return toKey(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public static int getX(long key) {
        return (int) (key >> 42) | -(int) ((key >> 42) & 0x100000);
    }

    public static int getY(long key) {
        return (int) ((key >> 21) & 0x1FFFFF) | -((int) ((key >> 21) & 0x100000));
    }

    public static int getZ(long key) {
        return (int) (key & 0x1FFFFF) | -((int) (key & 0x100000));
    }

    public static Location toLocation(long key, World world) {
        if (world == null) return null;
        return new Location(world, getX(key), getY(key), getZ(key));
    }

    // =========================
    // NORMALIZE (SAFE) — теперь возвращает тот же объект, если уже нормализован
    // =========================
    public static Location normalize(Location loc) {

        if (loc == null || loc.getWorld() == null) {
            return null;
        }

        // Если уже нормализован — возвращаем как есть (экономия GC)
        if (loc.getX() == loc.getBlockX() && loc.getY() == loc.getBlockY() && loc.getZ() == loc.getBlockZ()) {
            return loc;
        }

        return new Location(
                loc.getWorld(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ()
        );
    }

    // =========================
    // NEIGHBORS (as long keys)
    // =========================
    public static long[] getNeighborKeys(long key) {
        int x = getX(key), y = getY(key), z = getZ(key);
        return new long[]{
            toKey(x + 1, y, z),
            toKey(x - 1, y, z),
            toKey(x, y + 1, z),
            toKey(x, y - 1, z),
            toKey(x, y, z + 1),
            toKey(x, y, z - 1)
        };
    }

    // =========================
    // NEIGHBORS (legacy, creates Location objects)
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
    // KEY-BASED CONNECTION CHECK
    // =========================
    public static boolean isConnected(long keyA, long keyB) {
        if (keyA == 0 || keyB == 0) return false;
        int dx = Math.abs(getX(keyA) - getX(keyB));
        int dy = Math.abs(getY(keyA) - getY(keyB));
        int dz = Math.abs(getZ(keyA) - getZ(keyB));
        return (dx + dy + dz) == 1;
    }

    // =========================
    // STRICT TWO-WAY CHECK
    // =========================
    public static boolean isFullyConnected(Location a, Location b) {

        return isConnected(a, b);
    }

    public static boolean isFullyConnected(long keyA, long keyB) {
        return isConnected(keyA, keyB);
    }
}