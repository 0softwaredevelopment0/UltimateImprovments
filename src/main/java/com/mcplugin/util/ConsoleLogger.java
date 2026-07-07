package com.mcplugin.util;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;

/**
 * Colourful console logger using MiniMessage and Paper's Adventure API.
 * <p>
 * Цветовая схема:
 * <ul>
 *   <li>{@link #info(String)} — <white>белый</white> (информационные сообщения)</li>
 *   <li>{@link #success(String)} — <green>зелёный</green> (успешные операции)</li>
 *   <li>{@link #warn(String)} — <yellow>жёлтый</yellow> (предупреждения)</li>
 *   <li>{@link #error(String)} — <red>красный</red> (ошибки)</li>
 * </ul>
 * <p>
 * Использует {@link Bukkit#getConsoleSender()} с {@link MiniMessage} — выводит цветной текст
 * в консоль (с поддержкой ANSI/Virtual Terminal).
 */
public final class ConsoleLogger {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static boolean initialized = false;

    private ConsoleLogger() {}

    /**
     * Инициализировать логгер. Должен вызываться в onEnable() после instance = this.
     */
    public static void init() {
        initialized = true;
    }

    /** <white>Белый</white> — информационные сообщения */
    public static void info(String message) {
        if (!initialized) return;
        Bukkit.getConsoleSender().sendMessage(MM.deserialize("<white>" + escape(message) + "</white>"));
    }

    /** <green>Зелёный</green> — успешные операции */
    public static void success(String message) {
        if (!initialized) return;
        Bukkit.getConsoleSender().sendMessage(MM.deserialize("<green>" + escape(message) + "</green>"));
    }

    /** <yellow>Жёлтый</yellow> — предупреждения */
    public static void warn(String message) {
        if (!initialized) return;
        Bukkit.getConsoleSender().sendMessage(MM.deserialize("<yellow>" + escape(message) + "</yellow>"));
    }

    /** <red>Красный</red> — ошибки */
    public static void error(String message) {
        if (!initialized) return;
        Bukkit.getConsoleSender().sendMessage(MM.deserialize("<red>" + escape(message) + "</red>"));
    }

    /**
     * Сырое MiniMessage-сообщение без экранирования (для ASCII-баннеров, градиентов).
     * Внимание: теги в message будут интерпретироваться как MiniMessage!
     */
    public static void raw(String miniMessage) {
        if (!initialized) return;
        Bukkit.getConsoleSender().sendMessage(MM.deserialize(miniMessage));
    }

    /**
     * Экранирует MiniMessage-чувствительные символы,
     * чтобы содержимое сообщения не интерпретировалось как теги.
     */
    private static String escape(String message) {
        if (message == null) return "";
        return message.replace("<", "\\<").replace(">", "\\>");
    }
}
