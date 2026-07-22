package com.ultimateimprovments.mechanics.security.anticheat;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.mechanics.security.anticheat.action.ActionManager;
import com.ultimateimprovments.mechanics.security.anticheat.core.AbstractCheck;
import com.ultimateimprovments.mechanics.security.anticheat.core.CheckCategory;
import com.ultimateimprovments.mechanics.security.anticheat.core.CheckResult;
import com.ultimateimprovments.mechanics.security.anticheat.core.ExemptionManager;
import com.ultimateimprovments.mechanics.security.anticheat.core.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AntiCheatManager — центральный менеджер античита.
 * <p>
 * Регистрирует все проверки, управляет PlayerData для каждого игрока,
 * обрабатывает результаты проверок и запускает действия (ActionManager).
 */
public class AntiCheatManager {

    private static AntiCheatManager instance;

    // Player data
    private final ConcurrentHashMap<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();

    // Registered checks by category
    private final Map<CheckCategory, List<AbstractCheck>> checksByCategory = new EnumMap<>(CheckCategory.class);
    private final Map<String, AbstractCheck> checksByName = new ConcurrentHashMap<>();

    // Global enabled flag (runtime toggle) — по дефолту ВЫКЛ
    private volatile boolean globalEnabled = false;
    private static final String CONFIG_ENABLED_PATH = "anticheat.enabled";

    // VL decay task
    private int decayTaskId = -1;

    private AntiCheatManager() {
        for (CheckCategory cat : CheckCategory.values()) {
            checksByCategory.put(cat, new ArrayList<>());
        }
    }

    public static void init() {
        if (instance != null) return;
        instance = new AntiCheatManager();

        ExemptionManager.init();
        ActionManager.init();

        ConsoleLogger.info("[AntiCheat] Manager initialized.");
    }

    public static AntiCheatManager getInstance() {
        return instance;
    }

    // =========================
    // CHECK REGISTRATION
    // =========================

    public void registerCheck(AbstractCheck check) {
        checksByCategory.get(check.getCategory()).add(check);
        checksByName.put(check.getName(), check);

        // Register as listener if it implements Listener
        if (check instanceof Listener) {
            Bukkit.getPluginManager().registerEvents((Listener) check, Main.getInstance());
        }

        check.onInit();
        ConsoleLogger.info("[AntiCheat] Registered: " + check.getCategory() + "/" + check.getName()
                + (check.isEnabled() ? " (enabled)" : " (disabled)"));
    }

    public AbstractCheck getCheck(String name) {
        return checksByName.get(name);
    }

    public List<AbstractCheck> getChecksByCategory(CheckCategory category) {
        return Collections.unmodifiableList(checksByCategory.getOrDefault(category, Collections.emptyList()));
    }

    public List<AbstractCheck> getAllChecks() {
        List<AbstractCheck> all = new ArrayList<>();
        for (List<AbstractCheck> list : checksByCategory.values()) {
            all.addAll(list);
        }
        return all;
    }

    // =========================
    // PLAYER DATA
    // =========================

    public PlayerData getPlayerData(Player player) {
        if (player == null) return null;
        return playerDataMap.get(player.getUniqueId());
    }

    public PlayerData getOrCreatePlayerData(Player player) {
        return playerDataMap.computeIfAbsent(player.getUniqueId(), id -> new PlayerData(player));
    }

    public void removePlayerData(UUID uuid) {
        playerDataMap.remove(uuid);
    }

    // =========================
    // VIOLATION HANDLING
    // =========================

    /**
     * Обрабатывает результат проверки и выполняет действие если нужно.
     */
    public void handleResult(Player player, AbstractCheck check, CheckResult result) {
        if (!globalEnabled) return;
        if (player == null || !player.isOnline() || result == null || !result.isFlagged()) return;

        PlayerData data = getOrCreatePlayerData(player);
        double totalVl = data.getVl(check.getName());

        ActionManager.getInstance().handleViolation(
                player, check.getName(), check.getCategory(), totalVl, result.getMessage());
    }

    // =========================
    // VL DECAY TASK
    // =========================

    public void startDecayTask() {
        decayTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(Main.getInstance(), () -> {
            for (PlayerData data : playerDataMap.values()) {
                for (AbstractCheck check : getAllChecks()) {
                    if (check.getVlDecay() > 0) {
                        data.decayVl(check.getName(), check.getVlDecay());
                    }
                }
            }
        }, 20L, 20L).getTaskId(); // every 1 second
    }

    public void stopDecayTask() {
        if (decayTaskId != -1) {
            Bukkit.getScheduler().cancelTask(decayTaskId);
            decayTaskId = -1;
        }
    }

    // =========================
    // RELOAD
    // =========================

    public boolean isGlobalEnabled() { return globalEnabled; }

    public void setGlobalEnabled(boolean enabled) {
        this.globalEnabled = enabled;
        ConsoleLogger.info("[AntiCheat] " + (enabled ? "ENABLED" : "DISABLED"));
    }

    public void reloadAll() {
        // Sync globalEnabled from config on reload
        this.globalEnabled = Main.getInstance().getConfig().getBoolean(CONFIG_ENABLED_PATH, false);
        for (AbstractCheck check : getAllChecks()) {
            check.loadConfig();
            check.onReload();
        }
        ConsoleLogger.info("[AntiCheat] All checks reloaded.");
    }

    // =========================
    // SHUTDOWN
    // =========================

    public static void shutdown() {
        if (instance == null) return;
        instance.stopDecayTask();
        instance.playerDataMap.clear();
        instance.checksByName.clear();
        instance.checksByCategory.clear();
        ActionManager.shutdown();
        ExemptionManager.shutdown();
        instance = null;
        ConsoleLogger.info("[AntiCheat] Manager shut down.");
    }
}
