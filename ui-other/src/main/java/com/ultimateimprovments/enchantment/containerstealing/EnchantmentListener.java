package com.ultimateimprovments.enchantment.containerstealing;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.StringReader;

/**
 * Listener: Container Stealing block breaking and placing.
 * <p>
 * When a player breaks a container (chest, barrel, furnace, dispenser, dropper,
 * hopper, shulker box, brewing stand, ...) with a Container Stealing tool, the
 * vanilla behavior (drop the container + spill every item) is overridden: a SINGLE
 * container item is dropped that retains ALL of its contents (serialized into the
 * item's {@code ui:container_stealing_contents} PDC key). Placing that container
 * reads the PDC and restores the items into the newly placed block.
 * <p>
 * Enderechests are not {@link Container} block states, so they are never affected.
 */
public class EnchantmentListener implements Listener {

    /** YAML section inside the serialized string that holds {@code slot -> item}. */
    private static final String SLOTS_SECTION = "i";

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || tool.getType() == Material.AIR) return;

        if (Enchantment.getLevel(tool) <= 0) return;

        Block block = event.getBlock();
        if (!(block.getState() instanceof Container container)) return;

        Material blockType = block.getType();
        if (blockType == Material.AIR) return;

        // 1. Capture the container's current contents.
        String data = serialize(container.getInventory());

        // 2. Empty the REAL world container so nothing spills when it breaks.
        //    (block.getState() returns a snapshot — write the cleared state back.)
        container.getInventory().clear();
        container.update(true, true);

        // 3. Drop a single container item that carries the contents.
        ItemStack stored = new ItemStack(blockType);
        if (!data.isEmpty()) {
            ItemMeta meta = stored.getItemMeta();
            meta.getPersistentDataContainer().set(Enchantment.CONTENTS_KEY, PersistentDataType.STRING, data);
            stored.setItemMeta(meta);
        }

        event.setDropItems(false);
        World world = block.getWorld();
        Location loc = block.getLocation().add(0.5, 0.5, 0.5);
        world.dropItemNaturally(loc, stored);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return;

        String data = item.getItemMeta().getPersistentDataContainer()
                .get(Enchantment.CONTENTS_KEY, PersistentDataType.STRING);
        if (data == null) return;

        if (!(event.getBlockPlaced().getState() instanceof Container container)) return;

        restore(container, data);
    }

    // ─────────────────────────────────────────────────────────────
    //  SERIALIZATION
    // ─────────────────────────────────────────────────────────────

    /**
     * Serializes all non-empty slots of an inventory into a YAML string.
     *
     * @return a non-empty string when at least one slot held an item, {@code ""} otherwise
     */
    private static String serialize(Inventory inv) {
        YamlConfiguration conf = new YamlConfiguration();
        boolean any = false;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && !it.getType().isAir()) {
                conf.set(SLOTS_SECTION + "." + i, it);
                any = true;
            }
        }
        return any ? conf.saveToString() : "";
    }

    /**
     * Restores the serialized contents into the placed container block state,
     * then pushes the state into the world.
     */
    private static void restore(Container container, String data) {
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
                if (it != null && it.getType() != Material.AIR) {
                    inv.setItem(slot, it);
                }
            }
        } catch (Exception e) {
            // Never let a restore failure break block placement.
            return;
        }
        container.update(true, true);
    }
}