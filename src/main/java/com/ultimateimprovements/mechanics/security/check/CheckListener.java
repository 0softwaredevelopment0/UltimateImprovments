package com.ultimateimprovements.mechanics.security.check;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;

/**
 * Слушатель для блокировки действий игрока, вызванного на проверку читов.
 * <p>
 * Полностью блокирует: движение, взаимодействие, ломание/ставку блоков,
 * команды (кроме /mp uncheck для досрочного завершения), выбрасывание предметов,
 * использование вёдер, нанесение урона, открытие инвентарей.
 * <p>
 * При выходе проверяющего — автоматически завершает проверку.
 * При выходе проверяемого — проверка ставится на паузу.
 * При реконнекте проверяемого — проверка восстанавливается автоматически.
 */
public class CheckListener implements Listener {

    // =========================
    // JOIN → resume check if paused
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        // Если игрок был на проверке до выхода — восстанавливаем
        if (CheckManager.isBeingChecked(player)) {
            CheckManager.rejoinCheck(player);
        }
    }

    // =========================
    // QUIT → auto-cleanup or pause
    // =========================
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();

        // Если проверяющий вышел — завершаем проверку для suspect
        if (CheckManager.isInspector(player)) {
            CheckManager.cleanupByInspector(player.getUniqueId());
            return;
        }

        // Если проверяемый вышел — ставим на паузу (данные сохраняются)
        if (CheckManager.isBeingChecked(player)) {
            CheckManager.cleanupBySuspect(player.getUniqueId());
        }
    }

    // =========================
    // BLOCK MOVEMENT
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent e) {
        Player player = e.getPlayer();
        if (!CheckManager.isBeingChecked(player)) return;
        if (e.getFrom().getBlockX() != e.getTo().getBlockX()
                || e.getFrom().getBlockY() != e.getTo().getBlockY()
                || e.getFrom().getBlockZ() != e.getTo().getBlockZ()) {
            e.setCancelled(true);
        }
    }

    // =========================
    // BLOCK INTERACT
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player player = e.getPlayer();
        if (!CheckManager.isBeingChecked(player)) return;
        e.setCancelled(true);
    }

    // =========================
    // BLOCK BREAK
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent e) {
        Player player = e.getPlayer();
        if (!CheckManager.isBeingChecked(player)) return;
        e.setCancelled(true);
    }

    // =========================
    // BLOCK PLACE
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent e) {
        Player player = e.getPlayer();
        if (!CheckManager.isBeingChecked(player)) return;
        e.setCancelled(true);
    }

    // =========================
    // BLOCK INVENTORY OPEN
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;
        if (!CheckManager.isBeingChecked(player)) return;
        e.setCancelled(true);
    }

    // =========================
    // BLOCK COMMANDS (кроме /mp uncheck)
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        Player player = e.getPlayer();
        if (!CheckManager.isBeingChecked(player)) return;

        String msg = e.getMessage().toLowerCase(Locale.ROOT).trim();

        // Проверяемый может использовать /mp uncheck чтобы попросить завершить проверку
        if (msg.startsWith("/mp uncheck") || msg.startsWith("/minecraft:mp uncheck")) {
            return;
        }

        e.setCancelled(true);
        player.sendMessage("§c❌ §fВы на проверке! Команды недоступны.");
    }

    // =========================
    // BLOCK ITEM DROP
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDropItem(PlayerDropItemEvent e) {
        Player player = e.getPlayer();
        if (!CheckManager.isBeingChecked(player)) return;
        e.setCancelled(true);
    }

    // =========================
    // BLOCK BUCKET USE
    // =========================
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent e) {
        Player player = e.getPlayer();
        if (!CheckManager.isBeingChecked(player)) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerBucketFill(PlayerBucketFillEvent e) {
        Player player = e.getPlayer();
        if (!CheckManager.isBeingChecked(player)) return;
        e.setCancelled(true);
    }

    // =========================
    // BLOCK DAMAGE TO ENTITIES
    // =========================
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player player)) return;
        if (!CheckManager.isBeingChecked(player)) return;
        e.setCancelled(true);
    }
}
