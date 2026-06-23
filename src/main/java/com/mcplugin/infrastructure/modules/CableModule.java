package com.mcplugin.infrastructure.modules;

import com.mcplugin.energy.transfer.cable.CableNetwork;
import org.bukkit.plugin.java.JavaPlugin;

public class CableModule extends PluginModule {

    public CableModule() { super("Cable", true); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        CableNetwork.init();
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}
}
