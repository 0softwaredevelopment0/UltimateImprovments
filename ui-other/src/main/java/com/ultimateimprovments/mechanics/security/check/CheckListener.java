package com.ultimateimprovments.mechanics.security.check;

import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Listener that blocks the actions of a player summoned for an anti-cheat check.
 * <p>
 * Fully blocks every escape vector: movement (walking and riding), interaction,
 * block breaking/placing, ALL commands (the suspect can no longer run even
 * {@code /ui uncheck} to free themselves), item drops, bucket use, dealing
 * damage, opening inventories, eating, fishing, sleeping, mounting entities,
 * and any teleport/portal.
 * <p>
 * When the inspector leaves — automatically finishes the check.
 * When the suspect leaves — the check is paused.
 * When the suspect reconnects — the check resumes automatically.
 */
public class CheckListener implements Listener {

    private String commandsBlocked() {
        return MessagesManager.getString("check.commands_blocked",
                "<red>❌ <white>You are under check! Commands are disabled.</white>");
    }

    // =========================
    // JOIN → resume check if paused
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        // If the player was under check before leaving — restore it
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

        // If the inspector left — finish the check for the suspect
        if (CheckManager.isInspector(player)) {
            CheckManager.cleanupByInspector(player.getUniqueId());
            return;
        }

        // If the suspect left — pause the check (data is kept)
        if (CheckManager.isBeingChecked(player)) {
            CheckManager.cleanupBySuspect(player.getUniqueId());
        }
    }

    // =========================
    // BLOCK MOVEMENT (walking & riding)
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent e) {
        Player player = e.getPlayer();
        if (!CheckManager.isBeingChecked(player)) return;
        // Block any block-position change AND disallow riding as an escape
        if (e.getFrom().getBlockX() != e.getTo().getBlockX()
                || e.getFrom().getBlockY() != e.getTo().getBlockY()
                || e.getFrom().getBlockZ() != e.getTo().getBlockZ()) {
            e.setCancelled(true);
        }
        if (player.getVehicle() != null) { // riding a vehicle/mob — force dismount, no escape
            player.leaveVehicle();
            e.setCancelled(true);
        }
    }

    // =========================
    // BLOCK MOUNTING — can't escape by getting on a horse/boat/minecart
    // =========================
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityMount(EntityMountEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        if (!CheckManager.isBeingChecked(player)) return;
        e.setCancelled(true);
        e.getEntity().leaveVehicle();
    }

    // =========================
    // BLOCK INTERACT — items, blocks
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player player = e.getPlayer();
        if (!CheckManager.isBeingChecked(player)) return;
        e.setCancelled(true);
    }

    // =========================
    // BLOCK INTERACT ENTITY — can't mount/interact with animals or NPCs
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent e) {
        Player player = e.getPlayer();
        if (!CheckManager.isBeingChecked(player)) return;
        e.setCancelled(true);
    }

    // =========================
    // BLOCK BREAK / PLACE
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent e) {
        Player player = e.getPlayer();
        if (!CheckManager.isBeingChecked(player)) return;
        e.setCancelled(true);
    }

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
    // BLOCK ALL COMMANDS — the suspect can no longer free themselves with /ui uncheck
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        Player player = e.getPlayer();
        if (!CheckManager.isBeingChecked(player)) return;
        e.setCancelled(true);
        player.sendMessage(MessageUtil.parse(commandsBlocked()));
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
    // BLOCK EATING / FISHING / SLEEPING — no way to pass time or use consumables
    // =========================
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerItemConsume(PlayerItemConsumeEvent e) {
        Player player = e.getPlayer();
        if (!CheckManager.isBeingChecked(player)) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent e) {
        Player player = e.getPlayer();
        if (!CheckManager.isBeingChecked(player)) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerBedEnter(PlayerBedEnterEvent e) {
        Player player = e.getPlayer();
        if (!CheckManager.isBeingChecked(player)) return;
        e.setCancelled(true);
    }

    // =========================
    // BLOCK TELEPORTS / PORTALS — no escape via plugins or portals
    // =========================
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent e) {
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