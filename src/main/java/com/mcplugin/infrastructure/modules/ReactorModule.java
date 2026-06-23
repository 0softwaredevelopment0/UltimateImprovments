package com.mcplugin.infrastructure.modules;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.energy.generation.reactor.ReactorListener;
import com.mcplugin.energy.generation.reactor.ReactorManager;
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
