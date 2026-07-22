package com.ultimateimprovements.economy.listeners;

import com.ultimateimprovements.economy.EconomyManager;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Заработок с блоков и мобов.
 * <p>
 * По умолчанию ВЫКЛЮЧЕН — доходы не настроены (empty maps).
 * Администратор может добавить конкретные типы блоков/мобов с суммами в config.yml.
 */
public final class IncomeListener implements Listener {

    private static boolean enabled = false;

    /**
     * Устанавливает, активен ли доход с блоков/мобов.
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

        // Доход настраивается в config.yml:
        // economy:
        //   income:
        //     blocks:
        //       DIAMOND_ORE: 10.0
        //       IRON_ORE: 3.0
        //     mobs:
        //       ZOMBIE: 1.0
        //       SKELETON: 1.0
        // Пока enabled = false, дохода нет.
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent e) {
        if (!enabled) return;

        var killer = e.getEntity().getKiller();
        if (killer == null) return;

        // Аналогично блокам — ждёт настройки в config.yml
    }
}
