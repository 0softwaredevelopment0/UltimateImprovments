package com.mcplugin.infrastructure.listeners;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.MessageUtil;
import com.mcplugin.infrastructure.util.ConsoleLogger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.util.CachedServerIcon;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Обработчик ServerListPingEvent — кастомный MOTD, иконка, список игроков и счётчик онлайна.
 * <p>
 * Конфигурация в config.yml → секция motd:
 * <ul>
 *   <li>enabled — вкл/выкл MOTD</li>
 *   <li>line1 / line2 — текст MOTD (MiniMessage)</li>
 *   <li>icon_enabled — показывать ли motd.png</li>
 *   <li>player_list — кастомные строки вместо списка игроков</li>
 *   <li>online_counter — управление счётчиком X/Y (normal/hide/fixed/percent/add/random)</li>
 * </ul>
 */
public class MOTDListener implements Listener {

    private static final Random RANDOM = new Random();

    private CachedServerIcon cachedIcon;
    private boolean iconLoaded = false;

    // ── Random counter caching (независимо для online и max) ──
    private long lastRandomUpdateOnline = 0;
    private long lastRandomUpdateMax = 0;
    private int cachedRandomOnline = 0;
    private int cachedRandomMax = 0;

    public MOTDListener() {
        loadIcon();
    }

    /**
     * Загружает иконку motd.png из папки плагина.
     * Можно вызвать повторно для перезагрузки (например при /mp reload).
     */
    public void loadIcon() {
        File iconFile = new File(Main.getInstance().getDataFolder(), "motd.png");
        if (!iconFile.exists()) {
            this.iconLoaded = false;
            this.cachedIcon = null;
            return;
        }

        try {
            this.cachedIcon = Bukkit.getServer().loadServerIcon(iconFile);
            this.iconLoaded = true;
            ConsoleLogger.info("[MOTD] Loaded server icon: motd.png");
        } catch (Exception e) {
            ConsoleLogger.warn("[MOTD] Failed to load motd.png (must be 64×64 PNG): " + e.getMessage());
            this.iconLoaded = false;
            this.cachedIcon = null;
        }
    }

    @EventHandler
    public void onServerListPing(PaperServerListPingEvent event) {
        var config = Main.getInstance().getConfig();

        if (!config.getBoolean("motd.enabled", false)) {
            return;
        }

        // =========================
        // MOTD TEXT
        // =========================
        applyMotdText(event, config);

        // =========================
        // SERVER ICON
        // =========================
        if (config.getBoolean("motd.icon_enabled", true) && iconLoaded) {
            event.setServerIcon(cachedIcon);
        }

        // =========================
        // CUSTOM PLAYER LIST (sample)
        // =========================
        applyPlayerList(event, config);

        // =========================
        // ONLINE COUNTER
        // =========================
        applyOnlineCounter(event, config);
    }

    // =========================================================
    //  MOTD TEXT
    // =========================================================

    private void applyMotdText(PaperServerListPingEvent event, FileConfiguration config) {
        String line1 = config.getString("motd.line1", "");
        String line2 = config.getString("motd.line2", "");

        if (line1.isEmpty() && line2.isEmpty()) return;

        Component motd;
        if (line2.isEmpty()) {
            motd = MessageUtil.parse(line1);
        } else {
            motd = Component.textOfChildren(
                    MessageUtil.parse(line1),
                    Component.newline(),
                    MessageUtil.parse(line2)
            );
        }
        event.motd(motd);
    }

    // =========================================================
    //  CUSTOM PLAYER LIST  (кастомные строки вместо ников)
    // =========================================================

    private void applyPlayerList(PaperServerListPingEvent event, FileConfiguration config) {
        if (!config.getBoolean("motd.player_list.enabled", false)) return;

        List<String> lines = config.getStringList("motd.player_list.lines");
        if (lines.isEmpty()) return;

        List<PaperServerListPingEvent.ListedPlayerInfo> sample = event.getListedPlayers();
        sample.clear();
        for (String line : lines) {
            // Конвертируем MiniMessage в plain text (без цветовых кодов)
            String plain = MessageUtil.toPlainText(line);
            // Обрезаем до 16 символов (лимит Minecraft на длину ника)
            if (plain.length() > 16) {
                plain = plain.substring(0, 16);
            }
            sample.add(new PaperServerListPingEvent.ListedPlayerInfo(plain, UUID.randomUUID()));
        }
    }

    // =========================================================
    //  ONLINE COUNTER (режимы: normal, hide, fixed, percent, add, random)
    // =========================================================

    private void applyOnlineCounter(PaperServerListPingEvent event, FileConfiguration config) {
        int realOnline = Bukkit.getOnlinePlayers().size();

        // ═════════════════════════════════════════════════════
        //  CURRENT ONLINE  (X в "X/Y")
        // ═════════════════════════════════════════════════════
        int count = applyCounterSection(
                config,
                "motd.online_counter.current_online",
                realOnline
        );

        // ═════════════════════════════════════════════════════
        //  MAX ONLINE  (Y в "X/Y")
        // ═════════════════════════════════════════════════════
        int max = applyCounterSection(
                config,
                "motd.online_counter.max_online",
                Bukkit.getMaxPlayers()
        );

        event.setNumPlayers(count);
        event.setMaxPlayers(max);
    }

    /**
     * Универсальный метод для применения режима счётчика к одному значению.
     *
     * @param config     конфиг
     * @param basePath   путь в конфиге (например "motd.online_counter.current_online")
     * @param realValue  реальное значение (онлайн или макс)
     * @return вычисленное значение
     */
    private int applyCounterSection(FileConfiguration config, String basePath, int realValue) {
        String mode = config.getString(basePath + ".mode", "normal");

        return switch (mode) {
            case "hide" -> 0;
            case "fixed" -> Math.max(0, config.getInt(basePath + ".value", 0));
            case "scale" -> Math.max(0, realValue * Math.max(0, config.getInt(basePath + ".scale", 50)) / 100);
            case "percent" -> realValue + (realValue * Math.max(0, config.getInt(basePath + ".percent", 20)) / 100);
            case "add" -> realValue + Math.max(0, config.getInt(basePath + ".add", 5));
            case "random" -> getCachedRandom(config, basePath);
            default -> realValue; // normal
        };
    }

    /**
     * Кэшированное рандомное значение для секции счётчика.
     * Каждая секция (current_online / max_online) имеет свой таймер и кэш.
     */
    private int getCachedRandom(FileConfiguration config, String basePath) {
        long now = System.currentTimeMillis();
        int intervalTicks = Math.max(1, config.getInt(basePath + ".update_interval_ticks", 100));
        long intervalMs = intervalTicks * 50L;

        // Определяем, какая это секция по пути, чтобы использовать свой кэш
        boolean isMax = basePath.contains("max_online");
        long lastUpdate = isMax ? lastRandomUpdateMax : lastRandomUpdateOnline;
        int cachedValue = isMax ? cachedRandomMax : cachedRandomOnline;

        if (now - lastUpdate >= intervalMs) {
            int min = Math.max(isMax ? 1 : 0, config.getInt(basePath + ".min", isMax ? 50 : 10));
            int max = Math.max(min + 1, config.getInt(basePath + ".max", isMax ? 500 : 100));
            cachedValue = RANDOM.nextInt(max - min + 1) + min;

            if (isMax) {
                cachedRandomMax = cachedValue;
                lastRandomUpdateMax = now;
            } else {
                cachedRandomOnline = cachedValue;
                lastRandomUpdateOnline = now;
            }
        }
        return cachedValue;
    }
}
