package com.mcplugin.infrastructure.modules;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.mechanics.features.blocks.GlassBreakManager;
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
