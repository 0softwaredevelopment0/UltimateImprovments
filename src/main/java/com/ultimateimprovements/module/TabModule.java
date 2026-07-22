package com.ultimateimprovements.module;

import com.ultimateimprovements.display.TabManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль кастомизации таб-листа:
 * header/footer + playerList objective (префикс/суффикс с плейсхолдерами).
 */
public class TabModule extends PluginModule {

    public TabModule() {
        super("Tab", "tab", false);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        TabManager.init();
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        TabManager.shutdown();
    }

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        TabManager.reload();
    }
}
