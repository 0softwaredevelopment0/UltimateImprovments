package com.mcplugin.energy.machines.assembler;

import com.mcplugin.infrastructure.util.LocationUtil;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import org.bukkit.entity.ItemFrame;

import java.util.ArrayList;
import java.util.List;

/**
 * Assembler multiblock structure validation.
 * <p>
 * Structure: CRAFTER + item frame on top face.
 * Center = crafter position.
 */
public class AssemblerStructure {

    // =========================
    // IS VALID
    // =========================
    public static boolean isValid(Location center) {
        return isValid(center, true);
    }

    public static boolean isValid(Location center, boolean requireFrame) {
        if (center == null || center.getWorld() == null) return false;
        Location base = LocationUtil.normalize(center);

        // 1. Crafter at center
        if (base.getBlock().getType() != Material.CRAFTER) return false;

        // 2. Item frame on top of crafter
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

        for (ItemFrame frame : world.getEntitiesByClass(ItemFrame.class)) {
            double dx = Math.abs(frame.getLocation().getX() - targetX);
            double dz = Math.abs(frame.getLocation().getZ() - targetZ);
            double dy = Math.abs(frame.getLocation().getY() - targetY);

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

        for (int x = bx - 2; x <= bx + 2; x++) {
            for (int y = by - 2; y <= by + 2; y++) {
                for (int z = bz - 2; z <= bz + 2; z++) {
                    if (world.getBlockAt(x, y, z).getType() == Material.CRAFTER) {
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
            errors.add("§c[1] Центр структуры = null");
            return errors;
        }
        Location base = LocationUtil.normalize(center);

        // 1. Crafter
        if (base.getBlock().getType() != Material.CRAFTER) {
            errors.add("§6[1] Крафтер §e(0, 0, 0)"
                    + " §7— должен быть CRAFTER на §f["
                    + base.getBlockX() + " " + base.getBlockY() + " " + base.getBlockZ() + "]"
                    + " §7(сейчас: §f" + base.getBlock().getType() + "§7)");
        }

        // 2. Item frame on top
        if (!hasItemFrameOnTop(base)) {
            errors.add("§6[2] Рамка §7— не найдена на верхней грани крафтера §f["
                    + base.getBlockX() + " " + base.getBlockY() + " " + base.getBlockZ() + "]"
                    + "§7. Повесьте рамку НА ВЕРХНЮЮ ГРАНЬ крафтера");
        }

        return errors;
    }
}
