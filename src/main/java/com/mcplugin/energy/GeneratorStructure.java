package com.mcplugin.energy;

import com.mcplugin.util.LocationUtil;

import org.bukkit.Location;
import org.bukkit.Material;
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
        if (base.getBlock().getType() != Material.BLAST_FURNACE) return false;

        // 2. Item frame on top of blast furnace
        if (requireFrame && !hasItemFrameOnTop(base)) return false;

        return true;
    }

    // =========================
    // HAS ITEM FRAME ON TOP
    // =========================
    private static boolean hasItemFrameOnTop(Location center) {
        // Search ONLY in the block DIRECTLY ABOVE the furnace (Y = centerY + 1)
        // Center of that block: (centerX+0.5, centerY+1.5, centerZ+0.5)
        // Y half-size = 0.5 → range [centerY+1.0, centerY+2.0] — only the block above
        Location frameArea = center.clone().add(0.5, 1.5, 0.5);
        World world = center.getWorld();
        if (world == null) return false;

        for (Entity entity : world.getNearbyEntities(
                frameArea, 0.5, 0.5, 0.5,
                e -> e instanceof ItemFrame)) {
            ItemFrame frame = (ItemFrame) entity;
            // Frame on TOP face of furnace → attachedFace = DOWN (mounted on block below)
            if (frame.getAttachedFace() == org.bukkit.block.BlockFace.DOWN) {
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
                    if (world.getBlockAt(x, y, z).getType() == Material.BLAST_FURNACE) {
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

        // 1. Blast furnace
        if (base.getBlock().getType() != Material.BLAST_FURNACE) {
            errors.add("§6[1] Плавильная печь §e(0, 0, 0)"
                    + " §7— должна быть BLAST_FURNACE на §f["
                    + base.getBlockX() + " " + base.getBlockY() + " " + base.getBlockZ() + "]"
                    + " §7(сейчас: §f" + base.getBlock().getType() + "§7)");
        }

        // 2. Item frame on top
        if (!hasItemFrameOnTop(base)) {
            errors.add("§6[2] Рамка §7— не найдена на верхней грани плавильной печи §f["
                    + base.getBlockX() + " " + base.getBlockY() + " " + base.getBlockZ() + "]"
                    + "§7. Повесьте рамку НА ВЕРХНЮЮ ГРАНЬ плавильной печи");
        }

        return errors;
    }
}
