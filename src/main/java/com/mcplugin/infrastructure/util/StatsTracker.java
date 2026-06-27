package com.mcplugin.infrastructure.util;

import com.mcplugin.infrastructure.core.Main;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.management.ManagementFactory;

/**
 * 📊 StatsTracker — сбор и хранение статистики сервера за временные окна.
 * <p>
 * Каждые 20 тиков (1 сек) записывает текущие TPS, MSPT, RAM%, Online
 * в циклические буферы. Позволяет получать min/avg/max за любой период
 * (1s, 5m, 1h, 1d, ss = server session).
 * <p>
 * Используется {@link PlaceholderResolver} для плейсхолдеров
 * {tps_1s}, {mspt_5m}, {online_max_1h}, {ram_avg_ss} и т.д.
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

        int idx = sampleCount % MAX_SAMPLES;
        tpsBuffer[idx] = tps;
        msptBuffer[idx] = mspt;
        ramBuffer[idx] = ram;
        onlineBuffer[idx] = online;
        sampleCount++;
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
            // TPS capped at 20.0 by Paper, but just in case
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
    // COLOR GRADIENTS
    // ════════════════════════════════════════════

    /**
     * HEX-градиент для TPS (0–20).
     * 0 = <#AA0000> (dark-red), 10 = <#FFAA00> (orange), 20 = <#00AA00> (green)
     */
    public static String tpsColor(double tps) {
        double clamped = Math.max(0, Math.min(20.0, tps));
        if (clamped >= 20.0) return "<#00AA00>";
        if (clamped >= 15.0) {
            double t = (clamped - 15.0) / 5.0;
            int r = lerp(0xFF, 0x00, t);
            int g = lerp(0xAA, 0xAA, t);
            int b = lerp(0x00, 0x00, t);
            return String.format("<#%02X%02X%02X>", r, g, b);
        }
        if (clamped >= 10.0) {
            double t = (clamped - 10.0) / 5.0;
            int r = 0xFF;
            int g = lerp(0x55, 0xAA, t);
            int b = 0x00;
            return String.format("<#%02X%02X%02X>", r, g, b);
        }
        if (clamped >= 5.0) {
            double t = (clamped - 5.0) / 5.0;
            int r = lerp(0xAA, 0xFF, t);
            int g = lerp(0x00, 0x55, t);
            int b = 0x00;
            return String.format("<#%02X%02X%02X>", r, g, b);
        }
        // 0-5
        double t = clamped / 5.0;
        int r = lerp(0xAA, 0xAA, t);
        int g = lerp(0x00, 0x00, t);
        int b = 0x00;
        return String.format("<#%02X%02X%02X>", r, g, b);
    }

    /**
     * HEX-градиент для MSPT (0–50+).
     * 0 = <#00AA00> (green), 25 = <#FFAA00> (yellow), 50+ = <#AA0000> (dark-red)
     */
    public static String msptColor(double mspt) {
        double clamped = Math.max(0, Math.min(100.0, mspt));
        if (clamped >= 50.0) {
            double t = Math.min(1.0, (clamped - 50.0) / 50.0);
            int r = lerp(0xFF, 0xAA, t);
            int g = lerp(0x55, 0x00, t);
            int b = lerp(0x00, 0x00, t);
            return String.format("<#%02X%02X%02X>", r, g, b);
        }
        if (clamped >= 25.0) {
            double t = (clamped - 25.0) / 25.0;
            int r = lerp(0x00, 0xFF, t);
            int g = lerp(0xAA, 0x55, t);
            int b = 0x00;
            return String.format("<#%02X%02X%02X>", r, g, b);
        }
        // 0-25
        double t = clamped / 25.0;
        int r = 0x00;
        int g = lerp(0xAA, 0xAA, t);
        int b = 0x00;
        return String.format("<#%02X%02X%02X>", r, g, b);
    }

    /**
     * HEX-градиент для RAM (0–100%).
     * 0% = <#00AA00> (green), 50% = <#FFAA00> (yellow), 100% = <#AA0000> (dark-red)
     */
    public static String ramColor(double pct) {
        double clamped = Math.max(0, Math.min(100.0, pct));
        if (clamped >= 75.0) {
            double t = (clamped - 75.0) / 25.0;
            int r = lerp(0xFF, 0xAA, t);
            int g = lerp(0x55, 0x00, t);
            int b = lerp(0x00, 0x00, t);
            return String.format("<#%02X%02X%02X>", r, g, b);
        }
        if (clamped >= 50.0) {
            double t = (clamped - 50.0) / 25.0;
            int r = lerp(0x00, 0xFF, t);
            int g = lerp(0xAA, 0x55, t);
            int b = 0x00;
            return String.format("<#%02X%02X%02X>", r, g, b);
        }
        // 0-50
        double t = clamped / 50.0;
        int r = 0x00;
        int g = lerp(0xAA, 0xAA, t);
        int b = 0x00;
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
