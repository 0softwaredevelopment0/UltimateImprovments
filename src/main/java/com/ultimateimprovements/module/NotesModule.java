package com.ultimateimprovements.module;

import com.ultimateimprovements.mechanics.features.items.NotesManager;
import org.bukkit.plugin.java.JavaPlugin;

public class NotesModule extends PluginModule {

    public NotesModule() { super("Notes", "mechanics/features/notes", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        NotesManager.init();
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {}
}
