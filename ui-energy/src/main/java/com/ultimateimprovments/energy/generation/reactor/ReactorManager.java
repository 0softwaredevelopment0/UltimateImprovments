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
    // Phase 2 — 60s timed countdown, the core runs normally but the control
    //           bulbs are locked; the signs show the protocol screen.
    // Phase 3 — overpower finale: Power Lasers ramp to 1000%, burning the
    //           shield to 0% (report stage) — the sequence then completes.
    // =========================
    public enum SelfdestructPhase { NONE, SENSORS_DOWN, TIMED, FINALE }

    private SelfdestructPhase selfdestructPhase = SelfdestructPhase.NONE;
    private int selfdestructTicks;        // ticks in the current phase
    private boolean selfdestructDone;     // completed — not rolled again
    private int selfdestructWarnTicks;    // legacy debounce (unused after the single T-10s warning)
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

    /** T-10s warning is sent once per second for the last 10 seconds of the timed phase. */
    private static final int SELFDESTRUCT_WARN_WINDOW = 10;

    private void tickSelfdestruct() {
        ReactorConfig cfg = ReactorConfig.getInstance();
        switch (selfdestructPhase) {
            case SENSORS_DOWN -> {
                selfdestructTicks++;
                if (selfdestructTicks >= cfg.getSelfdestructNoSignalSec() * 20) {
                    beginTimedSelfdestruct();
                }
            }
            case TIMED -> {
                selfdestructTicks++;
                int total = cfg.getSelfdestructTimedSec() * 20;
                int leftTicks = total - selfdestructTicks;
                // ONE warning at exactly 10 seconds left — no spam
                if (leftTicks == SELFDESTRUCT_WARN_WINDOW * 20) {
                    broadcast(StructuresMessages.get("selfdestruct_final_warn",
                            "<dark_red>Danger! <white>Core shield has been compromised, core detonation estimated in T-10s, good luck."));
                }
                if (selfdestructTicks >= total) {
                    // The timed phase runs out — the overpower finale begins
                    selfdestructPhase = SelfdestructPhase.FINALE;
                    selfdestructTicks = 0;
                    lasers.beginOverpower();
                    broadcast(StructuresMessages.get("selfdestruct_finale",
                            "<dark_red>☠ <red>Self-destruct finale: the Power Lasers are running at 1000%!"));
                }
            }
            case FINALE -> {
                // Report stage reached (shield burned to 0% → detonation
                // countdown): the self-destruct sequence is complete.
                if (shield.isFailed()) {
                    onSelfdestructReportStage();
                }
            }
            case NONE -> { /* not armed */ }
        }
    }

    /** Phase 1 → Phase 2 transition: the protocol screen replaces No signal. */
    private void beginTimedSelfdestruct() {
        selfdestructPhase = SelfdestructPhase.TIMED;
        selfdestructTicks = 0;
        selfdestructWarnTicks = -1;
        display.resetSignCache();
        lasers.setControlLocked(true);
        broadcast(StructuresMessages.get("selfdestruct_protocol",
                "<dark_red>☠ <red>Self-destruct protocol engaged! Detonation in T-1:00."));
    }

    /**
     * Report stage reached (shield 0% → detonation countdown): the self-destruct
     * sequence is complete and disarms itself — the shield detonation proceeds
     * on its own countdown.
     */
    public void onSelfdestructReportStage() {
        if (selfdestructPhase == SelfdestructPhase.NONE || selfdestructDone) return;
        selfdestructPhase = SelfdestructPhase.NONE;
        selfdestructDone = true;
        broadcast(StructuresMessages.get("selfdestruct_complete",
                "<dark_red>☠ <red>Self-destruct sequence complete — the core is beyond saving."));
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

    /** Emergency core shutdown latch (shield integrity fell below the critical threshold). */
    private boolean coreEmergencyStopped = false;
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

        // Self-destruct overpower finale: re-arm the forced 1000% ramp
        if (selfdestructPhase == SelfdestructPhase.FINALE) {
            lasers.beginOverpower();
        } else if (selfdestructPhase == SelfdestructPhase.TIMED) {
            lasers.setControlLocked(true);
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
            // Marker entity for reactor identification
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
            if (oldId != null) {
                deleteFromDb(oldId);
            }
            this.reactorLocation = null;
            this.valid = false;
            this.reactorId = null;
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

        // West tower bulb = heater, east tower bulb = cooler (DFC 10×11×9 geometry)
        // =========================
        // LASERS — roof controls, per-tick ramp + smooth heating/cooling
        // Damaged structure: the core can no longer be controlled — no heat,
        // no power ramp; ONLY the Stabilization Laser keeps working so the
        // reactor can be shut down by cooling it to 0 C* (cooldown mode).
        // =========================
        if (!structureDamaged) {
            lasers.tick(base);
            shield.tick(base);
        } else if (coreTemp > coreTempMin) {
            lasers.tickCooldownMode(base);
        }

        // =========================
        // EMERGENCY CORE SHUTDOWN — shield integrity below the critical
        // threshold (25% by default): the core shuts itself off, lasers reset.
        // =========================
        if (!coreEmergencyStopped
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
            boolean shieldActive = shieldState == ReactorShield.State.CREATING
                    || shieldState == ReactorShield.State.WORKING;
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
        if (structureDamaged) return; // sensors are dead — pressure readouts frozen

        Location base = reactorLocation;
        // Above the core chamber, matches the core visuals
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
            Location smokePos = coreCenter.clone().add(0, 2.5, 0);
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
        if (structureDamaged) return; // sensors dead: no particle tracking, no case readouts
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
        if (structureDamaged) return; // sign panels are broken — nothing to smooth
        display.tickSmoothDisplay();
    }

    // =========================
    // VISUAL TICK (every tick - particles)
    // =========================
    public void tickVisual() {
        if (structureDamaged) return; // sign panels are broken — no sensor-driven visuals
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
        selfdestructWarnTicks = -1;

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
        if (!structureDamaged || coreTemp > coreTempMin) return;

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
        selfdestructWarnTicks = -1;
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
    // (self-destruct phase 1, damaged structure, shield detonation countdown).
    // =========================
    public boolean isSensorsDead() {
        return structureDamaged
                || selfdestructPhase == SelfdestructPhase.SENSORS_DOWN                || shield.isFailed();
    }

    /** Fuel tick (every second): consumption by spin + spin decay when dry. */
    public void tickFuel() {
        if (structureDamaged) return; // spin is unmanaged while the structure is damaged
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

    /** Called by the laser startup pulse — ignites the shield formation. */
    public void onStartupPulse() {
        if (coreEmergencyStopped) {
            coreEmergencyStopped = false;
            broadcast(StructuresMessages.get("core_restart_after_shutdown",
                    "<green>✔ <white>Core restarted after the emergency shutdown."));
        }
        shield.start();
        rollSelfdestruct();
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
     * category was broken inside the structure.
     */
    public void addDamage(ReactorDamageTracker.Category cat) {
        if (reactorLocation == null) return;

        ReactorDamageTracker.Snapshot snap = ReactorDamageTracker.scan(reactorLocation);
        if (snap == null) return;

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
        int[] c = ReactorDamageTracker.count(reactorLocation, cat);
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
    public void addRepair(ReactorDamageTracker.Category cat) {
        if (reactorLocation == null) return;

        ReactorDamageTracker.Snapshot snap = ReactorDamageTracker.scan(reactorLocation);
        if (snap == null) return;

        // Case auto-repair: player restored glass into a broken case
        if (cat == ReactorDamageTracker.Category.GLASS && caseSys.isBroken()) {
            caseSys.checkAutoRepair(reactorLocation);
        }

        // Remaining/total of the AFFECTED category (glass → glass cells, etc.)
        int[] c = ReactorDamageTracker.count(reactorLocation, cat);
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

        if (snap.allPresent() && structureDamaged) {
            structureDamaged = false;
            damageWarnTick = 0;
            broadcast(StructuresMessages.get("structure_repaired",
                    "<green>✔ <white>Reactor structure fully restored — control returned."));
        } else if (cat == ReactorDamageTracker.Category.STRUCTURE
                && snap.structPresent() >= snap.structTotal() && structureDamaged) {
            // All "control" cells are back — control returns even if some
            // glass/signs are still missing (those are cosmetic/physical only).
            structureDamaged = false;
            damageWarnTick = 0;
            broadcast(StructuresMessages.get("structure_repaired",
                    "<green>✔ <white>Reactor structure fully restored — control returned."));
        }
        saveToDb();
    }
}
