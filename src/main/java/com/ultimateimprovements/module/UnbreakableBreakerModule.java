package com.ultimateimprovements.module;

import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.mechanics.features.items.UnbreakableBreakerManager;
import org.bukkit.plugin.java.JavaPlugin;

public class UnbreakableBreakerModule extends PluginModule {

    public UnbreakableBreakerModule() { super("UnbreakableBreaker", "mechanics/features/unbreakable_breaker", false); }

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
