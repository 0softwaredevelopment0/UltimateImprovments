package com.ultimateimprovments.energy.generation.reactor;

import com.ultimateimprovments.util.LocationUtil;
import org.bukkit.Location;

/**
 * Reactor state — core, case, wear and recipe parameters.
 * Contains only fields and getter/setter methods, no tick logic.
 */
public class ReactorState {

    private Location reactorLocation;
    private boolean valid;
    private String reactorId;

    // Core parameters
    private int coreTemp;          // Core temperature, C* [-273 .. 2,000,000,000]
    private double shieldPress;    // Shield pressure, MPa (≈10.01 at working temp 10M C*)
    private double spin;           // Core spin, RPS (0.95x of the ten-million multiplier)
    private int coreCaseTemp;
    private int coreCasePress;
    private int coreCaseInt = 100;

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
    private double energyRemainder;

    // Lasers (roof controls): startup latch + powers P1, P2 (0..100),
    // Stab (0..200), Absorber valve (0..100)
    private boolean laserStarted;
    private double[] laserPowers = new double[4];

    private boolean structureDamaged;
    private int damageWarnTick;

    public boolean isStructureDamaged() { return structureDamaged; }
    public void setStructureDamaged(boolean val) { structureDamaged = val; }

    /** Copies all fields from another state (used by persistence load). */
    public void copyFrom(ReactorState o) {
        reactorLocation = o.reactorLocation;
        valid = o.valid;
        reactorId = o.reactorId;
        coreTemp = o.coreTemp;
        shieldPress = o.shieldPress;
        spin = o.spin;
        coreCaseTemp = o.coreCaseTemp;
        coreCasePress = o.coreCasePress;
        coreCaseInt = o.coreCaseInt;
        fusionParticles = o.fusionParticles;
        fusionCollected = o.fusionCollected;
        caseBroken = o.caseBroken;
        caseTemp = o.caseTemp;
        casePress = o.casePress;
        caseIntegrity = o.caseIntegrity;
        energyGenerated = o.energyGenerated;
        energyRemainder = o.energyRemainder;
        laserStarted = o.laserStarted;
        laserPowers = o.laserPowers == null ? new double[4] : o.laserPowers.clone();
        structureDamaged = o.structureDamaged;
    }

    // Tick counters
    private int soundTick;
    private int displayTick;
    private boolean prevHeating;
    private boolean prevCooling;
    private int integrityWarnTick;
    private int noFuelWarnTick;

    // Display (smooth)
    private double displayCoreTemp;
    private double displayShieldPress;
    private double displaySpin;
    private double displayCoreShInt = 100;
    private double displayCoreCaseTemp;
    private double displayCoreCasePress;
    private double displayCoreCaseInt = 100;
    private double displayEnergyRate;

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
    // CASE
    // =========================
    public int getCoreCaseTemp() { return coreCaseTemp; }
    public void setCoreCaseTemp(int val) { coreCaseTemp = val; }
    public void addCoreCaseTemp(int val) { coreCaseTemp = Math.min(coreCaseTemp + val, ReactorConfig.getInstance().getCaseTempMax()); }

    public int getCoreCasePress() { return coreCasePress; }
    public void setCoreCasePress(int val) { coreCasePress = val; }

    public int getCoreCaseInt() { return coreCaseInt; }
    public void setCoreCaseInt(int val) { coreCaseInt = Math.max(0, Math.min(100, val)); }

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
    public double getEnergyRemainder() { return energyRemainder; }
    public void setEnergyRemainder(double val) { energyRemainder = val; }
    public void addEnergyGenerated(int val) { energyGenerated += val; }

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

    // =========================
    // TICK COUNTERS
    // =========================
    public int getDisplayTick() { return displayTick; }
    public void setDisplayTick(int val) { displayTick = val; }
    public void incrementDisplayTick() { displayTick++; }

    public boolean isPrevHeating() { return prevHeating; }
    public void setPrevHeating(boolean val) { prevHeating = val; }
    public boolean isPrevCooling() { return prevCooling; }
    public void setPrevCooling(boolean val) { prevCooling = val; }

    public int getIntegrityWarnTick() { return integrityWarnTick; }
    public void setIntegrityWarnTick(int val) { integrityWarnTick = val; }
    public int getNoFuelWarnTick() { return noFuelWarnTick; }
    public void setNoFuelWarnTick(int val) { noFuelWarnTick = val; }

    // =========================
    // DISPLAY (interpolated/smoothed)
    // =========================
    public double getDisplayCoreTemp() { return displayCoreTemp; }
    public void setDisplayCoreTemp(double val) { displayCoreTemp = val; }
    public double getDisplayShieldPress() { return displayShieldPress; }
    public void setDisplayShieldPress(double val) { displayShieldPress = val; }
    public double getDisplaySpin() { return displaySpin; }
    public void setDisplaySpin(double val) { displaySpin = val; }
    public double getDisplayCoreShInt() { return displayCoreShInt; }
    public void setDisplayCoreShInt(double val) { displayCoreShInt = val; }
    public double getDisplayCoreCaseTemp() { return displayCoreCaseTemp; }
    public void setDisplayCoreCaseTemp(double val) { displayCoreCaseTemp = val; }
    public double getDisplayCoreCasePress() { return displayCoreCasePress; }
    public void setDisplayCoreCasePress(double val) { displayCoreCasePress = val; }
    public double getDisplayCoreCaseInt() { return displayCoreCaseInt; }
    public void setDisplayCoreCaseInt(double val) { displayCoreCaseInt = val; }
    public double getDisplayEnergyRate() { return displayEnergyRate; }
    public void setDisplayEnergyRate(double val) { displayEnergyRate = val; }

    // =========================
    // INT DISPLAY GETTERS
    // =========================
    public int getDisplayCoreTempInt() { return (int) Math.round(displayCoreTemp); }
    public int getDisplayShieldPressInt() { return (int) Math.round(displayShieldPress); }
    public int getDisplayCoreShIntInt() { return (int) Math.round(displayCoreShInt); }
    public int getDisplayCoreCaseTempInt() { return (int) Math.round(displayCoreCaseTemp); }
    public int getDisplayCoreCasePressInt() { return (int) Math.round(displayCoreCasePress); }
    public int getDisplayCoreCaseIntInt() { return (int) Math.round(displayCoreCaseInt); }

    // =========================
    // RESET
    // =========================
    public void resetAll() {
        coreTemp = 0;
        shieldPress = 0;
        spin = 0;
        coreCaseTemp = 0;
        coreCasePress = 0;
        coreCaseInt = 100;
        fusionParticles = 0;
        fusionCollected = 0;
        caseBroken = false;
        caseTemp = -273;
        casePress = 0;
        caseIntegrity = 100;
        energyGenerated = 0;
        energyRemainder = 0;

        soundTick = 0;
        noFuelWarnTick = 0;

        displayCoreTemp = 0;
        displayShieldPress = 0;
        displaySpin = 0;
        displayCoreShInt = 100;
        displayCoreCaseTemp = 0;
        displayCoreCasePress = 0;
        displayCoreCaseInt = 100;
        displayEnergyRate = 0;
        displayTick = 0;
        prevHeating = false;
        prevCooling = false;
        integrityWarnTick = 0;
        laserStarted = false;
        laserPowers = new double[4];
    }
}
