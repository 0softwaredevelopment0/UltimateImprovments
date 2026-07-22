package com.ultimateimprovments.energy.machines.assembler;

import com.ultimateimprovments.util.LocationUtil;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Item Creator structure validation.
 * <p>
 * Structure: CRAFTER block (single block, no item frame required).
 */
public class AssemblerStructure {

    // =========================
    // IS VALID
    // =========================
    public static boolean isValid(Location center) {
        return isValid(center, false);
    }

    public static boolean isValid(Location center, boolean unused) {
        if (center == null || center.getWorld() == null) return false;
        Location base = LocationUtil.normalize(center);

        // Just check that it's a CRAFTER block
        return base.getBlock().getType() == Material.CRAFTER;
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
        var world = near.getWorld();
        int bx = near.getBlockX(), by = near.getBlockY(), bz = near.getBlockZ();

        for (int x = bx - 1; x <= bx + 1; x++) {
            for (int y = by - 1; y <= by + 1; y++) {
                for (int z = bz - 1; z <= bz + 1; z++) {
                    if (world.getBlockAt(x, y, z).getType() == Material.CRAFTER) {
                        return new Location(world, x, y, z);
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
            errors.add("§c[1] Центр структуры = null");
            return errors;
        }
        Location base = LocationUtil.normalize(center);

        if (base.getBlock().getType() != Material.CRAFTER) {
            errors.add("§6[1] Крафтер §e(0, 0, 0)"
                    + " §7— должен быть CRAFTER на §f["
                    + base.getBlockX() + " " + base.getBlockY() + " " + base.getBlockZ() + "]"
                    + " §7(сейчас: §f" + base.getBlock().getType() + "§7)");
        }

        return errors;
    }
}
