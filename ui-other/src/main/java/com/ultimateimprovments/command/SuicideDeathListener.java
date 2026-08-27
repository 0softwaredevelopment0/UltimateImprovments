package com.ultimateimprovments.command;

import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Overrides the death message for players who died from a suicide countdown
 * ({@link SuicideCommand}) with a custom message:
 * {@code <white><player> committed a suicide</white>}.
 */
public class SuicideDeathListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!SuicideCommand.consumeSuicideDeath(player.getUniqueId())) {
            return;
        }
        event.setDeathMessage(MessageUtil.legacy(
                "<white>" + player.getName() + " committed a suicide</white>"));
    }
}
