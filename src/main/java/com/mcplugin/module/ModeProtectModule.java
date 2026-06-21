package com.mcplugin.module;

import com.mcplugin.Main;
import com.mcplugin.features.modeprotect.ModeProtectManager;
import org.bukkit.plugin.java.JavaPlugin;

public class ModeProtectModule extends PluginModule {

    public ModeProtectModule() { super("ModeProtect", false); }

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
