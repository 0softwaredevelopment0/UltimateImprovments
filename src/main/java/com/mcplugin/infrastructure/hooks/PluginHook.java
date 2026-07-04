package com.mcplugin.infrastructure.hooks;

import com.mcplugin.infrastructure.util.ConsoleLogger;
import org.bukkit.Bukkit;

/**
 * Утилита для безопасных хуков к softdepend-плагинам.
 * <p>
 * Позволяет проверить наличие плагина {@code Bukkit.getPluginManager().getPlugin()}
 * ДО того, как загружаются классы с зависимостью от этого плагина.
 * Это предотвращает {@link NoClassDefFoundError} при отсутствии softdepend-плагина.
 * <p>
 * Пример:
 * <pre>{@code
 * if (PluginHook.check("Vault", "Economy")) {
 *     new VaultIntegration(plugin);  // безопасно — Vault есть
 * }
 * }</pre>
 */
public final class PluginHook {

    private PluginHook() {}

    /**
     * Проверяет, загружен ли плагин с указанным именем.
     * <p>
     * Если плагин не найден — логирует чистое сообщение без stack trace
     * и возвращает {@code false}. Хук следует пропустить.
     *
     * @param pluginName  имя плагина (из plugin.yml, например "Vault")
     * @param featureName название фичи/хука для сообщения в консоль
     * @return {@code true} если плагин загружен, {@code false} если нет
     */
    public static boolean check(String pluginName, String featureName) {
        if (Bukkit.getPluginManager().getPlugin(pluginName) == null) {
            ConsoleLogger.info("[Hook:" + featureName + "] " + pluginName
                    + " not found — " + featureName + " hook disabled.");
            return false;
        }
        return true;
    }

    /**
     * Проверяет, загружен ли плагин, и возвращает {@code false} если нет.
     * Упрощённая версия без кастомного имени фичи — использует имя плагина.
     */
    public static boolean check(String pluginName) {
        return check(pluginName, pluginName);
    }
}
