package com.ultimateimprovments.module;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.mechanics.features.blocks.EnderChestManager;
import org.bukkit.plugin.java.JavaPlugin;

public class EnderChestModule extends PluginModule {

    public EnderChestModule() { super("EnderChest", "mechanics/features/ender_chest", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        EnderChestManager.init((Main) plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        EnderChestManager.reloadConfig();
    }
}
