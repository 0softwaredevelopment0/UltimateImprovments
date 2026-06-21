package com.mcplugin.module;

import com.mcplugin.features.lightning.LightningManager;
import org.bukkit.plugin.java.JavaPlugin;

public class LightningModule extends PluginModule {

    public LightningModule() { super("Lightning", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        LightningManager.init();
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}
}
