package com.ultimateimprovments.display;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.PlaceholderResolver;
import com.ultimateimprovments.util.ConsoleLogger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

/**
 * Manages the per-player team suffix system — displays formatted text
 * (MiniMessage + placeholders) after each player's name (in the nametag).
 * <p>
 * Uses {@code Team.suffix(Component)} with a separator prefix ({@code " • "}).
 * Unlike the old BELOW_NAME + Score.customName approach
 * (which is limited to integer scores), Team suffix properly renders Components.
 * <p>
 * <b>Заметка:</b> Полноценный текст под ником (на второй строке), к сожалению,
 * невозможен через стандартный Bukkit API в 1.21.4 — {@code Component.newline()}
 * рендерится как символ {@code [LF]}. Текст отображается после ника с разделителем.
 * <p>
 * Config example:
 * <pre>
 * belowname:
 *   enabled: true
 *   format: "&lt;red&gt;❤ %player_health%&lt;/red&gt; &lt;dark_gray&gt;|&lt;/dark_gray&gt; &lt;white&gt;%player_food%&lt;/white&gt;"
 *   update_interval_ticks: 10
 * </pre>
 */
public class BelowNameManager extends BukkitRunnable implements Listener {

    private static BelowNameManager instance;
    /** Prefix for team names, max 3 chars (team names max 16 chars). */
    private static final String TEAM_PREFIX = "bn_";

    private boolean enabled;
    private String format;
    private int intervalTicks;

    public static void init() {
        instance = new BelowNameManager();
        instance.reloadConfig();
        Main.getInstance().getServer().getPluginManager().registerEvents(instance, Main.getInstance());
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
        this.format = config.getString("belowname.format", "<red>❤ %player_health%</red>");
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
        } catch (Exception e) {
            ConsoleLogger.warn("[BelowName] cleanup error: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════
    // SHIFT+RMB ON PLAYER — показать здоровье и еду
    // ════════════════════════════════════════
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent e) {
        if (!enabled) return;
        if (!e.getPlayer().isSneaking()) return;
        if (!(e.getRightClicked() instanceof Player target)) return;

        e.setCancelled(true);

        Player viewer = e.getPlayer();
        String healthStr = String.valueOf((int) target.getHealth());
        String foodStr = String.valueOf(target.getFoodLevel());
        String maxHealthStr = String.valueOf((int) target.getMaxHealth());

        Component msg = MessageUtil.parse(
                "<gold>" + target.getName() + "</gold>"
                + " <dark_gray>|</dark_gray> "
                + "<red>❤ " + healthStr + "/" + maxHealthStr + "</red>"
                + " <dark_gray>|</dark_gray> "
                + "<gold>\uD83C\uDF56 " + foodStr + "/20</gold>"
        );
        viewer.sendActionBar(msg);
    }

    // ════════════════════════════════════════
    // RUN — teams cleanup only, no suffix
    // ════════════════════════════════════════
    @Override
    public void run() {
        if (!enabled) return;

        ScoreboardManager mgr = Bukkit.getScoreboardManager();
        if (mgr == null) return;
        Scoreboard sb = mgr.getMainScoreboard();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline()) continue;

            try {
                // Build safe team name from UUID hash
                String teamName = TEAM_PREFIX + Integer.toHexString(player.getUniqueId().hashCode());

                // Ensure team exists (without suffix)
                Team team = sb.getTeam(teamName);
                if (team == null) {
                    team = sb.registerNewTeam(teamName);
                    team.addEntry(player.getName());
                }

            } catch (Exception e) {
                ConsoleLogger.warn("[BelowName] Error updating team: " + e.getMessage());
            }
        }
    }
}
