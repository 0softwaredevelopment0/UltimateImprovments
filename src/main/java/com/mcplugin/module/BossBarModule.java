package com.mcplugin.module;

import com.mcplugin.display.BossBarManager;
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
}
