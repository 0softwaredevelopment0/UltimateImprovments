package com.ultimateimprovments.display;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.database.PlayerSettingsDB;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.PlaceholderResolver;
import com.ultimateimprovments.util.ConsoleLogger;
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
import org.bukkit.scoreboard.Team;

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
                ConsoleLogger.warn("[Scoreboard] Board '" + key + "' has " + lines.size()
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

            // Per-player toggle: если скорборд отключён — прячем
            if (!PlayerSettingsDB.isScoreboardEnabled(player.getUniqueId())) {
                org.bukkit.scoreboard.Scoreboard old = playerBoards.remove(player.getUniqueId());
                if (old != null) {
                    player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
                }
                continue;
            }

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
        // Hide the red score numbers on the right of sidebar entries.
        // Uses reflection because adventure-scoreboard module is not in compile classpath
        // but IS present on Paper 1.21+ server runtime.
        setBlankNumberFormat(objective);

        // Build lines bottom-to-top (score 0 = bottom) using Team prefix/suffix
        // with Component — preserves MiniMessage gradients, unlike legacy §-format
        // which only supports 16 basic Minecraft colours.
        int score = config.lines().size();
        for (String line : config.lines()) {
            String resolved = PlaceholderResolver.resolve(line, player);
            // Unique invisible entry name for this line (just a color code — no visible glyph)
            String entryName = "§" + (char) ('a' + (score % 26));
            Team team = board.registerNewTeam("sbln" + score);
            if (resolved.isEmpty()) {
                // Empty separator — invisible entry, no prefix
                team.prefix(Component.empty());
            } else {
                // Component preserves gradients (<gradient:...>, <rainbow>, etc.)
                team.prefix(MessageUtil.parse(resolved));
            }
            team.addEntry(entryName);
            objective.getScore(entryName).setScore(score);
            score--;
        }

        return board;
    }

    // ── Cached reflection handles for NumberFormat.blankFormat() ──
    private static boolean blankFormatResolved = false;
    private static boolean blankFormatAvailable = false;
    private static Object blankNumberFormat;   // NumberFormat instance (blank)
    private static java.lang.reflect.Method setNumberFormatMethod;

    /**
     * Пытается скрыть цифры-очки справа от строк скорборда через
     * {@code NumberFormat.blankFormat()} (Adventure 5.x / Paper 1.21+).
     * Если метод отсутствует (старый сервер), просто игнорирует — цифры
     * останутся видны, но всё остальное работает.
     * <p>
     * Рефлекшн-методы кэшируются в статику при первом вызове,
     * чтобы не дёргать {@code Class.forName}/{@code getMethod} каждый тик.
     */
    private static void setBlankNumberFormat(Objective objective) {
        if (!blankFormatResolved) {
            resolveBlankFormat();
        }
        if (!blankFormatAvailable) return;
        try {
            setNumberFormatMethod.invoke(objective, blankNumberFormat);
        } catch (Exception ignored) {
            // Не должно падать после успешного resolve, но защита не помешает
        }
    }

    private static void resolveBlankFormat() {
        blankFormatResolved = true;
        try {
            Class<?> nfClass = Class.forName("net.kyori.adventure.scoreboard.NumberFormat");
            // Adventure 4.x → blankFormat(), Adventure 5.x → blank(), пробуем оба
            java.lang.reflect.Method factoryMethod = null;
            try {
                factoryMethod = nfClass.getMethod("blankFormat");
            } catch (NoSuchMethodException e1) {
                try {
                    factoryMethod = nfClass.getMethod("blank");
                } catch (NoSuchMethodException e2) {
                    ConsoleLogger.warn("[Scoreboard] NumberFormat.blank()/blankFormat() not found — scores will be visible");
                    return;
                }
            }
            blankNumberFormat = factoryMethod.invoke(null);
            setNumberFormatMethod = Objective.class.getMethod("setNumberFormat", nfClass);
            blankFormatAvailable = true;
        } catch (Exception e) {
            ConsoleLogger.info("[Scoreboard] NumberFormat not available on this server — scores visible");
        }
    }
}
