package com.ultimateimprovments.mechanics.protection;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.config.MessagesManager;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads the {@code protection.*} configuration from the composite config
 * (stored in {@code configs/UI-Protection.toml}, section {@code [protection]}).
 * <p>
 * All «Protection Block» parameters are collected here for a single point of access.
 * Default values are chosen so the system works
 * «out of the box» without painstaking configuration.
 * <p>
 * Every getter is defensive: a broken/absent config never throws, it falls back
 * to the built-in default (so a half-written TOML can't disable the feature).
 */
public class ProtectionConfig {

    /**
     * Container blocks blocked by RMB inside a protected zone.
     * <p>
     * Doors, buttons, levers, crafting tables, anvils, beds etc. are NOT blocked —
     * only blocks that own an inventory (i.e. that can steal/store items).
     * Override with {@code protection.interact.blocked_materials} in the TOML.
     */
    private static final Set<Material> DEFAULT_CONTAINERS;
    static {
        Set<Material> m = EnumSet.noneOf(Material.class);
        m.add(Material.CHEST);
        m.add(Material.TRAPPED_CHEST);
        m.add(Material.ENDER_CHEST);
        m.add(Material.BARREL);
        m.add(Material.FURNACE);
        m.add(Material.BLAST_FURNACE);
        m.add(Material.SMOKER);
        m.add(Material.HOPPER);
        m.add(Material.DROPPER);
        m.add(Material.DISPENSER);
        m.add(Material.BREWING_STAND);
        m.add(Material.CRAFTER);
        m.add(Material.DECORATED_POT);
        m.add(Material.CHISELED_BOOKSHELF);
        m.add(Material.LECTERN);
        m.add(Material.JUKEBOX);
        m.add(Material.VAULT);
        // Every coloured shulker box (16 colours + plain one).
        for (Material candidate : Material.values()) {
            String name = candidate.name();
            if (name.equals("SHULKER_BOX") || name.endsWith("_SHULKER_BOX")) {
                m.add(candidate);
            }
        }
        DEFAULT_CONTAINERS = Collections.unmodifiableSet(m);
    }

    private ProtectionConfig() {}

    // =========================
    // BOOLEAN CHECKS
    // =========================
    /** Master switch: {@code protection.enabled}. When false the whole block system is off. */
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

    /** Whether pistons are forbidden from moving blocks into / out of a protected zone. */
    public static boolean isPistonProtectionEnabled() {
        return getBool("protection.pistons.enabled", true);
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

    /** Integrity percent deducted for one refused action (break / place / interact / piston). */
    public static double getIntegrityLossPerBreakAttempt() {
        return getDouble("protection.integrity.loss_per_break_attempt", 0.1);
    }

    /** Integrity percent deducted for each block the explosion tried to destroy. */
    public static double getIntegrityLossPerExplosionBlock() {
        return getDouble("protection.integrity.loss_per_explosion_block", 0.1);
    }

    /** Multiplier of points from a fuel item (furnace burn ticks × multiplier). */
    public static double getFuelPointsMultiplier() {
        return getDouble("protection.points.fuel_multiplier", 0.1);
    }

    // =========================
    // INTERACTION RULES
    // =========================
    /** Cached result of {@link #configuredInteractionMaterials()} keyed by the raw config list. */
    private static volatile List<String> cachedRawMaterials;
    private static volatile Set<Material> cachedMaterials;

    /**
     * Whether RMB on this material inside a protected zone must be cancelled.
     * <p>
     * Uses {@code protection.interact.blocked_materials} when it is not empty,
     * otherwise the built-in container list (chests, barrels, shulkers, furnaces,
     * hoppers, droppers, dispensers, brewing stands, crafters, pots, bookshelves...).
     * <p>
     * Called on EVERY right-click in the world, so the material set is cached and
     * only rebuilt when the configured list actually changes.
     */
    public static boolean isInteractionBlocked(Material type) {
        if (type == null) return false;
        Set<Material> configured = interactionMaterials();
        return (configured != null ? configured : DEFAULT_CONTAINERS).contains(type);
    }

    /** Drops the cached interaction list (called on config reload). */
    public static void invalidateCache() {
        cachedRawMaterials = null;
        cachedMaterials = null;
    }

    /** @return the configured container list, or {@code null} when the built-in default applies. */
    private static Set<Material> interactionMaterials() {
        List<String> raw;
        try {
            raw = Main.getInstance().getConfig()
                    .getStringList("protection.interact.blocked_materials");
        } catch (Exception e) {
            return null;
        }
        if (raw == null || raw.isEmpty()) return null;
        List<String> cached = cachedRawMaterials;
        if (cached != null && cached.equals(raw)) return cachedMaterials;
        Set<Material> result = EnumSet.noneOf(Material.class);
        for (String entry : raw) {
            if (entry == null || entry.isBlank()) continue;
            Material mat = matchMaterial(entry);
            if (mat != null) result.add(mat);
        }
        Set<Material> value = result.isEmpty() ? null : result;
        cachedMaterials = value;
        cachedRawMaterials = raw;
        return value;
    }

    /** Lenient material lookup: accepts {@code chest}, {@code CHEST}, {@code minecraft:chest}. */
    private static Material matchMaterial(String raw) {
        String name = raw.trim().toUpperCase(Locale.ROOT);
        int colon = name.indexOf(':');
        if (colon >= 0) name = name.substring(colon + 1);
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** @return true when the given material is the configured protection block material. */
    public static boolean isProtectionMaterial(Material type) {
        if (type == null) return false;
        Material configured = matchMaterial(getBlockMaterial());
        return configured != null && configured == type;
    }

    // =========================
    // MESSAGE GETTERS (MiniMessage)
    // =========================
    public static String getMessage(String path, String def) {
        return MessagesManager.getString("protection." + path, def);
    }

    /** Multi-line message (e.g. the placement broadcast) for the current language. */
    public static List<String> getMessageList(String path, List<String> def) {
        List<String> value = MessagesManager.getStringList("protection." + path,
                def != null ? def : new ArrayList<>());
        return value != null ? value : new ArrayList<>();
    }

    /** Void-protection message (lives in the same addon file, different root section). */
    public static String getVoidMessage(String def) {
        return MessagesManager.getString("void_protection.message", def);
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
