package com.mcplugin.infrastructure.modules;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.mechanics.features.player.ModeProtectManager;
import org.bukkit.plugin.java.JavaPlugin;

public class ModeProtectModule extends PluginModule {

    public ModeProtectModule() { super("ModeProtect", "mechanics/features/mode_protect", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        ModeProtectManager.init((Main) plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        ModeProtectManager.reloadConfig();
    }
}
