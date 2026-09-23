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
    private int coreTempMax;        // hard limit (2,000,000,000)
    private int coreTempMin;        // hard limit (-273)
    private int coreWorkTemp;       // working temperature = 1x (10,000,000)
    private double pressFollowRate; // how fast shield pressure follows its temperature target
    private double spinFollowRate;  // how fast core spin follows its temperature target
    private int energyRate;         // energy per tick at working temperature
    private int meltdownExplosionRadius;
    private double shieldBuildRate;       // shield integrity build-up %/sec (Creating state)
    private double shieldStressHeatPer;   // C* per 1% temperature stress
    private double shieldStressPressPer;  // MPa per 1% pressure stress
    private double shieldStressSpinPer;   // RPS per 1% spin stress
    private double shieldDecayBase;       // %/sec at 100% over-stress (scales linearly)
    private int shieldRecoveryEverySec;   // passive recovery: +1% every N seconds (5)
    private double shieldRecoveryRate;    // passive recovery amount % per interval (1)
    private int shieldFailureCountdown;   // seconds between shield 0% and detonation
    private int shieldExplosionRadius;    // creeper explosion radius
    private double shieldParticleRodSpeed;   // END_ROD beam speed (blocks/tick)
    private int shieldParticleRodCount;      // total END_ROD particles per tick
    private int shieldIntegrityShutdownPercent; // emergency core shutdown threshold
    private int shieldParticleDustCount;     // DUST particles in the core per tick

    // Self-destruct protocol — 1% chance on startup, 5s of dead sensors (No
    // signal), a timed countdown with locked control bulbs, then the overpower
    // finale: the Power Lasers run at 1000% and burn the shield at %/sec.
    private double selfdestructChance;       // % (1)
    private int selfdestructNoSignalSec;     // phase 1 duration (5)
    private int selfdestructTimedSec;        // phase 2 countdown (60)
    private double selfdestructOverpowerRate; // shield %/sec burn at 1000% (10)
    private double selfdestructAdvRadius;    // advancement grant radius from the center (20)

    // Content Absorber valve cooling — quadratic heat drain carried by the
    // collected fusion particles: cool = k × valve%² per second, but only while
    // particles are actually flowing into the absorber. Vented particles also
    // leak radiation around the reactor.
    private double absorberCoolRate;         // C*/sec at 100% valve (≈ stab_cool_rate)
    private double absorberRadPerSec;        // rad per second at 100% valve

    // Fuel system — consumption by core spin (100% = 1 gold ingot + 1 diamond / 10s)
    private double fuelSpinMin;              // below this spin nothing is consumed
    private double fuelWorkSpin;             // working point: base rate (95 000 RPS)
    private double fuelOverSpin;             // over-spin threshold (100 000 RPS)
    private double fuelBaseRate;             // %/s at the working point (5 = 1 unit / 10s)
    private double fuelMinRate;              // 1% floor
    private double fuelOverPer10k;           // +1% per 10 000 RPS above over-spin
    private double fuelNoFuelSpinDecay;      // RPS lost per second when out of fuel

    // Fusion system — ancient debris forms in the core from fusion particles
    private int fusionTempMin;             // below this temp no fusion (1M C*)
    private int fusionTempWork;            // 100% speed point (10M C*)
    private int fusionTempPeak;            // 150% speed point (15M C*)
    private int fusionTempEnd;             // back to 0% (25M C*)
    private int fusionParticlesPerSec;     // particles/sec at 100% speed (20)
    private double fusionAbsorbRate;       // particles/sec collected by the absorber at 100%
    private int fusionDebrisPer;           // collected particles per ancient debris

    // Case system — outer glass protection (T/pressure/integrity)
    private int caseHeatRate;              // C*/sec at 150% fusion speed (15)
    private int caseTempMax;               // glass melts at this temp (10 000)
    private int caseTempMin;               // hard minimum (-273)
    private double casePressMax;           // glass bursts at this MPa (15)
    private double casePressFollowRate;    // how fast pressure follows the temp (0..1/tick)
    private int caseIntDecayTemp;          // integrity decays above this temp
    private double caseIntDecayPress;      // integrity decays above this MPa
    private double caseIntDecayRate;       // %/sec decay while over thresholds

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

    public double getSelfdestructChance() { return selfdestructChance; }
    public int getSelfdestructNoSignalSec() { return selfdestructNoSignalSec; }
    public int getSelfdestructTimedSec() { return selfdestructTimedSec; }
    public double getSelfdestructOverpowerRate() { return selfdestructOverpowerRate; }
    public double getSelfdestructAdvRadius() { return selfdestructAdvRadius; }
    public double getAbsorberCoolRate() { return absorberCoolRate; }
    public double getAbsorberRadPerSec() { return absorberRadPerSec; }

    private void load() {
        FileConfiguration cfg = Main.getInstance().getConfig();

        enabled = cfg.getBoolean("reactor.enabled", true);
        coreTempMax = cfg.getInt("reactor.core_temp_max", 2000000000);
        coreTempMin = cfg.getInt("reactor.core_temp_min", -273);
        coreWorkTemp = cfg.getInt("reactor.core_work_temp", 10000000);
        pressFollowRate = cfg.getDouble("reactor.press_follow_rate", 0.02);
        spinFollowRate = cfg.getDouble("reactor.spin_follow_rate", 0.01);
        energyRate = cfg.getInt("reactor.energy_rate", 100);
        meltdownExplosionRadius = cfg.getInt("reactor.meltdown_explosion_radius", 128);
        laserRampRate = cfg.getDouble("reactor.laser_ramp_rate", 5.0);
        powerLaserHeatRate = cfg.getInt("reactor.power_laser_heat_rate", 5000);
        stabCoolRate = cfg.getInt("reactor.stab_cool_rate", 9500);
        shieldBuildRate = cfg.getDouble("reactor.shield_build_rate", 5.0);
        shieldStressHeatPer = cfg.getDouble("reactor.shield_stress_heat_per", 400000);
        shieldStressPressPer = cfg.getDouble("reactor.shield_stress_press_per", 0.5);
        shieldStressSpinPer = cfg.getDouble("reactor.shield_stress_spin_per", 35000);
        shieldDecayBase = cfg.getDouble("reactor.shield_decay_base", 0.333);
        shieldRecoveryEverySec = cfg.getInt("reactor.shield_recovery_every_sec", 5);
        shieldRecoveryRate = cfg.getDouble("reactor.shield_recovery_rate", 1.0);
        shieldFailureCountdown = cfg.getInt("reactor.shield_failure_countdown", 10);
        shieldExplosionRadius = cfg.getInt("reactor.shield_explosion_radius", 10);
        shieldParticleRodSpeed = cfg.getDouble("reactor.shield_particle_rod_speed", 0.8);
        shieldParticleRodCount = cfg.getInt("reactor.shield_particle_rod_count", 16);
        shieldIntegrityShutdownPercent = cfg.getInt("reactor.shield_integrity_shutdown_percent", 25);
        shieldParticleDustCount = cfg.getInt("reactor.shield_particle_dust_count", 16);
        selfdestructChance = cfg.getDouble("reactor.selfdestruct_chance", 1.0);
        selfdestructNoSignalSec = cfg.getInt("reactor.selfdestruct_no_signal_sec", 5);
        selfdestructTimedSec = cfg.getInt("reactor.selfdestruct_timed_sec", 60);
        selfdestructOverpowerRate = cfg.getDouble("reactor.selfdestruct_overpower_rate", 10.0);
        selfdestructAdvRadius = cfg.getDouble("reactor.selfdestruct_adv_radius", 20.0);
        absorberCoolRate = cfg.getDouble("reactor.absorber_cool_rate", 9500.0);
        absorberRadPerSec = cfg.getDouble("reactor.absorber_rad_per_sec", 40.0);
        fuelSpinMin = cfg.getDouble("reactor.fuel_spin_min", 1000);
        fuelWorkSpin = cfg.getDouble("reactor.fuel_work_spin", 95000);
        fuelOverSpin = cfg.getDouble("reactor.fuel_over_spin", 100000);
        fuelBaseRate = cfg.getDouble("reactor.fuel_base_rate", 5.0);
        fuelMinRate = cfg.getDouble("reactor.fuel_min_rate", 1.0);
        fuelOverPer10k = cfg.getDouble("reactor.fuel_over_per_10k", 1.0);
        fuelNoFuelSpinDecay = cfg.getDouble("reactor.fuel_no_fuel_spin_decay", 500.0);
        fusionTempMin = cfg.getInt("reactor.fusion_temp_min", 1000000);
        fusionTempWork = cfg.getInt("reactor.fusion_temp_work", 10000000);
        fusionTempPeak = cfg.getInt("reactor.fusion_temp_peak", 15000000);
        fusionTempEnd = cfg.getInt("reactor.fusion_temp_end", 25000000);
        fusionParticlesPerSec = cfg.getInt("reactor.fusion_particles_per_sec", 20);
        fusionAbsorbRate = cfg.getDouble("reactor.fusion_absorb_rate", 21.0);
        fusionDebrisPer = cfg.getInt("reactor.fusion_debris_per", 10000);
        caseHeatRate = cfg.getInt("reactor.case_heat_rate", 15);
        caseTempMax = cfg.getInt("reactor.case_temp_max", 10000);
        caseTempMin = cfg.getInt("reactor.case_temp_min", -273);
        casePressMax = cfg.getDouble("reactor.case_press_max", 15.0);
        casePressFollowRate = cfg.getDouble("reactor.case_press_follow_rate", 0.02);
        caseIntDecayTemp = cfg.getInt("reactor.case_int_decay_temp", 8000);
        caseIntDecayPress = cfg.getDouble("reactor.case_int_decay_press", 12.0);
        caseIntDecayRate = cfg.getDouble("reactor.case_int_decay_rate", 1.0);
    }

    // =========================
    // GETTERS
    // =========================
    public boolean isEnabled() { return enabled; }
    public int getCoreTempMax() { return coreTempMax; }
    public int getCoreTempMin() { return coreTempMin; }
    public int getCoreWorkTemp() { return coreWorkTemp; }
    public double getPressFollowRate() { return pressFollowRate; }
    public double getSpinFollowRate() { return spinFollowRate; }
    public int getEnergyRate() { return energyRate; }
    public int getMeltdownExplosionRadius() { return meltdownExplosionRadius; }
    public double getLaserRampRate() { return laserRampRate; }
    public int getPowerLaserHeatRate() { return powerLaserHeatRate; }
    public int getStabCoolRate() { return stabCoolRate; }
    public double getShieldBuildRate() { return shieldBuildRate; }
    public double getShieldStressHeatPer() { return shieldStressHeatPer; }
    public double getShieldStressPressPer() { return shieldStressPressPer; }
    public double getShieldStressSpinPer() { return shieldStressSpinPer; }
    public double getShieldDecayBase() { return shieldDecayBase; }
    public int getShieldRecoveryEverySec() { return shieldRecoveryEverySec; }
    public double getShieldRecoveryRate() { return shieldRecoveryRate; }
    public int getShieldFailureCountdown() { return shieldFailureCountdown; }
    public int getShieldExplosionRadius() { return shieldExplosionRadius; }
    public double getShieldParticleRodSpeed() { return shieldParticleRodSpeed; }
    public int getShieldParticleRodCount() { return shieldParticleRodCount; }
    public int getShieldIntegrityShutdownPercent() { return shieldIntegrityShutdownPercent; }
    public int getShieldParticleDustCount() { return shieldParticleDustCount; }
    public double getFuelSpinMin() { return fuelSpinMin; }
    public double getFuelWorkSpin() { return fuelWorkSpin; }
    public double getFuelOverSpin() { return fuelOverSpin; }
    public double getFuelBaseRate() { return fuelBaseRate; }
    public double getFuelMinRate() { return fuelMinRate; }
    public double getFuelOverPer10k() { return fuelOverPer10k; }
    public double getFuelNoFuelSpinDecay() { return fuelNoFuelSpinDecay; }
    public int getFusionTempMin() { return fusionTempMin; }
    public int getFusionTempWork() { return fusionTempWork; }
    public int getFusionTempPeak() { return fusionTempPeak; }
    public int getFusionTempEnd() { return fusionTempEnd; }
    public int getFusionParticlesPerSec() { return fusionParticlesPerSec; }
    public double getFusionAbsorbRate() { return fusionAbsorbRate; }
    public int getFusionDebrisPer() { return fusionDebrisPer; }
    public int getCaseHeatRate() { return caseHeatRate; }
    public int getCaseTempMax() { return caseTempMax; }
    public int getCaseTempMin() { return caseTempMin; }
    public double getCasePressMax() { return casePressMax; }
    public double getCasePressFollowRate() { return casePressFollowRate; }
    public int getCaseIntDecayTemp() { return caseIntDecayTemp; }
    public double getCaseIntDecayPress() { return caseIntDecayPress; }
    public double getCaseIntDecayRate() { return caseIntDecayRate; }
}
