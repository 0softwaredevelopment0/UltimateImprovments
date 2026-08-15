package com.ultimateimprovments.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.lang.management.ManagementFactory;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The single UltimateImprovments placeholder resolver.
 * <p>
 * <b>Format:</b> {@code %<name>%} — e.g. {@code %player_ping%}, {@code %online%}, {@code %tps_5m%}.
 * The same format is used inside the plugin (file/config/GUI/chat/scoreboard/tab)
 * and externally via PlaceholderAPI (TAB, deluxe menus, etc.) — the
 * {@code %ui_<name>%} placeholder inside PAPI references the same resolver.
 *
 * <p>If PlaceholderAPI is installed — our {@link com.ultimateimprovments.hook.UIPlaceholderExpansion}
 * registers ALL names from the registry into PAPI. If PAPI is missing — the expansion
 * isn't registered, but the internal resolver still handles all placeholders
 * from the plugin's configs/MiniMessage strings.
 *
 * <p><b>Available names:</b> see {@link #getBuiltinNames()}.
 * Dynamic/ping/copy-link placeholders are resolved separately (see {@link #resolve(String, Player)}).
 */
public class PlaceholderResolver {

    private static final DecimalFormat TPS_FORMAT = new DecimalFormat("0.00");
    private static final DecimalFormat PCT_FORMAT = new DecimalFormat("#.#");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /** Main pattern: %name% — only simple names from BUILTIN. */
    private static final Pattern NAME_PLACEHOLDER = Pattern.compile("%([a-z_][a-z0-9_]*)%");
    /** Dynamic/colored versions: %tps_5m%, %mspt_max_1h_color% etc. */
    private static final Pattern DYNAMIC_PLACEHOLDER = Pattern.compile(
            "%(tps|mspt|online|ram)(?:_(min|max|avg))?(?:_(\\d+[smhd]|ss))?(?:_(color))?%"
    );
    /** Ping placeholders with scope and color. */
    private static final Pattern PING_PLACEHOLDER = Pattern.compile(
            "%ping(?:_(min|max|avg))?(?:_(\\d+[smhd]|ss))?(?:_(?!(?:color)\\b)(\\w+))?(?:_(color))?%"
    );
    /** Click-actions: %copy:"text"% and %link:"url"%. */
    private static final Pattern COPY_LINK_PATTERN = Pattern.compile(
            "%(copy|link):\"([^\"]+)\"%"
    );

    // === Ping gradient stops ===
    public static final int[][] PING_COLOR_STOPS = {
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

    // === LuckPerms reflection state (forwarded to PlaceholderRegistry) ===
    public static Method lpGetUserManager;
    public static Object lpInstance;
    public static Method lpUserManagerGetUser;
    public static Method lpUserGetCachedData;
    public static Method lpMetaDataGetPrefix;
    public static Method lpMetaDataGetSuffix;
    public static Method lpMetaDataGetPrimaryGroup;

    // === Registry of INTERNAL placeholders ===
    private static final Map<String, BiFunction<Player, String, String>> BUILTIN = new HashMap<>();

    static {
        // ── Player ──
        BUILTIN.put("player_name",          (p, s) -> p != null ? p.getName() : "?");
        BUILTIN.put("player_displayname",   (p, s) -> p != null ? p.getDisplayName() : "?");
        BUILTIN.put("player_uuid",          (p, s) -> p != null ? p.getUniqueId().toString() : "?");
        BUILTIN.put("player_ping",          (p, s) -> p != null ? String.valueOf(p.getPing()) : "0");
        BUILTIN.put("player_ping_color",    PlaceholderResolver::resolvePingColor);
        BUILTIN.put("player_ping_gradient", PlaceholderResolver::resolvePingGradient);
        BUILTIN.put("player_gamemode",      (p, s) -> p != null ? p.getGameMode().name().toLowerCase() : "?");
        BUILTIN.put("player_health",        (p, s) -> p != null ? String.valueOf((int) p.getHealth()) : "0");
        BUILTIN.put("player_food",          (p, s) -> p != null ? String.valueOf(p.getFoodLevel()) : "0");
        BUILTIN.put("player_level",         (p, s) -> p != null ? String.valueOf(p.getLevel()) : "0");
        BUILTIN.put("player_xp",            (p, s) -> p != null ? String.valueOf(Math.round(p.getExp() * 100)) : "0");

        // ── World ──
        BUILTIN.put("world_name",    (p, s) -> p != null ? p.getWorld().getName() : "?");
        BUILTIN.put("world_players", (p, s) -> p != null ? String.valueOf(p.getWorld().getPlayers().size()) : "0");
        BUILTIN.put("player_world",  (p, s) -> p != null ? p.getWorld().getName() : "?");
        BUILTIN.put("player_coords", PlaceholderResolver::resolvePlayerCoords);

        // ── Date / Time (server OS local TZ) ──
        BUILTIN.put("server_time", (p, s) -> LocalTime.now().format(TIME_FORMAT));
        BUILTIN.put("server_date", (p, s) -> LocalDate.now().format(DATE_FORMAT));

        // ── Server (static) ──
        BUILTIN.put("online",        (p, s) -> String.valueOf(Bukkit.getOnlinePlayers().size()));
        BUILTIN.put("max_players",   (p, s) -> String.valueOf(Bukkit.getMaxPlayers()));
        BUILTIN.put("server_name",   (p, s) -> Bukkit.getName());
        BUILTIN.put("server_version", (p, s) -> Bukkit.getVersion());
        BUILTIN.put("uptime", (p, s) -> {
            long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
            long sec = uptimeMs / 1000;
            long min = sec / 60;
            long hour = min / 60;
            long day = hour / 24;
            return day + "d " + (hour % 24) + "h " + (min % 60) + "m";
        });

        // ── TPS / MSPT / RAM (as %name%) ──
        BUILTIN.put("tps",  (p, s) -> TPS_FORMAT.format(Math.min(Bukkit.getTPS()[0], 20.0)));
        BUILTIN.put("mspt", (p, s) -> TPS_FORMAT.format(Bukkit.getAverageTickTime()));
        BUILTIN.put("ram", PlaceholderResolver::resolveRam);
        BUILTIN.put("tps_color",  PlaceholderResolver::resolveTpsColor);
        BUILTIN.put("mspt_color", PlaceholderResolver::resolveMsptColor);
        BUILTIN.put("ram_color",  PlaceholderResolver::resolveRamColor);

        // ── LuckPerms ──
        BUILTIN.put("prefix", PlaceholderResolver::resolveLuckPermsPrefix);
        BUILTIN.put("suffix", PlaceholderResolver::resolveLuckPermsSuffix);
        BUILTIN.put("group",  PlaceholderResolver::resolveLuckPermsGroup);
    }

    private PlaceholderResolver() {}

    // ════════════════════════════════════════════
    // Public API (used by PlaceholderExpansion)
    // ════════════════════════════════════════════

    /** Returns the resolver for a placeholder name (without %). */
    public static BiFunction<Player, String, String> getBuiltin(String name) {
        return BUILTIN.get(name);
    }

    /** All known names for documentation and PAPI registration. */
    public static Set<String> getBuiltinNames() {
        return BUILTIN.keySet();
    }

    /** Resolves a single name (called from UIPlaceholderExpansion). */
    public static String resolveBuiltin(Player player, String name) {
        BiFunction<Player, String, String> fn = BUILTIN.get(name);
        return fn != null ? String.valueOf(fn.apply(player, "")) : null;
    }

    /** true if PlaceholderAPI is installed and available. */
    public static boolean isPapiAvailable() {
        return papiAvailable;
    }

    /** true if LuckPerms is installed and the hook connected. */
    public static boolean isLuckPermsAvailable() {
        return luckPermsAvailable;
    }

    /** The registry name the PAPI Expansion hooks into. */
    public static String getPapiIdentifier() {
        return "ui";
    }

    // ════════════════════════════════════════════
    // Init: detect PAPI and LuckPerms
    // ════════════════════════════════════════════

    public static void init() {
        // ── PAPI ──
        try {
            Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            papiAvailable = true;
            ConsoleLogger.info("[PlaceholderResolver] PlaceholderAPI detected — внешняя интеграция активна");
        } catch (ClassNotFoundException e) {
            papiAvailable = false;
            ConsoleLogger.info("[PlaceholderResolver] PlaceholderAPI не найден — внешняя интеграция отключена, внутренний резолвер работает");
        }

        // ── LuckPerms ──
        try {
            Class<?> lpClass = Class.forName("net.luckperms.api.LuckPerms");
            Class<?> lpProvider = Class.forName("net.luckperms.api.LuckPermsProvider");
            Method getMethod = lpProvider.getMethod("get");
            lpInstance = getMethod.invoke(null);

            lpGetUserManager = lpClass.getMethod("getUserManager");
            Object userManager = lpGetUserManager.invoke(lpInstance);

            Class<?> userManagerClass = Class.forName("net.luckperms.api.user.UserManager");
            lpUserManagerGetUser = userManagerClass.getMethod("getIfLoaded", java.util.UUID.class);

            Class<?> userClass = Class.forName("net.luckperms.api.model.user.User");
            lpUserGetCachedData = userClass.getMethod("getCachedData");

            Class<?> cachedDataClass = Class.forName("net.luckperms.api.cacheddata.CachedMetaData");
            lpMetaDataGetPrefix    = cachedDataClass.getMethod("getPrefix");
            lpMetaDataGetSuffix    = cachedDataClass.getMethod("getSuffix");
            lpMetaDataGetPrimaryGroup = cachedDataClass.getMethod("getPrimaryGroup");

            if (userManager != null) {
                luckPermsAvailable = true;
                ConsoleLogger.info("[PlaceholderResolver] LuckPerms API detected — %prefix%/%suffix%/%group% активны");
            }
        } catch (Exception e) {
            luckPermsAvailable = false;
            ConsoleLogger.info("[PlaceholderResolver] LuckPerms не найден — %prefix%/%suffix%/%group% отключены");
        }

        StatsTracker.init();
    }

    // ════════════════════════════════════════════
    // RESOLVE — the main entry point
    // ════════════════════════════════════════════

    /**
     * Resolves all placeholders in a string.
     * Order:
     * <ol>
     *   <li>{@code %name%} — simple built-ins</li>
     *   <li>{@code %tps_5m%} / {@code %mspt%} — dynamic/colored</li>
     *   <li>{@code %ping_5m_all%} — ping</li>
     *   <li>{@code %copy:"x"%} and {@code %link:"url"%} — clicks</li>
     *   <li>PlaceholderAPI (if present) — last step, third-party placeholders allowed too ({@code %vault_balance%}, {@code %someplugin_x%}, etc.)</li>
     * </ol>
     */
    public static String resolve(String text, Player player) {
        // Fast-path: if the string has no '%', nothing to resolve.
        if (text == null || text.isEmpty() || text.indexOf('%') < 0) return text;
        text = resolveInternal(text, player);
        if (papiAvailable) {
            // The PAPI call is allowed for a null player too — some Expansion plugins
            // support server placeholders (e.g. %server_online%) without a player.
            text = resolvePapi(text, player);
        }
        return text;
    }

    /**
     * Internal resolve without the PAPI step — so UIPlaceholderExpansion can
     * safely delegate here without causing PAPI → resolve → PAPI recursion.
     */
    public static String resolveInternal(String text, Player player) {
        if (text == null || text.isEmpty()) return text;
        text = resolveStatic(text, player);
        text = resolveDynamic(text);
        text = resolvePing(text, player);
        text = resolveCopyLink(text);
        return text;
    }

    /** Only static {@code %name%} — fast path without regex ping/dynamic. */
    public static String resolveOnlyStatic(String text, Player player) {
        if (text == null || text.isEmpty()) return text;
        return resolveStatic(text, player);
    }

    private static String resolveStatic(String text, Player player) {
        if (text == null || text.isEmpty()) return text;
        Matcher m = NAME_PLACEHOLDER.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String name = m.group(1);
            BiFunction<Player, String, String> fn = BUILTIN.get(name);
            if (fn == null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                continue;
            }
            String value = String.valueOf(fn.apply(player, text));
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String resolvePapi(String text, Player player) {
        try {
            Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method setPlaceholders = papiClass.getMethod("setPlaceholders", Player.class, String.class);
            Object result = setPlaceholders.invoke(null, player, text);
            return result != null ? (String) result : text;
        } catch (Exception e) {
            return text;
        }
    }

    private static String resolveDynamic(String text) {
        if (text == null || text.isEmpty()) return text;
        Matcher m = DYNAMIC_PLACEHOLDER.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String metric = m.group(1);
            String stat = m.group(2);
            String timeStr = m.group(3);
            String colorFlag = m.group(4);

            int timeSec;
            if (timeStr == null) {
                timeSec = 0;
            } else {
                timeSec = StatsTracker.parseTimeSeconds(timeStr);
                if (timeSec < 0) {
                    m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                    continue;
                }
            }

            StatsTracker st = StatsTracker.getInstance();
            if (st == null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                continue;
            }

            int samples = timeSec <= 0 ? st.getSampleCount(0) : st.getSampleCount(timeSec);
            String value = resolveMetric(metric, stat, "color".equals(colorFlag), st, samples);
            if (value == null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                continue;
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String resolveMetric(String metric, String stat, boolean isColor,
                                         StatsTracker st, int samples) {
        switch (metric) {
            case "tps": {
                double val;
                if ("min".equals(stat)) val = st.getMinTps(samples);
                else if ("max".equals(stat)) val = st.getMaxTps(samples);
                else val = st.getAvgTps(samples);
                val = Math.min(val, 20.0);
                return isColor ? StatsTracker.tpsColor(val) + TPS_FORMAT.format(val) : TPS_FORMAT.format(val);
            }
            case "mspt": {
                double val;
                if ("min".equals(stat)) val = st.getMinMspt(samples);
                else if ("max".equals(stat)) val = st.getMaxMspt(samples);
                else val = st.getAvgMspt(samples);
                return isColor ? StatsTracker.msptColor(val) + TPS_FORMAT.format(val) : TPS_FORMAT.format(val);
            }
            case "online": {
                if ("min".equals(stat)) return String.valueOf(st.getMinOnline(samples));
                if ("max".equals(stat)) return String.valueOf(st.getMaxOnline(samples));
                return String.valueOf(st.getCurrentOnline());
            }
            case "ram": {
                double val;
                if ("min".equals(stat)) val = st.getMinRam(samples);
                else if ("max".equals(stat)) val = st.getMaxRam(samples);
                else val = st.getAvgRam(samples);
                return isColor ? StatsTracker.ramColor(val) + PCT_FORMAT.format(val) + "%" : PCT_FORMAT.format(val) + "%";
            }
            default: return null;
        }
    }

    private static String resolvePing(String text, Player player) {
        if (text == null || text.isEmpty()) return text;
        Matcher m = PING_PLACEHOLDER.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String stat = m.group(1);
            String timeStr = m.group(2);
            String scope = m.group(3);
            String colorFlag = m.group(4);

            StatsTracker st = StatsTracker.getInstance();
            boolean isColor = "color".equals(colorFlag);
            boolean isAll = "all".equals(scope);

            if (!isAll) {
                Player target = player;
                if (scope != null && !scope.equals("ys")) {
                    target = Bukkit.getPlayer(scope);
                    if (target == null || !target.isOnline()) {
                        m.appendReplacement(sb, "0");
                        continue;
                    }
                }
                if (target == null) {
                    m.appendReplacement(sb, "0");
                    continue;
                }
                int ping = target.getPing();
                String val = isColor ? StatsTracker.pingColor(ping) + ping : String.valueOf(ping);
                m.appendReplacement(sb, Matcher.quoteReplacement(val));
                continue;
            }

            if (st == null) {
                m.appendReplacement(sb, "0");
                continue;
            }

            int timeSec = timeStr == null ? 0 : StatsTracker.parseTimeSeconds(timeStr);
            if (timeSec < 0) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                continue;
            }
            int samples = timeSec <= 0 ? st.getSampleCount(0) : st.getSampleCount(timeSec);

            double val;
            if ("min".equals(stat)) val = st.getMinPing(samples);
            else if ("max".equals(stat)) val = st.getMaxPing(samples);
            else val = st.getAvgPing(samples);

            String formatted = TPS_FORMAT.format(val);
            if (isColor) formatted = StatsTracker.pingColor(val) + formatted;
            m.appendReplacement(sb, Matcher.quoteReplacement(formatted));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String resolveCopyLink(String text) {
        if (text == null || text.isEmpty()) return text;
        Matcher m = COPY_LINK_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String type = m.group(1);
            String content = m.group(2);
            String action = "copy".equals(type) ? "copy_to_clipboard" : "open_url";
            String clickValue = content.replace("'", "\\'");
            String replacement = "<click:" + action + ":'" + clickValue + "'>" + content + "</click>";
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // ════════════════════════════════════════════
    // Utilities for built-in values
    // ════════════════════════════════════════════

    private static String resolvePingColor(Player player, String unused) {
        if (player == null) return "<#00AA00>";
        return StatsTracker.pingColor(player.getPing());
    }

    private static String resolvePingGradient(Player player, String unused) {
        if (player == null) return "<#00AA00>0ms";
        int rawPing = player.getPing();
        int clamped = Math.min(rawPing, 1000);
        for (int i = 0; i < PING_COLOR_STOPS.length - 1; i++) {
            int[] lower = PING_COLOR_STOPS[i];
            int[] upper = PING_COLOR_STOPS[i+1];
            if (clamped >= lower[0] && clamped <= upper[0]) {
                String lh = String.format("#%02X%02X%02X", lower[1], lower[2], lower[3]);
                String uh = String.format("#%02X%02X%02X", upper[1], upper[2], upper[3]);
                return String.format("<gradient:%s:%s>%dms</gradient>", lh, uh, rawPing);
            }
        }
        return "<gradient:#FF5555:#AA0000>" + rawPing + "ms</gradient>";
    }

    private static String resolveRam(Player player, String unused) {
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        long max = rt.maxMemory();
        if (max <= 0) return "0%";
        return PCT_FORMAT.format((double) used / (double) max * 100.0) + "%";
    }

    private static String resolveRamColor(Player player, String unused) {
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        long max = rt.maxMemory();
        if (max <= 0) return StatsTracker.ramColor(0) + "0%";
        double pct = (double) used / (double) max * 100.0;
        return StatsTracker.ramColor(pct) + PCT_FORMAT.format(pct) + "%";
    }

    private static String resolveTpsColor(Player player, String unused) {
        double tps = Math.min(Bukkit.getTPS()[0], 20.0);
        return StatsTracker.tpsColor(tps) + TPS_FORMAT.format(tps);
    }

    private static String resolveMsptColor(Player player, String unused) {
        double mspt = Bukkit.getAverageTickTime();
        return StatsTracker.msptColor(mspt) + TPS_FORMAT.format(mspt);
    }

    private static String resolveLuckPermsPrefix(Player player, String unused) {
        return getLuckPermsMeta(player, lpMetaDataGetPrefix);
    }

    private static String resolveLuckPermsSuffix(Player player, String unused) {
        return getLuckPermsMeta(player, lpMetaDataGetSuffix);
    }

    private static String resolveLuckPermsGroup(Player player, String unused) {
        return getLuckPermsMeta(player, lpMetaDataGetPrimaryGroup);
    }

    private static String resolvePlayerCoords(Player player, String unused) {
        if (player == null) return "0/0/0";
        Location loc = player.getLocation();
        return loc.getBlockX() + "/" + loc.getBlockY() + "/" + loc.getBlockZ();
    }

    private static String getLuckPermsMeta(Player player, Method metaMethod) {
        if (player == null || !luckPermsAvailable || lpInstance == null || metaMethod == null) {
            return "";
        }
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
}
