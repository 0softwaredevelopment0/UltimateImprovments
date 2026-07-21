package com.mcplugin.mechanics.protection;

import com.mcplugin.core.Keys;
import com.mcplugin.core.Main;
import com.mcplugin.util.ConsoleLogger;
import com.mcplugin.util.MessageUtil;

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
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Iterator;
import java.util.List;

/**
 * Слушатель событий «Блока защиты».
 * <p>
 * <ul>
 *   <li>{@link BlockBreakEvent}, {@link BlockPlaceEvent}, {@link PlayerInteractEvent}
 *       — проверяет, попадает ли действие в радиус активного блока,
 *       и отменяет событие для не-whitelisted игроков.</li>
 *   <li>{@link Shift+RMB по блоку} — открывает GUI для хозяина/whitelisted.</li>
 *   <li>{@link RMB по блоку с топливом} — добавляет очки и потребляет предмет.</li>
 *   <li>{@link EntityExplodeEvent},{@link BlockExplodeEvent} — убирает защищённые
 *       блоки из списка взрыва, списывает целостность защитного блока.</li>
 *   <li>Сохранение данных при ломании «Блока защиты» в {@link BlockBreakEvent#HIGHEST}.</li>
 *   <li>Spawn/despawn голограмм на chunk load/unload.</li>
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
        // Только RMB (и shift+RMB) — LMB игнорируем.
        // В Paper 1.21.x Action.RIGHT_CLICK удалён, нужно проверять обе варианта.
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        Player player = e.getPlayer();
        if (player == null) return;
        Block block = e.getClickedBlock();
        if (block == null) return;

        ProtectionBlock pb = manager.getBlockAt(block.getLocation());
        if (pb == null) return; // кликнули не по блоку защиты

        // Все клики по нашему блоку — прерываем стандартное поведение
        e.setCancelled(true);

        // Офф-блок: всё равно говорим, что он выключен
        if (!pb.isEnabled()) {
            // Whitelist/owner всё равно могут открыть GUI чтобы включить
            if (!pb.isWhitelisted(player.getUniqueId()) && !player.getUniqueId().equals(pb.getOwner())) {
                player.sendMessage(MessageUtil.parse(
                        ProtectionConfig.getMessage("not_whitelisted",
                                "<red>Этот Блок защиты вам не принадлежит!</red>")));
                return;
            }
        } else {
            // Если блок включён, но играка нет в whitelist — тыкать нельзя (даже RMB)
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
            // RMB — попытка топлива
            handleFuelClick(player, pb);
        }
    }

    /**
     * RMB с топливом: добавляет очки и потребляет стак.
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
            // Воспроизводим звук ошибки
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
            return;
        }
        pb.setPoints(pb.getPoints() + points);
        manager.saveBlockState(pb);
        // Потребляем стак
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
            // Ломают сам блок защиты: разрешаем только владельцу/whitelist (правило всегда активно)
            Player p = e.getPlayer();
            if (p != null && !pb.isWhitelisted(p.getUniqueId()) && !p.getUniqueId().equals(pb.getOwner())) {
                p.sendMessage(MessageUtil.parse(ProtectionConfig.getMessage(
                        "not_whitelisted",
                        "<red>Этот Блок защиты вам не принадлежит!</red>")));
                triggerIntruderEffects(pb);
                e.setCancelled(true);
                return;
            }
            // Сохраняем данные перед удалением (на всякий случай)
            manager.saveBlockState(pb);
            manager.saveWhitelistToDb(pb);
            // Дальше удаление сделает HIGHEST monitor или логика блок уберётся сама; очищаем кэш
            manager.unregisterBlock(pb.getId(), true);
            if (p != null) {
                p.sendMessage(MessageUtil.parse(ProtectionConfig.getMessage(
                        "broken", "<yellow>Блок защиты снят и удалён из БД.</yellow>")));
            }
            return;
        }

        // Защищаемый блок: только не-whitelisted
        ProtectionBlock protecting = manager.findProtectingBlock(broken.getLocation());
        if (protecting == null) return;

        if (e.getPlayer() != null && protecting.isWhitelisted(e.getPlayer().getUniqueId())) {
            return; // владелец/whitelist — пропускаем
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
    // BLOCK INTERACT (не по блоку защиты — чужие блоки внутри зоны).
    // Только RMB → разрешаем LMB идти свободно.
    // =========================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        Block block = e.getClickedBlock();
        if (block == null) return;
        ProtectionBlock pb = manager.getBlockAt(block.getLocation());
        if (pb != null) return; // наш блок, обрабатывается раньше
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
        // Собираем уникальные защитные блоки, чью зону задело
        java.util.Set<ProtectionBlock> affected = new java.util.HashSet<>();
        Iterator<Block> it = blockList.iterator();
        int totalRemoved = 0;
        while (it.hasNext()) {
            Block b = it.next();
            if (b == null || b.getWorld() == null || !b.getWorld().equals(world)) continue;
            ProtectionBlock protecting = manager.findProtectingBlock(b.getLocation());
            if (protecting == null) continue;
            // Не позволяем этому блоку взорваться
            it.remove();
            totalRemoved++;
            if (affected.add(protecting) && protecting.isAlive()) {
                double damage = ProtectionConfig.getIntegrityLossPerExplosionBlock();
                manager.applyIntegrityDamage(protecting, damage);
            }
        }
        if (totalRemoved > 0 && !affected.isEmpty()) {
            // Логируем одно сообщение
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
        // Дым от блока вверх
        block.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE,
                block.getLocation().add(0.5, 1.0, 0.5), 12, 0.3, 0.3, 0.3, 0.02);
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
        if (pb.isEnabled() && pb.isAlive()) {
            manager.applyIntegrityDamage(pb, ProtectionConfig.getIntegrityLossPerBreakAttempt());
        }
    }

    // =========================
    // CHUNK LOAD / UNLOAD — голограммы
    // =========================
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent e) {
        manager.onChunkLoad(e.getChunk().getX(), e.getChunk().getZ(), e.getWorld());
    }
}
