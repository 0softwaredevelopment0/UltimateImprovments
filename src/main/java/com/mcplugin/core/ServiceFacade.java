package com.mcplugin.core;

import com.mcplugin.config.MessagesManager;
import com.mcplugin.module.ModuleManager;
import com.mcplugin.util.ConsoleLogger;
import com.mcplugin.util.MessageUtil;
import com.mcplugin.mechanics.security.check.CheckManager;
import org.bukkit.entity.Player;

/**
 * ServiceFacade — централизованная точка доступа к сервисам плагина.
 * <p>
 * Заменяет разрозненные статические импорты единым фасадом.
 * Упрощает рефакторинг: все зависимости в одном месте.
 * <p>
 * Пример:
 * <pre>{@code
 * ServiceFacade.info("Hello");
 * ServiceFacade.mm().getModule("name");
 * ServiceFacade.message("key", "default");
 * }</pre>
 */
public final class ServiceFacade {

    private ServiceFacade() {}

    // ========================================================================
    // LOGGING
    // ========================================================================

    /** Shorthand: {@code ServiceFacade.info(msg)} */
    public static void info(String msg) {
        ConsoleLogger.info(msg);
    }

    /** Shorthand: {@code ServiceFacade.warn(msg)} */
    public static void warn(String msg) {
        ConsoleLogger.warn(msg);
    }

    /** Shorthand: {@code ServiceFacade.error(msg)} */
    public static void error(String msg) {
        ConsoleLogger.error(msg);
    }

    /** Shorthand: {@code ServiceFacade.success(msg)} */
    public static void success(String msg) {
        ConsoleLogger.success(msg);
    }

    // ========================================================================
    // MESSAGES
    // ========================================================================

    /**
     * Возвращает строку из {@code config.yml#messages.<path>} (с фолбеком на
     * {@code config.yml#messages_en.<path>}). Префикс секции НЕ нужен в path.
     * <pre>ServiceFacade.message("auth.gui.register", "Register");</pre>
     * <p>
     * С v26.2 отдельный messages.yml/messages-en.yml больше не существует —
     * все локализованные строки живут внутри единого config.yml.
     */
    public static String message(String path, String def) {
        return MessagesManager.getString(path, def);
    }

    /**
     * Shorthand: {@code ServiceFacade.parsed("key", "default")}
     * Возвращает распарсенный Component из секции messages: в config.yml.
     */
    public static net.kyori.adventure.text.Component parsed(String key, String def) {
        return MessageUtil.parse(MessagesManager.getString(key, def));
    }

    // ========================================================================
    // MODULE MANAGER
    // ========================================================================

    /**
     * Централизованный доступ к {@link ModuleManager}.
     * <pre>ServiceFacade.mm().getModule("MyModule");</pre>
     */
    public static ModuleManager mm() {
        return ModuleManager.getInstance();
    }

    // ========================================================================
    // PLUGIN INSTANCE
    // ========================================================================

    /**
     * Централизованный доступ к экземпляру плагина.
     * <pre>ServiceFacade.plugin().getConfig();</pre>
     */
    public static Main plugin() {
        return Main.getInstance();
    }

    // ========================================================================
    // CHECK MANAGER
    // ========================================================================

    /**
     * Централизованный доступ к {@link CheckManager}.
     */
    public static CheckManager checks() {
        return CheckManager.getInstance();
    }

    // ========================================================================
    // PERMISSIONS — удобные хелперы
    // ========================================================================

    /**
     * Проверяет, есть ли у игрока базовое право на команды плагина.
     */
    public static boolean canUseCommands(Player player) {
        return player.hasPermission("mcplugin");
    }
}
