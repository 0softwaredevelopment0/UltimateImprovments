package com.mcplugin.module;

import com.mcplugin.core.Main;
import com.mcplugin.mechanics.features.blocks.ContainerTriggerManager;
import org.bukkit.plugin.java.JavaPlugin;

public class ContainerTriggerModule extends PluginModule {

    public ContainerTriggerModule() { super("ContainerTrigger", "mechanics/features/container_trigger", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        ContainerTriggerManager.init((Main) plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        ContainerTriggerManager.reloadConfig();
    }
}
