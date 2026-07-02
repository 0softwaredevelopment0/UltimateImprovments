package com.mcplugin.mechanics.security.anticheat.misc;

import com.mcplugin.mechanics.security.anticheat.AntiCheatManager;
import com.mcplugin.mechanics.security.anticheat.core.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * ExtraInventory — доступ к слотам инвентаря вне нормального диапазона.
 * Детекция: клик по слоту с индексом вне стандартных 36 слотов в survival.
 */
public class ExtraInventoryCheck extends AbstractCheck {

    private int maxSurvivalSlot;

    public ExtraInventoryCheck() {
        super("ExtraInventory", CheckCategory.MISC);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxSurvivalSlot = getConfigInt("max_survival_slot", 40);
    }

    @Override
    public void onReload() {
        loadConfig();
        maxSurvivalSlot = getConfigInt("max_survival_slot", 40);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!isEnabled() || isExempted(player)) return;

        // In survival, player inventory has 36 main slots + 4 armor + 1 offhand = 41
        // Clicking beyond that indicates a hack
        int slot = e.getSlot();
        int rawSlot = e.getRawSlot();

        if (slot < -1 || rawSlot > maxSurvivalSlot + 5) {
            CheckResult result = flag(player, 3.0,
                    "ExtraInventory: rawSlot=" + rawSlot + " slot=" + slot + " (max: " + (maxSurvivalSlot + 5) + ")");
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }
    }
}
