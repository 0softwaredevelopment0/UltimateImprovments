package com.mcplugin.infrastructure.listeners;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.MessageUtil;
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
            Main.getInstance().getLogger().info("[MOTD] Loaded server icon: motd.png");
        } catch (Exception e) {
            Main.getInstance().getLogger().warning("[MOTD] Failed to load motd.png (must be 64×64 PNG): " + e.getMessage());
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
        String mode = config.getString("motd.online_counter.mode", "normal");
        int realOnline = Bukkit.getOnlinePlayers().size();

        int count = realOnline;
        int max = Bukkit.getMaxPlayers();

        switch (mode) {
            case "hide":
                count = 0;
                break;
            case "fixed":
                count = Math.max(0, config.getInt("motd.online_counter.online_value", 0));
                break;
            case "percent":
                count = realOnline + (realOnline * Math.max(0, config.getInt("motd.online_counter.online_percent", 20)) / 100);
                break;
            case "add":
                count = realOnline + Math.max(0, config.getInt("motd.online_counter.online_add", 5));
                break;
            case "random":
                count = getCachedRandomOnline(config);
                break;
            default:
                // normal — ничего не меняем
                break;
        }

        // Apply max mode (теперь тоже поддерживает random — независимо от online)
        String maxMode = config.getString("motd.online_counter.max_mode", "normal");
        switch (maxMode) {
            case "hide":
                max = 0;
                break;
            case "fixed":
                max = Math.max(0, config.getInt("motd.online_counter.max_value", 100));
                break;
            case "percent":
                max = Bukkit.getMaxPlayers() + (Bukkit.getMaxPlayers() * Math.max(0, config.getInt("motd.online_counter.max_percent", 0)) / 100);
                break;
            case "add":
                max = Bukkit.getMaxPlayers() + Math.max(0, config.getInt("motd.online_counter.max_add", 0));
                break;
            case "random":
                max = getCachedRandomMax(config);
                break;
            default:
                // normal — ничего не меняем
                break;
        }

        event.setNumPlayers(count);
        event.setMaxPlayers(max);
    }

    /**
     * Возвращает кэшированное рандомное значение онлайна, обновляя его
     * раз в update_interval_ticks (независимо от max).
     */
    private int getCachedRandomOnline(FileConfiguration config) {
        long now = System.currentTimeMillis();
        int intervalTicks = Math.max(1, config.getInt("motd.online_counter.update_interval_ticks", 100));
        long intervalMs = intervalTicks * 50L; // 1 тик = ~50 мс

        if (now - lastRandomUpdateOnline >= intervalMs) {
            int min = Math.max(0, config.getInt("motd.online_counter.online_min", 10));
            int max = Math.max(min + 1, config.getInt("motd.online_counter.online_max", 100));
            cachedRandomOnline = RANDOM.nextInt(max - min + 1) + min;
            lastRandomUpdateOnline = now;
        }
        return cachedRandomOnline;
    }

    /**
     * Возвращает кэшированное рандомное значение максимального онлайна,
     * обновляя его раз в update_interval_ticks (независимо от online).
     */
    private int getCachedRandomMax(FileConfiguration config) {
        long now = System.currentTimeMillis();
        int intervalTicks = Math.max(1, config.getInt("motd.online_counter.update_interval_ticks", 100));
        long intervalMs = intervalTicks * 50L; // 1 тик = ~50 мс

        if (now - lastRandomUpdateMax >= intervalMs) {
            int min = Math.max(1, config.getInt("motd.online_counter.max_min", 50));
            int max = Math.max(min + 1, config.getInt("motd.online_counter.max_max", 500));
            cachedRandomMax = RANDOM.nextInt(max - min + 1) + min;
            lastRandomUpdateMax = now;
        }
        return cachedRandomMax;
    }
}
