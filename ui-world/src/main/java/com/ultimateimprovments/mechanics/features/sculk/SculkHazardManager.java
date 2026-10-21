package com.ultimateimprovments.mechanics.features.sculk;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.core.Permissions;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * ☣ Sculk contact hazards — the sculk patch fights back.
 * <p>
 * The growth module makes sculk spread forever; this module makes walking on
 * the result unpleasant. Every {@code interval_ticks} (20 = one second) the
 * task looks at the block the player occupies and applies the debuff bound to
 * that block — no event is involved, so the effect is refreshed for as long as
 * the player keeps standing there and simply expires (default: one second)
 * after stepping off.
 * <p>
 * <b>Geometry.</b> Which block is checked is configurable per rule through
 * {@code offset}, counted from the block the player's feet are in:
 * <ul>
 *   <li>{@code offset = 0} — the feet block itself. Sculk veins have no
 *       collision and the shrieker/sensor are only half a block tall, so the
 *       player literally stands inside their block space;</li>
 *   <li>{@code offset = -1} — the block directly under the feet. Sculk and the
 *       catalyst are full blocks, so a player standing on one occupies the air
 *       block above it.</li>
 * </ul>
 * <p>
 * <b>Rule kinds.</b> A rule either applies a potion effect ({@code effect},
 * {@code duration_ticks}, {@code amplifier}, {@code particles},
 * {@code ambient}, {@code icon}) or summons evoker fangs ({@code fangs = true},
 * {@code fangs_count}, {@code fangs_attack_delay_ticks}). Several rules can
 * fire in the same pulse — a vein lying on top of a catalyst gives both its
 * wither and the catalyst's mining fatigue.
 * <p>
 * <b>Defaults</b> (see {@code [sculk.hazards]} in {@code configs/UI-World.toml}):
 * vein → wither I, sculk block → wither I, shrieker → one evoker fang at the
 * shrieker's block, sensor → slowness 255, catalyst → mining fatigue 255; every
 * effect lasts one second and is refreshed every second.
 * <p>
 * <b>Safety.</b> Each rule has its own {@code enabled} switch and the whole
 * module has a master switch. Creative/spectator players are skipped by default
 * and {@code bypass_permission} (default {@code ui.sculk.hazards.bypass}) makes
 * a player immune. The module is independent of the growth start/stop switch:
 * stopping the spread does not disarm the blocks that already exist, and
 * {@code use_growth_world_filter = true} makes the hazards follow the
 * {@code [sculk.worlds]} filter of the growth module.
 */
public final class SculkHazardManager extends BukkitRunnable {

    private static SculkHazardManager instance;

    private final Main plugin;

    // ===== CONFIG =====
    /** Master switch: {@code [sculk.hazards] enabled}. */
    private boolean enabled = true;
    /** Hazard scan cooldown; 20 ticks = one second. */
    private int intervalTicks = 20;
    /** Skip creative players (a hazard cannot hurt them anyway). */
    private boolean ignoreCreative = true;
    /** Skip spectators (they can fly through blocks). */
    private boolean ignoreSpectator = true;
    /** When true, hazards follow the growth module's {@code [sculk.worlds]} filter. */
    private boolean useGrowthWorldFilter = false;
    /** Permission that makes a player immune; empty = no bypass. */
    private String bypassPermission = "";

    /** Active rules in config order — every matching rule fires in one pulse. */
    private final List<HazardRule> rules = new ArrayList<>();

    private BukkitTask task;

    private SculkHazardManager(Main plugin) {
        this.plugin = plugin;
    }

    // =========================
    // INIT / SHUTDOWN / RELOAD
    // =========================

    public static void init(Main plugin) {
        if (instance != null) {
            instance.shutdown();
        }
        instance = new SculkHazardManager(plugin);
        instance.reloadConfig();
        instance.startTimer();
        ConsoleLogger.info("[SCULK] Hazards " + (instance.enabled ? "enabled" : "disabled")
                + " (" + instance.rules.size() + " rule(s), every " + instance.intervalTicks + "t)");
    }

    public static SculkHazardManager getInstance() {
        return instance;
    }

    public static void reload() {
        if (instance == null) return;
        int previousInterval = instance.intervalTicks;
        instance.reloadConfig();
        if (instance.task != null && previousInterval != instance.intervalTicks) {
            instance.startTimer();
        }
        ConsoleLogger.info("[SCULK] Hazards reloaded (" + (instance.enabled ? "enabled" : "disabled")
                + ", " + instance.rules.size() + " rule(s), interval=" + instance.intervalTicks + "t)");
    }

    public static void shutdown() {
        if (instance == null) return;
        instance.stopTimer();
        instance.rules.clear();
        instance = null;
        ConsoleLogger.info("[SCULK] Hazards disabled.");
    }

    private void reloadConfig() {
        rules.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("sculk.hazards");
        if (sec == null) {
            enabled = false;
            ConsoleLogger.warn("[SCULK] Config section 'sculk.hazards' not found — contact hazards DISABLED."
                    + " Add it to configs/UI-World.toml to enable the mechanic.");
            return;
        }

        enabled = sec.getBoolean("enabled", true);
        intervalTicks = Math.max(1, sec.getInt("interval_ticks", 20));
        ignoreCreative = sec.getBoolean("ignore_creative", true);
        ignoreSpectator = sec.getBoolean("ignore_spectator", true);
        useGrowthWorldFilter = sec.getBoolean("use_growth_world_filter", false);
        String bypass = sec.getString("bypass_permission", Permissions.UI_SCULK_HAZARDS_BYPASS);
        bypassPermission = bypass == null ? "" : bypass.trim();

        loadRule(sec, "vein");
        loadRule(sec, "block");
        loadRule(sec, "shrieker");
        loadRule(sec, "sensor");
        loadRule(sec, "catalyst");
    }

    /** Reads one hazard rule table (e.g. {@code [sculk.hazards.vein]}). */
    private void loadRule(ConfigurationSection hazards, String id) {
        ConfigurationSection sec = hazards.getConfigurationSection(id);
        if (sec == null) return;
        if (!sec.getBoolean("enabled", true)) return;

        Set<Material> blocks = readMaterials(sec, id);
        if (blocks.isEmpty()) {
            ConsoleLogger.warn("[SCULK] Hazards rule '" + id + "': no valid blocks — rule skipped.");
            return;
        }

        int offset = sec.getInt("offset", 0);

        if (sec.getBoolean("fangs", false)) {
            int count = Math.max(1, sec.getInt("fangs_count", 1));
            // Paper requires a positive delay; 1 tick is effectively instant.
            int delay = Math.max(1, sec.getInt("fangs_attack_delay_ticks", 1));
            rules.add(new HazardRule(id, offset, blocks, null, 0, 0,
                    false, false, false, delay, count));
            return;
        }

        PotionEffectType effect = resolveEffect(sec.getString("effect", ""));
        if (effect == null) {
            ConsoleLogger.warn("[SCULK] Hazards rule '" + id + "': unknown effect '"
                    + sec.getString("effect", "") + "' — rule skipped.");
            return;
        }

        int durationTicks = Math.max(1, sec.getInt("duration_ticks", intervalTicks));
        int amplifier = Math.max(0, sec.getInt("amplifier", 0));
        boolean particles = sec.getBoolean("particles", true);
        boolean ambient = sec.getBoolean("ambient", false);
        boolean icon = sec.getBoolean("icon", true);
        rules.add(new HazardRule(id, offset, blocks, effect, durationTicks, amplifier,
                particles, ambient, icon, 0, 0));
    }

    /** Parses a rule's {@code blocks} list; unknown materials are logged and skipped. */
    private static Set<Material> readMaterials(ConfigurationSection sec, String id) {
        List<String> names = sec.getStringList("blocks");
        if (names.isEmpty()) return Set.of();
        Set<Material> out = EnumSet.noneOf(Material.class);
        for (String name : names) {
            if (name == null || name.isBlank()) continue;
            Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
            if (material == null) {
                ConsoleLogger.warn("[SCULK] Hazards rule '" + id + "': unknown block material '"
                        + name.trim() + "' — ignored.");
                continue;
            }
            out.add(material);
        }
        return out.isEmpty() ? Set.of() : out;
    }

    /** Resolves a potion effect by registry key (accepts {@code wither} and {@code minecraft:wither}). */
    private static PotionEffectType resolveEffect(String name) {
        if (name == null || name.isBlank()) return null;
        String key = name.trim().toLowerCase(Locale.ROOT);
        if (key.startsWith("minecraft:")) key = key.substring("minecraft:".length());
        try {
            for (PotionEffectType type : Registry.POTION_EFFECT_TYPE) {
                if (type.getKey().getKey().equalsIgnoreCase(key)) return type;
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[SCULK] Effect registry lookup failed: " + e.getMessage());
        }
        return null;
    }

    private void startTimer() {
        stopTimer();
        task = runTaskTimer(plugin, intervalTicks, intervalTicks);
    }

    private void stopTimer() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    // =========================
    // STATE (status command)
    // =========================

    /** Master switch: {@code [sculk.hazards] enabled}. */
    public boolean isEnabled() { return enabled; }

    /** Hazard scan cooldown in ticks. */
    public int getIntervalTicks() { return intervalTicks; }

    /** How many rules are active (skipped/disabled ones do not count). */
    public int activeRules() { return rules.size(); }

    /**
     * Human-readable rule list for {@code /ui sculk status}, already in
     * MiniMessage, e.g. {@code vein=WITHER 1, shrieker=fangs×1, ...}.
     */
    public String describe() {
        StringBuilder out = new StringBuilder();
        for (HazardRule rule : rules) {
            if (out.length() > 0) out.append("<dark_gray>, </dark_gray>");
            out.append("<white>").append(rule.id()).append("</white><gray>=</gray>");
            if (rule.isFangs()) {
                out.append("<yellow>fangs×").append(rule.fangsCount()).append("</yellow>");
            } else {
                out.append("<aqua>").append(rule.effect().getKey().getKey().toUpperCase(Locale.ROOT))
                        .append(' ').append(rule.amplifier() + 1).append("</aqua>");
            }
        }
        return out.length() == 0 ? "<dark_gray>(none)</dark_gray>" : out.toString();
    }

    // =========================
    // PULSE — one debuff per matching block, every interval
    // =========================

    @Override
    public void run() {
        if (!enabled || rules.isEmpty()) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!applies(player)) continue;
            applyHazards(player);
        }
    }

    /** Whether this player can be affected at all. */
    private boolean applies(Player player) {
        if (!player.isOnline() || player.isDead()) return false;
        GameMode mode = player.getGameMode();
        if (ignoreSpectator && mode == GameMode.SPECTATOR) return false;
        if (ignoreCreative && mode == GameMode.CREATIVE) return false;
        if (!bypassPermission.isEmpty() && player.hasPermission(bypassPermission)) return false;
        return true;
    }

    /**
     * Applies every rule whose source block matches. A rule with
     * {@code offset = 0} inspects the feet block, a negative offset looks
     * below it ({@code -1} = the block the player stands on).
     */
    private void applyHazards(Player player) {
        World world = player.getWorld();
        if (!isWorldAllowed(world)) return;

        Block feet = player.getLocation().getBlock();
        for (HazardRule rule : rules) {
            Block source = rule.offset() == 0 ? feet : feet.getRelative(BlockFace.UP, rule.offset());
            if (!rule.blocks().contains(source.getType())) continue;

            if (rule.isFangs()) {
                summonFangs(source, rule);
            } else {
                player.addPotionEffect(new PotionEffect(rule.effect(), rule.durationTicks(),
                        rule.amplifier(), rule.ambient(), rule.particles(), rule.icon()));
            }
        }
    }

    private boolean isWorldAllowed(World world) {
        if (!useGrowthWorldFilter) return true;
        SculkGrowthManager growth = SculkGrowthManager.getInstance();
        return growth == null || growth.isWorldAllowed(world.getName());
    }

    /**
     * Summons the configured number of evoker fangs at the centre of the source
     * block. No owner is set on purpose: fangs without an owner damage every
     * entity in their hitbox, which is exactly what "standing on the shrieker
     * hurts" means.
     */
    private void summonFangs(Block source, HazardRule rule) {
        Location centre = source.getLocation().add(0.5, 0.0, 0.5);
        World world = source.getWorld();
        for (int i = 0; i < rule.fangsCount(); i++) {
            try {
                EvokerFangs fangs = world.spawn(centre, EvokerFangs.class);
                fangs.setOwner(null);
                fangs.setAttackDelay(rule.fangsAttackDelayTicks());
            } catch (Throwable t) {
                ConsoleLogger.warn("[SCULK] Failed to summon fangs at " + world.getName() + " "
                        + source.getX() + " " + source.getY() + " " + source.getZ()
                        + ": " + t.getMessage());
                return;
            }
        }
    }

    /**
     * One configured rule: which blocks trigger it, which block relative to the
     * feet is inspected, and what happens on a match. A rule with
     * {@code effect == null} summons fangs instead of a potion effect.
     */
    private record HazardRule(
            String id,
            int offset,
            Set<Material> blocks,
            PotionEffectType effect,
            int durationTicks,
            int amplifier,
            boolean particles,
            boolean ambient,
            boolean icon,
            int fangsAttackDelayTicks,
            int fangsCount) {

        boolean isFangs() {
            return effect == null;
        }
    }
}
