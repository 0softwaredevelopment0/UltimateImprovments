package com.mcplugin.mechanics.features.world;

import com.mcplugin.infrastructure.commands.subcommands.ExpSplitSubcommand;
import com.mcplugin.infrastructure.structure.StructureChunkTracker;
import com.mcplugin.infrastructure.structure.StructureMarker;
import com.mcplugin.infrastructure.util.ConsoleLogger;
import com.mcplugin.infrastructure.util.LocationUtil;
import com.mcplugin.infrastructure.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Слушатель для:
 * <ul>
 *   <li>Использования XP-бутылки (/mp expsplit) — ПКМ забирает опыт</li>
 *   <li>Установки чанклоадера (изумрудный блок с PDC) — создаёт StructureMarker</li>
 *   <li>Ломания чанклоадера — удаляет StructureMarker</li>
 * </ul>
 */
public class ChunkLoaderItemListener implements Listener {

    private static final NamespacedKey CHUNK_LOADER_KEY = new NamespacedKey("mcplugin", "is_chunk_loader");

    // =========================
    // XP BOTTLE USE
    // =========================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player player = e.getPlayer();
        ItemStack item = e.getItem();

        // Only right-click air/block
        if (e.getAction() != Action.RIGHT_CLICK_AIR
                && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (ExpSplitSubcommand.useBottle(player, item)) {
            e.setCancelled(true);
        }
    }

    // =========================
    // CHUNK LOADER PLACE
    // =========================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        Player player = e.getPlayer();
        ItemStack item = e.getItemInHand();

        if (e.getBlock().getType() != Material.EMERALD_BLOCK) return;

        // Check if the placed item is a chunk loader
        if (!isChunkLoaderItem(item)) return;

        Location loc = LocationUtil.normalize(e.getBlock().getLocation());
        if (loc == null || loc.getWorld() == null) return;

        // Don't place if there's already a structure here
        if (StructureMarker.existsAt(loc)) {
            player.sendMessage(MessageUtil.parse("<red>❌ Здесь уже есть структура!</red>"));
            e.setCancelled(true);
            return;
        }

        // Create StructureMarker with type "chunkloader"
        UUID structureId = UUID.randomUUID();
        StructureMarker.place(loc, "chunkloader", structureId);

        player.sendMessage(MessageUtil.parse(
                "<green>✔</green> <white>Чанклоадер установлен! Чанк</white> <gold>("
                        + (loc.getBlockX() >> 4) + ", " + (loc.getBlockZ() >> 4)
                        + ")</gold> <white>теперь всегда прогружен.</white>"));

        ConsoleLogger.info("[ChunkLoader] Placed at " + loc.getWorld().getName()
                + " " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()
                + " by " + player.getName());
    }

    // =========================
    // CHUNK LOADER BREAK
    // =========================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Location loc = LocationUtil.normalize(e.getBlock().getLocation());
        if (loc == null || loc.getWorld() == null) return;

        // Check if this position has a chunkloader structure
        StructureMarker.StructureData data = StructureMarker.getAt(loc);
        if (data == null || !"chunkloader".equals(data.type())) return;

        Player breaker = e.getPlayer();

        // Remove the marker (this also rebuilds StructureChunkTracker)
        StructureMarker.removeAt(loc);

        // Remove plugin chunk ticket for this chunk
        World world = loc.getWorld();
        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;
        world.removePluginChunkTicket(cx, cz, com.mcplugin.infrastructure.core.Main.getInstance());

        e.setDropItems(false);

        // Drop the chunk loader item
        ItemStack drop = createChunkLoaderItem();
        world.dropItemNaturally(loc, drop);

        breaker.sendMessage(MessageUtil.parse(
                "<yellow>⚠</yellow> <white>Чанклоадер разрушен! Чанк</white> <gold>(" + cx + ", " + cz
                        + ")</gold> <white>больше не прогружен принудительно.</white>"));

        ConsoleLogger.info("[ChunkLoader] Broken at " + loc.getWorld().getName()
                + " " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()
                + " by " + breaker.getName());
    }

    // =========================
    // UTILITY
    // =========================

    /**
     * Проверяет, является ли предмет чанклоадером (изумрудный блок с PDC).
     */
    public static boolean isChunkLoaderItem(ItemStack item) {
        if (item == null || item.getType() != Material.EMERALD_BLOCK) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(CHUNK_LOADER_KEY, PersistentDataType.BYTE);
    }

    /**
     * Создаёт предмет чанклоадера.
     */
    public static ItemStack createChunkLoaderItem() {
        ItemStack item = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(MessageUtil.parse("<i:false><gold>✦ Чанклоадер ✦</gold>"));
        meta.lore(java.util.List.of(
                MessageUtil.parse("<i:false><gray>При установке чанк остается загруженным</gray>"),
                MessageUtil.parse("<i:false><gray>Разрушить — получить предмет обратно</gray>")
        ));
        meta.getPersistentDataContainer().set(CHUNK_LOADER_KEY, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);

        return item;
    }

    public static NamespacedKey getChunkLoaderKey() {
        return CHUNK_LOADER_KEY;
    }
}
