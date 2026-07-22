package com.ultimateimprovments.module;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.listener.VoidProtectionListener;
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
