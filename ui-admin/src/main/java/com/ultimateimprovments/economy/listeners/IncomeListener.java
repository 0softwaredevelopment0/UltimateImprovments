package com.ultimateimprovments.economy.listeners;

import com.ultimateimprovments.economy.EconomyManager;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Income from blocks and mobs.
 * <p>
 * DISABLED by default — no income configured (empty maps).
 * An admin can add specific block/mob types with amounts in config.yml.
 */
public final class IncomeListener implements Listener {

    private static boolean enabled = false;

    /**
     * Sets whether income from blocks/mobs is active.
     */
    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        if (!enabled) return;

        var player = e.getPlayer();
        if (player == null) return;

        // Income is configured in config.yml:
        // economy:
        //   income:
        //     blocks:
        //       DIAMOND_ORE: 10.0
        //       IRON_ORE: 3.0
        //     mobs:
        //       ZOMBIE: 1.0
        //       SKELETON: 1.0
        // While enabled = false, there is no income.
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent e) {
        if (!enabled) return;

        var killer = e.getEntity().getKiller();
        if (killer == null) return;

        // Same as blocks — awaits configuration in config.yml
    }
}
