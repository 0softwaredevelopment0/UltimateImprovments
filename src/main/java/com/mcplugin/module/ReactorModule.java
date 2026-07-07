package com.mcplugin.module;

import com.mcplugin.core.Main;
import com.mcplugin.core.TaskManager;
import com.mcplugin.energy.generation.reactor.ReactorListener;
import com.mcplugin.energy.generation.reactor.ReactorManager;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public class ReactorModule extends PluginModule {

    private ReactorListener reactorListener;

    public ReactorModule() { super("Reactor", "energy/generation/reactor", true); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;
        ReactorManager.init();
        reactorListener = new ReactorListener();
        main.getServer().getPluginManager().registerEvents(reactorListener, main);
        TaskManager.getInstance().startReactorTask(main);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        TaskManager.getInstance().stopReactorTask();
        if (reactorListener != null) {
            HandlerList.unregisterAll(reactorListener);
            reactorListener = null;
        }
        ReactorManager.shutdown();
    }
}
