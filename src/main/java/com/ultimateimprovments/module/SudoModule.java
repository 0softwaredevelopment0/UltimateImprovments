package com.ultimateimprovments.module;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.mechanics.security.sudo.SudoCommandInterceptor;
import com.ultimateimprovments.mechanics.security.sudo.SudoManager;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль sudo-режима (GitHub-style).
 * Опасные команды требуют ввода sudo-пароля игроками с правом ui.sudo.
 */
public class SudoModule extends PluginModule {

    public SudoModule() {
        super("Sudo", "mechanics/security/sudo", false);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;
        if (!main.getConfig().getBoolean("features.sudo.enabled", true)) {
            ConsoleLogger.info("[SudoModule] Sudo mode is disabled in config (features.sudo.enabled: false).");
            return;
        }
        SudoManager.init();
        main.getServer().getPluginManager().registerEvents(new SudoCommandInterceptor(), main);
        main.getServer().getPluginManager().registerEvents(new SudoQuitListener(), main);
        ConsoleLogger.info("[SudoModule] ✔ Sudo mode initialized.");
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        // Nothing to clean up
    }

    /**
     * Очищает sudo-состояние игрока при выходе (сессии, кулдауны, pending-команды).
     */
    private static class SudoQuitListener implements Listener {
        @EventHandler
        public void onPlayerQuit(PlayerQuitEvent event) {
            SudoManager manager = SudoManager.getInstance();
            if (manager != null) {
                manager.removePlayer(event.getPlayer().getUniqueId());
            }
        }
    }
}
