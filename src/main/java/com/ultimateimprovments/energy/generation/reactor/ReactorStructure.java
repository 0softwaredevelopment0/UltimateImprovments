package com.ultimateimprovments.energy.generation.reactor;

import com.ultimateimprovments.util.LocationUtil;
import com.ultimateimprovments.util.Materials;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;

import java.util.ArrayList;
import java.util.List;

/**
 * Reactor structure validation.
 *
 * The reactor is a 5×6×6 structure (NBT-based).
 * The item frame goes ON THE TOP FACE of the upper core (polished_blackstone).
 * Center = frame position. All offsets are relative to the frame.
 * This class checks for the KEY functional blocks (not all blocks)
 * so the structure can be validated even with minor decoration differences.
 */
public class ReactorStructure {

    // =========================
    // KEY FUNCTIONAL BLOCKS
    // (relative to structure center = item frame block position)
    // =========================

    // Cooling bulb (right side of reactor top)
    private static final int[] BULB_COOL  = { 1, 0, -2 };
    // Heating bulb (left side of reactor top)
    private static final int[] BULB_HEAT  = { -1, 0, -2 };
    // Shell integrity indicator (opposite heating bulb at Z=+2)
    private static final int[] BULB_SH_INT  = { -1, 0, 2 };
    // Case integrity indicator (opposite cooling bulb at Z=+2)
    private static final int[] BULB_CASE_INT = { 1, 0, 2 };
    // Diamond barrel (fuel input) at Y=-3
    private static final int[] DIAMOND_BARREL = { 0, -3, -2 };
    // Gold barrel (fuel input) at Y=-3
    private static final int[] GOLD_BARREL = { 0, -3, 2 };
    // Lever at Y=0 (heating control)
    private static final int[] LEVER = { -1, 0, -3 };
    // Cooling lever at Y=0
    private static final int[] LEVER_COOL = { 1, 0, -3 };
    // 3 wall signs on south face at Y=-4
    private static final int[][] WALL_SIGNS = {
        { -1, -4, -3 },
        {  0, -4, -3 },
        {  1, -4, -3 }
    };
    // Upper core block at Y=-1 (polished blackstone)
    private static final int[] UPPER_CORE = { 0, -1, 0 };
    // Lower core block at Y=-5 (polished blackstone)
    private static final int[] LOWER_CORE = { 0, -5, 0 };

    // =========================
    // CHECK KEY BLOCKS ONLY (requires item frame)
    // Used for initial assembly validation.
    // =========================
    public static boolean isValid(Location center) {
        return isValid(center, true);
    }

    // =========================
    // CHECK KEY BLOCKS (with optional item frame)
    // requireFrame = true  → used during assembly (frame must be present)
    // requireFrame = false → used for active reactor checks (frame already removed)
    // =========================
    public static boolean isValid(Location center, boolean requireFrame) {

        if (center == null || center.getWorld() == null) return false;

        Location base = LocationUtil.normalize(center);

        // =========================
        // 1. COPPER BULBS
        // =========================
        if (!isBlock(base, BULB_COOL,  Materials.WAXED_COPPER_BULB)) return false;
        if (!isBlock(base, BULB_HEAT,  Materials.WAXED_COPPER_BULB)) return false;
        // Integrity bulbs (required — opposite heat/cool bulbs)
        if (!isBlock(base, BULB_SH_INT,  Materials.WAXED_COPPER_BULB)) return false;
        if (!isBlock(base, BULB_CASE_INT, Materials.WAXED_COPPER_BULB)) return false;

        // =========================
        // 2. BARRELS (fuel input)
        // =========================
        if (!isBlock(base, DIAMOND_BARREL, Material.BARREL)) return false;
        if (!isBlock(base, GOLD_BARREL,    Material.BARREL))    return false;

        // =========================
        // 2.5. CORE BLOCKS (upper and lower — polished blackstone)
        // =========================
        if (!isBlock(base, UPPER_CORE, Material.POLISHED_BLACKSTONE)) return false;
        if (!isBlock(base, LOWER_CORE, Material.POLISHED_BLACKSTONE)) return false;

        // =========================
        // 3. LEVERS (optional — heating/cooling controlled by bulb redstone)
        // =========================
        if (!isBlockOrAir(base, LEVER, Material.LEVER)) return false;
        if (!isBlockOrAir(base, LEVER_COOL, Material.LEVER)) return false;

        // =========================
        // 4. WALL SIGNS (south face)
        // =========================
        for (int[] pos : WALL_SIGNS) {
            if (!isAnyWallSign(base, pos[0], pos[1], pos[2])) return false;
        }

        // =========================
        // 5. STRUCTURE CHECK: verify the chamber walls exist
        //    (check that there's a solid wall perimeter at Y=-2)
        // =========================
        if (!hasSolidWalls(base, -2)) return false;
        if (!hasSolidFloor(base))     return false;

        // =========================
        // 6. ITEM FRAME on top of upper core (attached to block at 0,-1,0)
        // =========================
        if (requireFrame && !hasItemFrame(base)) return false;

        return true;
    }

    // =========================
    // FIND STRUCTURE CENTER (with validation)
    // Uses BARREL as anchor.
    // Barrel is at rel (0, -3, -2) from center, so center = (dx, dy+3, dz+2).
    // =========================
    public static Location findCenter(Location entityLoc) {
        Location center = locateCenter(entityLoc);
        if (center != null && isValid(center)) {
            return center;
        }
        return null;
    }

    // =========================
    // LOCATE CENTER (without full validation — только поиск бочки)
    // Находит бочку для алмазов и вычисляет центр, НО не проверяет всю структуру.
    // Нужно для меню сборки: показать опцию реактора,
    // а полная валидация будет при клике на кнопку.
    // =========================
    public static Location locateCenter(Location entityLoc) {

        if (entityLoc == null || entityLoc.getWorld() == null) return null;

        Location base = LocationUtil.normalize(entityLoc);
        World world = base.getWorld();
        int bx = base.getBlockX(), by = base.getBlockY(), bz = base.getBlockZ();

        // Scan ±5 in X/Z, -7 to +3 in Y for barrel (diamond fuel barrel)
        for (int x = bx - 5; x <= bx + 5; x++) {
            for (int y = by - 7; y <= by + 3; y++) {
                for (int z = bz - 5; z <= bz + 5; z++) {
                    if (world.getBlockAt(x, y, z).getType() == Material.BARREL) {
                        // Barrel at rel (0, -3, -2) → center = (x, y+3, z+2)
                        // Verify that there's another barrel at (x, y, z+4) for gold
                        if (world.getBlockAt(x, y, z + 4).getType() == Material.BARREL) {
                            return new Location(world, x, y + 3, z + 2);
                        }
                    }
                }
            }
        }

        return null;
    }

    // =========================
    // DETAILED VALIDATION WITH ERRORS
    // Returns list of problems found. Empty list = valid structure.
    // =========================
    public static List<String> getValidationErrors(Location center) {

        List<String> errors = new ArrayList<>();

        if (center == null || center.getWorld() == null) {
            errors.add("§c[1] Reactor center = null (search error)");
            return errors;
        }

        Location base = LocationUtil.normalize(center);

        // =========================
        // 1. COPPER BULBS
        // =========================
        checkBlockDetailed(errors, base, BULB_COOL, Materials.WAXED_COPPER_BULB,
                "§6[1] Cooling bulb §e(1, 0, -2)"
                        + "§7 — must be WAXED_COPPER_BULB at §f"
                        + locStr(base, BULB_COOL));

        checkBlockDetailed(errors, base, BULB_HEAT, Materials.WAXED_COPPER_BULB,
                "§6[2] Heating bulb §e(-1, 0, -2)"
                        + "§7 — must be WAXED_COPPER_BULB at §f"
                        + locStr(base, BULB_HEAT));

        checkBlockDetailed(errors, base, BULB_SH_INT, Materials.WAXED_COPPER_BULB,
                "§6[3] Shell integrity bulb §e(-1, 0, 2)"
                        + "§7 — must be WAXED_COPPER_BULB at §f"
                        + locStr(base, BULB_SH_INT));

        checkBlockDetailed(errors, base, BULB_CASE_INT, Materials.WAXED_COPPER_BULB,
                "§6[4] Case integrity bulb §e(1, 0, 2)"
                        + "§7 — must be WAXED_COPPER_BULB at §f"
                        + locStr(base, BULB_CASE_INT));

        // =========================
        // 2. BARRELS (fuel input)
        // =========================
        checkBlockDetailed(errors, base, DIAMOND_BARREL, Material.BARREL,
                "§6[5] Diamond barrel (fuel input) §e(0, -3, -2)"
                        + "§7 — must be BARREL at §f"
                        + locStr(base, DIAMOND_BARREL));

        checkBlockDetailed(errors, base, GOLD_BARREL, Material.BARREL,
                "§6[6] Gold barrel (fuel input) §e(0, -3, 2)"
                        + "§7 — must be BARREL at §f"
                        + locStr(base, GOLD_BARREL));

        // =========================
        // 2.5. CORE BLOCKS (upper and lower — polished blackstone)
        // =========================
        checkBlockDetailed(errors, base, UPPER_CORE, Material.POLISHED_BLACKSTONE,
                "§6[5.5] Upper core §e(0, -1, 0)"
                        + "§7 — must be POLISHED_BLACKSTONE at §f"
                        + locStr(base, UPPER_CORE));

        checkBlockDetailed(errors, base, LOWER_CORE, Material.POLISHED_BLACKSTONE,
                "§6[5.6] Lower core §e(0, -5, 0)"
                        + "§7 — must be POLISHED_BLACKSTONE at §f"
                        + locStr(base, LOWER_CORE));

        // =========================
        // 3. LEVERS (optional)
        // =========================
        checkBlockDetailedOptional(errors, base, LEVER, Material.LEVER,
                "§6[7] Heating lever §e(-1, 0, -3)"
                        + "§7 — optional, but must be LEVER at §f"
                        + locStr(base, LEVER));

        checkBlockDetailedOptional(errors, base, LEVER_COOL, Material.LEVER,
                "§6[7.2] Cooling lever §e(1, 0, -3)"
                        + "§7 — optional, but must be LEVER at §f"
                        + locStr(base, LEVER_COOL));

        // =========================
        // 4. WALL SIGNS (south face)
        // =========================
        for (int i = 0; i < WALL_SIGNS.length; i++) {
            int[] pos = WALL_SIGNS[i];
            int dx = pos[0], dy = pos[1], dz = pos[2];
            Material actual = getBlock(base, dx, dy, dz);
            if (!isAnyWallSign(actual)) {
                String side = switch (i) {
                    case 0 -> "left";
                    case 1 -> "center";
                    case 2 -> "right";
                    default -> "";
                };
                errors.add("§6[8." + (i + 1) + "] Wall sign (" + side + ") §e("
                        + dx + ", " + dy + ", " + dz + ")"
                        + "§7 — not found at §f" + locStr(base, pos)
                        + "§7. Current block: §f" + actual
                        + "§7. Place any wall sign (OAK/DARK_OAK/BIRCH/...)");
            }
        }

        // =========================
        // 5. STRUCTURE CHECK: walls at Y=-2
        // =========================
        checkSolidWallsDetailed(errors, base);

        // =========================
        // 6. FLOOR CHECK
        // =========================
        checkSolidFloorDetailed(errors, base);

        // =========================
        // 7. ITEM FRAME
        // =========================
        if (!hasItemFrame(base)) {
            errors.add("§6[11] ItemFrame §e(0, 0, 0)"
                    + "§7 — not found on top of core §f"
                    + locStr(base, new int[]{0, 0, 0})
                    + "§7. Place the item frame ON THE TOP FACE of the center block (polished blackstone)");
        }

        return errors;
    }

    // =========================
    // DETAILED CHECK HELPERS
    // =========================
    private static String locStr(Location base, int[] pos) {
        return "§f[" + (base.getBlockX() + pos[0])
                + " " + (base.getBlockY() + pos[1])
                + " " + (base.getBlockZ() + pos[2]) + "]";
    }

    private static void checkBlockDetailed(List<String> errors, Location base, int[] pos,
                                            Material expected, String desc) {
        Material actual = getBlock(base, pos[0], pos[1], pos[2]);
        if (actual != expected) {
            errors.add(desc
                    + "§7. Сейчас: §f" + actual
                    + "§7. Нужно: §f" + expected
                    + "§7. " + getBlockAdvice(expected));
        }
    }

    private static void checkBlockDetailedOptional(List<String> errors, Location base, int[] pos,
                                                    Material expected, String desc) {
        Material actual = getBlock(base, pos[0], pos[1], pos[2]);
        if (actual != expected && actual != Material.AIR) {
            errors.add(desc
                    + "§7. Сейчас: §f" + actual
                    + "§7. Нужно: §f" + expected + "§7 или §fAIR");
        }
    }

    private static void checkSolidWallsDetailed(List<String> errors, Location base) {
        int relY = -2;
        int[][] checkPositions = {
                { -2, relY, -2 }, {  0, relY, -2 }, {  2, relY, -2 },
                { -2, relY,  2 }, {  0, relY,  2 }, {  2, relY,  2 },
                { -2, relY,  0 },
                {  2, relY,  0 },
        };

        String[] wallNames = {
                "north wall (corner)", "north wall (center)", "north wall (corner)",
                "south wall (corner)", "south wall (center)", "south wall (corner)",
                "west wall (center)",
                "east wall (center)",
        };

        for (int i = 0; i < checkPositions.length; i++) {
            int dx = checkPositions[i][0], dy = checkPositions[i][1], dz = checkPositions[i][2];
            Material mat = getBlock(base, dx, dy, dz);
            if (mat == Material.GLASS) continue;
            if (mat == Material.AIR) {
                errors.add("§6[9." + (i + 1) + "] Reactor wall (" + wallNames[i] + ") §e("
                        + dx + ", " + dy + ", " + dz + ")"
                        + "§7 — empty (AIR) at §f" + locStr(base, checkPositions[i])
                        + "§7. Place any solid block");
                continue;
            }
            if (isCopperBlock(mat)) continue;
            if (mat == Materials.WAXED_CHISELED_COPPER) continue;
            if (mat == Material.DIAMOND_BLOCK) continue;
            if (mat == Material.GOLD_BLOCK) continue;
            if (mat == Material.BARREL) continue;
            if (mat == Material.END_ROD) continue;
            errors.add("§6[9." + (i + 1) + "] Reactor wall (" + wallNames[i] + ") §e("
                    + dx + ", " + dy + ", " + dz + ")"
                    + "§7 — invalid block §f" + mat + "§7 at §f"
                    + locStr(base, checkPositions[i])
                    + "§7. Allowed: copper blocks, glass, WAXED_CHISELED_COPPER");
        }
    }

    private static void checkSolidFloorDetailed(List<String> errors, Location base) {
        int[][] floorPositions = {
                { -2, -5, -2 }, { -1, -5, -2 }, { 0, -5, -2 }, { 1, -5, -2 }, { 2, -5, -2 },
                { -2, -5,  0 }, {  0, -5,  0 }, {  2, -5,  0 },
                { -2, -5,  2 }, {  0, -5,  2 }, {  2, -5,  2 },
        };

        for (int i = 0; i < floorPositions.length; i++) {
            int dx = floorPositions[i][0], dy = floorPositions[i][1], dz = floorPositions[i][2];
            Material mat = getBlock(base, dx, dy, dz);
            if (mat == Material.AIR) {
                errors.add("§6[10." + (i + 1) + "] Reactor floor §e("
                        + dx + ", " + dy + ", " + dz + ")"
                        + "§7 — empty (AIR) at §f" + locStr(base, floorPositions[i])
                        + "§7. Fill the reactor floor with any solid blocks");
            }
        }
    }

    private static String getBlockAdvice(Material expected) {
        return switch (expected) {
            case WAXED_COPPER_BULB -> "§aTip: waxed copper bulb (WAXED_COPPER_BULB)";
            case BARREL -> "§aTip: barrel (BARREL) — fuel goes inside";
            case LEVER -> "§aTip: lever (LEVER)";
            case WAXED_CHISELED_COPPER -> "§aTip: waxed chiseled copper (WAXED_CHISELED_COPPER)";
            case POLISHED_BLACKSTONE -> "§aTip: polished blackstone (POLISHED_BLACKSTONE)";
            default -> "";
        };
    }

    // =========================
    // CHECK SOLID WALLS
    // =========================
    private static boolean hasSolidWalls(Location base, int relY) {

        // The 5x5 interior is at rel X=-2..2, Z=-2..2
        // Walls are at Z=-2, Z=+2, X=-2, X=+2
        // Check corners and midpoints of each wall
        int[][] checkPositions = {
            // North wall (Z=-2)
            { -2, relY, -2 }, {  0, relY, -2 }, {  2, relY, -2 },
            // South wall (Z=+2)
            { -2, relY,  2 }, {  0, relY,  2 }, {  2, relY,  2 },
            // West wall (X=-2) — middle
            { -2, relY,  0 },
            // East wall (X=+2) — middle
            {  2, relY,  0 },
        };

        for (int[] pos : checkPositions) {
            Material mat = getBlock(base, pos[0], pos[1], pos[2]);
            if (mat == Material.GLASS) continue; // glass windows allowed
            if (mat == Material.AIR) return false;
            if (isCopperBlock(mat)) continue;
            // Allow waxed chiseled copper, diamond, gold, end rods inside
            if (mat == Materials.WAXED_CHISELED_COPPER) continue;
            if (mat == Material.DIAMOND_BLOCK) continue;
            if (mat == Material.GOLD_BLOCK) continue;
            if (mat == Material.BARREL) continue;
            if (mat == Material.END_ROD) continue;
            return false;
        }

        return true;
    }

    // =========================
    // CHECK SOLID FLOOR
    // =========================
    private static boolean hasSolidFloor(Location base) {

        // 5x5 interior floor at Y=-5: rel X=-2..2, Z=-2..2
        int[][] floorPositions = {
            { -2, -5, -2 }, { -1, -5, -2 }, { 0, -5, -2 }, { 1, -5, -2 }, { 2, -5, -2 },
            { -2, -5,  0 }, {  0, -5,  0 }, {  2, -5,  0 },
            { -2, -5,  2 }, {  0, -5,  2 }, {  2, -5,  2 },
        };

        for (int[] pos : floorPositions) {
            Material mat = getBlock(base, pos[0], pos[1], pos[2]);
            if (mat == Material.AIR) return false;
        }

        return true;
    }

    // =========================
    // HAS ITEM FRAME
    // =========================
    private static boolean hasItemFrame(Location base) {

        // Search a generous area since the frame can be on different block faces
        for (Entity entity : base.getWorld().getNearbyEntities(
                base.clone().add(0.5, 0.5, 0.5), 2.0, 2.0, 2.0,
                e -> e instanceof ItemFrame)) {
            return true;
        }

        return false;
    }

    // =========================
    // GET BLOCK TYPE
    // =========================
    private static Material getBlock(Location base, int dx, int dy, int dz) {
        return base.clone().add(dx, dy, dz).getBlock().getType();
    }

    private static boolean isBlock(Location base, int[] pos, Material expected) {
        return getBlock(base, pos[0], pos[1], pos[2]) == expected;
    }

    private static boolean isBlockOrAir(Location base, int[] pos, Material expected) {
        Material actual = getBlock(base, pos[0], pos[1], pos[2]);
        return actual == expected || actual == Material.AIR;
    }

    // =========================
    // IS COPPER BLOCK
    // =========================
    private static boolean isCopperBlock(Material mat) {
        return mat == Materials.WAXED_CUT_COPPER
            || mat == Materials.WAXED_CHISELED_COPPER
            || mat == Materials.WAXED_COPPER_BLOCK
            || mat == Materials.WAXED_CUT_COPPER_STAIRS;
    }

    // =========================
    // IS ANY SIGN
    // =========================
    private static boolean isAnyWallSign(Material mat) {
        return mat == Material.OAK_WALL_SIGN
            || mat == Material.DARK_OAK_WALL_SIGN
            || mat == Material.BIRCH_WALL_SIGN
            || mat == Material.SPRUCE_WALL_SIGN
            || mat == Material.JUNGLE_WALL_SIGN
            || mat == Material.ACACIA_WALL_SIGN
            || mat == Material.CHERRY_WALL_SIGN
            || mat == Material.MANGROVE_WALL_SIGN
            || mat == Material.CRIMSON_WALL_SIGN
            || mat == Material.WARPED_WALL_SIGN
            || mat == Material.PALE_OAK_WALL_SIGN;
    }

    private static boolean isAnyWallSign(Location base, int dx, int dy, int dz) {
        return isAnyWallSign(getBlock(base, dx, dy, dz));
    }
}
