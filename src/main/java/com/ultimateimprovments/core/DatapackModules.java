package com.ultimateimprovments.core;

import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DatapackModules — modular loading of the bundled UI-Datapack.
 * <p>
 * The datapack is a SINGLE pack, but its parts can be enabled/disabled via
 * {@code datapack.modules.*} in config.yml (all parts are enabled by default).
 * Disabled parts are NOT copied into the world when the datapack is installed,
 * and the plugin code modules bound to those parts are not registered either.
 */
public final class DatapackModules {

    private DatapackModules() {}

    /** Config root section: {@code datapack.modules}. */
    public static final String CONFIG_ROOT = "datapack.modules";

    // =========================
    // PARTS
    // =========================

    public static final String ENCHANTMENTS = "enchantments";
    public static final String ADVANCEMENTS = "advancements";
    public static final String CUSTOM_RECIPES = "custom_recipes";
    public static final String VANILLA_RECIPES = "vanilla_recipes";
    public static final String LOOT_TABLES = "loot_tables";
    public static final String WORLDGEN = "worldgen";
    public static final String DIMENSION_LIMITS = "dimension_limits";

    /** Order used for logging. */
    private static final List<String> ORDER = List.of(
            ENCHANTMENTS, ADVANCEMENTS, CUSTOM_RECIPES, VANILLA_RECIPES,
            LOOT_TABLES, WORLDGEN, DIMENSION_LIMITS);

    /**
     * Datapack path prefixes per part (relative to the pack's {@code data/} folder).
     * Everything under a prefix belongs to that part.
     */
    private static final Map<String, List<String>> PATH_PREFIXES = Map.of(
            ENCHANTMENTS, List.of("ui/enchantment/", "ui/tags/item/", "minecraft/tags/enchantment/"),
            ADVANCEMENTS, List.of("ui/advancement/"),
            CUSTOM_RECIPES, List.of("ui/recipe/"),
            VANILLA_RECIPES, List.of("minecraft/recipe/"),
            LOOT_TABLES, List.of("minecraft/loot_table/"),
            WORLDGEN, List.of("minecraft/worldgen/", "minecraft/structure/"),
            DIMENSION_LIMITS, List.of("minecraft/dimension_type/"));

    /**
     * Plugin code modules (ModuleManager names) bound to a datapack part.
     * If the part is disabled, these modules are skipped at registration.
     */
    private static final Map<String, List<String>> CODE_MODULES = Map.of(
            ENCHANTMENTS, List.of(
                    "AOEEnchantment", "AutoSmeltEnchantment", "VeinMinerEnchantment",
                    "TreeCapitatorEnchantment", "FlightEnchantment", "MagnetEnchantment",
                    "IgnitingEnchantment", "LevitationEnchantment", "SelfDestructEnchantment",
                    "DegradationEnchantment", "AttackAoeEnchantment", "ItemStealingEnchantment",
                    "RepairingEnchantment"),
            ADVANCEMENTS, List.of(
                    "BeyondSpace", "BedrockBreak", "Kaboom", "EarthCore", "ServerOverload",
                    "WoodcutterChallenge", "EnderPearlChallenge", "NetheriteKing",
                    "OutOfMemory", "ServerFreeze"));

    private static final Map<String, Boolean> CACHE = new HashMap<>();

    // =========================
    // INIT
    // =========================

    /**
     * Reads the toggles from the config. Called once at startup (PluginStartup),
     * before any module registration or datapack install.
     */
    public static void init(Main plugin) {
        FileConfiguration cfg = plugin.getConfig();
        for (String part : ORDER) {
            boolean enabled = cfg.getBoolean(CONFIG_ROOT + "." + part, true);
            CACHE.put(part, enabled);
        }
        ConsoleLogger.info("[Datapack] Modules: " + describe());
    }

    /** Human-readable state, e.g. "enchantments ✔, advancements ✘, ...". */
    public static String describe() {
        StringBuilder sb = new StringBuilder();
        for (String part : ORDER) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(part).append(isEnabled(part) ? " ✔" : " ✘");
        }
        return sb.toString();
    }

    /**
     * Whether a datapack part is enabled. Uninitialized / unknown parts default
     * to enabled, so nothing is ever skipped by accident.
     */
    public static boolean isEnabled(String part) {
        return CACHE.getOrDefault(part, Boolean.TRUE);
    }

    // =========================
    // PATH / MODULE LOOKUP
    // =========================

    /**
     * Whether a datapack file (path relative to the pack root, e.g.
     * {@code data/ui/enchantment/aoe.json}) should be copied to the world.
     */
    public static boolean isPathEnabled(String relPath) {
        if (relPath == null || !relPath.startsWith("data/")) return true;
        for (Map.Entry<String, List<String>> e : PATH_PREFIXES.entrySet()) {
            for (String prefix : e.getValue()) {
                if (relPath.startsWith("data/" + prefix)) {
                    return isEnabled(e.getKey());
                }
            }
        }
        return true; // unknown paths are always copied
    }

    /**
     * @return the datapack part a plugin module is bound to, or null if none
     */
    public static String getPartForModule(String moduleName) {
        if (moduleName == null) return null;
        for (Map.Entry<String, List<String>> e : CODE_MODULES.entrySet()) {
            if (e.getValue().contains(moduleName)) return e.getKey();
        }
        return null;
    }
}
