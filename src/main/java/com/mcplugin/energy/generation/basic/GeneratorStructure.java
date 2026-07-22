package com.mcplugin.energy.generation.basic;

import com.mcplugin.util.LocationUtil;

import com.mcplugin.util.Materials;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * Generator block validation (single block — no frame required).
 * <p>
 * Block: BLAST_FURNACE with PDC tag.
 * Cable must be connected to an adjacent block (within 1 block).
 */
public class GeneratorStructure {

    // =========================
    // IS VALID
    // =========================
    public static boolean isValid(Location center) {
        if (center == null || center.getWorld() == null) return false;
        Location base = LocationUtil.normalize(center);

        // Just check for blast furnace
        return base.getBlock().getType() == Materials.BLAST_FURNACE;
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
        int bx = near.getBlockX(), by = near.getBlockY(), bz = near.getBlockZ();

        // Scan for blast furnace
        for (int x = bx - 2; x <= bx + 2; x++) {
            for (int y = by - 2; y <= by + 2; y++) {
                for (int z = bz - 2; z <= bz + 2; z++) {
                    if (near.getWorld().getBlockAt(x, y, z).getType() == Materials.BLAST_FURNACE) {
                        Location candidate = new Location(near.getWorld(), x, y, z);
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

        // Just check blast furnace
        if (base.getBlock().getType() != Materials.BLAST_FURNACE) {
            errors.add("§6[1] Blast furnace §e(0, 0, 0)"
                    + " §7— must be BLAST_FURNACE at §f["
                    + base.getBlockX() + " " + base.getBlockY() + " " + base.getBlockZ() + "]"
                    + " §7(current: §f" + base.getBlock().getType() + "§7)");
        }

        return errors;
    }
}
