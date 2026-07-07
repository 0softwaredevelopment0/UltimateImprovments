package com.mcplugin.module;

import com.mcplugin.core.Main;
import com.mcplugin.mechanics.features.blocks.BlockDmgManager;
import org.bukkit.plugin.java.JavaPlugin;

public class BlockDmgModule extends PluginModule {

    public BlockDmgModule() { super("BlockDmg", "mechanics/features/block_damage", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        BlockDmgManager.init((Main) plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        BlockDmgManager.reloadConfig();
    }
}
