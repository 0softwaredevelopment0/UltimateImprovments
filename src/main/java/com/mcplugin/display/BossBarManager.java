package com.mcplugin.display;

import com.mcplugin.core.Main;
import com.mcplugin.database.PlayerSettingsDB;
import com.mcplugin.util.MessageUtil;
import com.mcplugin.util.PlaceholderResolver;
import com.mcplugin.util.ConsoleLogger;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Configurable bossbar with progress modes.
 * <p>
 * Supports per-player toggle via PlayerSettingsDB.
 * Progress modes: STATIC, INCREASE_DECREASE, INCREASE_RESET, DECREASE_INCREASE, DECREASE_RESET, RANDOM
 */
public class BossBarManager extends BukkitRunnable {

    private static BossBarManager instance;
    private final Map<UUID, BossBar> playerBars = new HashMap<>();

    private boolean enabled;
    private BossBar.Color color;
    private BossBar.Overlay style;
    private ProgressMode progressMode;
    private String format;
    private int intervalTicks;

    private double progress = 0.5;
    private boolean progressIncreasing = true;
    private double progressSpeed = 0.005;

    public enum ProgressMode {
        STATIC,
        INCREASE_DECREASE,
        INCREASE_RESET,
        DECREASE_INCREASE,
        DECREASE_RESET,
        RANDOM
    }

    public static void init() {
        instance = new BossBarManager();
        instance.reloadConfig();
    }

    public static void shutdown() {
        if (instance != null) {
            instance.cancel();
            instance.removeAllBars();
            instance = null;
        }
    }

    public static void reload() {
        if (instance != null) {
            instance.cancel();
            instance.removeAllBars();
            instance.reloadConfig();
        } else {
            init();
        }
    }

    public static BossBarManager getInstance() {
        return instance;
    }

    private void reloadConfig() {
        FileConfiguration config = Main.getInstance().getConfig();

        this.enabled = config.getBoolean("bossbar.enabled", false);
        this.intervalTicks = Math.max(5, config.getInt("bossbar.update_interval_ticks", 20));
        this.format = config.getString("bossbar.format", "<white>MC-Plugin</white>");

        // Color
        String colorStr = config.getString("bossbar.color", "YELLOW").toUpperCase();
        try {
            this.color = BossBar.Color.valueOf(colorStr);
        } catch (IllegalArgumentException e) {
            this.color = BossBar.Color.YELLOW;
        }

        // Style (overlay) — map user-friendly names to Adventure API enum
        this.style = parseOverlay(config.getString("bossbar.style", "SOLID"));

        // Progress mode
        String modeStr = config.getString("bossbar.progress_mode", "STATIC").toUpperCase().replace("-", "_");
        try {
            this.progressMode = ProgressMode.valueOf(modeStr);
        } catch (IllegalArgumentException e) {
            this.progressMode = ProgressMode.STATIC;
        }

        // Init progress direction from config
        this.progressIncreasing = config.getBoolean("bossbar.progress_increasing", true);
        this.progressSpeed = Math.max(0.0, config.getDouble("bossbar.progress_speed", 0.005));

        if (enabled) {
            this.runTaskTimer(Main.getInstance(), 20L, intervalTicks);
        }
    }

    // =========================
    // BOSS BAR MANAGEMENT
    // =========================

    /**
     * Gets or creates a bossbar for a player.
     */
    private BossBar getOrCreateBar(Player player) {
        BossBar bar = playerBars.get(player.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(Component.text(""), 0f, color, style);
            player.showBossBar(bar);
            playerBars.put(player.getUniqueId(), bar);
        }
        return bar;
    }

    /**
     * Removes all bossbars from all players.
     */
    private void removeAllBars() {
        for (Map.Entry<UUID, BossBar> entry : playerBars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.hideBossBar(entry.getValue());
            }
        }
        playerBars.clear();
    }

    // =========================
    // TICK
    // =========================

    @Override
    public void run() {
        // Safety net: перечитываем enabled из конфига на каждый тик.
        // Если кто-то выключил bossbar.enabled в config.yml и сделал reload,
        // но таск по какой-то причине остался запущен — проверка отсечёт.
        if (!Main.getInstance().getConfig().getBoolean("bossbar.enabled", false)) {
            // Если таск всё ещё работает, а конфиг говорит false — останавливаемся
            removeAllBars();
            this.cancel();
            return;
        }

        // Update progress
        updateProgress();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline()) continue;

            // Check per-player toggle
            if (!PlayerSettingsDB.isBossbarEnabled(player.getUniqueId())) {
                BossBar existing = playerBars.remove(player.getUniqueId());
                if (existing != null) {
                    player.hideBossBar(existing);
                }
                continue;
            }

            try {
                String resolved = PlaceholderResolver.resolve(format, player);
                Component component = MessageUtil.parse(resolved);
                BossBar bar = getOrCreateBar(player);
                bar.name(component);
                bar.color(color);
                bar.overlay(style);
                bar.progress((float) progress);
            } catch (Exception e) {
                ConsoleLogger.warn("[BossBar] Tick error for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    // =========================
    // PROGRESS MODES
    // =========================

    private void updateProgress() {
        if (progressMode == ProgressMode.STATIC) {
            progress = (float) Main.getInstance().getConfig().getDouble("bossbar.static_value", 0.5);
            progress = Math.max(0.0, Math.min(1.0, progress));
            return;
        }

        switch (progressMode) {
            case INCREASE_DECREASE -> {
                if (progressIncreasing) {
                    progress += progressSpeed;
                    if (progress >= 1.0) {
                        progress = 1.0;
                        progressIncreasing = false;
                    }
                } else {
                    progress -= progressSpeed;
                    if (progress <= 0.0) {
                        progress = 0.0;
                        progressIncreasing = true;
                    }
                }
            }
            case INCREASE_RESET -> {
                progress += progressSpeed;
                if (progress >= 1.0) {
                    progress = 0.0;
                }
            }
            case DECREASE_INCREASE -> {
                if (progressIncreasing) {
                    progress -= progressSpeed;
                    if (progress <= 0.0) {
                        progress = 0.0;
                        progressIncreasing = false;
                    }
                } else {
                    progress += progressSpeed;
                    if (progress >= 1.0) {
                        progress = 1.0;
                        progressIncreasing = true;
                    }
                }
            }
            case DECREASE_RESET -> {
                progress -= progressSpeed;
                if (progress <= 0.0) {
                    progress = 1.0;
                }
            }
            case RANDOM -> {
                progress = Math.random();
            }
        }

        // Clamp
        progress = Math.max(0.0, Math.min(1.0, progress));
    }

    // =========================
    // OVERLAY MAPPING
    // =========================

    /**
     * Maps user-friendly style names (SOLID, SEGMENTED_6, etc.) to Adventure BossBar.Overlay enum.
     */
    private static BossBar.Overlay parseOverlay(String configValue) {
        if (configValue == null) return BossBar.Overlay.PROGRESS;
        return switch (configValue.toUpperCase()) {
            case "SOLID" -> BossBar.Overlay.PROGRESS;
            case "SEGMENTED_6", "NOTCHED_6" -> BossBar.Overlay.NOTCHED_6;
            case "SEGMENTED_10", "NOTCHED_10" -> BossBar.Overlay.NOTCHED_10;
            case "SEGMENTED_12", "NOTCHED_12" -> BossBar.Overlay.NOTCHED_12;
            case "SEGMENTED_20", "NOTCHED_20" -> BossBar.Overlay.NOTCHED_20;
            default -> BossBar.Overlay.PROGRESS;
        };
    }
}
