package com.ultimateimprovements.mechanics.security.anticheat.action;

import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.util.ConsoleLogger;
import com.ultimateimprovements.util.MessageUtil;
import com.ultimateimprovements.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovements.mechanics.security.anticheat.core.CheckCategory;
import com.ultimateimprovements.mechanics.security.anticheat.core.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ActionManager — обрабатывает действия при достижении порога нарушений.
 * <p>
 * Действия (по возрастанию строгости):
 * 1. LOG — запись в консоль/лог
 * 2. NOTIFY — уведомление администраторам
 * 3. SETBACK — откат игрока на последнюю валидную позицию
 * <p>
 * Kick и Ban НЕ используются — античит только флагает и откатывает.
 */
public class ActionManager {

    private static ActionManager instance;

    // Cooldowns to prevent spam: UUID → last action time
    private final ConcurrentHashMap<UUID, Long> actionCooldowns = new ConcurrentHashMap<>();
    private long cooldownMs = 1000; // 1 second between actions per player

    // Log file
    private final java.util.logging.Logger logFile = Main.getInstance().getLogger();

    private ActionManager() {}

    public static void init() {
        instance = new ActionManager();
    }

    public static ActionManager getInstance() {
        return instance;
    }

    /**
     * Обрабатывает нарушение и выполняет соответствующее действие.
     *
     * @param player    игрок
     * @param checkName имя проверки
     * @param category  категория
     * @param vl        violation level
     * @param message   сообщение
     */
    public void handleViolation(Player player, String checkName, CheckCategory category, double vl, String message) {
        if (player == null || !player.isOnline()) return;

        UUID uuid = player.getUniqueId();

        // Cooldown check
        long now = System.currentTimeMillis();
        Long lastAction = actionCooldowns.get(uuid);
        if (lastAction != null && now - lastAction < cooldownMs) return;
        actionCooldowns.put(uuid, now);

        var cfg = Main.getInstance().getConfig();
        String basePath = "anticheat.actions";

        // Always log
        if (cfg.getBoolean(basePath + ".log.enabled", true)) {
            ConsoleLogger.raw("<gray>[<white>Server<dark_gray>/<yellow>Warning<gray>]</dark_gray> " + player.getName() + " flagged " + checkName
                    + " (VL: " + String.format("%.1f", vl) + ") — " + message);
        }

        // Determine action based on VL thresholds
        // Дефолты: notify и setback при 1 VL — любой флаг сразу setback'ает
        double notifyVl = cfg.getDouble(basePath + ".notify.vl_threshold", 1.0);
        double setbackVl = cfg.getDouble(basePath + ".setback.vl_threshold", 1.0);

        if (vl >= setbackVl) {
            executeSetback(player, checkName, vl);
        } else if (vl >= notifyVl) {
            executeNotify(player, checkName, vl, message);
        }
    }

    // =========================
    // ACTIONS
    // =========================

    private void executeSetback(Player player, String checkName, double vl) {
        PlayerData data = AntiCheatManager.getInstance().getPlayerData(player);
        if (data == null) return;

        Location safe = data.getLastGroundLocation();
        if (safe != null) {
            Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                if (player.isOnline()) {
                    player.teleport(safe);
                    player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                }
            });
        }
    }

    private void executeNotify(Player player, String checkName, double vl, String message) {            String msg = "<gray>[<white>Server<dark_gray>/<yellow>Warning<gray>] <yellow>" + player.getName()
                + "</yellow> <gray>flagged</gray> <red>" + checkName
                + "</red> <gray>(VL: " + String.format("%.1f", vl) + ") — " + message + "</gray>";

        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("mcplugin.anticheat.notify") || p.isOp())
                .forEach(p -> p.sendMessage(MessageUtil.parse(msg)));
    }

    // =========================
    // COOLDOWN
    // =========================

    public void setCooldownMs(long ms) {
        this.cooldownMs = ms;
    }

    public void clearCooldown(UUID uuid) {
        actionCooldowns.remove(uuid);
    }

    public static void shutdown() {
        if (instance != null) {
            instance.actionCooldowns.clear();
            instance = null;
        }
    }
}
