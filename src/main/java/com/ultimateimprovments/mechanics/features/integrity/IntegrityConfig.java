package com.ultimateimprovments.mechanics.features.integrity;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.config.MessagesManager;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashSet;
import java.util.Set;

/**
 * Загружает и предоставляет конфигурацию системы целостности из config.yml.
 * <p>
 * Выделена из IntegrityManager для уменьшения размера класса.
 */
public class IntegrityConfig {

    // =========================
    // Версия системы целостности (для детекта миграции PDC)
    // =========================
    public static final int INTEGRITY_VERSION = 3;

    // =========================
    // НАСТРОЙКИ
    // =========================
    private boolean enabled = true;
    private int intervalTicks = 10;
    private double costMultiplier = 1.0;

    // HEX градиент
    private int gradientRedHigh = 0x00;
    private int gradientGreenHigh = 0x66;
    private int gradientBlueHigh = 0x00;
    private int gradientRedLow = 0x99;
    private int gradientGreenLow = 0x00;
    private int gradientBlueLow = 0x00;

    private String loreText = "§fЦелостность:";
    private String bareLorePrefix = "Целостность:";

    // Поведение при поломке
    private boolean breakPlaySound = true;
    private boolean breakSendMessage = true;
    private String breakMessage = "<dark_red>❌</dark_red> <red>Ваш предмет</red> <white>%item%</white> <red>сломался!</red>";
    private String breakSoundName = "ENTITY_ITEM_BREAK";
    private float breakSoundVolume = 1.0f;
    private float breakSoundPitch = 1.0f;

    // Логирование
    private boolean logInit = false;
    private boolean logBreak = true;
    private boolean logErrors = false;

    // Фильтры
    private Set<String> blacklist = new HashSet<>();
    private Set<String> whitelist = new HashSet<>();

    // Ремонт
    private boolean anvilRepairEnabled = true;
    private double anvilRepairMultiplier = 0.25;
    private boolean anvilCombineEnabled = true;
    private double anvilCombineBonus = 0.1;

    // XP + Mending (Починка)
    private boolean mendingXpEnabled = true;
    private double mendingXpMultiplier = 0.5;

    // Крафт / объединение
    private boolean combineEnabled = true;
    private double combineLossRate = 0.0;

    // Сообщения
    private String anvilRepairMessage = "<green>🔧</green> <white>Целостность восстановлена до</white> <yellow>%current%%</yellow><white>!</white>";
    private String anvilCombineMessage = "<green>🔗</green> <white>Предметы объединены! Целостность:</white> <yellow>%current%%</yellow><white></white>";
    private String mendingMessage = "<aqua>✨</aqua> <white>Починка восстановила</white> <yellow>%amount%</yellow> <white>целостности!</white>";

    public void load() {
        var cfg = Main.getInstance().getConfig().getConfigurationSection("features.integrity");
        if (cfg == null) {
            enabled = false;
            return;
        }

        enabled = cfg.getBoolean("enabled", true);
        intervalTicks = cfg.getInt("interval_ticks", 10);
        costMultiplier = cfg.getDouble("cost_multiplier", 1.0);

        var gradient = cfg.getConfigurationSection("gradient");
        if (gradient != null) {
            int[] high = parseHexColor(gradient.getString("high_color", "#006600"));
            int[] low = parseHexColor(gradient.getString("low_color", "#990000"));
            if (high != null) { gradientRedHigh = high[0]; gradientGreenHigh = high[1]; gradientBlueHigh = high[2]; }
            if (low != null) { gradientRedLow = low[0]; gradientGreenLow = low[1]; gradientBlueLow = low[2]; }
        }

        loreText = cfg.getString("lore_text", "§7Целостность:");
        bareLorePrefix = loreText.replaceAll("§.", "").trim();

        var onBreak = cfg.getConfigurationSection("on_break");
        if (onBreak != null) {
            breakPlaySound = onBreak.getBoolean("play_sound", true);
            breakSendMessage = onBreak.getBoolean("send_message", true);
            breakMessage = onBreak.getString("message", "<dark_red>❌</dark_red> <red>Ваш предмет</red> <white>%item%</white> <red>сломался!</red>");
            breakSoundName = onBreak.getString("sound", "ENTITY_ITEM_BREAK");
            breakSoundVolume = (float) onBreak.getDouble("sound_volume", 1.0);
            breakSoundPitch = (float) onBreak.getDouble("sound_pitch", 1.0);
        }

        var logging = cfg.getConfigurationSection("logging");
        if (logging != null) {
            logInit = logging.getBoolean("log_init", false);
            logBreak = logging.getBoolean("log_break", true);
            logErrors = logging.getBoolean("log_errors", false);
        }

        blacklist = new HashSet<>(cfg.getStringList("blacklist"));
        whitelist = new HashSet<>(cfg.getStringList("whitelist"));

        var anvil = cfg.getConfigurationSection("anvil_repair");
        if (anvil != null) {
            anvilRepairEnabled = anvil.getBoolean("enabled", true);
            anvilRepairMultiplier = anvil.getDouble("integrity_multiplier", 0.25);
            anvilCombineEnabled = anvil.getBoolean("combine_enabled", true);
            anvilCombineBonus = anvil.getDouble("combine_bonus", 0.1);
            anvilRepairMessage = MessagesManager.getString("features.integrity.anvil_repair.repair_message", "<green>🔧</green> <white>Целостность восстановлена до</white> <yellow>%current%%</yellow><white>!</white>");
            anvilCombineMessage = MessagesManager.getString("features.integrity.anvil_repair.combine_message", "<green>🔗</green> <white>Предметы объединены! Целостность:</white> <yellow>%current%%</yellow><white></white>");
        }

        // ===== XP + MENDING (ПОЧИНКА) =====
        var mending = cfg.getConfigurationSection("mending_xp");
        if (mending != null) {
            mendingXpEnabled = mending.getBoolean("enabled", true);
            mendingXpMultiplier = mending.getDouble("integrity_multiplier", 0.5);
            mendingMessage = MessagesManager.getString("features.integrity.mending_xp.message", "<aqua>✨</aqua> <white>Починка восстановила</white> <yellow>%amount%%</yellow> <white>целостности!</white>");
        } else {
            // Fallback: старый ключ silk_touch_xp (для обратной совместимости)
            var stxp = cfg.getConfigurationSection("silk_touch_xp");
            if (stxp != null) {
                mendingXpEnabled = stxp.getBoolean("enabled", true);
                mendingXpMultiplier = stxp.getDouble("integrity_multiplier", 0.5);
                mendingMessage = MessagesManager.getString("features.integrity.silk_touch_message", "<aqua>✨</aqua> <white>Починка восстановила</white> <yellow>%amount%%</yellow> <white>целостности!</white>");
            }
        }

        var combine = cfg.getConfigurationSection("combine");
        if (combine != null) {
            combineEnabled = combine.getBoolean("enabled", true);
            combineLossRate = combine.getDouble("loss_rate", 0.0);
        }
    }

    // =========================
    // GETTERS
    // =========================
    public boolean isEnabled() { return enabled; }
    public int getIntervalTicks() { return intervalTicks; }
    public double getCostMultiplier() { return costMultiplier; }
    public String getLoreText() { return loreText; }
    public String getBareLorePrefix() { return bareLorePrefix; }
    public boolean isBreakPlaySound() { return breakPlaySound; }
    public boolean isBreakSendMessage() { return breakSendMessage; }
    public String getBreakMessage() { return breakMessage; }
    public String getBreakSoundName() { return breakSoundName; }
    public float getBreakSoundVolume() { return breakSoundVolume; }
    public float getBreakSoundPitch() { return breakSoundPitch; }
    public boolean isLogInit() { return logInit; }
    public boolean isLogBreak() { return logBreak; }
    public boolean isLogErrors() { return logErrors; }
    public Set<String> getBlacklist() { return blacklist; }
    public Set<String> getWhitelist() { return whitelist; }
    public boolean isAnvilRepairEnabled() { return anvilRepairEnabled; }
    public boolean isAnvilCombineEnabled() { return anvilCombineEnabled; }
    public boolean isSilkTouchXpEnabled() { return mendingXpEnabled; }
    public boolean isCombineEnabled() { return combineEnabled; }
    public double getAnvilRepairMultiplier() { return anvilRepairMultiplier; }
    public double getAnvilCombineBonus() { return anvilCombineBonus; }
    public double getSilkTouchXpMultiplier() { return mendingXpMultiplier; }
    public double getCombineLossRate() { return combineLossRate; }
    public String getAnvilRepairMessage() { return anvilRepairMessage; }
    public String getAnvilCombineMessage() { return anvilCombineMessage; }
    public String getSilkTouchMessage() { return mendingMessage; }

    public int getGradientRedHigh() { return gradientRedHigh; }
    public int getGradientGreenHigh() { return gradientGreenHigh; }
    public int getGradientBlueHigh() { return gradientBlueHigh; }
    public int getGradientRedLow() { return gradientRedLow; }
    public int getGradientGreenLow() { return gradientGreenLow; }
    public int getGradientBlueLow() { return gradientBlueLow; }

    // =========================
    // УТИЛИТЫ
    // =========================
    public static int[] parseHexColor(String hex) {
        try {
            String clean = hex.replace("#", "").trim();
            if (clean.length() == 6) {
                return new int[]{
                    Integer.parseInt(clean.substring(0, 2), 16),
                    Integer.parseInt(clean.substring(2, 4), 16),
                    Integer.parseInt(clean.substring(4, 6), 16)
                };
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static int getIntegrityVersion() { return INTEGRITY_VERSION; }
}
