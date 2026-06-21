package com.mcplugin.module;

import com.mcplugin.Main;
import com.mcplugin.features.entitylocator.EntityLocatorManager;
import org.bukkit.plugin.java.JavaPlugin;

public class EntityLocatorModule extends PluginModule {

    public EntityLocatorModule() { super("EntityLocator", false); }

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
