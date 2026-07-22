package com.ultimateimprovments.module;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.mechanics.features.items.ItemKillManager;
import org.bukkit.plugin.java.JavaPlugin;

public class ItemKillModule extends PluginModule {

    public ItemKillModule() { super("ItemKill", "mechanics/features/item_kill", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        ItemKillManager.init((Main) plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        ItemKillManager.reloadConfig();
    }
}
