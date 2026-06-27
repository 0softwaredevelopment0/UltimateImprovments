package com.mcplugin.infrastructure.modules;

import com.mcplugin.infrastructure.bossbar.BossBarManager;
import com.mcplugin.infrastructure.database.PlayerSettingsDB;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Module wrapper for the BossBar system.
 */
public class BossBarModule extends PluginModule {

    public BossBarModule() {
        super("BossBar", "infrastructure/bossbar", false);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        PlayerSettingsDB.init();
        BossBarManager.init();
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        BossBarManager.shutdown();
    }
}
