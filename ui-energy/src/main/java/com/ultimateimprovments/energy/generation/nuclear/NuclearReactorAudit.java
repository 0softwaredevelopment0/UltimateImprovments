package com.ultimateimprovments.energy.generation.nuclear;

import com.ultimateimprovments.util.LocationUtil;
import com.ultimateimprovments.util.StructureTemplate;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NBT-driven structural audit of the compact nuclear reactor, modelled on the
 * DFC {@code ReactorDamageTracker}.
 * <p>
 * The 27 solid cells of the {@code nuclearreactor} template are indexed once
 * from the NBT. A per-anchor cache remembers which cells are missing, so the
 * tick can ask "is the structure whole?" without re-reading every block:
 * <ul>
 *   <li><b>Events</b> — {@link #noteBroken} / {@link #noteRepaired} update the
 *       cache in O(1) right from the break/place listener, so a player action
 *       is reflected instantly in the damage report and the all-gone check;</li>
 *   <li><b>Rotating audit</b> — {@link #audit} checks
 *       {@code nuclear_reactor.audit_cells_per_second} cells per pass
 *       (full sweep with the default 9 in 3 seconds) and catches event-blind
 *       block changes: explosions, pistons, plugin {@code setType()} — none of
 *       those fire BlockBreak/PlaceEvent.</li>
 * </ul>
 * The cache is seeded with one full pass on first use and dropped on
 * disassembly, exactly like the DFC audit.
 */
public final class NuclearReactorAudit {

    /** One solid template cell (anchor-relative) and its expected material. */
    private record Cell(int dx, int dy, int dz, Material material) {}

    /** Snapshot of one audit pass. */
    public record Result(int present, int total, boolean allMissing) {

        /** @return {@code true} when every template cell is in place */
        public boolean intact() {
            return total > 0 && present >= total;
        }
    }

    private static volatile Cell[] cells;

    /** Per-anchor cache: which cells are missing + the rotating cursor. */
    private static final class State {
        final BitSet missing = new BitSet();
        int cursor;
    }

    private static final Map<Location, State> states = new HashMap<>();

    private NuclearReactorAudit() {}

    // =========================
    // TEMPLATE INDEX (lazy, built once)
    // =========================

    private static void init() {
        if (cells != null) return;

        StructureTemplate template = StructureTemplate.get(NuclearReactorManager.TEMPLATE_NAME);
        if (template == null) {
            cells = new Cell[0];
            return;
        }

        List<Cell> list = new ArrayList<>();
        for (StructureTemplate.BlockEntry entry : template.getBlocks()) {
            list.add(new Cell(entry.dx(), entry.dy(), entry.dz(), entry.material()));
        }
        cells = list.toArray(new Cell[0]);
    }

    /** Total number of audited template cells. */
    public static synchronized int total() {
        init();
        return cells.length;
    }

    // =========================
    // STATE
    // =========================

    private static State stateFor(NuclearReactor reactor) {
        if (reactor == null) return null;
        Location anchor = reactor.getAnchor();
        if (anchor == null || anchor.getWorld() == null) return null;

        Location key = LocationUtil.normalize(anchor);
        if (key == null) return null;

        init();
        State state = states.get(key);
        if (state == null) {
            state = seed(reactor, key);
        }
        return state;
    }

    /** Full pass baseline — the cache starts empty (nothing missing) and is filled here. */
    private static State seed(NuclearReactor reactor, Location key) {
        State state = new State();
        Location anchor = reactor.getAnchor();
        World world = anchor.getWorld();
        int bx = anchor.getBlockX(), by = anchor.getBlockY(), bz = anchor.getBlockZ();

        for (int i = 0; i < cells.length; i++) {
            Cell cell = cells[i];
            if (!world.isChunkLoaded((bx + cell.dx()) >> 4, (bz + cell.dz()) >> 4)) continue;
            Material actual = world.getBlockAt(
                    bx + cell.dx(), by + cell.dy(), bz + cell.dz()).getType();
            if (actual != cell.material()) {
                state.missing.set(i);
            }
        }

        states.put(key, state);
        return state;
    }

    /** Drops the cache of one anchor (disassembly — the next use re-seeds). */
    public static synchronized void reset(Location anchor) {
        if (anchor == null) return;
        Location key = LocationUtil.normalize(anchor);
        if (key != null) {
            states.remove(key);
        }
    }

    // =========================
    // ROTATING AUDIT
    // =========================

    /**
     * Advances the rotating audit by {@code cellsPerPass} cells.
     * <p>
     * Unloaded chunks are skipped without state changes (blocks there cannot be
     * verified — reading them would sync-load the chunk). The first call for an
     * anchor seeds the cache with a full pass instead of rotating.
     */
    public static synchronized Result audit(NuclearReactor reactor, int cellsPerPass) {
        State state = stateFor(reactor);
        if (state == null || cells.length == 0) {
            return new Result(0, cells == null ? 0 : cells.length, false);
        }

        Location anchor = reactor.getAnchor();
        World world = anchor.getWorld();
        int bx = anchor.getBlockX(), by = anchor.getBlockY(), bz = anchor.getBlockZ();

        int steps = Math.min(Math.max(1, cellsPerPass), cells.length);
        for (int step = 0; step < steps; step++) {
            state.cursor = (state.cursor + 1) % cells.length;
            int index = state.cursor;
            Cell cell = cells[index];

            if (!world.isChunkLoaded((bx + cell.dx()) >> 4, (bz + cell.dz()) >> 4)) continue;

            Material actual = world.getBlockAt(
                    bx + cell.dx(), by + cell.dy(), bz + cell.dz()).getType();
            if (actual != cell.material()) {
                state.missing.set(index);
            } else {
                state.missing.clear(index);
            }
        }

        int present = cells.length - state.missing.cardinality();
        return new Result(present, cells.length, present <= 0);
    }

    // =========================
    // EVENT UPDATES (O(1))
    // =========================

    /** One template cell was broken by a player. */
    public static synchronized void noteBroken(NuclearReactor reactor, Location loc) {
        State state = stateFor(reactor);
        if (state == null) return;
        int index = indexOf(reactor, loc);
        if (index < 0) return;
        state.missing.set(index);
    }

    /** One template cell was placed back by a player. */
    public static synchronized void noteRepaired(NuclearReactor reactor, Location loc) {
        State state = stateFor(reactor);
        if (state == null) return;
        int index = indexOf(reactor, loc);
        if (index < 0) return;
        state.missing.clear(index);
    }

    /** Template index of the block at {@code loc}, or {@code -1} when not a cell. */
    private static int indexOf(NuclearReactor reactor, Location loc) {
        init();
        if (reactor == null || loc == null) return -1;
        Location anchor = reactor.getAnchor();
        if (anchor == null || anchor.getWorld() == null || loc.getWorld() == null) return -1;
        if (!anchor.getWorld().equals(loc.getWorld())) return -1;

        int dx = loc.getBlockX() - anchor.getBlockX();
        int dy = loc.getBlockY() - anchor.getBlockY();
        int dz = loc.getBlockZ() - anchor.getBlockZ();
        for (int i = 0; i < cells.length; i++) {
            Cell cell = cells[i];
            if (cell.dx() == dx && cell.dy() == dy && cell.dz() == dz) {
                return i;
            }
        }
        return -1;
    }

    // =========================
    // LOOKUPS
    // =========================

    /** Cached number of template cells still in place. */
    public static synchronized int presentCount(NuclearReactor reactor) {
        State state = stateFor(reactor);
        init();
        if (state == null) return 0;
        return cells.length - state.missing.cardinality();
    }

    /** Cached "structure is whole" check (no block reads). */
    public static synchronized boolean allPresent(NuclearReactor reactor) {
        State state = stateFor(reactor);
        init();
        return state != null && cells.length > 0 && state.missing.isEmpty();
    }
}
