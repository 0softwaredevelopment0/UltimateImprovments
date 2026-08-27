package com.ultimateimprovments.mechanics.protection;

import com.ultimateimprovments.core.Keys;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.event.block.Action;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Iterator;
import java.util.List;

/**
 * Listener for «Protection Block» events.
 * <p>
 * <ul>
 *   <li>{@link BlockBreakEvent}, {@link BlockPlaceEvent}, {@link PlayerInteractEvent}
 *       — checks whether the action falls within an active block's radius,
 *       and cancels the event for non-whitelisted players.</li>
 *   <li>{@link Shift+RMB on a block} — opens the GUI for the owner/whitelisted.</li>
 *   <li>{@link RMB on a block with fuel} — adds points and consumes the item.</li>
 *   <li>{@link EntityExplodeEvent},{@link BlockExplodeEvent} — removes protected
 *       blocks from the explosion list, spends the protection block's integrity.</li>
 *   <li>Saves data when the «Protection Block» is broken at {@link BlockBreakEvent#HIGHEST}.</li>
 *   <li>Spawn/despawn of holograms on chunk load/unload.</li>
 * </ul>
 */
public class ProtectionListener implements Listener {

    private final ProtectionManager manager;

    public ProtectionListener(ProtectionManager manager) {
        this.manager = manager;
        Bukkit.getPluginManager().registerEvents(this, Main.getInstance());
    }

    // =========================
    // SHIFT+RMB and RMB on protection block
    // =========================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        // Only RMB (and shift+RMB) — LMB is ignored.
        // In Paper 1.21.x Action.RIGHT_CLICK was removed, both variants must be checked.
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        Player player = e.getPlayer();
        if (player == null) return;
        Block block = e.getClickedBlock();
        if (block == null) return;

        ProtectionBlock pb = manager.getBlockAt(block.getLocation());
        if (pb == null) return; // clicked a non-protection block

        // All clicks on our block — break the standard behavior
        e.setCancelled(true);

        // Off-block: still say it's disabled
        if (!pb.isEnabled()) {
            // Whitelist/owner can still open the GUI to enable it
            if (!pb.isWhitelisted(player.getUniqueId()) && !player.getUniqueId().equals(pb.getOwner())) {
                player.sendMessage(MessageUtil.parse(
                        ProtectionConfig.getMessage("not_whitelisted",
                                "<red>Этот Блок защиты вам не принадлежит!</red>")));
                return;
            }
        } else {
            // If the block is enabled but the player isn't in the whitelist — can't click (even RMB)
            if (!pb.isWhitelisted(player.getUniqueId()) && !player.getUniqueId().equals(pb.getOwner())) {
                player.sendMessage(MessageUtil.parse(
                        ProtectionConfig.getMessage("not_whitelisted",
                                "<red>Этот Блок защиты вам не принадлежит!</red>")));
                triggerIntruderEffects(pb);
                return;
            }
        }

        if (player.isSneaking()) {
            // SHIFT+RMB — open GUI
            ProtectionGUI.openMainMenu(player, pb);
        } else {
            // RMB — fuel attempt
            handleFuelClick(player, pb);
        }
    }

    /**
     * RMB with fuel: adds points and consumes the stack.
     */
    private void handleFuelClick(Player player, ProtectionBlock pb) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            player.sendMessage(MessageUtil.parse(
                    ProtectionConfig.getMessage("fuel_hint",
                            "<gray>Возьмите в руку топливо, чтобы получить очки.</gray>")));
            return;
        }
        int points = manager.computePointsFromFuel(hand.getType(), hand.getAmount());
        if (points <= 0) {
            player.sendMessage(MessageUtil.parse(ProtectionConfig.getMessage(
                    "fuel_not_burnable",
                    "<red>Этот предмет не переплавляется и не даёт очков!</red>")));
            // Play the error sound
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
            return;
        }
        pb.setPoints(pb.getPoints() + points);
        manager.saveBlockState(pb);
        // Consume the stack
        hand.setAmount(0);
        player.getInventory().setItemInMainHand(null);
        player.playSound(player.getLocation(), Sound.BLOCK_SMOKER_SMOKE, 0.6f, 1.2f);
        player.sendMessage(MessageUtil.parse(ProtectionConfig.getMessage(
                "fuel_acquired",
                "<green>✔</green> <white>Получено <gold>%points%</gold> очков.</white>")
                .replace("%points%", String.valueOf(points))));
    }

    // =========================
    // BLOCK BREAK for protection block itself
    // =========================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Block broken = e.getBlock();
        if (broken == null) return;
        ProtectionBlock pb = manager.getBlockAt(broken.getLocation());

        if (pb != null) {
            // Breaking the protection block itself: only the owner/whitelist may do it (always active)
            Player p = e.getPlayer();
            if (p != null && !pb.isWhitelisted(p.getUniqueId()) && !p.getUniqueId().equals(pb.getOwner())) {
                p.sendMessage(MessageUtil.parse(ProtectionConfig.getMessage(
                        "not_whitelisted",
                        "<red>Этот Блок защиты вам не принадлежит!</red>")));
                triggerIntruderEffects(pb);
                e.setCancelled(true);
                return;
            }
            // Previously this was saveBlockState + saveWhitelistToDb + an immediate unregisterBlock
            // doing a DELETE — three useless DB-writes per removed block.
            // Order: unregister first (DB deletion), then an optional confirm message.
            manager.unregisterBlock(pb.getId(), true);
            if (p != null) {
                p.sendMessage(MessageUtil.parse(ProtectionConfig.getMessage(
                        "broken", "<yellow>Блок защиты снят и удалён из БД.</yellow>")));
            }
            return;
        }

        // Protected block: only non-whitelisted
        ProtectionBlock protecting = manager.findProtectingBlock(broken.getLocation());
        if (protecting == null) return;

        if (e.getPlayer() != null && protecting.isWhitelisted(e.getPlayer().getUniqueId())) {
            return; // owner/whitelist — skip
        }
        e.setCancelled(true);
        if (e.getPlayer() != null) {
            e.getPlayer().sendMessage(MessageUtil.parse(ProtectionConfig.getMessage(
                    "cant_break",
                    "<red>Территория под защитой!</red> <gray>Этот блок принадлежит другому игроку.</gray>")));
        }
        triggerIntruderEffects(protecting);
    }

    // =========================
    // BLOCK PLACE
    // =========================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        ProtectionBlock protecting = manager.findProtectingBlock(e.getBlock().getLocation());
        if (protecting == null) return;
        if (protecting.isWhitelisted(e.getPlayer().getUniqueId())) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(MessageUtil.parse(ProtectionConfig.getMessage(
                "cant_place",
                "<red>Территория под защитой! Установка блоков запрещена.</red>")));
        triggerIntruderEffects(protecting);
    }

    // =========================
    // BLOCK INTERACT (not on a protection block — foreign blocks inside the zone).
    // Only RMB → let LMB go freely.
    // =========================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        Block block = e.getClickedBlock();
        if (block == null) return;
        ProtectionBlock pb = manager.getBlockAt(block.getLocation());
        if (pb != null) return; // our block, handled earlier
        ProtectionBlock protecting = manager.findProtectingBlock(block.getLocation());
        if (protecting == null) return;
        if (protecting.isWhitelisted(e.getPlayer().getUniqueId())) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(MessageUtil.parse(ProtectionConfig.getMessage(
                "cant_interact",
                "<red>Территория под защитой! Взаимодействие запрещено.</red>")));
        triggerIntruderEffects(protecting);
    }

    // =========================
    // ENTITY EXPLODE (TNT, creepers etc.)
    // =========================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        applyExplosionProtection(e.blockList(), e.getLocation().getWorld());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        // BlockExplodeEvent extends BlockEvent — use source block's world as reference.
        org.bukkit.World w = e.getBlock().getWorld();
        applyExplosionProtection(e.blockList(), w);
    }

    private void applyExplosionProtection(List<Block> blockList, org.bukkit.World world) {
        if (blockList == null || blockList.isEmpty()) return;
        // Collect unique protection blocks whose zone was hit
        java.util.Set<ProtectionBlock> affected = new java.util.HashSet<>();
        Iterator<Block> it = blockList.iterator();
        int totalRemoved = 0;
        while (it.hasNext()) {
            Block b = it.next();
            if (b == null || b.getWorld() == null || !b.getWorld().equals(world)) continue;
            ProtectionBlock protecting = manager.findProtectingBlock(b.getLocation());
            if (protecting == null) continue;
            // Don't let this block explode
            it.remove();
            totalRemoved++;
            if (affected.add(protecting) && protecting.isAlive()) {
                double damage = ProtectionConfig.getIntegrityLossPerExplosionBlock();
                manager.applyIntegrityDamage(protecting, damage);
            }
        }
        if (totalRemoved > 0 && !affected.isEmpty()) {
            // Log a single message
            ConsoleLogger.info("[ProtectionBlock] Explosion absorbed " + totalRemoved
                    + " blocks by " + affected.size() + " protection block(s).");
        }
    }

    // =========================
    // Intruder visual: smoke + integrity loss
    // =========================
    private void triggerIntruderEffects(ProtectionBlock pb) {
        if (pb == null) return;
        Block block = pb.getBlockLocation().getBlock();
        if (block == null || block.getWorld() == null) return;
        // Smoke from the block upward
        block.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE,
                block.getLocation().add(0.5, 1.0, 0.5), 12, 0.3, 0.3, 0.3, 0.02);
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
        if (pb.isEnabled() && pb.isAlive()) {
            manager.applyIntegrityDamage(pb, ProtectionConfig.getIntegrityLossPerBreakAttempt());
        }
    }

    // =========================
    // CHUNK LOAD / UNLOAD — holograms
    // =========================
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent e) {
        manager.onChunkLoad(e.getChunk().getX(), e.getChunk().getZ(), e.getWorld());
    }

    /**
     * World loaded — register all deferred protection blocks that were
     * waiting for this world at plugin startup (see ProtectionManager.pendingByWorld).
     * Without this, blocks in custom Multiverse worlds were silently lost.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent e) {
        manager.onWorldLoad(e.getWorld());
    }
}
