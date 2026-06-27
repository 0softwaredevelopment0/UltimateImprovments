package com.mcplugin.mechanics.security.botprotect;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.config.MessagesManager;
import com.mcplugin.infrastructure.database.DatabaseManager;
import com.mcplugin.infrastructure.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Защита от ботов / флуда входом.
 * <p>
 * Две фичи:
 * <ol>
 *   <li><b>Очередь входа</b> — если больше N игроков пытаются зайти за окно времени,
 *       лишние получают сообщение об очереди. Операторы и консоль уведомляются.</li>
 *   <li><b>Кулдаун реджоина</b> — если игрок вышел и пытается зайти снова слишком быстро,
 *       получает сообщение подождать. Время выхода сохраняется в БД, так что перезапуск
 *       сервера не сбрасывает кулдаун.</li>
 * </ol>
 * <p>
 * Использует {@link AsyncPlayerPreLoginEvent} (асинхронный, до захода игрока).
 * Все Bukkit API вызовы синхронизируются через {@code runTask()}.
 */
public class BotProtectionListener implements Listener {

    private final Main plugin;

    // ── Feature 1: Sliding window join attempt timestamps ──
    private final ConcurrentLinkedDeque<Long> joinTimestamps = new ConcurrentLinkedDeque<>();
    private final AtomicLong lastOpNotify = new AtomicLong(0);

    // ── Feature 2: Player quit times (in-memory cache + DB persisted) ──
    private final Map<UUID, Long> quitTimes = new ConcurrentHashMap<>();

    // ── Config cache ──
    private boolean enabled = true;
    private int maxJoinsPerWindow = 2;
    private int windowSeconds = 1;
    private boolean notifyOps = true;
    private int rejoinCooldownSeconds = 3;

    public BotProtectionListener(Main plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * Перезагружает конфиг из config.yml.
     */
    public void loadConfig() {
        var config = plugin.getConfig();
        enabled = config.getBoolean("bot_protection.enabled", true);
        maxJoinsPerWindow = Math.max(1, config.getInt("bot_protection.queue.max_joins_per_window", 2));
        windowSeconds = Math.max(1, config.getInt("bot_protection.queue.window_seconds", 1));
        notifyOps = config.getBoolean("bot_protection.queue.notify_ops", true);
        rejoinCooldownSeconds = Math.max(1, config.getInt("bot_protection.rejoin_cooldown_seconds", 3));
    }

    // =========================================================
    //  DB HELPERS
    // =========================================================

    /**
     * Сохраняет время выхода в БД.
     */
    private void dbSaveQuitTime(UUID uuid, long quitTime) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection con = DatabaseManager.getConnection();
                 PreparedStatement st = con.prepareStatement(
                         "INSERT OR REPLACE INTO bot_protection_cooldowns (uuid, quit_time) VALUES (?, ?)")) {
                st.setString(1, uuid.toString());
                st.setLong(2, quitTime);
                st.executeUpdate();
            } catch (Exception e) {
                plugin.getLogger().warning("[BotProtect] Failed to save quit time to DB: " + e.getMessage());
            }
        });
    }

    /**
     * Удаляет время выхода из БД (после успешного захода или истечения кулдауна).
     */
    private void dbRemoveQuitTime(UUID uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection con = DatabaseManager.getConnection();
                 PreparedStatement st = con.prepareStatement(
                         "DELETE FROM bot_protection_cooldowns WHERE uuid = ?")) {
                st.setString(1, uuid.toString());
                st.executeUpdate();
            } catch (Exception e) {
                plugin.getLogger().warning("[BotProtect] Failed to remove quit time from DB: " + e.getMessage());
            }
        });
    }

    /**
     * Читает время выхода из БД (если в памяти нет — перезагрузка сервера).
     */
    private Long dbLoadQuitTime(UUID uuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "SELECT quit_time FROM bot_protection_cooldowns WHERE uuid = ?")) {
            st.setString(1, uuid.toString());
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("quit_time");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[BotProtect] Failed to load quit time from DB: " + e.getMessage());
        }
        return null;
    }

    // =========================================================
    //  FEATURE 1: JOIN QUEUE (sliding-window rate limiter)
    //  FEATURE 2: REJOIN COOLDOWN (persisted in DB)
    // =========================================================

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!enabled) return;

        UUID uuid = event.getUniqueId();
        String name = event.getName();
        long now = System.currentTimeMillis();

        // ═════════════════════════════════════════════════════
        //  FEATURE 2: Rejoin cooldown
        // ═════════════════════════════════════════════════════
        // Пробуем из in-memory кэша, потом из БД (на случай перезагрузки)
        Long quitTime = quitTimes.remove(uuid);
        if (quitTime == null) {
            quitTime = dbLoadQuitTime(uuid);
        }

        if (quitTime != null) {
            long elapsed = now - quitTime;
            int cooldownMs = rejoinCooldownSeconds * 1000;
            if (elapsed < cooldownMs) {
                long remaining = (cooldownMs - elapsed + 999) / 1000; // округление вверх
                String msg = MessagesManager.getString("bot_protection.rejoin_cooldown",
                        "<red>❌ Вы вышли слишком недавно! Подождите</red> <yellow>{seconds}</yellow> <red>сек перед повторным входом.</red>")
                        .replace("{seconds}", String.valueOf(remaining));
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, MessageUtil.legacy(msg));
                plugin.getLogger().info("[BotProtect] " + name + " rejected: rejoin cooldown (" + remaining + "s remaining)");

                // ВАЖНО: возвращаем quitTime обратно в кэш, чтобы следующий реконнект
                // (в пределах того же кулдауна) тоже был отклонён.
                quitTimes.put(uuid, quitTime);
                return;
            }

            // Кулдаун истёк — удаляем из БД (запись в памяти уже удалена через remove)
            dbRemoveQuitTime(uuid);
        }

        // ═════════════════════════════════════════════════════
        //  FEATURE 1: Join queue
        // ═════════════════════════════════════════════════════

        // Clean old timestamps outside the window
        long cutoff = now - (windowSeconds * 1000L);
        while (!joinTimestamps.isEmpty() && joinTimestamps.peekFirst() < cutoff) {
            joinTimestamps.pollFirst();
        }

        // Add this attempt
        joinTimestamps.addLast(now);

        // Check limit
        int count = joinTimestamps.size();
        if (count > maxJoinsPerWindow) {
            int position = count - maxJoinsPerWindow;
            String msg = MessagesManager.getString("bot_protection.queue_full",
                    "<red>❌ Сервер перегружен! Вы в очереди: позиция #{position}. Подождите и попробуйте снова.</red>")
                    .replace("{position}", String.valueOf(position));
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, MessageUtil.legacy(msg));

            // Notify ops + console (rate-limited to once per window)
            notifyOpsAsync(now, count);

            plugin.getLogger().info("[BotProtect] " + name + " queued: position #" + position
                    + " (" + count + " joins in " + windowSeconds + "s window)");
        }
    }

    /**
     * Уведомляет операторов и консоль о превышении лимита входа (не чаще раза за окно).
     * Безопасно для вызова из асинхронного события — планирует на главный поток.
     */
    private void notifyOpsAsync(long now, int count) {
        if (!notifyOps) return;

        long lastNotify = lastOpNotify.get();
        long notifyCooldown = windowSeconds * 1000L;
        if (now - lastNotify > notifyCooldown) {
            if (lastOpNotify.compareAndSet(lastNotify, now)) {
                String msg = MessagesManager.getString("bot_protection.queue_notify_op",
                        "<yellow>⚠</yellow> <red>Bot protection:</red> <yellow>{count}</yellow> <red>игрок(а) пытались зайти за</red> <yellow>{seconds}s</yellow><red>. Включена очередь.</red>")
                        .replace("{count}", String.valueOf(count))
                        .replace("{seconds}", String.valueOf(windowSeconds));

                // Планируем на главный поток (Bukkit API не thread-safe)
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Console
                    Bukkit.getConsoleSender().sendMessage(MessageUtil.legacy(msg));

                    // Ops
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.isOp()) {
                            player.sendMessage(MessageUtil.parse(msg));
                        }
                    }
                });
            }
        }
    }

    // =========================================================
    //  JOIN / QUIT TRACKING
    // =========================================================

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        // Clear rejoin cooldown if player successfully joined
        quitTimes.remove(uuid);
        dbRemoveQuitTime(uuid);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!enabled) return;
        UUID uuid = event.getPlayer().getUniqueId();
        long now = System.currentTimeMillis();
        quitTimes.put(uuid, now);
        dbSaveQuitTime(uuid, now);
    }
}
