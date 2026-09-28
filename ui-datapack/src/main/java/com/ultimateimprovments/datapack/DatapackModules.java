package com.ultimateimprovments.datapack;

import com.ultimateimprovments.module.ModuleManager;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

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

    /** Master toggle: {@code datapack.enabled}. */
    public static final String MASTER_KEY = "datapack.enabled";

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
                    "RepairingEnchantment", "ContainerStealingEnchantment"),
            ADVANCEMENTS, List.of(
                    "BeyondSpace", "BedrockBreak", "Kaboom", "EarthCore", "ServerOverload",
                    "WoodcutterChallenge", "EnderPearlChallenge", "NetheriteKing",
                    "OutOfMemory", "ServerFreeze"));

    private static final Map<String, Boolean> CACHE = new HashMap<>();
    private static boolean masterEnabled = true;

    // =========================
    // INIT
    // =========================

    /**
     * Reads the toggles from the UI-Datapack config. Called once at startup
     * (UIDatapack), before any module registration or datapack install.
     */
    public static void init(JavaPlugin plugin) {
        // Single config lives in UI-Core (Main.getInstance().getConfig()).
        FileConfiguration cfg = com.ultimateimprovments.core.Main.getInstance().getConfig();
        masterEnabled = cfg.getBoolean(MASTER_KEY, true);
        for (String part : ORDER) {
            boolean enabled = cfg.getBoolean(CONFIG_ROOT + "." + part, true);
            CACHE.put(part, enabled);
        }
        ConsoleLogger.info("[Datapack] " + (isMasterEnabled()
                ? "Master: ON | Modules: " + describe()
                : "Master: OFF (datapack.enabled: false) — datapack disabled entirely."));
    }

    /**
     * Whether the whole datapack is enabled ({@code datapack.enabled}).
     * When off, no part is loaded and no bound code module is registered.
     */
    public static boolean isMasterEnabled() {
        return masterEnabled;
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
     * Whether a datapack part is enabled. The master toggle gates everything:
     * with {@code datapack.enabled: false} no part is enabled. Uninitialized /
     * unknown parts default to enabled, so nothing is skipped by accident.
     */
    public static boolean isEnabled(String part) {
        if (!masterEnabled) return false;
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
        if (!masterEnabled) return false;
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

    /**
     * A {@link ModuleManager.DatapackGate} that gates plugin code modules
     * (enchantments, achievement listeners, ...) by the datapack part they are
     * bound to. Installed by UI-Datapack so UI-Other skips the modules of
     * disabled datapack parts.
     */
    public static ModuleManager.DatapackGate gate() {
        return new ModuleManager.DatapackGate() {
            @Override
            public String partForModule(String moduleName) {
                return getPartForModule(moduleName);
            }

            @Override
            public boolean isPartEnabled(String part) {
                return isEnabled(part);
            }
        };
    }
}
