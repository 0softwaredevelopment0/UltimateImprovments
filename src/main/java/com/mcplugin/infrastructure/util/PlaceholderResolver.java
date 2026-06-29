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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Резольвер плейсхолдеров — встроенные + PAPI + динамические (time-based + ping).
 * <p>
 * <b>Существующие плейсхолдеры:</b>
 * <ul>
 *   <li>{player_name}, {player_displayname}, {player_uuid}</li>
 *   <li>{player_ping}, {player_ping_color}, {player_ping_gradient}</li>
 *   <li>{player_gamemode}, {player_health}, {player_food}</li>
 *   <li>{player_level}, {player_xp}</li>
 *   <li>{world_name}, {world_players}</li>
 *   <li>{online}, {max_players}</li>
 *   <li>{tps}</li>
 *   <li>{uptime}, {server_name}, {server_version}</li>
 *   <li>{prefix}, {suffix}, {group} — LuckPerms</li>
 * </ul>
 *
 * <b>Динамические плейсхолдеры (time-based):</b>
 * <ul>
 *   <li>{tps_&lt;time&gt;}, {tps_min_&lt;time&gt;}, {tps_max_&lt;time&gt;}</li>
 *   <li>{tps_&lt;time&gt;_color}, {tps_min_&lt;time&gt;_color}, {tps_max_&lt;time&gt;_color}</li>
 *   <li>{mspt}, {mspt_&lt;time&gt;}, {mspt_min_&lt;time&gt;}, {mspt_max_&lt;time&gt;}</li>
 *   <li>{mspt_&lt;time&gt;_color} и т.д.</li>
 *   <li>{online_min_&lt;time&gt;}, {online_max_&lt;time&gt;}</li>
 *   <li>{ram}, {ram_min_&lt;time&gt;}, {ram_avg_&lt;time&gt;}, {ram_max_&lt;time&gt;}</li>
 *   <li>{ram_&lt;time&gt;_color} и т.д.</li>
 * </ul>
 *
 * <b>Ping-плейсхолдеры:</b>
 * <ul>
 *   <li>{ping} — средний пинг всех за сессию</li>
 *   <li>{ping_&lt;time&gt;} — сред. пинг всех за период</li>
 *   <li>{ping_min_&lt;time&gt;_all} — мин. пинг всех за период</li>
 *   <li>{ping_max_&lt;time&gt;_all} — макс. пинг всех за период</li>
 *   <li>{ping_&lt;time&gt;_ys} — текущий пинг игрока (без истории)</li>
 *   <li>{ping_&lt;time&gt;_color}, {ping_&lt;time&gt;_ys_color} — с HEX-градиентом</li>
 * </ul>
 * <p>
 * <b>Формат &lt;time&gt;:</b> 1s, 5m, 1h, 1d, ss (серверная сессия).<br>
 * <b>Scope:</b> ys = свой пинг (yourself), all = средний всех (из StatsTracker).<br>
 * <b>Цветные версии (_color):</b> содержат встроенный MiniMessage HEX-цвет
 * ({@code <#RRGGBB>}) и НЕ переопределяются внешним MiniMessage.<br>
 * <b>Обычные версии (без _color):</b> просто числа, переопределяются
 * внешним MiniMessage-форматированием.
 */
public class PlaceholderResolver {

    private static final DecimalFormat TPS_FORMAT = new DecimalFormat("0.00");
    private static final DecimalFormat PCT_FORMAT = new DecimalFormat("#.#");
    private static final Pattern DYNAMIC_PLACEHOLDER = Pattern.compile(
            "\\{(tps|mspt|online|ram)(?:_(min|max|avg))?(?:_(\\d+[smhd]|ss))?(?:_(color))?\\}"
    );
    // Формат ping: {ping_[min|max|avg]_<time>_<scope>[_color]}
    // scope может быть: ys (yourself), all (все), или ник игрока (Steve)
    // Также: {ping} (без суффиксов), {ping_<time>}, {ping_ys}, {ping_all}, {ping_<time>_color}
    private static final Pattern PING_PLACEHOLDER = Pattern.compile(
            "\\{ping(?:_(min|max|avg))?(?:_(\\d+[smhd]|ss))?(?:_(\\w+))?(?:_(color))?\\}"
    );

    private static final Map<String, BiFunction<Player, String, String>> BUILTIN = new HashMap<>();

    // ===== Пинг-градиент (0ms → 1000ms) =====
    private static final int[][] PING_COLOR_STOPS = {
        {0,     0x00, 0xAA, 0x00},
        {200,   0x55, 0xFF, 0x55},
        {400,   0xFF, 0xFF, 0x55},
        {600,   0xFF, 0xAA, 0x00},
        {800,   0xFF, 0x55, 0x55},
        {1000,  0xAA, 0x00, 0x00}
    };
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.legacyAmpersand();

    private static boolean papiAvailable = false;
    private static boolean luckPermsAvailable = false;
    private static Object lpInstance;
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
        BUILTIN.put("player_ping_gradient", PlaceholderResolver::resolvePingGradient);
        BUILTIN.put("player_gamemode", (p, s) -> p != null ? p.getGameMode().name().toLowerCase() : "?");
        BUILTIN.put("player_health", (p, s) -> p != null ? String.valueOf((int) p.getHealth()) : "0");
        BUILTIN.put("player_food", (p, s) -> p != null ? String.valueOf(p.getFoodLevel()) : "0");
        BUILTIN.put("player_level", (p, s) -> p != null ? String.valueOf(p.getLevel()) : "0");
        BUILTIN.put("player_xp", (p, s) -> p != null ? String.valueOf(Math.round(p.getExp() * 100)) : "0");

        // ── World ──
        BUILTIN.put("world_name", (p, s) -> p != null ? p.getWorld().getName() : "?");
        BUILTIN.put("world_players", (p, s) -> p != null ? String.valueOf(p.getWorld().getPlayers().size()) : "0");

        // ── Server (static) ──
        BUILTIN.put("online", (p, s) -> String.valueOf(Bukkit.getOnlinePlayers().size()));
        BUILTIN.put("max_players", (p, s) -> String.valueOf(Bukkit.getMaxPlayers()));
        BUILTIN.put("tps", (p, s) -> {
            double tps = Bukkit.getTPS()[0];
            return TPS_FORMAT.format(Math.min(tps, 20.0));
        });
        BUILTIN.put("tps_color", (p, s) -> {
            double tps = Math.min(Bukkit.getTPS()[0], 20.0);
            return StatsTracker.tpsColor(tps) + TPS_FORMAT.format(tps);
        });
        BUILTIN.put("mspt", (p, s) -> TPS_FORMAT.format(Bukkit.getAverageTickTime()));
        BUILTIN.put("mspt_color", (p, s) -> {
            double mspt = Bukkit.getAverageTickTime();
            return StatsTracker.msptColor(mspt) + TPS_FORMAT.format(mspt);
        });
        BUILTIN.put("ram", (p, s) -> {
            Runtime rt = Runtime.getRuntime();
            long used = rt.totalMemory() - rt.freeMemory();
            long max = rt.maxMemory();
            if (max <= 0) return "0%";
            return PCT_FORMAT.format((double) used / (double) max * 100.0) + "%";
        });
        BUILTIN.put("ram_color", (p, s) -> {
            Runtime rt = Runtime.getRuntime();
            long used = rt.totalMemory() - rt.freeMemory();
            long max = rt.maxMemory();
            if (max <= 0) return StatsTracker.ramColor(0) + "0%";
            double pct = (double) used / (double) max * 100.0;
            return StatsTracker.ramColor(pct) + PCT_FORMAT.format(pct) + "%";
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

        // ── LuckPerms ──
        BUILTIN.put("prefix", PlaceholderResolver::resolveLuckPermsPrefix);
        BUILTIN.put("suffix", PlaceholderResolver::resolveLuckPermsSuffix);
        BUILTIN.put("group", PlaceholderResolver::resolveLuckPermsGroup);
    }

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
            lpUserManagerGetUser = userManagerClass.getMethod("getIfLoaded", java.util.UUID.class);

            Class<?> userClass = Class.forName("net.luckperms.api.model.user.User");
            lpUserGetCachedData = userClass.getMethod("getCachedData");

            Class<?> cachedDataClass = Class.forName("net.luckperms.api.cacheddata.CachedMetaData");
            lpMetaDataGetPrefix = cachedDataClass.getMethod("getPrefix");
            lpMetaDataGetSuffix = cachedDataClass.getMethod("getSuffix");
            lpMetaDataGetPrimaryGroup = cachedDataClass.getMethod("getPrimaryGroup");

            if (userManager != null) {
                luckPermsAvailable = true;
                Main.getInstance().getLogger().info("[PlaceholderResolver] LuckPerms API detected!");
            }
        } catch (Exception e) {
            luckPermsAvailable = false;
            Main.getInstance().getLogger().info("[PlaceholderResolver] LuckPerms not found — {prefix}/{suffix}/{group} disabled");
        }

        // Инициализируем StatsTracker
        StatsTracker.init();
    }

    // ════════════════════════════════════════════
    // LuckPerms
    // ════════════════════════════════════════════

    private static String resolveLuckPermsPrefix(Player player, String unused) {
        return getLuckPermsMeta(player, lpMetaDataGetPrefix);
    }

    private static String resolveLuckPermsSuffix(Player player, String unused) {
        return getLuckPermsMeta(player, lpMetaDataGetSuffix);
    }

    private static String resolveLuckPermsGroup(Player player, String unused) {
        return getLuckPermsMeta(player, lpMetaDataGetPrimaryGroup);
    }

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

            Component comp = LEGACY_AMPERSAND.deserialize(legacyStr);
            return MM.serialize(comp);
        } catch (Exception e) {
            return "";
        }
    }

    // ════════════════════════════════════════════
    // Ping color (для {player_ping_color} / {player_ping_gradient})
    // ════════════════════════════════════════════

    private static String resolvePingColor(Player player, String unused) {
        if (player == null) return "<#00AA00>";
        int ping = player.getPing(); // не обрезаем — pingColor сам обработает >1000 как тёмно-красный
        return StatsTracker.pingColor(ping);
    }

    private static String resolvePingGradient(Player player, String unused) {
        if (player == null) return "<#00AA00>0ms";
        int rawPing = player.getPing();          // реальный пинг для отображения
        int clamped = Math.min(rawPing, 1000);    // для выбора цвета градиента
        for (int i = 0; i < PING_COLOR_STOPS.length - 1; i++) {
            int[] lower = PING_COLOR_STOPS[i];
            int[] upper = PING_COLOR_STOPS[i + 1];
            if (clamped >= lower[0] && clamped <= upper[0]) {
                String lowerHex = String.format("#%02X%02X%02X", lower[1], lower[2], lower[3]);
                String upperHex = String.format("#%02X%02X%02X", upper[1], upper[2], upper[3]);
                return String.format("<gradient:%s:%s>%dms</gradient>", lowerHex, upperHex, rawPing);
            }
        }
        return "<gradient:#FF5555:#AA0000>" + rawPing + "ms</gradient>";
    }

    // ════════════════════════════════════════════
    // RESOLVE
    // ════════════════════════════════════════════

    /**
     * Разрешает плейсхолдеры в строке.
     * Сначала встроенные (статичные + динамические), затем PAPI.
     */
    public static String resolve(String text, Player player) {
        if (text == null || text.isEmpty()) return text;

        // 1. Статичные встроенные {name}
        StringBuilder sb = new StringBuilder(text);
        for (Map.Entry<String, BiFunction<Player, String, String>> entry : BUILTIN.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            int idx;
            while ((idx = sb.indexOf(placeholder)) != -1) {
                String value = entry.getValue().apply(player, sb.toString());
                sb.replace(idx, idx + placeholder.length(), value != null ? value : "");
            }
        }

        // 2. Ping-плейсхолдеры {ping_...}
        sb = new StringBuilder(resolvePing(sb.toString(), player));

        // 3. Динамические плейсхолдеры (time-based)
        sb = new StringBuilder(resolveDynamic(sb.toString()));

        // 4. PAPI
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

    // ════════════════════════════════════════════
    // Ping placeholders
    // ════════════════════════════════════════════

    /**
     * Разрешает ping-плейсхолдеры {ping_min_30s_all}, {ping_avg_1m_ys} и т.д.
     */
    private static String resolvePing(String text, Player player) {
        if (text == null || text.isEmpty()) return text;

        StringBuffer sb = new StringBuffer();
        Matcher m = PING_PLACEHOLDER.matcher(text);
        while (m.find()) {
            String stat = m.group(1);      // min, max, avg, or null
            String timeStr = m.group(2);   // 1s, 5m, 1h, 1d, ss, or null
            String scope = m.group(3);     // ys, all, or null
            String colorFlag = m.group(4); // color or null

            StatsTracker st = StatsTracker.getInstance();

            boolean isColor = "color".equals(colorFlag);
            boolean isAll = "all".equals(scope);

            // _ys, конкретный ник или без scope → текущий пинг игрока (без истории)
            if (!isAll) {
                Player targetPlayer = player;
                // Если scope — ник конкретного игрока
                if (scope != null && !scope.equals("ys")) {
                    targetPlayer = Bukkit.getPlayerExact(scope);
                    if (targetPlayer == null || !targetPlayer.isOnline()) {
                        m.appendReplacement(sb, "0");
                        continue;
                    }
                }
                if (targetPlayer == null) {
                    m.appendReplacement(sb, "0");
                    continue;
                }
                int ping = targetPlayer.getPing();
                String val = String.valueOf(ping);
                if (isColor) {
                    val = StatsTracker.pingColor(ping) + ping;
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(val));
                continue;
            }

            // _all → из StatsTracker
            if (st == null) {
                m.appendReplacement(sb, "0");
                continue;
            }

            int timeSec;
            if (timeStr == null) {
                timeSec = 0; // ss
            } else {
                timeSec = StatsTracker.parseTimeSeconds(timeStr);
                if (timeSec < 0) {
                    m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                    continue;
                }
            }

            int samples = timeSec <= 0 ? st.getSampleCount(0) : st.getSampleCount(timeSec);

            double val;
            if ("min".equals(stat)) val = st.getMinPing(samples);
            else if ("max".equals(stat)) val = st.getMaxPing(samples);
            else val = st.getAvgPing(samples);

            String formatted = TPS_FORMAT.format(val);
            if (isColor) {
                formatted = StatsTracker.pingColor(val) + formatted;
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(formatted));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // ════════════════════════════════════════════
    // Dynamic placeholders (time-based — tps, mspt, online, ram)
    // ════════════════════════════════════════════

    /**
     * Разрешает динамические плейсхолдеры вида {tps_5m}, {mspt_max_1h_color} и т.д.
     */
    private static String resolveDynamic(String text) {
        if (text == null || text.isEmpty()) return text;

        StringBuffer sb = new StringBuffer();
        Matcher m = DYNAMIC_PLACEHOLDER.matcher(text);
        while (m.find()) {
            String metric = m.group(1);      // tps, mspt, online, ram
            String stat = m.group(2);        // min, max, avg, or null
            String timeStr = m.group(3);     // 1s, 5m, 1h, 1d, ss, or null
            String colorFlag = m.group(4);   // color or null

            // Определяем время
            int timeSec;
            if (timeStr == null) {
                timeSec = 0; // ss = server session / всё время
            } else {
                timeSec = StatsTracker.parseTimeSeconds(timeStr);
                if (timeSec < 0) {
                    m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                    continue;
                }
            }

            StatsTracker st = StatsTracker.getInstance();
            if (st == null) {
                m.appendReplacement(sb, "?");
                continue;
            }

            int samples = timeSec <= 0 ? st.getSampleCount(0) : st.getSampleCount(timeSec);

            String value = resolveMetric(metric, stat, colorFlag, st, samples);
            if (value == null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                continue;
            }

            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Разрешает конкретный metric+stat+color.
     */
    private static String resolveMetric(String metric, String stat, boolean isColor,
                                         StatsTracker st, int samples) {
        switch (metric) {
            case "tps" -> {
                double val;
                if ("min".equals(stat)) val = st.getMinTps(samples);
                else if ("max".equals(stat)) val = st.getMaxTps(samples);
                else val = st.getAvgTps(samples);
                val = Math.min(val, 20.0);
                if (isColor) return StatsTracker.tpsColor(val) + TPS_FORMAT.format(val);
                return TPS_FORMAT.format(val);
            }
            case "mspt" -> {
                double val;
                if ("min".equals(stat)) val = st.getMinMspt(samples);
                else if ("max".equals(stat)) val = st.getMaxMspt(samples);
                else val = st.getAvgMspt(samples);
                if (isColor) return StatsTracker.msptColor(val) + TPS_FORMAT.format(val);
                return TPS_FORMAT.format(val);
            }
            case "online" -> {
                if ("min".equals(stat)) return String.valueOf(st.getMinOnline(samples));
                if ("max".equals(stat)) return String.valueOf(st.getMaxOnline(samples));
                return String.valueOf(st.getCurrentOnline());
            }
            case "ram" -> {
                double val;
                if ("min".equals(stat)) val = st.getMinRam(samples);
                else if ("max".equals(stat)) val = st.getMaxRam(samples);
                else val = st.getAvgRam(samples);
                if (isColor) return StatsTracker.ramColor(val) + PCT_FORMAT.format(val) + "%";
                return PCT_FORMAT.format(val) + "%";
            }
            default -> { return null; }
        }
    }

    /** Overload with String colorFlag */
    private static String resolveMetric(String metric, String stat, String colorFlag,
                                         StatsTracker st, int samples) {
        return resolveMetric(metric, stat, "color".equals(colorFlag), st, samples);
    }

    public static boolean isPapiAvailable() {
        return papiAvailable;
    }
}
