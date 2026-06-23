package com.mcplugin.infrastructure.modules;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.commands.PowerManager;
import com.mcplugin.infrastructure.listeners.PowerInterceptListener;
import org.bukkit.plugin.java.JavaPlugin;

public class PowerModule extends PluginModule {

    public PowerModule() { super("Power", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;
        PowerManager.init();
        main.getServer().getPluginManager().registerEvents(new PowerInterceptListener(), main);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        PowerManager.reloadConfig();
    }
}
