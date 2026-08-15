package com.ultimateimprovments.mechanics.security.anticheat.action;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.AlertBroadcast;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovments.mechanics.security.anticheat.core.CheckCategory;
import com.ultimateimprovments.mechanics.security.anticheat.core.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ActionManager — handles actions when a violation threshold is reached.
 * <p>
 * Actions (in increasing severity):
 * 1. LOG — write to console/log
 * 2. NOTIFY — notify administrators
 * 3. SETBACK — teleport the player back to the last valid position
 * <p>
 * Kick and Ban are NOT used — the anti-cheat only flags and sets back.
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
     * Handles a violation and executes the corresponding action.
     *
     * @param player    the player
     * @param checkName the check name
     * @param category  the category
     * @param vl        violation level
     * @param message   the message
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
        // Defaults: notify and setback at 1 VL — any flag immediately sets back
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

    private void executeNotify(Player player, String checkName, double vl, String message) {
        String msg = "<gray>[<white>Server<dark_gray>/<yellow>Warning<gray>] <yellow>" + player.getName()
                + "</yellow> <gray>flagged</gray> <red>" + checkName
                + "</red> <gray>(VL: " + String.format("%.1f", vl) + ") — " + message + "</gray>";

        AlertBroadcast.send(msg);
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
