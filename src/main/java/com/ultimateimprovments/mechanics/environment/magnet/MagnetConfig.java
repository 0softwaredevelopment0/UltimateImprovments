package com.ultimateimprovments.mechanics.environment.magnet;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;

/**
 * Конфигурация магнита — все параметры из config.yml (features.magnet).
 * <p>
 * Извлечено из {@link MagnetManager} для уменьшения размера класса.
 */
public final class MagnetConfig {

    private MagnetConfig() {}

    private static boolean enabled = true;
    private static int minRadius = 3;
    private static int maxRadius = 15;
    private static int intervalTicks = 1;
    private static double forceBase = 0.15;
    private static double forceDistanceMultiplier = 0.35;
    private static double forceMax = 0.45;
    private static double forceMaxSpeed = 0.6;
    private static double itemYBoost = 0.05;
    private static double powerExponent = 0.55;
    private static double powerNormalize = 20.0;
    private static String distanceCurveType = "smoothstep";
    private static double distanceMinFactor = 0.0;
    private static int particleCenterMax = 50;
    private static int particleBlocksMax = 30;
    private static int particleCritMax = 25;
    private static int particlePortalMax = 15;

    public static void reloadConfig() {
        var cfg = Main.getInstance().getConfig().getConfigurationSection("features.magnet");
        if (cfg == null) return;

        enabled = cfg.getBoolean("enabled", true);

        var r = cfg.getConfigurationSection("radius");
        if (r != null) {
            minRadius = r.getInt("min", 3);
            maxRadius = r.getInt("max", 15);
        }

        intervalTicks = cfg.getInt("interval_ticks", 1);

        var f = cfg.getConfigurationSection("force");
        if (f != null) {
            forceBase = f.getDouble("base", 0.15);
            forceDistanceMultiplier = f.getDouble("distance_multiplier", 0.35);
            forceMax = f.getDouble("max", 0.45);
            forceMaxSpeed = f.getDouble("max_speed", 0.6);
        }

        itemYBoost = cfg.getDouble("item_y_boost", 0.05);

        var fc = cfg.getConfigurationSection("force_curve");
        if (fc != null) {
            powerExponent = fc.getDouble("power_exponent", 0.55);
            powerNormalize = fc.getDouble("power_normalize", 20.0);
        }

        var dc = cfg.getConfigurationSection("distance_curve");
        if (dc != null) {
            distanceCurveType = dc.getString("type", "smoothstep");
            distanceMinFactor = dc.getDouble("min_factor", 0.0);
        }

        var pc = cfg.getConfigurationSection("particles");
        if (pc != null) {
            particleCenterMax = pc.getInt("center_max", 50);
            particleBlocksMax = pc.getInt("blocks_max", 30);
            particleCritMax = pc.getInt("crit_max", 25);
            particlePortalMax = pc.getInt("portal_max", 15);
        }

        ConsoleLogger.info("[Magnet] Config reloaded.");
    }

    // =========================
    // GETTERS
    // =========================
    public static boolean isEnabled() { return enabled; }
    public static int getMinRadius() { return minRadius; }
    public static int getMaxRadius() { return maxRadius; }
    public static int getIntervalTicks() { return intervalTicks; }
    public static double getForceBase() { return forceBase; }
    public static double getForceDistanceMultiplier() { return forceDistanceMultiplier; }
    public static double getForceMax() { return forceMax; }
    public static double getForceMaxSpeed() { return forceMaxSpeed; }
    public static double getItemYBoost() { return itemYBoost; }
    public static double getPowerExponent() { return powerExponent; }
    public static double getPowerNormalize() { return powerNormalize; }
    public static String getDistanceCurveType() { return distanceCurveType; }
    public static double getDistanceMinFactor() { return distanceMinFactor; }
    public static int getParticleCenterMax() { return particleCenterMax; }
    public static int getParticleBlocksMax() { return particleBlocksMax; }
    public static int getParticleCritMax() { return particleCritMax; }
    public static int getParticlePortalMax() { return particlePortalMax; }
}
