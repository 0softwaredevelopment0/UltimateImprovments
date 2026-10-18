package com.ultimateimprovments.energy.generation.reactor;

import com.ultimateimprovments.util.StructuresMessages;

import com.ultimateimprovments.core.Main;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/**
 * DFC case system — the outer glass protection of the reactor.
 * <p>
 * The case has three parameters:
 * <ul>
 *   <li><b>Temperature (T)</b>, C*: lower limit..10 000 (the reactor's dynamic
 *       lower temperature limit, −273 C* legacy). Rises only while fusion is
 *       running (+15 C* per sec at 150% speed, scaled by the speed); passively
 *       cools at case_cool_rate C* per second scaled by the build-site humidity
 *       multiplier (the core's roll) — clamped below the working-point heat,
 *       so a reactor at ≥100% always nets heating.</li>
 *   <li><b>Pressure (P)</b>, MPa: 0..15, follows the case temperature the same
 *       way the shield pressure follows the core temperature.</li>
 *   <li><b>Integrity (I)</b>, %: <b>the live glass count</b> — every one of the
 *       98 template panes is one point on the 100%..0% scale. Over-temp or
 *       over-pressure degradation breaks one random intact pane per 1% of
 *       decay, so the case falls apart gradually; every pane a player places
 *       back raises the integrity by one point. No passive recovery.</li>
 * </ul>
 * A breach (any missing pane) vents the case: every missing cell drains the
 * pressure at (broken/total) × 100% of the max per second, while the
 * temperature follows at a 10× slower rate as long as ANY pane remains (the
 * surviving glass insulates — the fusion heat rise can outpace a small
 * breach's temp drain, so a lightly holed case keeps heating); a completely
 * broken case has no containment at all and the temperature drains at the
 * full rate (floor = the reactor's lower temperature limit).
 * There is NO hard shatter and NO reset: a fully broken case is just every
 * pane gone, venting at full scale.
 * <p>
 * While the case is breached, fusion particles escape through the broken
 * glass (see ReactorFusion): with particles in the core they leak out at a
 * breach-scaled rate; a drained core additionally loses fusion speed
 * points proportional to the breach scale — the signs simply show the
 * reduced values.
 * <p>
 * Integrity decay above the safe thresholds does not snap panes blindly:
 * a decay wave marks a random intact pane CRITICAL for
 * {@code case_maintain_window_sec} (ramping CRIT particles) and the players
 * can save it by right-clicking the pane with an iron ingot — one window
 * at a time, further decay is held while a window runs. A saved pane
 * starts a {@code case_maintain_cooldown_sec} cooldown before the next
 * window can be marked — decay arriving during it waits. A missed
 * window snaps the pane and escalates the case — while it is not fully
 * repaired again, further decay points snap random panes instantly.
 * <p>
 * Case messaging is exactly three events: breach detected (first pane broken),
 * case failure (fully broken, any cause, once per episode), breach restored
 * (fully repaired). Players repair pane by pane — a placement must pass the
 * repair-failure gate (case cool enough + an iron ingot in the off hand,
 * see {@link #onPaneRepairFailed}); {@link #repair(Location,
 * boolean)} is the instant full-repair path (DB load / admin), ungated.
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
    private int temp;                  // C*, lower limit..10000
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

    // =========================
    // REPAIR FAILURE GATE — a pane placed into the case without passing the
    // repair conditions (case hotter than case_repair_max_temp, or no iron
    // ingot in the off hand) does NOT count as a repair: the cell is ignored
    // by the damage tracker for 1 second, then the verdict re-checks the
    // conditions — still failing → the pane pops off naturally
    // (breakNaturally, loot-table drop); cleared in time (grace window) →
    // it holds, the ingot is taken and it counts as a real repair. Failing
    // panes are keyed per template cell ("dx,dy,dz"). Player placements
    // only — event-blind placements (plugins/commands) are caught by the
    // rotating audit as plain repairs, ungated by design.
    // =========================
    private static final int REPAIR_VERDICT_TICKS = 20; // 1 second
    private final java.util.Set<String> failingPanes = new java.util.HashSet<>();

    // =========================
    // MAINTENANCE — degradation does not snap panes blindly: a decay wave
    // marks ONE random intact pane CRITICAL for case_maintain_window_sec
    // (CRIT particles at the block ramp 1..10 with each elapsed second) and
    // the players can save it by right-clicking the pane with an iron ingot
    // (1 ingot). While a window is pending, further decay is HELD — no new
    // marks and no breaks, one maintenance at a time. A pane whose window
    // expires breaks (its integrity point is spent) and flips the case into
    // ESCALATION mode: while the case is not fully repaired, further decay
    // points snap random panes instantly, no maintenance window. Full
    // repair resets the escalation and the crits.
    //
    // POST-FIX COOLDOWN — a successfully saved pane starts a
    // case_maintain_cooldown_sec cooldown (0 = off) before a new window can
    // be marked. Decay waves arriving during the cooldown wait it out
    // (held, same as the one-window-at-a-time rule) — no marks and no
    // breaks until it expires. Transient like the crit panes (not
    // persisted).
    // =========================
    private static final double CRIT_PARTICLE_RADIUS = 0.25;
    private final java.util.List<CritPane> critPanes = new java.util.ArrayList<>();
    private boolean escalationMode;
    private long maintainCooldownUntil; // ms — earliest time a new window may be marked (0 = none)

    /** One pane pending its maintenance verdict (transient — not persisted). */
    private static final class CritPane {
        final int dx;
        final int dy;
        final int dz;
        final long deadline;  // ms — when the pane snaps if not saved
        long nextBurst;       // ms — the next CRIT particle burst

        CritPane(int dx, int dy, int dz, long deadline, long now) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.deadline = deadline;
            this.nextBurst = now;
        }
    }

    public ReactorCase(ReactorManager reactor) {
        this.reactor = reactor;
        // Start at the reactor's lower temperature limit (the legacy −273
        // default until the build rolls the biome-based one)
        this.temp = reactor.getTempMin();
    }

    // =========================
    // CASE TICK (every server tick)
    // =========================
    public void tick(Location base) {
        ReactorConfig cfg = ReactorConfig.getInstance();

        // =========================
        // PASSIVE COOLING — the case sheds heat to the environment exactly
        // like the core: case_cool_rate C*/sec scaled by the build-site
        // humidity multiplier (the same roll as the core's natural cooling;
        // legacy reactors multiply by 1). Config-clamped so even at the max
        // multiplier it stays below the working-point (100%) fusion heat —
        // a running reactor at >=100% always nets heating. Floor = the
        // reactor's dynamic lower temperature limit.
        // =========================
        if (temp > reactor.getTempMin()) {
            double mult = reactor.getNaturalCoolMult() < 0 ? 1.0 : reactor.getNaturalCoolMult();
            double v = cfg.getCaseCoolRate() * mult / 20.0 + caseCoolRemainder;
            int whole = (int) v;
            caseCoolRemainder = v - whole;
            if (whole > 0) {
                temp = Math.max(reactor.getTempMin(), temp - whole);
            }
        }

        // =========================
        // TEMPERATURE — rises only from fusion (speed-scaled). A breached
        // case vents it slowly while any pane remains (see tickBreach) —
        // a small breach's drain can lose to the heat rise, so a lightly
        // holed case keeps heating; a fully broken one trends to ambient
        // on its own — no state switch needed.
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
        // INTEGRITY — decays above the safe thresholds: a decay wave either
        // marks one random intact pane critical (the single maintenance
        // window — further decay is held while it runs) or, in escalation
        // mode, snaps random panes instantly (98 panes = the whole scale).
        // =========================
        boolean over = temp >= cfg.getCaseIntDecayTemp()
                || press >= cfg.getCaseIntDecayPress() * 1000.0; // config MPa → kPa
        if (over) {
            double ratePerTick = cfg.getCaseIntDecayRate() / 20.0;
            caseIntRemainder += ratePerTick;
            int whole = (int) caseIntRemainder;
            if (whole > 0) {
                caseIntRemainder -= whole;
                degradePanes(base, whole, cfg);
            }
        }

        // =========================
        // MAINTENANCE — crit pane particles and expiry checks (every tick).
        // =========================
        tickMaintenance(base, cfg);
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

        // Vent physics — proportional to the breach scale. The PRESSURE is
        // what should drop hard: (broken/total) × 100% of max per second at
        // any breach size. The TEMPERATURE follows at a 10× slower rate
        // while any pane remains (the surviving glass still insulates — a
        // small breach's temp drain can even lose to the fusion heat rise),
        // while a COMPLETELY broken case has no containment at all —
        // full-rate drain, the temp trends back to the dynamic lower
        // temperature limit within a second or two (no hard reset, the vent
        // replaces the old shatter snap).
        if (broken > 0) {
            double frac = broken / (double) total;

            ventPressRemainder += frac * cfg.getCasePressMax() * 1000.0 / 20.0;
            int pressDrain = (int) ventPressRemainder;
            ventPressRemainder -= pressDrain;
            if (pressDrain > 0) press = Math.max(0, press - pressDrain);

            double tempDrainPerSec = frac * cfg.getCaseTempMax()
                    * (broken >= total ? 0.5 : 0.5 / 10.0);
            ventTempRemainder += tempDrainPerSec / 20.0;
            int tempDrain = (int) ventTempRemainder;
            ventTempRemainder -= tempDrain;
            if (tempDrain > 0) temp = Math.max(reactor.getTempMin(), temp - tempDrain);
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
            // Full repair resets the maintenance escalation: the maintenance
            // windows return and any pending crits start from scratch.
            escalationMode = false;
            critPanes.clear();
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
    // GRADUAL DEGRADATION / MAINTENANCE
    // =========================
    /**
     * Decay wave handling. While a maintenance window is already pending,
     * the wave is HELD — no new marks and no breaks (one window at a time).
     * The same hold applies while the post-fix maintenance cooldown runs —
     * decay arriving during it waits for the cooldown to expire. Escalation
     * mode (a window was already missed) snaps panes instantly; otherwise
     * exactly one random intact pane is marked critical — the players have
     * {@code case_maintain_window_sec} to save it.
     */
    private void degradePanes(Location base, int count, ReactorConfig cfg) {
        if (!critPanes.isEmpty()) return; // a window is pending — decay is held
        if (System.currentTimeMillis() < maintainCooldownUntil) return; // post-fix cooldown — decay waits it out
        if (escalationMode) {
            breakRandomPanes(base, count);
        } else {
            markRandomPaneCritical(base, cfg);
        }
    }

    /** True while the template cell has a pending maintenance window. */
    private boolean isCritCell(int dx, int dy, int dz) {
        for (CritPane pane : critPanes) {
            if (pane.dx == dx && pane.dy == dy && pane.dz == dz) return true;
        }
        return false;
    }

    /** Removes and returns the crit entry of a cell, or null when it is not critical. */
    private CritPane removeCritCell(int dx, int dy, int dz) {
        for (java.util.Iterator<CritPane> it = critPanes.iterator(); it.hasNext(); ) {
            CritPane pane = it.next();
            if (pane.dx == dx && pane.dy == dy && pane.dz == dz) {
                it.remove();
                return pane;
            }
        }
        return null;
    }

    /**
     * Marks one random intact (not already critical) pane critical: CRIT
     * particles ramp at the block for {@code case_maintain_window_sec} and
     * the maintenance broadcast fires within the normal 30-block audience.
     * False when there is no pane left to mark.
     */
    private boolean markRandomPaneCritical(Location base, ReactorConfig cfg) {
        java.util.List<int[]> candidates = new java.util.ArrayList<>();
        for (int[] off : GLASS) {
            if (!isCritCell(off[0], off[1], off[2])
                    && base.clone().add(off[0], off[1], off[2]).getBlock().getType() == Material.GLASS) {
                candidates.add(off);
            }
        }
        if (candidates.isEmpty()) return false;

        int[] off = candidates.get(random.nextInt(candidates.size()));
        long now = System.currentTimeMillis();
        long windowMs = cfg.getCaseMaintainWindowSec() * 1000L;
        critPanes.add(new CritPane(off[0], off[1], off[2], now + windowMs, now));

        reactor.broadcastRaw(StructuresMessages.get("case_maintenance_required",
                "<gold>Attention! <white>DFC Case requires maintenance."));
        return true;
    }

    /**
     * Maintenance tick: per-second CRIT bursts at every critical pane (the
     * particle count ramps 1..10 with the elapsed seconds) and the expiry
     * check — an unsaved pane snaps (its integrity point is spent) and the
     * case enters escalation mode until fully repaired.
     */
    private void tickMaintenance(Location base, ReactorConfig cfg) {
        if (critPanes.isEmpty()) return;
        long now = System.currentTimeMillis();
        int windowSec = cfg.getCaseMaintainWindowSec();

        for (java.util.Iterator<CritPane> it = critPanes.iterator(); it.hasNext(); ) {
            CritPane pane = it.next();

            if (now >= pane.deadline) {
                // Not saved in time: the pane snaps and the escalation begins
                it.remove();
                breakPaneAt(base, pane.dx, pane.dy, pane.dz);
                escalationMode = true;
                continue;
            }

            if (now >= pane.nextBurst) {
                pane.nextBurst += 1000L;
                long remainSec = Math.max(0, (pane.deadline - now + 999) / 1000);
                int count = (int) Math.min(10, Math.max(1, windowSec - remainSec + 1));
                Location cell = base.clone().add(pane.dx + 0.5, pane.dy + 0.5, pane.dz + 0.5);
                base.getWorld().spawnParticle(Particle.CRIT, cell, count,
                        CRIT_PARTICLE_RADIUS, CRIT_PARTICLE_RADIUS, CRIT_PARTICLE_RADIUS, 0.05);
            }
        }
    }

    /**
     * A player right-clicked a critical pane with an iron ingot: the pane is
     * saved (the window closes, no break, no escalation), one ingot is
     * consumed and the post-fix maintenance cooldown starts
     * ({@code case_maintain_cooldown_sec} — new decay waves wait it out).
     * False when the cell has no pending maintenance window — the caller
     * should not cancel the interaction.
     */
    public boolean tryFixCriticalPane(Player player, Location base, int dx, int dy, int dz) {
        if (removeCritCell(dx, dy, dz) == null) return false;

        int cooldownSec = ReactorConfig.getInstance().getCaseMaintainCooldownSec();
        if (cooldownSec > 0) {
            maintainCooldownUntil = System.currentTimeMillis() + cooldownSec * 1000L;
        } else {
            maintainCooldownUntil = 0L;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand != null && hand.getType() == Material.IRON_INGOT) {
            if (hand.getAmount() > 1) {
                ItemStack rest = hand.clone();
                rest.setAmount(hand.getAmount() - 1);
                player.getInventory().setItemInMainHand(rest);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        }

        Location cell = base.clone().add(dx + 0.5, dy + 0.5, dz + 0.5);
        base.getWorld().playSound(cell, Sound.BLOCK_GLASS_PLACE, SoundCategory.MASTER, 1.0f, 1.3f);
        return true;
    }

    /**
     * Gradual degradation: breaks up to {@code count} random intact panes
     * (1 pane = 1 point of the 98-pane integrity scale). Critical panes are
     * never picked — they are pending their own verdict. The breach tick
     * re-derives the integrity from the live counts and runs the vent and
     * the messages — no full shatter, no reset, the case just loses panes
     * one by one while the decay conditions hold.
     */
    private void breakRandomPanes(Location base, int count) {
        java.util.List<int[]> intact = new java.util.ArrayList<>();
        for (int[] off : GLASS) {
            if (!isCritCell(off[0], off[1], off[2])
                    && base.clone().add(off[0], off[1], off[2]).getBlock().getType() == Material.GLASS) {
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

    /** Snaps one specific pane (the missed maintenance verdict). */
    private void breakPaneAt(Location base, int dx, int dy, int dz) {
        Block block = base.clone().add(dx, dy, dz).getBlock();
        if (block.getType() != Material.GLASS) return;
        block.setType(Material.AIR);
        // setType fires no events — keep the audit cache exact (O(1))
        ReactorDamageTracker.noteCellBroken(base, dx, dy, dz);
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
    // REPAIR FAILURE GATE
    // =========================
    /** True while the case is too hot for pane repairs (temp > case_repair_max_temp). */
    public boolean isRepairBlocked() {
        return temp > ReactorConfig.getInstance().getCaseRepairMaxTemp();
    }

    /** Iron ingots (off hand) one pane repair costs (0 = repairs are free). */
    public static int repairIronCost() {
        return ReactorConfig.getInstance().getCaseRepairIronCost();
    }

    /** True when the player holds enough iron ingots in the OFF hand (the main hand has the glass). */
    public static boolean hasRepairIngot(Player player) {
        ItemStack off = player.getInventory().getItemInOffHand();
        return off != null && off.getType() == Material.IRON_INGOT
                && off.getAmount() >= repairIronCost();
    }

    /** Takes one repair's worth of iron ingots from the off-hand stack. */
    public static void consumeRepairIngot(Player player) {
        int cost = repairIronCost();
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off == null || off.getType() != Material.IRON_INGOT) return;
        if (off.getAmount() > cost) {
            ItemStack rest = off.clone();
            rest.setAmount(off.getAmount() - cost);
            player.getInventory().setItemInOffHand(rest);
        } else {
            player.getInventory().setItemInOffHand(null);
        }
    }

    /**
     * A glass pane was placed into the template cell without passing the
     * repair conditions (case too hot, or no iron ingot in the off hand) —
     * the caller did NOT count the pane. The cell is marked ignored and its
     * verdict runs after 1 second: conditions still failing → the pane pops
     * off naturally (loot-table drop per the datapack); cleared in time
     * (grace window) → the pane holds, the ingot is consumed and the pane
     * counts as a real repair.
     */
    public void onPaneRepairFailed(Player player, Location base, int dx, int dy, int dz) {
        failingPanes.add(dx + "," + dy + "," + dz);
        ReactorDamageTracker.setCellIgnored(base, dx, dy, dz, true);
        scheduleRepairVerdict(player, base, dx, dy, dz);
    }

    private void scheduleRepairVerdict(Player player, Location base, int dx, int dy, int dz) {
        Bukkit.getScheduler().runTaskLater(Main.getInstance(),
                () -> verdictRepairFailure(player, base, dx, dy, dz), REPAIR_VERDICT_TICKS);
    }

    /** The 1-second verdict for one failed pane (see {@link #onPaneRepairFailed}). */
    private void verdictRepairFailure(Player player, Location base, int dx, int dy, int dz) {
        String key = dx + "," + dy + "," + dz;
        if (!failingPanes.remove(key)) {
            // Superseded (reset/reassembly) — just drop the ignore mark
            ReactorDamageTracker.setCellIgnored(base, dx, dy, dz, false);
            return;
        }

        Location blockLoc = base.clone().add(dx, dy, dz);
        if (base.getWorld() == null
                || !base.getWorld().isChunkLoaded(blockLoc.getBlockX() >> 4, blockLoc.getBlockZ() >> 4)) {
            // Chunk unloaded mid-verdict — defer; the pane stays ignored
            // until the world around it is readable again.
            failingPanes.add(key);
            scheduleRepairVerdict(player, base, dx, dy, dz);
            return;
        }
        ReactorDamageTracker.setCellIgnored(base, dx, dy, dz, false);

        // The verdict re-checks the conditions (heat first): still failing →
        // the pane does not hold — natural break with the glass loot-table
        // drop (the datapack overrides it to drop glass).
        boolean conditionsMet = !isRepairBlocked()
                && player != null && player.isOnline()
                && (repairIronCost() <= 0 || hasRepairIngot(player));
        Block block = blockLoc.getBlock();
        if (!conditionsMet || block.getType() != Material.GLASS) {
            if (conditionsMet) return; // pane gone by itself — nothing to accept
            if (block.getType() == Material.GLASS) {
                block.breakNaturally();
                base.getWorld().playSound(blockLoc.clone().add(0.5, 0.5, 0.5),
                        Sound.BLOCK_GLASS_BREAK, SoundCategory.MASTER, 1.2f, 0.9f);
            }
            return;
        }

        // Grace won: the pane holds, the ingot is taken, the repair counts
        if (repairIronCost() > 0) consumeRepairIngot(player);
        ReactorDamageTracker.noteCellRepaired(base, dx, dy, dz);
        onGlassPlaced();
    }

    // =========================
    // RESET
    // =========================
    public void reset() {
        state = State.OK;
        temp = reactor.getTempMin();
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
        caseCoolRemainder = 0;
        failingPanes.clear(); // pending verdicts no-op (the ignore marks die with resetAudit)
        critPanes.clear();
        escalationMode = false;
        maintainCooldownUntil = 0L;
    }

    // =========================
    // GETTERS / SETTERS (persistence)
    // =========================
    public State getState() { return state; }
    public void setState(State val) { state = val; }

    public int getTemp() { return temp; }
    public void setTemp(int val) {
        temp = Math.max(reactor.getTempMin(), Math.min(10000, val));
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
    private double caseCoolRemainder;
}
