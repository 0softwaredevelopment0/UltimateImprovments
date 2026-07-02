package com.mcplugin.mechanics.security.anticheat;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener для управления жизненным циклом PlayerData.
 */
public class AntiCheatListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        AntiCheatManager.getInstance().getOrCreatePlayerData(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        AntiCheatManager.getInstance().removePlayerData(e.getPlayer().getUniqueId());
    }
}
