package com.ultimateimprovments.structure;

import com.ultimateimprovments.util.StructureTemplate;

import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the structure occupancy rule: a structure that merely
 * stands NEXT TO another one must be considered free (the old 5-block radius
 * scan mistook it for "the structure is already here"), while a structure that
 * would occupy another one's cells — including standing inside its volume — is
 * a conflict.
 * <p>
 * The check is the plain 3-coordinate AABB comparison in
 * {@link StructureTemplate#overlapsVolume(StructureTemplate, int, int, int)};
 * these tests use the real bundled NBT templates, so they also lock the
 * template bounds (anchor-relative cells) in place.
 */
class StructureVolumeOverlapTest {

    private static StructureTemplate load(String name) throws Exception {
        try (InputStream is = StructureVolumeOverlapTest.class
                .getResourceAsStream("/NBT-Files/" + name + ".nbt")) {
            assertNotNull(is, name + ".nbt must be on the test classpath");
            return StructureTemplate.loadFromNbt(is, name);
        }
    }

    private static StructureTemplate nuclear() throws Exception {
        return load("nuclearreactor");
    }

    private static StructureTemplate fusion() throws Exception {
        return load("darkfusionreactor");
    }

    /**
     * The reported bug: two compact nuclear reactors standing near each other
     * with a gap (anchor 4 blocks apart) or even directly against each other
     * (anchor 3 blocks apart — adjacent cells, no shared cell) must NOT overlap.
     * The old radius scan picked the assembled reactor's matching volume and
     * reported "a nuclear reactor is already active at this place".
     */
    @Test
    void nearbyNuclearReactorsDoNotOverlap() throws Exception {
        StructureTemplate n = nuclear();

        // Anchor 4 apart along X: a full 1-block air gap between the cubes.
        assertFalse(n.overlapsVolume(4, 0, 0), "reactor with a gap must be free");
        assertFalse(n.overlapsVolume(-4, 0, 0));
        assertFalse(n.overlapsVolume(0, 0, 4), "reactor with a gap along Z must be free");

        // Anchor 3 apart: faces touching, but no shared cell — still free.
        assertFalse(n.overlapsVolume(3, 0, 0), "touching reactors must not overlap");
        assertFalse(n.overlapsVolume(0, 0, 3));
        assertFalse(n.overlapsVolume(0, 3, 0), "stacked reactors (3 up) must not overlap");
    }

    /** The same shape at the same / overlapping cells IS a conflict. */
    @Test
    void sameCubeAtSameOrOverlappingCellsConflicts() throws Exception {
        StructureTemplate n = nuclear();

        assertTrue(n.overlapsVolume(0, 0, 0), "same center is a conflict");
        assertTrue(n.overlapsVolume(2, 0, 0), "anchor 2 apart shares a cell plane");
        assertTrue(n.overlapsVolume(0, 0, -2));
        assertTrue(n.overlapsVolume(0, 2, 0), "anchor 2 apart vertically shares a cell");
    }

    /** A reactor standing INSIDE the fusion reactor volume is a conflict. */
    @Test
    void reactorInsideFusionVolumeConflicts() throws Exception {
        StructureTemplate f = fusion();
        StructureTemplate n = nuclear();
        Bounds b = bounds(f);

        // Fully inside the DFC footprint (all three axes).
        int insideX = b.minX() + 2;
        int insideY = b.minY() + 2;
        int insideZ = b.minZ() + 2;
        assertTrue(f.overlapsVolume(n, insideX, insideY, insideZ),
                "structure inside another volume must conflict");

        // Just outside on X: its nearest cell is one block past the DFC maxX.
        int outsideX = b.maxX() + 2; // nuclear minX is -1 → first cell = maxX + 1
        assertFalse(f.overlapsVolume(n, outsideX, insideY, insideZ),
                "structure directly next to the volume must be free");
    }

    /** The solid-cell lookup used to find the structure that owns the clicked frame. */
    @Test
    void solidCellLookup() throws Exception {
        StructureTemplate n = nuclear();
        // Anchor: top-center fuel barrel; middle: reinforced deepslate.
        assertTrue(n.hasSolidCell(0, 0, 0));
        assertTrue(n.hasSolidCell(0, -1, 0));
        // Outside the 3×3×3 cube.
        assertFalse(n.hasSolidCell(2, 0, 0));
        assertFalse(n.hasSolidCell(0, 1, 0));

        StructureTemplate f = fusion();
        // The frame anchor cell of the DFC is required AIR — not a solid cell.
        assertFalse(f.hasSolidCell(0, 0, 0));
        // The central top bulb the frame stands on IS a solid cell.
        assertTrue(f.hasSolidCell(0, -1, 0));
    }

    // =========================
    // BOUNDS (from the loaded cells)
    // =========================
    private record Bounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {}

    private static Bounds bounds(StructureTemplate t) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        var cells = new java.util.ArrayList<StructureTemplate.BlockEntry>();
        cells.addAll(t.getBlocks());
        cells.addAll(t.getAirBlocks());
        for (StructureTemplate.BlockEntry b : cells) {
            minX = Math.min(minX, b.dx());
            maxX = Math.max(maxX, b.dx());
            minY = Math.min(minY, b.dy());
            maxY = Math.max(maxY, b.dy());
            minZ = Math.min(minZ, b.dz());
            maxZ = Math.max(maxZ, b.dz());
        }
        return new Bounds(minX, maxX, minY, maxY, minZ, maxZ);
    }
}
