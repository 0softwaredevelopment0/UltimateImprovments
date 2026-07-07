package com.mcplugin.module;

import com.mcplugin.core.Main;
import com.mcplugin.mechanics.features.integrity.IntegrityManager;
import com.mcplugin.mechanics.features.integrity.IntegrityListener;
import com.mcplugin.mechanics.features.integrity.IntegrityCombineListener;
import org.bukkit.plugin.java.JavaPlugin;

public class IntegrityModule extends PluginModule {

    public IntegrityModule() { super("Integrity", "mechanics/features/integrity", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;
        IntegrityManager.init(main);
        main.getServer().getPluginManager().registerEvents(new IntegrityListener(), main);
        main.getServer().getPluginManager().registerEvents(new IntegrityCombineListener(), main);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        IntegrityManager.reloadConfig();
    }
}
