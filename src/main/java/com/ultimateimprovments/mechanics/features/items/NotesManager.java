package com.ultimateimprovments.mechanics.features.items;

import com.ultimateimprovments.core.Main;

import org.bukkit.Bukkit;

/**
 * Entry point for the notes system.
 * Initializes the DB and registers event listeners.
 */
public class NotesManager {

    private static NotesManager instance;

    private NotesManager() {}

    public static void init() {
        instance = new NotesManager();
        Bukkit.getPluginManager().registerEvents(new NotesGUIListener(), Main.getInstance());
    }

    public static NotesManager getInstance() {
        return instance;
    }
}
