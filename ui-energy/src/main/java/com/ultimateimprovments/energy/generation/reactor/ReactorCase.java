package com.ultimateimprovments.energy.generation.reactor;

import com.ultimateimprovments.util.StructuresMessages;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;

import java.util.Random;

/**
 * DFC case system — the outer glass protection of the reactor.
 * <p>
 * The case has three parameters:
 * <ul>
 *   <li><b>Temperature (T)</b>, C*: −273..10 000. Rises only while fusion is
 *       running (+15 C* per sec at 150% speed, scaled by the speed).</li>
 *   <li><b>Pressure (P)</b>, MPa: 0..15, follows the case temperature the same
 *       way the shield pressure follows the core temperature.</li>
 *   <li><b>Integrity (I)</b>, %: <b>the live glass count</b> — every one of the
 *       98 template panes is one point on the 100%..0% scale. Over-temp or
 *       over-pressure degradation breaks one random intact pane per 1% of
 *       decay, so the case falls apart gradually; every pane a player places
 *       back raises the integrity by one point. No passive recovery.</li>
 * </ul>
 * A breach (any missing pane) vents the case: every missing cell drains the
 * pressure at (broken/total) × 100% of the max per second and the temperature
 * at half that rate (floor −273 C*) — the fusion heat rise never outruns the
 * drain, so a breached case trends to ambient and stays there until repaired.
 * There is NO hard shatter and NO reset: a fully broken case is just every
 * pane gone, venting at full scale.
 * <p>
 * Case messaging is exactly three events: breach detected (first pane broken),
 * case failure (fully broken, any cause, once per episode), breach restored
 * (fully repaired). Players repair pane by pane; {@link #repair(Location,
 * boolean)} is the instant full-repair path (DB load / admin).
 */
public class ReactorCase {

    public enum State { OK, BROKEN }

    /**
     * Glass wall offsets relative to the anchor — 98 cells, parsed from
     * darkfusionreactor.nbt (template y 1..7 → dy −8..−2). The old hardcoded
     * table was shifted one block down (dy −9..−3), so shatter/repair/
     * auto-repair operated on cells that are not glass in the template and
     * missed the real ones.
     */
    public static final int[][] GLASS = loadGlassFromTemplate();

    /** Builds the glass table from the NBT template (fallback: hardcoded ring). */
    private static int[][] loadGlassFromTemplate() {
        try {
            com.ultimateimprovments.util.StructureTemplate tmpl =
                    com.ultimateimprovments.util.StructureTemplate.get("darkfusionreactor");
            if (tmpl == null) {
                com.ultimateimprovments.util.StructureTemplate.initAll();
                tmpl = com.ultimateimprovments.util.StructureTemplate.get("darkfusionreactor");
            }
            if (tmpl != null) {
                java.util.List<int[]> list = new java.util.ArrayList<>();
                for (com.ultimateimprovments.util.StructureTemplate.BlockEntry b : tmpl.getBlocks()) {
                    if (b.material() == Material.GLASS) {
                        list.add(new int[]{ b.dx(), b.dy(), b.dz() });
                    }
                }
                if (!list.isEmpty()) return list.toArray(new int[0][]);
            }
        } catch (Exception | LinkageError ignored) {
            // fall through to the static fallback
        }
        // Static fallback (NBT unavailable): the full glass ring, dy −8..−2
        java.util.List<int[]> list = new java.util.ArrayList<>();
        for (int dy = -8; dy <= -2; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                list.add(new int[]{ dx, dy, -4 });
                list.add(new int[]{ dx, dy, 4 });
            }
        }
        return list.toArray(new int[0][]);
    }

    private final ReactorManager reactor;
    private final Random random = new Random();

    private State state = State.OK;
    private int temp = -273;          // C*, -273..10000
    private double press;             // kPa, 0..15000 (shown as MPa)
    private int integrity = 100;      // 0..100 %, mirrors the live glass count
    private double jitterPress;      // smoothed jitter value for the venting display

    // =========================
    // BREACH STATE — the three-event messaging (breach detected / case
    // failure / breach restored) plus the vent accumulators. The audit needs
    // up to a full pass (~1s) to see template-driven repairs, so a fresh
    // repair holds all breach announcements until it settles.
    // =========================
    private boolean breachAnnounced;  // the breach message fired (glass missing)
    private boolean failureAnnounced; // the full-failure message fired this episode
    private int ventGraceTicks;       // post-repair holdoff while the audit catches up
    private double ventPressRemainder; // fractional pressure vent accumulator (kPa)
    private double ventTempRemainder;  // fractional temperature vent accumulator (C*)

    public ReactorCase(ReactorManager reactor) {
        this.reactor = reactor;
    }

    // =========================
    // CASE TICK (every server tick)
    // =========================
    public void tick(Location base) {
        ReactorConfig cfg = ReactorConfig.getInstance();

        // =========================
        // TEMPERATURE — rises only from fusion (speed-scaled). A breached
        // case loses it faster than fusion can add (see tickBreach), so a
        // holed case trends to ambient on its own — no state switch needed.
        // =========================
        double fusionSpeed = reactor.getFusion().getSpeedPct();
        if (fusionSpeed > 0) {
            // 150% speed → +15 C*/sec (case_heat_rate), scaled by speed
            double perTick = fusionSpeed / 150.0 * cfg.getCaseHeatRate() / 20.0;
            int whole = (int) perTick;
            caseTempRemainder += perTick - whole;
            if (caseTempRemainder >= 1) { whole += 1; caseTempRemainder -= 1; }
            temp = Math.min(cfg.getCaseTempMax(), temp + whole);
        }

        // =========================
        // PRESSURE — follows the case temperature (like the shield follows T)
        // =========================
        double target = pressureTarget(temp, cfg);
        press = Math.max(0, press + (target - press) * cfg.getCasePressFollowRate());

        // =========================
        // BREACH VENT — broken glass cells vent the case (pressure drains at
        // broken/total × 100% of max per second, temperature at half that
        // rate); the integrity is re-derived from the live glass and the
        // breach/failure/restore messages fire here
        // =========================
        tickBreach(base, cfg);

        // =========================
        // INTEGRITY — decays above the safe thresholds: every whole 1% of
        // decay breaks one random intact pane (98 panes = the whole scale),
        // so the case falls apart gradually instead of shattering at 0%.
        // =========================
        boolean over = temp >= cfg.getCaseIntDecayTemp()
                || press >= cfg.getCaseIntDecayPress() * 1000.0; // config MPa → kPa
        if (over) {
            double ratePerTick = cfg.getCaseIntDecayRate() / 20.0;
            caseIntRemainder += ratePerTick;
            int whole = (int) caseIntRemainder;
            if (whole > 0) {
                caseIntRemainder -= whole;
                breakRandomPanes(base, whole);
            }
        }
    }

    /**
     * Breach tick: vent physics, the integrity derivation and the three case
     * messages (breach detected / case failure / breach restored), driven by
     * the damage tracker's live glass counts. The tracker cache is updated
     * O(1) by the break/place listeners and by {@link #breakRandomPanes}, so
     * the counts are exact up to one audit pass after plugin-made changes.
     */
    private void tickBreach(Location base, ReactorConfig cfg) {
        if (!ReactorDamageTracker.isTracked()) return;

        int[] glass = ReactorDamageTracker.cachedCount(base, ReactorDamageTracker.Category.GLASS);
        int total = glass[1];
        if (total <= 0) return;
        int broken = total - glass[0];

        if (ventGraceTicks > 0) {
            // A repair() filled the glass with block.setType() (no place
            // events) — the rotating audit needs a pass to reflect it; hold
            // the derivation and the announcements until it settles.
            ventGraceTicks--;
            return;
        }

        // The integrity IS the glass: every pane is one point on the
        // 100%..0% scale — repairs and breaks move it 1:1.
        integrity = (int) Math.round(glass[0] * 100.0 / total);
        state = broken >= total ? State.BROKEN : State.OK;

        // Vent physics — proportional to the breach scale, at ANY breach
        // size: pressure drains at (broken/total) × 100% of max per second,
        // the temperature at half that rate (floor −273 C*). A fully open
        // case vents out within a second or two and stays ambient until
        // repaired — no hard reset, the vent replaces the old shatter snap.
        if (broken > 0) {
            double frac = broken / (double) total;

            ventPressRemainder += frac * cfg.getCasePressMax() * 1000.0 / 20.0;
            int pressDrain = (int) ventPressRemainder;
            ventPressRemainder -= pressDrain;
            if (pressDrain > 0) press = Math.max(0, press - pressDrain);

            ventTempRemainder += frac * cfg.getCaseTempMax() * 0.5 / 20.0;
            int tempDrain = (int) ventTempRemainder;
            ventTempRemainder -= tempDrain;
            if (tempDrain > 0) temp = Math.max(-273, temp - tempDrain);
        }

        // Messaging — exactly three events.
        if (broken >= total) {
            if (!failureAnnounced) {
                failureAnnounced = true;
                breachAnnounced = true;
                reactor.broadcastRaw(StructuresMessages.get("case_failure_detected",
                        "<gold>Attention! <white>DFC case failure detected!"));
            }
        } else if (broken > 0) {
            failureAnnounced = false;
            if (!breachAnnounced) {
                breachAnnounced = true;
                reactor.broadcastRaw(StructuresMessages.get("case_breach_detected",
                        "<gold>Attention! <white>DFC case breach detected!"));
            }
        } else if (breachAnnounced || failureAnnounced) {
            // Every pane is back: restored (the last placed pane lands here).
            breachAnnounced = false;
            failureAnnounced = false;
            reactor.broadcastRaw(StructuresMessages.get("case_breach_restored",
                    "<gold>Attention! <white>DFC case breach restored."));
        }
    }

    /**
     * Case pressure target in <b>kPa</b> — scales with the case temperature:
     * 12 000 kPa at the 10 000 C* melting point (= 12 MPa, just below the
     * 15 MPa burst threshold). The case temperature is 10000 C* vs the core's
     * 10M C*, so the case pressure scale is 1000× the core pressure scale —
     * keep the kPa unit everywhere internally and convert to MPa only for
     * the display.
     */
    static double pressureTarget(int temp, ReactorConfig cfg) {
        double maxTemp = Math.max(1, cfg.getCaseTempMax());
        double t = Math.max(0, temp) / maxTemp;
        return t * cfg.getCasePressMax() * 1000.0;
    }

    // =========================
    // GRADUAL DEGRADATION / REPAIR
    // =========================
    /**
     * Gradual degradation: breaks up to {@code count} random intact panes
     * (1 pane = 1 point of the 98-pane integrity scale). The breach tick
     * re-derives the integrity from the live counts and runs the vent and
     * the messages — no full shatter, no reset, the case just loses panes
     * one by one while the decay conditions hold.
     */
    private void breakRandomPanes(Location base, int count) {
        java.util.List<int[]> intact = new java.util.ArrayList<>();
        for (int[] off : GLASS) {
            if (base.clone().add(off[0], off[1], off[2]).getBlock().getType() == Material.GLASS) {
                intact.add(off);
            }
        }
        if (intact.isEmpty()) return;

        java.util.Collections.shuffle(intact, random);
        int n = Math.min(count, intact.size());
        for (int i = 0; i < n; i++) {
            int[] off = intact.get(i);
            base.clone().add(off[0], off[1], off[2]).getBlock().setType(Material.AIR);
            // setType fires no events — keep the audit cache exact (O(1))
            ReactorDamageTracker.noteCellBroken(base, off[0], off[1], off[2]);
        }

        Location core = base.clone().add(0.5, -5.5, 0.5);
        base.getWorld().playSound(core, Sound.BLOCK_GLASS_BREAK, SoundCategory.MASTER, 2.0f, 0.8f);
        reactor.saveToDb();
    }

    /**
     * Full instant repair: restores every missing pane and reseeds the
     * integrity to 100% (the breach tick re-derives it from the audit once
     * the grace window passes). Admin/DB path — players repair pane by pane
     * and the integrity follows the glass automatically.
     */
    public boolean repair(Location base) {
        return repair(base, true);
    }

    /**
     * Full instant repair, optionally silent — the DB-load path restores the
     * glass blocks without announcing a player-facing repair.
     */
    public boolean repair(Location base, boolean announce) {
        boolean anyMissing = false;
        for (int[] off : GLASS) {
            Block block = base.clone().add(off[0], off[1], off[2]).getBlock();
            if (block.getType() != Material.GLASS) {
                anyMissing = true;
                block.setType(Material.GLASS);
            }
        }
        if (!anyMissing) return false;

        integrity = 100;
        state = State.OK;
        breachAnnounced = false;
        failureAnnounced = false;
        // The glass was filled with block.setType() — hold the breach
        // announcements (and the derivation) while the audit catches up.
        ventGraceTicks = 60;

        Location core = base.clone().add(0.5, -5.5, 0.5);
        base.getWorld().playSound(core, Sound.BLOCK_GLASS_PLACE, SoundCategory.MASTER, 2.0f, 1.0f);
        if (announce) {
            reactor.broadcastRaw(StructuresMessages.get("case_breach_restored",
                    "<gold>Attention! <white>DFC case breach restored."));
        }

        reactor.saveToDb();
        return true;
    }

    /**
     * A player placed a glass pane into the structure (the place path already
     * updated the audit cache O(1)). The breach tick re-derives the integrity
     * from the live counts — every pane raises it by one point, the last pane
     * fires the breach-restored broadcast. Only the progress is persisted here.
     */
    public void onGlassPlaced() {
        reactor.saveToDb();
    }

    // =========================
    // RESET
    // =========================
    public void reset() {
        state = State.OK;
        temp = -273;
        press = 0;
        integrity = 100;
        jitterPress = 0;
        breachAnnounced = false;
        failureAnnounced = false;
        ventGraceTicks = 0;
        ventPressRemainder = 0;
        ventTempRemainder = 0;
        caseTempRemainder = 0;
        caseIntRemainder = 0;
    }

    // =========================
    // GETTERS / SETTERS (persistence)
    // =========================
    public State getState() { return state; }
    public void setState(State val) { state = val; }

    public int getTemp() { return temp; }
    public void setTemp(int val) {
        temp = Math.max(-273, Math.min(10000, val));
    }

    public double getPress() { return press; }
    /** Sets press, clamped to 0..case_press_max (kPa). */
    public void setPress(double val) { press = Math.max(0, Math.min(15000, val)); }

    public int getIntegrity() { return integrity; }
    /** DB-load seed only — the breach tick re-derives the integrity from the live glass. */
    public void setIntegrity(int val) {
        integrity = Math.max(0, Math.min(100, val));
    }

    /**
     * Quiet resume after a DB load: a saved breached case does not re-announce
     * on startup; it re-announces only on a new episode (or restores normally).
     */
    public void setBreachAnnounced(boolean val) {
        breachAnnounced = val;
        failureAnnounced = val;
    }

    public boolean isBroken() { return state == State.BROKEN; }

    private double caseTempRemainder;
    private double caseIntRemainder;
}
