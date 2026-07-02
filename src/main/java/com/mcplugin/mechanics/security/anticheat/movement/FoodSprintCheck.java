package com.mcplugin.mechanics.security.anticheat.movement;

import com.mcplugin.mechanics.security.anticheat.AntiCheatManager;
import com.mcplugin.mechanics.security.anticheat.core.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerItemConsumeEvent;

/**
 * FoodSprint — спринт во время поедания еды (невозможно в ванилле).
 * Детекция: игрок спринтит в момент поедания.
 */
public class FoodSprintCheck extends AbstractCheck {

    public FoodSprintCheck() {
        super("FoodSprint", CheckCategory.MOVEMENT);
    }

    @Override
    public void onInit() {
        loadConfig();
    }

    @Override
    public void onReload() {
        loadConfig();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;

        if (player.isSprinting()) {
            CheckResult result = flag(player, 2.0,
                    "FoodSprint: sprinting while eating");
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }
    }
}
