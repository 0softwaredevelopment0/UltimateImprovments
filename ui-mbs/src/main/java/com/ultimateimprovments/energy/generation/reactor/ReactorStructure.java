package com.ultimateimprovments.energy.generation.reactor;

import com.ultimateimprovments.util.LocationUtil;
import com.ultimateimprovments.util.Materials;
import com.ultimateimprovments.util.StructureTemplate;
import com.ultimateimprovments.util.StructuresMessages;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;

import java.util.ArrayList;
import java.util.List;

/**
 * Reactor structure validation — key functional blocks of the
 * <b>Dark Fusion Reactor</b> (NBT template {@code darkfusionreactor.nbt}, 10×11×9).
 *
 * <p><b>Anchor:</b> the item frame stands on the TOP FACE of the central top copper
 * bulb (template cell (5, 8, 4)) — 0.5 blocks above it, one block below the roof
 * control bulbs, surrounded by the copper ring of the roof plate. The frame cell
 * is template cell (5, 9, 4); all offsets below are relative to it:
 * {@code offset = templatePos − (5, 9, 4)}.</p>
 *
 * <p>Key blocks (all verified against the NBT template):</p>
 * <ul>
 *   <li>Central top bulb (5,8,4) → (0,−1,0) — the block the frame is attached to</li>
 *   <li>Tower mid bulbs (2,4,4)/(8,4,4) → (∓3,−5,0)</li>
 *   <li>Roof control bulbs (y=9): x=1 → (−4,0,−4..4), x=3 → (−2,0,−4..2)</li>
 *   <li>Fuel barrels: floor (5,0,4) → (0,−9,0), sides (1,4,4)/(9,4,4) → (∓4,−5,0)</li>
 *   <li>Central grate (5,7,4) → (0,−2,0) — under the lightning-rod column</li>
 *   <li>Levers (y=9): x=0 → (−5,0,−4..4), x=2 → (−3,0,−4..2)</li>
 *   <li>Stats signs: wall (0,1..2,2..5) → (−5,−8..−7,−2..1), roof (1|3,10,·) → (−4|−2,1,·)</li>
 *   <li>Floor slab (y=0) → 9×9 at y=−9</li>
 * </ul>
 *
 * <p>This class checks the KEY functional blocks (not every block) so the structure
 * can be validated even with minor decoration differences. Full cell-by-cell
 * validation is done by the NBT template (see {@code ReactorCommand.validateReactorByTemplate});
 * this validator is the fallback when the template is not loaded.</p>
 */
public class ReactorStructure {

    // =========================
    // KEY FUNCTIONAL BLOCKS
    // (relative to the anchor = item frame cell above the central top bulb)
    // =========================

    /** Central top bulb — the block the item frame is attached to. */
    private static final int[] CENTRAL_BULB = { 0, -1, 0 };

    /** Tower mid bulbs (inside the two parallel towers, y=4). */
    private static final int[] BULB_WEST = { -3, -5, 0 };
    private static final int[] BULB_EAST = {  3, -5, 0 };

    /** Roof control bulbs (y=9): row x=1 (5 pcs) + row x=3 (4 pcs). */
    private static final int[][] ROOF_BULBS = {
            { -4, 0, -4 }, { -4, 0, -2 }, { -4, 0, 0 }, { -4, 0, 2 }, { -4, 0, 4 },
            { -2, 0, -4 }, { -2, 0, -2 }, { -2, 0, 0 }, { -2, 0, 2 }
    };

    /** Fuel barrels: center floor + two side barrels at mid-height. */
    private static final int[] BARREL_FLOOR = {  0, -9, 0 };
    private static final int[] BARREL_WEST  = { -4, -5, 0 };
    private static final int[] BARREL_EAST  = {  4, -5, 0 };

    /** Central grate under the lightning-rod column (5,7,4). */
    private static final int[] FLOOR_GRATE = { 0, -2, 0 };

    /** Levers (y=9): 5 on the front edge (x=0) + 4 between the bulb rows (x=2). */
    private static final int[][] LEVERS = {
            { -5, 0, -4 }, { -5, 0, -2 }, { -5, 0, 0 }, { -5, 0, 2 }, { -5, 0, 4 },
            { -3, 0, -4 }, { -3, 0, -2 }, { -3, 0, 0 }, { -3, 0, 2 }
    };

    /** Stats signs on the front wall (y=1..2): Power Stats, Shield Stats, etc. */
    private static final int[][] WALL_SIGNS = {
            { -5, -8, -2 }, { -5, -8, -1 }, { -5, -8, 0 }, { -5, -8, 1 },
            { -5, -7, -1 }, { -5, -7, 0 }, { -5, -7, 1 }
    };

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

        // 1. Central bulb (frame attachment point)
        if (!isBlock(base, CENTRAL_BULB, Materials.WAXED_COPPER_BULB)) return false;

        // 2. Tower mid bulbs
        if (!isBlock(base, BULB_WEST, Materials.WAXED_COPPER_BULB)) return false;
        if (!isBlock(base, BULB_EAST, Materials.WAXED_COPPER_BULB)) return false;

        // 3. Roof control bulbs
        for (int[] pos : ROOF_BULBS) {
            if (!isBlock(base, pos, Materials.WAXED_COPPER_BULB)) return false;
        }

        // 4. Fuel barrels
        if (!isBlock(base, BARREL_FLOOR, Material.BARREL)) return false;
        if (!isBlock(base, BARREL_WEST,  Material.BARREL)) return false;
        if (!isBlock(base, BARREL_EAST,  Material.BARREL)) return false;

        // 5. Central grate under the rod column
        if (!isBlock(base, FLOOR_GRATE, Materials.WAXED_COPPER_GRATE)) return false;

        // 6. Levers
        for (int[] pos : LEVERS) {
            if (!isBlock(base, pos, Material.LEVER)) return false;
        }

        // 7. Stats signs on the front wall
        for (int[] pos : WALL_SIGNS) {
            if (!isAnySign(base, pos[0], pos[1], pos[2])) return false;
        }

        // 8. Floor slab: the whole 9×9 at y=-9 must be solid (no holes)
        if (!hasSolidFloor(base)) return false;

        // 9. Item frame on top of the central bulb
        if (requireFrame && !hasItemFrame(base)) return false;

        return true;
    }

    // =========================
    // FIND STRUCTURE CENTER (with validation)
    // Uses the side fuel barrels as anchor: they sit at (∓4,-5,0) relative to
    // the anchor, i.e. 8 blocks apart on X. center = (wx+4, wy+5, wz).
    // =========================
    public static Location findCenter(Location entityLoc) {
        Location center = locateCenter(entityLoc);
        if (center != null && isValid(center)) {
            return center;
        }
        return null;
    }

    // =========================
    // LOCATE CENTER (without full validation — barrel search only)
    // Finds a side fuel barrel and computes the anchor, but does NOT validate
    // the whole structure. Needed for the assembly menu: show the reactor option,
    // with full validation happening on button click.
    // =========================
    public static Location locateCenter(Location entityLoc) {

        if (entityLoc == null || entityLoc.getWorld() == null) return null;

        Location base = LocationUtil.normalize(entityLoc);
        World world = base.getWorld();
        int bx = base.getBlockX(), by = base.getBlockY(), bz = base.getBlockZ();

        // Scan ±8 in X/Z, -10 to +2 in Y for a barrel with a partner 8 blocks east
        for (int x = bx - 8; x <= bx + 8; x++) {
            for (int y = by - 10; y <= by + 2; y++) {
                for (int z = bz - 8; z <= bz + 8; z++) {
                    if (world.getBlockAt(x, y, z).getType() != Material.BARREL) continue;
                    // Partner barrel 8 blocks east (the opposite side barrel)
                    if (world.getBlockAt(x + 8, y, z).getType() == Material.BARREL) {
                        // Anchor = (west barrel offset (-4,-5,0)) + (4, 5, 0)
                        return new Location(world, x + 4, y + 5, z);
                    }
                }
            }
        }

        return null;
    }

    // =========================
    // DETAILED VALIDATION WITH ERRORS (legacy fallback)
    // Returns list of fix instructions. Empty list = valid structure.
    // Used only when the NBT template is unavailable — the template check
    // (cell-by-cell) is the primary validator.
    // =========================
    public static List<String> getValidationErrors(Location center) {

        List<String> errors = new ArrayList<>();

        if (center == null || center.getWorld() == null) {
            errors.add("<red>[1] Reactor center = null (search error)");
            return errors;
        }

        Location base = LocationUtil.normalize(center);

        check(errors, base, CENTRAL_BULB, Materials.WAXED_COPPER_BULB);
        check(errors, base, BULB_WEST, Materials.WAXED_COPPER_BULB);
        check(errors, base, BULB_EAST, Materials.WAXED_COPPER_BULB);

        for (int[] pos : ROOF_BULBS) {
            check(errors, base, pos, Materials.WAXED_COPPER_BULB);
        }

        check(errors, base, BARREL_FLOOR, Material.BARREL);
        check(errors, base, BARREL_WEST, Material.BARREL);
        check(errors, base, BARREL_EAST, Material.BARREL);
        check(errors, base, FLOOR_GRATE, Materials.WAXED_COPPER_GRATE);

        for (int[] pos : LEVERS) {
            check(errors, base, pos, Material.LEVER);
        }

        for (int[] pos : WALL_SIGNS) {
            Material actual = getBlock(base, pos[0], pos[1], pos[2]);
            if (!isAnySign(actual)) {
                errors.add(StructureTemplate.formatFixRelative(
                        new StructureTemplate.Fix(pos[0], pos[1], pos[2], Material.ACACIA_WALL_SIGN, actual)));
            }
        }

        checkSolidFloorDetailed(errors, base);

        if (!hasItemFrame(base)) {
            errors.add("<red>[frame] " + StructuresMessages.get("frame_missing",
                    "<gray>Рамка не найдена над центральной лампой!"));
        }

        return errors;
    }

    // =========================
    // CHECK HELPERS
    // =========================

    private static void check(List<String> errors, Location base, int[] pos, Material expected) {
        Material actual = getBlock(base, pos[0], pos[1], pos[2]);
        if (actual != expected) {
            errors.add(StructureTemplate.formatFixRelative(
                    new StructureTemplate.Fix(pos[0], pos[1], pos[2], expected, actual)));
        }
    }

    private static void checkSolidFloorDetailed(List<String> errors, Location base) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                Material mat = getBlock(base, dx, -9, dz);
                if (mat == Material.AIR) {
                    errors.add(StructureTemplate.formatFixRelative(
                            new StructureTemplate.Fix(dx, -9, dz, Material.WAXED_COPPER_BLOCK, Material.AIR)));
                }
            }
        }
    }

    // =========================
    // CHECK SOLID FLOOR
    // =========================
    private static boolean hasSolidFloor(Location base) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (getBlock(base, dx, -9, dz) == Material.AIR) return false;
            }
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

    // =========================
    // IS ANY SIGN (wall or standing, any wood type)
    // =========================
    private static boolean isAnySign(Material mat) {
        return mat == Material.OAK_WALL_SIGN || mat == Material.OAK_SIGN
            || mat == Material.DARK_OAK_WALL_SIGN || mat == Material.DARK_OAK_SIGN
            || mat == Material.BIRCH_WALL_SIGN || mat == Material.BIRCH_SIGN
            || mat == Material.SPRUCE_WALL_SIGN || mat == Material.SPRUCE_SIGN
            || mat == Material.JUNGLE_WALL_SIGN || mat == Material.JUNGLE_SIGN
            || mat == Material.ACACIA_WALL_SIGN || mat == Material.ACACIA_SIGN
            || mat == Material.CHERRY_WALL_SIGN || mat == Material.CHERRY_SIGN
            || mat == Material.MANGROVE_WALL_SIGN || mat == Material.MANGROVE_SIGN
            || mat == Material.CRIMSON_WALL_SIGN || mat == Material.CRIMSON_SIGN
            || mat == Material.WARPED_WALL_SIGN || mat == Material.WARPED_SIGN
            || mat == Material.PALE_OAK_WALL_SIGN || mat == Material.PALE_OAK_SIGN;
    }

    private static boolean isAnySign(Location base, int dx, int dy, int dz) {
        return isAnySign(getBlock(base, dx, dy, dz));
    }
}
