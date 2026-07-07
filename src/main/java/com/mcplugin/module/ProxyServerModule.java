package com.mcplugin.module;

import com.mcplugin.core.Main;
import com.mcplugin.server.ProxyServerListener;
import org.bukkit.plugin.java.JavaPlugin;

public class ProxyServerModule extends PluginModule {

    public ProxyServerModule() { super("ProxyServer", "infrastructure/server", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;
        ProxyServerListener.init(main);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}
}
