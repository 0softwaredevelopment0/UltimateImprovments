package com.ultimateimprovments.mechanics.features.player;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * ⚔️ TrimEffectsManager — config-driven potion effects for worn armor TRIMS.
 * <p>
 * The {@code armor_trim_effects} config section holds any number of <b>units</b>.
 * Each unit matches pieces by their TRIM MATERIAL — the ingot/crystal used in
 * the smithing table (amethyst, copper, diamond, emerald, gold, iron, lapis,
 * netherite, quartz, redstone, resin) which also defines the trim COLOR. The
 * trim PATTERN (the shape: sentry, dune, coast, ...) is irrelevant:
 * <pre>
 * armor_trim_effects:
 *   enabled: true
 *   units:
 *     fire_resistance_netherite_trim:
 *       materials: [netherite]    # trim materials to match
 *       count_rule: EXACT         # EXACT: level = number of matching pieces; MIN: activates at `count`
 *       count: 1                  # only for MIN (1-4); ignored for EXACT
 *       effect: fire_resistance
 *       amplifier: 0              # strength at level 1 (0 = effect level I)
 *       duration_ticks: 120
 *       interval_ticks: 100
 *       particles: true
 *       ambient: false
 *       icon: true
 * </pre>
 * <p>
 * <b>Level scaling:</b> under the EXACT rule the effect LEVEL equals the number
 * of worn pieces carrying a matching trim material (1 piece → amplifier 0,
 * 2 pieces → amplifier 1, ..., 4 pieces → amplifier 3), on top of the unit's
 * {@code amplifier} base. Under the MIN rule the effect activates with its base
 * amplifier once {@code count} pieces match and does not grow further.
 * <p>
 * Every second (via {@link TrimEffectsTask}) each online player is scanned:
 * the 4 armor slots are read, pieces are grouped by trim material, and every
 * unit whose rule currently holds (re-)applies its effect with
 * {@code duration_ticks}. When the rule stops holding the effect simply
 * expires naturally (no forceful removal — several units may grant the same
 * effect type, so the manager never strips what another unit has given).
 * Materials and effects are resolved through the Paper registries
 * ({@link Registry#TRIM_MATERIAL}, {@link Registry#POTION_EFFECT_TYPE}), so
 * custom datapack-added trim materials work in the config too. Malformed
 * units are logged and skipped — one broken unit never kills the feature.
 */
public final class TrimEffectsManager {

    /** Count rule: level grows with matching pieces (EXACT) or a fixed activation threshold (MIN). */
    public enum CountRule { EXACT, MIN }

    /** One configured unit: trim materials + effect + schedule. */
    public static final class Unit {
        final String id;
        final List<TrimMaterial> materials;
        final CountRule rule;
        final int minCount;        // only meaningful for MIN
        final PotionEffectType effect;
        final int amplifier;       // 0-based, at level 1
        final int durationTicks;
        final int intervalTicks;
        final boolean particles;
        final boolean ambient;
        final boolean icon;

        Unit(String id, List<TrimMaterial> materials, CountRule rule, int minCount,
             PotionEffectType effect, int amplifier, int durationTicks,
             int intervalTicks, boolean particles, boolean ambient, boolean icon) {
            this.id = id;
            this.materials = materials;
            this.rule = rule;
            this.minCount = minCount;
            this.effect = effect;
            this.amplifier = amplifier;
            this.durationTicks = durationTicks;
            this.intervalTicks = intervalTicks;
            this.particles = particles;
            this.ambient = ambient;
            this.icon = icon;
        }
    }

    /** Per-player schedule bookkeeping for one applied unit. */
    private record AppliedUnit(int ticksLeft) {}

    private static TrimEffectsManager instance;

    private boolean enabled;
    private final List<Unit> units = new ArrayList<>();

    /** uuid → (unit id → ticks left until this unit re-applies its effect). */
    private final Map<UUID, Map<String, AppliedUnit>> schedules = new HashMap<>();

    private TrimEffectsManager() {}

    // =========================
    // LIFECYCLE
    // =========================

    public static void init() {
        instance = new TrimEffectsManager();
        instance.loadConfig();
        ConsoleLogger.info("[TrimEffects] Initialized — " + instance.units.size()
                + " unit(s), enabled=" + instance.enabled);
    }

    public static void reloadConfig() {
        if (instance == null) {
            init();
            return;
        }
        // Drop all schedules: units may have changed completely.
        instance.schedules.clear();
        instance.loadConfig();
    }

    public static void shutdown() {
        if (instance == null) return;
        instance.schedules.clear();
        instance.units.clear();
        instance = null;
    }

    public static TrimEffectsManager getInstance() {
        return instance;
    }

    public boolean isEnabled() {
        return enabled;
    }

    // =========================
    // CONFIG
    // =========================

    private void loadConfig() {
        units.clear();
        enabled = false;

        var cfg = Main.getInstance().getConfig().getConfigurationSection("armor_trim_effects");
        if (cfg == null) {
            ConsoleLogger.warn("[TrimEffects] No armor_trim_effects section in config — feature idle.");
            return;
        }
        enabled = cfg.getBoolean("enabled", true);

        ConfigurationSection unitsSection = cfg.getConfigurationSection("units");
        if (unitsSection == null || unitsSection.getKeys(false).isEmpty()) {
            ConsoleLogger.warn("[TrimEffects] armor_trim_effects.units is empty — nothing to grant.");
            return;
        }

        int skipped = 0;
        for (String id : unitsSection.getKeys(false)) {
            ConfigurationSection u = unitsSection.getConfigurationSection(id);
            if (u == null) { skipped++; continue; }

            // --- materials (trim materials, resolved through the registry) ---
            List<TrimMaterial> materials = new ArrayList<>();
            for (String raw : u.getStringList("materials")) {
                TrimMaterial material = resolveTrimMaterial(raw.trim());
                if (material == null) {
                    ConsoleLogger.warn("[TrimEffects] Unit '" + id + "': unknown trim material '"
                            + raw + "' — skipped.");
                    continue;
                }
                materials.add(material);
            }
            if (materials.isEmpty()) {
                ConsoleLogger.warn("[TrimEffects] Unit '" + id + "': no valid materials — skipped.");
                skipped++;
                continue;
            }

            // --- count rule ---
            String ruleName = u.getString("count_rule", "EXACT").trim().toUpperCase(Locale.ROOT);
            final CountRule rule;
            final int minCount;
            switch (ruleName) {
                case "EXACT" -> {
                    rule = CountRule.EXACT;
                    minCount = 1; // level always grows with matches
                }
                case "MIN" -> {
                    rule = CountRule.MIN;
                    minCount = Math.max(1, Math.min(4, u.getInt("count", 1)));
                }
                default -> {
                    ConsoleLogger.warn("[TrimEffects] Unit '" + id + "': unknown count_rule '" + ruleName
                            + "' (use EXACT or MIN) — skipped.");
                    skipped++;
                    continue;
                }
            }

            // --- effect ---
            String effectName = u.getString("effect", "").trim().toLowerCase(Locale.ROOT);
            PotionEffectType effect = resolveEffect(effectName);
            if (effect == null) {
                ConsoleLogger.warn("[TrimEffects] Unit '" + id + "': unknown effect '" + effectName + "' — skipped.");
                skipped++;
                continue;
            }

            int amplifier = Math.max(0, u.getInt("amplifier", 0));
            int durationTicks = Math.max(1, u.getInt("duration_ticks", 120));
            int intervalTicks = Math.max(20, u.getInt("interval_ticks", 100));
            boolean particles = u.getBoolean("particles", true);
            boolean ambient = u.getBoolean("ambient", false);
            boolean icon = u.getBoolean("icon", true);

            units.add(new Unit(id, materials, rule, minCount, effect,
                    amplifier, durationTicks, intervalTicks, particles, ambient, icon));
        }

        ConsoleLogger.info("[TrimEffects] Config loaded: " + units.size() + " unit(s) valid, "
                + skipped + " skipped.");
    }

    /** Resolves a trim material by key name (e.g. "netherite") via the registry. */
    private static TrimMaterial resolveTrimMaterial(String name) {
        if (name.isEmpty()) return null;
        try {
            for (TrimMaterial material : Registry.TRIM_MATERIAL) {
                if (material.getKey().getKey().equalsIgnoreCase(name)) return material;
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[TrimEffects] Trim material registry lookup failed: " + e.getMessage());
        }
        return null;
    }

    /** Resolves a potion effect by key name (e.g. "fire_resistance") via the registry. */
    private static PotionEffectType resolveEffect(String name) {
        if (name.isEmpty()) return null;
        try {
            for (PotionEffectType type : Registry.POTION_EFFECT_TYPE) {
                if (type.getKey().getKey().equalsIgnoreCase(name)) return type;
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[TrimEffects] Effect registry lookup failed: " + e.getMessage());
        }
        return null;
    }

    // =========================
    // TICK (called every 20 ticks from TrimEffectsTask)
    // =========================

    /**
     * Scans all online players. A unit whose rule holds (re-)applies its
     * effect every {@code intervalTicks}; a unit whose rule no longer holds
     * is dropped from the schedule (its effect expires on its own).
     */
    public void tick() {
        if (!enabled || units.isEmpty()) return;

        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (player.isDead() || player.getHealth() <= 0) continue;
            if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;

            Map<String, AppliedUnit> playerSchedule = schedules.computeIfAbsent(
                    player.getUniqueId(), k -> new LinkedHashMap<>());

            // Count matching trim-material pieces ONCE per player.
            Map<TrimMaterial, Integer> materialCounts = countTrimmedPieces(player);

            for (Unit unit : units) {
                int matching = 0;
                for (TrimMaterial material : unit.materials) {
                    matching += materialCounts.getOrDefault(material, 0);
                }

                boolean holds;
                int levelAmplifier;
                if (unit.rule == CountRule.EXACT) {
                    holds = matching >= 1;
                    // Level = number of matching pieces (1 piece → base, 4 pieces → base + 3).
                    levelAmplifier = unit.amplifier + (matching - 1);
                } else {
                    holds = matching >= unit.minCount;
                    levelAmplifier = unit.amplifier;
                }

                if (holds) {
                    AppliedUnit pending = playerSchedule.get(unit.id);
                    if (pending == null || pending.ticksLeft() <= 0) {
                        applyEffect(player, unit, levelAmplifier);
                        playerSchedule.put(unit.id, new AppliedUnit(unit.intervalTicks));
                    } else {
                        playerSchedule.put(unit.id, new AppliedUnit(pending.ticksLeft() - 1));
                    }
                } else {
                    // Rule stopped holding — let the effect expire naturally.
                    playerSchedule.remove(unit.id);
                }
            }
        }

        // Drop schedules of players that are gone.
        schedules.keySet().removeIf(uuid -> org.bukkit.Bukkit.getPlayer(uuid) == null);
    }

    /** Counts worn pieces (4 armor slots) grouped by their trim material. */
    private static Map<TrimMaterial, Integer> countTrimmedPieces(Player player) {
        Map<TrimMaterial, Integer> counts = new HashMap<>();
        for (ItemStack piece : new ItemStack[]{
                player.getInventory().getHelmet(),
                player.getInventory().getChestplate(),
                player.getInventory().getLeggings(),
                player.getInventory().getBoots()}) {
            TrimMaterial material = trimMaterialOf(piece);
            if (material != null) {
                counts.merge(material, 1, Integer::sum);
            }
        }
        return counts;
    }

    /** The trim material of the piece, or null when the item is not trimmed armor. */
    private static TrimMaterial trimMaterialOf(ItemStack piece) {
        if (piece == null || piece.getType().isAir()) return null;
        ItemMeta meta = piece.getItemMeta();
        if (!(meta instanceof org.bukkit.inventory.meta.ArmorMeta armorMeta)) return null;
        if (!armorMeta.hasTrim()) return null;
        ArmorTrim trim = armorMeta.getTrim();
        return trim == null ? null : trim.getMaterial();
    }

    private void applyEffect(Player player, Unit unit, int levelAmplifier) {
        PotionEffect current = player.getPotionEffect(unit.effect);
        // Do not downgrade: skip if a stronger or equal amplifier is already active
        // for at least as long as this application would last.
        if (current != null
                && current.getAmplifier() >= levelAmplifier
                && current.getDuration() != -1
                && current.getDuration() >= Math.min(unit.durationTicks, unit.intervalTicks)) {
            return;
        }
        player.addPotionEffect(new PotionEffect(unit.effect, unit.durationTicks,
                levelAmplifier, unit.ambient, unit.particles, unit.icon));
    }

    // =========================
    // EVENTS
    // =========================

    public void onQuit(UUID uuid) {
        schedules.remove(uuid);
    }
}
