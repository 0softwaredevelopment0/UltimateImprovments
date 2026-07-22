package com.ultimateimprovments.module;

import com.ultimateimprovments.energy.generation.basic.GeneratorManager;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.core.TaskManager;
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
