package com.ultimateimprovments.enchantment.containerstealing;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.block.ShulkerBox;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.persistence.PersistentDataType;

import java.io.StringReader;

/**
 * Listener: Container Stealing block breaking.
 * <p>
 * When a player breaks a container (chest, barrel, furnace, dispenser, dropper,
 * hopper, shulker box, brewing stand, ...) with a Container Stealing tool, the
 * vanilla behavior (drop the container + spill every item) is overridden: a SINGLE
 * container item is dropped that retains ALL of its contents. The contents live in
 * the item's vanilla block-state NBT ({@link BlockStateMeta} holding a snapshot of
 * the broken block) — the same format a vanilla "chest with items" obtained via
 * ctrl+pick-block uses. No plugin-side serialization is involved, so the contents
 * survive server restarts and datapack outages, and the item tooltip preview shows
 * the stored items. Placing the container restores the items natively (vanilla
 * applies the item's BlockEntityTag to the placed block).
 * <p>
 * Enderechests are not {@link Container} block states, so they are never affected.
 * Shulker boxes are deliberately EXCLUDED too: they already keep their contents
 * when broken by hand, and stealing them would double-preserve the loot.
 * <p>
 * <b>Steal roll:</b> the steal is a CHANCE — level N = N×10% (level 1 → 10%,
 * level 10 → 100%). On a failed roll the break behaves VANILLA: the container
 * drops empty and all its contents spill out. No contents are ever destroyed
 * by this charm.
 */
public class EnchantmentListener implements Listener {

    /**
     * MONITOR priority (not NORMAL): the contents are cleared and the vanilla
     * drops suppressed only after every protection plugin has had its say. At
     * MONITOR we run last, so a cancelled break never loses items (the handler
     * is {@code ignoreCancelled}) and the snapshot below only happens when the
     * break decision is final.
     * <p>
     * The snapshot + wipe run INLINE, not on the next tick: by the next tick
     * the broken block is already AIR, so a deferred task could never capture
     * the container state. This is the same reason
     * {@code BlockBreakListener#scheduleStoneReplacement} checks for AIR on the
     * next tick.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || tool.getType() == Material.AIR) return;

        int level = Enchantment.getLevel(tool);
        if (level <= 0) return;

        Block block = event.getBlock();
        Material blockType = block.getType();
        if (blockType == Material.AIR || !blockType.isItem()) return;

        BlockState state = block.getState();
        if (!(state instanceof Container)) return;

        // Shulker boxes are excluded: vanilla already keeps their contents on
        // break, and a steal would double-preserve the loot.
        if (state instanceof ShulkerBox) return;

        // Steal roll: level N = N×10% chance the charm works. A failed roll →
        // vanilla behavior (empty container drop + spilled contents), nothing
        // is ever destroyed.
        if (java.util.concurrent.ThreadLocalRandom.current().nextInt(100) >= level * 10) return;

        // Break decision is final at MONITOR (ignoreCancelled + last priority):
        // snapshot and empty the container NOW. A next-tick task would be too
        // late — the block is already AIR by then and the guard would always
        // bail, silently destroying the contents.
        event.setDropItems(false);

        // 1. Snapshot the whole block (contents included) into vanilla item NBT.
        ItemStack stored = new ItemStack(blockType);
        stored.editMeta(BlockStateMeta.class, meta -> {
        meta.setBlockState(state);
        });

        // 2. Empty the REAL world container so nothing spills when it breaks
        //    (the snapshot above already carries the items).
        Container live = (Container) block.getState();
        live.getInventory().clear();
        live.update(true, true);

        // 3. Drop our single container item (the block itself breaks right after,
        //    vanilla suppresses the (now empty) container drop via setDropItems(false)).
        World world = block.getWorld();
        Location loc = block.getLocation().add(0.5, 0.5, 0.5);
        world.dropItemNaturally(loc, stored);
    }

    /**
     * Legacy migration: containers stolen by the OLD implementation carried their
     * contents in the {@code ui:container_stealing_contents} PDC key (a YAML blob)
     * instead of vanilla block-state NBT. When such an item is placed, restore its
     * contents from the PDC blob and drop the key — so pre-fix stolen containers
     * don't lose their items.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return;

        String data = item.getItemMeta().getPersistentDataContainer()
                .get(Enchantment.CONTENTS_KEY, PersistentDataType.STRING);
        if (data == null) return;

        if (!(event.getBlockPlaced().getState() instanceof Container container)) return;

        restoreLegacy(container, data);
    }

    // ─────────────────────────────────────────────────────────────
    //  LEGACY SERIALIZATION
    // ─────────────────────────────────────────────────────────────

    /** YAML section inside the legacy serialized string that holds {@code slot -> item}. */
    private static final String SLOTS_SECTION = "i";

    /**
     * Restores the legacy serialized contents into the placed container block state,
     * then pushes the state into the world. Failures never break block placement.
     */
    private static void restoreLegacy(Container container, String data) {
        Inventory inv = container.getInventory();
        try {
            YamlConfiguration conf = YamlConfiguration.loadConfiguration(new StringReader(data));
            ConfigurationSection slots = conf.getConfigurationSection(SLOTS_SECTION);
            if (slots == null) return;

            for (String key : slots.getKeys(false)) {
                final int slot;
                try {
                    slot = Integer.parseInt(key);
                } catch (NumberFormatException e) {
                    continue;
                }
                if (slot < 0 || slot >= inv.getSize()) continue;

                ItemStack it = slots.getItemStack(key);
                if (it != null && !it.getType().isAir()) {
                    inv.setItem(slot, it);
                }
            }
        } catch (Exception e) {
            return;
        }
        container.update(true, true);
    }
}
