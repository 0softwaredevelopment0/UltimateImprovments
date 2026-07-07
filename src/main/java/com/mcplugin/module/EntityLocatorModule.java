package com.mcplugin.module;

import com.mcplugin.core.Main;
import com.mcplugin.mechanics.features.world.EntityLocatorManager;
import org.bukkit.plugin.java.JavaPlugin;

public class EntityLocatorModule extends PluginModule {

    public EntityLocatorModule() { super("EntityLocator", "mechanics/features/entity_locator", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        EntityLocatorManager.init((Main) plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        EntityLocatorManager.reloadConfig();
    }
}
