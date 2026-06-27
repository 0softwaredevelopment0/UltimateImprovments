package com.mcplugin.infrastructure.util;

import com.mcplugin.infrastructure.core.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.management.ManagementFactory;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Резольвер плейсхолдеров — встроенные + PAPI (если установлен).
 * <p>
 * Встроенные плейсхолдеры:
 * <ul>
 *   <li>{player_name} — ник игрока</li>
 *   <li>{player_displayname} — отображаемое имя</li>
 *   <li>{player_uuid} — UUID игрока</li>
 *   <li>{player_ping} — пинг игрока</li>
 *   <li>{player_gamemode} — режим игры</li>
 *   <li>{player_health} — здоровье (HP)</li>
 *   <li>{player_food} — сытость</li>
 *   <li>{player_level} — уровень опыта</li>
 *   <li>{player_xp} — опыт (0.0–1.0)</li>
 *   <li>{world_name} — название мира</li>
 *   <li>{world_players} — игроков в мире игрока</li>
 *   <li>{online} — онлайн</li>
 *   <li>{max_players} — макс. игроков</li>
 *   <li>{tps} — TPS сервера (1m avg)</li>
 *   <li>{uptime} — аптайм сервера</li>
 *   <li>{server_name} — имя сервера (bukkit.name)</li>
 *   <li>{server_version} — версия сервера</li>
 * </ul>
 */
public class PlaceholderResolver {

    private static final DecimalFormat TPS_FORMAT = new DecimalFormat("#.##");
    private static final Map<String, BiFunction<Player, String, String>> BUILTIN = new HashMap<>();

    private static boolean papiAvailable = false;

    static {
        // ── Player ──
        BUILTIN.put("player_name", (p, s) -> p != null ? p.getName() : "?");
        BUILTIN.put("player_displayname", (p, s) -> p != null ? p.getDisplayName() : "?");
        BUILTIN.put("player_uuid", (p, s) -> p != null ? p.getUniqueId().toString() : "?");
        BUILTIN.put("player_ping", (p, s) -> p != null ? String.valueOf(p.getPing()) : "0");
        BUILTIN.put("player_gamemode", (p, s) -> p != null ? p.getGameMode().name().toLowerCase() : "?");
        BUILTIN.put("player_health", (p, s) -> p != null ? String.valueOf((int) p.getHealth()) : "0");
        BUILTIN.put("player_food", (p, s) -> p != null ? String.valueOf(p.getFoodLevel()) : "0");
        BUILTIN.put("player_level", (p, s) -> p != null ? String.valueOf(p.getLevel()) : "0");
        BUILTIN.put("player_xp", (p, s) -> p != null ? String.valueOf(Math.round(p.getExp() * 100)) : "0");

        // ── World ──
        BUILTIN.put("world_name", (p, s) -> p != null ? p.getWorld().getName() : "?");
        BUILTIN.put("world_players", (p, s) -> p != null ? String.valueOf(p.getWorld().getPlayers().size()) : "0");

        // ── Server ──
        BUILTIN.put("online", (p, s) -> String.valueOf(Bukkit.getOnlinePlayers().size()));
        BUILTIN.put("max_players", (p, s) -> String.valueOf(Bukkit.getMaxPlayers()));
        BUILTIN.put("tps", (p, s) -> {
            double tps = Bukkit.getTPS()[0];
            return TPS_FORMAT.format(Math.min(tps, 20.0));
        });
        BUILTIN.put("uptime", (p, s) -> {
            long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
            long sec = uptimeMs / 1000;
            long min = sec / 60;
            long hour = min / 60;
            long day = hour / 24;
            return day + "d " + (hour % 24) + "h " + (min % 60) + "m";
        });
        BUILTIN.put("server_name", (p, s) -> Bukkit.getName());
        BUILTIN.put("server_version", (p, s) -> Bukkit.getVersion());
    }

    /**
     * Вызывается при старте — проверяет наличие PAPI.
     */
    public static void init() {
        try {
            Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            papiAvailable = true;
            Main.getInstance().getLogger().info("[PlaceholderResolver] PlaceholderAPI detected!");
        } catch (ClassNotFoundException e) {
            papiAvailable = false;
        }
    }

    /**
     * Разрешает плейсхолдеры в строке.
     * Сначала встроенные, затем PAPI (если доступен).
     *
     * @param text   строка с плейсхолдерами
     * @param player игрок (может быть null для общих плейсхолдеров)
     * @return строка с разрешёнными значениями
     */
    public static String resolve(String text, Player player) {
        if (text == null || text.isEmpty()) return text;

        // 1. Встроенные плейсхолдеры {name}
        StringBuilder sb = new StringBuilder(text);
        for (Map.Entry<String, BiFunction<Player, String, String>> entry : BUILTIN.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            int idx;
            while ((idx = sb.indexOf(placeholder)) != -1) {
                String value = entry.getValue().apply(player, sb.toString());
                sb.replace(idx, idx + placeholder.length(), value != null ? value : "");
            }
        }

        // 2. PAPI (если установлен)
        if (player != null && papiAvailable) {
            try {
                Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                java.lang.reflect.Method setPlaceholders = papiClass.getMethod("setPlaceholders", Player.class, String.class);
                String result = (String) setPlaceholders.invoke(null, player, sb.toString());
                if (result != null) sb = new StringBuilder(result);
            } catch (Exception ignored) {}
        }

        return sb.toString();
    }

    /**
     * @return true если PlaceholderAPI установлен на сервере
     */
    public static boolean isPapiAvailable() {
        return papiAvailable;
    }
}
