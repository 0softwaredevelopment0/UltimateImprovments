package com.mcplugin.module;

import com.mcplugin.Main;
import com.mcplugin.features.unbreakablebreaker.UnbreakableBreakerManager;
import org.bukkit.plugin.java.JavaPlugin;

public class UnbreakableBreakerModule extends PluginModule {

    public UnbreakableBreakerModule() { super("UnbreakableBreaker", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        UnbreakableBreakerManager.init((Main) plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        UnbreakableBreakerManager.reloadConfig();
    }
}
