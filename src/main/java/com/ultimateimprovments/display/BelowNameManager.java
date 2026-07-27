package com.ultimateimprovments.display;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.PlaceholderResolver;
import com.ultimateimprovments.util.ConsoleLogger;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BelowNameManager — отображает произвольный текст под ником каждого игрока
 * через {@link NumberFormat#fixed(Component)} на Objective с DisplaySlot.BELOW_NAME.
 * <p>
 * Использует Paper API {@code objective.numberFormat(NumberFormat.fixed(component))},
 * который заменяет стандартное число в слоте BELOW_NAME на любой MiniMessage-текст.
 * <p>
 * Для поддержки разных значений у разных игроков использует пер-плеер скорборды:
 * каждый игрок получает свой скорборд, в котором BELOW_NAME objective показывает
 * текст, соответствующий этому игроку.
 * <p>
 * Формат (MiniMessage — настраивается в config.yml):
 * <pre>
 * belowname:
 *   enabled: true
 *   format: "&lt;red&gt;❤️ &lt;white&gt;%player_health% &lt;dark_gray&gt;|&lt;/dark_gray&gt; &lt;gold&gt;🔥 &lt;white&gt;%player_food%&lt;/white&gt;"
 *   update_interval_ticks: 20
 * </pre>
 * <p>
 * Плейсхолдеры:
 * <ul>
 *   <li>{@code %player_health%} — текущее здоровье игрока (double)</li>
 *   <li>{@code %player_health_rounded%} — здоровье, округлённое до целого</li>
 *   <li>{@code %player_max_health%} — максимальное здоровье</li>
 *   <li>{@code %player_food%} — уровень сытости (0-20)</li>
 *   <li>{@code %player_saturation%} — насыщение</li>
 *   <li>{@code %player_level%} — уровень опыта</li>
 *   <li>{@code %player_ping%} — пинг</li>
 *   <li>{@code %player_name%} — имя игрока</li>
 * </ul>
 */
public class BelowNameManager extends BukkitRunnable implements Listener {

    private static BelowNameManager instance;

    /** Имя objective для BELOW_NAME (макс 16 символов). */
    private static final String OBJECTIVE_NAME = "bn";

    private boolean enabled;
    private String format;
    private int intervalTicks;

    /** Кэш пер-плеер скорбордов — каждый игрок видит свой текст. */
    private final Map<UUID, Scoreboard> playerBoards = new HashMap<>();

    public static void init() {
        instance = new BelowNameManager();
        instance.reloadConfig();
        Main.getInstance().getServer().getPluginManager().registerEvents(instance, Main.getInstance());
        if (instance.enabled) {
            ConsoleLogger.info("[BelowName] Initialized (BELOW_NAME via NumberFormat.fixed)");
        }
    }

    public static void shutdown() {
        if (instance != null) {
            instance.cancel();
            instance.playerBoards.clear();
            // Сбрасываем скорборды игроков на main, чтобы не осталось мусора
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
            instance = null;
        }
    }

    public static void reload() {
        shutdown();
        init();
    }

    private void reloadConfig() {
        FileConfiguration config = Main.getInstance().getConfig();

        this.enabled = config.getBoolean("belowname.enabled", false);
        this.format = config.getString("belowname.format",
                "<red>❤️ <white>%player_health_rounded% <dark_gray>|</dark_gray> <gold>🔥 <white>%player_food%</white>");
        this.intervalTicks = Math.max(5, config.getInt("belowname.update_interval_ticks", 20));

        if (enabled) {
            this.runTaskTimer(Main.getInstance(), 20L, intervalTicks);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        playerBoards.remove(e.getPlayer().getUniqueId());
    }

    // ════════════════════════════════════════
    // RUN — каждые N тиков обновляем BELOW_NAME
    // ════════════════════════════════════════
    @Override
    public void run() {
        if (!enabled) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline()) continue;

            try {
                // Получаем или создаём пер-плеер скорборд
                Scoreboard board = playerBoards.computeIfAbsent(
                        player.getUniqueId(),
                        k -> createBoard(player)
                );

                // Обновляем текст BELOW_NAME
                updateBelowName(board, player);

                // Устанавливаем скорборд игроку
                player.setScoreboard(board);

            } catch (Exception e) {
                ConsoleLogger.warn("[BelowName] Error updating for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Создаёт новый скорборд с BELOW_NAME objective для игрока.
     */
    private Scoreboard createBoard(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective(OBJECTIVE_NAME, "dummy",
                net.kyori.adventure.text.Component.text("bn"));
        obj.setDisplaySlot(DisplaySlot.BELOW_NAME);
        return board;
    }

    /**
     * Обновляет FixedFormat на BELOW_NAME objective скорборда игрока.
     */
    private void updateBelowName(Scoreboard board, Player player) {
        Objective objective = board.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            ConsoleLogger.warn("[BelowName] Objective 'bn' not found for " + player.getName());
            return;
        }

        // Резолвим плейсхолдеры
        String resolved = resolvePlaceholders(format, player);

        // Парсим MiniMessage → Component
        Component component = MessageUtil.parse(resolved);

        // Устанавливаем NumberFormat.fixed — вместо числа показываем наш Component
        objective.numberFormat(NumberFormat.fixed(component));
    }

    // ════════════════════════════════════════
    // ПЛЕЙСХОЛДЕРЫ
    // ════════════════════════════════════════
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%([a-z_]+)%");

    private String resolvePlaceholders(String input, Player player) {
        if (input == null || input.isEmpty()) return input;

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String replacement = getPlaceholderValue(placeholder, player);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    private String getPlaceholderValue(String placeholder, Player player) {
        return switch (placeholder) {
            case "player_health" -> String.format("%.1f", player.getHealth());
            case "player_health_rounded" -> String.valueOf((int) Math.ceil(player.getHealth()));
            case "player_max_health" -> String.valueOf((int) player.getMaxHealth());
            case "player_food" -> String.valueOf(player.getFoodLevel());
            case "player_saturation" -> String.format("%.1f", player.getSaturation());
            case "player_level" -> String.valueOf(player.getLevel());
            case "player_ping" -> String.valueOf(player.getPing());
            case "player_name" -> player.getName();
            default -> "%" + placeholder + "%";
        };
    }
}
