package com.ultimateimprovments.util;

import com.ultimateimprovments.core.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 📊 StatsTracker — сбор и хранение статистики сервера за временные окна.
 * <p>
 * Каждые 20 тиков (1 сек) записывает текущие TPS, MSPT, RAM%, Online, средний Ping
 * в циклические буферы. Позволяет получать min/avg/max за любой период
 * (1s, 5m, 1h, 1d, ss = server session).
 * <p>
 * Используется {@link PlaceholderResolver} для плейсхолдеров
 * %tps_1s%, %mspt_5m%, %online_max_1h%, %ram_avg_ss%, %ping_min_30s_all% и т.д.
 */
public class StatsTracker extends BukkitRunnable {

    private static StatsTracker instance;

    // Максимальное кол-во хранимых сэмплов (~1 день при 1 сэмпле/сек)
    private static final int MAX_SAMPLES = 86400;

    // Циклические буферы
    private final double[] tpsBuffer = new double[MAX_SAMPLES];
    private final double[] msptBuffer = new double[MAX_SAMPLES];
    private final double[] ramBuffer = new double[MAX_SAMPLES];
    private final int[] onlineBuffer = new int[MAX_SAMPLES];
    private final double[] pingBuffer = new double[MAX_SAMPLES];
    private int sampleCount = 0;

    private StatsTracker() {}

    public static void init() {
        if (instance != null) {
            instance.cancel();
        }
        instance = new StatsTracker();
        instance.runTaskTimer(Main.getInstance(), 20L, 20L); // каждую секунду
    }

    public static StatsTracker getInstance() {
        return instance;
    }

    @Override
    public void run() {
        // Текущие значения
        double tps = Math.min(Bukkit.getTPS()[0], 20.0);
        double mspt = Bukkit.getAverageTickTime();
        double ram = getRamUsage();
        int online = Bukkit.getOnlinePlayers().size();
        double avgPing = getAvgPlayerPing();

        int idx = sampleCount % MAX_SAMPLES;
        tpsBuffer[idx] = tps;
        msptBuffer[idx] = mspt;
        ramBuffer[idx] = ram;
        onlineBuffer[idx] = online;
        pingBuffer[idx] = avgPing;
        sampleCount++;
    }

    /** Средний пинг всех онлайн игроков */
    private static double getAvgPlayerPing() {
        Player[] players = Bukkit.getOnlinePlayers().toArray(new Player[0]);
        if (players.length == 0) return 0;
        long sum = 0;
        for (Player p : players) {
            sum += p.getPing();
        }
        return (double) sum / players.length;
    }

    /**
     * Парсит временной суффикс из плейсхолдера.
     * Форматы: 1s, 5m, 1h, 1d, ss.
     * Возвращает количество секунд (0 = ss, -1 = ошибка).
     */
    public static int parseTimeSeconds(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return -1;
        String lower = timeStr.toLowerCase().trim();
        if (lower.equals("ss")) return 0; // server session

        try {
            if (lower.endsWith("s")) {
                return Integer.parseInt(lower.substring(0, lower.length() - 1));
            } else if (lower.endsWith("m")) {
                return Integer.parseInt(lower.substring(0, lower.length() - 1)) * 60;
            } else if (lower.endsWith("h")) {
                return Integer.parseInt(lower.substring(0, lower.length() - 1)) * 3600;
            } else if (lower.endsWith("d")) {
                return Integer.parseInt(lower.substring(0, lower.length() - 1)) * 86400;
            }
        } catch (NumberFormatException ignored) {}
        return -1;
    }

    /**
     * Возвращает количество сэмплов для запроса.
     */
    public int getSampleCount(int timeSeconds) {
        if (timeSeconds <= 0) return sampleCount; // ss = все
        int samples = timeSeconds; // 1 sample/sec
        return Math.min(samples, sampleCount);
    }

    // ════════════════════════════════════════════
    // TPS
    // ════════════════════════════════════════════

    /** Текущий TPS */
    public double getCurrentTps() {
        return Math.min(Bukkit.getTPS()[0], 20.0);
    }

    /** Средний TPS за N сэмплов */
    public double getAvgTps(int samples) {
        if (samples <= 0 || sampleCount <= 0) return getCurrentTps();
        int count = Math.min(samples, sampleCount);
        double sum = 0;
        int idx = 0;
        for (int i = 0; i < count; i++) {
            int bufIdx = (sampleCount - 1 - i) % MAX_SAMPLES;
            double val = tpsBuffer[bufIdx];
            sum += Math.min(val, 20.0);
            idx++;
        }
        return idx > 0 ? sum / idx : 20.0;
    }

    /** Мин. TPS за N сэмплов */
    public double getMinTps(int samples) {
        if (samples <= 0 || sampleCount <= 0) return getCurrentTps();
        int count = Math.min(samples, sampleCount);
        double min = 20.0;
        for (int i = 0; i < count; i++) {
            int bufIdx = (sampleCount - 1 - i) % MAX_SAMPLES;
            double val = Math.min(tpsBuffer[bufIdx], 20.0);
            if (val < min) min = val;
        }
        return min;
    }

    /** Макс. TPS за N сэмплов */
    public double getMaxTps(int samples) {
        if (samples <= 0 || sampleCount <= 0) return getCurrentTps();
        int count = Math.min(samples, sampleCount);
        double max = 0;
        for (int i = 0; i < count; i++) {
            int bufIdx = (sampleCount - 1 - i) % MAX_SAMPLES;
            double val = Math.min(tpsBuffer[bufIdx], 20.0);
            if (val > max) max = val;
        }
        return max;
    }

    // ════════════════════════════════════════════
    // MSPT
    // ════════════════════════════════════════════

    /** Текущий MSPT */
    public double getCurrentMspt() {
        return Bukkit.getAverageTickTime();
    }

    /** Средний MSPT за N сэмплов */
    public double getAvgMspt(int samples) {
        if (samples <= 0 || sampleCount <= 0) return getCurrentMspt();
        int count = Math.min(samples, sampleCount);
        double sum = 0;
        int idx = 0;
        for (int i = 0; i < count; i++) {
            sum += msptBuffer[(sampleCount - 1 - i) % MAX_SAMPLES];
            idx++;
        }
        return idx > 0 ? sum / idx : 0;
    }

    /** Мин. MSPT за N сэмплов */
    public double getMinMspt(int samples) {
        if (samples <= 0 || sampleCount <= 0) return getCurrentMspt();
        int count = Math.min(samples, sampleCount);
        double min = Double.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            double val = msptBuffer[(sampleCount - 1 - i) % MAX_SAMPLES];
            if (val < min) min = val;
        }
        return min == Double.MAX_VALUE ? 0 : min;
    }

    /** Макс. MSPT за N сэмплов */
    public double getMaxMspt(int samples) {
        if (samples <= 0 || sampleCount <= 0) return getCurrentMspt();
        int count = Math.min(samples, sampleCount);
        double max = 0;
        for (int i = 0; i < count; i++) {
            double val = msptBuffer[(sampleCount - 1 - i) % MAX_SAMPLES];
            if (val > max) max = val;
        }
        return max;
    }

    // ════════════════════════════════════════════
    // RAM
    // ════════════════════════════════════════════

    /** Текущая RAM % */
    public double getCurrentRam() {
        return getRamUsage();
    }

    /** Средняя RAM % за N сэмплов */
    public double getAvgRam(int samples) {
        if (samples <= 0 || sampleCount <= 0) return getCurrentRam();
        int count = Math.min(samples, sampleCount);
        double sum = 0;
        int idx = 0;
        for (int i = 0; i < count; i++) {
            sum += ramBuffer[(sampleCount - 1 - i) % MAX_SAMPLES];
            idx++;
        }
        return idx > 0 ? sum / idx : 0;
    }

    /** Мин. RAM % за N сэмплов */
    public double getMinRam(int samples) {
        if (samples <= 0 || sampleCount <= 0) return getCurrentRam();
        int count = Math.min(samples, sampleCount);
        double min = Double.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            double val = ramBuffer[(sampleCount - 1 - i) % MAX_SAMPLES];
            if (val < min) min = val;
        }
        return min == Double.MAX_VALUE ? 0 : min;
    }

    /** Макс. RAM % за N сэмплов */
    public double getMaxRam(int samples) {
        if (samples <= 0 || sampleCount <= 0) return getCurrentRam();
        int count = Math.min(samples, sampleCount);
        double max = 0;
        for (int i = 0; i < count; i++) {
            double val = ramBuffer[(sampleCount - 1 - i) % MAX_SAMPLES];
            if (val > max) max = val;
        }
        return max;
    }

    // ════════════════════════════════════════════
    // PING (средний пинг всех игроков)
    // ════════════════════════════════════════════

    /** Текущий средний пинг всех игроков */
    public double getCurrentPing() {
        return getAvgPlayerPing();
    }

    /** Средний пинг за N сэмплов */
    public double getAvgPing(int samples) {
        if (samples <= 0 || sampleCount <= 0) return getCurrentPing();
        int count = Math.min(samples, sampleCount);
        double sum = 0;
        int idx = 0;
        for (int i = 0; i < count; i++) {
            sum += pingBuffer[(sampleCount - 1 - i) % MAX_SAMPLES];
            idx++;
        }
        return idx > 0 ? sum / idx : 0;
    }

    /** Мин. пинг за N сэмплов */
    public double getMinPing(int samples) {
        if (samples <= 0 || sampleCount <= 0) return getCurrentPing();
        int count = Math.min(samples, sampleCount);
        double min = Double.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            double val = pingBuffer[(sampleCount - 1 - i) % MAX_SAMPLES];
            if (val < min) min = val;
        }
        return min == Double.MAX_VALUE ? 0 : min;
    }

    /** Макс. пинг за N сэмплов */
    public double getMaxPing(int samples) {
        if (samples <= 0 || sampleCount <= 0) return getCurrentPing();
        int count = Math.min(samples, sampleCount);
        double max = 0;
        for (int i = 0; i < count; i++) {
            double val = pingBuffer[(sampleCount - 1 - i) % MAX_SAMPLES];
            if (val > max) max = val;
        }
        return max;
    }

    // ════════════════════════════════════════════
    // ONLINE
    // ════════════════════════════════════════════

    /** Текущий онлайн */
    public int getCurrentOnline() {
        return Bukkit.getOnlinePlayers().size();
    }

    /** Мин. онлайн за N сэмплов */
    public int getMinOnline(int samples) {
        if (samples <= 0 || sampleCount <= 0) return getCurrentOnline();
        int count = Math.min(samples, sampleCount);
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            int val = onlineBuffer[(sampleCount - 1 - i) % MAX_SAMPLES];
            if (val < min) min = val;
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    /** Макс. онлайн за N сэмплов */
    public int getMaxOnline(int samples) {
        if (samples <= 0 || sampleCount <= 0) return getCurrentOnline();
        int count = Math.min(samples, sampleCount);
        int max = 0;
        for (int i = 0; i < count; i++) {
            int val = onlineBuffer[(sampleCount - 1 - i) % MAX_SAMPLES];
            if (val > max) max = val;
        }
        return max;
    }

    // ════════════════════════════════════════════
    // COLOR GRADIENTS — плавный 3-стопный градиент
    // ════════════════════════════════════════════

    /**
     * Плавный HEX-градиент для TPS (0–20).
     * 0  = #AA0000 (dark-red)
     * 10 = #FFAA00 (orange/yellow)
     * 20 = #00AA00 (green)
     */
    public static String tpsColor(double tps) {
        double clamped = Math.max(0, Math.min(20.0, tps));
        return gradient3(clamped, 0, 10, 20,
                         0xAA, 0x00, 0x00,
                         0xFF, 0xAA, 0x00,
                         0x00, 0xAA, 0x00);
    }

    /**
     * Плавный HEX-градиент для MSPT (0–50+).
     * 0  = #00AA00 (green)
     * 25 = #FFAA00 (yellow)
     * 50+ = #AA0000 (dark-red, clips at 100)
     */
    public static String msptColor(double mspt) {
        double clamped = Math.max(0, Math.min(100.0, mspt));
        return gradient3(clamped, 0, 25, 50,
                         0x00, 0xAA, 0x00,
                         0xFF, 0xAA, 0x00,
                         0xAA, 0x00, 0x00);
    }

    /**
     * Плавный HEX-градиент для RAM (0–100%).
     * 0%   = #00AA00 (green)
     * 50%  = #FFAA00 (yellow)
     * 100% = #AA0000 (dark-red)
     */
    public static String ramColor(double pct) {
        double clamped = Math.max(0, Math.min(100.0, pct));
        return gradient3(clamped, 0, 50, 100,
                         0x00, 0xAA, 0x00,
                         0xFF, 0xAA, 0x00,
                         0xAA, 0x00, 0x00);
    }

    /**
     * Плавный HEX-градиент для Ping (0–1000ms).
     * 0    = #00AA00 (green)
     * 200  = #55FF55 (light green)
     * 400  = #FFFF55 (yellow)
     * 600  = #FFAA00 (orange)
     * 800  = #FF5555 (light red)
     * 1000 = #AA0000 (dark-red)
     */
    public static String pingColor(double ping) {
        double clamped = Math.max(0, Math.min(1000.0, ping));
        if (clamped <= 200) {
            double t = clamped / 200.0;
            return lerpColor(0x00, 0xAA, 0x00, 0x55, 0xFF, 0x55, t);
        } else if (clamped <= 400) {
            double t = (clamped - 200) / 200.0;
            return lerpColor(0x55, 0xFF, 0x55, 0xFF, 0xFF, 0x55, t);
        } else if (clamped <= 600) {
            double t = (clamped - 400) / 200.0;
            return lerpColor(0xFF, 0xFF, 0x55, 0xFF, 0xAA, 0x00, t);
        } else if (clamped <= 800) {
            double t = (clamped - 600) / 200.0;
            return lerpColor(0xFF, 0xAA, 0x00, 0xFF, 0x55, 0x55, t);
        } else {
            double t = (clamped - 800) / 200.0;
            return lerpColor(0xFF, 0x55, 0x55, 0xAA, 0x00, 0x00, t);
        }
    }

    /** 3-стопный градиент */
    private static String gradient3(double val, double stop0, double stop1, double stop2,
                                     int r0, int g0, int b0,
                                     int r1, int g1, int b1,
                                     int r2, int g2, int b2) {
        if (val <= stop1) {
            double t = stop1 == stop0 ? 0 : (val - stop0) / (stop1 - stop0);
            return lerpColor(r0, g0, b0, r1, g1, b1, t);
        } else {
            double t = stop2 == stop1 ? 1.0 : (val - stop1) / (stop2 - stop1);
            return lerpColor(r1, g1, b1, r2, g2, b2, Math.min(1.0, t));
        }
    }

    /** Лерп между двумя цветами */
    private static String lerpColor(int r0, int g0, int b0, int r1, int g1, int b1, double t) {
        int r = lerp(r0, r1, t);
        int g = lerp(g0, g1, t);
        int b = lerp(b0, b1, t);
        return String.format("<#%02X%02X%02X>", r, g, b);
    }

    /** Линейная интерполяция int */
    private static int lerp(int a, int b, double t) {
        return (int) Math.round(a + (b - a) * t);
    }

    /** Текущая RAM % */
    private static double getRamUsage() {
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        long max = rt.maxMemory();
        if (max <= 0) return 0;
        return (double) used / (double) max * 100.0;
    }

}
