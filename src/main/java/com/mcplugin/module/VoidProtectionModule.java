package com.mcplugin.module;

import com.mcplugin.Main;
import com.mcplugin.listeners.VoidProtectionListener;
import org.bukkit.plugin.java.JavaPlugin;

public class VoidProtectionModule extends PluginModule {

    public VoidProtectionModule() { super("VoidProtection", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;
        main.getServer().getPluginManager().registerEvents(new VoidProtectionListener(), main);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}
}
