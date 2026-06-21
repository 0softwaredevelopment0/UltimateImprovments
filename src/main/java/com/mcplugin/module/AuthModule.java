package com.mcplugin.module;

import com.mcplugin.Main;
import com.mcplugin.auth.AuthListener;
import com.mcplugin.auth.AuthManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль авторизации.
 * Essential — система auth критична для безопасности.
 */
public class AuthModule extends PluginModule {

    public AuthModule() {
        super("Auth", true);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;
        AuthManager.init();
        main.getServer().getPluginManager().registerEvents(new AuthListener(), main);
        plugin.getLogger().info("[AuthModule] ✓ Auth system initialized.");
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        // Nothing to clean up
    }
}
