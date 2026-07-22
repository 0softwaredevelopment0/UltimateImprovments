package com.ultimateimprovments.module;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.mechanics.features.blocks.GlassBreakManager;
import org.bukkit.plugin.java.JavaPlugin;

public class GlassBreakModule extends PluginModule {

    public GlassBreakModule() { super("GlassBreak", "mechanics/features/glass_break", false); }

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
