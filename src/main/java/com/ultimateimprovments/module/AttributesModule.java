package com.ultimateimprovments.module;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.mechanics.features.player.AttributesManager;
import org.bukkit.plugin.java.JavaPlugin;

public class AttributesModule extends PluginModule {

    public AttributesModule() { super("Attributes", "mechanics/features/attributes", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        AttributesManager.init((Main) plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        AttributesManager.reloadConfig();
    }
}
