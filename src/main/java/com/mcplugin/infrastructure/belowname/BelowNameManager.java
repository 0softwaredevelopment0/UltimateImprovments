package com.mcplugin.infrastructure.belowname;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.MessageUtil;
import com.mcplugin.infrastructure.util.PlaceholderResolver;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

/**
 * Manages the per-player team suffix system — displays formatted text
 * (MiniMessage + placeholders) below each player's nametag in the world.
 * <p>
 * Uses {@code Team.suffix(Component)} with a newline prefix to show custom text
 * below the player name. Unlike the old BELOW_NAME + Score.customName approach
 * (which is limited to integer scores), Team suffix properly renders Components.
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
    /** Prefix for team names, max 3 chars (team names max 16 chars). */
    private static final String TEAM_PREFIX = "bn_";

    private boolean enabled;
    private String format;
    private int intervalTicks;

    public static void init() {
        instance = new BelowNameManager();
        instance.reloadConfig();
    }

    public static void shutdown() {
        if (instance != null) {
            instance.cancel();
            cleanupAllTeams();
            instance = null;
        }
    }

    public static void reload() {
        if (instance != null) {
            instance.cancel();
            cleanupAllTeams();
            instance = null;
        }
        init();
    }

    private void reloadConfig() {
        FileConfiguration config = Main.getInstance().getConfig();

        this.enabled = config.getBoolean("belowname.enabled", false);
        this.format = config.getString("belowname.format", "<red>❤ {player_health}</red>");
        this.intervalTicks = Math.max(5, config.getInt("belowname.update_interval_ticks", 10));

        if (enabled) {
            this.runTaskTimer(Main.getInstance(), 20L, intervalTicks);
        }
    }

    /**
     * Unregisters ALL belowname teams from the main scoreboard.
     * Called on shutdown and before reload to clean up stale entries.
     */
    private static void cleanupAllTeams() {
        try {
            ScoreboardManager mgr = Bukkit.getScoreboardManager();
            if (mgr == null) return;
            Scoreboard sb = mgr.getMainScoreboard();
            for (Team team : sb.getTeams()) {
                if (team.getName().startsWith(TEAM_PREFIX)) {
                    team.unregister();
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void run() {
        if (!enabled) return;

        ScoreboardManager mgr = Bukkit.getScoreboardManager();
        if (mgr == null) return;
        Scoreboard sb = mgr.getMainScoreboard();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline()) continue;

            try {
                String resolved = PlaceholderResolver.resolve(format, player);
                Component component = MessageUtil.parse(resolved);

                // Build safe team name from UUID hash (always 11 chars, no collisions)
                String teamName = TEAM_PREFIX + Integer.toHexString(player.getUniqueId().hashCode());

                // Get or create the player's team
                Team team = sb.getTeam(teamName);
                if (team == null) {
                    team = sb.registerNewTeam(teamName);
                    team.addEntry(player.getName());
                }

                // Set suffix: newline + formatted belowname text
                // Component.newline() creates a line break in the nametag,
                // pushing the belowname text to the row below the player name.
                team.suffix(Component.newline().append(component));

            } catch (Exception e) {
                // Silently skip on error (player might have disconnected)
            }
        }
    }
}
