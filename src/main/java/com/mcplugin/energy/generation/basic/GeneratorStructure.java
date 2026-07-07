package com.mcplugin.energy.generation.basic;

import com.mcplugin.util.LocationUtil;

import com.mcplugin.util.Materials;
import org.bukkit.Location;
import org.bukkit.World;

import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;

import java.util.ArrayList;
import java.util.List;

/**
 * Generator multiblock structure validation.
 * <p>
 * Structure: BLAST_FURNACE + item frame on top face.
 * Center = blast furnace position.
 * Cable must be connected to an adjacent block (within 1 block).
 */
public class GeneratorStructure {

    // =========================
    // IS VALID
    // =========================
    public static boolean isValid(Location center) {
        return isValid(center, true);
    }

    public static boolean isValid(Location center, boolean requireFrame) {
        if (center == null || center.getWorld() == null) return false;
        Location base = LocationUtil.normalize(center);

        // 1. Blast furnace at center
        if (base.getBlock().getType() != Materials.BLAST_FURNACE) return false;

        // 2. Item frame on top of blast furnace
        if (requireFrame && !hasItemFrameOnTop(base)) return false;

        return true;
    }

    // =========================
    // HAS ITEM FRAME ON TOP
    // =========================
    private static boolean hasItemFrameOnTop(Location center) {
        World world = center.getWorld();
        if (world == null) return false;

        double targetX = center.getX() + 0.5;
        double targetY = center.getY() + 1.0;
        double targetZ = center.getZ() + 0.5;

        // Ищем рамки только среди ItemFrame — гораздо быстрее чем world.getEntities()
        for (ItemFrame frame : world.getEntitiesByClass(ItemFrame.class)) {

            double dx = Math.abs(frame.getLocation().getX() - targetX);
            double dz = Math.abs(frame.getLocation().getZ() - targetZ);
            double dy = Math.abs(frame.getLocation().getY() - targetY);

            // Position check only — getAttachedFace is unreliable across Paper versions
            // Tolerance: x/z ±0.6, y ±0.6 (covers all possible frame entity Y positions)
            if (dx < 0.6 && dz < 0.6 && dy < 0.6) {
                return true;
            }
        }
        return false;
    }

    // =========================
    // FIND STRUCTURE CENTER
    // =========================
    public static Location findCenter(Location near) {
        if (near == null || near.getWorld() == null) return null;
        Location center = locateCenter(near);
        if (center != null && isValid(center)) return center;
        return null;
    }

    public static Location locateCenter(Location near) {
        if (near == null || near.getWorld() == null) return null;
        World world = near.getWorld();
        int bx = near.getBlockX(), by = near.getBlockY(), bz = near.getBlockZ();

        // Scan for blast furnace, then verify item frame on top
        for (int x = bx - 2; x <= bx + 2; x++) {
            for (int y = by - 2; y <= by + 2; y++) {
                for (int z = bz - 2; z <= bz + 2; z++) {
                    if (world.getBlockAt(x, y, z).getType() == Materials.BLAST_FURNACE) {
                        Location candidate = new Location(world, x, y, z);
                        if (isValid(candidate)) {
                            return candidate;
                        }
                    }
                }
            }
        }
        return null;
    }

    // =========================
    // VALIDATION ERRORS
    // =========================
    public static List<String> getValidationErrors(Location center) {
        List<String> errors = new ArrayList<>();
        if (center == null || center.getWorld() == null) {
            errors.add("§c[1] Structure center = null");
            return errors;
        }
        Location base = LocationUtil.normalize(center);

        // 1. Blast furnace
        if (base.getBlock().getType() != Materials.BLAST_FURNACE) {
            errors.add("§6[1] Blast furnace §e(0, 0, 0)"
                    + " §7— must be BLAST_FURNACE at §f["
                    + base.getBlockX() + " " + base.getBlockY() + " " + base.getBlockZ() + "]"
                    + " §7(current: §f" + base.getBlock().getType() + "§7)");
        }

        // 2. Item frame on top
        if (!hasItemFrameOnTop(base)) {
            errors.add("§6[2] Item frame §7— not found on top of blast furnace §f["
                    + base.getBlockX() + " " + base.getBlockY() + " " + base.getBlockZ() + "]"
                    + "§7. Place the item frame ON THE TOP FACE of the blast furnace");
        }

        return errors;
    }
}
