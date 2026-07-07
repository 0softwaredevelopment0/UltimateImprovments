package com.mcplugin.mechanics.security.codepanel;

import com.mcplugin.core.Main;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CodePanelSession {

    private static final Map<UUID, StringBuilder> input = new HashMap<>();

    // Enter cooldown (ms timestamp when player can press Enter again)
    private static final Map<UUID, Long> enterCooldown = new HashMap<>();

    // =========================
    // INPUT
    // =========================
    public static StringBuilder get(UUID uuid) {
        if (!isSafe()) {
            clearAll();
            return new StringBuilder();
        }
        return input.computeIfAbsent(uuid, k -> new StringBuilder());
    }

    public static void reset(UUID uuid) {
        input.put(uuid, new StringBuilder());
    }

    public static String getCode(UUID uuid) {
        return get(uuid).toString();
    }

    public static void add(UUID uuid, String digit) {
        if (digit == null) return;
        get(uuid).append(digit);
    }

    // =========================
    // ENTER COOLDOWN
    // =========================
    public static boolean isEnterOnCooldown(UUID uuid) {
        return enterCooldown.getOrDefault(uuid, 0L) > System.currentTimeMillis();
    }

    public static long getRemainingCooldown(UUID uuid) {
        long end = enterCooldown.getOrDefault(uuid, 0L);
        return Math.max(0, end - System.currentTimeMillis());
    }

    public static void setEnterCooldown(UUID uuid, long durationMs) {
        enterCooldown.put(uuid, System.currentTimeMillis() + durationMs);
    }

    // =========================
    // CLEAN
    // =========================
    public static void clearAll() {
        input.clear();
        enterCooldown.clear();
    }

    // =========================
    // SAFE CHECK
    // =========================
    private static boolean isSafe() {
        return Main.getInstance() != null && Main.getInstance().getConfig() != null;
    }
}