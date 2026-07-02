package com.mcplugin.infrastructure.maintenance;

import com.mcplugin.infrastructure.config.MessagesManager;
import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.database.DatabaseManager;
import com.mcplugin.infrastructure.util.MessageUtil;
import com.mcplugin.infrastructure.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Maintenance mode manager.
 * <p>
 * Когда техработы включены, только белый список может зайти на сервер.
 * Все настройки и whitelist хранятся в SQLite (maintenance_whitelist + maintenance_meta),
 * кроме kick_message и текстов сообщений — они в messages.yml.
 */
public class MaintenanceManager implements Listener {

    private static MaintenanceManager instance;

    private boolean maintenanceMode = false;
    private BukkitRunnable scheduledTask = null;

    private MaintenanceManager() {}

    public static void init() {
        instance = new MaintenanceManager();
        Bukkit.getPluginManager().registerEvents(instance, Main.getInstance());
        instance.loadFromDb();
    }

    public static MaintenanceManager getInstance() {
        return instance;
    }

    /**
     * Проверяет, включена ли фича техработ в config.yml.
     * Отключает всю систему: /mp maint будет недоступен, вход не блокируется.
     */
    public boolean isFeatureEnabled() {
        return Main.getInstance().getConfig().getBoolean("maintenance.enabled", true);
    }

    // =========================
    // DATABASE
    // =========================

    /**
     * Загружает enabled-флаг из БД.
     * При первом запуске (нет записи в БД) — статус = false (техработы выключены).
     * Флаг config.yml "maintenance.enabled" теперь НЕ влияет на статус —
     * он только включает/выключает саму фичу (проверяется в isFeatureEnabled()).
     */
    public void loadFromDb() {
        try (Connection con = DatabaseManager.getConnection()) {
            try (PreparedStatement st = con.prepareStatement(
                     "SELECT value FROM maintenance_meta WHERE key = ?")) {
                st.setString(1, "enabled");
                try (ResultSet rs = st.executeQuery()) {
                    if (rs.next()) {
                        maintenanceMode = Boolean.parseBoolean(rs.getString("value"));
                    } else {
                        // Первый запуск — начинаем с выключенным статусом.
                        // Раньше тут мигрировалось из config.yml, теперь
                        // config.yml управляет только вкл/выкл фичи, а статус = false.
                        maintenanceMode = false;
                        try (PreparedStatement ins = con.prepareStatement(
                                "INSERT OR REPLACE INTO maintenance_meta (key, value) VALUES (?, ?)")) {
                            ins.setString(1, "enabled");
                            ins.setString(2, "false");
                            ins.executeUpdate();
                        }
                    }
                }
            }
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[Maintenance] Failed to load state from DB", e);
        }
    }

    /**
     * Сохраняет enabled-флаг в БД.
     */
    private void saveStateToDb() {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "INSERT OR REPLACE INTO maintenance_meta (key, value) VALUES (?, ?)")) {
            st.setString(1, "enabled");
            st.setString(2, String.valueOf(maintenanceMode));
            st.executeUpdate();
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[Maintenance] Failed to save state to DB", e);
        }
    }

    // =========================
    // MAINTENANCE STATE
    // =========================

    public boolean isMaintenanceMode() {
        return maintenanceMode;
    }

    /**
     * Включает режим техработ немедленно.
     */
    public void enable() {
        cancelScheduled();
        maintenanceMode = true;
        saveStateToDb();
        kickNonWhitelisted();
        broadcastMaintenance(true, null);
    }

    /**
     * Включает режим техработ через задержку.
     */
    public void enableLater(long delayTicks) {
        cancelScheduled();
        String timeStr = formatTime(delayTicks);
        broadcastScheduled(true, timeStr);
        scheduledTask = new BukkitRunnable() {
            @Override
            public void run() {
                enable();
            }
        };
        scheduledTask.runTaskLater(Main.getInstance(), delayTicks);
    }

    /**
     * Выключает режим техработ немедленно.
     */
    public void disable() {
        cancelScheduled();
        maintenanceMode = false;
        saveStateToDb();
        broadcastMaintenance(false, null);
    }

    /**
     * Выключает режим техработ через задержку.
     */
    public void disableLater(long delayTicks) {
        cancelScheduled();
        String timeStr = formatTime(delayTicks);
        broadcastScheduled(false, timeStr);
        scheduledTask = new BukkitRunnable() {
            @Override
            public void run() {
                disable();
            }
        };
        scheduledTask.runTaskLater(Main.getInstance(), delayTicks);
    }

    public void cancelScheduled() {
        if (scheduledTask != null) {
            scheduledTask.cancel();
            scheduledTask = null;
        }
    }

    public boolean hasScheduledTask() {
        return scheduledTask != null;
    }

    // =========================
    // WHITELIST — SQLite
    // =========================

    public List<String> getWhitelistNames() {
        List<String> names = new ArrayList<>();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "SELECT player_name FROM maintenance_whitelist ORDER BY player_name");
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                names.add(rs.getString("player_name"));
            }
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[Maintenance] Failed to list whitelist", e);
        }
        return names;
    }

    public boolean isWhitelisted(UUID uuid) {
        // Проверяем по имени игрока
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        if (name == null) return false;
        return isWhitelisted(name);
    }

    public boolean isWhitelisted(String playerName) {
        String lower = playerName.toLowerCase().trim();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "SELECT 1 FROM maintenance_whitelist WHERE player_name = ?")) {
            st.setString(1, lower);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.FINE, "[Maintenance] Check error for: " + lower, e);
            return false;
        }
    }

    /**
     * Добавляет игрока в whitelist.
     */
    public boolean addWhitelist(String playerName) {
        if (playerName == null || playerName.isBlank()) return false;
        String lower = playerName.toLowerCase().trim();

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "INSERT OR IGNORE INTO maintenance_whitelist (player_name) VALUES (?)")) {
            st.setString(1, lower);
            int rows = st.executeUpdate();
            if (rows > 0) {
                ConsoleLogger.info("[Maintenance] Added to whitelist: " + lower);
                return true;
            }
            return false; // уже есть
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[Maintenance] Failed to add: " + lower, e);
            return false;
        }
    }

    /**
     * Удаляет игрока из whitelist.
     */
    public boolean removeWhitelist(String playerName) {
        if (playerName == null || playerName.isBlank()) return false;
        String lower = playerName.toLowerCase().trim();

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "DELETE FROM maintenance_whitelist WHERE player_name = ?")) {
            st.setString(1, lower);
            int rows = st.executeUpdate();
            if (rows > 0) {
                ConsoleLogger.info("[Maintenance] Removed from whitelist: " + lower);
                return true;
            }
            return false; // не найден
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[Maintenance] Failed to remove: " + lower, e);
            return false;
        }
    }

    // =========================
    // KICK LOGIC
    // =========================

    private void kickNonWhitelisted() {
        String kickMessage = MessageUtil.legacy(
                MessagesManager.getString("maintenance.kick_message",
                        "<red>⛏ Server is currently under maintenance!</red>\\n<gray>Please come back later.</gray>")
        );
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isWhitelisted(player.getUniqueId())) {
                player.kickPlayer(kickMessage);
            }
        }
    }

    // =========================
    // LOGIN LISTENER
    // =========================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        // Проверка: включена ли фича техработ в config.yml
        if (!isFeatureEnabled() || !maintenanceMode) return;

        Player player = event.getPlayer();
        if (isWhitelisted(player.getUniqueId())) return;

        String kickMessage = MessageUtil.legacy(
                MessagesManager.getString("maintenance.kick_message",
                        "<red>⛏ Server is currently under maintenance!</red>\\n<gray>Please come back later.</gray>")
        );
        event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, kickMessage);
    }

    // =========================
    // BROADCASTS — из messages.yml
    // =========================

    private void broadcastMaintenance(boolean enabled, String timeStr) {
        String key = enabled ? "maintenance.messages.enabled" : "maintenance.messages.disabled";
        String def = enabled
                ? "<red>⛏</red> <white>Maintenance mode </white><green>ENABLED</green>"
                : "<green>✔</green> <white>Maintenance mode </white><red>DISABLED</red>";

        String msg = MessagesManager.getString(key, def);
        Bukkit.broadcast(MessageUtil.parse(msg));
    }

    private void broadcastScheduled(boolean enable, String timeStr) {
        String key = enable ? "maintenance.messages.scheduled_enable" : "maintenance.messages.scheduled_disable";
        String def = enable
                ? "<yellow>⏰</yellow> <white>Maintenance will be enabled in </white><yellow>{time}</yellow>"
                : "<yellow>⏰</yellow> <white>Maintenance will be disabled in </white><yellow>{time}</yellow>";

        String msg = MessagesManager.getString(key, def).replace("{time}", timeStr);
        Bukkit.broadcast(MessageUtil.parse(msg));
    }

    // =========================
    // TIME HELPERS
    // =========================

    private static String formatTime(long ticks) {
        long totalSeconds = ticks / 20;
        if (totalSeconds < 60) {
            return totalSeconds + "s";
        }
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        if (minutes < 60) {
            return minutes + "m " + seconds + "s";
        }
        long hours = minutes / 60;
        minutes = minutes % 60;
        if (hours < 24) {
            return hours + "h " + minutes + "m";
        }
        long days = hours / 24;
        hours = hours % 24;
        return days + "d " + hours + "h";
    }

    public static long parseTimeToTicks(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return -1;
        String lower = timeStr.toLowerCase().trim();
        try {
            if (lower.endsWith("s")) {
                long secs = Long.parseLong(lower.substring(0, lower.length() - 1));
                return secs * 20;
            } else if (lower.endsWith("m")) {
                long mins = Long.parseLong(lower.substring(0, lower.length() - 1));
                return mins * 20 * 60;
            } else if (lower.endsWith("h")) {
                long hours = Long.parseLong(lower.substring(0, lower.length() - 1));
                return hours * 20 * 60 * 60;
            } else if (lower.endsWith("d")) {
                long days = Long.parseLong(lower.substring(0, lower.length() - 1));
                return days * 20 * 60 * 60 * 24;
            }
        } catch (NumberFormatException e) {
            return -1;
        }
        return -1;
    }
}
