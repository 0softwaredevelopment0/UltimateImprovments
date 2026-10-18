package com.ultimateimprovments.energy.generation.reactor;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.structure.StructureMarker;
import com.ultimateimprovments.energy.transfer.cable.CableNetwork;
import com.ultimateimprovments.energy.transfer.cable.EnergyFlow;
import com.ultimateimprovments.mechanics.environment.radiation.RadiationManager;
import com.ultimateimprovments.util.LocationUtil;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.Materials;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.StructuresMessages;

import org.bukkit.*;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrator of the Dark Fusion Core reactor (D.F.C).
 * <p>
 * Manages the reactor's state, simulation, saving and ticks.
 * Visual effects, sounds and sign updates are delegated to {@link ReactorDisplay}.<br>
 * Save/load — {@link ReactorPersistence}+{@link ReactorState}.<br>
 * Configuration — {@link ReactorConfig}.
 */
public class ReactorManager {

    // =========================
    // SINGLETON
    // =========================
    private static ReactorManager instance;

    /** Registry of all active reactors (multi-reactor support). */
    private static java.util.List<ReactorManager> reactors;

    private final ReactorDisplay display;
    private final ReactorLasers lasers;
    private final ReactorShield shield;
    private final ReactorFuel fuel;
    private final ReactorFusion fusion;
    private final ReactorCase caseSys;

    // =========================
    // DAMAGE STATE (block-level tracking: glass / signs / bulbs / structure)
    // A damaged reactor keeps running uncontrolled: lasers, sensors and sign
    // panels are dead, the core coasts on its own decay — to shut it down you
    // must cool it to 0 C* with the Stabilization Laser (or ride it out).
    // =========================
    private boolean structureDamaged = false;
    private int damageWarnTick = 0;

    // =========================
    // SELF-DESTRUCT PROTOCOL (DFC): 1% chance on startup.
    // Phase 1 — sensors go dark for 5s (No signal screen).
    // Phase 2 — 1s, then "All controls are non-functional..." — every control
    //           lamp is locked (inert to the levers).
    // Phase 3 — 5s, then the T-60s announcement: the protocol screen replaces
    //           the readings, a warning ping sounds every second.
    // Phase 4 — 60s timed countdown → "Beginning detonation procedure..."
    // Phase 5 — 5s → "Bypassing internal PL power limits, new limit is 2000%."
    // Phase 6 — 5s → the Power Lasers overdrive to 2000% and burn the shield;
    //           at 0% integrity the shield failure countdown (T-10s) begins and
    //           the protocol reports "detecting core shield failure" — the
    //           detonation then proceeds on its own.
    // =========================
    public enum SelfdestructPhase { NONE, SENSORS_DOWN, CONTROLS_DOWN, PROTOCOL_MSG, TIMED, DETONATION_MSG, BYPASS_MSG, FINALE }

    private SelfdestructPhase selfdestructPhase = SelfdestructPhase.NONE;
    private int selfdestructTicks;        // ticks in the current phase
    private boolean selfdestructDone;     // completed — not rolled again
    private boolean selfdestructCancelled; // cancelled once — re-roll chance halved until disassembly
    private boolean selfdestructAnnounced; // one-tick latch: the T-60s announcement fired → dfc_self_destruct grant
    private boolean fusionDebrisJustCrafted; // one-tick latch: debris crafted → power_of_fusion grant

    /** Seconds remaining in the timed phase (for the sign timer). */
    public int getSelfdestructSecondsLeft() {
        return selfdestructPhase == SelfdestructPhase.TIMED
                ? Math.max(0, (selfdestructCfg().getSelfdestructTimedSec() * 20
                        - selfdestructTicks + 19) / 20)
                : 0;
    }

    private static ReactorConfig selfdestructCfg() { return ReactorConfig.getInstance(); }

    public boolean isSelfdestructActive() { return selfdestructPhase != SelfdestructPhase.NONE; }
    public boolean isSelfdestructFinale() { return selfdestructPhase == SelfdestructPhase.FINALE; }
    public SelfdestructPhase getSelfdestructPhase() { return selfdestructPhase; }

    private void tickSelfdestruct() {
        ReactorConfig cfg = ReactorConfig.getInstance();
        switch (selfdestructPhase) {
            case SENSORS_DOWN -> {
                selfdestructTicks++;
                if (selfdestructTicks >= cfg.getSelfdestructNoSignalSec() * 20) {
                    // 1s of silence, then the controls-down report
                    selfdestructPhase = SelfdestructPhase.CONTROLS_DOWN;
                    selfdestructTicks = 0;
                }
            }
            case CONTROLS_DOWN -> {
                selfdestructTicks++;
                if (selfdestructTicks >= 20) {
                    // Every control lamp goes dead to the levers
                    lasers.setControlLocked(true);
                    broadcast(StructuresMessages.get("selfdestruct_controls_down",
                            "<light_purple>All controls are non-functional, restarting systems..."));
                    selfdestructPhase = SelfdestructPhase.PROTOCOL_MSG;
                    selfdestructTicks = 0;
                }
            }
            case PROTOCOL_MSG -> {
                selfdestructTicks++;
                if (selfdestructTicks >= 20 * 5) {
                    // The T-60s announcement, then the timed countdown begins
                    broadcast(StructuresMessages.get("selfdestruct_announce",
                            "<light_purple>Attention all personal, an <red>Internal Dark Fusion Reactor Systems <light_purple>initiated a <red>self-destruct protocol, <light_purple> detonation procedure will begin in <red>T-60s, <light_purple>good luck."));
                    selfdestructPhase = SelfdestructPhase.TIMED;
                    selfdestructTicks = 0;
                    selfdestructAnnounced = true;
                    display.resetSignCache();
                }
            }
            case TIMED -> {
                selfdestructTicks++;
                int total = cfg.getSelfdestructTimedSec() * 20;
                // Protocol screen + a warning ping every second
                if (selfdestructTicks % 20 == 0 && reactorLocation != null) {
                    reactorLocation.getWorld().playSound(reactorLocation,
                            org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING,
                            org.bukkit.SoundCategory.MASTER, 1.0f, 1.5f);
                }
                if (selfdestructTicks >= total) {
                    broadcast(StructuresMessages.get("selfdestruct_detonation_begin",
                            "<light_purple>Beginning detonation procedure..."));
                    selfdestructPhase = SelfdestructPhase.DETONATION_MSG;
                    selfdestructTicks = 0;
                }
            }
            case DETONATION_MSG -> {
                selfdestructTicks++;
                if (selfdestructTicks >= 20 * 5) {
                    broadcast(StructuresMessages.get("selfdestruct_bypass_limits",
                            "<light_purple>Bypassing internal PL power limits, new limit is <red>2000%."));
                    selfdestructPhase = SelfdestructPhase.BYPASS_MSG;
                    selfdestructTicks = 0;
                }
            }
            case BYPASS_MSG -> {
                selfdestructTicks++;
                if (selfdestructTicks >= 20 * 5) {
                    broadcast(StructuresMessages.get("selfdestruct_overdrive",
                            "<light_purple>Overdriving power lasers for <red>2000%, <light_purple>waiting for a meltdown."));
                    lasers.beginOverpower();
                    selfdestructPhase = SelfdestructPhase.FINALE;
                    selfdestructTicks = 0;
                }
            }
            case FINALE -> {
                // The shield died (overpower burn / stress — whatever got it to
                // 0% first): the T-10s shield failure countdown is already
                // running — the protocol reports completion and disarms.
                if (shield.isFailed()) {
                    onSelfdestructReportStage();
                } else if (shield.getState() != ReactorShield.State.WORKING) {
                    // The protocol ran on a core with no formed shield (the
                    // startup was blocked — nothing to burn): it completes and
                    // shuts the systems down instead of hanging forever.
                    onSelfdestructReportStage();
                }
            }
            case NONE -> { /* not armed */ }
        }
    }

    /**
     * Shield failure detected (0% integrity → T-10s detonation countdown):
     * the self-destruct sequence reports completion and disarms itself — the
     * detonation proceeds on its own countdown.
     */
    public void onSelfdestructReportStage() {
        if (selfdestructPhase == SelfdestructPhase.NONE || selfdestructDone) return;
        selfdestructPhase = SelfdestructPhase.NONE;
        selfdestructDone = true;
        broadcast(StructuresMessages.get("selfdestruct_shutdown_systems",
                "<light_purple>Self-destruct protocol complete, detecting core shield failure, shutting down systems..."));
        saveToDb();
    }

    /** Rolls the self-destruct chance at startup (1%, halved after a cancel). */
    private void rollSelfdestruct() {
        ReactorConfig cfg = ReactorConfig.getInstance();
        if (selfdestructDone || isSelfdestructActive()) return;
        double chance = cfg.getSelfdestructChance();
        if (selfdestructCancelled) chance *= 0.5;
        if (Math.random() * 100.0 < chance) {
            selfdestructPhase = SelfdestructPhase.SENSORS_DOWN;
            selfdestructTicks = 0;
            display.resetSignCache();
            broadcast(StructuresMessages.get("sensor_no_signal",
                    "<red>Cannot receive any data from sensors: <gray>No signal"));
        }
    }

    // =========================
    // STALL WARNINGS — one message per downward threshold crossing while the
    // reaction is running (the exact moment of heat loss, not "any time below").
    // =========================
    /** Below this core temperature the fusion reaction stops (1M C* default). */
    private static final int STALL_WARN_CRITICAL = 10_000;

    private void checkStallWarnings(int prevTemp) {
        if (isStallShutdownActive() || structureDamaged
                || !lasers.isStarted() || isSelfdestructActive()
                || shield.getState() != ReactorShield.State.WORKING) return;

        int fusionMin = ReactorConfig.getInstance().getFusionTempMin();
        if (prevTemp > fusionMin && coreTemp <= fusionMin) {
            broadcast(StructuresMessages.get("stall_warn_low",
                    "<white>Core temperatures are too low to continue fusion reaction, please activate all power lasers."));
        }
        if (prevTemp > STALL_WARN_CRITICAL && coreTemp <= STALL_WARN_CRITICAL) {
            broadcast(StructuresMessages.get("stall_warn_critical",
                    "<aqua>Core temperatures are critically low, activate all power lasers immediately, to avoid a potential reaction stall!"));
        }
        // The reaction-failure warning ("stall imminent") is NOT here — it
        // fires at the lower-limit crossing, 5s before the shutdown starts
        // (see tryTriggerStallShutdown, together with the advancement).
    }

    /**
     * The core reached the reactor's dynamic lower temperature limit (the
     * build-site biome temperature × multiplier, −273 C* legacy default).
     * The "reaction failure, stall imminent" warning fires first and the
     * automatic shutdown starts 5 seconds later, together with the
     * operator_incompetence advancement. Crossing detection, so a reactor
     * restarted cold does not instantly re-stall: it must first gain heat
     * and lose it again.
     */
    private void tryTriggerStallShutdown(int prevTemp) {
        if (stallPhase != StallPhase.NONE) return;
        if (structureDamaged || !lasers.isStarted() || isSelfdestructActive()) return;
        if (shield.getState() != ReactorShield.State.WORKING) return;
        if (prevTemp <= tempMin || coreTemp > tempMin) return;

        // Automatic trigger: the reaction died from heat loss (limit
        // reached) — warn first, the shutdown follows in 5 seconds
        stallPhase = StallPhase.STALL_WARN;
        stallTicks = 0;
        broadcast(StructuresMessages.get("stall_warn_failure",
                "<aqua>Core reaction failure due to critically low temperatures, reaction stall imminent!"));
        saveToDb();
    }

    /**
     * Manual shutdown: the Trigger Action lamp pulsed while the core is
     * already running. Silently ignored while the shield stress is above
     * 10% (nothing happens); otherwise the MANUAL final shutdown procedure
     * starts — the same chain as the automatic one, only the announcement
     * differs (due to manual trigger).
     */
    public void tryManualShutdownTrigger() {
        // Meltdown restabilization: the trigger lamp pulse while the aux
        // shield holds is the restabilization attempt
        if (meltdownPhase == MeltdownPhase.AUX_ACTIVE) {
            tryRestabilization();
            return;
        }
        // Self-destruct, timed countdown: the trigger lamp pulse becomes the
        // secret protocol-cancel attempt — 0.5% per rising edge that the
        // systems "malfunction" the protocol away. Past the announcement of
        // the detonation procedure (TIMED over) it is already too late.
        if (selfdestructPhase == SelfdestructPhase.TIMED) {
            tryCancelSelfdestruct();
            return;
        }
        if (stallPhase != StallPhase.NONE) return;
        if (structureDamaged || isSelfdestructActive()) return;
        if (shield.getState() != ReactorShield.State.WORKING) return;
        if (shield.getTotalStress() > MANUAL_TRIGGER_MAX_STRESS) return;

        // Manual trigger: the operator pulsed the lamp on a running reactor
        beginFinalShutdown(ShutdownTrigger.MANUAL);
    }

    /**
     * The final shutdown phase — the shared chain every shutdown runs after
     * its trigger: power lasers off one by one, heat dump to the lower
     * limit, the stabilization lasers, the content absorber valve, the
     * shield ramp and
     * the offline mark (see {@link #tickStallShutdown()}). The MANUAL and
     * AUTOMATIC procedures differ only in the announcement: the operator
     * manual trigger vs the automatic trigger (the reaction-failure stall
     * at the lower limit, the meltdown restabilization success).
     */
    private void beginFinalShutdown(ShutdownTrigger trigger) {
        stallTrigger = trigger;
        // Every trigger dumps the heat to the lower limit at 10%/sec of the
        // shutdown-start temperature; the automatic stall is already there
        // and passes through the dump instantly.
        stallCoolPerTick = Math.max(1.0, coreTemp * 0.10 / 20.0);
        stallPhase = StallPhase.WAIT_POWER;
        stallTicks = 0;
        boolean manual = trigger == ShutdownTrigger.MANUAL;
        broadcast(StructuresMessages.get(manual ? "stall_shutdown_manual" : "stall_shutdown_automatic",
                manual ? "<white>Core shutdown initiated due to manual trigger, please wait."
                       : "<white>Core shutdown initiated due to automatic trigger, please wait."));
        saveToDb();
    }

    /**
     * The secret protocol cancel (Trigger Action lamp pulse during the timed
     * countdown): 0.5% chance per pulse that the systems malfunction the
     * protocol away — it disarms, the reactor powers down (controlled shield
     * ramp, lasers off, controls unlocked) and can be started again later.
     * The protocol is not gone for good: it can roll again on the next
     * startups, at a halved chance until the reactor is disassembled.
     * 🏆 Advancement: is_it_really_that_simple — everyone near the reactor.
     */
    private void tryCancelSelfdestruct() {
        if (Math.random() * 100.0 >= 0.5) return;

        selfdestructPhase = SelfdestructPhase.NONE;
        selfdestructTicks = 0;
        selfdestructCancelled = true;
        lasers.reset();
        shield.beginShutdown();
        display.resetSignCache();
        broadcast(StructuresMessages.get("selfdestruct_cancelled",
                "<white>Self-destruct protocol has been cancelled due to system malfunction."));
        grantAdvancementNear("datapack/is_it_really_that_simple");
        saveToDb();
    }

    // =========================
    // MELTDOWN RESTABILIZATION PROTOCOL — the reactor shield just died (0%
    // integrity). Instead of an instant T-10s detonation the emergency
    // systems buy three minutes: an auxiliary shield (100% integrity,
    // draining linearly to 0% over the window) holds the core while the
    // operators try to restabilize the reactor. Netherite items in the
    // content barrel (the floor barrel the fusion debris lands in) set the
    // success chance, summed per item: ancient debris / netherite scrap +1%,
    // netherite ingot +2%, netherite block +3% — other items are ignored.
    // A working reactor naturally accumulates debris there, so a long run
    // can afford a no-cargo attempt. The total is capped by
    // reactor.restab_chance_max (default 99%) — the best possible attempt
    // is never certain. The attempt is the Trigger Action lamp pulse: the
    // netherite items are consumed, the systems run their calibration
    // sequence and roll the captured chance. Success starts the manual
    // (heat-dump) shutdown procedure — the reactor powers down and leaves
    // the meltdown phase. Failure, or an expired aux shield (integrity 0%),
    // makes the detonation unavoidable (the shield FAILED T-10s countdown).
    // =========================
    public enum MeltdownPhase {
        NONE,          // normal operation
        AUX_ANALYZING, // explosion + fire in the core, "analyzing core systems" (3s)
        AUX_CREATING,  // "auxiliary shield creation in progress" (3s)
        AUX_LINES,     // core smoke + the WARNING block, one line per 5 ticks
        AUX_ACTIVE,    // aux shield holds: T-3m timer, the attempt is allowed
        RESTAB_SEQ,    // attempt running: chance captured, ingots consumed
        RESTAB_FAIL    // attempt failed: failure reports, then T-10s
    }

    /**
     * The auxiliary shield lifetime — the "T-3m" restabilization window.
     * Public: the shield drains its integrity linearly over the same span.
     */
    public static final int AUX_SHIELD_SEC = 180;
    /** Ticks between the WARNING block lines. */
    private static final int MELTDOWN_LINE_DELAY_TICKS = 5;

    private MeltdownPhase meltdownPhase = MeltdownPhase.NONE;
    private int meltdownTicks;      // ticks in the current phase
    private int meltdownLineIndex;  // WARNING line queue cursor
    private double restabChance;    // chance captured at the attempt start

    public boolean isMeltdownActive() { return meltdownPhase != MeltdownPhase.NONE; }
    public MeltdownPhase getMeltdownPhase() { return meltdownPhase; }

    /**
     * True while the aux shield is up and must drain: the T-3m window is open
     * (AUX_ACTIVE) or the attempt/fail sequence is running on top of it. The
     * forming stages (analyzing / creating / warning lines) hold 100%.
     */
    public boolean isAuxShieldDraining() {
        return meltdownPhase == MeltdownPhase.AUX_ACTIVE
                || meltdownPhase == MeltdownPhase.RESTAB_SEQ
                || meltdownPhase == MeltdownPhase.RESTAB_FAIL;
    }

    /**
     * The aux shield integrity hit 0% (ReactorShield drain callback): the
     * detonation becomes unavoidable — the standard shield-failure broadcast
     * and the T-10s countdown. Also aborts an in-flight attempt/fail
     * sequence: without a shield there is nothing left to restabilize.
     */
    public void onAuxShieldExpired() {
        if (!isMeltdownActive()) return;
        if (shield.getState() != ReactorShield.State.AUXILIARY) return;
        beginUnavoidable(true);
    }

    /**
     * The reactor shield just died (0% integrity): the meltdown
     * restabilization protocol starts. The self-destruct protocol keeps its
     * own flow — its finale deliberately burns the shield, so a depleted
     * shield there goes straight to the old T-10s failure.
     * 🏆 Advancement: critical_overload — bring the DFC to this phase.
     */
    public void onShieldDepleted() {
        if (isSelfdestructActive()) {
            shield.fail(true);
            return;
        }
        if (isMeltdownActive() || shield.isFailed()) return;
        // The regular shield is gone — the AUXILIARY state takes its place:
        // it forms full and drains linearly to 0% over the T-3m window
        // (the drain itself lives in ReactorShield.tick)
        shield.setState(ReactorShield.State.AUXILIARY);
        shield.setIntegrity(100);
        meltdownPhase = MeltdownPhase.AUX_ANALYZING;
        meltdownTicks = 0;
        meltdownLineIndex = 0;
        spawnMeltdownExplosion();
        broadcast(StructuresMessages.get("meltdown_analyzing",
                "<red>Reactor shield failure, analyzing core systems..."));
        grantAdvancementNear("datapack/critical_overload");
        saveToDb();
    }

    /** Meltdown restabilization phase machine (every tick). */
    private void tickMeltdown() {
        switch (meltdownPhase) {
            case NONE -> { }

            case AUX_ANALYZING -> {
                if (++meltdownTicks >= 20 * 3) {
                    broadcast(StructuresMessages.get("meltdown_aux_creating",
                            "<white>Auxiliary shield creation in progress, manual shutdown required."));
                    meltdownPhase = MeltdownPhase.AUX_CREATING;
                    meltdownTicks = 0;
                }
            }

            case AUX_CREATING -> {
                if (++meltdownTicks >= 20 * 3) {
                    spawnMeltdownSmoke();
                    meltdownPhase = MeltdownPhase.AUX_LINES;
                    meltdownTicks = 0;
                    meltdownLineIndex = 0;
                }
            }

            case AUX_LINES -> {
                meltdownTicks++;
                if (meltdownTicks % MELTDOWN_LINE_DELAY_TICKS == 0) {
                    String[] lines = meltdownWarningLines();
                    if (meltdownLineIndex < lines.length) {
                        broadcast(lines[meltdownLineIndex++]);
                    }
                    if (meltdownLineIndex >= lines.length) {
                        // The aux shield is up: the T-3m window opens. The
                        // stabilizers are cranked to full (200%) and the
                        // control is locked — the ±5% lamps still click but
                        // nothing responds.
                        meltdownPhase = MeltdownPhase.AUX_ACTIVE;
                        meltdownTicks = 0;
                        lasers.setPower(ReactorLasers.LASER_STAB, 200);
                        lasers.setControlLocked(true);
                    }
                }
            }

            case AUX_ACTIVE -> {
                if (++meltdownTicks >= AUX_SHIELD_SEC * 20) {
                    // The auxiliary shield died — the detonation is now unavoidable
                    beginUnavoidable(true);
                }
            }

            case RESTAB_SEQ -> {
                meltdownTicks++;
                if (meltdownTicks == 20 * 5) {
                    broadcast(StructuresMessages.get("meltdown_recalibrating",
                            "<white>Recalibrating stabilization lasers..."));
                } else if (meltdownTicks == 20 * 10) {
                    broadcast(StructuresMessages.get("meltdown_recreation",
                            "<white>Attempting shield re-creation..."));
                } else if (meltdownTicks == 20 * 15) {
                    broadcast(StructuresMessages.get("meltdown_estimating",
                            "<white>Estimating chance of successful shutdown..."));
                } else if (meltdownTicks == 20 * 20) {
                    broadcast(StructuresMessages.get("meltdown_chance",
                            "<white>Estimated chance of success: <yellow>%chance%%<white>.")
                            .replace("%chance%", String.valueOf((int) restabChance)));
                } else if (meltdownTicks == 20 * 25) {
                    broadcast(StructuresMessages.get("meltdown_awaiting",
                            "<white>Awaiting reactor systems to respond..."));
                }
                if (meltdownTicks >= 20 * 30) {
                    rollRestabilization();
                }
            }

            case RESTAB_FAIL -> {
                meltdownTicks++;
                if (meltdownTicks == 20 * 5) {
                    broadcast(StructuresMessages.get("meltdown_fail_critical",
                            "<red>Auxiliary shield integrity critical, failure imminent."));
                } else if (meltdownTicks == 20 * 10) {
                    broadcast(StructuresMessages.get("meltdown_fail_total",
                            "<red>Auxiliary shield failure detected, total system failure in T-10s."));
                }
                if (meltdownTicks >= 20 * 20) {
                    beginUnavoidable(false);
                }
            }
        }
    }

    /** The WARNING block of the aux shield announcement, one line per 5 ticks. */
    private String[] meltdownWarningLines() {
        return new String[] {
                StructuresMessages.get("meltdown_warn_1", "<gold>WARNING!!!"),
                StructuresMessages.get("meltdown_warn_2", "<white>Reactor <gold>Emergency Termination Sequence"),
                StructuresMessages.get("meltdown_warn_3", "<white>is now <gold>available<white>, auxiliary shield"),
                StructuresMessages.get("meltdown_warn_4", "<red>failure estimated in T-3m<white>, shut down reactor immediately."),
                StructuresMessages.get("meltdown_warn_5", "<gray>Load netherite items into the contents barrel (debris/scrap +1%, ingot +2%, block +3% each) and pulse the trigger lamp."),
                StructuresMessages.get("meltdown_warn_6", "<gold>This is not a drill, I repeat, this is not a drill!")
        };
    }

    /**
     * The detonation becomes unavoidable: the shield enters FAILED with the
     * T-10s countdown. The aux-shield expiry announces the standard
     * shield-failure broadcast; a failed attempt stays silent — it already
     * reported its own total-failure message.
     */
    private void beginUnavoidable(boolean announce) {
        meltdownPhase = MeltdownPhase.NONE;
        meltdownTicks = 0;
        shield.fail(announce);
        saveToDb();
    }

    // =========================
    // RESTABILIZATION ATTEMPT — the Trigger Action lamp pulse while the aux
    // shield holds. The success chance is the netherite value summed over
    // every item in the content barrel (debris/scrap +1%, ingot +2%, block
    // +3% — other items are ignored), capped by reactor.restab_chance_max;
    // the items are consumed whether the attempt succeeds or not.
    // =========================
    private void tryRestabilization() {
        int chance = countRestabValue();
        // Cap: the best possible attempt can never be certain (config, default 99%)
        restabChance = Math.min(ReactorConfig.getInstance().getRestabChanceMax(), chance);
        clearRestabItems();
        meltdownPhase = MeltdownPhase.RESTAB_SEQ;
        meltdownTicks = 0;
        broadcast(StructuresMessages.get("meltdown_attempt",
                "<white>Attempting reactor shutdown..."));
        saveToDb();
    }

    /** Rolls the captured chance: success → the automatic shutdown, failure → T-10s. */
    private void rollRestabilization() {
        if (Math.random() * 100.0 < restabChance) {
            broadcast(StructuresMessages.get("meltdown_success",
                    "<green>Reactor re-stabilization successful, performing reactor shutdown..."));
            meltdownPhase = MeltdownPhase.NONE;
            meltdownTicks = 0;
            lasers.setControlLocked(false);
            // The restabilized reactor runs the AUTOMATIC procedure — the
            // manual trigger procedure stays reserved for the operator lamp
            // pulse (the announcement comes from beginFinalShutdown).
            beginFinalShutdown(ShutdownTrigger.RESTAB);
        } else {
            broadcast(StructuresMessages.get("meltdown_fail_timeout",
                    "<red>Timed out waiting for reactor systems to respond..."));
            meltdownPhase = MeltdownPhase.RESTAB_FAIL;
            meltdownTicks = 0;
            saveToDb();
        }
    }

    /** The content barrel — the floor barrel the fusion debris lands in. */
    private Barrel contentBarrel() {
        if (reactorLocation == null) return null;
        Block block = reactorLocation.clone()
                .add(ReactorFusion.BARREL_FLOOR[0], ReactorFusion.BARREL_FLOOR[1], ReactorFusion.BARREL_FLOOR[2])
                .getBlock();
        return block.getState() instanceof Barrel barrel ? barrel : null;
    }

    /** Restabilization chance value per item type: debris/scrap +1%, ingot +2%, block +3%. */
    private static int restabValueOf(Material type) {
        return switch (type) {
            case ANCIENT_DEBRIS, NETHERITE_SCRAP -> 1;
            case NETHERITE_INGOT -> 2;
            case NETHERITE_BLOCK -> 3;
            default -> 0;
        };
    }

    /** Restabilization chance summed over every netherite item in the content barrel (stacks count fully). */
    private int countRestabValue() {
        Barrel barrel = contentBarrel();
        if (barrel == null) return 0;
        int chance = 0;
        for (ItemStack item : barrel.getInventory().getContents()) {
            if (item != null) {
                chance += restabValueOf(item.getType()) * item.getAmount();
            }
        }
        return chance;
    }

    /** Removes every netherite item from the content barrel (the attempt consumes them). */
    private void clearRestabItems() {
        Barrel barrel = contentBarrel();
        if (barrel == null) return;
        Inventory inv = barrel.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && restabValueOf(item.getType()) > 0) {
                inv.setItem(i, null);
            }
        }
    }

    /** Shield-depleted visual: explosion, fire and lava particles in the core. */
    private void spawnMeltdownExplosion() {
        if (reactorLocation == null) return;
        Location core = reactorLocation.clone().add(0.5, -4.5, 0.5);
        var world = core.getWorld();
        world.spawnParticle(Particle.EXPLOSION_EMITTER, core, 3, 0.5, 0.5, 0.5, 0);
        world.spawnParticle(Particle.EXPLOSION, core, 20, 1.0, 1.0, 1.0, 0.1);
        world.spawnParticle(Particle.FLAME, core, 80, 1.2, 1.2, 1.2, 0.08);
        world.spawnParticle(Particle.LAVA, core, 30, 1.0, 1.0, 1.0, 0);
        world.playSound(core, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.MASTER, 2.5f, 0.7f);
    }

    /** Aux shield up visual: smoke venting from the core chamber. */
    private void spawnMeltdownSmoke() {
        if (reactorLocation == null) return;
        Location core = reactorLocation.clone().add(0.5, -4.5, 0.5);
        core.getWorld().spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, core, 48, 0.8, 0.8, 0.8, 0.05);
    }

    /** Stall shutdown phase machine (every tick). */
    private void tickStallShutdown() {
        switch (stallPhase) {
            case NONE -> { }

            case STALL_WARN -> {
                // 5s dramatic pause after the reaction-failure warning (the
                // warning says "stall imminent") — then the shutdown starts
                // and the advancement is granted
                if (++stallTicks >= 20 * 5) {
                    // 🏆 Advancement: operator_incompetence — the core
                    // reaction died from cooling; the stall is happening
                    grantAdvancementNear("datapack/operator_incompetence");
                    beginFinalShutdown(ShutdownTrigger.AUTOMATIC);
                }
            }

            case SHIELD_RAMP -> {
                // Normal path: the ramp completes via onStallShieldDown().
                // A shield that dissolved instantly (the meltdown
                // restabilization auxiliary shield has no integrity to ramp)
                // never reports back — complete the ramp here.
                if (shield.getState() != ReactorShield.State.SHUTDOWN) {
                    onStallShieldDown();
                }
            }

            case WAIT_POWER -> {
                if (++stallTicks >= 20 * 5) {
                    broadcast(StructuresMessages.get("stall_power_lasers",
                            "<white>Shutting down power lasers..."));
                    lasers.shutDownLaser(ReactorLasers.LASER_P1);
                    // Every trigger dumps the heat to the lower limit before
                    // the next step (a stall at the limit passes through
                    // instantly)
                    stallPhase = StallPhase.HEAT_DUMP;
                    stallTicks = 0;
                }
            }
            case HEAT_DUMP -> {
                // Common heat dump: the core loses 10%/sec (of the temperature
                // it had at the shutdown start) until absolute zero — the next
                // shutdown step does not proceed before that. (Lasers are
                // already inert — the shutdown gates the heat/cool path.)
                double v = stallCoolPerTick + stallCoolRemainder;
                int whole = (int) v;
                stallCoolRemainder = v - whole;
                if (whole > 0) {
                    coreTemp = Math.max(tempMin, coreTemp - whole);
                }
                if (coreTemp <= tempMin) {
                    stallPhase = StallPhase.WAIT_P2;
                    stallTicks = 0;
                }
            }
            case WAIT_P2 -> {
                if (++stallTicks >= 20 * 3) {
                    lasers.shutDownLaser(ReactorLasers.LASER_P2);
                    broadcast(StructuresMessages.get("stall_success", "<green>Success."));
                    stallPhase = StallPhase.WAIT_STAB_MSG;
                    stallTicks = 0;
                }
            }
            case WAIT_STAB_MSG -> {
                if (++stallTicks >= 20 * 2) {
                    broadcast(StructuresMessages.get("stall_stab_lasers",
                            "<white>Shutting down stabilization lasers..."));
                    stallPhase = StallPhase.WAIT_STAB_OFF;
                    stallTicks = 0;
                }
            }
            case WAIT_STAB_OFF -> {
                if (++stallTicks >= 20 * 2) {
                    lasers.shutDownLaser(ReactorLasers.LASER_STAB);
                    broadcast(StructuresMessages.get("stall_success", "<green>Success."));
                    stallPhase = StallPhase.WAIT_ABS_MSG;
                    stallTicks = 0;
                }
            }
            case WAIT_ABS_MSG -> {
                if (++stallTicks >= 20 * 2) {
                    broadcast(StructuresMessages.get("stall_absorber",
                            "<white>Closing content absorber valve..."));
                    stallPhase = StallPhase.WAIT_ABS_OFF;
                    stallTicks = 0;
                }
            }
            case WAIT_ABS_OFF -> {
                if (++stallTicks >= 20 * 3) {
                    lasers.shutDownLaser(ReactorLasers.LASER_ABSORBER);
                    broadcast(StructuresMessages.get("stall_success", "<green>Success."));
                    stallPhase = StallPhase.WAIT_SHIELD_MSG;
                    stallTicks = 0;
                }
            }
            case WAIT_SHIELD_MSG -> {
                // 3s pause after the absorber "Success." — only then the shield message
                if (++stallTicks >= 20 * 3) {
                    broadcast(StructuresMessages.get("stall_shield",
                            "<white>Shutting down reactor shield..."));
                    shield.beginShutdown();
                    stallPhase = StallPhase.SHIELD_RAMP;
                    stallTicks = 0;
                }
            }
            case WAIT_OFFLINE_MSG -> {
                // 3s pause after the shield "Success." — only then the offline mark
                if (++stallTicks >= 20 * 3) {
                    broadcast(StructuresMessages.get("stall_offline",
                            "<white>Core marked as offline, awaiting for startup."));
                    finishStallShutdown();
                }
            }
        }
    }

    /** Final step of the stall shutdown: the core is marked offline. */
    private void finishStallShutdown() {
        // 🏆 Advancement: saved_from_extinction — only the EMERGENCY shutdown
        // counts (reaction failure, the automatic lower-limit stall). A manual
        // trigger shutdown or the restabilization success shutdown does not.
        boolean emergencyShutdown = stallTrigger == ShutdownTrigger.AUTOMATIC;
        lasers.setStarted(false);
        coreOfflineMarked = true;
        stallPhase = StallPhase.NONE;
        stallTicks = 0;
        stallTrigger = ShutdownTrigger.AUTOMATIC;
        stallCoolPerTick = 0;
        stallCoolRemainder = 0;
        if (emergencyShutdown) {
            grantAdvancementNear("datapack/saved_from_extinction");
        }
        saveToDb();
    }

    /**
     * The shield finished its smooth shutdown ramp (stall procedure) — report
     * "Success." and hold a 3s pause before the offline mark.
     */
    public void onStallShieldDown() {
        if (stallPhase != StallPhase.SHIELD_RAMP) return;
        broadcast(StructuresMessages.get("stall_success", "<green>Success."));
        stallPhase = StallPhase.WAIT_OFFLINE_MSG;
        stallTicks = 0;
    }

    /** Emergency core shutdown latch (shield integrity fell below the critical threshold). */
    private boolean coreEmergencyStopped = false;

    // =========================
    // STALL SHUTDOWN PROTOCOL: the reaction lost its heat. While the core is
    // running, threshold crossings broadcast one warning each (below 1M C* —
    // fusion stops, below 10k C* — critical). Reaching the reactor's dynamic
    // lower temperature limit (build-site biome × multiplier, −273 C* legacy)
    // fires the reaction-failure warning ("stall imminent") and, 5 seconds
    // later, starts the full automatic shutdown together with the
    // operator_incompetence advancement:
    // power lasers off one by one (their ±5% control detached), stab laser,
    // absorber valve, then the shield ramps down smoothly (Shutting down) and
    // the core is marked offline, awaiting a new trigger pulse. Control is
    // never blocked while the reactor is offline/starting/stopping — the
    // lasers simply have no effect and no particles are emitted.
    // =========================
    public enum StallPhase {
        NONE,            // idle
        STALL_WARN,      // limit reached: "reaction failure" warning → 5s → shutdown + advancement
        WAIT_POWER,      // 5s after the announcement → power lasers step
        HEAT_DUMP,       // every trigger: cool to the lower limit at 10%/sec of the shutdown-start temp
        WAIT_P2,         // P1 off → 3s → P2 off
        WAIT_STAB_MSG,   // 2s → "Shutting down stabilization lasers..."
        WAIT_STAB_OFF,   // 2s → stab off
        WAIT_ABS_MSG,    // 2s → "Closing content absorber valve..."
        WAIT_ABS_OFF,    // 3s → valve closed + "Success."
        WAIT_SHIELD_MSG, // 3s → "Shutting down reactor shield..." + the ramp begins
        SHIELD_RAMP,     // shield ramps down at 10%/sec (ReactorShield) → onStallShieldDown()
        WAIT_OFFLINE_MSG // shield "Success." → 3s → "Core marked as offline..." → done
    }

    /**
     * How the running final shutdown was entered. MANUAL is the operator
     * procedure (Trigger Action lamp pulse on a running reactor); AUTOMATIC
     * and RESTAB share the automatic chain — the trigger kind only selects
     * the announcement (due to manual / automatic trigger) and whether
     * the emergency-stall advancement counts (saved_from_extinction —
     * AUTOMATIC only).
     */
    public enum ShutdownTrigger {
        /** Operator procedure: the trigger lamp pulsed while the reactor was running. */
        MANUAL,
        /** Reaction failure: the core cooled down to its lower limit on its own. */
        AUTOMATIC,
        /** Meltdown restabilization attempt succeeded. */
        RESTAB
    }

    /** Manual shutdown (Trigger Action lamp re-pulse) is silently ignored above this shield stress %. */
    private static final double MANUAL_TRIGGER_MAX_STRESS = 10.0;

    private StallPhase stallPhase = StallPhase.NONE;
    private int stallTicks;
    /** How the running shutdown was entered (announcement + advancement kind). */
    private ShutdownTrigger stallTrigger = ShutdownTrigger.AUTOMATIC;
    /** HEAT_DUMP cooling rate, C* per tick: 10%/sec of the temp at the shutdown start. */
    private double stallCoolPerTick;
    private double stallCoolRemainder;
    /** Set once the stall shutdown completes — the startup sign shows Offline. */
    private boolean coreOfflineMarked = false;

    public boolean isStallShutdownActive() { return stallPhase != StallPhase.NONE; }
    public StallPhase getStallPhase() { return stallPhase; }

    // =========================
    // STARTUP SEQUENCE: the Trigger Action lamp pulse begins the cinematic
    // startup — announcement → 5s → stabilization lasers → power lasers →
    // absorber valve (each with its own 3s pauses) → shield forming at
    // 10%/sec → ignition: the core becomes operational, central particles
    // appear and the laser/absorber control takes effect (it is inert during
    // the sequence).
    // =========================
    public enum StartupPhase {
        NONE,             // idle
        WAIT_ANNOUNCE,    // 5s → "Starting up stabilization lasers..."
        WAIT_STAB_MSG,    // 3s → "Success."
        WAIT_STAB_PAUSE,  // 3s → "Starting up power lasers..."
        WAIT_POWER_MSG,   // 3s → "Success."
        WAIT_POWER_PAUSE, // 3s → "Opening content absorber valve..."
        WAIT_ABS_MSG,     // 3s → "Success."
        WAIT_ABS_PAUSE,   // 3s → "Forming reactor shield..." + shield forming begins
        SHIELD_FORMING,   // until integrity 100% (10%/sec) → "Success."
        WAIT_IGNITE,      // 3s → "Igniting reactor core..." (ignite + pre-heat)
        IGNITE_HEATING,   // the core ramps smoothly to the 20k idle burn (~5s)
        WAIT_COMPLETE     // 3s → "Reactor startup complete, resume normal operations."
    }

    private StartupPhase startupPhase = StartupPhase.NONE;
    private int startupTicks;

    /**
     * Core temperature right after ignition, C* — the idle burn level the
     * startup sequence leaves the core at. The reaction is running, so the
     * core must not sit at the lower-limit ambient until the operator ramps
     * the power lasers; without heating it decays back down at 1 C* per tick.
     */
    private static final int IGNITION_TEMP = 20_000;

    /** C* per tick during the ignition pre-heat ramp (~5s from ambient). */
    private double igniteRampRate;
    /** Fractional ignition-ramp accumulator. */
    private double igniteRampRemainder;

    public boolean isStartupSequenceActive() { return startupPhase != StartupPhase.NONE; }
    public StartupPhase getStartupPhase() { return startupPhase; }

    /** Startup sequence phase machine (every tick). */
    private void tickStartupSequence() {
        // The self-destruct protocol owns the reactor — the normal startup
        // cannot run alongside it: an in-flight sequence is aborted
        if (isSelfdestructActive()) {
            abortStartupSequence();
            return;
        }
        switch (startupPhase) {
            case NONE -> { }

            case WAIT_ANNOUNCE -> {
                if (++startupTicks >= 20 * 5) {
                    broadcast(StructuresMessages.get("startup_stab_lasers",
                            "<white>Starting up stabilization lasers..."));
                    startupPhase = StartupPhase.WAIT_STAB_MSG;
                    startupTicks = 0;
                }
            }
            case WAIT_STAB_MSG -> {
                if (++startupTicks >= 20 * 3) {
                    broadcast(StructuresMessages.get("stall_success", "<green>Success."));
                    startupPhase = StartupPhase.WAIT_STAB_PAUSE;
                    startupTicks = 0;
                }
            }
            case WAIT_STAB_PAUSE -> {
                if (++startupTicks >= 20 * 3) {
                    broadcast(StructuresMessages.get("startup_power_lasers",
                            "<white>Starting up power lasers..."));
                    startupPhase = StartupPhase.WAIT_POWER_MSG;
                    startupTicks = 0;
                }
            }
            case WAIT_POWER_MSG -> {
                if (++startupTicks >= 20 * 3) {
                    broadcast(StructuresMessages.get("stall_success", "<green>Success."));
                    startupPhase = StartupPhase.WAIT_POWER_PAUSE;
                    startupTicks = 0;
                }
            }
            case WAIT_POWER_PAUSE -> {
                if (++startupTicks >= 20 * 3) {
                    broadcast(StructuresMessages.get("startup_absorber",
                            "<white>Opening content absorber valve..."));
                    startupPhase = StartupPhase.WAIT_ABS_MSG;
                    startupTicks = 0;
                }
            }
            case WAIT_ABS_MSG -> {
                if (++startupTicks >= 20 * 3) {
                    broadcast(StructuresMessages.get("stall_success", "<green>Success."));
                    startupPhase = StartupPhase.WAIT_ABS_PAUSE;
                    startupTicks = 0;
                }
            }
            case WAIT_ABS_PAUSE -> {
                if (++startupTicks >= 20 * 3) {
                    broadcast(StructuresMessages.get("startup_shield",
                            "<white>Forming reactor shield..."));
                    shield.start();
                    startupPhase = StartupPhase.SHIELD_FORMING;
                    startupTicks = 0;
                }
            }
            case SHIELD_FORMING -> {
                // Safety: a lost CREATING state cannot build — re-arm it
                if (shield.getState() == ReactorShield.State.OFFLINE) {
                    shield.start();
                }
                if (shield.getIntegrity() >= 100) {
                    broadcast(StructuresMessages.get("stall_success", "<green>Success."));
                    startupPhase = StartupPhase.WAIT_IGNITE;
                    startupTicks = 0;
                }
            }
            case WAIT_IGNITE -> {
                if (++startupTicks >= 20 * 3) {
                    // The core is formed: particles appear, laser/absorber
                    // control takes effect (the inert gate lifts with WORKING)
                    broadcast(StructuresMessages.get("startup_ignite",
                            "<white>Igniting reactor core..."));
                    shield.ignite();
                    // Ignition pre-heat: ramp the core smoothly to the 20k
                    // idle burn level over ~5 seconds (~4k C* per second)
                    startupPhase = StartupPhase.IGNITE_HEATING;
                    startupTicks = 0;
                    igniteRampRate = Math.max(0, (IGNITION_TEMP - coreTemp) / (20.0 * 5));
                    igniteRampRemainder = 0;
                    if (igniteRampRate <= 0) igniteRampFinish(); // already at/above 20k
                }
            }

            case IGNITE_HEATING -> {
                // Mid-ramp restart self-heal: the phase persists in the DB but
                // the rate does not — re-derive it (~5s from the current temp)
                if (igniteRampRate <= 0) {
                    igniteRampRate = Math.max(0, (IGNITION_TEMP - coreTemp) / (20.0 * 5));
                }
                if (igniteRampRate <= 0 || coreTemp >= IGNITION_TEMP) {
                    igniteRampFinish();
                } else {
                    double v = igniteRampRate + igniteRampRemainder;
                    int whole = (int) v;
                    igniteRampRemainder = v - whole;
                    if (whole > 0) {
                        coreTemp = Math.min(IGNITION_TEMP, coreTemp + whole);
                    }
                    if (coreTemp >= IGNITION_TEMP) {
                        igniteRampFinish();
                    }
                }
            }
            case WAIT_COMPLETE -> {
                if (++startupTicks >= 20 * 3) {
                    broadcast(StructuresMessages.get("startup_complete",
                            "<white>Reactor startup complete, resume normal operations."));
                    startupPhase = StartupPhase.NONE;
                    startupTicks = 0;
                    saveToDb();
                }
            }
        }
    }

    /**
     * The ±5% control is inert during the whole startup sequence until the
     * core ignites ("Igniting reactor core..." step, shield WORKING).
     */
    public boolean isStartupControlInert() {
        return isStartupSequenceActive()
                && shield.getState() != ReactorShield.State.WORKING;
    }

    /**
     * Kills the in-flight startup sequence (self-destruct owns the reactor).
     * A still-forming CREATING shield is reset to OFFLINE — nothing formed
     * while the sequence ran should survive the abort.
     */
    private void abortStartupSequence() {
        if (startupPhase == StartupPhase.NONE) return;
        startupPhase = StartupPhase.NONE;
        startupTicks = 0;
        igniteRampRate = 0;
        igniteRampRemainder = 0;
        if (shield.getState() == ReactorShield.State.CREATING) {
            shield.reset();
        }
        saveToDb();
    }

    /** Ends the ignition pre-heat: the core is at the idle burn level, hand over to the completion message. */
    private void igniteRampFinish() {
        coreTemp = Math.max(coreTemp, IGNITION_TEMP);
        igniteRampRate = 0;
        igniteRampRemainder = 0;
        startupPhase = StartupPhase.WAIT_COMPLETE;
        startupTicks = 0;
        saveToDb();
    }
    /** Whether the core finished its shutdown and is awaiting a new trigger pulse. */
    public boolean isCoreOfflineMarked() { return coreOfflineMarked; }

    /**
     * The core is actually running: started, shield formed and no shutdown in
     * progress. Gates particles and heat/cool effects — while the reactor is
     * offline / forming / shutting down nothing is emitted.
     */
    public boolean isCoreActive() {
        return lasers.isStarted()
                && shield.getState() == ReactorShield.State.WORKING
                && !isStallShutdownActive();
    }

    public static ReactorManager getInstance() {
        // Kept for compatibility: the first (or only) reactor.
        return (reactors == null || reactors.isEmpty()) ? instance : reactors.get(0);
    }

    /** All active reactors (multi-reactor support). */
    public static java.util.List<ReactorManager> getReactors() {
        return reactors == null ? java.util.List.of() : reactors;
    }

    /** Finds an active reactor whose anchor is exactly at the given center. */
    public static ReactorManager getAt(Location center) {
        if (center == null) return null;
        Location n = LocationUtil.normalize(center);
        for (ReactorManager r : getReactors()) {
            if (r.reactorLocation != null && r.reactorLocation.equals(n)) return r;
        }
        return null;
    }

    /**
     * Finds the reactor whose structure contains the given block location
     * (DFC bounds: X ±5, Y −9..0, Z ±4 relative to the anchor).
     */
    public static ReactorManager getReactorForBlock(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        for (ReactorManager r : getReactors()) {
            Location rl = r.reactorLocation;
            if (rl == null || !loc.getWorld().equals(rl.getWorld())) continue;
            int dx = Math.abs(rl.getBlockX() - loc.getBlockX());
            int dy = Math.abs(rl.getBlockY() - loc.getBlockY());
            int dz = Math.abs(rl.getBlockZ() - loc.getBlockZ());
            if (dx <= 5 && dy <= 9 && dz <= 4) return r;
        }
        return null;
    }

    /** Creates a fresh unregistered reactor instance (for a NEW assembly). */
    public static ReactorManager createPending() {
        ReactorManager r = new ReactorManager();
        r.cfg = ReactorConfig.getInstance();
        r.copyConfig();
        return r;
    }

    public static void init() {
        if (instance != null) return; // prevent double-init
        instance = new ReactorManager();
        reactors = new java.util.ArrayList<>();
        ReactorConfig.init();
        instance.cfg = ReactorConfig.getInstance();
        instance.copyConfig();
        // The panel-sign registry is fully derived from the live reactors:
        // stale rows (a reactor disassembled while the server was down) are
        // wiped and every loaded reactor re-registers its panel signs.
        panelSignRegistry.clear();
        ReactorPersistence.clearPanelSigns();
        loadAll();
    }

    /** Clean shutdown — saves state, clears instance. Call before re-init for hot-toggle. */
    public static void shutdown() {
        if (instance != null) {
            saveAll();
            instance.setReactorLocation(null);
            instance = null;
            if (reactors != null) reactors.clear();
        }
    }

    // =========================
    // CONFIG (delegated to ReactorConfig)
    // =========================
    private ReactorConfig cfg;
    private boolean enabled;
    private int coreTempMax;
    private int coreWorkTemp;
    private double pressFollowRate;
    private double spinFollowRate;
    private int energyRate;
    private int meltdownExplosionRadius;

    private void copyConfig() {
        if (cfg == null) return;
        enabled = cfg.isEnabled();
        coreTempMax = cfg.getCoreTempMax();
        coreWorkTemp = cfg.getCoreWorkTemp();
        pressFollowRate = cfg.getPressFollowRate();
        spinFollowRate = cfg.getSpinFollowRate();
        energyRate = cfg.getEnergyRate();
        meltdownExplosionRadius = cfg.getMeltdownExplosionRadius();
    }

    // =========================
    // STATE
    // =========================
    private Location reactorLocation;
    private boolean valid;
    private String reactorId;

    // Core parameters
    // Hard temperature limits (C*): absolute zero .. 2 billion
    public static final int TEMP_MIN = -273;
    public static final int TEMP_MAX = 2_000_000_000;

    private int coreTemp;           // C*, [tempMin .. TEMP_MAX]
    /**
     * Dynamic lower temperature limit (C*) — rolled from the build-site
     * biome at assembly (biome temperature × multiplier + offset, rounded).
     * Every lower limit of this reactor (core cooling floor, stall point,
     * heat dump target, case ambient) uses it instead of the TEMP_MIN
     * constant, which remains only as the legacy default (pre-mechanic
     * reactors keep −273 until rebuilt).
     */
    private int tempMin = TEMP_MIN;
    private double shieldPress;     // Shield pressure, MPa — follows (T/10M) × 10.01
    private double spin;            // Core spin, RPS — follows 95 000 × (T/10M)

    // =========================
    // ENERGY GENERATION (output to cable network)
    // =========================
    private long energyGenerated;
    private double energyRemainder;

    // =========================
    // ENERGY BUFFER (startup cost, charged through the case junctions)
    // The reactor's own WAXED_CHISELED_COPPER case blocks double as network
    // junctions ("Junction" in the energy code): every case cell adjacent to
    // the cable network pulls energy into this buffer. A startup requires a
    // full buffer (reactor.startup_energy, default 5000) and consumes it.
    // =========================
    private int energyBuffer;
    private int junctionSaveCounter;

    // Previous integrity values for threshold warnings
    private int prevShInt = 100;
    private int prevCaseInt = 100;

    // Advancement tracking (one-time grants)
    private boolean advStartDfcGranted = false;
    private boolean advDfcUnstableGranted = false;
    private boolean advExplodeDfcGranted = false;
    private final Set<UUID> advInsideDfcGranted = ConcurrentHashMap.newKeySet();
    private final Set<UUID> advBurnInsideDfcGranted = ConcurrentHashMap.newKeySet();
    private final Set<UUID> advVaporisedInPlasmaGranted = ConcurrentHashMap.newKeySet();

    // 🏆 senior_reactor_ops: consecutive ticks with the shield integrity
    // inside the 0–10% band; leaving the band (or losing the WORKING shield)
    // resets the progress. One minute = 20 × 60 ticks.
    private int advSeniorOpsTicks = 0;
    private static final int SENIOR_OPS_TICKS_REQUIRED = 20 * 60;
    private static final double SENIOR_OPS_BAND_MAX = 10.0;

    private static final Map<UUID, Map<String, PendingAssembly>> pendingAssemblies = new HashMap<>();

    // =========================
    // PANEL SIGN REGISTRY — the control-panel DATA signs registered per
    // reactor at assembly (DB: reactor_panel_signs) and cleared at
    // disassembly. ONLY these signs demand the multimeter on click; every
    // other sign of the structure (roof lamp labels, etc.) keeps its normal
    // behavior. Key: StructureMarker.fullKey (worldUid:x,y,z) → reactorId.
    // =========================
    private static final Map<String, String> panelSignRegistry = new ConcurrentHashMap<>();

    /** Whether this position is a registered control-panel data sign. */
    public static boolean isPanelSign(Location loc) {
        Location norm = LocationUtil.normalize(loc);
        if (norm == null || norm.getWorld() == null) return false;
        return panelSignRegistry.containsKey(StructureMarker.fullKey(norm));
    }

    /** Registers (idempotent rewrite) this reactor's 8 panel signs — DB + RAM cache. */
    private void registerPanelSigns(Location base) {
        if (base == null || base.getWorld() == null || reactorId == null) return;
        ReactorPersistence.registerPanelSigns(reactorId, base);
        for (int[] off : ReactorDisplay.PANEL_SIGN_OFFSETS) {
            Location signLoc = new Location(base.getWorld(),
                    base.getBlockX() + off[0],
                    base.getBlockY() + off[1],
                    base.getBlockZ() + off[2]);
            panelSignRegistry.put(StructureMarker.fullKey(signLoc), reactorId);
        }
    }

    /** Removes one reactor's panel signs — DB + RAM cache. */
    private static void unregisterPanelSigns(String reactorId) {
        if (reactorId == null) return;
        ReactorPersistence.unregisterPanelSigns(reactorId);
        panelSignRegistry.values().removeIf(reactorId::equals);
    }

    public record PendingAssembly(Location center, org.bukkit.entity.ItemFrame frame, String type) {}

    public static void setPendingAssembly(Player player, Location center, org.bukkit.entity.ItemFrame frame, String type) {
        pendingAssemblies.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(type, new PendingAssembly(center, frame, type));
    }

    public static PendingAssembly getPendingAssembly(Player player, String type) {
        var map = pendingAssemblies.get(player.getUniqueId());
        return map != null ? map.get(type) : null;
    }

    public static void clearPendingAssembly(Player player) {
        pendingAssemblies.remove(player.getUniqueId());
    }

    // =========================
    // GRANT ADVANCEMENT HELPER
    // =========================
    private void grantAdvancement(Player player, String key) {
        try {
            var adv = Bukkit.getAdvancement(new org.bukkit.NamespacedKey("ui", key));
            if (adv != null) {
                var progress = player.getAdvancementProgress(adv);
                if (!progress.isDone()) {
                    progress.awardCriteria("1");
                }
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[Reactor] grantAdvancement error: " + e.getMessage());
        }
    }

    /**
     * Advancement grant radius — every DFC advancement is granted only to
     * players within this distance (blocks) of the DFC structure center.
     */
    private static final double ADV_GRANT_RADIUS = 30.0;

    /**
     * Broadcast radius (blocks from the anchor) — kept equal to the
     * advancement grant radius so every warning (e.g. the T-10s "run"
     * message) reaches exactly the audience eligible for the advancements.
     */
    private static final double BROADCAST_RADIUS = 30.0;

    /** The geometric center of the DFC structure (10×11×9 box around the anchor). */
    private Location reactorCenter() {
        return reactorLocation.clone().add(0.5, -4.5, 0.5);
    }

    /** Grants to every online player within {@link #ADV_GRANT_RADIUS} of the reactor center. */
    private void grantAdvancementNear(String key) {
        if (reactorLocation == null) return;
        double radiusSq = ADV_GRANT_RADIUS * ADV_GRANT_RADIUS;
        Location center = reactorCenter();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(center.getWorld())
                    && player.getLocation().distanceSquared(center) <= radiusSq) {
                grantAdvancement(player, key);
            }
        }
    }

    // =========================
    // CONSTRUCTOR
    // =========================
    private ReactorManager() {
        this.display = new ReactorDisplay(this);
        this.lasers = new ReactorLasers(this);
        this.shield = new ReactorShield(this);
        this.fuel = new ReactorFuel(this);
        this.fusion = new ReactorFusion(this);
        this.caseSys = new ReactorCase(this);
    }

    // =========================
    // DATABASE PERSISTENCE
    // =========================
    public static void saveAll() {
        for (ReactorManager r : getReactors()) {
            try { r.saveToDb(); } catch (Exception e) {
                ConsoleLogger.error("[Reactor] Save error: " + e.getMessage());
            }
        }
    }

    public void saveToDb() {
        ReactorState state = buildState(this);
        ReactorPersistence.saveToDb(state);
    }

    private static ReactorState buildState(ReactorManager r) {
        ReactorState s = new ReactorState();
        s.setReactorId(r.reactorId);
        s.setReactorLocation(r.reactorLocation);
        s.setCoreTemp(r.coreTemp);
        s.setTempMin(r.tempMin);
        s.setShieldPress(r.shieldPress);
        s.setSpin(r.spin);
        s.setFusionParticles(r.fusion.getParticles());
        s.setFusionCollected(r.fusion.getCollected());
        s.setCaseBroken(r.caseSys.isBroken());
        s.setCaseTemp(r.caseSys.getTemp());
        s.setCasePress(r.caseSys.getPress());
        s.setCaseIntegrity(r.caseSys.getIntegrity());
        s.setEnergyGenerated(r.energyGenerated);
        s.setEnergyBuffer(r.energyBuffer);
        s.setLaserStarted(r.lasers.isStarted());
        s.setStructureDamaged(r.structureDamaged);
        s.setShieldState(r.shield.getState().name());
        s.setShieldIntegrity(r.shield.getIntegrity());
        s.setShieldFailCountdown(r.shield.getFailCountdown());
        s.setSelfdestructPhase(r.selfdestructPhase.name());
        s.setSelfdestructTicks(r.selfdestructTicks);
        s.setSelfdestructDone(r.selfdestructDone);
        s.setSelfdestructCancelled(r.selfdestructCancelled);
        s.setCoreEmergencyStopped(r.coreEmergencyStopped);
        s.setMeltdownPhase(r.meltdownPhase.name());
        s.setMeltdownTicks(r.meltdownTicks);
        s.setRestabChance(r.restabChance);
        s.setStallPhase(r.stallPhase.name());
        s.setStallTicks(r.stallTicks);
        s.setStallTrigger(r.stallTrigger.name());
        s.setCoreOffline(r.coreOfflineMarked);
        s.setStartupPhase(r.startupPhase.name());
        s.setStartupTicks(r.startupTicks);
        s.setLaserPowers(new double[] {
                r.lasers.getPower(ReactorLasers.LASER_P1),
                r.lasers.getPower(ReactorLasers.LASER_P2),
                r.lasers.getPower(ReactorLasers.LASER_STAB),
                r.lasers.getPower(ReactorLasers.LASER_ABSORBER) });
        return s;
    }

    public static void loadAll() {
        if (instance == null || reactors == null) return;
        // Track existing locations so a second DB row for the same reactor is not loaded twice
        for (ReactorState st : ReactorPersistence.loadAllFromDb()) {
            Location loc = st.getReactorLocation();
            if (loc == null) continue;

            boolean exists = false;
            for (ReactorManager r : reactors) {
                if (r.reactorLocation != null && r.reactorLocation.equals(loc)) { exists = true; break; }
            }
            if (exists) continue;

            ReactorManager r = new ReactorManager();
            r.cfg = ReactorConfig.getInstance();
            r.copyConfig();
            reactors.add(r);
            r.applyLoadedState(st);
            // Self-heal: (re-)register the panel signs for every loaded
            // reactor — covers reactors built before the registry existed.
            r.registerPanelSigns(loc);
        }
    }

    /** Applies a DB-loaded state to this reactor instance (used by loadAll). */
    private void applyLoadedState(ReactorState state) {
        reactorLocation = state.getReactorLocation();
        valid = state.isValid();
        reactorId = state.getReactorId();
        coreTemp = state.getCoreTemp();
        tempMin = state.getTempMin();
        shieldPress = state.getShieldPress();
        spin = state.getSpin();
        fusion.setParticles(state.getFusionParticles());
        fusion.setCollected(state.getFusionCollected());
        caseSys.setTemp(state.getCaseTemp());
        caseSys.setPress(state.getCasePress());
        caseSys.setIntegrity(state.getCaseIntegrity());
        // The glass on disk is the truth: the breach tick re-derives the
        // integrity from the live block audit within one tick. A saved
        // breached case resumes quietly — no re-announcement, no silent
        // repair; the panes stay broken until players place them back.
        caseSys.setBreachAnnounced(state.isCaseBroken());
        energyGenerated = state.getEnergyGenerated();
        energyBuffer = Math.max(0, state.getEnergyBuffer());
        lasers.setStarted(state.isLaserStarted());
        structureDamaged = state.isStructureDamaged();
        coreEmergencyStopped = state.isCoreEmergencyStopped();
        selfdestructDone = state.isSelfdestructDone();
        selfdestructCancelled = state.isSelfdestructCancelled();
        selfdestructPhase = parseSelfdestructPhase(state.getSelfdestructPhase());
        selfdestructTicks = state.getSelfdestructTicks();
        meltdownPhase = parseMeltdownPhase(state.getMeltdownPhase());
        meltdownTicks = state.getMeltdownTicks();
        meltdownLineIndex = 0;
        restabChance = state.getRestabChance();
        stallPhase = parseStallPhase(state.getStallPhase());
        stallTicks = state.getStallTicks();
        coreOfflineMarked = state.isCoreOffline();
        // The trigger kind is persisted: a manual shutdown that already
        // passed its HEAT_DUMP phase must not masquerade as the emergency
        // stall after a restart. Rows saved before the stall_trigger column
        // fall back to the legacy stall_manual flag.
        stallTrigger = parseStallTrigger(state.getStallTrigger(), state.isStallManual());
        // Every trigger dumps the heat to the lower limit; the dump rate is re-derived
        // from the current temperature (the persisted phase survives the restart)
        stallCoolPerTick = Math.max(1.0, coreTemp * 0.10 / 20.0);
        stallCoolRemainder = 0;
        startupPhase = parseStartupPhase(state.getStartupPhase());
        startupTicks = state.getStartupTicks();

        // Shield: restore the exact phase + integrity + detonation countdown
        try {
            shield.setState(ReactorShield.State.valueOf(state.getShieldState()));
        } catch (Exception e) {
            shield.setState(state.isLaserStarted()
                    ? ReactorShield.State.WORKING : ReactorShield.State.OFFLINE);
        }
        shield.setIntegrity(state.getShieldIntegrity());
        shield.restoreFailCountdown(state.getShieldFailCountdown());
        if (shield.getState() == ReactorShield.State.WORKING && state.isLaserStarted()) {
            // legacy rows (pre-shield columns): keep the old full-restore behaviour
            shield.setIntegrity(Math.max(shield.getIntegrity(), 100));
        }

        // Self-destruct overpower finale: re-arm the forced 2000% ramp.
        // The control lamps are dead from the controls-down phase on.
        if (selfdestructPhase == SelfdestructPhase.FINALE) {
            lasers.beginOverpower();
        } else if (selfdestructPhase == SelfdestructPhase.CONTROLS_DOWN
                || selfdestructPhase == SelfdestructPhase.PROTOCOL_MSG
                || selfdestructPhase == SelfdestructPhase.TIMED
                || selfdestructPhase == SelfdestructPhase.DETONATION_MSG
                || selfdestructPhase == SelfdestructPhase.BYPASS_MSG) {
            lasers.setControlLocked(true);
        }

        // Meltdown restabilization: the stabilizers are cranked and the
        // control stays locked while any meltdown phase runs (the laser
        // powers themselves persist in the laser columns — stab at 200%)
        if (isMeltdownActive()) {
            lasers.setControlLocked(true);
        }

        // The protocol owns the reactor: a restored in-flight startup
        // sequence cannot run alongside it — abort it
        if (isSelfdestructActive() && startupPhase != StartupPhase.NONE) {
            abortStartupSequence();
        }

        // Stall procedure: a persisted SHUTDOWN shield (restart mid-ramp)
        // resumes its ramp; a SHUTDOWN shield without the stall phase (should
        // not happen) completes the shutdown instantly; a SHIELD_RAMP phase
        // without a SHUTDOWN shield re-arms the ramp.
        if (shield.getState() == ReactorShield.State.SHUTDOWN && stallPhase != StallPhase.SHIELD_RAMP) {
            if (stallPhase == StallPhase.NONE) {
                shield.setState(ReactorShield.State.OFFLINE);
                shield.setIntegrity(0);
                lasers.setStarted(false);
                coreOfflineMarked = true;
            } else {
                shield.setState(ReactorShield.State.WORKING);
            }
        } else if (stallPhase == StallPhase.SHIELD_RAMP
                && shield.getState() != ReactorShield.State.SHUTDOWN) {
            if (shield.getState() == ReactorShield.State.OFFLINE) {
                onStallShieldDown();
            } else {
                shield.beginShutdown();
            }
        }
        double[] lp = state.getLaserPowers();
        if (lp != null && lp.length >= 4) {
            lasers.setPower(ReactorLasers.LASER_P1, lp[0]);
            lasers.setPower(ReactorLasers.LASER_P2, lp[1]);
            lasers.setPower(ReactorLasers.LASER_STAB, lp[2]);
            lasers.setPower(ReactorLasers.LASER_ABSORBER, lp[3]);
        }
    }

    public static void deleteFromDb(String reactorId) {
        ReactorPersistence.deleteFromDb(reactorId);
    }

    /** Parses a persisted self-destruct phase name, NONE on any mismatch. */
    private static SelfdestructPhase parseSelfdestructPhase(String name) {
        if (name == null) return SelfdestructPhase.NONE;
        try {
            return SelfdestructPhase.valueOf(name);
        } catch (IllegalArgumentException e) {
            return SelfdestructPhase.NONE;
        }
    }

    /** Parses a persisted stall-shutdown phase name, NONE on any mismatch. */
    private static StallPhase parseStallPhase(String name) {
        if (name == null) return StallPhase.NONE;
        try {
            return StallPhase.valueOf(name);
        } catch (IllegalArgumentException e) {
            return StallPhase.NONE;
        }
    }

    /** Parses a persisted meltdown-restabilization phase name, NONE on any mismatch. */
    private static MeltdownPhase parseMeltdownPhase(String name) {
        if (name == null) return MeltdownPhase.NONE;
        try {
            return MeltdownPhase.valueOf(name);
        } catch (IllegalArgumentException e) {
            return MeltdownPhase.NONE;
        }
    }

    /**
     * Parses a persisted shutdown trigger kind. Rows saved before the
     * stall_trigger column (null/malformed) fall back to the legacy
     * stall_manual flag.
     */
    private static ShutdownTrigger parseStallTrigger(String name, boolean legacyManual) {
        if (name != null) {
            try {
                return ShutdownTrigger.valueOf(name);
            } catch (IllegalArgumentException ignored) {
                // fall through to the legacy flag
            }
        }
        return legacyManual ? ShutdownTrigger.MANUAL : ShutdownTrigger.AUTOMATIC;
    }

    /** Parses a persisted startup-sequence phase name, NONE on any mismatch. */
    private static StartupPhase parseStartupPhase(String name) {
        if (name == null) return StartupPhase.NONE;
        try {
            return StartupPhase.valueOf(name);
        } catch (IllegalArgumentException e) {
            return StartupPhase.NONE;
        }
    }

    // =========================
    // GET LOCATION
    // =========================
    public Location getReactorLocation() { return reactorLocation; }
    public boolean isValid() { return valid && reactorLocation != null; }
    public String getReactorId() { return reactorId; }

    // =========================
    // SET REACTOR LOCATION
    // =========================
    public void setReactorLocation(Location loc) {
        if (loc != null) {
            Location normalized = LocationUtil.normalize(loc);
            this.reactorLocation = normalized;
            this.valid = true;
            this.reactorId = "REACTOR-" + normalized.getWorld().getName() + "-"
                    + normalized.getBlockX()
                    + "-" + normalized.getBlockY()
                    + "-" + normalized.getBlockZ();
            // Register in the multi-reactor registry (multi-reactor support)
            if (reactors != null && !reactors.contains(this)) {
                reactors.add(this);
            }
            // Structure registry entry (structure_markers) for reactor identification
            StructureMarker.place(normalized, "reactor", UUID.randomUUID());
            // Stamp the lamp label signs immediately at assembly — the
            // periodic 10s pass would heal them too, but a fresh build
            // should show the white labels right away
            display.updateLampSigns(normalized);
            // Register the control-panel DATA signs (the only signs that
            // demand the multimeter); cleared at disassembly
            registerPanelSigns(normalized);
            // Roll the dynamic lower temperature limit from the build-site
            // biome — a disassemble + rebuild rolls a fresh one. Nothing in
            // this reactor may sit below its new limit (a fresh core starts
            // at 0, the case at the old limit — raise both).
            this.tempMin = rollTempLimit(normalized);
            if (coreTemp < tempMin) coreTemp = tempMin;
            if (caseSys.getTemp() < tempMin) caseSys.setTemp(tempMin);
            saveToDb();
        } else {
            if (reactorLocation != null) {
                StructureMarker.removeAt(reactorLocation);

                // Remove the cable node created by the reactor for energy output
                Location coreLoc = reactorLocation.clone().add(0, -1, 0);
                if (CableNetwork.exists(coreLoc)) {
                    CableNetwork.removeNode(coreLoc);
                }
            }
            String oldId = this.reactorId;
            Location oldLoc = this.reactorLocation;
            if (oldId != null) {
                deleteFromDb(oldId);
                // The panel signs stop demanding the multimeter
                unregisterPanelSigns(oldId);
            }
            this.reactorLocation = null;
            this.valid = false;
            this.reactorId = null;
            ReactorDamageTracker.resetAudit(oldLoc);
            resetAll();
        }
    }

    /**
     * Rolls the dynamic lower temperature limit from the build-site biome:
     * biome temperature × reactor.temp_limit_biome_multiplier +
     * reactor.temp_limit_offset, rounded to whole C* (the reactor
     * temperature has no fractions). The value is read live from the biome
     * holder, so datapack/custom biomes work; a biome without a temperature
     * value falls back to reactor.temp_limit_biome_fallback.
     */
    private int rollTempLimit(Location base) {
        ReactorConfig c = cfg != null ? cfg : ReactorConfig.getInstance();
        double biomeTemp;
        try {
            double t = base.getBlock().getTemperature();
            biomeTemp = Double.isFinite(t) ? t : c.getTempLimitBiomeFallback();
        } catch (Exception e) {
            biomeTemp = c.getTempLimitBiomeFallback();
        }
        double raw = biomeTemp * c.getTempLimitBiomeMultiplier() + c.getTempLimitOffset();
        return (int) Math.round(raw);
    }

    /** The reactor's dynamic lower temperature limit (C*), shared by every lower-limit check. */
    public int getTempMin() { return tempMin; }

    // =========================
    // VALIDATE STRUCTURE
    // =========================
    public void validateStructure() {
        if (reactorLocation == null) return;
        if (structureDamaged) {
            // A damaged structure is intentionally incomplete — do not tear the
            // reactor down while it is running uncontrolled. Recovery happens
            // through block repairs (addRepair) or a controlled shutdown.
            return;
        }
        boolean wasValid = valid;
        valid = ReactorStructure.isValid(reactorLocation, false);
        if (!valid && wasValid) {
            setReactorLocation(null);
        }
    }

    // =========================
    // MAIN TICK (every server tick)
    // =========================
    public void tick() {
        if (!enabled || !valid || reactorLocation == null) return;

        Location base = reactorLocation;
        int prevTemp = coreTemp;

        // West tower bulb = heater, east tower bulb = cooler (DFC 10×11×9 geometry)
        // =========================
        // LASERS — roof controls, per-tick ramp + smooth heating/cooling
        // Damaged structure: CONTROL is lost (lamps and sensors are dead) but
        // the core keeps RUNNING — the lasers hold their last power and keep
        // heating/cooling, the shield keeps taking stress. The only shutdown
        // path is the natural/absorber cooling down to 0 C*
        // (see checkControlledShutdown).
        // =========================
        lasers.tick(base);
        shield.tick(base);

        // =========================
        // EMERGENCY CORE SHUTDOWN — shield integrity below the critical
        // threshold (25% by default): the core shuts itself off, lasers reset.
        // Needs working control systems — never fires on a damaged structure
        // (sensors dead: the uncontrolled core cannot save itself).
        // The safety only rescues a shield that is failing on its own
        // (stress within limits): a deliberately overloaded shield
        // (stress above 100%) is the operators' responsibility — no rescue
        // while they keep riding the overheat (Senior ReactorOps).
        // =========================
        if (!coreEmergencyStopped
                && !structureDamaged
                && !isSelfdestructActive()
                && shield.getState() == ReactorShield.State.WORKING
                && shield.getIntegrity() > 0
                && shield.getIntegrity() < cfg.getShieldIntegrityShutdownPercent()
                && shield.getTotalStress() <= 100
                && !shield.isFailed()) {
            coreEmergencyStopped = true;
            lasers.reset();
            shield.setState(ReactorShield.State.OFFLINE);
            broadcast(StructuresMessages.get("core_emergency_shutdown",
                    "<dark_red>⚠ <red>Shield integrity critical — emergency core shutdown! Restart the reactor."));
            saveToDb();
        }

        // 🏆 Advancement: senior_reactor_ops — ride the dying shield: keep the
        // integrity within 0–10% for one minute straight. The deliberate
        // overload (stress above 100%) keeps the emergency shutdown away;
        // dropping the overload below 10% integrity lets it fire — and ends
        // the ride. A misstep to 0% is the detonation, to 11% — progress reset.
        if (shield.getState() == ReactorShield.State.WORKING
                && shield.getIntegrity() > 0
                && shield.getIntegrity() <= SENIOR_OPS_BAND_MAX) {
            if (++advSeniorOpsTicks >= SENIOR_OPS_TICKS_REQUIRED) {
                advSeniorOpsTicks = 0;
                grantAdvancementNear("datapack/senior_reactor_ops");
            }
        } else {
            advSeniorOpsTicks = 0;
        }

        boolean heating = lasers.isHeating();
        boolean cooling = lasers.isCooling();

        // =========================
        // BROADCAST STATE CHANGES
        // =========================
        if (heating != display.wasHeating()) {
            broadcast(heating ? "<gold>🔥 <yellow>Heating enabled" : "<gray>🔥 <white>Heating disabled");
            display.setHeating(heating);
        }
        if (cooling != display.wasCooling()) {
            broadcast(cooling ? "<aqua>❄ <dark_aqua>Cooling enabled" : "<gray>❄ <white>Cooling disabled");
            display.setCooling(cooling);
        }

        // 🏆 Advancement: start_dfc — reactor startup (trigger pulse starts the lasers)
        if (lasers.isStarted() && !advStartDfcGranted) {
            advStartDfcGranted = true;
            Bukkit.getScheduler().runTask(Main.getInstance(), () ->
                grantAdvancementNear("datapack/start_dfc"));
        }

        // 🏆 Advancement: dfc_self_destruct — the T-60s announcement just fired
        // (systems rebooted: sensors down → controls down → protocol message)
        if (selfdestructAnnounced) {
            selfdestructAnnounced = false;
            Bukkit.getScheduler().runTask(Main.getInstance(), () ->
                grantAdvancementNear("datapack/dfc_self_destruct"));
        }

        // 🏆 Advancement: power_of_fusion — the fusion completed one ancient debris
        if (fusionDebrisJustCrafted) {
            fusionDebrisJustCrafted = false;
            Bukkit.getScheduler().runTask(Main.getInstance(), () ->
                grantAdvancementNear("datapack/power_of_fusion"));
        }

        // =========================
        // INTEGRITY WARNING — removed (10-second spam while integrity < 100%).
        // Over-stress degradation is announced once per phase change by
        // ReactorShield (entering / leaving the degradation phase).
        // =========================

        // =========================
        // DAMAGE STATE WARNING — removed (spam). While the structure is damaged
        // the signs show the No signal screen and a single message announces it.
        // =========================

        // =========================
        // SELF-DESTRUCT STATE MACHINE
        // 1% chance at startup: 5s of No signal → 60s timed countdown →
        // overpower finale (Power Lasers at 1000%) until the report stage.
        // =========================
        tickSelfdestruct();

        // =========================
        // MELTDOWN RESTABILIZATION — the aux shield countdown, the WARNING
        // block and the restabilization attempt sequence
        // =========================
        tickMeltdown();

        // =========================
        // SHIELD PRESSURE & CORE SPIN
        // P follows (T/10M) × 10.01 MPa — 10.010 MPa at the 10M working point
        // (the .01 is the passive 1.01x multiplier). S follows 0.95×(T/10M) RPS.
        // Future sources (Power Lasers) add pressure via addShieldPress().
        // =========================
        shieldPress = Math.max(0, shieldPress + (pressureTarget(coreTemp) - shieldPress) * pressFollowRate);
        spin = Math.max(0, spin + (spinTarget(coreTemp) - spin) * spinFollowRate);

        // =========================
        // RADIATION INSIDE CORE CHAMBER
        // =========================
        if (coreTemp >= 100000) {
            int radiationAmount = Math.min(coreTemp / 500000, 10);
            int bx = base.getBlockX(), by = base.getBlockY(), bz = base.getBlockZ();
            Player[] online = Bukkit.getOnlinePlayers().toArray(new Player[0]);
            for (Player player : online) {
                if (!player.getWorld().equals(base.getWorld())) continue;
                Location ploc = player.getLocation();
                int px = ploc.getBlockX(), py = ploc.getBlockY(), pz = ploc.getBlockZ();
                if (px >= bx - 5 && px <= bx + 4
                        && py >= by - 9 && py <= by
                        && pz >= bz - 4 && pz <= bz + 4) {
                    RadiationManager.addRadiation(player, radiationAmount);

                    // 🏆 Advancement: inside_dfc — player inside the reactor
                    if (advInsideDfcGranted.add(player.getUniqueId())) {
                        Bukkit.getScheduler().runTask(Main.getInstance(), () ->
                            grantAdvancement(player, "datapack/inside_dfc"));
                    }

                    // 🏆 Advancement: burn_inside_dfc — lethal dose inside the reactor
                    if (RadiationManager.getRadiation(player) >= 6400) {
                        if (advBurnInsideDfcGranted.add(player.getUniqueId())) {
                            Bukkit.getScheduler().runTask(Main.getInstance(), () ->
                                grantAdvancement(player, "datapack/burn_inside_dfc"));
                        }
                    }
                }
            }
        }

        // =========================
        // NATURAL TEMP DECAY — passive cooling 1 C*/tick (always, even with
        // a damaged structure: the core coasts on its own decay), down to
        // the reactor's dynamic lower temperature limit
        // =========================
        if (coreTemp > tempMin) {
            coreTemp = Math.max(tempMin, coreTemp - 1);
        }

        // =========================
        // STALL — reaction lost its heat: threshold warnings (1M / 10k C*),
        // then the reaction-failure warning at the lower limit and the
        // automatic shutdown 5s later
        // =========================
        checkStallWarnings(prevTemp);
        tryTriggerStallShutdown(prevTemp);
        tickStallShutdown();

        // =========================
        // STARTUP SEQUENCE — cinematic startup driven by the Trigger Action lamp
        // =========================
        tickStartupSequence();

        // =========================
        // SHIELD INTEGRITY THRESHOLD WARNINGS (75%, 50%, 25%) — via the
        // stress model; the case integrity lives in ReactorCase. The
        // AUXILIARY shield (meltdown restabilization) has its own warning
        // set (75/50/25/10%) and the SHUTDOWN ramp is a planned shutdown —
        // neither is the regular shield degrading, so the regular warnings
        // stay silent there. prevShInt still tracks, so a real degradation
        // after the special state does not replay missed crossings.
        // =========================
        if (shield.getState() == ReactorShield.State.WORKING) {
            checkIntegrityThreshold(prevShInt, (int) Math.round(shield.getIntegrity()), "Shield");
        }
        prevShInt = (int) Math.round(shield.getIntegrity());

        // =========================
        // 🏆 dfc_unstable — the first sign of instability while the core runs:
        // any integrity (shield or case) below 100%
        // =========================
        checkDfcUnstable();

        // =========================
        // ENERGY GENERATION (capped at 50% while the structure is damaged)
        // =========================
        if (coreTemp > coreWorkTemp / 100) {
            double damageCap = structureDamaged ? 0.5 : 1.0;
            double energyPerTick = ((double) coreTemp / coreWorkTemp) * energyRate * damageCap;
            energyRemainder += energyPerTick;
            int toGenerate = (int) energyRemainder;
            if (toGenerate > 0) {
                energyRemainder -= toGenerate;
                energyGenerated += toGenerate;

                // Normalized output: push into the cable/battery network via
                // the unified traversal. The node under the reactor base is
                // the entry point (created on demand; it auto-connects to
                // adjacent cables). Energy the network can't accept is lost —
                // the old code silently dropped it into CABLE nodes (a no-op:
                // cables don't store energy) or a GENERATOR buffer nobody
                // consumed.
                Location coreLoc = base.clone().add(0, -1, 0);
                CableNetwork.addNode(coreLoc);
                EnergyFlow.push(coreLoc, toGenerate);
            }
        }
    }

    // =========================
    // ENERGY BUFFER — JUNCTION CHARGING (every 1s, from ReactorTask)
    // The case (WAXED_CHISELED_COPPER — the same material the energy code
    // calls a "Junction") accepts energy wherever it touches the cable
    // network: the block itself is registered as a network node when the
    // player places it, and a cable run against the wall makes its wall
    // cells network-adjacent. Verified against darkfusionreactor.nbt: the
    // whole case wall is waxed_chiseled_copper (the palette also has
    // waxed_copper_bars — bars are NOT junctions).
    // =========================
    private static final int JUNCTION_CHARGE_PER_PASS = 250; // ⚡/s into the buffer

    /** Called by {@link ReactorTask} once per second. */
    public void tickEnergyBuffer() {
        if (!enabled || !valid || reactorLocation == null) return;

        int max = startupEnergy();
        if (energyBuffer >= max) return;

        World world = reactorLocation.getWorld();
        int bx = reactorLocation.getBlockX(), by = reactorLocation.getBlockY(), bz = reactorLocation.getBlockZ();
        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) return;

        int pulled = 0;
        for (Location junction : findJunctions(world, bx, by, bz)) {
            if (pulled >= JUNCTION_CHARGE_PER_PASS) break;
            pulled += EnergyFlow.pull(junction,
                    Math.min(JUNCTION_CHARGE_PER_PASS - pulled, max - energyBuffer - pulled));
        }

        if (pulled <= 0) return;
        energyBuffer = Math.min(max, energyBuffer + pulled);

        // Persist at most every 5s while charging (the row is small, but
        // there is no reason to rewrite it every second).
        if (++junctionSaveCounter >= 5) {
            junctionSaveCounter = 0;
            saveToDb();
        }
    }

    /**
     * Case cells (dx −5..4, dy −9..1, dz −4..4 — the exact template bounds)
     * that are waxed_chiseled_copper AND connected to the cable network:
     * either the cell itself is a network node (placing the block registers
     * it) or a network node is right next to it.
     */
    private java.util.List<Location> findJunctions(World world, int bx, int by, int bz) {
        java.util.List<Location> junctions = new java.util.ArrayList<>();
        for (int dx = -5; dx <= 4; dx++) {
            for (int dy = -9; dy <= 1; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    Location loc = new Location(world, bx + dx, by + dy, bz + dz);
                    if (loc.getBlock().getType() != Materials.WAXED_CHISELED_COPPER) continue;
                    if (CableNetwork.getNode(loc) != null) {
                        junctions.add(loc);
                        continue;
                    }
                    for (Location near : LocationUtil.getNeighbors(loc)) {
                        if (CableNetwork.getNode(near) != null) {
                            junctions.add(loc);
                            break;
                        }
                    }
                }
            }
        }
        return junctions;
    }

    /** Startup energy requirement = the buffer capacity (config, default 5000). */
    private int startupEnergy() {
        ReactorConfig config = ReactorConfig.getInstance();
        return Math.max(0, config != null ? config.getStartupEnergy() : 5000);
    }

    public int getEnergyBuffer() { return energyBuffer; }

    // =========================
    // PRESSURE TICK (every 5s)
    // =========================
    public void tickPressure() {
        if (!enabled || !valid || reactorLocation == null) return;

        Location base = reactorLocation;
        // Pressure vent: 1.5 blocks above the core column top — otherwise the
        // smoke spawns inside the core chamber instead of venting outward.
        Location coreCenter = base.clone().add(0.5, -4.5, 0.5);

        int particleCount;
        int radAmount;

        if (shieldPress >= 8)       { particleCount = 512; radAmount = 600; }
        else if (shieldPress >= 6)  { particleCount = 256; radAmount = 500; }
        else if (shieldPress >= 4)  { particleCount = 128; radAmount = 400; }
        else if (shieldPress >= 2)  { particleCount = 64;  radAmount = 300; }
        else if (shieldPress >= 1)  { particleCount = 32;  radAmount = 200; }
        else                        { particleCount = 0;   radAmount = 0;   }

        if (particleCount > 0) {
            Location smokePos = coreCenter.clone().add(0, 4.0, 0);
            base.getWorld().spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE,
                    smokePos, particleCount, 0, 0, 0, 0.1);
            if (radAmount > 0) {
                RadiationManager.addRadiationNear(base, 4.0, radAmount);
            }
        }
    }

    // =========================
    // FUSION TICK (every tick) — fusion particles, absorber collection
    // =========================
    public void tickFusion() {
        if (!enabled || !valid || reactorLocation == null) return;
        // Damaged structure: fusion KEEPS running (control lost ≠ frozen) —
        // the case keeps heating and the glass can still melt.
        fusion.tick(reactorLocation);
        caseSys.tick(reactorLocation);
    }

    // =========================
    // SOUND TICK (every 10 ticks)
    // =========================
    public void tickSound() {
        display.tickSound();
    }

    // =========================
    // SMOOTH DISPLAY TICK (every tick)
    // =========================
    public void tickSmoothDisplay() {
        display.tickSmoothDisplay();
    }

    // =========================
    // VISUAL TICK (every tick - particles)
    // =========================
    public void tickVisual() {
        display.tickVisual();
    }

    // =========================
    // UPDATE DISPLAYS (signs)
    // =========================
    public void updateDisplays() {
        display.updateDisplays();
    }

    // =========================
    // INTEGRITY THRESHOLD CHECK (75% / 50% / 25% warnings)
    // =========================
    private void checkIntegrityThreshold(int prevVal, int currVal, String name) {
        if (currVal < prevVal) {
            if (currVal == 75 || currVal == 50 || currVal == 25) {
                broadcast("<dark_red>⚠ <red>" + name + " integrity: <white>" + currVal + "%");
            }
        }
    }

    // =========================
    // 🏆 dfc_unstable ADVANCEMENT — the first integrity loss of a running
    // reactor (shield or case below 100%). Granted once per reactor.
    // =========================
    private void checkDfcUnstable() {
        if (advDfcUnstableGranted || !isCoreActive()) return;
        if (shield.getIntegrity() < 100 || caseSys.getIntegrity() < 100) {
            advDfcUnstableGranted = true;
            Bukkit.getScheduler().runTask(Main.getInstance(), () ->
                grantAdvancementNear("datapack/dfc_unstable"));
        }
    }

    // =========================
    // FULL RESET (disassemble / teardown)
    // =========================
    private void resetReactorState() {
        coreTemp = 0;
        tempMin = TEMP_MIN;
        shieldPress = 0;
        spin = 0;
        lasers.reset();
        shield.reset();
        fusion.reset();
        prevShInt = 100;
        prevCaseInt = 100;
        energyGenerated = 0;
        energyBuffer = 0;
        energyRemainder = 0;
        structureDamaged = false;
        damageWarnTick = 0;
        selfdestructPhase = SelfdestructPhase.NONE;
        selfdestructTicks = 0;
        selfdestructDone = false;
        selfdestructCancelled = false;
        meltdownPhase = MeltdownPhase.NONE;
        meltdownTicks = 0;
        meltdownLineIndex = 0;
        restabChance = 0;
        stallPhase = StallPhase.NONE;
        stallTicks = 0;
        stallTrigger = ShutdownTrigger.AUTOMATIC;
        stallCoolPerTick = 0;
        stallCoolRemainder = 0;
        coreOfflineMarked = false;
        startupPhase = StartupPhase.NONE;
        startupTicks = 0;
        igniteRampRate = 0;
        igniteRampRemainder = 0;

        display.resetDisplay();

        saveToDb();
    }

    // =========================
    // CORE SHUTDOWN — damaged structure, cooled to 0 C* (controlled stop).
    // A damaged reactor cannot be run again: the teardown disassembles it and
    // it must be re-assembled from scratch. (A normal shutdown — intact
    // structure, lasers reset first — does NOT tear the reactor down.)
    // =========================
    public void checkControlledShutdown() {
        // Shutdown = the uncontrolled core cooled down to 0 C* (passive decay
        // and/or the Stab Laser holding its last power). Then the damaged
        // reactor powers down and disassembles.
        if (!structureDamaged || coreTemp > 0) return;

        structureDamaged = false;
        damageWarnTick = 0;
        broadcast(StructuresMessages.get("damage_shutdown_complete",
                "<green>✔ <white>Core cooled to <yellow>0 C* <white>— reactor stopped and powered down."));
        saveToDb();
        // Teardown — the damaged reactor disassembles and leaves the registry
        setReactorLocation(null);
    }

    // =========================
    // MELTDOWN
    // =========================
    private void meltdown() {
        energyRemainder = 0;
        energyGenerated = 0;
        energyBuffer = 0;

        if (reactorLocation == null) return;

        Location base = reactorLocation;
        Location coreCenter = base.clone().add(0.5, -5.5, 0.5);

        RadiationManager.addRadiationNear(coreCenter, 1.0, 6400);
        RadiationManager.addRadiationNear(coreCenter, 20.0, 3200);

        base.clone().add(0, -1, 0).getBlock().setType(Material.AIR);
        base.clone().add(0, -5, 0).getBlock().setType(Material.AIR);

        coreCenter.getWorld().spawn(coreCenter, org.bukkit.entity.Creeper.class, creeper -> {
            creeper.setPowered(true);
            creeper.setExplosionRadius(meltdownExplosionRadius);
            creeper.setMaxFuseTicks(0);
            creeper.setIgnited(true);
        });

        base.getWorld().strikeLightning(coreCenter);
        base.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, coreCenter, 100, 3.0, 3.0, 3.0, 0.5);
        base.getWorld().spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, coreCenter, 64, 0, 0, 0, 0.1);

        broadcast("<dark_red>☠ <red>Meltdown! The reactor core is destroyed!");

        // 🏆 Advancement: explode_dfc — reactor explosion
        if (!advExplodeDfcGranted) {
            advExplodeDfcGranted = true;
            grantAdvancementNear("datapack/explode_dfc");
        }

        // 🏆 Advancement: vaporised_in_plasma — player inside the reactor during the explosion
        if (reactorLocation != null) {
            int bx = reactorLocation.getBlockX(), by = reactorLocation.getBlockY(), bz = reactorLocation.getBlockZ();
            Player[] online = Bukkit.getOnlinePlayers().toArray(new Player[0]);
            for (Player player : online) {
                if (!player.getWorld().equals(base.getWorld())) continue;
                if (advVaporisedInPlasmaGranted.contains(player.getUniqueId())) continue;
                Location ploc = player.getLocation();
                int px = ploc.getBlockX(), py = ploc.getBlockY(), pz = ploc.getBlockZ();
                if (px >= bx - 2 && px <= bx + 2
                        && py >= by - 5 && py <= by
                        && pz >= bz - 2 && pz <= bz + 2) {
                    advVaporisedInPlasmaGranted.add(player.getUniqueId());
                    grantAdvancement(player, "datapack/vaporised_in_plasma");
                }
            }
        }

        setReactorLocation(null);
    }

    // =========================
    // RESET ALL PARAMETERS
    // =========================
    private void resetAll() {
        coreTemp = 0;
        tempMin = TEMP_MIN;
        shieldPress = 0;
        spin = 0;
        lasers.reset();
        shield.reset();
        fuel.reset();
        fusion.reset();
        caseSys.reset();
        structureDamaged = false;
        damageWarnTick = 0;
        coreEmergencyStopped = false;
        selfdestructPhase = SelfdestructPhase.NONE;
        selfdestructTicks = 0;
        selfdestructDone = false;
        selfdestructCancelled = false;
        meltdownPhase = MeltdownPhase.NONE;
        meltdownTicks = 0;
        meltdownLineIndex = 0;
        restabChance = 0;
        stallPhase = StallPhase.NONE;
        stallTicks = 0;
        stallTrigger = ShutdownTrigger.AUTOMATIC;
        stallCoolPerTick = 0;
        stallCoolRemainder = 0;
        coreOfflineMarked = false;
        startupPhase = StartupPhase.NONE;
        startupTicks = 0;
        igniteRampRate = 0;
        igniteRampRemainder = 0;
        energyGenerated = 0;
        energyBuffer = 0;
        energyRemainder = 0;
        junctionSaveCounter = 0;
        broadcastCooldowns.clear();
        prevShInt = 100;
        prevCaseInt = 100;

        display.resetDisplay();
    }

    // =========================
    // GETTERS
    // =========================
    public int getCoreTemp() { return coreTemp; }
    public double getShieldPress() { return shieldPress; }
    public double getCoreSpin() { return spin; }
    public int getCoreShInt() { return (int) Math.round(shield.getIntegrity()); }
    public int getCoreCaseTemp() { return caseSys.getTemp(); }
    public double getCoreCasePress() { return caseSys.getPress(); }
    public int getCoreCaseInt() { return caseSys.getIntegrity(); }
    public boolean isCaseBroken() { return caseSys.isBroken(); }

    /** Whether the structure is damaged (uncontrolled-core mode). */
    public boolean isStructureDamaged() { return structureDamaged; }

    /** Whether the core is emergency-stopped (integrity below the critical threshold). */
    public boolean isCoreEmergencyStopped() { return coreEmergencyStopped; }

    /** Shield failure countdown (detonation timer) — delegated to ReactorShield. */
    public boolean isMeltdownCountdown() { return shield.getState() == ReactorShield.State.FAILED; }
    public int getMeltdownTimer() { return shield.getFailCountdown(); }
    public long getEnergyGenerated() { return energyGenerated; }

    public int getCoreWorkTemp() { return coreWorkTemp; }
    public int getEnergyRate() { return energyRate; }
    /** Fuel status for the display: both side fuel barrels contain their fuel. */
    public boolean hasBarrelFuelPublic() { return hasBarrelFuel(); }

    // =========================
    // DFC STAT MODEL HELPERS
    // =========================
    /**
     * Shield pressure target, MPa: (T / working temp) × 10.01.
     * At the 10M C* working point this is exactly 10.010 MPa — the .01 comes
     * from the passive 1.01x multiplier; multipliers add up (lasers add more later).
     */
    public double pressureTarget(double temp) {
        if (coreWorkTemp <= 0) return 0;
        return Math.max(0, temp / coreWorkTemp) * 10.01;
    }

    /** Core spin target, RPS: 95 000 RPS at the 10M working point (scales linearly). */
    public double spinTarget(double temp) {
        if (coreWorkTemp <= 0) return 0;
        return Math.max(0, temp / coreWorkTemp) * 95000.0;
    }

    /** Adds shield pressure from external sources (Power Lasers etc.), clamped ≥ 0. */
    public void addShieldPress(double mPa) {
        shieldPress = Math.max(0, shieldPress + mPa);
    }

    /** Applies a signed core spin delta (RPS), clamped ≥ 0. */
    public void applySpinDelta(double delta) {
        spin = Math.max(0, spin + delta);
    }

    /** Applies a signed core temperature delta (from lasers), clamped to hard limits. */
    public void applyCoreTempDelta(int delta) {
        coreTemp = Math.max(tempMin, Math.min(TEMP_MAX, coreTemp + delta));
    }

    /** Bulb power check for the laser system. */
    public boolean isBulbPoweredAt(Location base, int dx, int dy, int dz) {
        return display.isBulbPowered(base, dx, dy, dz);
    }

    /**
     * Broadcast to nearby players — the [UI][DFC] prefix is added exactly once
     * by {@link #broadcast(String)} (this used to prepend it a second time,
     * producing "[UI] [DFC] [UI] [DFC] ...").
     */
    public void broadcastRaw(String message) {
        broadcast(message);
    }

    public ReactorLasers getLasers() { return lasers; }
    public ReactorShield getShield() { return shield; }
    public ReactorFuel getFuel() { return fuel; }
    public ReactorFusion getFusion() { return fusion; }
    public ReactorCase getCase() { return caseSys; }

    // =========================
    // SENSOR DEAD — the signs show the No signal screen instead of readings
    // (self-destruct pre-timer phases, damaged structure, shield detonation
    // countdown). The timed phase itself shows the protocol screen.
    // =========================
    public boolean isSensorsDead() {
        return structureDamaged
                || isMeltdownActive()
                || selfdestructPhase == SelfdestructPhase.SENSORS_DOWN
                || selfdestructPhase == SelfdestructPhase.CONTROLS_DOWN
                || selfdestructPhase == SelfdestructPhase.PROTOCOL_MSG
                || shield.isFailed();
    }

    /** Fuel tick (every second): consumption by spin + spin decay when dry. */
    public void tickFuel() {
        // Damaged structure: fuel keeps burning (processes continue)
        if (!enabled || !valid || reactorLocation == null) return;
        fuel.ensureNamed(reactorLocation);
        fuel.tick(reactorLocation);
    }

    /** Fuel Stats: average fill % of both fuel barrels (F indicator). */
    public int getFuelFillPercent() {
        if (reactorLocation == null) return 0;
        return fuel.getFillPercent(reactorLocation);
    }

    /** Fuel Stats: current consumption % (M indicator). */
    public double getFuelConsumptionPct() { return fuel.getConsumptionPct(); }

    /** Both fuel barrels contain fuel (gold ingot / diamond). */
    public boolean hasBarrelFuel() {
        if (reactorLocation == null) return false;
        return fuel.hasFuel(reactorLocation);
    }

    /** Called by the fusion system right after one ancient debris is crafted. */
    public void onFusionDebrisCrafted() {
        fusionDebrisJustCrafted = true;
    }

    /**
     * Called by the Trigger Action lamp pulse. Routes the action by the
     * reactor state: a self-destruct owns the reactor (ignored), an emergency
     * stop is acknowledged, a running core shuts down manually, an aux-shield
     * meltdown attempts restabilization — and a fresh core begins the
     * cinematic startup sequence.
     */
    public void onTriggerPulse() {
        // The self-destruct protocol owns the reactor: no normal startup
        // while it runs (the pulse is silently ignored)
        if (isSelfdestructActive()) {
            return;
        }
        if (coreEmergencyStopped) {
            coreEmergencyStopped = false;
            broadcast(StructuresMessages.get("core_restart_after_shutdown",
                    "<green>✔ <white>Core restarted after the emergency shutdown."));
        }
        // New run: the offline mark goes away and the ±5% control lamps work again
        coreOfflineMarked = false;
        lasers.clearControlDisable();
        rollSelfdestruct();
        if (isSelfdestructActive()) {
            // The roll just armed the protocol — the startup sequence is
            // aborted before it begins (the protocol replaces it)
            return;
        }

        // =========================
        // ENERGY GATE — the startup is only possible with a full energy
        // buffer (charged through the case junctions from the cable
        // network). Not enough → the startup is blocked and the charge
        // is announced out of the requirement.
        // =========================
        int required = startupEnergy();
        if (energyBuffer < required) {
            broadcast(StructuresMessages.get("startup_no_energy",
                            "<red>⚡ <white>Not enough energy to start the reactor!"
                                    + " <gray>Buffer: <yellow>%have%<gray>/<white>%need% ⚡"
                                    + " <dark_gray>(charge through the case junctions)")
                    .replace("%have%", String.valueOf(energyBuffer))
                    .replace("%need%", String.valueOf(required)));
            return;
        }
        // The startup consumes the whole buffer ("после стартапа она тратится")
        energyBuffer = 0;
        // Latch the laser system now that the startup is accepted: pulses
        // route to the manual-shutdown handler until a shutdown resets it
        // (the latch used to be set before this method — a rejected pulse
        // wedged the reactor: every next pulse was silently ignored).
        lasers.setStarted(true);

        // The shield starts forming later, at the "Forming reactor shield..." step
        startupPhase = StartupPhase.WAIT_ANNOUNCE;
        startupTicks = 0;
        broadcast(StructuresMessages.get("startup_initiated",
                "<white>Core startup initiated due to a manual trigger, please wait."));
        saveToDb();
    }

    /** Shield breach detonation — tears down the reactor. */
    public void onShieldDetonated() {
        meltdown();
    }

    // Smoothed display values (delegated to ReactorDisplay)
    public int getDisplayCoreTemp() { return display.getDisplayCoreTemp(); }    public double getDisplayShieldPress() { return display.getDisplayShieldPress(); }
    public double getDisplayCoreSpin() { return display.getDisplayCoreSpin(); }
    public int getDisplayCoreShInt() { return display.getDisplayCoreShInt(); }
    public int getDisplayCoreCaseTemp() { return display.getDisplayCoreCaseTemp(); }
    public int getDisplayCoreCasePress() { return display.getDisplayCoreCasePress(); }
    public int getDisplayCoreCaseInt() { return display.getDisplayCoreCaseInt(); }
    public int getDisplayEnergyRate() { return display.getDisplayEnergyRate(); }

    // =========================
    // HELPER: BROADCAST
    // Messages within a 30-block radius (same audience as the advancement
    // grants). Identical messages are throttled (3s cooldown) so a lever
    // flickering or a rapid pulse train can't spam the chat.
    // =========================
    private static final long BROADCAST_COOLDOWN_MS = 3000;
    private final Map<String, Long> broadcastCooldowns = new HashMap<>();

    private void broadcast(String message) {
        long now = System.currentTimeMillis();
        Long last = broadcastCooldowns.get(message);
        if (last != null && now - last < BROADCAST_COOLDOWN_MS) return;
        broadcastCooldowns.put(message, now);

        String prefix = MessageUtil.PREFIX + "<dark_gray>[<yellow>DFC<dark_gray>] ";
        Player[] online = Bukkit.getOnlinePlayers().toArray(new Player[0]);
        for (Player player : online) {
            if (reactorLocation != null
                    && player.getWorld().equals(reactorLocation.getWorld())
                    && player.getLocation().distanceSquared(reactorLocation) <= BROADCAST_RADIUS * BROADCAST_RADIUS) {
                player.sendMessage(MessageUtil.parse(prefix + message));
            }
        }
    }

    // =========================
    // DAMAGE / REPAIR REPORTS (localized Attention! messages, [UI][DFC] prefix)
    // =========================

    /** Localized category name for the damage report placeholders. */
    private static String catName(ReactorDamageTracker.Category cat) {
        return switch (cat) {
            case GLASS -> StructuresMessages.get("damage_cat_glass", "Case glass");
            case SIGN -> StructuresMessages.get("damage_cat_sign", "Sign panel");
            case BULB -> StructuresMessages.get("damage_cat_bulb", "Control bulb");
            case STRUCTURE -> StructuresMessages.get("damage_cat_structure", "Structure");
        };
    }

    /**
     * Block-level damage report from the listener: one cell of the given
     * category was broken inside the structure. The cell is marked missing in
     * the audit cache immediately (O(1)) — the counts in the report are exact.
     */
    public void addDamage(int dx, int dy, int dz, ReactorDamageTracker.Category cat) {
        ReactorDamageTracker.noteCellBroken(reactorLocation, dx, dy, dz);
        reportDamage(cat);
    }

    /** Broadcasts the damage message for a category using the audit cache. */
    private void reportDamage(ReactorDamageTracker.Category cat) {
        if (reactorLocation == null || !ReactorDamageTracker.isTracked()) return;

        // Only "everything else" (core copper, stairs, rods, barrels…) puts the
        // reactor into uncontrolled mode. Broken bulbs/signs physically stop
        // working on their own; glass is the case system's domain.
        if (cat == ReactorDamageTracker.Category.STRUCTURE && !structureDamaged) {
            structureDamaged = true;
            damageWarnTick = 0;
            broadcast(StructuresMessages.get("damage_uncontrolled",
                            "<gold>❕ <white>Reactor structure damaged — control lost! Cool the core down to <yellow>0 C*"));
            broadcast(StructuresMessages.get("sensor_no_signal",
                            "<red>Cannot receive any data from sensors: <gray>No signal"));
        }

        // Case glass: per-pane reports would spam — the case announces exactly
        // three events itself (breach detected / case failure / breach
        // restored, see ReactorCase.tickBreach).
        if (cat == ReactorDamageTracker.Category.GLASS) return;

        // Remaining/total of the AFFECTED category (glass → glass cells, etc.)
        int[] c = ReactorDamageTracker.cachedCount(reactorLocation, cat);
        boolean fullyGone = c[0] <= 0;
        String key = fullyGone ? "failure_report" : "damage_report";
        String body = StructuresMessages.get(key,
                        fullyGone
                                ? "<gold>Attention! <white>%cat% failure detected! <dark_gray>(<green>%left%<gray>/<white>%total%<dark_gray>"
                                : "<gold>Attention! <white>%cat% damage detected! <dark_gray>(<green>%left%<gray>/<white>%total%<dark_gray>")
                .replace("%cat%", catName(cat))
                .replace("%left%", String.valueOf(c[0]))
                .replace("%total%", String.valueOf(c[1]));
        broadcast(body);
        saveToDb();
    }

    /**
     * Block-level repair report from the listener: a cell of the given category
     * was restored. When every tracked template cell matches the world again,
     * the structure counts as repaired.
     */
    public void addRepair(int dx, int dy, int dz, ReactorDamageTracker.Category cat) {
        ReactorDamageTracker.noteCellRepaired(reactorLocation, dx, dy, dz);
        reportRepair(cat);
    }

    /** Broadcasts the repair message for a category using the audit cache. */
    private void reportRepair(ReactorDamageTracker.Category cat) {
        if (reactorLocation == null || !ReactorDamageTracker.isTracked()) return;

        // Case glass: no per-pane reports — the case system owns the messaging.
        // The progress persist stays: every placed pane raises the integrity
        // by one point (the breach tick derives it from the live counts).
        if (cat == ReactorDamageTracker.Category.GLASS) {
            caseSys.onGlassPlaced();
            return;
        }

        // Remaining/total of the AFFECTED category (glass → glass cells, etc.)
        int[] c = ReactorDamageTracker.cachedCount(reactorLocation, cat);
        String body = StructuresMessages.get("repair_report",
                "<gold>Attention! <white>%cat% repair detected! <dark_gray>(<green>%left%<gray>/<white>%total%<dark_gray>")
                .replace("%cat%", catName(cat))
                .replace("%left%", String.valueOf(c[0]))
                .replace("%total%", String.valueOf(c[1]));
        broadcast(body);

        // Sign panels are back — drop the cached text so the sensors rewrite them
        if (cat == ReactorDamageTracker.Category.SIGN) {
            display.resetSignCache();
        }

        if (ReactorDamageTracker.cachedAllPresent(reactorLocation) && structureDamaged) {
            structureDamaged = false;
            damageWarnTick = 0;
            broadcast(StructuresMessages.get("structure_repaired",
                    "<green>✔ <white>Reactor structure fully restored — control returned."));
        } else if (cat == ReactorDamageTracker.Category.STRUCTURE
                && ReactorDamageTracker.cachedCount(reactorLocation, ReactorDamageTracker.Category.STRUCTURE)[0]
                        >= ReactorDamageTracker.totalOf(ReactorDamageTracker.Category.STRUCTURE)
                && structureDamaged) {
            // All "control" cells are back — control returns even if some
            // glass/signs are still missing (those are cosmetic/physical only).
            structureDamaged = false;
            damageWarnTick = 0;
            broadcast(StructuresMessages.get("structure_repaired",
                    "<green>✔ <white>Reactor structure fully restored — control returned."));
        }
        saveToDb();
    }

    // =========================
    // ROTATING STRUCTURE AUDIT (every tick, ~50 cells — full pass in ~1s)
    // Catches event-blind block changes (explosions, pistons, plugin
    // block.setType()) and reports them like listener damage/repair.
    // =========================
    public void tickStructureAudit() {
        if (reactorLocation == null || !valid) return;
        ReactorDamageTracker.AuditResult res = ReactorDamageTracker.auditTick(reactorLocation);
        for (ReactorDamageTracker.Category cat : res.damaged()) {
            reportDamage(cat);
        }
        for (ReactorDamageTracker.Category cat : res.repaired()) {
            reportRepair(cat);
        }
    }
}
