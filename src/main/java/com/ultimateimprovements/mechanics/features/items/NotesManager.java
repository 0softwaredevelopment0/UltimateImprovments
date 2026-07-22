package com.ultimateimprovements.mechanics.features.items;

import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.util.ConsoleLogger;

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
