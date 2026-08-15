package com.ultimateimprovments.broadcast;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 🕐 AutoBroadcastManager — automatic broadcast system.
 * <p>
 * Reads the {@code auto_broadcast} section from config.yml and every second
 * (20 ticks) checks the readiness of sections. When a section "ripens"
 * ({@code cooldown_ticks} accumulated), the message is sent to all
 * online players who pass the section's conditions.
 * <p>
 * Features:
 * <ul>
 *   <li>Global conditions ({@code online-*}) are checked once per cycle;</li>
 *   <li>Game conditions are checked separately for each player;</li>
 *   <li>Placeholders in messages are resolved PERSONALLY for each player
 *       ({@code %player_name%}, {@code %online%}, any PlaceholderAPI);</li>
 *   <li>There can be any number of sections, each with its own message queue.</li>
 * </ul>
 * <p>
 * Lifecycle: {@link #start(Main)} on startup/reload,
 * {@link #stop()} on plugin shutdown. {@link #start} is idempotent
 * (stops the previous task first), so it is safe to call
 * again on {@code /ui reload}.
 */
public final class AutoBroadcastManager {

    /** Main ticker period in ticks (1 sec). */
    private static final int TICK_PERIOD = 20;

    private static AutoBroadcastManager instance;

    private Main plugin;
    private BukkitTask task;
    private final List<BroadcastSection> sections = new ArrayList<>();

    private AutoBroadcastManager() {}

    public static AutoBroadcastManager getInstance() {
        if (instance == null) {
            instance = new AutoBroadcastManager();
        }
        return instance;
    }

    /**
     * Starts the auto-broadcast system: reads the config, parses the sections,
     * schedules the ticker. Idempotent — repeated calls are safe.
     */
    public synchronized void start(Main plugin) {
        this.plugin = plugin;
        stop();

        var config = plugin.getConfig();
        if (!config.getBoolean("auto_broadcast.enabled", true)) {
            ConsoleLogger.info("[AutoBroadcast] Disabled in config (auto_broadcast.enabled: false)");
            return;
        }

        ConfigurationSection sectionsSection = config.getConfigurationSection("auto_broadcast.sections");
        if (sectionsSection == null) {
            ConsoleLogger.info("[AutoBroadcast] No 'auto_broadcast.sections' in config.yml — nothing to do");
            return;
        }

        for (String key : sectionsSection.getKeys(false)) {
            ConfigurationSection section = sectionsSection.getConfigurationSection(key);
            if (section == null) continue;
            if (!section.getBoolean("enabled", true)) {
                ConsoleLogger.info("[AutoBroadcast] Section '" + key + "' disabled, skipped");
                continue;
            }
            BroadcastSection parsed = BroadcastSection.parse(section);
            if (parsed != null) {
                sections.add(parsed);
            }
        }

        if (sections.isEmpty()) {
            ConsoleLogger.info("[AutoBroadcast] No enabled sections — nothing to do");
            return;
        }

        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);

        String names = sections.stream()
                .map(s -> s.getName() + "(" + s.getCooldownTicks() + "t, " + s.getMessageCount() + " msg)")
                .collect(Collectors.joining(", "));
        ConsoleLogger.info("[AutoBroadcast] Enabled with " + sections.size() + " section(s): " + names);
    }

    /** Stops the system and resets all sections. */
    public synchronized void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        sections.clear();
        plugin = null;
    }

    /** Restart after a configuration reload. */
    public synchronized void reload() {
        if (plugin == null) return;
        start(plugin);
    }

    /** Ticker: every second accumulate section ticks and send the ripened ones. */
    private void tick() {
        for (BroadcastSection section : sections) {
            section.accumulate(TICK_PERIOD);
            if (section.isReady()) {
                fire(section);
            }
        }
    }

    /** Sends the section's next message to players who pass the conditions. */
    private void fire(BroadcastSection section) {
        // Global conditions (online-*) — if not met, the whole cycle is skipped
        if (!section.matchesGlobal()) return;

        List<Player> targets = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (section.matches(player)) {
                targets.add(player);
            }
        }

        // Message rotation advances ONLY on an actual send
        if (targets.isEmpty()) return;
        String message = section.nextMessage();

        // Resolve placeholders personally for each player
        for (Player player : targets) {
            player.sendMessage(MessageUtil.parse(message, player));
        }
    }
}
