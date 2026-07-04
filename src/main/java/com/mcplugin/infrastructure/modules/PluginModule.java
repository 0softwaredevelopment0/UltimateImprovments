package com.mcplugin.infrastructure.modules;

import com.mcplugin.infrastructure.util.ConsoleLogger;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Базовая абстракция модуля плагина.
 * <p>
 * Каждый модуль независим: если {@link #onInit} выбрасывает исключение,
 * модуль считается отключённым, но остальные модули продолжают работать.
 * <p>
 * {@code essential = true} — модуль критичен для работы плагина,
 * {@code essential = false} — модуль можно отключить без потери основной функциональности.
 */
public abstract class PluginModule {

    private final String name;
    private final String modulePath;
    private boolean enabled = false;
    private final boolean essential;
    private String disableReason = null;

    public PluginModule(String name, String modulePath, boolean essential) {
        this.name = name;
        this.modulePath = modulePath;
        this.essential = essential;
    }

    /** Backward-compatible constructor without path */
    public PluginModule(String name, boolean essential) {
        this(name, name.toLowerCase().replace(" ", "_"), essential);
    }

    // =========================
    // GETTERS
    // =========================

    public String getName() { return name; }
    public String getModulePath() { return modulePath; }
    public boolean isEnabled() { return enabled; }
    public boolean isEssential() { return essential; }
    public String getDisableReason() { return disableReason; }

    // =========================
    // LIFECYCLE
    // =========================

    /**
     * Инициализирует модуль. В случае ошибки модуль отключается,
     * но исключение НЕ пробрасывается — плагин продолжает работу.
     *
     * @return true если модуль успешно инициализирован
     */
    public boolean initialize(JavaPlugin plugin) {
        if (enabled) return true;
        try {
            onInit(plugin);
            enabled = true;
            disableReason = null;
            ConsoleLogger.info("[Module:" + name + "] \u2713 Enabled");
            return true;
        } catch (Throwable t) {
            enabled = false;
            String msg = t.getMessage() != null ? t.getMessage() : "";
            disableReason = msg.isEmpty() ? t.getClass().getSimpleName() : msg;

            // Детектируем ошибку несовместимости Java-версии (Paper не может сконвертировать class)
            if (msg.contains("major version") || msg.contains("Unsupported class file")) {
                ConsoleLogger.error("[Module:" + name + "] \u2717 Java version mismatch!");
                ConsoleLogger.error("[Module:" + name + "]   Update your Java Runtime to fix this issue.");
            } else {
                ConsoleLogger.error("[Module:" + name + "] \u2717 FAILED: " + disableReason);
            }
            return false;
        }
    }

    /**
     * Отключает модуль. Ошибки при отключении логируются, но не пробрасываются.
     */
    public boolean disable(JavaPlugin plugin) {
        if (!enabled) return true;
        try {
            onDisable(plugin);
            ConsoleLogger.info("[Module:" + name + "] \u2713 Disabled");
        } catch (Throwable t) {
            ConsoleLogger.warn("[Module:" + name + "] Shutdown error: " + t.getMessage());
        }
        enabled = false;
        return true;
    }

    /**
     * Перезагружает конфигурацию модуля (если поддерживается).
     */
    public void reloadConfig(JavaPlugin plugin) {
        if (!enabled) return;
        try {
            onReloadConfig(plugin);
        } catch (Throwable t) {
            ConsoleLogger.warn("[Module:" + name + "] ReloadConfig error: " + t.getMessage());
        }
    }

    // =========================
    // ABSTRACT / OVERRIDE POINTS
    // =========================

    /** Выполнить инициализацию модуля. */
    protected abstract void onInit(JavaPlugin plugin) throws Exception;

    /** Выполнить остановку модуля. */
    protected abstract void onDisable(JavaPlugin plugin);

    /** Перезагрузить конфиг (по умолчанию — no-op). */
    protected void onReloadConfig(JavaPlugin plugin) {}
}
