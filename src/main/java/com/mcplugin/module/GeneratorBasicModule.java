package com.mcplugin.module;

import com.mcplugin.energy.generation.basic.GeneratorManager;
import com.mcplugin.core.Main;
import com.mcplugin.core.TaskManager;
import org.bukkit.plugin.java.JavaPlugin;

public class GeneratorBasicModule extends PluginModule {

    public GeneratorBasicModule() { super("Basic Generator", "energy/generation/basic", true); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        GeneratorManager.init();
        TaskManager.getInstance().startGeneratorTask((Main) plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        TaskManager.getInstance().stopGeneratorTask();
        GeneratorManager.shutdown();
    }
}
