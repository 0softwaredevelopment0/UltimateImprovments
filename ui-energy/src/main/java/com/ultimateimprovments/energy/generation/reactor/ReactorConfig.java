package com.ultimateimprovments.energy.generation.reactor;

import com.ultimateimprovments.core.Main;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Loads and stores the reactor configuration from config.yml.
 * <p>
 * The DFC stat model works on the "ten-million multiplier": the working
 * temperature is 10,000,000 C* = 1x (30M = 3x, etc.). Shield pressure (P)
 * and core spin (S) are derived from the temperature through that multiplier:
 * <ul>
 *   <li><b>P</b> — {@code (T / core_work_temp) * 10.01} MPa → 10.010 MPa at working temp</li>
 *   <li><b>S</b> — {@code 0.95 * (T / core_work_temp)} RPS → 0.95 RPS at working temp</li>
 * </ul>
 */
public class ReactorConfig {

    // =========================
    // SINGLETON
    // =========================
    private static ReactorConfig instance;

    public static ReactorConfig getInstance() {
        return instance;
    }

    public static void init() {
        instance = new ReactorConfig();
        instance.load();
    }

    // =========================
    // CONFIG FIELDS
    // =========================
    private boolean enabled;
    private int tempDecayDivisor;   // passive decay: per tick = max(1, T / divisor)
    private int heatRate;
    private int coolRate;
    private int coreTempMax;        // hard limit (2,000,000,000)
    private int coreTempMin;        // hard limit (-273)
    private int coreTempCoolMin;
    private int coreWorkTemp;       // working temperature = 1x (10,000,000)
    private double pressFollowRate; // how fast shield pressure follows its temperature target
    private double spinFollowRate;  // how fast core spin follows its temperature target
    private int energyRate;         // energy per tick at working temperature
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
    private int selfDestructChance;
    private int selfDestructIntDecayRate;
    private int meltdownExplosionRadius;
    private int recipeTimeMax;
    private int recipeTempMin;      // fusion recipe progresses in [min, max] window
    private int recipeTempMax;
    private double shieldBuildRate;       // shield integrity build-up %/sec (Creating state)
    private double shieldStressHeatPer;   // C* per 1% temperature stress
    private double shieldStressPressPer;  // MPa per 1% pressure stress
    private double shieldStressSpinPer;   // RPS per 1% spin stress
    private double shieldDecayBase;       // %/sec at 100% over-stress (scales linearly)
    private int shieldFailureCountdown;   // seconds between shield 0% and detonation
    private int shieldExplosionRadius;    // creeper explosion radius
    private double shieldParticleRodSpeed;   // END_ROD beam speed (blocks/tick)
    private int shieldParticleRodCount;      // total END_ROD particles per tick
    private int shieldParticleDustCount;     // DUST particles in the core per tick

    // Fuel system — consumption by core spin (100% = 1 gold ingot + 1 diamond / 10s)
    private double fuelSpinMin;              // below this spin nothing is consumed
    private double fuelWorkSpin;             // working point: base rate (95 000 RPS)
    private double fuelOverSpin;             // over-spin threshold (100 000 RPS)
    private double fuelBaseRate;             // %/s at the working point (5 = 1 unit / 10s)
    private double fuelMinRate;              // 1% floor
    private double fuelOverPer10k;           // +1% per 10 000 RPS above over-spin
    private double fuelNoFuelSpinDecay;      // RPS lost per second when out of fuel

    // =========================
    // LASERS (roof controls)
    // Power Laser #1/#2 heat the core at power_laser_heat_rate C*/sec each
    // at 100% power (range 0..100%). Stab. Laser cools at stab_cool_rate
    // C*/sec per 100% of power (range 0..200%, 200% = 2x power).
    // laser_ramp_rate is the ±%/sec applied while a +5%/−5% lamp is powered.
    // =========================
    private double laserRampRate;
    private int powerLaserHeatRate;
    private int stabCoolRate;

    private void load() {
        FileConfiguration cfg = Main.getInstance().getConfig();

        enabled = cfg.getBoolean("reactor.enabled", true);
        tempDecayDivisor = cfg.getInt("reactor.temp_decay_divisor", 10000);
        heatRate = cfg.getInt("reactor.heat_rate", 5000);
        coolRate = cfg.getInt("reactor.cool_rate", 5000);
        coreTempMax = cfg.getInt("reactor.core_temp_max", 2000000000);
        coreTempMin = cfg.getInt("reactor.core_temp_min", -273);
        coreTempCoolMin = cfg.getInt("reactor.core_temp_cool_min", -270);
        coreWorkTemp = cfg.getInt("reactor.core_work_temp", 10000000);
        pressFollowRate = cfg.getDouble("reactor.press_follow_rate", 0.02);
        spinFollowRate = cfg.getDouble("reactor.spin_follow_rate", 0.01);
        energyRate = cfg.getInt("reactor.energy_rate", 100);
        caseTempHeatRate = cfg.getInt("reactor.case_temp_heat_rate", 2);
        caseTempMax = cfg.getInt("reactor.case_temp_max", 8000);
        caseTempCoolRate = cfg.getInt("reactor.case_temp_cool_rate", 2);
        caseTempCoolMin = cfg.getInt("reactor.case_temp_cool_min", -271);
        caseTempDecayRate = cfg.getInt("reactor.case_temp_decay_rate", 1);
        casePressHeatRate = cfg.getInt("reactor.case_press_heat_rate", 4);
        casePressMax = cfg.getInt("reactor.case_press_max", 10000);
        casePressDecayRate = cfg.getInt("reactor.case_press_decay_rate", 1);
        shIntDecayTempThreshold = cfg.getInt("reactor.shell_integrity_decay_temp", 10000000);
        shellIntDecayRate = cfg.getInt("reactor.shell_int_decay_rate", 1);
        shellIntRecoveryTempMax = cfg.getInt("reactor.shell_int_recovery_temp_max", 9999999);
        shellIntRecoveryRate = cfg.getInt("reactor.shell_int_recovery_rate", 1);
        caseIntDecayPressThreshold = cfg.getInt("reactor.case_integrity_decay_press", 7000);
        caseIntDecayTempThreshold = cfg.getInt("reactor.case_integrity_decay_temp", 7000);
        caseIntDecayPressRate = cfg.getInt("reactor.case_int_decay_press_rate", 1);
        caseIntDecayTempRate = cfg.getInt("reactor.case_int_decay_temp_rate", 1);
        caseIntRecoveryPressMax = cfg.getInt("reactor.case_int_recovery_press_max", 7000);
        caseIntRecoveryTempMax = cfg.getInt("reactor.case_int_recovery_temp_max", 4999);
        caseIntRecoveryRate = cfg.getInt("reactor.case_int_recovery_rate", 1);
        wearEnabled = cfg.getBoolean("reactor.wear.enabled", true);
        wearIntervalNormal = cfg.getInt("reactor.wear.interval_normal", 1200);
        wearIntervalDegradation = cfg.getInt("reactor.wear.interval_degradation", 20);
        wearChatCountdown = cfg.getInt("reactor.wear.chat_countdown", 30);
        wearFinalMeltdownAt = cfg.getInt("reactor.wear.final_meltdown_start_at", 11);
        wearFinalMeltdownDuration = cfg.getInt("reactor.wear.final_meltdown_duration", 10);
        selfDestructChance = cfg.getInt("reactor.self_destruct_chance", 1000000);
        selfDestructIntDecayRate = cfg.getInt("reactor.self_destruct_int_decay_rate", 2);
        meltdownExplosionRadius = cfg.getInt("reactor.meltdown_explosion_radius", 128);
        recipeTimeMax = cfg.getInt("reactor.recipe_time_max", 100);
        recipeTempMin = cfg.getInt("reactor.recipe_temp_min", 5000000);
        recipeTempMax = cfg.getInt("reactor.recipe_temp_max", 15000000);
        laserRampRate = cfg.getDouble("reactor.laser_ramp_rate", 5.0);
        powerLaserHeatRate = cfg.getInt("reactor.power_laser_heat_rate", 5000);
        stabCoolRate = cfg.getInt("reactor.stab_cool_rate", 9500);
        shieldBuildRate = cfg.getDouble("reactor.shield_build_rate", 5.0);
        shieldStressHeatPer = cfg.getDouble("reactor.shield_stress_heat_per", 400000);
        shieldStressPressPer = cfg.getDouble("reactor.shield_stress_press_per", 0.5);
        shieldStressSpinPer = cfg.getDouble("reactor.shield_stress_spin_per", 35000);
        shieldDecayBase = cfg.getDouble("reactor.shield_decay_base", 0.333);
        shieldFailureCountdown = cfg.getInt("reactor.shield_failure_countdown", 10);
        shieldExplosionRadius = cfg.getInt("reactor.shield_explosion_radius", 10);
        shieldParticleRodSpeed = cfg.getDouble("reactor.shield_particle_rod_speed", 0.8);
        shieldParticleRodCount = cfg.getInt("reactor.shield_particle_rod_count", 16);
        shieldParticleDustCount = cfg.getInt("reactor.shield_particle_dust_count", 16);
        fuelSpinMin = cfg.getDouble("reactor.fuel_spin_min", 1000);
        fuelWorkSpin = cfg.getDouble("reactor.fuel_work_spin", 95000);
        fuelOverSpin = cfg.getDouble("reactor.fuel_over_spin", 100000);
        fuelBaseRate = cfg.getDouble("reactor.fuel_base_rate", 5.0);
        fuelMinRate = cfg.getDouble("reactor.fuel_min_rate", 1.0);
        fuelOverPer10k = cfg.getDouble("reactor.fuel_over_per_10k", 1.0);
        fuelNoFuelSpinDecay = cfg.getDouble("reactor.fuel_no_fuel_spin_decay", 500.0);
    }

    // =========================
    // GETTERS
    // =========================
    public boolean isEnabled() { return enabled; }
    public int getTempDecayDivisor() { return tempDecayDivisor; }
    public int getHeatRate() { return heatRate; }
    public int getCoolRate() { return coolRate; }
    public int getCoreTempMax() { return coreTempMax; }
    public int getCoreTempMin() { return coreTempMin; }
    public int getCoreTempCoolMin() { return coreTempCoolMin; }
    public int getCoreWorkTemp() { return coreWorkTemp; }
    public double getPressFollowRate() { return pressFollowRate; }
    public double getSpinFollowRate() { return spinFollowRate; }
    public int getEnergyRate() { return energyRate; }
    public int getCaseTempHeatRate() { return caseTempHeatRate; }
    public int getCaseTempMax() { return caseTempMax; }
    public int getCaseTempCoolRate() { return caseTempCoolRate; }
    public int getCaseTempCoolMin() { return caseTempCoolMin; }
    public int getCaseTempDecayRate() { return caseTempDecayRate; }
    public int getCasePressHeatRate() { return casePressHeatRate; }
    public int getCasePressMax() { return casePressMax; }
    public int getCasePressDecayRate() { return casePressDecayRate; }
    public int getShIntDecayTempThreshold() { return shIntDecayTempThreshold; }
    public int getShellIntDecayRate() { return shellIntDecayRate; }
    public int getShellIntRecoveryTempMax() { return shellIntRecoveryTempMax; }
    public int getShellIntRecoveryRate() { return shellIntRecoveryRate; }
    public int getCaseIntDecayPressThreshold() { return caseIntDecayPressThreshold; }
    public int getCaseIntDecayTempThreshold() { return caseIntDecayTempThreshold; }
    public int getCaseIntDecayPressRate() { return caseIntDecayPressRate; }
    public int getCaseIntDecayTempRate() { return caseIntDecayTempRate; }
    public int getCaseIntRecoveryPressMax() { return caseIntRecoveryPressMax; }
    public int getCaseIntRecoveryTempMax() { return caseIntRecoveryTempMax; }
    public int getCaseIntRecoveryRate() { return caseIntRecoveryRate; }
    public boolean isWearEnabled() { return wearEnabled; }
    public int getWearIntervalNormal() { return wearIntervalNormal; }
    public int getWearIntervalDegradation() { return wearIntervalDegradation; }
    public int getWearChatCountdown() { return wearChatCountdown; }
    public int getWearFinalMeltdownAt() { return wearFinalMeltdownAt; }
    public int getWearFinalMeltdownDuration() { return wearFinalMeltdownDuration; }
    public int getSelfDestructChance() { return selfDestructChance; }
    public int getSelfDestructIntDecayRate() { return selfDestructIntDecayRate; }
    public int getMeltdownExplosionRadius() { return meltdownExplosionRadius; }
    public int getRecipeTimeMax() { return recipeTimeMax; }
    public int getRecipeTempMin() { return recipeTempMin; }
    public int getRecipeTempMax() { return recipeTempMax; }
    public double getLaserRampRate() { return laserRampRate; }
    public int getPowerLaserHeatRate() { return powerLaserHeatRate; }
    public int getStabCoolRate() { return stabCoolRate; }
    public double getShieldBuildRate() { return shieldBuildRate; }
    public double getShieldStressHeatPer() { return shieldStressHeatPer; }
    public double getShieldStressPressPer() { return shieldStressPressPer; }
    public double getShieldStressSpinPer() { return shieldStressSpinPer; }
    public double getShieldDecayBase() { return shieldDecayBase; }
    public int getShieldFailureCountdown() { return shieldFailureCountdown; }
    public int getShieldExplosionRadius() { return shieldExplosionRadius; }
    public double getShieldParticleRodSpeed() { return shieldParticleRodSpeed; }
    public int getShieldParticleRodCount() { return shieldParticleRodCount; }
    public int getShieldParticleDustCount() { return shieldParticleDustCount; }
    public double getFuelSpinMin() { return fuelSpinMin; }
    public double getFuelWorkSpin() { return fuelWorkSpin; }
    public double getFuelOverSpin() { return fuelOverSpin; }
    public double getFuelBaseRate() { return fuelBaseRate; }
    public double getFuelMinRate() { return fuelMinRate; }
    public double getFuelOverPer10k() { return fuelOverPer10k; }
    public double getFuelNoFuelSpinDecay() { return fuelNoFuelSpinDecay; }
}
