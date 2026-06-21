package com.mcplugin.module;

import com.mcplugin.features.vanish.VanishManager;
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
