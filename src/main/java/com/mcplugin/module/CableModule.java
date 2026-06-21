package com.mcplugin.module;

import com.mcplugin.cable.CableNetwork;
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
