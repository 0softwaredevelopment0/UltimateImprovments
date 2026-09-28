package com.ultimateimprovments.core;

import com.ultimateimprovments.display.BossBarManager;
import com.ultimateimprovments.display.ScoreboardManager;
import com.ultimateimprovments.display.TabManager;
import com.ultimateimprovments.module.ModuleManager;
import com.ultimateimprovments.module.PluginModule;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * DisplayModules — registration of the UI addon's modules (tab list,
 * scoreboard, boss bar). Moved out of ui-other's SimpleModules.
 */
public final class DisplayModules {

    private DisplayModules() {}

    public static void registerAll(ModuleManager mm) {
        // Tab
        mm.register(new PluginModule("Tab", "tab", false) {
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
        });

        // Scoreboard
        mm.register(new PluginModule("Scoreboard", "scoreboard", false) {
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
        });

        // BossBar
        mm.register(new PluginModule("BossBar", "infrastructure/bossbar", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                BossBarManager.init();
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                BossBarManager.shutdown();
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                BossBarManager.reload();
            }
        });
    }
}
