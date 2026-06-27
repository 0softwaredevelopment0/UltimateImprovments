package com.mcplugin.infrastructure.util;

import com.mcplugin.infrastructure.core.Main;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.management.ManagementFactory;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Резольвер плейсхолдеров — встроенные + PAPI + LuckPerms (если установлен).
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
 *   <li>{prefix} — префикс LuckPerms (legacy → MiniMessage)</li>
 *   <li>{suffix} — суффикс LuckPerms (legacy → MiniMessage)</li>
 *   <li>{group} — основная группа LuckPerms</li>
 * </ul>
 */
public class PlaceholderResolver {

    private static final DecimalFormat TPS_FORMAT = new DecimalFormat("#.##");
    private static final Map<String, BiFunction<Player, String, String>> BUILTIN = new HashMap<>();

    // ===== Пинг-градиент (0ms → 1000ms) =====
    // Стопы: [ping, R, G, B]
    private static final int[][] PING_COLOR_STOPS = {
        {0,     0x00, 0xAA, 0x00},   // 0ms:   тёмно-зелёный
        {200,   0x55, 0xFF, 0x55},   // 200ms: зелёный
        {400,   0xFF, 0xFF, 0x55},   // 400ms: жёлтый
        {600,   0xFF, 0xAA, 0x00},   // 600ms: оранжевый
        {800,   0xFF, 0x55, 0x55},   // 800ms: красный
        {1000,  0xAA, 0x00, 0x00}    // 1000ms: тёмно-красный
    };
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.legacyAmpersand();

    private static boolean papiAvailable = false;
    private static boolean luckPermsAvailable = false;
    private static Object lpInstance; // net.luckperms.api.LuckPerms
    private static java.lang.reflect.Method lpGetUserManager;
    private static java.lang.reflect.Method lpUserManagerGetUser;
    private static java.lang.reflect.Method lpUserGetCachedData;
    private static java.lang.reflect.Method lpMetaDataGetPrefix;
    private static java.lang.reflect.Method lpMetaDataGetSuffix;
    private static java.lang.reflect.Method lpMetaDataGetPrimaryGroup;

    static {
        // ── Player ──
        BUILTIN.put("player_name", (p, s) -> p != null ? p.getName() : "?");
        BUILTIN.put("player_displayname", (p, s) -> p != null ? p.getDisplayName() : "?");
        BUILTIN.put("player_uuid", (p, s) -> p != null ? p.getUniqueId().toString() : "?");
        BUILTIN.put("player_ping", (p, s) -> p != null ? String.valueOf(p.getPing()) : "0");
        BUILTIN.put("player_ping_color", PlaceholderResolver::resolvePingColor);
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

        // ── LuckPerms placeholders (lazy — resolved after init()) ──
        BUILTIN.put("prefix", PlaceholderResolver::resolveLuckPermsPrefix);
        BUILTIN.put("suffix", PlaceholderResolver::resolveLuckPermsSuffix);
        BUILTIN.put("group", PlaceholderResolver::resolveLuckPermsGroup);
    }

    /**
     * Вызывается при старте — проверяет наличие PAPI и LuckPerms.
     */
    public static void init() {
        // ── PAPI ──
        try {
            Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            papiAvailable = true;
            Main.getInstance().getLogger().info("[PlaceholderResolver] PlaceholderAPI detected!");
        } catch (ClassNotFoundException e) {
            papiAvailable = false;
        }

        // ── LuckPerms ──
        try {
            Class<?> lpClass = Class.forName("net.luckperms.api.LuckPerms");
            Class<?> lpProvider = Class.forName("net.luckperms.api.LuckPermsProvider");
            java.lang.reflect.Method getMethod = lpProvider.getMethod("get");
            lpInstance = getMethod.invoke(null);

            lpGetUserManager = lpClass.getMethod("getUserManager");
            Object userManager = lpGetUserManager.invoke(lpInstance);

            Class<?> userManagerClass = Class.forName("net.luckperms.api.user.UserManager");
            // getIfLoaded returns User directly (no CompletableFuture) — safe for online players
            lpUserManagerGetUser = userManagerClass.getMethod("getIfLoaded", java.util.UUID.class);

            Class<?> userClass = Class.forName("net.luckperms.api.model.user.User");
            lpUserGetCachedData = userClass.getMethod("getCachedData");

            Class<?> cachedDataClass = Class.forName("net.luckperms.api.cacheddata.CachedMetaData");
            lpMetaDataGetPrefix = cachedDataClass.getMethod("getPrefix");
            lpMetaDataGetSuffix = cachedDataClass.getMethod("getSuffix");
            lpMetaDataGetPrimaryGroup = cachedDataClass.getMethod("getPrimaryGroup");

            // Убеждаемся что API работает: пробный вызов getUserManager
            if (userManager != null) {
                luckPermsAvailable = true;
                Main.getInstance().getLogger().info("[PlaceholderResolver] LuckPerms API detected!");
            }
        } catch (Exception e) {
            luckPermsAvailable = false;
            Main.getInstance().getLogger().info("[PlaceholderResolver] LuckPerms not found — {prefix}/{suffix}/{group} disabled");
        }
    }

    // ── LuckPerms resolvers ──

    private static String resolveLuckPermsPrefix(Player player, String unused) {
        return getLuckPermsMeta(player, lpMetaDataGetPrefix);
    }

    private static String resolveLuckPermsSuffix(Player player, String unused) {
        return getLuckPermsMeta(player, lpMetaDataGetSuffix);
    }

    private static String resolveLuckPermsGroup(Player player, String unused) {
        return getLuckPermsMeta(player, lpMetaDataGetPrimaryGroup);
    }

    /**
     * Получает мета-данные LuckPerms для игрока (prefix/suffix/group)
     * и конвертирует legacy-формат (&7) в MiniMessage (<gray>).
     */
    private static String getLuckPermsMeta(Player player, java.lang.reflect.Method metaMethod) {
        if (player == null || !luckPermsAvailable || lpInstance == null) return "";
        try {
            Object userManager = lpGetUserManager.invoke(lpInstance);
            Object user = lpUserManagerGetUser.invoke(userManager, player.getUniqueId());
            if (user == null) return "";

            Object cachedData = lpUserGetCachedData.invoke(user);
            Object value = metaMethod.invoke(cachedData);
            if (value == null) return "";

            String legacyStr = value.toString();
            if (legacyStr.isEmpty()) return "";

            // Конвертируем legacy (&7) → Component → MiniMessage (<gray>)
            Component comp = LEGACY_AMPERSAND.deserialize(legacyStr);
            return MM.serialize(comp);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Возвращает MiniMessage-цвет (<#RRGGBB>) для пинга игрока.
     * Градиент от тёмно-зелёного (0ms) до тёмно-красного (1000ms+).
     */
    private static String resolvePingColor(Player player, String unused) {
        if (player == null) return "<#00AA00>";
        int ping = Math.min(player.getPing(), 1000);

        // Находим две стопы между которыми находится ping
        for (int i = 0; i < PING_COLOR_STOPS.length - 1; i++) {
            int[] lower = PING_COLOR_STOPS[i];
            int[] upper = PING_COLOR_STOPS[i + 1];
            if (ping >= lower[0] && ping <= upper[0]) {
                if (ping == lower[0]) {
                    return String.format("<%02X%02X%02X>", lower[1], lower[2], lower[3]);
                }
                if (ping == upper[0]) {
                    return String.format("<%02X%02X%02X>", upper[1], upper[2], upper[3]);
                }
                double t = (double) (ping - lower[0]) / (upper[0] - lower[0]);
                int r = (int) Math.round(lower[1] + (upper[1] - lower[1]) * t);
                int g = (int) Math.round(lower[2] + (upper[2] - lower[2]) * t);
                int b = (int) Math.round(lower[3] + (upper[3] - lower[3]) * t);
                return String.format("<%02X%02X%02X>", r, g, b);
            }
        }

        // fallback — тёмно-красный (1000ms+)
        return "<#AA0000>";
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
