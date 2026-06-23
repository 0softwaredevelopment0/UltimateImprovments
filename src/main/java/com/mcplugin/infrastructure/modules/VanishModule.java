package com.mcplugin.infrastructure.modules;

import com.mcplugin.mechanics.features.player.VanishManager;
import org.bukkit.plugin.java.JavaPlugin;

public class VanishModule extends PluginModule {

    public VanishModule() { super("Vanish", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        VanishManager.init();
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        VanishManager.reloadConfig();
    }
}
