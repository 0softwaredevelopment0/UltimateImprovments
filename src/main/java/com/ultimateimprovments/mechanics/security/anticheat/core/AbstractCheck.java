package com.ultimateimprovments.mechanics.security.anticheat.core;

import com.ultimateimprovments.mechanics.security.anticheat.AntiCheatManager;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

/**
 * Base class for all anti-cheat checks.
 * <p>
 * Every check extends this class and implements its detection logic.
 * A check can be a Listener (event-based) or be invoked from AntiCheatManager.
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
     * Called on initialization — config loading, listener registration.
     */
    public abstract void onInit();

    /**
     * Called when the config is reloaded.
     */
    public abstract void onReload();

    // =========================
    // CONFIG LOADING
    // =========================

    public void loadConfig() {
        var cfg = com.ultimateimprovments.core.Main.getInstance().getConfig();
        enabled = cfg.getBoolean(configPath + ".enabled", true);
        maxVl = cfg.getDouble(configPath + ".max_vl", 10.0);
        vlDecay = cfg.getDouble(configPath + ".vl_decay", 0.5);
    }

    protected String getConfigString(String key, String def) {
        return com.ultimateimprovments.core.Main.getInstance().getConfig().getString(configPath + "." + key, def);
    }

    protected boolean getConfigBoolean(String key, boolean def) {
        return com.ultimateimprovments.core.Main.getInstance().getConfig().getBoolean(configPath + "." + key, def);
    }

    protected double getConfigDouble(String key, double def) {
        return com.ultimateimprovments.core.Main.getInstance().getConfig().getDouble(configPath + "." + key, def);
    }

    protected int getConfigInt(String key, int def) {
        return com.ultimateimprovments.core.Main.getInstance().getConfig().getInt(configPath + "." + key, def);
    }

    // =========================
    // EXEMPTION CHECK
    // =========================

    /**
     * Checks whether the player is exempt from this check.
     */
    protected boolean isExempted(Player player) {
        return ExemptionManager.getInstance().isExempted(player, name);
    }

    // =========================
    // VL MANAGEMENT
    // =========================

    /**
     * Adds VL and returns the result.
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
