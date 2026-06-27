package com.mcplugin.infrastructure.scoreboard;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.MessageUtil;
import com.mcplugin.infrastructure.util.PlaceholderResolver;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;

import java.util.*;

/**
 * Менеджер кастомных скорбордов.
 * <p>
 * Поддерживает:
 * <ul>
 *   <li>Несколько конфигураций скорбордов (boards)</li>
 *   <li>Условия отображения: always, world, world_blacklist, permission</li>
 *   <li>MiniMessage + плейсхолдеры в каждой строке</li>
 *   <li>Настраиваемый интервал обновления</li>
 * </ul>
 */
public class ScoreboardManager extends BukkitRunnable implements Listener {

    private static ScoreboardManager instance;
    private boolean enabled;
    private int intervalTicks;
    private List<ScoreboardConfig> boards = new ArrayList<>();
    private final Map<UUID, org.bukkit.scoreboard.Scoreboard> playerBoards = new HashMap<>();

    public record ScoreboardConfig(
            String name,
            ConditionType conditionType,
            List<String> worlds,
            String permission,
            String title,
            List<String> lines
    ) {}

    public enum ConditionType {
        ALWAYS, WORLD, WORLD_BLACKLIST, PERMISSION
    }

    public static void init() {
        instance = new ScoreboardManager();
        Bukkit.getPluginManager().registerEvents(instance, Main.getInstance());
        instance.reloadConfig();
        if (instance.enabled) {
            instance.runTaskTimer(Main.getInstance(), 20L, instance.intervalTicks);
        }
    }

    public static void shutdown() {
        if (instance != null) {
            instance.cancel();
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            }
            instance.playerBoards.clear();
            instance = null;
        }
    }

    public static void reload() {
        if (instance != null) {
            instance.cancel();
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            }
            instance.playerBoards.clear();
            instance.reloadConfig();
            if (instance.enabled) {
                instance.runTaskTimer(Main.getInstance(), 20L, instance.intervalTicks);
            }
        }
    }

    private void reloadConfig() {
        FileConfiguration config = Main.getInstance().getConfig();
        this.enabled = config.getBoolean("scoreboard.enabled", false);
        this.intervalTicks = Math.max(10, config.getInt("scoreboard.update_interval_ticks", 20));
        this.boards = new ArrayList<>();

        ConfigurationSection boardsSection = config.getConfigurationSection("scoreboard.boards");
        if (boardsSection == null) return;

        for (String key : boardsSection.getKeys(false)) {
            ConfigurationSection sec = boardsSection.getConfigurationSection(key);
            if (sec == null) continue;

            String conditionTypeStr = sec.getString("condition.type", "always");
            ConditionType conditionType;
            try {
                conditionType = ConditionType.valueOf(conditionTypeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                conditionType = ConditionType.ALWAYS;
            }

            List<String> worlds = sec.getStringList("condition.worlds");
            String permission = sec.getString("condition.permission", "");
            String title = sec.getString("title", "");
            List<String> lines = sec.getStringList("lines");

            // Ограничение: макс 15 строк для sidebar
            if (lines.size() > 15) {
                Main.getInstance().getLogger().warning("[Scoreboard] Board '" + key + "' has " + lines.size()
                        + " lines, max is 15. Extra lines will be ignored.");
                lines = lines.subList(0, 15);
            }

            boards.add(new ScoreboardConfig(
                    key, conditionType, worlds, permission, title, lines
            ));
        }
    }

    // ── Cleanup on quit ──
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        playerBoards.remove(e.getPlayer().getUniqueId());
    }

    @Override
    public void run() {
        if (!enabled) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline()) continue;

            ScoreboardConfig config = findBestConfig(player);
            if (config == null) {
                org.bukkit.scoreboard.Scoreboard old = playerBoards.remove(player.getUniqueId());
                if (old != null) {
                    player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
                }
                continue;
            }

            org.bukkit.scoreboard.Scoreboard board = buildScoreboard(config, player);
            if (board != null) {
                player.setScoreboard(board);
                playerBoards.put(player.getUniqueId(), board);
            }
        }
    }

    private ScoreboardConfig findBestConfig(Player player) {
        for (ScoreboardConfig config : boards) {
            switch (config.conditionType()) {
                case ALWAYS -> { return config; }
                case WORLD -> {
                    if (config.worlds().isEmpty() || config.worlds().contains(player.getWorld().getName())) {
                        return config;
                    }
                }
                case WORLD_BLACKLIST -> {
                    if (!config.worlds().contains(player.getWorld().getName())) {
                        return config;
                    }
                }
                case PERMISSION -> {
                    if (config.permission().isEmpty() || player.hasPermission(config.permission())) {
                        return config;
                    }
                }
            }
        }
        return null;
    }

    private org.bukkit.scoreboard.Scoreboard buildScoreboard(ScoreboardConfig config, Player player) {
        if (config.title() == null) return null;

        String resolvedTitle = PlaceholderResolver.resolve(config.title(), player);
        if (resolvedTitle.isEmpty()) return null;

        org.bukkit.scoreboard.Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        // Objective name limited to 16 chars in Bukkit API
        String objName = "sb_" + config.name();
        if (objName.length() > 16) objName = objName.substring(0, 16);
        // Title supports Component (Paper API)
        Objective objective = board.registerNewObjective(objName, "dummy", MessageUtil.parse(resolvedTitle));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Build lines bottom-to-top (score 0 = bottom)
        int score = config.lines().size();
        for (String line : config.lines()) {
            String resolved = PlaceholderResolver.resolve(line, player);
            String display;
            if (resolved.isEmpty()) {
                // Пустой разделитель — §-код для уникальности + невидимости
                display = "§" + (char) ('a' + (score % 26));
            } else {
                // Конвертируем MiniMessage в §-формат для scoreboard (getScore принимает String)
                display = MessageUtil.legacy(resolved);
                if (display.length() > 40) {
                    display = display.substring(0, 40);
                }
            }
            objective.getScore(display).setScore(score);
            score--;
        }

        return board;
    }
}
