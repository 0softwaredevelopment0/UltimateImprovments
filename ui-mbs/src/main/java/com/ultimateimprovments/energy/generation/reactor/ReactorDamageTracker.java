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
    // ROTATING AUDIT — live cell-state cache (per reactor base)
    // =========================
    /** Cells checked per {@link #auditTick} call (full pass ≈ index/50 ticks). */
    public static final int AUDIT_CELLS_PER_TICK = 50;

    /** Audit outcome: categories that gained (damaged) or lost (repaired) missing cells this call. */
    public record AuditResult(java.util.List<Category> damaged, java.util.List<Category> repaired) {}

    private static final Category[] CATEGORIES = Category.values();

    /** Per-reactor audit cache (multi-reactor support: one state per anchor). */
    private static final class AuditState {
        final int[] present = new int[CATEGORIES.length];
        final java.util.Set<String> missing = new java.util.HashSet<>();
        String[] keys;
        int cursor;
    }

    /** Reactor anchor → its audit cache. */
    private static final Map<Location, AuditState> audits = new HashMap<>();

    /** True when the template index is loaded and has tracked cells. */
    public static boolean isTracked() {
        init();
        return !index.isEmpty();
    }

    /** Seeds the audit state for one base with a full pass over the template. */
    private static AuditState seedState(Location base) {
        AuditState st = new AuditState();
        World world = base.getWorld();
        int bx = base.getBlockX(), by = base.getBlockY(), bz = base.getBlockZ();
        for (Map.Entry<String, Category> e : index.entrySet()) {
            String[] p = e.getKey().split(",");
            int dx = Integer.parseInt(p[0]);
            int dy = Integer.parseInt(p[1]);
            int dz = Integer.parseInt(p[2]);
            boolean present = matches(dx, dy, dz, e.getValue(), world.getBlockAt(bx + dx, by + dy, bz + dz).getType());
            if (present) st.present[e.getValue().ordinal()]++;
            else st.missing.add(e.getKey());
        }
        st.keys = index.keySet().toArray(new String[0]);
        audits.put(base.clone(), st);
        return st;
    }

    /** Audit cache for the base, seeded on first use (null when the template is empty). */
    private static synchronized AuditState stateFor(Location base) {
        if (base == null) return null;
        AuditState st = audits.get(base);
        if (st == null && isTracked()) st = seedState(base);
        return st;
    }

    /**
     * Advances the rotating audit by {@value #AUDIT_CELLS_PER_TICK} cells.
     * Every reactor base keeps its own cache. Unloaded chunks are skipped
     * without state changes (blocks there cannot be verified — and reading
     * them would sync-load the chunk).
     */
    public static synchronized AuditResult auditTick(Location base) {
        init();
        if (index.isEmpty() || base == null || base.getWorld() == null) {
            return new AuditResult(java.util.List.of(), java.util.List.of());
        }
        AuditState st = stateFor(base);
        if (st == null) {
            return new AuditResult(java.util.List.of(), java.util.List.of());
        }

        java.util.List<Category> damaged = new java.util.ArrayList<>();
        java.util.List<Category> repaired = new java.util.ArrayList<>();
        World world = base.getWorld();
        int bx = base.getBlockX(), by = base.getBlockY(), bz = base.getBlockZ();
        int steps = Math.min(AUDIT_CELLS_PER_TICK, st.keys.length);
        for (int i = 0; i < steps; i++) {
            st.cursor = (st.cursor + 1) % st.keys.length;
            String key = st.keys[st.cursor];
            String[] p = key.split(",");
            int dx = Integer.parseInt(p[0]);
            int dy = Integer.parseInt(p[1]);
            int dz = Integer.parseInt(p[2]);
            if (!world.isChunkLoaded((bx + dx) >> 4, (bz + dz) >> 4)) continue;

            Category cat = index.get(key);
            boolean present = matches(dx, dy, dz, cat, world.getBlockAt(bx + dx, by + dy, bz + dz).getType());
            boolean knownMissing = st.missing.contains(key);
            if (!present && !knownMissing) {
                st.missing.add(key);
                st.present[cat.ordinal()]--;
                if (!damaged.contains(cat)) damaged.add(cat);
            } else if (present && knownMissing) {
                st.missing.remove(key);
                st.present[cat.ordinal()]++;
                if (!repaired.contains(cat)) repaired.add(cat);
            }
        }
        return new AuditResult(damaged, repaired);
    }

    /**
     * O(1) cache update from the break listener: one tracked cell disappeared.
     * No-op when the audit was not initialized yet (the first tick/lookup seeds it).
     */
    public static synchronized void noteCellBroken(Location base, int dx, int dy, int dz) {
        if (base == null) return;
        AuditState st = audits.get(base);
        if (st == null) return;
        String key = dx + "," + dy + "," + dz;
        Category cat = index.get(key);
        if (cat != null && st.missing.add(key)) st.present[cat.ordinal()]--;
    }

    /** O(1) cache update from the place listener: one tracked cell is back. */
    public static synchronized void noteCellRepaired(Location base, int dx, int dy, int dz) {
        if (base == null) return;
        AuditState st = audits.get(base);
        if (st == null) return;
        String key = dx + "," + dy + "," + dz;
        Category cat = index.get(key);
        if (cat != null && st.missing.remove(key)) st.present[cat.ordinal()]++;
    }

    /** Cached {@code {present, total}} for one category — the (left/total) message placeholders. */
    public static synchronized int[] cachedCount(Location base, Category cat) {
        AuditState st = stateFor(base);
        int present = st != null ? st.present[cat.ordinal()] : 0;
        return new int[]{ present, totalOf(cat) };
    }

    /** True when no tracked cell is currently missing. */
    public static synchronized boolean cachedAllPresent(Location base) {
        AuditState st = stateFor(base);
        return st == null || st.missing.isEmpty();
    }

    /** Drops the audit cache of one base (teardown/reassembly — the next tick re-seeds it). */
    public static synchronized void resetAudit(Location base) {
        if (base != null) audits.remove(base);
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
