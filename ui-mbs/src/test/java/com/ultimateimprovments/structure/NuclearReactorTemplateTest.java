package com.ultimateimprovments.structure;

import com.ultimateimprovments.util.StructureTemplate;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the nuclearreactor NBT template: a compact 3×3×3 cube whose
 * top-center block is the fuel barrel (template anchor), the geometric
 * center is the redstone-controlled reinforced deepslate, and the bottom
 * center is the waxed chiseled copper energy port.
 * <p>
 * This file is exported in the newer palette format ({@code "id"} instead of
 * {@code "Name"}), so the test also locks the parser's dual-format support.
 */
class NuclearReactorTemplateTest {

    private static StructureTemplate load() throws Exception {
        try (InputStream is = NuclearReactorTemplateTest.class
                .getResourceAsStream("/NBT-Files/nuclearreactor.nbt")) {
            assertNotNull(is, "nuclearreactor.nbt must be on the test classpath");
            return StructureTemplate.loadFromNbt(is, "nuclearreactor");
        }
    }

    @Test
    void loadsCompactCubeWithBarrelAnchor() throws Exception {
        StructureTemplate t = load();

        assertEquals("nuclearreactor", t.getName());

        // Every cell of the 3×3×3 cube is occupied: no required-air cells.
        assertEquals(27, t.totalCells());
        assertEquals(27, t.getBlocks().size());
        assertEquals(0, t.getAirBlocks().size());

        // Anchor (top-center cell) is the fuel barrel.
        assertBlock(t, 0, 0, 0, Material.BARREL);

        // Geometric center: reinforced deepslate, one block below the barrel.
        assertBlock(t, 0, -1, 0, Material.REINFORCED_DEEPSLATE);

        // Bottom-center energy port: waxed chiseled copper, two blocks below.
        assertBlock(t, 0, -2, 0, Material.WAXED_CHISELED_COPPER);

        // Top and bottom corners are chiseled deepslate.
        assertBlock(t, -1, 0, -1, Material.CHISELED_DEEPSLATE);
        assertBlock(t, 1, 0, 1, Material.CHISELED_DEEPSLATE);

        // Material census: 8 stairs, 4 walls, 4 glass panes, 8 chiseled
        // deepslate, 1 copper, 1 reinforced deepslate, 1 barrel.
        assertEquals(8, count(t, Material.POLISHED_DEEPSLATE_STAIRS));
        assertEquals(4, count(t, Material.POLISHED_DEEPSLATE_WALL));
        assertEquals(4, count(t, Material.GLASS));
        assertEquals(8, count(t, Material.CHISELED_DEEPSLATE));
        assertEquals(1, count(t, Material.WAXED_CHISELED_COPPER));
        assertEquals(1, count(t, Material.REINFORCED_DEEPSLATE));
        assertEquals(1, count(t, Material.BARREL));
    }

    private static void assertBlock(StructureTemplate t, int dx, int dy, int dz, Material material) {
        assertTrue(t.getBlocks().stream().anyMatch(b ->
                        b.dx() == dx && b.dy() == dy && b.dz() == dz && b.material() == material),
                "expected " + material + " at (" + dx + "," + dy + "," + dz + ")");
    }

    private static long count(StructureTemplate t, Material material) {
        return t.getBlocks().stream().filter(b -> b.material() == material).count();
    }
}
