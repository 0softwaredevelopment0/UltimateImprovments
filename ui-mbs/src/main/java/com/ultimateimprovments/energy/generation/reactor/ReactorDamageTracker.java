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
 *   <li>{@link Category#SIGN} — sign panels (16 cells),</li>
 *   <li>{@link Category#BULB} — copper control bulbs (12 cells),</li>
 *   <li>{@link Category#STRUCTURE} — everything else (copper, stairs, rods, barrels…).</li>
 * </ul>
 * {@link #scan(Location)} compares the world against the template cell-by-cell and
 * returns present/total per category — the reactor broadcasts localized
 * "Attention!" damage/repair reports from these numbers and keeps running
 * (uncontrolled) instead of tearing down when blocks are broken.
 */
public final class ReactorDamageTracker {

    /** Damage categories reported by the Attention! messages. */
    public enum Category { GLASS, SIGN, BULB, STRUCTURE }

    /** Cell-by-cell scan result: present template cells per category. */
    public record Snapshot(int glassPresent, int glassTotal,
                           int signPresent, int signTotal,
                           int bulbPresent, int bulbTotal,
                           int structPresent, int structTotal) {

        /** True when every tracked template cell matches the world. */
        public boolean allPresent() {
            return glassPresent >= glassTotal && signPresent >= signTotal
                    && bulbPresent >= bulbTotal && structPresent >= structTotal;
        }
    }

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

    /** Full cell-by-cell scan vs the NBT template; {@code null} when the template is unavailable. */
    public static Snapshot scan(Location base) {
        init();
        if (index.isEmpty() || base == null || base.getWorld() == null) return null;

        World world = base.getWorld();
        int bx = base.getBlockX(), by = base.getBlockY(), bz = base.getBlockZ();

        int gp = 0, sp = 0, bp = 0, rp = 0;
        for (Map.Entry<String, Category> e : index.entrySet()) {
            String[] p = e.getKey().split(",");
            int dx = Integer.parseInt(p[0]);
            int dy = Integer.parseInt(p[1]);
            int dz = Integer.parseInt(p[2]);
            Material actual = world.getBlockAt(bx + dx, by + dy, bz + dz).getType();
            if (matches(dx, dy, dz, e.getValue(), actual)) {
                switch (e.getValue()) {
                    case GLASS -> gp++;
                    case SIGN -> sp++;
                    case BULB -> bp++;
                    case STRUCTURE -> rp++;
                }
            }
        }
        return new Snapshot(gp, glassTotal, sp, signTotal, bp, bulbTotal, rp, structTotal);
    }

    /** {@code {present, total}} for one category — the (left/total) message placeholders. */
    public static int[] count(Location base, Category cat) {
        init();
        int total = totalOf(cat);
        if (index.isEmpty() || total == 0 || base == null || base.getWorld() == null) {
            return new int[]{ total, total };
        }

        World world = base.getWorld();
        int bx = base.getBlockX(), by = base.getBlockY(), bz = base.getBlockZ();

        int present = 0;
        for (Map.Entry<String, Category> e : index.entrySet()) {
            if (e.getValue() != cat) continue;
            String[] p = e.getKey().split(",");
            int dx = Integer.parseInt(p[0]);
            int dy = Integer.parseInt(p[1]);
            int dz = Integer.parseInt(p[2]);
            Material actual = world.getBlockAt(bx + dx, by + dy, bz + dz).getType();
            if (matches(dx, dy, dz, cat, actual)) present++;
        }
        return new int[]{ present, total };
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
