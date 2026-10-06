package com.ultimateimprovments.energy.generation.reactor;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.structure.StructureMarker;
import com.ultimateimprovments.energy.transfer.cable.CableNetwork;
import com.ultimateimprovments.energy.transfer.cable.CableNode;
import com.ultimateimprovments.energy.transfer.cable.NodeType;
import com.ultimateimprovments.mechanics.environment.radiation.RadiationManager;
import com.ultimateimprovments.util.LocationUtil;
import com.ultimateimprovments.util.ConsoleLogger;
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
    private boolean selfdestructJustRolled; // one-tick latch: the protocol armed → dfc_self_destruct grant
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

    /** Rolls the 1% self-destruct chance at startup. */
    private void rollSelfdestruct() {
        ReactorConfig cfg = ReactorConfig.getInstance();
        if (selfdestructDone || isSelfdestructActive()) return;
        if (Math.random() * 100.0 < cfg.getSelfdestructChance()) {
            selfdestructPhase = SelfdestructPhase.SENSORS_DOWN;
            selfdestructTicks = 0;
            selfdestructJustRolled = true;
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
        if (prevTemp > 0 && coreTemp <= 0) {
            broadcast(StructuresMessages.get("stall_warn_failure",
                    "<aqua>Core reaction failure due to critically low temperatures, reaction stall imminent!"));
        }
    }

    /**
     * Starts the stall shutdown when the core reaches absolute zero (−273 C*)
     * — crossing into it, so a reactor restarted cold does not instantly
     * re-stall: it must first gain heat and lose it again.
     */
    private void tryTriggerStallShutdown(int prevTemp) {
        if (stallPhase != StallPhase.NONE) return;
        if (structureDamaged || !lasers.isStarted() || isSelfdestructActive()) return;
        if (shield.getState() != ReactorShield.State.WORKING) return;
        if (prevTemp <= TEMP_MIN || coreTemp > TEMP_MIN) return;

        stallPhase = StallPhase.WAIT_POWER;
        stallTicks = 0;
        broadcast(StructuresMessages.get("stall_shutdown_initiated",
                "<white>Core shutdown initiated due to reaction failure, please wait."));
        saveToDb();
    }

    /**
     * Manual shutdown: the startup lamp pulsed while the core is already
     * running. Silently ignored while the shield stress is above 10% (nothing
     * happens); otherwise the stall procedure runs with the manual trigger
     * announcement, and after the power lasers step the core dumps all its
     * heat to −273 C* at 10%/sec of the temperature it had at the shutdown
     * start — the next shutdown step waits until −273 is reached.
     */
    public void tryManualShutdownTrigger() {
        if (stallPhase != StallPhase.NONE) return;
        if (structureDamaged || isSelfdestructActive()) return;
        if (shield.getState() != ReactorShield.State.WORKING) return;
        if (shield.getTotalStress() > MANUAL_TRIGGER_MAX_STRESS) return;

        stallManual = true;
        stallCoolPerTick = Math.max(1.0, coreTemp * 0.10 / 20.0);
        stallPhase = StallPhase.WAIT_POWER;
        stallTicks = 0;
        broadcast(StructuresMessages.get("stall_shutdown_manual",
                "<white>Core shutdown initiated due to manual trigger, please wait."));
        saveToDb();
    }

    /** Stall shutdown phase machine (every tick). */
    private void tickStallShutdown() {
        switch (stallPhase) {
            case NONE, SHIELD_RAMP -> { /* SHIELD_RAMP completes via onStallShieldDown() */ }

            case WAIT_POWER -> {
                if (++stallTicks >= 20 * 5) {
                    broadcast(StructuresMessages.get("stall_power_lasers",
                            "<white>Shutting down power lasers..."));
                    lasers.shutDownLaser(ReactorLasers.LASER_P1);
                    // Manual shutdown: dump the heat to -273 C* before the next step
                    stallPhase = stallManual ? StallPhase.HEAT_DUMP : StallPhase.WAIT_P2;
                    stallTicks = 0;
                }
            }
            case HEAT_DUMP -> {
                // Manual shutdown heat dump: the core loses 10%/sec (of the
                // temperature it had at the shutdown start) until absolute
                // zero — the next shutdown step does not proceed before that.
                // (Lasers are already inert — stall gates the heat/cool path.)
                double v = stallCoolPerTick + stallCoolRemainder;
                int whole = (int) v;
                stallCoolRemainder = v - whole;
                if (whole > 0) {
                    coreTemp = Math.max(TEMP_MIN, coreTemp - whole);
                }
                if (coreTemp <= TEMP_MIN) {
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
        lasers.setStarted(false);
        coreOfflineMarked = true;
        stallPhase = StallPhase.NONE;
        stallTicks = 0;
        stallManual = false;
        stallCoolPerTick = 0;
        stallCoolRemainder = 0;
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
    // fusion stops, below 10k C* — critical, below 0 C* — reaction failure).
    // Reaching absolute zero (−273 C*) starts the full automatic shutdown:
    // power lasers off one by one (their ±5% control detached), stab laser,
    // absorber valve, then the shield ramps down smoothly (Shutting down) and
    // the core is marked offline, awaiting a new startup pulse. Control is
    // never blocked while the reactor is offline/starting/stopping — the
    // lasers simply have no effect and no particles are emitted.
    // =========================
    public enum StallPhase {
        NONE,            // idle
        WAIT_POWER,      // 5s after the announcement → power lasers step
        HEAT_DUMP,       // manual only: cool to -273 C* at 10%/sec of the shutdown-start temp
        WAIT_P2,         // P1 off → 3s → P2 off
        WAIT_STAB_MSG,   // 2s → "Shutting down stabilization lasers..."
        WAIT_STAB_OFF,   // 2s → stab off
        WAIT_ABS_MSG,    // 2s → "Closing content absorber valve..."
        WAIT_ABS_OFF,    // 3s → valve closed + "Success."
        WAIT_SHIELD_MSG, // 3s → "Shutting down reactor shield..." + the ramp begins
        SHIELD_RAMP,     // shield ramps down at 10%/sec (ReactorShield) → onStallShieldDown()
        WAIT_OFFLINE_MSG // shield "Success." → 3s → "Core marked as offline..." → done
    }

    /** Manual shutdown (startup lamp re-trigger) is silently ignored above this shield stress %. */
    private static final double MANUAL_TRIGGER_MAX_STRESS = 10.0;

    private StallPhase stallPhase = StallPhase.NONE;
    private int stallTicks;
    /** Manual trigger (startup lamp pulse while running) vs automatic stall (-273 C*). */
    private boolean stallManual;
    /** HEAT_DUMP cooling rate, C* per tick: 10%/sec of the temp at the shutdown start. */
    private double stallCoolPerTick;
    private double stallCoolRemainder;
    /** Set once the stall shutdown completes — the startup sign shows Offline. */
    private boolean coreOfflineMarked = false;

    public boolean isStallShutdownActive() { return stallPhase != StallPhase.NONE; }
    public StallPhase getStallPhase() { return stallPhase; }

    // =========================
    // STARTUP SEQUENCE: the startup lamp pulse begins the cinematic startup —
    // announcement → 5s → stabilization lasers → power lasers → absorber valve
    // (each with its own 3s pauses) → shield forming at 10%/sec → ignition:
    // the core becomes operational, central particles appear and the
    // laser/absorber control takes effect (it is inert during the sequence).
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
        WAIT_IGNITE,      // 3s → "Igniting reactor core..." (ignite)
        WAIT_COMPLETE     // 3s → "Reactor startup complete, resume normal operations."
    }

    private StartupPhase startupPhase = StartupPhase.NONE;
    private int startupTicks;

    public boolean isStartupSequenceActive() { return startupPhase != StartupPhase.NONE; }
    public StartupPhase getStartupPhase() { return startupPhase; }

    /** Startup sequence phase machine (every tick). */
    private void tickStartupSequence() {
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
                    startupPhase = StartupPhase.WAIT_COMPLETE;
                    startupTicks = 0;
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
    /** Whether the core finished its shutdown and is awaiting a new startup pulse. */
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
    private int coreTempMin;
    private int coreWorkTemp;
    private double pressFollowRate;
    private double spinFollowRate;
    private int energyRate;
    private int meltdownExplosionRadius;

    private void copyConfig() {
        if (cfg == null) return;
        enabled = cfg.isEnabled();
        coreTempMax = cfg.getCoreTempMax();
        coreTempMin = cfg.getCoreTempMin();
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

    private int coreTemp;           // C*, [TEMP_MIN .. TEMP_MAX]
    private double shieldPress;     // Shield pressure, MPa — follows (T/10M) × 10.01
    private double spin;            // Core spin, RPS — follows 0.95 × (T/10M)

    // =========================
    // ENERGY GENERATION (output to cable network)
    // =========================
    private long energyGenerated;
    private double energyRemainder;

    // Previous integrity values for threshold warnings
    private int prevShInt = 100;
    private int prevCaseInt = 100;

    // Advancement tracking (one-time grants)
    private boolean advStartDfcGranted = false;
    private boolean advDfcUnstableGranted = false;
    private boolean advExplodeDfcGranted = false;
    private final Set<UUID> advInsideDfcGranted = ConcurrentHashMap.newKeySet();
    private final Set<UUID> advBurnInsideDfcGranted = ConcurrentHashMap.newKeySet();
    private final Set<UUID> advOneTimeHeaterGranted = ConcurrentHashMap.newKeySet();

    private static final Map<UUID, Map<String, PendingAssembly>> pendingAssemblies = new HashMap<>();

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

    private void grantAdvancementAll(String key) {
        Player[] online = Bukkit.getOnlinePlayers().toArray(new Player[0]);
        for (Player player : online) {
            if (reactorLocation != null
                    && player.getWorld().equals(reactorLocation.getWorld())
                    && player.getLocation().distanceSquared(reactorLocation) <= 225) {
                grantAdvancement(player, key);
            }
        }
    }

    /** Grants to every online player within {@code radius} blocks of the reactor center. */
    private void grantAdvancementNear(String key, double radius) {
        if (reactorLocation == null) return;
        double radiusSq = radius * radius;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(reactorLocation.getWorld())
                    && player.getLocation().distanceSquared(reactorLocation) <= radiusSq) {
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
        s.setShieldPress(r.shieldPress);
        s.setSpin(r.spin);
        s.setFusionParticles(r.fusion.getParticles());
        s.setFusionCollected(r.fusion.getCollected());
        s.setCaseBroken(r.caseSys.isBroken());
        s.setCaseTemp(r.caseSys.getTemp());
        s.setCasePress(r.caseSys.getPress());
        s.setCaseIntegrity(r.caseSys.getIntegrity());
        s.setEnergyGenerated(r.energyGenerated);
        s.setLaserStarted(r.lasers.isStarted());
        s.setStructureDamaged(r.structureDamaged);
        s.setShieldState(r.shield.getState().name());
        s.setShieldIntegrity(r.shield.getIntegrity());
        s.setShieldFailCountdown(r.shield.getFailCountdown());
        s.setSelfdestructPhase(r.selfdestructPhase.name());
        s.setSelfdestructTicks(r.selfdestructTicks);
        s.setSelfdestructDone(r.selfdestructDone);
        s.setCoreEmergencyStopped(r.coreEmergencyStopped);
        s.setStallPhase(r.stallPhase.name());
        s.setStallTicks(r.stallTicks);
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
        }
    }

    /** Applies a DB-loaded state to this reactor instance (used by loadAll). */
    private void applyLoadedState(ReactorState state) {
        reactorLocation = state.getReactorLocation();
        valid = state.isValid();
        reactorId = state.getReactorId();
        coreTemp = state.getCoreTemp();
        shieldPress = state.getShieldPress();
        spin = state.getSpin();
        fusion.setParticles(state.getFusionParticles());
        fusion.setCollected(state.getFusionCollected());
        caseSys.setState(state.isCaseBroken() ? ReactorCase.State.BROKEN : ReactorCase.State.OK);
        caseSys.setTemp(state.getCaseTemp());
        caseSys.setPress(state.getCasePress());
        caseSys.setIntegrity(state.getCaseIntegrity());
        if (caseSys.isBroken()) {
            caseSys.repair(reactorLocation);
            caseSys.setState(ReactorCase.State.BROKEN);
        }
        energyGenerated = state.getEnergyGenerated();
        lasers.setStarted(state.isLaserStarted());
        structureDamaged = state.isStructureDamaged();
        coreEmergencyStopped = state.isCoreEmergencyStopped();
        selfdestructDone = state.isSelfdestructDone();
        selfdestructPhase = parseSelfdestructPhase(state.getSelfdestructPhase());
        selfdestructTicks = state.getSelfdestructTicks();
        stallPhase = parseStallPhase(state.getStallPhase());
        stallTicks = state.getStallTicks();
        coreOfflineMarked = state.isCoreOffline();
        // HEAT_DUMP exists only in manual shutdowns; the dump rate is re-derived
        // from the current temperature (the persisted phase survives the restart)
        stallManual = stallPhase == StallPhase.HEAT_DUMP;
        stallCoolPerTick = stallManual ? Math.max(1.0, coreTemp * 0.10 / 20.0) : 0;
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
            }
            this.reactorLocation = null;
            this.valid = false;
            this.reactorId = null;
            ReactorDamageTracker.resetAudit(oldLoc);
            resetAll();
        }
    }

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
        // =========================
        if (!coreEmergencyStopped
                && !structureDamaged
                && !isSelfdestructActive()
                && shield.getState() == ReactorShield.State.WORKING
                && shield.getIntegrity() > 0
                && shield.getIntegrity() < cfg.getShieldIntegrityShutdownPercent()
                && !shield.isFailed()) {
            coreEmergencyStopped = true;
            lasers.reset();
            shield.setState(ReactorShield.State.OFFLINE);
            broadcast(StructuresMessages.get("core_emergency_shutdown",
                    "<dark_red>⚠ <red>Shield integrity critical — emergency core shutdown! Restart the reactor."));
            saveToDb();
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

        // 🏆 Advancement: start_dfc — reactor startup (laser startup pulse)
        if (lasers.isStarted() && !advStartDfcGranted) {
            advStartDfcGranted = true;
            Bukkit.getScheduler().runTask(Main.getInstance(), () ->
                grantAdvancementAll("datapack/start_dfc"));
        }

        // 🏆 Advancement: dfc_self_destruct — the protocol just armed
        if (selfdestructJustRolled) {
            selfdestructJustRolled = false;
            Bukkit.getScheduler().runTask(Main.getInstance(), () ->
                grantAdvancementNear("datapack/dfc_self_destruct",
                        cfg.getSelfdestructAdvRadius()));
        }

        // 🏆 Advancement: power_of_fusion — the fusion completed one ancient debris
        if (fusionDebrisJustCrafted) {
            fusionDebrisJustCrafted = false;
            Bukkit.getScheduler().runTask(Main.getInstance(), () ->
                grantAdvancementNear("datapack/power_of_fusion",
                        cfg.getSelfdestructAdvRadius()));
        }

        // =========================
        // INTEGRITY WARNING (every 10 seconds)
        // =========================
        int warnTick = display.getIntegrityWarnTick() + 1;
        display.setIntegrityWarnTick(warnTick);
        if (warnTick >= 200) {
            display.setIntegrityWarnTick(0);
            // Warn only while the shield is actually operating — an OFFLINE
            // shield (no startup yet, integrity 0) is its normal state and
            // must not spam "Shield integrity compromised!"
            var shieldState = shield.getState();
            // Warn only while the shield is actually operating (WORKING) —
            // CREATING (forming) and SHUTDOWN (planned shutdown) legitimately
            // run below 100% and must not raise false alarms.
            boolean shieldActive = shieldState == ReactorShield.State.WORKING;
            if (shieldActive && shield.getIntegrity() < 100) broadcast("<dark_red>⚠ <red>Shield integrity compromised!");
            if (caseSys.isBroken()) broadcast("<dark_red>⚠ <red>Case glass is broken!");
        }

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
        // a damaged structure: the core coasts on its own decay)
        // =========================
        if (coreTemp > coreTempMin) {
            coreTemp = Math.max(coreTempMin, coreTemp - 1);
        }

        // =========================
        // STALL — reaction lost its heat: threshold warnings (1M / 10k / 0 C*)
        // and the automatic shutdown once the core reaches −273 C*
        // =========================
        checkStallWarnings(prevTemp);
        tryTriggerStallShutdown(prevTemp);
        tickStallShutdown();

        // =========================
        // STARTUP SEQUENCE — cinematic startup driven by the startup lamp
        // =========================
        tickStartupSequence();

        // =========================
        // SHIELD INTEGRITY THRESHOLD WARNINGS (75%, 50%, 25%) — via the
        // stress model; the case integrity lives in ReactorCase.
        // =========================
        checkIntegrityThreshold(prevShInt, (int) Math.round(shield.getIntegrity()), "Shield");
        prevShInt = (int) Math.round(shield.getIntegrity());

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

                // Optimized: iterate only nodes in the same world, not ALL worlds
                java.util.Collection<CableNode> worldNodes = CableNetwork.getWorldNodes(base.getWorld().getUID().toString());
                java.util.List<CableNode> nearbyCables = new java.util.ArrayList<>();
                for (CableNode node : worldNodes) {
                    int dx = Math.abs(node.getBlockX() - base.getBlockX());
                    int dy = Math.abs(node.getBlockY() - base.getBlockY());
                    int dz = Math.abs(node.getBlockZ() - base.getBlockZ());
                    if (dx <= 5 && dy <= 9 && dz <= 4) {
                        nearbyCables.add(node);
                    }
                }

                if (!nearbyCables.isEmpty()) {
                    int remaining = toGenerate;
                    int perNode = toGenerate / nearbyCables.size();
                    for (CableNode node : nearbyCables) {
                        int space = node.getMaxEnergy() - node.getEnergy();
                        int give = Math.min(perNode, space);
                        if (give <= 0) continue;
                        node.addEnergy(give);
                        remaining -= give;
                        CableNetwork.saveNode(node);
                    }
                    if (remaining > 0) {
                        Location coreLoc = base.clone().add(0, -1, 0);
                        CableNode genNode = CableNetwork.getNode(coreLoc);
                        if (genNode == null) {
                            CableNetwork.addNode(coreLoc);
                            genNode = CableNetwork.getNode(coreLoc);
                        }
                        if (genNode != null) {
                            genNode.setType(NodeType.GENERATOR);
                            genNode.setMaxEnergy(coreWorkTemp * 10);
                            genNode.addEnergy(remaining);
                            CableNetwork.saveNode(genNode);
                        }
                    }
                } else {
                    Location coreLoc = base.clone().add(0, -1, 0);
                    CableNode genNode = CableNetwork.getNode(coreLoc);
                    if (genNode == null) {
                        CableNetwork.addNode(coreLoc);
                        genNode = CableNetwork.getNode(coreLoc);
                    }
                    if (genNode != null) {
                        genNode.setType(NodeType.GENERATOR);
                        genNode.setMaxEnergy(coreWorkTemp * 10);
                        genNode.addEnergy(toGenerate);
                        CableNetwork.saveNode(genNode);
                    }
                }
            }
        }
    }

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
    }    // =========================
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
    // INTEGRITY DECAY — dfc_unstable achievement
    // =========================
    private void checkDfcUnstable() {
        if (!advDfcUnstableGranted && (shield.getIntegrity() < 100 || caseSys.getIntegrity() < 100)) {
            advDfcUnstableGranted = true;
            Bukkit.getScheduler().runTask(Main.getInstance(), () ->
                grantAdvancementAll("datapack/dfc_unstable"));
        }
    }

    // =========================
    // FULL RESET (disassemble / teardown)
    // =========================
    private void resetReactorState() {
        coreTemp = 0;
        shieldPress = 0;
        spin = 0;
        lasers.reset();
        shield.reset();
        fusion.reset();
        prevShInt = 100;
        prevCaseInt = 100;
        energyGenerated = 0;
        energyRemainder = 0;
        structureDamaged = false;
        damageWarnTick = 0;
        selfdestructPhase = SelfdestructPhase.NONE;
        selfdestructTicks = 0;
        selfdestructDone = false;
        stallPhase = StallPhase.NONE;
        stallTicks = 0;
        stallManual = false;
        stallCoolPerTick = 0;
        stallCoolRemainder = 0;
        coreOfflineMarked = false;
        startupPhase = StartupPhase.NONE;
        startupTicks = 0;

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
            grantAdvancementAll("datapack/explode_dfc");
        }

        // 🏆 Advancement: one_time_heater — player inside the reactor during the explosion
        if (reactorLocation != null) {
            int bx = reactorLocation.getBlockX(), by = reactorLocation.getBlockY(), bz = reactorLocation.getBlockZ();
            Player[] online = Bukkit.getOnlinePlayers().toArray(new Player[0]);
            for (Player player : online) {
                if (!player.getWorld().equals(base.getWorld())) continue;
                if (advOneTimeHeaterGranted.contains(player.getUniqueId())) continue;
                Location ploc = player.getLocation();
                int px = ploc.getBlockX(), py = ploc.getBlockY(), pz = ploc.getBlockZ();
                if (px >= bx - 2 && px <= bx + 2
                        && py >= by - 5 && py <= by
                        && pz >= bz - 2 && pz <= bz + 2) {
                    advOneTimeHeaterGranted.add(player.getUniqueId());
                    grantAdvancement(player, "datapack/one_time_heater");
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
        stallPhase = StallPhase.NONE;
        stallTicks = 0;
        stallManual = false;
        stallCoolPerTick = 0;
        stallCoolRemainder = 0;
        coreOfflineMarked = false;
        startupPhase = StartupPhase.NONE;
        startupTicks = 0;
        energyGenerated = 0;
        energyRemainder = 0;
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
        coreTemp = Math.max(TEMP_MIN, Math.min(TEMP_MAX, coreTemp + delta));
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

    /** Called by the laser startup pulse — begins the cinematic startup sequence. */
    public void onStartupPulse() {
        if (coreEmergencyStopped) {
            coreEmergencyStopped = false;
            broadcast(StructuresMessages.get("core_restart_after_shutdown",
                    "<green>✔ <white>Core restarted after the emergency shutdown."));
        }
        // New run: the offline mark goes away and the ±5% control lamps work again
        coreOfflineMarked = false;
        lasers.clearControlDisable();
        rollSelfdestruct();
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
    // =========================
    private void broadcast(String message) {
        String prefix = MessageUtil.PREFIX + "<dark_gray>[<yellow>DFC<dark_gray>] ";
        Player[] online = Bukkit.getOnlinePlayers().toArray(new Player[0]);
        for (Player player : online) {
            if (reactorLocation != null
                    && player.getWorld().equals(reactorLocation.getWorld())
                    && player.getLocation().distanceSquared(reactorLocation) <= 225) {
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

        // Case auto-repair: player restored glass into a broken case
        if (cat == ReactorDamageTracker.Category.GLASS && caseSys.isBroken()) {
            caseSys.checkAutoRepair(reactorLocation);
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
