package com.ultimateimprovments.module;

import com.ultimateimprovments.display.BelowNameManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Module wrapper for the BelowName scoreboard feature.
 * Shows formatted text (MiniMessage + placeholders) below player nametags.
 */
public class BelowNameModule extends PluginModule {

    public BelowNameModule() {
        super("BelowName", "infrastructure/belowname", false);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        BelowNameManager.init();
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        BelowNameManager.shutdown();
    }

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        BelowNameManager.reload();
    }
}
