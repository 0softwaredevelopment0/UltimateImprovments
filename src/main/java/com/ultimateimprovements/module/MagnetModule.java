package com.ultimateimprovements.module;

import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.mechanics.environment.magnet.MagnetEventListener;
import com.ultimateimprovements.mechanics.environment.magnet.MagnetManager;
import org.bukkit.plugin.java.JavaPlugin;

public class MagnetModule extends PluginModule {

    public MagnetModule() { super("Magnet", "mechanics/environment/magnet", false); }

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
        com.ultimateimprovements.mechanics.environment.magnet.MagnetConfig.reloadConfig();
    }
}
