package com.ultimateimprovments.economy.listeners;

import com.ultimateimprovments.economy.EconomyManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Выдаёт дефолтный баланс игроку при первом входе на сервер.
 */
public final class PlayerJoinListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent e) {
        EconomyManager.getInstance().ensureDefaultBalance(e.getPlayer().getUniqueId());
    }
}
