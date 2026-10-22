package com.ultimateimprovments.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the structure-assembler priority rule: a frame hung on a
 * block that is ALSO an adaptive-structure trigger (waxed copper bulb -> electric
 * lamp, waxed copper grate -> battery) must not hijack a partially built NBT
 * structure. The full template check has to run first and report the closest
 * structure with its fixes.
 * <p>
 * The world is a tiny in-memory fake (dynamic proxies around
 * {@link World} / {@link Block}) so the real bundled NBT templates can be
 * checked without a running server.
 */
class StructureAssemblyClaimTest {

    // =========================
    // REAL TEMPLATES
    // =========================
    private static StructureTemplate load(String name) throws Exception {
        try (InputStream is = StructureAssemblyClaimTest.class
                .getResourceAsStream("/NBT-Files/" + name + ".nbt")) {
            assertNotNull(is, name + ".nbt must be on the test classpath");
            return StructureTemplate.loadFromNbt(is, name);
        }
    }

    // =========================
    // SOLID COVERAGE
    // =========================
    /**
     * The compact reactor template exposes its solid body: a complete build is
     * 100% covered, one missing block drops the coverage below 100 -- unlike the
     * air-inclusive percentage, which would still round to 100.
     */
    @Test
    void solidCoverageCountsTheBuiltBody() throws Exception {
        StructureTemplate t = load("nuclearreactor");
        FakeWorld world = new FakeWorld();
        Location center = new Location(world.world(), 10, 64, 10);

        world.placeTemplate(t, center);

        StructureTemplate.MatchResult full = t.checkAt(center);
        assertTrue(full.matched(), "a fully placed template must match");
        assertEquals(100, full.solidPercent());
        assertEquals(t.getBlocks().size(), full.solidCells());
        assertEquals(0, full.solidMismatches());

        StructureTemplate.BlockEntry removed = t.getBlocks().get(0);
        world.remove(center.getBlockX() + removed.dx(),
                center.getBlockY() + removed.dy(),
                center.getBlockZ() + removed.dz());

        StructureTemplate.MatchResult oneMissing = t.checkAt(center);
        assertFalse(oneMissing.matched());
        assertEquals(1, oneMissing.solidMismatches());
        // 26 of 27 solid cells = 96.3% -> 96
        assertEquals(96, oneMissing.solidPercent());
    }

    // =========================
    // CLAIM POLICY
    // =========================
    /**
     * A partially built DFC (more than a quarter of the body already placed)
     * owns the frame hanging on its central top bulb: the assembler must report
     * the DFC fix list instead of trying to build an electric lamp.
     */
    @Test
    void halfBuiltDarkFusionReactorClaimsTheFrame() throws Exception {
        StructureTemplate fusion = load("darkfusionreactor");
        StructureTemplate nuclear = load("nuclearreactor");
        StructureTemplate lightning = load("lightning_str");

        FakeWorld world = new FakeWorld();
        Location center = new Location(world.world(), 0, 80, 0);
        // Upper part of the reactor only (the frame anchor is the air cell at
        // 0,0,0; the central top bulb sits directly below it at 0,-1,0).
        world.placeTemplateCells(fusion, center, cell -> cell.dy() >= -5);

        Location frame = center.clone();
        Location attached = center.clone().add(0, -1, 0);
        assertEquals(Material.WAXED_COPPER_BULB, world.world()
                .getBlockAt(attached.getBlockX(), attached.getBlockY(), attached.getBlockZ()).getType());

        StructureTemplate.BestCandidate owning = StructureTemplate.findOwningCandidate(
                List.of(fusion, nuclear, lightning), frame, 5, attached);

        assertNotNull(owning, "the frame on the central bulb belongs to a template");
        assertEquals(fusion, owning.template(), "the half-built DFC must win by body coverage");
        assertTrue(owning.result().solidPercent() >= StructureTemplate.CLAIM_MIN_SOLID_PERCENT,
                "the test build must exceed the claim threshold, got "
                        + owning.result().solidPercent() + "%");
        assertTrue(StructureTemplate.claimsFrame(owning, attached));
    }

    /** A complete DFC also claims its frame (the exact-match path runs even earlier). */
    @Test
    void completeDarkFusionReactorClaimsTheFrame() throws Exception {
        StructureTemplate fusion = load("darkfusionreactor");
        FakeWorld world = new FakeWorld();
        Location center = new Location(world.world(), 0, 80, 0);
        world.placeTemplate(fusion, center);

        Location frame = center.clone();
        Location attached = center.clone().add(0, -1, 0);
        StructureTemplate.BestCandidate owning = StructureTemplate.findOwningCandidate(
                List.of(fusion), frame, 5, attached);

        assertNotNull(owning);
        assertEquals(fusion, owning.template());
        assertEquals(100, owning.result().solidPercent());
        assertTrue(StructureTemplate.claimsFrame(owning, attached));
    }

    /**
     * The battery trigger (waxed copper grate) is a DFC cell too: a frame on the
     * reactor's grate must claim the reactor, not start a battery.
     */
    @Test
    void halfBuiltDarkFusionReactorClaimsAGrateFrame() throws Exception {
        StructureTemplate fusion = load("darkfusionreactor");
        StructureTemplate nuclear = load("nuclearreactor");
        StructureTemplate lightning = load("lightning_str");

        FakeWorld world = new FakeWorld();
        Location center = new Location(world.world(), 0, 80, 0);
        world.placeTemplateCells(fusion, center, cell -> cell.dy() >= -5);

        // The DFC's own grate at rel (0,-2,0); the frame hangs on it (the air
        // cell above belongs to the reactor interior, the exact frame placement
        // is irrelevant for the ownership lookup this test covers).
        Location attached = center.clone().add(0, -2, 0);
        assertEquals(Material.WAXED_COPPER_GRATE, world.world()
                .getBlockAt(attached.getBlockX(), attached.getBlockY(), attached.getBlockZ()).getType());
        Location frame = center.clone().add(0, -1, 0);

        StructureTemplate.BestCandidate owning = StructureTemplate.findOwningCandidate(
                List.of(fusion, nuclear, lightning), frame, 5, attached);

        assertNotNull(owning);
        assertEquals(fusion, owning.template(), "the grate belongs to the reactor body");
        assertTrue(StructureTemplate.claimsFrame(owning, attached),
                "a half-built reactor's grate must not start an adaptive battery");
    }

    /**
     * Trigger blocks that no template contains (the magnet's lodestone, the
     * generator's blast furnace) have NO owning candidate at all - the adaptive
     * branches are reached immediately and the search costs nothing.
     */
    @Test
    void nonTemplateTriggerBlocksHaveNoOwner() throws Exception {
        StructureTemplate fusion = load("darkfusionreactor");
        StructureTemplate nuclear = load("nuclearreactor");
        StructureTemplate lightning = load("lightning_str");

        FakeWorld world = new FakeWorld();
        world.put(0, 64, 0, Material.LODESTONE);
        Location attached = new Location(world.world(), 0, 64, 0);
        Location frame = new Location(world.world(), 0, 65, 0);

        assertNull(StructureTemplate.findOwningCandidate(
                List.of(fusion, nuclear, lightning), frame, 5, attached));
    }

    /**
     * A standalone electric lamp (one bulb + one chiseled copper junction, the
     * documented minimum) must NOT claim any template: the adaptive lamp branch
     * keeps assembling it.
     */
    @Test
    void standaloneLampDoesNotClaimATemplate() throws Exception {
        StructureTemplate fusion = load("darkfusionreactor");
        StructureTemplate nuclear = load("nuclearreactor");
        StructureTemplate lightning = load("lightning_str");

        FakeWorld world = new FakeWorld();
        Location bulb = new Location(world.world(), 20, 64, 20);
        world.put(bulb.getBlockX(), bulb.getBlockY(), bulb.getBlockZ(), Material.WAXED_COPPER_BULB);
        world.put(bulb.getBlockX(), bulb.getBlockY() - 1, bulb.getBlockZ(),
                Material.WAXED_CHISELED_COPPER);
        Location frame = bulb.clone().add(0, 1, 0);
        Location attached = bulb.clone();

        StructureTemplate.BestCandidate owning = StructureTemplate.findOwningCandidate(
                List.of(fusion, nuclear, lightning), frame, 5, attached);

        assertNotNull(owning, "a block can geometrically belong to a sparse template");
        assertTrue(owning.result().solidPercent() < StructureTemplate.CLAIM_MIN_SOLID_PERCENT,
                "a standalone lamp has almost no template body, got "
                        + owning.result().solidPercent() + "%");
        assertFalse(StructureTemplate.claimsFrame(owning, attached),
                "the electric lamp branch must stay in charge");
    }

    // =========================
    // CLAIM RULE DETAILS
    // =========================
    @Test
    void claimNeedsTheAttachedBlockToBeASolidCell() throws Exception {
        StructureTemplate nuclear = load("nuclearreactor");
        FakeWorld world = new FakeWorld();
        Location center = new Location(world.world(), 0, 64, 0);

        StructureTemplate.BestCandidate candidate = new StructureTemplate.BestCandidate(
                nuclear, new StructureTemplate.MatchResult(false, 27, 7, center,
                        List.of(), List.of(), 27, 7));

        // 20 of 27 solid cells placed = 74% -> claims the attached cell.
        assertTrue(StructureTemplate.claimsFrame(candidate, center.clone()));
        // (0,1,0) is outside the 3x3x3 cube -- never a solid cell.
        assertFalse(StructureTemplate.claimsFrame(candidate, center.clone().add(0, 1, 0)));
    }

    @Test
    void claimThresholdIs25PercentOfTheSolidBody() throws Exception {
        StructureTemplate nuclear = load("nuclearreactor");
        FakeWorld world = new FakeWorld();
        Location center = new Location(world.world(), 0, 64, 0);

        // 7 of 27 placed = 26% -> claimed.
        StructureTemplate.BestCandidate above = new StructureTemplate.BestCandidate(
                nuclear, new StructureTemplate.MatchResult(false, 27, 20, center,
                        List.of(), List.of(), 27, 20));
        assertTrue(StructureTemplate.claimsFrame(above, center.clone()));

        // 6 of 27 placed = 22% -> below the threshold, the adaptive branch wins.
        StructureTemplate.BestCandidate below = new StructureTemplate.BestCandidate(
                nuclear, new StructureTemplate.MatchResult(false, 27, 21, center,
                        List.of(), List.of(), 27, 21));
        assertFalse(StructureTemplate.claimsFrame(below, center.clone()));
    }

    // =========================
    // FAKE WORLD (dynamic proxies around Bukkit World/Block)
    // =========================
    private static final class FakeWorld {
        private final Map<String, Material> blocks = new HashMap<>();
        private final World world;

        FakeWorld() {
            final World[] holder = new World[1];
            holder[0] = (World) Proxy.newProxyInstance(
                    World.class.getClassLoader(),
                    new Class<?>[]{World.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "getBlockAt":
                                if (args != null && args.length == 3) {
                                    return block(holder[0], (int) args[0], (int) args[1], (int) args[2]);
                                }
                                if (args != null && args.length == 1 && args[0] instanceof Location loc) {
                                    return block(holder[0], loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                                }
                                return block(holder[0], 0, 0, 0);
                            case "equals":
                                return proxy == args[0];
                            case "hashCode":
                                return System.identityHashCode(proxy);
                            case "toString":
                                return "FakeWorld";
                            case "getName":
                                return "fake_world";
                            default:
                                return defaultValue(method.getReturnType());
                        }
                    });
            this.world = holder[0];
        }

        World world() { return world; }

        void put(int x, int y, int z, Material material) { blocks.put(key(x, y, z), material); }

        void remove(int x, int y, int z) { blocks.remove(key(x, y, z)); }

        /** Places every solid cell of the template, positioned at {@code center}. */
        void placeTemplate(StructureTemplate template, Location center) {
            placeTemplateCells(template, center, cell -> true);
        }

        /** Places the solid cells of the template selected by {@code filter}. */
        void placeTemplateCells(StructureTemplate template, Location center,
                                java.util.function.Predicate<StructureTemplate.BlockEntry> filter) {
            for (StructureTemplate.BlockEntry cell : template.getBlocks()) {
                if (!filter.test(cell)) continue;
                put(center.getBlockX() + cell.dx(),
                        center.getBlockY() + cell.dy(),
                        center.getBlockZ() + cell.dz(),
                        cell.material());
            }
        }

        private Block block(World world, int x, int y, int z) {
            return (Block) Proxy.newProxyInstance(
                    Block.class.getClassLoader(),
                    new Class<?>[]{Block.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "getType":
                                return blocks.getOrDefault(key(x, y, z), Material.AIR);
                            case "getLocation":
                                return new Location(world, x, y, z);
                            case "equals":
                                return proxy == args[0];
                            case "hashCode":
                                return System.identityHashCode(proxy);
                            case "toString":
                                return "FakeBlock(" + x + "," + y + "," + z + ")";
                            default:
                                return defaultValue(method.getReturnType());
                        }
                    });
        }

        private static String key(int x, int y, int z) { return x + "," + y + "," + z; }

        /** A type-compatible default so unhandled World/Block calls do not NPE the proxy. */
        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) return null;
            if (type == boolean.class) return false;
            if (type == byte.class) return (byte) 0;
            if (type == short.class) return (short) 0;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == float.class) return 0f;
            if (type == double.class) return 0d;
            if (type == char.class) return (char) 0;
            return null; // void
        }
    }
}

