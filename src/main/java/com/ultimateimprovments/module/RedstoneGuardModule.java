package com.ultimateimprovments.module;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.server.EmergencyEntitiesKill;
import com.ultimateimprovments.server.RedstoneGuard;
import com.ultimateimprovments.server.RedstoneGuardListener;
import com.ultimateimprovments.server.ServerOverloadWarning;
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
