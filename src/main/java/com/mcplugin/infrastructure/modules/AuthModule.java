package com.mcplugin.infrastructure.modules;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.ConsoleLogger;
import com.mcplugin.mechanics.security.auth.AuthListener;
import com.mcplugin.mechanics.security.auth.AuthManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль авторизации.
 * Essential — система auth критична для безопасности.
 */
public class AuthModule extends PluginModule {

    public AuthModule() {
        super("Auth", "mechanics/security/auth", true);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;
        AuthManager.init();
        main.getServer().getPluginManager().registerEvents(new AuthListener(), main);
        ConsoleLogger.info("[AuthModule] ✔ Auth system initialized.");
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        // Nothing to clean up
    }
}
