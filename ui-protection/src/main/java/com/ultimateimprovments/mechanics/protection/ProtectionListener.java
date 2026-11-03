package com.ultimateimprovments.mechanics.protection;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Listener for «Protection Block» events.
 * <p>
 * <b>What is cancelled</b> inside an active zone for non-whitelisted players:
 * <ul>
 *   <li>breaking / placing blocks,</li>
 *   <li>RMB on CONTAINER blocks only (chests, barrels, shulkers, furnaces, hoppers,
 *       dispensers, crafters...) — doors, buttons, levers, crafting tables and plain
 *       blocks stay usable,</li>
 *   <li>explosions (TNT, creepers, beds, respawn anchors) — the protection block
 *       itself is blast-proof and absorbs the hit as integrity damage,</li>
 *   <li>pistons moving blocks INTO or OUT of a zone (and moving the protection block),</li>
 *   <li>fire ignition / spread / burn, water &amp; lava flow, ice/snow melting,</li>
 *   <li>entity block changes (endermen, falling sand, ravagers, wither).</li>
 * </ul>
 * <p>
 * <b>Priority notes.</b> {@link #onBlockBreak} runs at {@link EventPriority#LOWEST}
 * so that area listeners (AoE / VeinMiner / TreeCapitator sit at LOW with
 * {@code ignoreCancelled = true}) never run for a protected block. Their own
 * per-block checks go through {@code CoreHooks.isBlockProtected}.
 */
public class ProtectionListener implements Listener {

    private final ProtectionManager manager;

    public ProtectionListener(ProtectionManager manager, JavaPlugin holder) {
        this.manager = manager;
        // Registered against the ADDON plugin (not the shared UI-Core instance) so
        // /ui reload's HandlerList.unregisterAll(addon) really removes this listener.
        // Before, every reload registered a fresh copy on top of the old one.
        JavaPlugin plugin = holder != null ? holder : Main.getInstance();
        Bukkit.getPluginManager().registerEvents(this, plugin);
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

        boolean trusted = pb.isWhitelisted(player.getUniqueId())
                || player.getUniqueId().equals(pb.getOwner());
        if (!trusted) {
            player.sendMessage(MessageUtil.parse(ProtectionConfig.getMessage(
                    "not_whitelisted", "<red>Этот Блок защиты вам не принадлежит!</red>")));
            triggerIntruderEffects(pb);
            return;
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
    // BLOCK BREAK — LOWEST priority so area-breakers (LOW, ignoreCancelled) skip us
    // =========================
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Block broken = e.getBlock();
        if (broken == null) return;
        ProtectionBlock pb = manager.getBlockAt(broken.getLocation());

        if (pb != null) {
            // Breaking the protection block itself: only the owner/whitelist may do it
            Player p = e.getPlayer();
            if (p != null && !pb.isWhitelisted(p.getUniqueId())
                    && !p.getUniqueId().equals(pb.getOwner())) {
                p.sendMessage(MessageUtil.parse(ProtectionConfig.getMessage(
                        "not_whitelisted",
                        "<red>Этот Блок защиты вам не принадлежит!</red>")));
                triggerIntruderEffects(pb);
                e.setCancelled(true);
                return;
            }
            // Unregister first (DB deletion), then an optional confirm message.
            manager.unregisterBlock(pb.getId(), true);
            // 🔁 Vanilla dropped a plain lodestone, so removing your own protection
            // block destroyed the item. Suppress the default drops and give the
            // placeable protection item back instead.
            e.setDropItems(false);
            if (broken.getWorld() != null) {
                broken.getWorld().dropItemNaturally(
                        broken.getLocation().add(0.5, 0.2, 0.5),
                        ProtectionItem.createProtectionItem(1));
            }
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
    // BLOCK INTERACT inside a zone — containers only
    // =========================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        Block block = e.getClickedBlock();
        if (block == null) return;
        if (manager.getBlockAt(block.getLocation()) != null) return; // our block, handled earlier
        // Only inventory-owning blocks are protected: previous behaviour cancelled
        // EVERY right-click (doors, buttons, levers, crafting tables, beds...),
        // which made protected areas unplayable instead of protected.
        if (!ProtectionConfig.isInteractionBlocked(block.getType())) return;

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
    // EXPLOSIONS (TNT, creepers, beds, respawn anchors)
    // =========================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        applyExplosionProtection(e.blockList(), e.getLocation().getWorld());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        // BlockExplodeEvent extends BlockEvent — use source block's world as reference.
        World w = e.getBlock().getWorld();
        applyExplosionProtection(e.blockList(), w);
    }

    /**
     * Removes protected blocks from the explosion list and charges integrity.
     * <p>
     * <b>Includes the protection block itself</b> — previously only blocks inside
     * its radius were filtered out ({@code findProtectingBlock} skips dx=dy=dz=0),
     * so TNT deleted the physical lodestone while the region stayed registered:
     * a zone without a block, cleaned up only after the next chunk load.
     */
    private void applyExplosionProtection(List<Block> blockList, World world) {
        if (blockList == null || blockList.isEmpty()) return;
        // protection block → how many of its blocks this explosion wanted to destroy
        Map<ProtectionBlock, Integer> hits = new HashMap<>();
        Set<ProtectionBlock> unique = new HashSet<>();
        Iterator<Block> it = blockList.iterator();
        int totalRemoved = 0;
        while (it.hasNext()) {
            Block b = it.next();
            if (b == null || b.getWorld() == null || !b.getWorld().equals(world)) continue;

            ProtectionBlock own = manager.getBlockAt(b.getLocation());
            if (own != null) {
                // Blast-proof: the protection block never disappears from an explosion,
                // the blast is absorbed as integrity damage instead.
                it.remove();
                totalRemoved++;
                unique.add(own);
                hits.merge(own, 1, Integer::sum);
                continue;
            }

            ProtectionBlock protecting = manager.findProtectingBlock(b.getLocation());
            if (protecting == null) continue;
            it.remove();
            totalRemoved++;
            unique.add(protecting);
            hits.merge(protecting, 1, Integer::sum);
        }
        if (hits.isEmpty()) return;
        double perBlock = ProtectionConfig.getIntegrityLossPerExplosionBlock();
        for (Map.Entry<ProtectionBlock, Integer> entry : hits.entrySet()) {
            ProtectionBlock pb = entry.getKey();
            if (!pb.isEnabled()) continue;
            int count = Math.max(1, entry.getValue());
            manager.applyIntegrityDamage(pb, perBlock * count);
        }
        if (totalRemoved > 0) {
            ConsoleLogger.info("[ProtectionBlock] Explosion absorbed " + totalRemoved
                    + " blocks by " + unique.size() + " protection block(s).");
        }
    }

    // =========================
    // PISTONS
    // =========================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (!ProtectionConfig.isPistonProtectionEnabled()) return;
        checkPistonMove(e.getBlock(), e.getBlocks(), e.getDirection(), false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (!ProtectionConfig.isPistonProtectionEnabled()) return;
        checkPistonMove(e.getBlock(), e.getBlocks(), e.getDirection(), true);
    }

    /**
     * Cancels a piston move that would cross a zone border (blocks pushed in, pulled
     * out) or move the protection block itself, and charges integrity per blocked move.
     * <p>
     * Moves that stay inside the SAME zone are allowed — the owner must be able to
     * automate their own base.
     *
     * @param face    the direction reported by the event (movement for extend, facing for retract)
     * @param retract true for retraction — the blocks move TOWARDS the piston instead
     */
    private void checkPistonMove(Block piston, List<Block> moved, BlockFace face, boolean retract) {
        if (piston == null || moved == null || moved.isEmpty()) return;
        Map<ProtectionBlock, Integer> charges = new HashMap<>();
        boolean cancel = false;

        for (Block source : moved) {
            if (source == null) continue;
            if (evaluatePistonBlock(piston, source, face, retract, charges)) cancel = true;
        }
        if (!cancel) return;

        for (Map.Entry<ProtectionBlock, Integer> entry : charges.entrySet()) {
            ProtectionBlock pb = entry.getKey();
            if (!pb.isEnabled()) continue;
            manager.applyIntegrityDamage(pb,
                    ProtectionConfig.getIntegrityLossPerBreakAttempt() * Math.max(1, entry.getValue()));
        }
    }

    /** @return true when this moved block must cancel the piston event. */
    private boolean evaluatePistonBlock(Block piston, Block source, BlockFace face, boolean retract,
                                        Map<ProtectionBlock, Integer> charges) {
        ProtectionBlock own = manager.getBlockAt(source.getLocation());
        if (own != null) {
            // The «Protection Block» itself must never be pushed/pulled — the region
            // would stay at the old coordinates and the hologram would drift away.
            charges.merge(own, 1, Integer::sum);
            return true;
        }

        // Extend: blocks move along the event direction. Retract: they move TOWARD
        // the piston, which is the opposite of the piston facing — derive the axis
        // from the coordinates so slime-block moves are handled too.
        BlockFace movement = retract ? towardPiston(piston, source) : face;
        if (movement == null) return false;

        Block destination = source.getRelative(movement);
        ProtectionBlock sourceZone = manager.findProtectingBlock(source.getLocation());
        ProtectionBlock destZone = manager.findProtectingBlock(destination.getLocation());
        if (sourceZone == destZone) {
            // Purely inside one zone (or entirely in the wild) — nothing to protect here.
            return false;
        }
        ProtectionBlock charged = destZone != null ? destZone : sourceZone;
        if (charged == null) return false;
        charges.merge(charged, 1, Integer::sum);
        return true;
    }

    /** Axis-aligned unit step from {@code source} towards {@code piston}. */
    private BlockFace towardPiston(Block piston, Block source) {
        int dx = piston.getX() - source.getX();
        int dy = piston.getY() - source.getY();
        int dz = piston.getZ() - source.getZ();
        if (dx == 0 && dy == 0 && dz == 0) return null;
        int ax = Math.abs(dx);
        int ay = Math.abs(dy);
        int az = Math.abs(dz);
        if (ax >= ay && ax >= az) return dx > 0 ? BlockFace.EAST : BlockFace.WEST;
        if (ay >= az) return dy > 0 ? BlockFace.UP : BlockFace.DOWN;
        return dz > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    // =========================
    // FIRE / LIQUIDS / ENTITY BLOCK CHANGES / MELTING
    // =========================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent e) {
        guard(e.getBlock(), e, false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent e) {
        guard(e.getBlock(), e, false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent e) {
        guard(e.getBlock(), e, false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent e) {
        guard(e.getBlock(), e, false);
    }

    /** Water / lava flowing into a zone (and onto the protection block). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent e) {
        guard(e.getToBlock(), e, false);
    }

    /** Endermen, falling sand, ravagers, wither roses eating blocks inside a zone. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        boolean cancelled = guard(e.getBlock(), e, true);
        // A cancelled falling block would hover forever and retry every tick —
        // remove it and leave its item behind instead (no lag machine).
        if (cancelled && e.getEntity() instanceof FallingBlock falling) {
            e.setCancelled(true);
            Material type = falling.getBlockData().getMaterial();
            falling.remove();
            if (type != null && type.isItem()) {
                falling.getWorld().dropItemNaturally(
                        falling.getLocation(), new ItemStack(type));
            }
        }
    }

    /**
     * Shared guard: cancels the event (and optionally charges integrity) when the
     * block is protected. Natural processes (fire spread, liquid flow, melting) are
     * cancelled WITHOUT charging — they fire many times per tick and would drain the
     * block in seconds, while a player-driven action is charged once.
     *
     * @return true when the event was cancelled
     */
    private boolean guard(Block block, org.bukkit.event.Cancellable event, boolean charge) {
        if (block == null || block.getWorld() == null) return false;
        ProtectionBlock own = manager.getBlockAt(block.getLocation());
        if (own != null) {
            event.setCancelled(true);
            if (charge) triggerIntruderEffects(own);
            return true;
        }
        ProtectionBlock protecting = manager.findProtectingBlock(block.getLocation());
        if (protecting == null) return false;
        event.setCancelled(true);
        if (charge) triggerIntruderEffects(protecting);
        return true;
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
    // CHUNK LOAD / WORLD LOAD — holograms
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
