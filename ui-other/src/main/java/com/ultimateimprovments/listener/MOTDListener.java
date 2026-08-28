package com.ultimateimprovments.listener;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.ConsoleLogger;
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
 * ServerListPingEvent handler — custom MOTD, icon, player list and online counter.
 * <p>
 * Configured in config.yml → the motd section:
 * <ul>
 *   <li>enabled — toggle the MOTD on/off</li>
 *   <li>line1 / line2 — MOTD text (MiniMessage)</li>
 *   <li>icon_enabled — whether to show server-icon.png</li>
 *   <li>player_list — custom lines instead of the player list</li>
 *   <li>online_counter — controlling the X/Y counter (normal/hide/fixed/percent/add/random)</li>
 * </ul>
 */
public class MOTDListener implements Listener {

    private static final Random RANDOM = new Random();

    private CachedServerIcon cachedIcon;
    private boolean iconLoaded = false;

    // ── Random counter caching (independent for online and max) ──
    private long lastRandomUpdateOnline = 0;
    private long lastRandomUpdateMax = 0;
    private int cachedRandomOnline = 0;
    private int cachedRandomMax = 0;

    public MOTDListener() {
        loadIcon();
    }

    /**
     * Loads the server-icon.png icon from the plugin folder.
     * Can be called again to reload (e.g. on /ui reload).
     */
    public void loadIcon() {
        File iconFile = new File(Main.getInstance().getDataFolder(), "server-icon.png");
        if (!iconFile.exists()) {
            this.iconLoaded = false;
            this.cachedIcon = null;
            return;
        }

        try {
            this.cachedIcon = Bukkit.getServer().loadServerIcon(iconFile);
            this.iconLoaded = true;
            ConsoleLogger.info("[MOTD] Loaded server icon: server-icon.png");
        } catch (Exception e) {
            ConsoleLogger.warn("[MOTD] Failed to load server-icon.png (must be 64×64 PNG): " + e.getMessage());
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
    //  CUSTOM PLAYER LIST  (custom lines instead of nicknames)
    // =========================================================

    private void applyPlayerList(PaperServerListPingEvent event, FileConfiguration config) {
        if (!config.getBoolean("motd.player_list.enabled", false)) return;

        List<String> lines = config.getStringList("motd.player_list.lines");
        if (lines.isEmpty()) return;

        List<PaperServerListPingEvent.ListedPlayerInfo> sample = event.getListedPlayers();
        sample.clear();
        for (String line : lines) {
            // Convert MiniMessage to plain text (without color codes)
            String plain = MessageUtil.toPlainText(line);
            // Trim to 16 characters (Minecraft's nick length limit)
            if (plain.length() > 16) {
                plain = plain.substring(0, 16);
            }
            sample.add(new PaperServerListPingEvent.ListedPlayerInfo(plain, UUID.randomUUID()));
        }
    }

    // =========================================================
    //  ONLINE COUNTER (modes: normal, hide, fixed, percent, add, random)
    // =========================================================

    private void applyOnlineCounter(PaperServerListPingEvent event, FileConfiguration config) {
        int realOnline = Bukkit.getOnlinePlayers().size();

        // ═════════════════════════════════════════════════════
        //  CURRENT ONLINE  (X in "X/Y")
        // ═════════════════════════════════════════════════════
        int count = applyCounterSection(
                config,
                "motd.online_counter.current_online",
                realOnline
        );

        // ═════════════════════════════════════════════════════
        //  MAX ONLINE  (Y in "X/Y")
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
     * Universal method for applying a counter mode to a single value.
     *
     * @param config     the config
     * @param basePath   the config path (e.g. "motd.online_counter.current_online")
     * @param realValue  the real value (online or max)
     * @return the computed value
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
     * Cached random value for a counter section.
     * Each section (current_online / max_online) has its own timer and cache.
     */
    private int getCachedRandom(FileConfiguration config, String basePath) {
        long now = System.currentTimeMillis();
        int intervalTicks = Math.max(1, config.getInt(basePath + ".update_interval_ticks", 100));
        long intervalMs = intervalTicks * 50L;

        // Determine which section this is by path, to use its own cache
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
