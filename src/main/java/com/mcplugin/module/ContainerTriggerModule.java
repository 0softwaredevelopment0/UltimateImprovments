package com.mcplugin.module;

import com.mcplugin.Main;
import com.mcplugin.features.containertrigger.ContainerTriggerManager;
import org.bukkit.plugin.java.JavaPlugin;

public class ContainerTriggerModule extends PluginModule {

    public ContainerTriggerModule() { super("ContainerTrigger", false); }

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
