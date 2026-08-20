package com.ultimateimprovments.util;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;

/**
 * Colourful console logger using MiniMessage and Paper's Adventure API.
 * <p>
 * Colour scheme:
 * <ul>
 *   <li>{@link #info(String)} — <white>white</white> (informational messages)</li>
 *   <li>{@link #success(String)} — <green>green</green> (successful operations)</li>
 *   <li>{@link #warn(String)} — <yellow>yellow</yellow> (warnings)</li>
 *   <li>{@link #error(String)} — <red>red</red> (errors)</li>
 * </ul>
 * <p>
 * Uses {@link Bukkit#getConsoleSender()} with {@link MiniMessage} — prints coloured text
 * to the console (with ANSI/Virtual Terminal support).
 */
public final class ConsoleLogger {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static boolean initialized = false;

    private ConsoleLogger() {}

    /**
     * Initializes the logger. Must be called in onEnable() after instance = this.
     */
    public static void init() {
        initialized = true;
    }

    /** <white>White</white> — informational messages */
    public static void info(String message) {
        if (!initialized) return;
        Bukkit.getConsoleSender().sendMessage(MM.deserialize("<white>" + escape(message) + "</white>"));
    }

    /** <green>Green</green> — successful operations */
    public static void success(String message) {
        if (!initialized) return;
        Bukkit.getConsoleSender().sendMessage(MM.deserialize("<green>" + escape(message) + "</green>"));
    }

    /** <yellow>Yellow</yellow> — warnings */
    public static void warn(String message) {
        if (!initialized) return;
        Bukkit.getConsoleSender().sendMessage(MM.deserialize("<yellow>" + escape(message) + "</yellow>"));
    }

    /** <red>Red</red> — errors */
    public static void error(String message) {
        if (!initialized) return;
        Bukkit.getConsoleSender().sendMessage(MM.deserialize("<red>" + escape(message) + "</red>"));
    }

    /**
     * Logs a fully-formatted MiniMessage string — tags are interpreted and
     * rendered as colors (Paper console with ANSI/VT support).
     * Use for messages that already contain the colored plugin prefix
     * ({@link MessageUtil#PREFIX}).
     */
    public static void log(String miniMessage) {
        if (!initialized) return;
        Bukkit.getConsoleSender().sendMessage(MM.deserialize(miniMessage));
    }

    /**
     * Raw MiniMessage message without escaping (for ASCII banners, gradients).
     * Warning: tags in message will be interpreted as MiniMessage!
     */
    public static void raw(String miniMessage) {
        if (!initialized) return;
        Bukkit.getConsoleSender().sendMessage(MM.deserialize(miniMessage));
    }

    /**
     * Escapes MiniMessage-sensitive characters,
     * so the message content is not interpreted as tags.
     */
    private static String escape(String message) {
        if (message == null) return "";
        // Escape MiniMessage tags and strip legacy §-codes — MiniMessage
        // throws on § and should never receive one.
        return message.replace("<", "\\<")
                .replace(">", "\\>")
                .replaceAll("\u00A7[0-9a-fk-orx]", "")
                .replace("\u00A7", "");
    }
}
