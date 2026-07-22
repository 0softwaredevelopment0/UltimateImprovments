package com.ultimateimprovements.mechanics.security.anticheat.misc;

import com.ultimateimprovements.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovements.mechanics.security.anticheat.core.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NoSwing / NoAnimation — атаки/ломание без анимации взмаха руки.
 * Детекция: игрок ломает блок или атакует entity, но не отправил swing packet.
 */
public class NoSwingCheck extends AbstractCheck {

    // Tracks last swing time per player
    private final ConcurrentHashMap<UUID, Long> lastSwingTimes = new ConcurrentHashMap<>();
    private long maxSwingDelayMs;

    public NoSwingCheck() {
        super("NoSwing", CheckCategory.MISC);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxSwingDelayMs = getConfigInt("max_swing_delay_ms", 500);
    }

    @Override
    public void onReload() {
        loadConfig();
        maxSwingDelayMs = getConfigInt("max_swing_delay_ms", 500);
    }

    // Track swings via animation event
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwing(org.bukkit.event.player.PlayerAnimationEvent e) {
        if (e.getAnimationType() != org.bukkit.event.player.PlayerAnimationType.ARM_SWING) return;
        lastSwingTimes.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player player)) return;
        if (!isEnabled() || isExempted(player)) return;
        checkSwing(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;
        checkSwing(player);
    }

    private void checkSwing(Player player) {
        Long lastSwing = lastSwingTimes.get(player.getUniqueId());
        long now = System.currentTimeMillis();

        if (lastSwing == null || now - lastSwing > maxSwingDelayMs) {
            CheckResult result = flag(player, 1.5,
                    "NoSwing: no arm swing before action"
                            + (lastSwing != null ? " (last swing " + (now - lastSwing) + "ms ago)" : " (never swung)"));
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }
    }
}
