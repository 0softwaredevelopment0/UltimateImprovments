package com.ultimateimprovments.module;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.server.PacketGuard;
import org.bukkit.plugin.java.JavaPlugin;

public class PacketGuardModule extends PluginModule {

    public PacketGuardModule() { super("PacketGuard", "infrastructure/server", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;
        PacketGuard.init(main);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}
}
