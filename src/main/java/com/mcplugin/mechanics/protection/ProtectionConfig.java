package com.mcplugin.mechanics.protection;

import com.mcplugin.core.Main;
import com.mcplugin.config.MessagesManager;

/**
 * Чтение конфигурации {@code protection.*} из config.yml.
 * <p>
 * Все параметры «Блока защиты» собраны здесь для единой точки доступа.
 * Значения по умолчанию подобраны так, чтобы система работала
 * «из коробки» без кропотливой настройки.
 */
public class ProtectionConfig {

    private ProtectionConfig() {}

    // =========================
    // BOOLEAN CHECKS
    // =========================
    public static boolean isEnabled() {
        return getBool("protection.enabled", true);
    }

    /** Материал, используемый как «визуальный» блок защиты. Mine placement uses this material. */
    public static String getBlockMaterial() {
        return getString("protection.block.material", "LODESTONE");
    }

    /** Разрешать ли крафт в обычном верстаке (по умолчанию false — рецепт виден, но не крафтится). */
    public static boolean isWorkbenchCraftAllowed() {
        return getBool("protection.crafting.workbench_allowed", false);
    }

    /** Разрешать ли крафт в Crafter (Paper 1.21+ поварские мульти-блоки). */
    public static boolean isCrafterCraftAllowed() {
        return getBool("protection.crafting.crafter_allowed", true);
    }

    /** Выдавать ли блок защиты админу-оператору через /mp protection give <player>. */
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

    /** Стоимость в очках первого улучшения радиуса. Далее стоимость умножается на 2 за клик. */
    public static int getRadiusUpgradeBaseCost() {
        return getInt("protection.points.radius_upgrade_base_cost", 1);
    }

    /** Стоимость в очках первого ремонта целостности. Далее стоимость умножается на 2 за клик. */
    public static int getRepairBaseCost() {
        return getInt("protection.points.repair_base_cost", 1);
    }

    /** Стартовая целостность блока при первой установке (проценты, 0..100). */
    public static double getStartingIntegrity() {
        return getDouble("protection.integrity.starting_value", 100.0);
    }

    /** Процент целостности, отнимаемый за попытку сломать защищённый блок. */
    public static double getIntegrityLossPerBreakAttempt() {
        return getDouble("protection.integrity.loss_per_break_attempt", 0.1);
    }

    /** Процент целостности, отнимаемый за каждый блок, пытавшийся взорваться внутри радиуса. */
    public static double getIntegrityLossPerExplosionBlock() {
        return getDouble("protection.integrity.loss_per_explosion_block", 0.1);
    }

    /** Множитель количества очков от сжигательного предмета (furnace burn ticks × multiplier). */
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
