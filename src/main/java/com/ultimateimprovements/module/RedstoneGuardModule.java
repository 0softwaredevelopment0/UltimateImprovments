package com.ultimateimprovements.module;

import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.server.EmergencyEntitiesKill;
import com.ultimateimprovements.server.RedstoneGuard;
import com.ultimateimprovements.server.RedstoneGuardListener;
import com.ultimateimprovements.server.ServerOverloadWarning;
import org.bukkit.plugin.java.JavaPlugin;

public class RedstoneGuardModule extends PluginModule {

    public RedstoneGuardModule() { super("RedstoneGuard", "infrastructure/server", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;
        RedstoneGuard.init(main);
        main.getServer().getPluginManager().registerEvents(new RedstoneGuardListener(), main);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        RedstoneGuard.reload();
        EmergencyEntitiesKill.reload();
        ServerOverloadWarning.reload();
    }
}
