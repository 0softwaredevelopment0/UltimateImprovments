package com.ultimateimprovments.mechanics.security.anticheat.combat;

import com.ultimateimprovments.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovments.mechanics.security.anticheat.core.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * AutoClicker — автоматический кликер.
 * Детекция: CPS выше лимита, аномально ровные интервалы между кликами.
 */
public class AutoClickerCheck extends AbstractCheck {

    private int maxCps;
    private double maxClickConsistency;

    public AutoClickerCheck() {
        super("AutoClicker", CheckCategory.COMBAT);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxCps = getConfigInt("max_cps", 20);
        maxClickConsistency = getConfigDouble("max_click_consistency", 0.95);
    }

    @Override
    public void onReload() { loadConfig(); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.LEFT_CLICK_AIR && e.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;

        PlayerData data = AntiCheatManager.getInstance().getOrCreatePlayerData(player);
        data.registerClick();

        int cps = data.getCps();
        if (cps > maxCps) {
            double vl = Math.min(5.0, (cps - maxCps) * 0.5);
            CheckResult result = flag(player, vl,
                    "CPS: " + cps + " (max: " + maxCps + ")");
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }
    }
}
