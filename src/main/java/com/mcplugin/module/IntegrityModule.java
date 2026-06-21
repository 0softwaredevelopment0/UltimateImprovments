package com.mcplugin.module;

import com.mcplugin.Main;
import com.mcplugin.features.integrity.IntegrityManager;
import com.mcplugin.features.integrity.IntegrityListener;
import com.mcplugin.features.integrity.IntegrityCombineListener;
import org.bukkit.plugin.java.JavaPlugin;

public class IntegrityModule extends PluginModule {

    public IntegrityModule() { super("Integrity", false); }

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
