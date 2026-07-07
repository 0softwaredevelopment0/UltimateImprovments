package com.mcplugin.mechanics.environment.lightning;

import com.mcplugin.util.LocationUtil;

import com.mcplugin.util.Materials;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightning Structure validation (NBT-based, 5x4x5).
 * <p>
 * Structure layout (relative to center = lightning rod):
 * <pre>
 *   Y= 0 : rod at (0,0,0)
 *   Y=-1 : bulb (0,-1,0) + 4 trapdoors around
 *   Y=-2 : grate (0,-2,0) + 4 stairs + 4 corner trapdoors
 *   Y=-3 : chiseled (0,-3,0) + 4 cut_copper cross + 4 copper_block corners + 4 edge trapdoors
 * </pre>
 * Bounds: X=-2..+2, Y=-3..0, Z=-2..+2
 * <p>
 * The item frame goes ON THE TOP FACE of the lightning rod (center block).
 */
public class LightningStructure {

    // =========================
    // BLOCK POSITIONS (relative to center = lightning rod)
    // =========================

    /** Lightning rod at the very top (center itself) */
    private static final int[] ROD = {0, 0, 0};

    // ── Y = -1 : bulb + 4 trapdoors ──
    private static final int[] BULB = {0, -1, 0};
    private static final int[][] TRAPDOORS_INNER = {
        { 0, -1, -1}, {-1, -1,  0}, { 1, -1,  0}, { 0, -1,  1},
    };

    // ── Y = -2 : grate + 4 stairs + 4 corner trapdoors ──
    private static final int[] GRATE = {0, -2, 0};
    private static final int[][] STAIRS = {
        { 0, -2, -1}, {-1, -2,  0}, { 1, -2,  0}, { 0, -2,  1},
    };
    private static final int[][] TRAPDOORS_MID = {
        {-1, -2, -1}, { 1, -2, -1}, {-1, -2,  1}, { 1, -2,  1},
    };

    // ── Y = -3 : chiseled + cross cut_copper + corner copper_block + 4 edge trapdoors ──
    private static final int[] CHISELED = {0, -3, 0};
    private static final int[][] CUT_COPPER_CROSS = {
        {-1, -3,  0}, { 1, -3,  0}, { 0, -3, -1}, { 0, -3,  1},
    };
    private static final int[][] COPPER_BLOCK_CORNERS = {
        {-1, -3, -1}, { 1, -3, -1}, {-1, -3,  1}, { 1, -3,  1},
    };
    private static final int[][] TRAPDOORS_OUTER = {
        { 0, -3, -2}, {-2, -3,  0}, { 2, -3,  0}, { 0, -3,  2},
    };

    // =========================
    // ENERGY INPUT LOCATION (WAXED_CHISELED_COPPER at 0, -3, 0 from center)
    // Сюда должен быть подведён кабель с энергией 100 за операцию.
    // =========================
    public static Location getEnergyInputLoc(Location center) {
        if (center == null) return null;
        return center.clone().add(0, -3, 0);
    }

    // =========================
    // IS WAXED COPPER (any variant)
    // =========================
    private static boolean isWaxedCopper(Material mat) {
        return mat == Materials.WAXED_COPPER_BLOCK
            || mat == Materials.WAXED_CUT_COPPER
            || mat == Materials.WAXED_CHISELED_COPPER
            || mat == Materials.WAXED_COPPER_GRATE
            || mat == Materials.WAXED_COPPER_BULB
            || mat == Materials.WAXED_COPPER_TRAPDOOR
            || mat == Materials.WAXED_CUT_COPPER_STAIRS
            || mat == Materials.WAXED_LIGHTNING_ROD;
    }

    // =========================
    // GET BLOCK
    // =========================
    private static Material getBlock(Location base, int dx, int dy, int dz) {
        return base.clone().add(dx, dy, dz).getBlock().getType();
    }

    private static boolean isBlock(Location base, int[] pos, Material expected) {
        return getBlock(base, pos[0], pos[1], pos[2]) == expected;
    }

    // =========================
    // HAS ITEM FRAME ON TOP (frame placed on TOP face of the rod)
    // =========================
    private static boolean hasItemFrameOnTop(Location center) {
        // Search ONLY in the block DIRECTLY ABOVE the rod (Y = centerY + 1)
        // Center of that block: (centerX+0.5, centerY+1.5, centerZ+0.5)
        // Y half-size = 0.5 → range [centerY+1.0, centerY+2.0] — only the block above
        Location frameArea = center.clone().add(0.5, 1.5, 0.5);
        for (Entity entity : center.getWorld().getNearbyEntities(
                frameArea, 0.5, 0.5, 0.5,
                e -> e instanceof ItemFrame)) {
            ItemFrame frame = (ItemFrame) entity;
            // Frame on TOP face of rod → attachedFace = DOWN (mounted on block below)
            if (frame.getAttachedFace() == org.bukkit.block.BlockFace.DOWN) {
                return true;
            }
        }
        return false;
    }

    // =========================
    // IS VALID
    // =========================
    public static boolean isValid(Location center) {
        return isValid(center, true);
    }

    public static boolean isValid(Location center, boolean requireFrame) {
        if (center == null || center.getWorld() == null) return false;
        Location base = LocationUtil.normalize(center);

        // 1. Lightning rod at center
        if (!isBlock(base, ROD, Materials.WAXED_LIGHTNING_ROD)) return false;

        // 2. Bulb at Y=-1
        if (!isBlock(base, BULB, Materials.WAXED_COPPER_BULB)) return false;

        // 3. Inner trapdoors at Y=-1 (any orientation)
        for (int[] pos : TRAPDOORS_INNER) {
            if (!isBlock(base, pos, Materials.WAXED_COPPER_TRAPDOOR)) return false;
        }

        // 4. Grate at Y=-2
        if (!isBlock(base, GRATE, Materials.WAXED_COPPER_GRATE)) return false;

        // 5. Stairs at Y=-2 (any orientation)
        for (int[] pos : STAIRS) {
            if (!isBlock(base, pos, Materials.WAXED_CUT_COPPER_STAIRS)) return false;
        }

        // 6. Mid trapdoors at Y=-2 (any orientation)
        for (int[] pos : TRAPDOORS_MID) {
            if (!isBlock(base, pos, Materials.WAXED_COPPER_TRAPDOOR)) return false;
        }

        // 7. Chiseled at Y=-3
        if (!isBlock(base, CHISELED, Materials.WAXED_CHISELED_COPPER)) return false;

        // 8. Cut copper cross at Y=-3
        for (int[] pos : CUT_COPPER_CROSS) {
            if (!isBlock(base, pos, Materials.WAXED_CUT_COPPER)) return false;
        }

        // 9. Copper block corners at Y=-3
        for (int[] pos : COPPER_BLOCK_CORNERS) {
            if (!isBlock(base, pos, Materials.WAXED_COPPER_BLOCK)) return false;
        }

        // 10. Outer trapdoors at Y=-3 (any orientation)
        for (int[] pos : TRAPDOORS_OUTER) {
            if (!isBlock(base, pos, Materials.WAXED_COPPER_TRAPDOOR)) return false;
        }

        // 11. Item frame on top of rod
        if (requireFrame && !hasItemFrameOnTop(base)) return false;

        return true;
    }

    // =========================
    // FIND STRUCTURE CENTER (lightning rod)
    // =========================
    public static Location findCenter(Location near) {
        if (near == null || near.getWorld() == null) return null;
        Location center = locateCenter(near);
        if (center != null && isValid(center)) return center;
        return null;
    }

    public static Location locateCenter(Location near) {
        if (near == null || near.getWorld() == null) return null;
        Location base = LocationUtil.normalize(near);
        World world = base.getWorld();
        int bx = base.getBlockX(), by = base.getBlockY(), bz = base.getBlockZ();

        // Scan for lightning rod, then verify chiseled copper 3 blocks below
        for (int x = bx - 3; x <= bx + 3; x++) {
            for (int y = by - 1; y <= by + 3; y++) {
                for (int z = bz - 3; z <= bz + 3; z++) {
                    if (world.getBlockAt(x, y, z).getType() == Materials.WAXED_LIGHTNING_ROD) {
                        // Quick confirm: chiseled copper at (x, y-3, z)
                        if (world.getBlockAt(x, y - 3, z).getType()
                                == Materials.WAXED_CHISELED_COPPER) {
                            return new Location(world, x, y, z);
                        }
                    }
                }
            }
        }
        return null;
    }

    // =========================
    // IS PART OF STRUCTURE
    // =========================
    public static boolean isPartOfStructure(Location center, Location checkLoc) {
        if (center == null || checkLoc == null) return false;
        if (!center.getWorld().equals(checkLoc.getWorld())) return false;

        int dx = checkLoc.getBlockX() - center.getBlockX();
        int dy = checkLoc.getBlockY() - center.getBlockY();
        int dz = checkLoc.getBlockZ() - center.getBlockZ();

        // Structure bounds: X=-2..2, Y=-3..0, Z=-2..2
        return dx >= -2 && dx <= 2 && dy >= -3 && dy <= 0 && dz >= -2 && dz <= 2;
    }

    // =========================
    // DETAILED VALIDATION ERRORS
    // =========================
    public static List<String> getValidationErrors(Location center) {
        List<String> errors = new ArrayList<>();
        if (center == null || center.getWorld() == null) {
            errors.add("§c[1] Центр структуры = null");
            return errors;
        }
        Location base = LocationUtil.normalize(center);

        // 1. Lightning rod
        if (!isBlock(base, ROD, Materials.WAXED_LIGHTNING_ROD)) {
            errors.add("§6[1] Громоотвод §e(0, 0, 0) §7— должен быть WAXED_LIGHTNING_ROD на §f["
                + (base.getBlockX()) + " " + base.getBlockY() + " " + base.getBlockZ() + "]");
        }

        // 2. Bulb
        if (!isBlock(base, BULB, Materials.WAXED_COPPER_BULB)) {
            errors.add("§6[2] Медная лампочка §e(0, -1, 0)"
                + " §7— должна быть WAXED_COPPER_BULB на §f["
                + (base.getBlockX()) + " " + (base.getBlockY() - 1) + " " + base.getBlockZ() + "]"
                + " §7(сейчас: §f" + getBlock(base, BULB[0], BULB[1], BULB[2]) + "§7)");
        }

        // 3. Inner trapdoors
        int innerTrapIssues = 0;
        for (int[] pos : TRAPDOORS_INNER) {
            if (!isBlock(base, pos, Materials.WAXED_COPPER_TRAPDOOR)) innerTrapIssues++;
        }
        if (innerTrapIssues > 0) {
            errors.add("§6[3] Люки вокруг лампочки §eY=-1"
                + " §7— " + innerTrapIssues + " не WAXED_COPPER_TRAPDOOR");
        }

        // 4. Grate
        if (!isBlock(base, GRATE, Materials.WAXED_COPPER_GRATE)) {
            errors.add("§6[4] Медная решётка §e(0, -2, 0)"
                + " §7— должна быть WAXED_COPPER_GRATE на §f["
                + (base.getBlockX()) + " " + (base.getBlockY() - 2) + " " + base.getBlockZ() + "]"
                + " §7(сейчас: §f" + getBlock(base, GRATE[0], GRATE[1], GRATE[2]) + "§7)");
        }

        // 5. Stairs
        int stairIssues = 0;
        for (int[] pos : STAIRS) {
            if (!isBlock(base, pos, Materials.WAXED_CUT_COPPER_STAIRS)) stairIssues++;
        }
        if (stairIssues > 0) {
            errors.add("§6[5] Ступени вокруг решётки §eY=-2"
                + " §7— " + stairIssues + " не WAXED_CUT_COPPER_STAIRS");
        }

        // 6. Mid trapdoors
        int midTrapIssues = 0;
        for (int[] pos : TRAPDOORS_MID) {
            if (!isBlock(base, pos, Materials.WAXED_COPPER_TRAPDOOR)) midTrapIssues++;
        }
        if (midTrapIssues > 0) {
            errors.add("§6[6] Угловые люки §eY=-2"
                + " §7— " + midTrapIssues + " не WAXED_COPPER_TRAPDOOR");
        }

        // 7. Chiseled
        if (!isBlock(base, CHISELED, Materials.WAXED_CHISELED_COPPER)) {
            errors.add("§6[7] Резная медь §e(0, -3, 0)"
                + " §7— должна быть WAXED_CHISELED_COPPER на §f["
                + (base.getBlockX()) + " " + (base.getBlockY() - 3) + " " + base.getBlockZ() + "]"
                + " §7(сейчас: §f" + getBlock(base, CHISELED[0], CHISELED[1], CHISELED[2]) + "§7)");
        }

        // 8. Cut copper cross
        int cutIssues = 0;
        for (int[] pos : CUT_COPPER_CROSS) {
            if (!isBlock(base, pos, Materials.WAXED_CUT_COPPER)) cutIssues++;
        }
        if (cutIssues > 0) {
            errors.add("§6[8] Резной медный крест §eY=-3"
                + " §7— " + cutIssues + " не WAXED_CUT_COPPER");
        }

        // 9. Copper block corners
        int cornerIssues = 0;
        for (int[] pos : COPPER_BLOCK_CORNERS) {
            if (!isBlock(base, pos, Materials.WAXED_COPPER_BLOCK)) cornerIssues++;
        }
        if (cornerIssues > 0) {
            errors.add("§6[9] Угловые медные блоки §eY=-3"
                + " §7— " + cornerIssues + " не WAXED_COPPER_BLOCK");
        }

        // 10. Outer trapdoors
        int outerTrapIssues = 0;
        for (int[] pos : TRAPDOORS_OUTER) {
            if (!isBlock(base, pos, Materials.WAXED_COPPER_TRAPDOOR)) outerTrapIssues++;
        }
        if (outerTrapIssues > 0) {
            errors.add("§6[10] Боковые люки §eY=-3"
                + " §7— " + outerTrapIssues + " не WAXED_COPPER_TRAPDOOR");
        }

        // 11. Item frame on top of rod
        if (!hasItemFrameOnTop(base)) {
            errors.add("§6[11] Рамка §7— не найдена на верхней грани громоотвода §f["
                + base.getBlockX() + " " + base.getBlockY() + " " + base.getBlockZ() + "]"
                + "§7. Повесьте рамку НА ВЕРХНЮЮ ГРАНЬ громоотвода");
        }

        return errors;
    }
}
