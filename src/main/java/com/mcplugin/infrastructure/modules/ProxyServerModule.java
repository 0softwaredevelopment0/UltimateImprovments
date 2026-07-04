package com.mcplugin.infrastructure.modules;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.server.ProxyServerListener;
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
