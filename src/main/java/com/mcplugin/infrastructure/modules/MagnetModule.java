package com.mcplugin.infrastructure.modules;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.mechanics.environment.magnet.MagnetEventListener;
import com.mcplugin.mechanics.environment.magnet.MagnetManager;
import org.bukkit.plugin.java.JavaPlugin;

public class MagnetModule extends PluginModule {

    public MagnetModule() { super("Magnet", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;
        MagnetManager.init(main);
        main.getServer().getPluginManager().registerEvents(new MagnetEventListener(), main);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        com.mcplugin.mechanics.environment.magnet.MagnetConfig.reloadConfig();
    }
}
