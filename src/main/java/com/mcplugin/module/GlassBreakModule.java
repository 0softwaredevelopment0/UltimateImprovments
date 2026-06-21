package com.mcplugin.module;

import com.mcplugin.Main;
import com.mcplugin.features.glassbreak.GlassBreakManager;
import org.bukkit.plugin.java.JavaPlugin;

public class GlassBreakModule extends PluginModule {

    public GlassBreakModule() { super("GlassBreak", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        GlassBreakManager.init((Main) plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        GlassBreakManager.reloadConfig();
    }
}
