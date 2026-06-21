package com.mcplugin.module;

import com.mcplugin.Main;
import com.mcplugin.features.terracotaspeed.TerracotaSpeedManager;
import org.bukkit.plugin.java.JavaPlugin;

public class TerracotaSpeedModule extends PluginModule {

    public TerracotaSpeedModule() { super("TerracotaSpeed", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        TerracotaSpeedManager.init((Main) plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        TerracotaSpeedManager.reloadConfig();
    }
}
