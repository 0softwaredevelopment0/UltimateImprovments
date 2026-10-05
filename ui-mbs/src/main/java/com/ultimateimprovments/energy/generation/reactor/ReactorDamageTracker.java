package com.ultimateimprovments.energy.generation.reactor;

import com.ultimateimprovments.util.Materials;
import com.ultimateimprovments.util.StructureTemplate;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;

/**
 * NBT-driven per-block damage tracking for the Dark Fusion Core (DFC).
 * <p>
 * Every solid cell of the {@code darkfusionreactor} NBT template (anchor-relative)
 * is indexed into a damage category:
 * <ul>
 *   <li>{@link Category#GLASS} — case glass (98 cells),</li>
 *   <li>{@link Category#SIGN} — sign panels (17 cells),</li>
 *   <li>{@link Category#BULB} — copper control bulbs (12 cells),</li>
 *   <li>{@link Category#STRUCTURE} — everything else (copper, stairs, rods, barrels…).</li>
 * </ul>
 * Cell state is tracked through two paths:
 * <ul>
 *   <li><b>Events</b> — {@code noteCellBroken/noteCellRepaired} update the cached
 *       present-counts in O(1) right from the break/place listener;</li>
 *   <li><b>Rotating audit</b> — {@link #auditTick} checks
 *       {@value #AUDIT_CELLS_PER_TICK} cells per call (full pass over the
 *       ~1000-cell template in ~20 ticks) and catches event-blind block changes
 *       (explosions, pistons, plugin {@code block.setType()} — none of these fire
 *       BlockBreak/PlaceEvent). Transitions are reported by the manager through
 *       the normal damage/repair messages.</li>
 * </ul>
 * The reactor broadcasts localized "Attention!" damage/repair reports from the
 * cached counts and keeps running (uncontrolled) instead of tearing down when
 * blocks are broken.
 */
public final class ReactorDamageTracker {

    /** Damage categories reported by the Attention! messages. */
    public enum Category { GLASS, SIGN, BULB, STRUCTURE }

    /** "dx,dy,dz" → category (anchor-relative, from the NBT template). */
    private static Map<String, Category> index;

    /** "dx,dy,dz" → expected template material (anchor-relative). */
    private static Map<String, Material> materials;

    private static int glassTotal;
    private static int signTotal;
    private static int bulbTotal;
    private static int structTotal;

    private ReactorDamageTracker() {}

    // =========================
    // TEMPLATE INDEX (lazy, built once)
    // =========================
    private static synchronized void init() {
        if (index != null) return;

        StructureTemplate tmpl = StructureTemplate.get("darkfusionreactor");
        if (tmpl == null) {
            StructureTemplate.initAll();
            tmpl = StructureTemplate.get("darkfusionreactor");
        }

        Map<String, Category> map = new HashMap<>();
        Map<String, Material> mats = new HashMap<>();
        int glass = 0, sign = 0, bulb = 0, rest = 0;

        if (tmpl != null) {
            for (StructureTemplate.BlockEntry b : tmpl.getBlocks()) {
                // The template index contains solid cells only (air cells and
                // levers are skipped by the parser) — anything outside these
                // cells is NOT part of the tracked structure and must never
                // trigger damage/repair reports.
                String key = b.dx() + "," + b.dy() + "," + b.dz();
                Material m = b.material();
                Category cat;
                if (m == Material.GLASS) {
                    cat = Category.GLASS;
                    glass++;
                } else if (isSign(m)) {
                    cat = Category.SIGN;
                    sign++;
                } else if (m == Materials.WAXED_COPPER_BULB) {
                    cat = Category.BULB;
                    bulb++;
                } else {
                    cat = Category.STRUCTURE;
                    rest++;
                }
                map.put(key, cat);
                mats.put(key, m);
            }
        }

        glassTotal = glass;
        signTotal = sign;
        bulbTotal = bulb;
        structTotal = rest;
        materials = mats;
        index = map;
    }

    /** Any kind of sign (standing or wall, any wood type). */
    private static boolean isSign(Material m) {
        return m.name().endsWith("SIGN");
    }

    // =========================
    // LOOKUPS
    // =========================

    /**
     * Damage category of a template cell (anchor-relative coordinates),
     * or {@code null} for cells the template does not track.
     */
    public static Category categoryOf(int dx, int dy, int dz) {
        init();
        return index.get(dx + "," + dy + "," + dz);
    }

    /** Total tracked template cells in the category. */
    public static int totalOf(Category cat) {
        init();
        return switch (cat) {
            case GLASS -> glassTotal;
            case SIGN -> signTotal;
            case BULB -> bulbTotal;
            case STRUCTURE -> structTotal;
        };
    }

    // =========================
    // ROTATING AUDIT — live cell-state cache
    // =========================
    /** Cells checked per {@link #auditTick} call (full pass ≈ index/50 ticks). */
    public static final int AUDIT_CELLS_PER_TICK = 50;

    /** Audit outcome: categories that gained (damaged) or lost (repaired) missing cells this call. */
    public record AuditResult(java.util.List<Category> damaged, java.util.List<Category> repaired) {}

    private static final Category[] CATEGORIES = Category.values();
    /** Cached number of present (matching) cells per category. */
    private static final int[] auditPresent = new int[CATEGORIES.length];
    /** Keys of tracked cells currently missing in the world. */
    private static final java.util.Set<String> missingCells = new java.util.HashSet<>();
    /** Rotating key ring over the index (null until the first audit tick). */
    private static String[] auditKeys;
    private static int auditCursor;
    /** Reactor base the audit state belongs to (re-bind resets the cache). */
    private static Location auditBase;

    /** True when the template index is loaded and has tracked cells. */
    public static boolean isTracked() {
        init();
        return !index.isEmpty();
    }

    /**
     * Advances the rotating audit by {@value #AUDIT_CELLS_PER_TICK} cells.
     * The first call (and every re-bind to another reactor base) runs one full
     * pass so the cached counts start correct. Unloaded chunks are skipped
     * without state changes (blocks there cannot be verified — and reading
     * them would sync-load the chunk).
     */
    public static synchronized AuditResult auditTick(Location base) {
        init();
        if (index.isEmpty() || base == null || base.getWorld() == null) {
            return new AuditResult(java.util.List.of(), java.util.List.of());
        }
        if (auditKeys == null || !base.equals(auditBase)) {
            resetAudit();
            auditBase = base.clone();
            auditKeys = index.keySet().toArray(new String[0]);
            fullAuditPass(base);
        }

        java.util.List<Category> damaged = new java.util.ArrayList<>();
        java.util.List<Category> repaired = new java.util.ArrayList<>();
        World world = base.getWorld();
        int bx = base.getBlockX(), by = base.getBlockY(), bz = base.getBlockZ();
        int steps = Math.min(AUDIT_CELLS_PER_TICK, auditKeys.length);
        for (int i = 0; i < steps; i++) {
            auditCursor = (auditCursor + 1) % auditKeys.length;
            String key = auditKeys[auditCursor];
            String[] p = key.split(",");
            int dx = Integer.parseInt(p[0]);
            int dy = Integer.parseInt(p[1]);
            int dz = Integer.parseInt(p[2]);
            if (!world.isChunkLoaded((bx + dx) >> 4, (bz + dz) >> 4)) continue;

            Category cat = index.get(key);
            boolean present = matches(dx, dy, dz, cat, world.getBlockAt(bx + dx, by + dy, bz + dz).getType());
            boolean knownMissing = missingCells.contains(key);
            if (!present && !knownMissing) {
                missingCells.add(key);
                auditPresent[cat.ordinal()]--;
                if (!damaged.contains(cat)) damaged.add(cat);
            } else if (present && knownMissing) {
                missingCells.remove(key);
                auditPresent[cat.ordinal()]++;
                if (!repaired.contains(cat)) repaired.add(cat);
            }
        }
        return new AuditResult(damaged, repaired);
    }

    /** One-time (per bind) full pass: seeds the cached counts from the world. */
    private static void fullAuditPass(Location base) {
        missingCells.clear();
        java.util.Arrays.fill(auditPresent, 0);
        World world = base.getWorld();
        int bx = base.getBlockX(), by = base.getBlockY(), bz = base.getBlockZ();
        for (Map.Entry<String, Category> e : index.entrySet()) {
            String[] p = e.getKey().split(",");
            int dx = Integer.parseInt(p[0]);
            int dy = Integer.parseInt(p[1]);
            int dz = Integer.parseInt(p[2]);
            boolean present = matches(dx, dy, dz, e.getValue(), world.getBlockAt(bx + dx, by + dy, bz + dz).getType());
            if (present) auditPresent[e.getValue().ordinal()]++;
            else missingCells.add(e.getKey());
        }
    }

    /**
     * O(1) cache update from the break listener: one tracked cell disappeared.
     * No-op when the audit was not initialized yet (the first full pass will
     * pick the real state up).
     */
    public static synchronized void noteCellBroken(int dx, int dy, int dz) {
        if (auditKeys == null) return;
        String key = dx + "," + dy + "," + dz;
        Category cat = index.get(key);
        if (cat != null && missingCells.add(key)) auditPresent[cat.ordinal()]--;
    }

    /** O(1) cache update from the place listener: one tracked cell is back. */
    public static synchronized void noteCellRepaired(int dx, int dy, int dz) {
        if (auditKeys == null) return;
        String key = dx + "," + dy + "," + dz;
        Category cat = index.get(key);
        if (cat != null && missingCells.remove(key)) auditPresent[cat.ordinal()]++;
    }

    /** Cached {@code {present, total}} for one category — the (left/total) message placeholders. */
    public static synchronized int[] cachedCount(Category cat) {
        return new int[]{ auditPresent[cat.ordinal()], totalOf(cat) };
    }

    /** True when no tracked cell is currently missing. */
    public static synchronized boolean cachedAllPresent() {
        return missingCells.isEmpty();
    }

    /** Drops the audit cache (teardown/reassembly — the next tick re-seeds it). */
    public static synchronized void resetAudit() {
        auditKeys = null;
        auditCursor = 0;
        auditBase = null;
        missingCells.clear();
        java.util.Arrays.fill(auditPresent, 0);
    }

    // =========================
    // MATCHING
    // =========================
    private static boolean matches(int dx, int dy, int dz, Category cat, Material actual) {
        return switch (cat) {
            case GLASS -> actual == Material.GLASS;
            case SIGN -> isSign(actual);
            case BULB -> actual == Materials.WAXED_COPPER_BULB;
            case STRUCTURE -> actual == materials.get(dx + "," + dy + "," + dz);
        };
    }
}
