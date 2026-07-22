package com.ultimateimprovements.module;

import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.listener.VoidProtectionListener;
import org.bukkit.plugin.java.JavaPlugin;

public class VoidProtectionModule extends PluginModule {

    public VoidProtectionModule() { super("VoidProtection", "infrastructure/listeners", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;
        main.getServer().getPluginManager().registerEvents(new VoidProtectionListener(), main);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}
}
