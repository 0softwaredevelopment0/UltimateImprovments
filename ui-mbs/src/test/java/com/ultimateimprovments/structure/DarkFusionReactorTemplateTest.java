package com.ultimateimprovments.structure;

import com.ultimateimprovments.util.StructureTemplate;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the darkfusionreactor NBT template is parsed with the frame anchor
 * (the free cell above the central top bulb) and validated FULLY — every solid
 * block and every required-air cell is checked — while block states and block
 * entity components are ignored (bulb lit/powered, barrel contents and facing,
 * sign text, lever orientation, stairs facing, etc.).
 */
class DarkFusionReactorTemplateTest {

    private static StructureTemplate load() throws Exception {
        try (InputStream is = DarkFusionReactorTemplateTest.class
                .getResourceAsStream("/NBT-Files/darkfusionreactor.nbt")) {
            assertNotNull(is, "darkfusionreactor.nbt must be on the test classpath");
            return StructureTemplate.loadFromNbt(is, "darkfusionreactor");
        }
    }

    @Test
    void loadsFullTemplateWithFrameAnchor() throws Exception {
        StructureTemplate t = load();

        assertEquals("darkfusionreactor", t.getName());

        // Every cell of the 10×11×9 template must be part of the check:
        // 990 total = 455 solid + 535 required air (nothing dropped, air included)
        assertEquals(990, t.totalCells());
        assertEquals(455, t.getBlocks().size());
        assertEquals(535, t.getAirBlocks().size());

        // Anchor: the item frame stands 0.5 above the central top bulb —
        // the bulb itself must sit directly below the template origin.
        assertTrue(t.getBlocks().stream().anyMatch(b ->
                        b.dx() == 0 && b.dy() == -1 && b.dz() == 0
                                && b.material() == Material.WAXED_COPPER_BULB),
                "central top bulb must be at (0,-1,0) relative to the frame anchor");

        // The anchor cell itself is required air (the frame occupies it in the world).
        assertTrue(t.getAirBlocks().stream().anyMatch(b ->
                        b.dx() == 0 && b.dy() == 0 && b.dz() == 0),
                "the anchor cell above the central bulb must be a required-air cell");

        // Roof control bulbs are one block above the anchor (frame is below them).
        assertTrue(t.getBlocks().stream().anyMatch(b ->
                        b.dx() == -4 && b.dy() == 0 && b.dz() == 0
                                && b.material() == Material.WAXED_COPPER_BULB),
                "roof control bulb row must be at y=0 relative to the anchor");

        // 12 bulbs total: 1 central + 2 tower + 9 roof control
        assertEquals(12, t.getBlocks().stream()
                .filter(b -> b.material() == Material.WAXED_COPPER_BULB).count());
        // 3 fuel barrels
        assertEquals(3, t.getBlocks().stream()
                .filter(b -> b.material() == Material.BARREL).count());
    }

    @Test
    void statefulBlocksMatchByMaterialOnly() throws Exception {
        StructureTemplate t = load();

        // All stateful blocks (stairs, levers, barrels, signs, rods, bars, grate)
        // keep their plain Material in the template — state/properties are dropped
        // by the parser, so matching is by material only.
        assertTrue(t.getBlocks().stream().anyMatch(b ->
                b.material() == Material.WAXED_CUT_COPPER_STAIRS));
        assertTrue(t.getBlocks().stream().anyMatch(b -> b.material() == Material.LEVER));
        assertTrue(t.getBlocks().stream().anyMatch(b -> b.material() == Material.BARREL));
        assertTrue(t.getBlocks().stream().anyMatch(b -> b.material() == Material.ACACIA_WALL_SIGN));
        assertTrue(t.getBlocks().stream().anyMatch(b -> b.material() == Material.ACACIA_SIGN));
        assertTrue(t.getBlocks().stream().anyMatch(b -> b.material() == Material.WAXED_LIGHTNING_ROD));
        assertTrue(t.getBlocks().stream().anyMatch(b -> b.material() == Material.WAXED_COPPER_BARS));
        assertTrue(t.getBlocks().stream().anyMatch(b -> b.material() == Material.WAXED_COPPER_GRATE));
    }
}
