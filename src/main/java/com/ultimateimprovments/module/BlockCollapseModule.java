package com.ultimateimprovments.module;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.mechanics.features.collapse.BlockCollapseListener;
import com.ultimateimprovments.mechanics.features.collapse.BlockCollapseManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль системы обрушения блоков (липкость/тяжесть).
 */
public class BlockCollapseModule extends PluginModule {

    public BlockCollapseModule() {
        super("BlockCollapse", "mechanics/features/collapse", false);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;
        BlockCollapseManager.init(main);
        main.getServer().getPluginManager().registerEvents(new BlockCollapseListener(), main);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        BlockCollapseManager.shutdown();
    }

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        BlockCollapseManager.reload();
    }
}
