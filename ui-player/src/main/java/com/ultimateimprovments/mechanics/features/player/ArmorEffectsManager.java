package com.ultimateimprovments.mechanics.features.player;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
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
 * ⚔️ ArmorEffectsManager — config-driven potion effects for worn armor.
 * <p>
 * The {@code armor_effects} config section holds any number of <b>units</b>.
 * Each unit names one or more armor material FAMILIES (leather, copper,
 * chainmail, iron, golden, diamond, netherite — any {@code *_HELMET /
 * _CHESTPLATE / _LEGGINGS / _BOOTS} family works) and one effect to grant:
 * <pre>
 * armor_effects:
 *   enabled: true
 *   units:
 *     fire_resistance_netherite:
 *       materials: [netherite]
 *       rule: FULL              # FULL = the whole 4-piece set; COUNT = at least `count` pieces
 *       count: 1                # only for COUNT (1-3); ignored for FULL
 *       effect: fire_resistance
 *       amplifier: 0            # 0 = effect level I
 *       duration_ticks: 40      # how long ONE application lasts
 *       check_interval_ticks: 40 # how often the check re-applies (min 20 = 1 s)
 *       particles: true         # show particle effects
 *       ambient: false          # beacon-style translucent swirls
 *       icon: true              # show the effect icon in the HUD
 *     speed_full_iron:
 *       materials: [iron]
 *       rule: COUNT
 *       count: 3
 *       effect: speed
 *       amplifier: 1
 *       duration_ticks: 40
 *       check_interval_ticks: 40
 *       particles: false
 *       ambient: false
 *       icon: true
 * </pre>
 * <p>
 * <b>Duration vs check period:</b> each application lasts {@code duration_ticks};
 * the check re-applies the effect every {@code check_interval_ticks} (whole-second
 * granularity, rounded down — the effect is never checked LESS often than
 * configured). With {@code duration_ticks >= check_interval_ticks} the refresh
 * lands before the previous application expires and the effect stays up
 * continuously; with a shorter duration the effect deliberately turns off
 * between checks. There is no hard minimum for {@code check_interval_ticks},
 * but values below 20 ticks make no sense: the internal heartbeat checks once
 * per second, so smaller values cannot check more often and only waste
 * performance. Defaults: 50 / 40 (duration longer than the 40-tick check interval - no flicker). Legacy units may still use the old
 * {@code interval_ticks} key — it is honored when {@code check_interval_ticks}
 * is absent.
 * <p>
 * The scan itself runs every second (fixed heartbeat): each player's armor is
 * checked for every unit whose whole-second countdown has elapsed. When the
 * rule stops holding the effect simply expires naturally (no forceful removal —
 * several units may grant the same effect type, so the manager never strips
 * what another unit has given).
 * <p>
 * A piece matches a unit's material family when the item material name
 * starts with {@code <family>_} and ends with {@code _HELMET/_CHESTPLATE/
 * _LEGGINGS/_BOOTS} (e.g. {@code NETHERITE_CHESTPLATE} ↔ family
 * {@code netherite}). Unknown effect names or malformed units are logged and
 * skipped — a broken unit never kills the whole feature.
 */
public final class ArmorEffectsManager implements org.bukkit.event.Listener {

    /** One configured unit: material families + effect + schedule. */
    public static final class Unit {
        final String id;
        final List<String> families;
        final boolean fullSetRule;
        final int minCount;
        final PotionEffectType effect;
        final int amplifier;       // 0-based
        final int durationTicks;   // potion effect length per application
        final int checkIntervalTicks; // how often the check re-applies, in ticks
        final boolean particles;
        final boolean ambient;
        final boolean icon;

        Unit(String id, List<String> families, boolean fullSetRule, int minCount,
             PotionEffectType effect, int amplifier, int durationTicks,
             int checkIntervalTicks, boolean particles, boolean ambient, boolean icon) {
            this.id = id;
            this.families = families;
            this.fullSetRule = fullSetRule;
            this.minCount = minCount;
            this.effect = effect;
            this.amplifier = amplifier;
            this.durationTicks = durationTicks;
            this.checkIntervalTicks = checkIntervalTicks;
            this.particles = particles;
            this.ambient = ambient;
            this.icon = icon;
        }
    }

    /** Per-player schedule bookkeeping for one applied unit (counts down in 1 s heartbeats). */
    private record AppliedUnit(Unit unit, int secondsLeft) {}

    private static ArmorEffectsManager instance;

    private boolean enabled;
    private final List<Unit> units = new ArrayList<>();

    /** uuid → (unit id → ticks left until this unit re-applies its effect). */
    private final Map<UUID, Map<String, AppliedUnit>> schedules = new HashMap<>();

    private ArmorEffectsManager() {}

    // =========================
    // LIFECYCLE
    // =========================

    public static void init() {
        instance = new ArmorEffectsManager();
        instance.loadConfig();
        ConsoleLogger.info("[ArmorEffects] Initialized — " + instance.units.size()
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

    public static ArmorEffectsManager getInstance() {
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

        var cfg = Main.getInstance().getConfig().getConfigurationSection("armor_effects");
        if (cfg == null) {
            ConsoleLogger.warn("[ArmorEffects] No armor_effects section in config — feature idle.");
            return;
        }
        enabled = cfg.getBoolean("enabled", true);

        ConfigurationSection unitsSection = cfg.getConfigurationSection("units");
        if (unitsSection == null || unitsSection.getKeys(false).isEmpty()) {
            ConsoleLogger.warn("[ArmorEffects] armor_effects.units is empty — nothing to grant.");
            return;
        }

        int skipped = 0;
        for (String id : unitsSection.getKeys(false)) {
            ConfigurationSection u = unitsSection.getConfigurationSection(id);
            if (u == null) { skipped++; continue; }

            // --- materials ---
            List<String> families = new ArrayList<>();
            for (String raw : u.getStringList("materials")) {
                String family = raw.trim().toLowerCase(Locale.ROOT);
                if (!family.isEmpty()) families.add(family);
            }
            if (families.isEmpty()) {
                ConsoleLogger.warn("[ArmorEffects] Unit '" + id + "': materials list is empty — skipped.");
                skipped++;
                continue;
            }

            // --- rule ---
            String rule = u.getString("rule", "FULL").trim().toUpperCase(Locale.ROOT);
            boolean fullSetRule;
            int minCount;
            if ("FULL".equals(rule)) {
                fullSetRule = true;
                minCount = 4; // count is ignored for FULL
            } else if ("COUNT".equals(rule)) {
                fullSetRule = false;
                minCount = Math.max(1, Math.min(3, u.getInt("count", 1)));
            } else {
                ConsoleLogger.warn("[ArmorEffects] Unit '" + id + "': unknown rule '" + rule
                        + "' (use FULL or COUNT) — skipped.");
                skipped++;
                continue;
            }

            // --- effect ---
            String effectName = u.getString("effect", "").trim().toLowerCase(Locale.ROOT);
            PotionEffectType effect = resolveEffect(effectName);
            if (effect == null) {
                ConsoleLogger.warn("[ArmorEffects] Unit '" + id + "': unknown effect '" + effectName + "' — skipped.");
                skipped++;
                continue;
            }

            int amplifier = Math.max(0, u.getInt("amplifier", 0));
            int durationTicks = Math.max(1, u.getInt("duration_ticks", 50));
            int checkIntervalTicks;
            if (u.isSet("check_interval_ticks")) {
                checkIntervalTicks = Math.max(1, u.getInt("check_interval_ticks", 40));
            } else {
                // legacy key from the pre-rename units
                checkIntervalTicks = Math.max(1, u.getInt("interval_ticks", 40));
            }
            if (durationTicks < checkIntervalTicks) {
                ConsoleLogger.warn("[ArmorEffects] Unit '" + id + "': duration_ticks (" + durationTicks
                        + ") < check_interval_ticks (" + checkIntervalTicks + ") — the effect will turn off between checks.");
            }
            boolean particles = u.getBoolean("particles", true);
            boolean ambient = u.getBoolean("ambient", false);
            boolean icon = u.getBoolean("icon", true);

            units.add(new Unit(id, families, fullSetRule, minCount, effect,
                    amplifier, durationTicks, checkIntervalTicks, particles, ambient, icon));
        }

        ConsoleLogger.info("[ArmorEffects] Config loaded: " + units.size() + " unit(s) valid, "
                + skipped + " skipped.");
    }

    /** Resolves a potion effect by key name (e.g. "fire_resistance") via the registry. */
    private static PotionEffectType resolveEffect(String name) {
        if (name.isEmpty()) return null;
        try {
            for (PotionEffectType type : Registry.POTION_EFFECT_TYPE) {
                if (type.getKey().getKey().equalsIgnoreCase(name)) return type;
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[ArmorEffects] Effect registry lookup failed: " + e.getMessage());
        }
        return null;
    }

    // =========================
    // TICK (called every 20 ticks from ArmorEffectsTask)
    // =========================

    /**
     * Scans all online players. A unit whose rule holds (re-)applies its
     * effect every {@code checkIntervalTicks}; a unit whose rule no longer holds
     * is dropped from the schedule (its effect expires on its own).
     */
    public void tick() {
        if (!enabled || units.isEmpty()) return;

        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (player.isDead() || player.getHealth() <= 0) continue;
            if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;

            Map<String, AppliedUnit> playerSchedule = schedules.computeIfAbsent(
                    player.getUniqueId(), k -> new LinkedHashMap<>());

            for (Unit unit : units) {
                boolean holds = ruleHolds(player, unit);

                if (holds) {
                    AppliedUnit pending = playerSchedule.get(unit.id);
                    if (pending == null || pending.secondsLeft() <= 0) {
                        applyEffect(player, unit);
                        playerSchedule.put(unit.id, new AppliedUnit(unit, secondsPerHeartbeat(unit.checkIntervalTicks)));
                    } else {
                        playerSchedule.put(unit.id, new AppliedUnit(unit, pending.secondsLeft() - 1));
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

    /** True when the unit's rule currently holds for the player. */
    private boolean ruleHolds(Player player, Unit unit) {
        PlayerInventory inv = player.getInventory();
        int matching = 0;
        matching += pieceMatches(inv.getHelmet(), unit) ? 1 : 0;
        matching += pieceMatches(inv.getChestplate(), unit) ? 1 : 0;
        matching += pieceMatches(inv.getLeggings(), unit) ? 1 : 0;
        matching += pieceMatches(inv.getBoots(), unit) ? 1 : 0;

        if (unit.fullSetRule) return matching == 4;
        return matching >= unit.minCount;
    }

    /** True when the armor piece belongs to one of the unit's material families. */
    private static boolean pieceMatches(ItemStack piece, Unit unit) {
        if (piece == null || piece.getType() == Material.AIR) return false;
        String name = piece.getType().name();
        // *_HELMET / *_CHESTPLATE / *_LEGGINGS / *_BOOTS
        int underscore = name.indexOf('_');
        if (underscore <= 0) return false;
        String family = name.substring(0, underscore).toLowerCase(Locale.ROOT);
        return unit.families.contains(family);
    }

    /** Converts a tick period into whole 1-second heartbeats (never below 1). */
    private static int secondsPerHeartbeat(int ticks) {
        return Math.max(1, ticks / 20);
    }

    private void applyEffect(Player player, Unit unit) {
        PotionEffect current = player.getPotionEffect(unit.effect);
        // Do not downgrade: skip if a stronger or equal amplifier is already active
        // for at least as long as this application would last.
        if (current != null
                && current.getAmplifier() >= unit.amplifier
                && current.getDuration() != -1
                && current.getDuration() >= unit.durationTicks) {
            return;
        }
        player.addPotionEffect(new PotionEffect(unit.effect, unit.durationTicks,
                unit.amplifier, unit.ambient, unit.particles, unit.icon));
    }

    // =========================
    // EVENTS
    // =========================

    /**
     * Modern Paper event: fires the moment a player's armor piece changes
     * (equip/unequip via any path — click, shift-click, dispenser, hopper,
     * break). Re-evaluates instantly instead of waiting for the 1s heartbeat,
     * so effects appear/disappear without a visible delay.
     */
    @org.bukkit.event.EventHandler
    public void onArmorChange(com.destroystokyo.paper.event.player.PlayerArmorChangeEvent event) {
        evaluate(event.getPlayer());
    }

    /** Applies (or refreshes) every unit whose rule currently holds for one player. */
    public void evaluate(Player player) {
        if (!enabled || units.isEmpty()) return;
        if (player.isDead() || player.getHealth() <= 0) return;
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;

        Map<String, AppliedUnit> playerSchedule = schedules.computeIfAbsent(
                player.getUniqueId(), k -> new LinkedHashMap<>());
        for (Unit unit : units) {
            if (ruleHolds(player, unit)) {
                applyEffect(player, unit);
                playerSchedule.put(unit.id, new AppliedUnit(unit, secondsPerHeartbeat(unit.checkIntervalTicks)));
            }
        }
    }

    public void onQuit(java.util.UUID uuid) {
        schedules.remove(uuid);
    }
}
