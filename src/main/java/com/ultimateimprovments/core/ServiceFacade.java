package com.ultimateimprovments.core;

import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.module.ModuleManager;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.mechanics.security.check.CheckManager;
import org.bukkit.entity.Player;

/**
 * ServiceFacade — centralized access point to plugin services.
 * <p>
 * Replaces scattered static imports with a single facade.
 * Simplifies refactoring: all dependencies in one place.
 * <p>
 * Example:
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
     * Returns a string from {@code config.yml#messages.<path>} (with a fallback to
     * {@code config.yml#messages_en.<path>}). The section prefix is NOT required in path.
     * <pre>ServiceFacade.message("auth.gui.register", "Register");</pre>
     * <p>
     * Since v26.2 a separate messages.yml/messages-en.yml no longer exists —
     * all localized strings live inside the single config.yml.
     */
    public static String message(String path, String def) {
        return MessagesManager.getString(path, def);
    }

    /**
     * Shorthand: {@code ServiceFacade.parsed("key", "default")}
     * Returns a parsed Component from the messages: section in config.yml.
     */
    public static net.kyori.adventure.text.Component parsed(String key, String def) {
        return MessageUtil.parse(MessagesManager.getString(key, def));
    }

    // ========================================================================
    // MODULE MANAGER
    // ========================================================================

    /**
     * Centralized access to {@link ModuleManager}.
     * <pre>ServiceFacade.mm().getModule("MyModule");</pre>
     */
    public static ModuleManager mm() {
        return ModuleManager.getInstance();
    }

    // ========================================================================
    // PLUGIN INSTANCE
    // ========================================================================

    /**
     * Centralized access to the plugin instance.
     * <pre>ServiceFacade.plugin().getConfig();</pre>
     */
    public static Main plugin() {
        return Main.getInstance();
    }

    // ========================================================================
    // CHECK MANAGER
    // ========================================================================

    /**
     * Centralized access to {@link CheckManager}.
     */
    public static CheckManager checks() {
        return CheckManager.getInstance();
    }

    // ========================================================================
    // PERMISSIONS — convenience helpers
    // ========================================================================

    /**
     * Checks whether the player has the base permission for the plugin's commands.
     */
    public static boolean canUseCommands(Player player) {
        return player.hasPermission("ui");
    }
}
