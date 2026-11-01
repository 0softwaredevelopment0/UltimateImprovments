package com.ultimateimprovments.mechanics.features.player;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.AttributeUtil;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
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
 * ⚔️ AttributesManager — config-driven SCHEDULED attribute grants.
 * <p>
 * The {@code features.attributes} config section holds any number of <b>units</b>.
 * A unit is one attribute + one value + its own schedule and target list; unit
 * names are ordinal numbers so the config reads like a list:
 * <pre>
 * [features.attributes]
 * enabled = true
 *
 * [features.attributes.units."1"]
 * attribute = "minecraft:waypoint_receive_range"  # namespaced, bare or CONSTANT form
 * value = 8192.0                                 # base value re-applied every interval
 * interval_ticks = 200                           # how often the value is re-applied
 * targets = ["all"]                              # or ["Steve", "Alex"]
 * </pre>
 * <p>
 * <b>Behaviour:</b> every unit owns one repeating task (created with its own
 * {@code interval_ticks}), and each run re-applies the unit's base value to every
 * online player matching its {@code targets} ({@code all} = everyone online; a
 * nickname list matches case-insensitively). Because the value is re-applied on
 * a schedule, it survives attribute resets (respawn, dimension change,
 * {@code /ui clear attributes}, plugins that write base values) without any
 * event-specific hooks. A joining player is granted every matching unit
 * immediately, so there is no "wait for the first interval" gap.
 * <p>
 * <b>Targets:</b> {@code ["all"]} (or a comma-separated string like
 * {@code "all"}) grants to everyone online; otherwise the list holds nicknames
 * ({@code ["Steve,Alex"]} and {@code ["Steve", "Alex"]} are equivalent).
 * An empty / missing list means everyone, so a freshly added unit works out of
 * the box.
 * <p>
 * <b>Value vs modifiers:</b> the unit writes the attribute's BASE value through
 * {@link AttributeUtil#applyBase}, so enchantment, item and potion modifiers keep
 * stacking on top of it (that is exactly what the previous attack-damage/speed
 * behaviour did). Two units granting the same attribute are allowed — the later
 * application wins on every overlap, and the conflict is logged on startup.
 * <p>
 * Malformed units (unknown attribute, missing/non-finite value) are logged and
 * skipped — one broken unit never kills the whole feature. With
 * {@code features.attributes.enabled = false} the manager stays idle and applies
 * nothing.
 */
public final class AttributesManager implements Listener {

    /** How often a unit re-applies its value when {@code interval_ticks} is absent. */
    private static final int DEFAULT_INTERVAL_TICKS = 200;

    /** One configured unit: one attribute, one value, its own schedule and targets. */
    public static final class Unit {
        final String id;
        final Attribute attribute;
        final double value;
        final int intervalTicks;
        final boolean allTargets;
        final Set<String> targetNames; // lowercase nicknames, empty when allTargets

        Unit(String id, Attribute attribute, double value, int intervalTicks,
             boolean allTargets, Set<String> targetNames) {
            this.id = id;
            this.attribute = attribute;
            this.value = value;
            this.intervalTicks = intervalTicks;
            this.allTargets = allTargets;
            this.targetNames = targetNames;
        }

        /** Whether this unit's value should be granted to the given player. */
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
        instance.schedule(plugin);
        ConsoleLogger.info("[Attributes] Initialized — " + instance.units.size()
                + " scheduled unit(s), enabled=" + instance.enabled);
    }

    public static void reloadConfig() {
        if (instance == null) {
            init(Main.getInstance());
            return;
        }
        instance.stopTasks();
        instance.loadConfig();
        instance.schedule(Main.getInstance());
        ConsoleLogger.info("[Attributes] Config reloaded — " + instance.units.size()
                + " scheduled unit(s), enabled=" + instance.enabled);
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
        if (!enabled) return;

        ConfigurationSection unitsSection = cfg.getConfigurationSection("units");
        if (unitsSection == null || unitsSection.getKeys(false).isEmpty()) {
            ConsoleLogger.warn("[Attributes] features.attributes.units is empty — nothing to grant.");
            return;
        }

        int skipped = 0;
        Map<Attribute, String> grantedBy = new HashMap<>();

        for (String id : sortedIds(unitsSection.getKeys(false))) {
            ConfigurationSection u = unitsSection.getConfigurationSection(id);
            if (u == null) {
                skipped++;
                continue;
            }

            // --- attribute ---
            String rawAttribute = u.getString("attribute");
            Attribute attribute = AttributeUtil.resolve(rawAttribute);
            if (attribute == null) {
                ConsoleLogger.warn("[Attributes] Unit " + id + ": unknown attribute '"
                        + rawAttribute + "' — skipped.");
                skipped++;
                continue;
            }

            // --- value ---
            if (!u.isSet("value")) {
                ConsoleLogger.warn("[Attributes] Unit " + id + ": 'value' is missing — skipped.");
                skipped++;
                continue;
            }
            double value = u.getDouble("value");
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                ConsoleLogger.warn("[Attributes] Unit " + id
                        + ": 'value' is not a finite number — skipped.");
                skipped++;
                continue;
            }

            // --- schedule (this unit's own re-application interval) ---
            int intervalTicks = Math.max(1, u.getInt("interval_ticks", DEFAULT_INTERVAL_TICKS));

            // --- targets (all online players / nickname list) ---
            TargetSet targets = parseTargets(id, u.get("targets"));
            if (targets == null) {
                skipped++;
                continue;
            }

            String previous = grantedBy.put(attribute, id);
            if (previous != null) {
                ConsoleLogger.warn("[Attributes] Units " + previous + " and " + id + " both grant "
                        + attribute.getKey() + " — the later unit wins wherever they overlap.");
            }

            units.add(new Unit(id, attribute, value, intervalTicks, targets.all(), targets.names()));
        }

        if (skipped > 0) {
            ConsoleLogger.warn("[Attributes] " + skipped + " invalid unit(s) skipped.");
        }
    }

    /**
     * Parses a unit's {@code targets} value: a list of nicknames, one
     * comma-separated string, or {@code "all"} / {@code "*"} for every online
     * player. Missing or empty → everyone.
     *
     * @return the parsed targets, or {@code null} when the value is malformed
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

    // =========================
    // SCHEDULED GRANTS
    // =========================

    /** Starts one repeating task per unit, each on its own {@code interval_ticks}. */
    private void schedule(Main plugin) {
        if (!enabled) return;
        for (Unit unit : units) {
            tasks.add(new BukkitRunnable() {
                @Override
                public void run() {
                    applyToOnline(unit);
                }
            }.runTaskTimer(plugin, 0L, unit.intervalTicks));
        }
    }

    /** Re-applies one unit to every online player matching its targets. */
    private void applyToOnline(Unit unit) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!unit.matches(player)) continue;
            AttributeUtil.applyBase(player, unit.attribute, unit.value);
        }
    }

    /**
     * Immediate grant on join: a player who just connected must not wait up to
     * {@code interval_ticks} for range/name-tag attributes to take effect.
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled || units.isEmpty()) return;
        Player player = event.getPlayer();
        for (Unit unit : units) {
            if (unit.matches(player)) {
                AttributeUtil.applyBase(player, unit.attribute, unit.value);
            }
        }
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
}
