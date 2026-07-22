package com.ultimateimprovments.module;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.mechanics.features.player.ShieldSlownessManager;
import org.bukkit.plugin.java.JavaPlugin;

public class ShieldSlownessModule extends PluginModule {

    public ShieldSlownessModule() { super("ShieldSlowness", "mechanics/features/shield_slowness", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        ShieldSlownessManager.init((Main) plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        ShieldSlownessManager.reloadConfig();
    }
}
