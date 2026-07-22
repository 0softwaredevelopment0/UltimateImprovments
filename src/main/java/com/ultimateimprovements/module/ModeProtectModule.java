package com.ultimateimprovements.module;

import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.mechanics.features.player.ModeProtectManager;
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
