package com.ultimateimprovments.module;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.mechanics.features.world.DragonEggManager;
import org.bukkit.plugin.java.JavaPlugin;

public class DragonEggModule extends PluginModule {

    public DragonEggModule() { super("DragonEgg", "mechanics/features/dragon_egg", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        DragonEggManager.init((Main) plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        DragonEggManager.reloadConfig();
    }
}
