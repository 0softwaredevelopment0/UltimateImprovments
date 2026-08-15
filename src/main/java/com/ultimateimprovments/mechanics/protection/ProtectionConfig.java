package com.ultimateimprovments.mechanics.protection;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.config.MessagesManager;

/**
 * Reads the {@code protection.*} configuration from config.yml.
 * <p>
 * All «Protection Block» parameters are collected here for a single point of access.
 * Default values are chosen so the system works
 * «out of the box» without painstaking configuration.
 */
public class ProtectionConfig {

    private ProtectionConfig() {}

    // =========================
    // BOOLEAN CHECKS
    // =========================
    public static boolean isEnabled() {
        return getBool("protection.enabled", true);
    }

    /** Material used as the "visual" protection block. Mine placement uses this material. */
    public static String getBlockMaterial() {
        return getString("protection.block.material", "LODESTONE");
    }

    /** Whether crafting in a regular workbench is allowed (default false — recipe visible, but not craftable). */
    public static boolean isWorkbenchCraftAllowed() {
        return getBool("protection.crafting.workbench_allowed", false);
    }

    /** Whether crafting in a Crafter is allowed (Paper 1.21+ automatic multi-blocks). */
    public static boolean isCrafterCraftAllowed() {
        return getBool("protection.crafting.crafter_allowed", true);
    }

    /** Whether admin operators can give the protection block via /ui protection give <player>. */
    public static boolean allowAdminGive() {
        return getBool("protection.admin.give_allowed", true);
    }

    // =========================
    // INT / DOUBLE VALUES
    // =========================
    public static int getDefaultRadius() {
        return getInt("protection.radius.default", 5);
    }

    public static int getMaxRadius() {
        return getInt("protection.radius.max", 64);
    }

    public static int getMinRadius() {
        return getInt("protection.radius.min", 1);
    }

    /** Point cost of the first radius upgrade. Afterwards the cost doubles per click. */
    public static int getRadiusUpgradeBaseCost() {
        return getInt("protection.points.radius_upgrade_base_cost", 1);
    }

    /** Point cost of the first integrity repair. Afterwards the cost doubles per click. */
    public static int getRepairBaseCost() {
        return getInt("protection.points.repair_base_cost", 1);
    }

    /** Initial integrity of the block on first placement (percent, 0..100). */
    public static double getStartingIntegrity() {
        return getDouble("protection.integrity.starting_value", 100.0);
    }

    /** Integrity percent deducted for an attempt to break a protected block. */
    public static double getIntegrityLossPerBreakAttempt() {
        return getDouble("protection.integrity.loss_per_break_attempt", 0.1);
    }

    /** Integrity percent deducted for each block that tried to explode within the radius. */
    public static double getIntegrityLossPerExplosionBlock() {
        return getDouble("protection.integrity.loss_per_explosion_block", 0.1);
    }

    /** Multiplier of points from a fuel item (furnace burn ticks × multiplier). */
    public static double getFuelPointsMultiplier() {
        return getDouble("protection.points.fuel_multiplier", 0.1);
    }

    // =========================
    // MESSAGE GETTERS (MiniMessage)
    // =========================
    public static String getMessage(String path, String def) {
        return MessagesManager.getString("protection." + path, def);
    }

    // =========================
    // RAW GETTERS
    // =========================
    private static int getInt(String path, int def) {
        try {
            return Main.getInstance().getConfig().getInt(path, def);
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean getBool(String path, boolean def) {
        try {
            return Main.getInstance().getConfig().getBoolean(path, def);
        } catch (Exception e) {
            return def;
        }
    }

    private static String getString(String path, String def) {
        try {
            return Main.getInstance().getConfig().getString(path, def);
        } catch (Exception e) {
            return def;
        }
    }

    private static double getDouble(String path, double def) {
        try {
            return Main.getInstance().getConfig().getDouble(path, def);
        } catch (Exception e) {
            return def;
        }
    }
}
