package com.mcplugin.module;

import com.mcplugin.Main;
import com.mcplugin.core1.ReactorListener;
import com.mcplugin.core1.ReactorManager;
import org.bukkit.plugin.java.JavaPlugin;

public class ReactorModule extends PluginModule {

    public ReactorModule() { super("Reactor", true); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;
        ReactorManager.init();
        main.getServer().getPluginManager().registerEvents(new ReactorListener(), main);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}
}
