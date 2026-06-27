package com.mcplugin.infrastructure.belowname;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.MessageUtil;
import com.mcplugin.infrastructure.util.PlaceholderResolver;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

/**
 * Manages the BELOW_NAME scoreboard objective — displays formatted text
 * (MiniMessage + placeholders) below each player's nametag in the world.
 * <p>
 * Uses {@code Score.customName(Component)} to show custom text instead of
 * a plain number. Falls back to a numeric score if customName is not supported.
 * <p>
 * Config example:
 * <pre>
 * belowname:
 *   enabled: true
 *   format: "&lt;red&gt;❤ {player_health}&lt;/red&gt; &lt;dark_gray&gt;|&lt;/dark_gray&gt; &lt;white&gt;{player_food}&lt;/white&gt;"
 *   update_interval_ticks: 10
 * </pre>
 */
public class BelowNameManager extends BukkitRunnable {

    private static BelowNameManager instance;
    private static final String OBJECTIVE_NAME = "mcplugin_bn";
    private static final String OBJECTIVE_CRITERIA = "dummy";

    private boolean enabled;
    private String format;
    private int intervalTicks;

    private Scoreboard scoreboard;
    private Objective objective;

    public static void init() {
        instance = new BelowNameManager();
        instance.reloadConfig();
    }

    public static void shutdown() {
        if (instance != null) {
            instance.cancel();
            instance.cleanupObjective();
            instance = null;
        }
    }

    public static void reload() {
        if (instance != null) {
            instance.cancel();
            instance.cleanupObjective();
            instance.reloadConfig();
        }
    }

    private void reloadConfig() {
        FileConfiguration config = Main.getInstance().getConfig();

        this.enabled = config.getBoolean("belowname.enabled", false);
        this.format = config.getString("belowname.format", "<red>❤ {player_health}</red>");
        this.intervalTicks = Math.max(5, config.getInt("belowname.update_interval_ticks", 10));

        if (enabled) {
            initObjective();
            this.runTaskTimer(Main.getInstance(), 20L, intervalTicks);
        }
    }

    // =========================
    // SCOREBOARD OBJECTIVE
    // =========================

    /**
     * Creates or finds the BELOW_NAME objective.
     */
    private void initObjective() {
        // Use the main scoreboard (Bukkit.getScoreboardManager().getMainScoreboard())
        // to ensure BELOW_NAME is visible to all players
        scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        // Unregister existing objective if it exists
        Objective existing = scoreboard.getObjective(OBJECTIVE_NAME);
        if (existing != null) {
            existing.unregister();
        }

        // Create new objective with BELOW_NAME display slot
        objective = scoreboard.registerNewObjective(
                OBJECTIVE_NAME,
                OBJECTIVE_CRITERIA,
                Component.text("BelowName") // display name (not shown in BELOW_NAME slot)
        );
        objective.setDisplaySlot(DisplaySlot.BELOW_NAME);
    }

    /**
     * Cleans up the objective on shutdown/reload.
     */
    private void cleanupObjective() {
        if (objective != null) {
            try {
                objective.unregister();
            } catch (Exception ignored) {}
            objective = null;
        }
    }

    // =========================
    // TICK
    // =========================

    @Override
    public void run() {
        if (!enabled || objective == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline()) continue;

            try {
                String resolved = PlaceholderResolver.resolve(format, player);
                Component component = MessageUtil.parse(resolved);

                // Get or create the score entry for this player
                Score score = objective.getScore(player.getName());
                score.setScore(0); // dummy value, custom name overrides display

                // Set custom name — this replaces the number with formatted text
                // Paper API: Score.setCustomName(@Nullable Component)
                score.customName(component);
            } catch (Exception e) {
                // Silently skip on error (player might have disconnected)
            }
        }
    }
}
