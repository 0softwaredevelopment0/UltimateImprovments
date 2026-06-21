package com.mcplugin.module;

import com.mcplugin.Main;
import com.mcplugin.server.PacketGuard;
import org.bukkit.plugin.java.JavaPlugin;

public class PacketGuardModule extends PluginModule {

    public PacketGuardModule() { super("PacketGuard", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;
        PacketGuard.init(main);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}
}
