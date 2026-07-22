package com.ultimateimprovements.module;

import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.mechanics.features.world.DeathBellManager;
import org.bukkit.plugin.java.JavaPlugin;

public class DeathBellModule extends PluginModule {

    public DeathBellModule() { super("DeathBell", "mechanics/features/bell_lightning", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        DeathBellManager.init((Main) plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        DeathBellManager.reloadConfig();
    }
}
