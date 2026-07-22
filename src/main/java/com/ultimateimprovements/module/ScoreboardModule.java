package com.ultimateimprovements.module;

import com.ultimateimprovements.display.ScoreboardManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль кастомных скорбордов.
 * Настраиваемые скорборды с условиями, MiniMessage, плейсхолдерами.
 */
public class ScoreboardModule extends PluginModule {

    public ScoreboardModule() {
        super("Scoreboard", "scoreboard", false);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        ScoreboardManager.init();
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        ScoreboardManager.shutdown();
    }

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        ScoreboardManager.reload();
    }
}
