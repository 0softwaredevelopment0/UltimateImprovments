package com.ultimateimprovments.mechanics.features.player;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.database.StateStore;
import com.ultimateimprovments.util.AttributeUtil;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * ⚔️ AttributesManager — config-driven attribute units, applied as MODIFIERS
 * (default) or as BASE values.
 * <p>
 * The {@code features.attributes} config section holds any number of <b>units</b>.
 * Unit names are ordinal numbers so the config reads like a list; each unit picks
 * a change type and supplies that type's parameters:
 * <pre>
 * [features.attributes]
 * enabled = true
 *
 * [features.attributes.units."1"]
 * type = "modifier"                            # modifier (default) | base
 * attribute = "minecraft:waypoint_receive_range"
 * target = 8192.0                              # modifier only: desired FINAL value (amount = target - base)
 * # or a raw addition: amount = 2.0
 * operation = "add_number"                     # modifier only: add_number | add_scalar | multiply_scalar_1
 * slot = "any"                                 # modifier only: any|hand|mainhand|offhand|armor|head|chest|legs|feet|body
 * schedule = false                             # false = apply once; true = re-apply every interval_ticks
 * interval_ticks = 200                         # period of the re-application when schedule = true
 * targets = ["all"]                            # every online player, or a nickname list
 *
 * [features.attributes.units."2"]
 * type = "base"
 * attribute = "minecraft:attack_damage"
 * value = 0.1                                  # base only: overrides the vanilla base value
 * schedule = true
 * interval_ticks = 200
 * targets = ["all"]
 * </pre>
 * <p>
 * <b>Modifiers are persistent.</b> {@code addModifier} writes into the player
 * data, so a modifier survives a restart (and even a plugin removal). That is
 * why every modifier gets a STABLE KEY derived from its unit name
 * ({@code <plugin>:unit_<name>}) — the key is what a later re-application
 * replaces and what the cleanup pass removes. Consequences:
 * <ul>
 *   <li>renaming/renumbering a unit leaves the old modifier behind — the
 *       documented way to drop modifiers is to disable the feature (keeping the
 *       units) and run {@code /ui reload} or restart;</li>
 *   <li>deleting a unit while its modifier is applied leaves that modifier
 *       behind as well (the plugin no longer knows its key);</li>
 *   <li>re-applying is idempotent: the old modifier with our key is removed
 *       before the new one is added, so changed {@code amount}/{@code operation}/
 *       {@code slot} take effect on the next application.</li>
 * </ul>
 * <p>
 * <b>Database state.</b> When the feature is enabled the manager records
 * {@code attributes.modifiers_enabled = 1} in the {@code ui_state} table
 * ({@link StateStore}). If the config turns the feature off while that record
 * says "enabled", startup (and {@code /ui reload}) strips every modifier
 * described by the still-configured units from all online players and flips the
 * record to {@code 0}. While the feature stays disabled, a joining player is
 * stripped too — so a player who was offline during the cleanup is cleaned on
 * his next join as well.
 * <p>
 * <b>Schedule.</b> {@code schedule = false} (the default for modifiers) applies
 * the unit once: at startup for online players and on every join. That is enough
 * for a persistent modifier, so no task runs. {@code schedule = true} keeps the
 * old periodic behaviour (base values get written by other plugins/vanilla on
 * respawn and dimension change, and a modifier may be stripped by a plugin that
 * restores attributes), re-applying the unit every {@code interval_ticks}.
 * <p>
 * <b>Targets.</b> {@code ["all"]} (or {@code "*"}) serves every online player,
 * otherwise the list holds nicknames matched case-insensitively;
 * {@code ["Steve,Alex"]} and {@code ["Steve", "Alex"]} are equivalent. A missing
 * or empty list means everyone.
 * <p>
 * Malformed units (unknown type/attribute/operation/slot, missing or non-finite
 * value/amount, duplicate modifier key) are logged and skipped — one broken unit
 * never kills the feature.
 */
public final class AttributesManager implements Listener {

    /** How a unit changes its attribute. */
    public enum ChangeType {
        /** Overrides the vanilla base value ({@code value}). */
        BASE,
        /** Adds a persistent attribute modifier ({@code amount}/{@code operation}/{@code slot}). */
        MODIFIER
    }

    /** How often a scheduled unit re-applies when {@code interval_ticks} is absent. */
    private static final int DEFAULT_INTERVAL_TICKS = 200;

    /** {@link StateStore} namespace/key of the "feature is enabled" record. */
    private static final String DB_NAMESPACE = "attributes";
    private static final String DB_ENABLED_KEY = "modifiers_enabled";

    /** Prefix of the modifier keys derived from unit names. */
    private static final String MODIFIER_KEY_PREFIX = "unit_";

    /** One configured unit: one attribute, its change type, schedule and targets. */
    public static final class Unit {
        final String id;
        final ChangeType type;
        final Attribute attribute;
        /** BASE only: the value that REPLACES the vanilla base value. */
        final double value;
        /** MODIFIER only: raw additive amount (used when {@link #target} is NaN). */
        final double amount;
        /**
         * MODIFIER only: desired FINAL value of the attribute, or {@link Double#NaN}
         * when the unit works with a raw {@link #amount}. The modifier amount is
         * derived at application time as {@code target - instance.getBaseValue()},
         * so a vanilla default (e.g. 60 000 000 for
         * {@code minecraft:waypoint_receive_range}) never skews the result.
         */
        final double target;
        final AttributeModifier.Operation operation;
        final EquipmentSlotGroup slot;
        /** MODIFIER only: the stable key of the modifier this unit owns. */
        final NamespacedKey key;
        final boolean schedule;
        final int intervalTicks;
        final boolean allTargets;
        final Set<String> targetNames; // lowercase nicknames, empty when allTargets

        Unit(String id, ChangeType type, Attribute attribute,
             double value, double amount, double target,
             AttributeModifier.Operation operation,
             EquipmentSlotGroup slot, NamespacedKey key,
             boolean schedule, int intervalTicks,
             boolean allTargets, Set<String> targetNames) {
            this.id = id;
            this.type = type;
            this.attribute = attribute;
            this.value = value;
            this.amount = amount;
            this.target = target;
            this.operation = operation;
            this.slot = slot;
            this.key = key;
            this.schedule = schedule;
            this.intervalTicks = intervalTicks;
            this.allTargets = allTargets;
            this.targetNames = targetNames;
        }

        /** Whether this unit's change should be applied to the given player. */
        boolean matches(Player player) {
            return allTargets || targetNames.contains(player.getName().toLowerCase(Locale.ROOT));
        }
    }

    /** Parsed {@code targets} list of one unit. */
    private record TargetSet(boolean all, Set<String> names) {}

    private static AttributesManager instance;

    private boolean enabled;
    private final List<Unit> units = new ArrayList<>();
    private final List<BukkitTask> tasks = new ArrayList<>();

    private AttributesManager() {}

    // =========================
    // LIFECYCLE
    // =========================

    public static void init(Main plugin) {
        shutdown(); // soft-reload safety: never leave tasks from a previous instance
        instance = new AttributesManager();
        instance.loadConfig();
        instance.applyState(plugin);
        ConsoleLogger.info("[Attributes] Initialized — " + instance.units.size()
                + " unit(s) (" + instance.countOf(ChangeType.MODIFIER) + " modifier, "
                + instance.countOf(ChangeType.BASE) + " base), enabled=" + instance.enabled);
    }

    public static void reloadConfig() {
        if (instance == null) {
            init(Main.getInstance());
            return;
        }
        instance.stopTasks();
        instance.loadConfig();
        instance.applyState(Main.getInstance());
        ConsoleLogger.info("[Attributes] Config reloaded — " + instance.units.size()
                + " unit(s), enabled=" + instance.enabled);
    }

    public static void shutdown() {
        if (instance == null) return;
        instance.stopTasks();
        instance.units.clear();
        instance.enabled = false;
        instance = null;
    }

    public static AttributesManager getInstance() {
        return instance;
    }

    public boolean isEnabled() {
        return enabled;
    }

    private long countOf(ChangeType type) {
        return units.stream().filter(u -> u.type == type).count();
    }

    // =========================
    // APPLY / REVOKE (state machine)
    // =========================

    /**
     * Applies the loaded config state: grants everything when enabled, or strips
     * the configured modifiers when disabled (the DB record tells whether the
     * feature was enabled before, which is what turns a plain "disabled" into a
     * cleanup run).
     */
    private void applyState(Main plugin) {
        if (enabled) {
            recordEnabled(true);
            for (Player player : Bukkit.getOnlinePlayers()) {
                for (Unit unit : units) {
                    if (unit.matches(player)) grant(unit, player);
                }
            }
            scheduleAll(plugin);
            return;
        }

        boolean wasRecordedEnabled = isRecordedEnabled();
        int removed = revokeAllOnline();
        recordEnabled(false);
        if (wasRecordedEnabled || removed > 0) {
            ConsoleLogger.info("[Attributes] Feature disabled — removed " + removed
                    + " attribute modifier(s) described by the configured units.");
        }
    }

    // =========================
    // CONFIG
    // =========================

    private void loadConfig() {
        units.clear();
        enabled = false;

        var cfg = Main.getInstance().getConfig().getConfigurationSection("features.attributes");
        if (cfg == null) {
            ConsoleLogger.warn("[Attributes] No features.attributes section in config — feature idle.");
            return;
        }
        enabled = cfg.getBoolean("enabled", true);

        // Units are parsed even when the feature is disabled: the very same
        // descriptions are what the cleanup pass removes from players.
        ConfigurationSection unitsSection = cfg.getConfigurationSection("units");
        if (unitsSection == null || unitsSection.getKeys(false).isEmpty()) {
            if (enabled) {
                ConsoleLogger.warn("[Attributes] features.attributes.units is empty — nothing to grant.");
            }
            return;
        }

        int skipped = 0;
        Map<Attribute, String> grantedBy = new HashMap<>();
        Map<NamespacedKey, String> keyOwners = new HashMap<>();

        for (String id : sortedIds(unitsSection.getKeys(false))) {
            ConfigurationSection u = unitsSection.getConfigurationSection(id);
            if (u == null) {
                skipped++;
                continue;
            }
            Unit unit = parseUnit(id, u);
            if (unit == null) {
                skipped++;
                continue;
            }

            String previous = grantedBy.put(unit.attribute, id);
            if (previous != null) {
                ConsoleLogger.warn("[Attributes] Units " + previous + " and " + id + " both change "
                        + unit.attribute.getKey() + " — both apply, the last one wins where they overlap.");
            }
            if (unit.type == ChangeType.MODIFIER) {
                String keyOwner = keyOwners.put(unit.key, id);
                if (keyOwner != null) {
                    ConsoleLogger.warn("[Attributes] Units " + keyOwner + " and " + id
                            + " map to the same modifier key (" + unit.key
                            + ") — rename one of them; unit " + id + " skipped.");
                    skipped++;
                    continue;
                }
            }
            units.add(unit);
        }

        if (skipped > 0) {
            ConsoleLogger.warn("[Attributes] " + skipped + " invalid unit(s) skipped.");
        }
    }

    /** Parses one unit; returns {@code null} (after logging) when it is malformed. */
    private static Unit parseUnit(String id, ConfigurationSection u) {
        // --- change type (explicit, or inferred from the parameters) ---
        ChangeType type = parseType(id, u);
        if (type == null) {
            ConsoleLogger.warn("[Attributes] Unit " + id
                    + ": 'type' must be 'base' or 'modifier' — skipped.");
            return null;
        }

        // --- attribute ---
        String rawAttribute = u.getString("attribute");
        Attribute attribute = AttributeUtil.resolve(rawAttribute);
        if (attribute == null) {
            ConsoleLogger.warn("[Attributes] Unit " + id + ": unknown attribute '"
                    + rawAttribute + "' — skipped.");
            return null;
        }

        // --- type-specific parameters ---
        double value = 0.0;
        double amount = 0.0;
        double target = Double.NaN; // modifier: desired FINAL value (NaN = raw amount)
        AttributeModifier.Operation operation = AttributeModifier.Operation.ADD_NUMBER;
        EquipmentSlotGroup slot = EquipmentSlotGroup.ANY;
        NamespacedKey key = null;

        if (type == ChangeType.BASE) {
            if (!u.isSet("value")) {
                ConsoleLogger.warn("[Attributes] Unit " + id
                        + ": base unit without 'value' — skipped.");
                return null;
            }
            value = u.getDouble("value");
            if (!isFinite(value)) {
                ConsoleLogger.warn("[Attributes] Unit " + id + ": 'value' is not a finite number — skipped.");
                return null;
            }
        } else {
            boolean hasTarget = u.isSet("target");
            boolean hasAmount = u.isSet("amount");
            if (!hasTarget && !hasAmount) {
                ConsoleLogger.warn("[Attributes] Unit " + id
                        + ": modifier unit without 'target' or 'amount' — skipped.");
                return null;
            }
            if (hasTarget) {
                target = u.getDouble("target");
                if (!isFinite(target)) {
                    ConsoleLogger.warn("[Attributes] Unit " + id
                            + ": 'target' is not a finite number — skipped.");
                    return null;
                }
                if (hasAmount) {
                    ConsoleLogger.warn("[Attributes] Unit " + id
                            + ": both 'target' and 'amount' are set — 'target' wins, the amount is computed.");
                }
            } else {
                amount = u.getDouble("amount");
                if (!isFinite(amount)) {
                    ConsoleLogger.warn("[Attributes] Unit " + id
                            + ": 'amount' is not a finite number — skipped.");
                    return null;
                }
            }
            operation = parseOperation(id, u.getString("operation"));
            if (operation == null) return null;
            slot = parseSlot(id, u.getString("slot"));
            if (slot == null) return null;
            key = modifierKey(id);
            if (key == null) {
                ConsoleLogger.warn("[Attributes] Unit " + id + ": cannot derive a modifier key — skipped.");
                return null;
            }
        }

        // --- schedule (modifiers default to a single application) ---
        boolean schedule = u.getBoolean("schedule", type == ChangeType.BASE);
        int intervalTicks = Math.max(1, u.getInt("interval_ticks", DEFAULT_INTERVAL_TICKS));

        // --- targets ---
        TargetSet targets = parseTargets(id, u.get("targets"));
        if (targets == null) return null;

        return new Unit(id, type, attribute, value, amount, target, operation, slot, key,
                schedule, intervalTicks, targets.all(), targets.names());
    }

    /**
     * Reads {@code type}. When it is absent the parameters decide
     * ({@code amount} → modifier, {@code value} → base), so configs written
     * before the type field keep working unchanged.
     */
    private static ChangeType parseType(String id, ConfigurationSection u) {
        String raw = u.getString("type");
        if (raw != null && !raw.isBlank()) {
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "base" -> ChangeType.BASE;
                case "modifier", "mod" -> ChangeType.MODIFIER;
                default -> null;
            };
        }
        if (u.isSet("target")) return ChangeType.MODIFIER;
        if (u.isSet("amount")) return ChangeType.MODIFIER;
        if (u.isSet("value")) return ChangeType.BASE;
        return null;
    }

    private static AttributeModifier.Operation parseOperation(String id, String raw) {
        if (raw == null || raw.isBlank()) return AttributeModifier.Operation.ADD_NUMBER;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "add_number", "add", "addition" -> AttributeModifier.Operation.ADD_NUMBER;
            case "add_scalar" -> AttributeModifier.Operation.ADD_SCALAR;
            case "multiply_scalar_1", "multiply" -> AttributeModifier.Operation.MULTIPLY_SCALAR_1;
            default -> {
                ConsoleLogger.warn("[Attributes] Unit " + id + ": unknown operation '" + raw
                        + "' (add_number | add_scalar | multiply_scalar_1) — skipped.");
                yield null;
            }
        };
    }

    private static EquipmentSlotGroup parseSlot(String id, String raw) {
        if (raw == null || raw.isBlank()) return EquipmentSlotGroup.ANY;
        EquipmentSlotGroup group = EquipmentSlotGroup.getByName(raw.trim());
        if (group == null) {
            ConsoleLogger.warn("[Attributes] Unit " + id + ": unknown slot '" + raw
                    + "' (any | hand | mainhand | offhand | armor | head | chest | legs | feet | body) — skipped.");
        }
        return group;
    }

    /** Stable modifier key derived from the unit name: {@code <plugin>:unit_<name>}. */
    private static NamespacedKey modifierKey(String id) {
        String sanitized = id.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        try {
            return new NamespacedKey(Main.getInstance(), MODIFIER_KEY_PREFIX + sanitized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Parses a unit's {@code targets} value: a list of nicknames, one
     * comma-separated string, or {@code "all"} / {@code "*"} for every online
     * player. Missing or empty → everyone.
     */
    private static TargetSet parseTargets(String id, Object raw) {
        List<String> entries = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) entries.add(String.valueOf(o));
            }
        } else if (raw instanceof String s) {
            entries.add(s);
        }

        boolean all = entries.isEmpty(); // a unit without targets serves everyone
        Set<String> names = new HashSet<>();
        for (String entry : entries) {
            for (String part : entry.split(",")) {
                String token = part.trim();
                if (token.isEmpty()) continue;
                if (token.equalsIgnoreCase("all") || token.equals("*")) {
                    all = true;
                } else {
                    names.add(token.toLowerCase(Locale.ROOT));
                }
            }
        }

        if (!all && names.isEmpty()) {
            ConsoleLogger.warn("[Attributes] Unit " + id
                    + ": targets list holds no nicknames — skipped.");
            return null;
        }
        return new TargetSet(all, names);
    }

    /** Ordinal unit names first (1, 2, 10, ...), then any custom names alphabetically. */
    private static List<String> sortedIds(Set<String> ids) {
        List<String> sorted = new ArrayList<>(ids);
        sorted.sort(Comparator.comparingLong(AttributesManager::numericId)
                .thenComparing(String::compareToIgnoreCase));
        return sorted;
    }

    private static long numericId(String id) {
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    // =========================
    // SCHEDULED GRANTS
    // =========================

    /** Starts one repeating task per scheduled unit, each on its own interval. */
    private void scheduleAll(Main plugin) {
        for (Unit unit : units) {
            if (!unit.schedule) continue;
            tasks.add(new BukkitRunnable() {
                @Override
                public void run() {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (unit.matches(player)) grant(unit, player);
                    }
                }
            }.runTaskTimer(plugin, 0L, unit.intervalTicks));
        }
    }

    /** Applies one unit to one player (idempotent for modifiers). */
    private void grant(Unit unit, Player player) {
        if (unit.type == ChangeType.BASE) {
            AttributeUtil.applyBase(player, unit.attribute, unit.value);
            return;
        }

        AttributeInstance instance = player.getAttribute(unit.attribute);
        if (instance == null) return;
        // addModifier rejects a duplicate key, and a changed amount/operation/slot
        // must replace the old entry — so ours is always removed first.
        if (instance.getModifier(unit.key) != null) {
            instance.removeModifier(unit.key);
        }
        // A target-style unit derives its amount from the LIVE base value, so the
        // RESULT is the configured target. Server defaults differ from vanilla and
        // may be overridden (SpigotConfig/DefaultAttributes/datapacks), so no
        // hard-coded default can be trusted here: e.g. waypoint_receive_range
        // defaults to 0 with a 6.0E7 maximum, and a plain +8192 modifier would
        // still leave the player at 8192 only because 0 + 8192 happens to match.
        double amount = Double.isNaN(unit.target)
                ? unit.amount
                : deriveAmount(unit, instance.getBaseValue());
        instance.addModifier(new AttributeModifier(unit.key, amount, unit.operation, unit.slot));
    }

    /**
     * Derives the modifier amount for a target-style unit from the attribute's
     * current base value: {@code ADD_NUMBER} adds the difference, the scalar
     * operations are inverted ({@code target / base - 1}). The inversion assumes
     * no other modifier of the same attribute is active — when several sources
     * change one attribute, configure a raw {@code amount} instead.
     */
    private static double deriveAmount(Unit unit, double base) {
        return switch (unit.operation) {
            case ADD_NUMBER -> unit.target - base;
            case ADD_SCALAR, MULTIPLY_SCALAR_1 -> base == 0.0 ? 0.0 : unit.target / base - 1.0;
        };
    }

    /** Removes one unit's modifier from one player. @return true when one was removed. */
    private boolean revoke(Unit unit, Player player) {
        if (unit.type != ChangeType.MODIFIER) return false;
        AttributeInstance instance = player.getAttribute(unit.attribute);
        if (instance == null || instance.getModifier(unit.key) == null) return false;
        instance.removeModifier(unit.key);
        return true;
    }

    /** Strips every configured modifier from every online player. @return removals. */
    private int revokeAllOnline() {
        int removed = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (Unit unit : units) {
                if (unit.matches(player) && revoke(unit, player)) removed++;
            }
        }
        return removed;
    }

    private void stopTasks() {
        for (BukkitTask task : tasks) {
            try {
                task.cancel();
            } catch (Exception ignored) {
                // already cancelled / scheduler shut down
            }
        }
        tasks.clear();
    }

    // =========================
    // PLAYER EVENTS
    // =========================

    /**
     * Enabled: grant every matching unit right away (no waiting for the first
     * interval). Disabled: strip the configured modifiers, so players who were
     * offline during the cleanup are cleaned on their next join.
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (units.isEmpty()) return;
        Player player = event.getPlayer();
        if (enabled) {
            for (Unit unit : units) {
                if (unit.matches(player)) grant(unit, player);
            }
        } else {
            for (Unit unit : units) {
                revoke(unit, player);
            }
        }
    }

    // =========================
    // DATABASE STATE
    // =========================

    /** Records the current enable state; the cleanup pass keys off this record. */
    private static void recordEnabled(boolean enabled) {
        if (!DatabaseManager.isConnected()) return; // DB not ready — nothing to persist
        StateStore.put(DB_NAMESPACE, DB_ENABLED_KEY, enabled ? "1" : "0");
    }

    /** @return true when the database says the feature was enabled before. */
    private static boolean isRecordedEnabled() {
        if (!DatabaseManager.isConnected()) return false;
        return "1".equals(StateStore.get(DB_NAMESPACE, DB_ENABLED_KEY));
    }
}
