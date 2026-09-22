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

    private final ReactorDisplay display;
    private final ReactorLasers lasers;
    private final ReactorShield shield;
    private final ReactorFuel fuel;
    private final ReactorFusion fusion;

    public static ReactorManager getInstance() {
        return instance;
    }

    public static void init() {
        if (instance != null) return; // prevent double-init
        instance = new ReactorManager();
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
        }
    }

    // =========================
    // CONFIG (delegated to ReactorConfig)
    // =========================
    private ReactorConfig cfg;
    private boolean enabled;
    private int tempDecayDivisor;
    private int heatRate;
    private int coolRate;
    private int coreTempMax;
    private int coreTempMin;
    private int coreTempCoolMin;
    private int coreWorkTemp;
    private double pressFollowRate;
    private double spinFollowRate;
    private int energyRate;
    private int caseTempHeatRate;
    private int caseTempMax;
    private int caseTempCoolRate;
    private int caseTempCoolMin;
    private int caseTempDecayRate;
    private int casePressHeatRate;
    private int casePressMax;
    private int casePressDecayRate;
    private int shIntDecayTempThreshold;
    private int shellIntDecayRate;
    private int shellIntRecoveryTempMax;
    private int shellIntRecoveryRate;
    private int caseIntDecayPressThreshold;
    private int caseIntDecayTempThreshold;
    private int caseIntDecayPressRate;
    private int caseIntDecayTempRate;
    private int caseIntRecoveryPressMax;
    private int caseIntRecoveryTempMax;
    private int caseIntRecoveryRate;
    private boolean wearEnabled;
    private int wearIntervalNormal;
    private int wearIntervalDegradation;
    private int wearChatCountdown;
    private int wearFinalMeltdownAt;
    private int wearFinalMeltdownDuration;
    private int meltdownExplosionRadius;

    private void copyConfig() {
        if (cfg == null) return;
        enabled = cfg.isEnabled();
        tempDecayDivisor = cfg.getTempDecayDivisor();
        heatRate = cfg.getHeatRate();
        coolRate = cfg.getCoolRate();
        coreTempMax = cfg.getCoreTempMax();
        coreTempMin = cfg.getCoreTempMin();
        coreTempCoolMin = cfg.getCoreTempCoolMin();
        coreWorkTemp = cfg.getCoreWorkTemp();
        pressFollowRate = cfg.getPressFollowRate();
        spinFollowRate = cfg.getSpinFollowRate();
        energyRate = cfg.getEnergyRate();
        caseTempHeatRate = cfg.getCaseTempHeatRate();
        caseTempMax = cfg.getCaseTempMax();
        caseTempCoolRate = cfg.getCaseTempCoolRate();
        caseTempCoolMin = cfg.getCaseTempCoolMin();
        caseTempDecayRate = cfg.getCaseTempDecayRate();
        casePressHeatRate = cfg.getCasePressHeatRate();
        casePressMax = cfg.getCasePressMax();
        casePressDecayRate = cfg.getCasePressDecayRate();
        shIntDecayTempThreshold = cfg.getShIntDecayTempThreshold();
        shellIntDecayRate = cfg.getShellIntDecayRate();
        shellIntRecoveryTempMax = cfg.getShellIntRecoveryTempMax();
        shellIntRecoveryRate = cfg.getShellIntRecoveryRate();
        caseIntDecayPressThreshold = cfg.getCaseIntDecayPressThreshold();
        caseIntDecayTempThreshold = cfg.getCaseIntDecayTempThreshold();
        caseIntDecayPressRate = cfg.getCaseIntDecayPressRate();
        caseIntDecayTempRate = cfg.getCaseIntDecayTempRate();
        caseIntRecoveryPressMax = cfg.getCaseIntRecoveryPressMax();
        caseIntRecoveryTempMax = cfg.getCaseIntRecoveryTempMax();
        caseIntRecoveryRate = cfg.getCaseIntRecoveryRate();
        wearEnabled = cfg.isWearEnabled();
        wearIntervalNormal = cfg.getWearIntervalNormal();
        wearIntervalDegradation = cfg.getWearIntervalDegradation();
        wearChatCountdown = cfg.getWearChatCountdown();
        wearFinalMeltdownAt = cfg.getWearFinalMeltdownAt();
        wearFinalMeltdownDuration = cfg.getWearFinalMeltdownDuration();
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
    private int coreShInt = 100;    // Shell integrity (0-100%)
    private int coreCaseTemp;
    private int coreCasePress;
    private int coreCaseInt = 100;  // Case integrity (0-100%)

    // Recipe


    // Self-destruct
    private boolean selfDestruct;
    private int sdText;

    // =========================
    // ENERGY GENERATION (output to cable network)
    // =========================
    private long energyGenerated;
    private double energyRemainder;

    // =========================
    // WEAR SYSTEM
    // =========================
    private int reactorWear;
    private int wearTickCounter;
    private boolean prevWearDegraded;
    private boolean selfDestructActive;
    private int selfDestructChatTimer;
    private boolean finalMeltdownActive;

    // Meltdown countdown (10s before explosion)
    private boolean meltdownCountdown;
    private int meltdownTimer;

    // Previous integrity values for threshold detection
    private int prevShInt = 100;
    private int prevCaseInt = 100;

    // Tick counters
    private int pressTick;
    private int recipeTick;
    private int intensityDownTick;
    private int intensityUpTick;
    private int soundTick;
    private int noFuelWarnTick;

    // =========================
    // PENDING ASSEMBLY
    // =========================
    // Advancement tracking (one-time grants)
    private boolean advStartDfcGranted = false;
    private boolean advDfcUnstableGranted = false;
    private boolean advDfcSelfDestructGranted = false;
    private boolean advExplodeDfcGranted = false;
    private boolean advCompletedRecipeGranted = false;
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

    // =========================
    // CONSTRUCTOR
    // =========================
    private ReactorManager() {
        this.display = new ReactorDisplay(this);
        this.lasers = new ReactorLasers(this);
        this.shield = new ReactorShield(this);
        this.fuel = new ReactorFuel(this);
        this.fusion = new ReactorFusion(this);
    }

    // =========================
    // DATABASE PERSISTENCE
    // =========================
    public static void saveAll() {
        ReactorManager r = instance;
        if (r == null) return;
        ReactorState state = buildState(r);
        ReactorPersistence.saveAll(state);
    }

    public void saveToDb() {
        ReactorState state = buildState(this);
        ReactorPersistence.saveToDb(state);
    }

    private static ReactorState buildState(ReactorManager r) {
        ReactorState s = new ReactorState();
        s.setReactorLocation(r.reactorLocation);
        s.setCoreTemp(r.coreTemp);
        s.setShieldPress(r.shieldPress);
        s.setSpin(r.spin);
        s.setFusionParticles(r.fusion.getParticles());
        s.setFusionCollected(r.fusion.getCollected());
        s.setCoreShInt(r.coreShInt);
        s.setCoreCaseTemp(r.coreCaseTemp);
        s.setCoreCasePress(r.coreCasePress);
        s.setCoreCaseInt(r.coreCaseInt);
        s.setSelfDestruct(r.selfDestruct);
        s.setReactorWear(r.reactorWear);
        s.setEnergyGenerated(r.energyGenerated);
        s.setLaserStarted(r.lasers.isStarted());
        s.setLaserPowers(new double[] {
                r.lasers.getPower(ReactorLasers.LASER_P1),
                r.lasers.getPower(ReactorLasers.LASER_P2),
                r.lasers.getPower(ReactorLasers.LASER_STAB),
                r.lasers.getPower(ReactorLasers.LASER_ABSORBER) });
        return s;
    }

    public static void loadAll() {
        if (instance == null) return;
        ReactorState state = new ReactorState();
        if (ReactorPersistence.loadFromDb(state)) {
            instance.reactorLocation = state.getReactorLocation();
            instance.valid = state.isValid();
            instance.reactorId = state.getReactorId();
            instance.coreTemp = state.getCoreTemp();
            instance.shieldPress = state.getShieldPress();
            instance.spin = state.getSpin();
            instance.fusion.setParticles(state.getFusionParticles());
            instance.fusion.setCollected(state.getFusionCollected());
            instance.coreShInt = state.getCoreShInt();
            instance.coreCaseTemp = state.getCoreCaseTemp();
            instance.coreCasePress = state.getCoreCasePress();
            instance.coreCaseInt = state.getCoreCaseInt();
            instance.selfDestruct = state.isSelfDestruct();
            instance.reactorWear = state.getReactorWear();
            instance.energyGenerated = state.getEnergyGenerated();
            instance.lasers.setStarted(state.isLaserStarted());
            // If the reactor was started before the restart, its shield was already
            // formed — otherwise the lasers would stay locked behind the WORKING gate.
            if (state.isLaserStarted()) {
                instance.shield.setState(ReactorShield.State.WORKING);
                instance.shield.setIntegrity(100);
            }
            double[] lp = state.getLaserPowers();
            if (lp != null && lp.length >= 4) {
                instance.lasers.setPower(ReactorLasers.LASER_P1, lp[0]);
                instance.lasers.setPower(ReactorLasers.LASER_P2, lp[1]);
                instance.lasers.setPower(ReactorLasers.LASER_STAB, lp[2]);
                instance.lasers.setPower(ReactorLasers.LASER_ABSORBER, lp[3]);
            }
        }
    }

    public static void deleteFromDb(String reactorId) {
        ReactorPersistence.deleteFromDb(reactorId);
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
            this.reactorId = "REACTOR-" + normalized.getBlockX()
                    + "-" + normalized.getBlockY()
                    + "-" + normalized.getBlockZ();
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
            if (reactorId != null) {
                deleteFromDb(reactorId);
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
        // =========================
        lasers.tick(base);
        shield.tick(base);

        boolean heating = lasers.isHeating();
        boolean cooling = lasers.isCooling();

        // =========================
        // BROADCAST STATE CHANGES
        // =========================
        if (heating != display.wasHeating()) {
            broadcast(heating ? "<gold>🔥 <yellow>Нагрев включён" : "<gray>🔥 <white>Нагрев выключен");
            display.setHeating(heating);
        }
        if (cooling != display.wasCooling()) {
            broadcast(cooling ? "<aqua>❄ <dark_aqua>Охлаждение включено" : "<gray>❄ <white>Охлаждение выключено");
            display.setCooling(cooling);
        }

        // 🏆 Advancement: start_dfc — reactor startup (laser startup pulse)
        if (lasers.isStarted() && !advStartDfcGranted) {
            advStartDfcGranted = true;
            Bukkit.getScheduler().runTask(Main.getInstance(), () ->
                grantAdvancementAll("datapack/start_dfc"));
        }

        // =========================
        // CASE REACTION — follows laser heating/cooling
        // =========================
        if (heating) {
            coreCaseTemp = Math.min(coreCaseTemp + caseTempHeatRate, caseTempMax);
            coreCasePress = Math.min(coreCasePress + casePressHeatRate, casePressMax);
        }
        if (cooling) {
            coreCaseTemp = Math.max(coreCaseTemp - caseTempCoolRate, caseTempCoolMin);
        }

        // =========================
        // INTEGRITY WARNING (every 10 seconds)
        // =========================
        int warnTick = display.getIntegrityWarnTick() + 1;
        display.setIntegrityWarnTick(warnTick);
        if (warnTick >= 200) {
            display.setIntegrityWarnTick(0);
            if (coreShInt < 100) broadcast("<dark_red>⚠ <red>Целостность оболочки ядра нарушена!");
            if (coreCaseInt < 100) broadcast("<dark_red>⚠ <red>Целостность корпуса реактора нарушена!");
        }

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
        // NATURAL TEMP DECAY — passive cooling 1 C*/tick (proportional cap
        // max(1, T/divisor) only applies above the working temperature)
        // =========================
        if (coreTemp > coreTempMin) {
            int decay = coreTemp > coreWorkTemp
                    ? Math.max(1, coreTemp / tempDecayDivisor)
                    : 1;
            coreTemp = Math.max(coreTempMin, coreTemp - decay);
        }

        // =========================
        // CASE PRESSURE DECAY
        // =========================
        if (coreCasePress > 0) {
            coreCasePress -= casePressDecayRate;
            if (coreCasePress < 0) coreCasePress = 0;
        }

        // =========================
        // CASE TEMP DECAY
        // =========================
        if (coreCaseTemp > caseTempCoolMin) {
            coreCaseTemp -= caseTempDecayRate;
        }

        // =========================
        // INTEGRITY THRESHOLD WARNINGS (75%, 50%, 25%)
        // =========================
        checkIntegrityThreshold(prevShInt, coreShInt, "оболочки ядра");
        checkIntegrityThreshold(prevCaseInt, coreCaseInt, "корпуса");
        prevShInt = coreShInt;
        prevCaseInt = coreCaseInt;

        // =========================
        // ENERGY GENERATION
        // =========================
        if (coreTemp > coreWorkTemp / 100) {
            double energyPerTick = ((double) coreTemp / coreWorkTemp) * energyRate;
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

        // =========================
        // MELTDOWN COUNTDOWN START
        // =========================
        if ((coreShInt <= 0 || coreCaseInt <= 0) && !meltdownCountdown && !selfDestructActive) {
            meltdownCountdown = true;
            meltdownTimer = 200; // 10 seconds
            selfDestruct = true;
            broadcast("<dark_red>☠ <red>Целостность разрушена! <white>10<red> секунд до детонации...");
        }
    }

    // =========================
    // PRESSURE TICK (every 5s)
    // =========================
    public void tickPressure() {
        if (!enabled || !valid || reactorLocation == null) return;

        Location base = reactorLocation;
        Location coreCenter = base.clone().add(0.5, -5.5, 0.5);

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
    }

    // =========================
    // INTENSITY DECAY TICK (every 1s)
    // =========================
    public void tickIntensityDown() {
        if (!enabled || !valid) return;

        if (coreTemp >= shIntDecayTempThreshold && coreShInt > 0) {
            coreShInt = Math.max(0, coreShInt - shellIntDecayRate);
        }
        if (coreCasePress >= caseIntDecayPressThreshold && coreCaseInt > 0) {
            coreCaseInt = Math.max(0, coreCaseInt - caseIntDecayPressRate);
        }
        if (coreCaseTemp >= caseIntDecayTempThreshold && coreCaseInt > 0) {
            coreCaseInt = Math.max(0, coreCaseInt - caseIntDecayTempRate);
        }

        // 🏆 Advancement: dfc_unstable — first integrity degradation
        Bukkit.getScheduler().runTask(Main.getInstance(), this::checkDfcUnstable);
    }

    // =========================
    // INTENSITY RECOVERY TICK (every 3s)
    // =========================
    public void tickIntensityUp() {
        if (!enabled || !valid) return;

        if (coreTemp <= shellIntRecoveryTempMax && coreShInt < 100) {
            coreShInt = Math.min(100, coreShInt + shellIntRecoveryRate);
        }
        if (coreCasePress <= caseIntRecoveryPressMax && coreCaseTemp <= caseIntRecoveryTempMax && coreCaseInt < 100) {
            coreCaseInt = Math.min(100, coreCaseInt + caseIntRecoveryRate);
        }
    }

    // =========================
    // FUSION TICK (every tick) — fusion particles, absorber collection
    // =========================
    public void tickFusion() {
        if (!enabled || !valid || reactorLocation == null) return;
        fusion.tick(reactorLocation);
    }

    // =========================
    // WEAR TICK (every second)
    // =========================
    public void tickWear() {
        if (!enabled || !valid || reactorLocation == null) return;

        if (wearEnabled && !selfDestructActive) {
            boolean isDegraded = coreShInt < 100 || coreCaseInt < 100;
            if (isDegraded != prevWearDegraded) {
                wearTickCounter = 0;
                prevWearDegraded = isDegraded;
            }
            wearTickCounter++;

            if (isDegraded) {
                if (wearTickCounter >= wearIntervalDegradation) {
                    wearTickCounter = 0;
                    if (reactorWear > 0) reactorWear--;
                }
            } else {
                if (wearTickCounter >= wearIntervalNormal) {
                    wearTickCounter = 0;
                    if (reactorWear < 100) {
                        reactorWear++;
                        if (reactorWear >= 100 && !selfDestructActive) {
                            startSelfDestruct();
                        }
                    }
                }
            }
        }

        if (selfDestructActive && !meltdownCountdown) {
            selfDestructChatTimer--;
            if (selfDestructChatTimer <= wearFinalMeltdownAt) {
                finalMeltdownActive = true;
                meltdownCountdown = true;
                meltdownTimer = wearFinalMeltdownDuration * 20;
                broadcast("<dark_red>☠ <red>Взрыв неизбежен! <white>" + wearFinalMeltdownDuration + "<red> сек до детонации...");
            } else if (selfDestructChatTimer > 0) {
                broadcast("<dark_red>☠ <red>Детонация через <white>" + selfDestructChatTimer + "<red> сек...");
            }
        }
    }

    // =========================
    // MELTDOWN COUNTDOWN TICK (final 10s)
    // =========================
    public void tickMeltdownCountdown() {
        if (!meltdownCountdown || !enabled || !valid || reactorLocation == null) return;

        meltdownTimer--;
        if (meltdownTimer > 0 && meltdownTimer % 20 == 0) {
            broadcast("<dark_red>☠ <red>Взрыв неизбежен! <white>" + (meltdownTimer / 20) + "<red> сек...");
        }
        if (meltdownTimer <= 0) {
            meltdownCountdown = false;
            finalMeltdownActive = false;
            selfDestructActive = false;
            meltdown();
        }
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
    // START SELF-DESTRUCT
    // =========================
    private void startSelfDestruct() {
        selfDestruct = true;
        selfDestructActive = true;
        selfDestructChatTimer = wearChatCountdown;
        finalMeltdownActive = false;
        broadcast("<dark_red>☠ <red>Критический износ реактора! <white>" + wearChatCountdown + "<red> сек до детонации...");
        broadcast("<dark_red>☠ <red>Протокол самоуничтожения инициирован.");

        // 🏆 Advancement: dfc_self_destruct — self-destruction
        if (!advDfcSelfDestructGranted) {
            advDfcSelfDestructGranted = true;
            grantAdvancementAll("datapack/dfc_self_destruct");
        }
    }

    // =========================
    // INTEGRITY THRESHOLD CHECK
    // =========================
    private void checkIntegrityThreshold(int prevVal, int currVal, String name) {
        if (currVal < prevVal) {
            if (currVal == 75 || currVal == 50 || currVal == 25) {
                broadcast("<dark_red>⚠ <red>Целостность " + name + ": <white>" + currVal + "%");
            }
        }
    }

    // =========================
    // INTEGRITY DECAY — dfc_unstable achievement
    // =========================
    private void checkDfcUnstable() {
        if (!advDfcUnstableGranted && (coreShInt < 100 || coreCaseInt < 100)) {
            advDfcUnstableGranted = true;
            Bukkit.getScheduler().runTask(Main.getInstance(), () ->
                grantAdvancementAll("datapack/dfc_unstable"));
        }
    }

    // =========================
    // FULL RESET (disassemble / teardown)
    // =========================
    private void resetReactorState() {
        coreShInt = 100;
        coreTemp = 0;
        coreCaseInt = 100;
        coreCaseTemp = 0;
        coreCasePress = 0;
        shieldPress = 0;
        spin = 0;
        lasers.reset();
        shield.reset();
        fusion.reset();
        selfDestruct = false;
        sdText = 0;
        meltdownCountdown = false;
        meltdownTimer = 0;
        prevShInt = 100;
        prevCaseInt = 100;
        reactorWear = 0;
        wearTickCounter = 0;
        prevWearDegraded = false;
        selfDestructActive = false;
        selfDestructChatTimer = 0;
        finalMeltdownActive = false;
        energyGenerated = 0;
        energyRemainder = 0;
        noFuelWarnTick = 0;

        display.resetDisplay();

        saveToDb();
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

        broadcast("<dark_red>☠ <red>Расплавление! Ядро реактора разрушено!");

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
        coreShInt = 100;
        coreCaseTemp = 0;
        coreCasePress = 0;
        coreCaseInt = 100;
        selfDestruct = false;
        sdText = 0;
        reactorWear = 0;
        wearTickCounter = 0;
        prevWearDegraded = false;
        selfDestructActive = false;
        selfDestructChatTimer = 0;
        finalMeltdownActive = false;
        energyGenerated = 0;
        energyRemainder = 0;
        pressTick = 0;
        recipeTick = 0;
        intensityDownTick = 0;
        intensityUpTick = 0;
        soundTick = 0;
        noFuelWarnTick = 0;
        meltdownCountdown = false;
        meltdownTimer = 0;
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
    public int getCoreShInt() { return coreShInt; }
    public int getCoreCaseTemp() { return coreCaseTemp; }
    public int getCoreCasePress() { return coreCasePress; }
    public int getCoreCaseInt() { return coreCaseInt; }

    public boolean isSelfDestruct() { return selfDestruct; }
    public boolean isMeltdownCountdown() { return meltdownCountdown; }
    public int getMeltdownTimer() { return meltdownTimer; }
    public boolean isSelfDestructActive() { return selfDestructActive; }
    public boolean isFinalMeltdownActive() { return finalMeltdownActive; }
    public int getReactorWear() { return reactorWear; }
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

    /** Broadcast to nearby players (used by the laser system). */
    public void broadcastRaw(String message) {
        broadcast(message);
    }

    public ReactorLasers getLasers() { return lasers; }
    public ReactorShield getShield() { return shield; }
    public ReactorFuel getFuel() { return fuel; }
    public ReactorFusion getFusion() { return fusion; }

    /** Fuel tick (every second): consumption by spin + spin decay when dry. */
    public void tickFuel() {
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

    /** Called by the laser startup pulse — ignites the shield formation. */
    public void onStartupPulse() {
        shield.start();
    }

    /** Shield breach detonation — tears down the reactor. */
    public void onShieldDetonated() {
        meltdown();
    }

    // Smoothed display values (delegated to ReactorDisplay)
    public int getDisplayCoreTemp() { return display.getDisplayCoreTemp(); }
    public double getDisplayShieldPress() { return display.getDisplayShieldPress(); }
    public double getDisplayCoreSpin() { return display.getDisplayCoreSpin(); }
    public int getDisplayCoreShInt() { return display.getDisplayCoreShInt(); }
    public int getDisplayCoreCaseTemp() { return display.getDisplayCoreCaseTemp(); }
    public int getDisplayCoreCasePress() { return display.getDisplayCoreCasePress(); }
    public int getDisplayCoreCaseInt() { return display.getDisplayCoreCaseInt(); }
    public int getDisplayReactorWear() { return display.getDisplayReactorWear(); }
    public int getDisplayEnergyRate() { return display.getDisplayEnergyRate(); }

    // =========================
    // HELPER: BROADCAST
    // =========================
    private void broadcast(String message) {
        String prefix = "<dark_red>Р.Т.С <dark_gray>» <white>";
        Player[] online = Bukkit.getOnlinePlayers().toArray(new Player[0]);
        for (Player player : online) {
            if (reactorLocation != null
                    && player.getWorld().equals(reactorLocation.getWorld())
                    && player.getLocation().distanceSquared(reactorLocation) <= 225) {
                player.sendMessage(MessageUtil.parse(prefix + message));
            }
        }
    }
}
