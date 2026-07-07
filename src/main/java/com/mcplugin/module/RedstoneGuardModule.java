package com.mcplugin.module;

import com.mcplugin.core.Main;
import com.mcplugin.server.EmergencyEntitiesKill;
import com.mcplugin.server.RedstoneGuard;
import com.mcplugin.server.RedstoneGuardListener;
import com.mcplugin.server.ServerOverloadWarning;
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
