package com.mcplugin.module;

import com.mcplugin.mechanics.environment.lightning.LightningManager;
import org.bukkit.plugin.java.JavaPlugin;

public class LightningModule extends PluginModule {

    public LightningModule() { super("Lightning", "mechanics/environment/lightning", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        LightningManager.init();
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}
}
