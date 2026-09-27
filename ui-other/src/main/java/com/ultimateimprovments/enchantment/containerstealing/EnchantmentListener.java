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
 */
public class EnchantmentListener implements Listener {

    /**
     * MONITOR priority (not NORMAL): the contents are cleared and the vanilla
     * drops suppressed BEFORE other plugins run — otherwise a protection
     * plugin could cancel the break AFTER we emptied the container, and the
     * cleared items would be gone for good. At MONITOR we run last; if a
     * plugin cancels the event, BlockBreakEvent#setDropItems is moot and our
     * snapshot is simply discarded (the container keeps its items — but the
     * wipe below must only happen when the break is final).
     * <p>
     * Safety model: the wipe + snapshot happen on the NEXT tick, and only if
     * the event was not cancelled meanwhile — protection plugins run at HIGH
     * or HIGHEST, i.e. BEFORE the MONITOR handler. So by the time we act, the
     * break decision is final and the wipe can never lose items.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || tool.getType() == Material.AIR) return;

        if (Enchantment.getLevel(tool) <= 0) return;

        Block block = event.getBlock();
        Material blockType = block.getType();
        if (blockType == Material.AIR || !blockType.isItem()) return;

        // Capture the state NOW (the container still holds its items), but act
        // only next tick, when no other plugin can cancel the break anymore.
        BlockState state = block.getState();
        if (!(state instanceof Container)) return;

        event.setDropItems(false);
        com.ultimateimprovments.core.Main.getInstance().getServer().getScheduler().runTask(
                com.ultimateimprovments.core.Main.getInstance(), () -> {
                    if (block.getType() != blockType) return; // replaced meanwhile

                    BlockState currentState = block.getState();
                    if (!(currentState instanceof Container currentContainer)) return;

                    // 1. Snapshot the whole block (contents included) into vanilla item NBT.
                    ItemStack stored = new ItemStack(blockType);
                    BlockStateMeta meta = (BlockStateMeta) stored.getItemMeta();
                    meta.setBlockState(currentState);
                    stored.setItemMeta(meta);

                    // 2. Empty the REAL world container so nothing spills when it breaks
                    //    (the snapshot above already carries the items).
                    currentContainer.getInventory().clear();
                    currentContainer.update(true, true);

                    // 3. Drop our single container item (the block itself breaks right after,
                    //    vanilla suppresses the (now empty) container drop via setDropItems(false)).
                    World world = block.getWorld();
                    Location loc = block.getLocation().add(0.5, 0.5, 0.5);
                    world.dropItemNaturally(loc, stored);
                });
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
