package com.mcplugin.module;

import com.mcplugin.Main;
import com.mcplugin.features.dragonegg.DragonEggManager;
import org.bukkit.plugin.java.JavaPlugin;

public class DragonEggModule extends PluginModule {

    public DragonEggModule() { super("DragonEgg", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        DragonEggManager.init((Main) plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        DragonEggManager.reloadConfig();
    }
}
