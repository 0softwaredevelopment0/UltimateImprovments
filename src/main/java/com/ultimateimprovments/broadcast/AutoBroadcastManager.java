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
 * 🕐 AutoBroadcastManager — система автоматических броадкастов.
 * <p>
 * Читает секцию {@code auto_broadcast} из config.yml и раз в секунду
 * (20 тиков) проверяет готовность секций. Когда секция «созревает»
 * (накоплено {@code cooldown_ticks}), сообщение отправляется всем
 * онлайн-игрокам, прошедшим условия секции.
 * <p>
 * Особенности:
 * <ul>
 *   <li>Глобальные условия ({@code online-*}) проверяются один раз за цикл;</li>
 *   <li>Игровые условия проверяются отдельно для каждого игрока;</li>
 *   <li>Плейсхолдеры в сообщениях резолвятся ПЕРСОНАЛЬНО для каждого игрока
 *       ({@code %player_name%}, {@code %online%}, любые PlaceholderAPI);</li>
 *   <li>Секций может быть сколько угодно, у каждой своя очередь сообщений.</li>
 * </ul>
 * <p>
 * Жизненный цикл: {@link #start(Main)} при старте/перезагрузке,
 * {@link #stop()} при остановке плагина. {@link #start} идемпотентен
 * (сначала останавливает предыдущую задачу), поэтому безопасно звать
 * повторно при {@code /ui reload}.
 */
public final class AutoBroadcastManager {

    /** Период основного тикера в тиках (1 сек). */
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
     * Запускает систему авто-броадкастов: читает конфиг, разбирает секции,
     * планирует тикер. Идемпотентен — повторный вызов безопасен.
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

    /** Останавливает систему и сбрасывает все секции. */
    public synchronized void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        sections.clear();
        plugin = null;
    }

    /** Перезапуск после перезагрузки конфигурации. */
    public synchronized void reload() {
        if (plugin == null) return;
        start(plugin);
    }

    /** Тикер: раз в секунду накапливаем тики секций и отправляем созревшие. */
    private void tick() {
        for (BroadcastSection section : sections) {
            section.accumulate(TICK_PERIOD);
            if (section.isReady()) {
                fire(section);
            }
        }
    }

    /** Отправляет следующее сообщение секции игрокам, прошедшим условия. */
    private void fire(BroadcastSection section) {
        // Глобальные условия (online-*) — если не выполнены, цикл пропускается целиком
        if (!section.matchesGlobal()) return;

        List<Player> targets = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (section.matches(player)) {
                targets.add(player);
            }
        }

        // Ротация сообщения продвигается ТОЛЬКО при реальной отправке
        if (targets.isEmpty()) return;
        String message = section.nextMessage();

        // Плейсхолдеры резолвим персонально для каждого игрока
        for (Player player : targets) {
            player.sendMessage(MessageUtil.parse(message, player));
        }
    }
}
