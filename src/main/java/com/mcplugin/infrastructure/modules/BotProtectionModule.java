package com.mcplugin.infrastructure.modules;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.ConsoleLogger;
import com.mcplugin.mechanics.security.botprotect.BotProtectionListener;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль защиты от ботов — очередь входа + кулдаун реджоина.
 * <p>
 * Регистрирует {@link BotProtectionListener} для обработки
 * {@link org.bukkit.event.player.AsyncPlayerPreLoginEvent}.
 */
public class BotProtectionModule extends PluginModule {

    private BotProtectionListener listener;

    public BotProtectionModule() {
        super("BotProtection", "mechanics/security/botprotect", false);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;
        this.listener = new BotProtectionListener(main);
        main.getServer().getPluginManager().registerEvents(listener, main);
        ConsoleLogger.info("[BotProtection] ✔ Anti-bot system initialized.");
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            this.listener = null;
        }
    }

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        if (listener != null) {
            listener.loadConfig();
        }
    }
}
