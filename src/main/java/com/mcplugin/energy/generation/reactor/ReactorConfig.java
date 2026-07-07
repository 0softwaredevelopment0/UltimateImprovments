package com.mcplugin.energy.generation.reactor;

import com.mcplugin.core.Main;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Загружает и хранит конфигурацию реактора из config.yml.
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
    private int tempDecayRate;
    private int heatRate;
    private int coolRate;
    private int coreTempMax;
    private int coreTempMin;
    private int coreTempCoolMin;
    private int corePressReduceRate;
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

    private void load() {
        FileConfiguration cfg = Main.getInstance().getConfig();

        enabled = cfg.getBoolean("reactor.enabled", true);
        tempDecayRate = cfg.getInt("reactor.temp_decay_rate", 1);
        heatRate = cfg.getInt("reactor.heat_rate", 3);
        coolRate = cfg.getInt("reactor.cool_rate", 3);
        coreTempMax = cfg.getInt("reactor.core_temp_max", 6000);
        coreTempMin = cfg.getInt("reactor.core_temp_min", -272);
        coreTempCoolMin = cfg.getInt("reactor.core_temp_cool_min", -270);
        corePressReduceRate = cfg.getInt("reactor.core_press_reduce_rate", 1);
        caseTempHeatRate = cfg.getInt("reactor.case_temp_heat_rate", 2);
        caseTempMax = cfg.getInt("reactor.case_temp_max", 8000);
        caseTempCoolRate = cfg.getInt("reactor.case_temp_cool_rate", 2);
        caseTempCoolMin = cfg.getInt("reactor.case_temp_cool_min", -271);
        caseTempDecayRate = cfg.getInt("reactor.case_temp_decay_rate", 1);
        casePressHeatRate = cfg.getInt("reactor.case_press_heat_rate", 4);
        casePressMax = cfg.getInt("reactor.case_press_max", 10000);
        casePressDecayRate = cfg.getInt("reactor.case_press_decay_rate", 1);
        shIntDecayTempThreshold = cfg.getInt("reactor.shell_integrity_decay_temp", 5000);
        shellIntDecayRate = cfg.getInt("reactor.shell_int_decay_rate", 1);
        shellIntRecoveryTempMax = cfg.getInt("reactor.shell_int_recovery_temp_max", 4999);
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
    }

    // =========================
    // GETTERS
    // =========================
    public boolean isEnabled() { return enabled; }
    public int getTempDecayRate() { return tempDecayRate; }
    public int getHeatRate() { return heatRate; }
    public int getCoolRate() { return coolRate; }
    public int getCoreTempMax() { return coreTempMax; }
    public int getCoreTempMin() { return coreTempMin; }
    public int getCoreTempCoolMin() { return coreTempCoolMin; }
    public int getCorePressReduceRate() { return corePressReduceRate; }
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
}
