package com.mcplugin.mechanics.features.items;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.ConsoleLogger;

import org.bukkit.Bukkit;

/**
 * Точка входа для системы заметок.
 * Инициализирует БД и регистрирует слушатели событий.
 */
public class NotesManager {

    private static NotesManager instance;

    private NotesManager() {}

    public static void init() {
        instance = new NotesManager();
        Bukkit.getPluginManager().registerEvents(new NotesGUIListener(), Main.getInstance());
        ConsoleLogger.info("[Notes] Manager initialized.");
    }

    public static NotesManager getInstance() {
        return instance;
    }
}
