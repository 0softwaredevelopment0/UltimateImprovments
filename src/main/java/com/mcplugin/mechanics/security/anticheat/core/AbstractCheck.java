package com.mcplugin.mechanics.security.anticheat.core;

import com.mcplugin.mechanics.security.anticheat.AntiCheatManager;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

/**
 * Базовый класс для всех проверок античита.
 * <p>
 * Каждая проверка наследует этот класс и реализует логику обнаружения.
 * Проверка может быть как Listener (event-based), так и вызываться из AntiCheatManager.
 */
public abstract class AbstractCheck implements Listener {

    private final String name;
    private final CheckCategory category;
    private final String configPath;
    private boolean enabled = true;
    private double maxVl = 10.0;
    private double vlDecay = 0.5; // VL decay per second

    protected AbstractCheck(String name, CheckCategory category) {
        this.name = name;
        this.category = category;
        this.configPath = "anticheat." + category.name().toLowerCase() + "." + name.toLowerCase().replace("/", "_").replace(" ", "_");
    }

    // =========================
    // ABSTRACT
    // =========================

    /**
     * Вызывается при инициализации — загрузка конфига, регистрация listener'ов.
     */
    public abstract void onInit();

    /**
     * Вызывается при перезагрузке конфига.
     */
    public abstract void onReload();

    // =========================
    // CONFIG LOADING
    // =========================

    public void loadConfig() {
        var cfg = com.mcplugin.core.Main.getInstance().getConfig();
        enabled = cfg.getBoolean(configPath + ".enabled", true);
        maxVl = cfg.getDouble(configPath + ".max_vl", 10.0);
        vlDecay = cfg.getDouble(configPath + ".vl_decay", 0.5);
    }

    protected String getConfigString(String key, String def) {
        return com.mcplugin.core.Main.getInstance().getConfig().getString(configPath + "." + key, def);
    }

    protected boolean getConfigBoolean(String key, boolean def) {
        return com.mcplugin.core.Main.getInstance().getConfig().getBoolean(configPath + "." + key, def);
    }

    protected double getConfigDouble(String key, double def) {
        return com.mcplugin.core.Main.getInstance().getConfig().getDouble(configPath + "." + key, def);
    }

    protected int getConfigInt(String key, int def) {
        return com.mcplugin.core.Main.getInstance().getConfig().getInt(configPath + "." + key, def);
    }

    // =========================
    // EXEMPTION CHECK
    // =========================

    /**
     * Проверяет, освобождён ли игрок от этой проверки.
     */
    protected boolean isExempted(Player player) {
        return ExemptionManager.getInstance().isExempted(player, name);
    }

    // =========================
    // VL MANAGEMENT
    // =========================

    /**
     * Добавляет VL и возвращает результат.
     */
    protected CheckResult flag(Player player, double vl, String message) {
        PlayerData data = AntiCheatManager.getInstance().getPlayerData(player);
        if (data == null) return CheckResult.passed();
        data.addVl(name, vl);
        return CheckResult.flagged(vl, message);
    }

    protected CheckResult pass() {
        return CheckResult.passed();
    }

    // =========================
    // GETTERS
    // =========================

    public String getName() { return name; }
    public CheckCategory getCategory() { return category; }
    public String getConfigPath() { return configPath; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public double getMaxVl() { return maxVl; }
    public double getVlDecay() { return vlDecay; }
}
