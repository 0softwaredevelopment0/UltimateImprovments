package com.mcplugin.module;

import com.mcplugin.core.Main;
import com.mcplugin.command.PowerManager;
import com.mcplugin.listener.PowerInterceptListener;
import org.bukkit.plugin.java.JavaPlugin;

public class PowerModule extends PluginModule {

    public PowerModule() { super("Power", "infrastructure/core", false); }

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
