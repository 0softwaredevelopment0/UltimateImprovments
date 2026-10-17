package com.ultimateimprovments.energy.generation.reactor;

import com.ultimateimprovments.util.StructuresMessages;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Random;

/**
 * DFC case system — the outer glass protection of the reactor.
 * <p>
 * The case has three parameters:
 * <ul>
 *   <li><b>Temperature (T)</b>, C*: −273..10 000. Rises only while fusion is
 *       running (+15 C* per sec at 150% speed, scaled by the speed), no
 *       passive cooling below the fusion minimum temperature.</li>
 *   <li><b>Pressure (P)</b>, MPa: 0..15, follows the case temperature the same
 *       way the shield pressure follows the core temperature. At 15 MPa the
 *       glass shatters and the pressure vents to ~0 (jitters around zero).</li>
 *   <li><b>Integrity (I)</b>, %: degrades while over-temperature/over-pressure
 *       (like the shield); at 0% the glass shatters. Does NOT recover
 *       passively — repairing the glass restores the integrity.</li>
 * </ul>
 * A PARTIAL breach vents too: every missing glass cell lets the pressure drain
 * at (broken/total) × 100% of the max pressure per second and the temperature
 * at half that rate (floor −273 C*) — a holed case cannot hold its heat or
 * pressure. A full physical destruction (every glass cell gone by any cause)
 * breaks the case just like the integrity shatter.
 * <p>
 * Case messaging is exactly three events: breach detected (first pane broken),
 * case failure (fully broken, any cause), breach restored (fully repaired).
 * Broken glass is replaced with air; repairing (right-click a broken glass
 * position with glass in hand, or the repair command) restores the blocks and
 * resets the integrity to 100%. The shatter vents heat and pressure TOGETHER:
 * the case temperature resets to the ambient −273 C* and jitters around it
 * while broken, the pressure jitters around ~0 — so the freshly repaired
 * glass starts cold and cannot re-break on its own.
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
    private double press;             // MPa, 0..15
    private int integrity = 100;      // 0..100 %
    private double jitterPress;      // smoothed jitter value for the venting display

    // =========================
    // BREACH STATE — the three-event messaging (breach detected / case
    // failure / breach restored) plus the vent accumulators. The audit needs
    // up to a full pass (~1s) to see template-driven repairs, so a fresh
    // repair holds all breach announcements until it settles.
    // =========================
    private boolean breachAnnounced;  // the breach message fired (OK + glass missing)
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
        // TEMPERATURE — rises only from fusion (speed-scaled)
        // =========================
        if (state == State.OK) {
            double fusionSpeed = reactor.getFusion().getSpeedPct();
            if (fusionSpeed > 0) {
                // 150% speed → +15 C*/sec (case_heat_rate), scaled by speed
                double perTick = fusionSpeed / 150.0 * cfg.getCaseHeatRate() / 20.0;
                int whole = (int) perTick;
                caseTempRemainder += perTick - whole;
                if (caseTempRemainder >= 1) { whole += 1; caseTempRemainder -= 1; }
                temp = Math.min(cfg.getCaseTempMax(), temp + whole);
            }
        } else {
            // Broken: the vented case stays at ambient — small jitter around −273
            temp = -273 + random.nextInt(80);
        }

        // =========================
        // PRESSURE — follows the case temperature (like the shield follows T)
        // =========================
        double target = pressureTarget(temp, cfg);
        if (state == State.OK) {
            press = Math.max(0, press + (target - press) * cfg.getCasePressFollowRate());
        } else {
            // Broken: pressure vented — small jitter around ~0 (kPa scale!
            // the old 0.15 was 150 Pa — effectively zero anyway, but the
            // display rounded 0.0..0.15 kPa to MPa as 0.000..0.0002).
            jitterPress += (0 - jitterPress) * 0.3;
            press = Math.max(0, random.nextDouble() * 0.15 + jitterPress);
        }

        // =========================
        // BREACH VENT — broken glass cells vent the case (pressure drains at
        // broken/total × 100% of max per second, temperature at half that
        // rate) and the breach/failure/restore messages fire here
        // =========================
        tickBreach(base, cfg);

        // =========================
        // INTEGRITY — decays above the safe thresholds (like the shield)
        // =========================
        if (state == State.OK) {
            boolean over = temp >= cfg.getCaseIntDecayTemp()
                    || press >= cfg.getCaseIntDecayPress() * 1000.0; // config MPa → kPa
            if (over && integrity > 0) {
                double ratePerTick = cfg.getCaseIntDecayRate() / 20.0;
                caseIntRemainder += ratePerTick;
                int whole = (int) caseIntRemainder;
                if (whole > 0) {
                    caseIntRemainder -= whole;
                    integrity = Math.max(0, integrity - whole);
                    if (integrity <= 0) {
                        shatterGlass(base, cfg);
                        return;
                    }
                }
            }
        }
    }

    /**
     * Breach tick: vent physics and the three case messages (breach detected /
     * case failure / breach restored), driven by the damage tracker's live
     * glass counts. Full-physical-destruction detection lives here too — an
     * explosion that takes out every pane must break the case just like the
     * integrity shatter does.
     */
    private void tickBreach(Location base, ReactorConfig cfg) {
        if (!ReactorDamageTracker.isTracked()) return;

        int[] glass = ReactorDamageTracker.cachedCount(base, ReactorDamageTracker.Category.GLASS);
        int total = glass[1];
        if (total <= 0) return;
        int broken = total - glass[0];

        if (ventGraceTicks > 0) {
            // A repair()/checkAutoRepair() filled the glass with
            // block.setType() (no place events) — the rotating audit needs a
            // pass to reflect it; announce nothing until it settles.
            ventGraceTicks--;
            return;
        }

        if (state != State.OK) return; // BROKEN already vents to ambient via its jitter model

        // Full failure — every glass cell gone (any cause): the same terminal
        // state as the integrity shatter, same single message.
        if (broken >= total) {
            shatterGlass(base, cfg);
            return;
        }

        if (broken > 0) {
            // Vent physics: the hole scale drives the drain — pressure loses
            // (broken/total) × 100% of the max pressure per second, the
            // temperature the same formula at half the rate (floor −273 C*).
            // The fusion heat rise (+15 C*/s at 150% speed) never outruns the
            // drain, so any breach trends the case toward ambient.
            double frac = broken / (double) total;

            ventPressRemainder += frac * cfg.getCasePressMax() * 1000.0 / 20.0;
            int pressDrain = (int) ventPressRemainder;
            ventPressRemainder -= pressDrain;
            if (pressDrain > 0) press = Math.max(0, press - pressDrain);

            ventTempRemainder += frac * cfg.getCaseTempMax() * 0.5 / 20.0;
            int tempDrain = (int) ventTempRemainder;
            ventTempRemainder -= tempDrain;
            if (tempDrain > 0) temp = Math.max(-273, temp - tempDrain);

            if (!breachAnnounced) {
                breachAnnounced = true;
                reactor.broadcastRaw(StructuresMessages.get("case_breach_detected",
                        "<gold>Attention! <white>DFC case breach detected!"));
            }
        } else if (breachAnnounced) {
            // Every pane is back (placed one by one — the place events kept
            // the cache current) and the case never broke: restored.
            breachAnnounced = false;
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
    // SHATTER / REPAIR
    // =========================
    /**
     * Breaks the case completely (integrity decay or every glass cell
     * physically destroyed — the message does not care about the cause).
     */
    private void shatterGlass(Location base, ReactorConfig cfg) {
        state = State.BROKEN;
        integrity = 0;
        // The vent releases heat AND pressure together: the case cools back to
        // the ambient −273 C*, so repairing a hot case can no longer re-break
        // it (the old code kept temp at ~10 000, which sat above the decay
        // threshold and melted the fresh glass again).
        temp = -273;
        press = 0;
        jitterPress = 0;
        // The failure supersedes the breach — the restore message announces
        // on the full repair.
        breachAnnounced = false;

        for (int[] off : GLASS) {
            Block block = base.clone().add(off[0], off[1], off[2]).getBlock();
            if (block.getType() == Material.GLASS) {
                block.setType(Material.AIR);
            }
        }

        Location core = base.clone().add(0.5, -5.5, 0.5);
        World world = base.getWorld();
        world.playSound(core, Sound.BLOCK_GLASS_BREAK, SoundCategory.MASTER, 3.0f, 0.8f);
        world.playSound(core, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.MASTER, 1.5f, 1.4f);

        reactor.broadcastRaw(StructuresMessages.get("case_failure_detected",
                "<gold>Attention! <white>DFC case failure detected!"));

        reactor.saveToDb();
    }

    /** Repairs the case: restores all glass and resets integrity. */
    public boolean repair(Location base) {
        return repair(base, true);
    }

    /**
     * Repairs the case, optionally silent — the DB-load path restores the
     * glass blocks without announcing a player-facing repair.
     */
    public boolean repair(Location base, boolean announce) {
        ReactorConfig cfg = ReactorConfig.getInstance();
        if (state != State.BROKEN) return false;

        for (int[] off : GLASS) {
            Block block = base.clone().add(off[0], off[1], off[2]).getBlock();
            if (block.getType() == Material.AIR) {
                block.setType(Material.GLASS);
            }
        }

        state = State.OK;
        integrity = 100;
        temp = Math.min(temp, cfg.getCaseTempMax() - 1);
        breachAnnounced = false;
        // The glass was filled with block.setType() — hold the breach
        // announcements while the rotating audit catches up.
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
     * Auto-repair check — called whenever a glass block is placed inside the
     * structure while the case is broken. When every glass position of the
     * template is filled again, the case repairs itself (integrity 100%).
     */
    public void checkAutoRepair(Location base) {
        if (state != State.BROKEN || base == null) return;
        for (int[] off : GLASS) {
            Block block = base.clone().add(off[0], off[1], off[2]).getBlock();
            if (block.getType() != Material.GLASS) return; // still missing glass
        }
        // Every glass position is filled — restore the case
        state = State.OK;
        integrity = 100;
        jitterPress = 0;
        temp = Math.min(temp, ReactorConfig.getInstance().getCaseTempMax() - 1);
        breachAnnounced = false;
        ventGraceTicks = 60;

        Location core = base.clone().add(0.5, -5.5, 0.5);
        base.getWorld().playSound(core, Sound.BLOCK_GLASS_PLACE, SoundCategory.MASTER, 2.0f, 1.0f);
        reactor.broadcastRaw(StructuresMessages.get("case_breach_restored",
                "<gold>Attention! <white>DFC case breach restored."));
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
    public void setIntegrity(int val) {
        integrity = Math.max(0, Math.min(100, val));
        if (integrity <= 0) state = State.BROKEN;
    }

    public boolean isBroken() { return state == State.BROKEN; }

    private double caseTempRemainder;
    private double caseIntRemainder;
}
