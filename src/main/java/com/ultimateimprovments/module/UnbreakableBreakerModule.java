package com.ultimateimprovments.module;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.mechanics.features.items.UnbreakableBreakerManager;
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
