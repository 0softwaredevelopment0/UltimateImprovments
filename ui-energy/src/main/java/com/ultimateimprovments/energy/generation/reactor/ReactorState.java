package com.ultimateimprovments.energy.generation.reactor;

import com.ultimateimprovments.util.LocationUtil;
import org.bukkit.Location;

/**
 * Reactor state transfer object for persistence (ReactorManager →
 * ReactorPersistence → DB and back). Contains only fields and
 * getter/setter methods, no tick logic — the live simulation state lives
 * in {@link ReactorManager} and its subsystems.
 */
public class ReactorState {

    private Location reactorLocation;
    private boolean valid;
    private String reactorId;

    // Core parameters
    private int coreTemp;          // Core temperature, C* [-273 .. 2,000,000,000]
    private double shieldPress;    // Shield pressure, MPa (≈10.01 at working temp 10M C*)
    private double spin;           // Core spin, RPS (95 000 at the 10M working point)

    // Fusion
    private double fusionParticles;
    private double fusionCollected;

    // Case
    private boolean caseBroken;
    private int caseTemp;
    private double casePress;
    private int caseIntegrity;

    // Energy
    private long energyGenerated;

    // Lasers: startup latch + powers P1, P2 (0..100), Stab (0..200), Absorber valve (0..100)
    private boolean laserStarted;
    private double[] laserPowers = new double[4];

    private boolean structureDamaged;

    // Shield (full restore — no loss on restart)
    private String shieldState = "OFFLINE";   // OFFLINE / CREATING / WORKING / FAILED
    private double shieldIntegrity;            // 0..100 %
    private int shieldFailCountdown;           // ticks until the detonation (FAILED)

    // Self-destruct protocol (full restore — no loss on restart)
    private String selfdestructPhase = "NONE"; // NONE / SENSORS_DOWN / TIMED / FINALE
    private int selfdestructTicks;
    private boolean selfdestructDone;
    private boolean selfdestructCancelled; // protocol was cancelled once — re-roll chance halved until disassembly

    // Emergency core shutdown latch (shield integrity below the critical threshold)
    private boolean coreEmergencyStopped;

    // Meltdown restabilization protocol (aux shield + attempt sequence):
    // full restore — the phase resumes after a restart mid-sequence
    private String meltdownPhase = "NONE"; // NONE / AUX_ANALYZING / ... / RESTAB_FAIL
    private int meltdownTicks;
    private double restabChance;       // success chance captured at the attempt start

    // Stall shutdown (reaction lost its heat → auto shutdown at −273 C*):
    // full restore — the procedure resumes after a restart mid-sequence
    private String stallPhase = "NONE"; // NONE / WAIT_POWER / ... / SHIELD_RAMP
    private int stallTicks;
    private boolean stallManual;        // manual trigger shutdown (Trigger Action lamp pulse), not the emergency stall
    private boolean coreOffline;        // shutdown finished, awaiting a new startup

    // Cinematic startup sequence (lamp pulse → lasers → valve → shield → ignite)
    private String startupPhase = "NONE";
    private int startupTicks;

    /** Copies all fields from another state (used by persistence load). */
    public void copyFrom(ReactorState o) {
        reactorLocation = o.reactorLocation;
        valid = o.valid;
        reactorId = o.reactorId;
        coreTemp = o.coreTemp;
        shieldPress = o.shieldPress;
        spin = o.spin;
        fusionParticles = o.fusionParticles;
        fusionCollected = o.fusionCollected;
        caseBroken = o.caseBroken;
        caseTemp = o.caseTemp;
        casePress = o.casePress;
        caseIntegrity = o.caseIntegrity;
        energyGenerated = o.energyGenerated;
        laserStarted = o.laserStarted;
        laserPowers = o.laserPowers == null ? new double[4] : o.laserPowers.clone();
        structureDamaged = o.structureDamaged;
        shieldState = o.shieldState;
        shieldIntegrity = o.shieldIntegrity;
        shieldFailCountdown = o.shieldFailCountdown;
        selfdestructPhase = o.selfdestructPhase;
        selfdestructTicks = o.selfdestructTicks;
        selfdestructDone = o.selfdestructDone;
        selfdestructCancelled = o.selfdestructCancelled;
        coreEmergencyStopped = o.coreEmergencyStopped;
        meltdownPhase = o.meltdownPhase;
        meltdownTicks = o.meltdownTicks;
        restabChance = o.restabChance;
        stallPhase = o.stallPhase;
        stallTicks = o.stallTicks;
        stallManual = o.stallManual;
        coreOffline = o.coreOffline;
        startupPhase = o.startupPhase;
        startupTicks = o.startupTicks;
    }

    // =========================
    // LOCATION
    // =========================
    public Location getReactorLocation() { return reactorLocation; }

    public void setReactorLocation(Location loc) {
        if (loc != null) {
            this.reactorLocation = LocationUtil.normalize(loc);
            this.valid = true;
            this.reactorId = "REACTOR-" + this.reactorLocation.getBlockX()
                    + "-" + this.reactorLocation.getBlockY()
                    + "-" + this.reactorLocation.getBlockZ();
        } else {
            this.reactorLocation = null;
            this.valid = false;
            this.reactorId = null;
        }
    }

    public boolean isValid() { return valid && reactorLocation != null; }
    public String getReactorId() { return reactorId; }
    public void setReactorId(String val) { reactorId = val; }
    public void setValid(boolean valid) { this.valid = valid; }

    // =========================
    // CORE
    // =========================
    public int getCoreTemp() { return coreTemp; }
    public void setCoreTemp(int val) { coreTemp = val; }
    public void addCoreTemp(int val) { coreTemp += val; }
    public void subtractCoreTemp(int val) { coreTemp -= val; }

    public double getShieldPress() { return shieldPress; }
    public void setShieldPress(double val) { shieldPress = Math.max(0, val); }

    public double getSpin() { return spin; }
    public void setSpin(double val) { spin = Math.max(0, val); }

    // =========================
    // RECIPE
    // =========================
    public double getFusionParticles() { return fusionParticles; }
    public void setFusionParticles(double val) { fusionParticles = Math.max(0, val); }
    public double getFusionCollected() { return fusionCollected; }
    public void setFusionCollected(double val) { fusionCollected = Math.max(0, val); }

    public boolean isCaseBroken() { return caseBroken; }
    public void setCaseBroken(boolean val) { caseBroken = val; }
    public int getCaseTemp() { return caseTemp; }
    public void setCaseTemp(int val) { caseTemp = val; }
    public double getCasePress() { return casePress; }
    public void setCasePress(double val) { casePress = val; }
    public int getCaseIntegrity() { return caseIntegrity; }
    public void setCaseIntegrity(int val) { caseIntegrity = val; }

    // =========================
    // ENERGY
    // =========================
    public long getEnergyGenerated() { return energyGenerated; }
    public void setEnergyGenerated(long val) { energyGenerated = val; }

    // =========================
    // LASERS
    // =========================
    public boolean isLaserStarted() { return laserStarted; }
    public void setLaserStarted(boolean val) { laserStarted = val; }
    public double[] getLaserPowers() { return laserPowers.clone(); }
    public void setLaserPowers(double[] val) {
        if (val == null) return;
        for (int i = 0; i < Math.min(4, val.length); i++) {
            laserPowers[i] = Math.max(0, val[i]);
        }
    }

    public boolean isStructureDamaged() { return structureDamaged; }
    public void setStructureDamaged(boolean val) { structureDamaged = val; }

    // =========================
    // SHIELD
    // =========================
    public String getShieldState() { return shieldState; }
    public void setShieldState(String val) { shieldState = val == null ? "OFFLINE" : val; }
    public double getShieldIntegrity() { return shieldIntegrity; }
    public void setShieldIntegrity(double val) { shieldIntegrity = Math.max(0, Math.min(100, val)); }
    public int getShieldFailCountdown() { return shieldFailCountdown; }
    public void setShieldFailCountdown(int val) { shieldFailCountdown = Math.max(0, val); }

    // =========================
    // SELF-DESTRUCT
    // =========================
    public String getSelfdestructPhase() { return selfdestructPhase; }
    public void setSelfdestructPhase(String val) { selfdestructPhase = val == null ? "NONE" : val; }
    public int getSelfdestructTicks() { return selfdestructTicks; }
    public void setSelfdestructTicks(int val) { selfdestructTicks = Math.max(0, val); }
    public boolean isSelfdestructDone() { return selfdestructDone; }
    public void setSelfdestructDone(boolean val) { selfdestructDone = val; }
    public boolean isSelfdestructCancelled() { return selfdestructCancelled; }
    public void setSelfdestructCancelled(boolean val) { selfdestructCancelled = val; }

    public boolean isCoreEmergencyStopped() { return coreEmergencyStopped; }
    public void setCoreEmergencyStopped(boolean val) { coreEmergencyStopped = val; }

    // =========================
    // MELTDOWN RESTABILIZATION
    // =========================
    public String getMeltdownPhase() { return meltdownPhase; }
    public void setMeltdownPhase(String val) { meltdownPhase = val == null ? "NONE" : val; }
    public int getMeltdownTicks() { return meltdownTicks; }
    public void setMeltdownTicks(int val) { meltdownTicks = Math.max(0, val); }
    public double getRestabChance() { return restabChance; }
    public void setRestabChance(double val) { restabChance = Math.max(0, Math.min(100, val)); }

    // =========================
    // STALL SHUTDOWN
    // =========================
    public String getStallPhase() { return stallPhase; }
    public void setStallPhase(String val) { stallPhase = val == null ? "NONE" : val; }
    public int getStallTicks() { return stallTicks; }
    public void setStallTicks(int val) { stallTicks = Math.max(0, val); }
    public boolean isStallManual() { return stallManual; }
    public void setStallManual(boolean val) { stallManual = val; }
    public boolean isCoreOffline() { return coreOffline; }
    public void setCoreOffline(boolean val) { coreOffline = val; }

    // =========================
    // STARTUP SEQUENCE
    // =========================
    public String getStartupPhase() { return startupPhase; }
    public void setStartupPhase(String val) { startupPhase = val == null ? "NONE" : val; }
    public int getStartupTicks() { return startupTicks; }
    public void setStartupTicks(int val) { startupTicks = Math.max(0, val); }
}
