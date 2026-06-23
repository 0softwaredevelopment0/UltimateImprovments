package com.mcplugin.infrastructure.modules;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.listeners.VoidProtectionListener;
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
