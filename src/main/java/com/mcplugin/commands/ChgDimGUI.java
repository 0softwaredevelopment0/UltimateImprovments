package com.mcplugin.commands;

import com.mcplugin.Keys;
import com.mcplugin.Main;
import com.mcplugin.config.MessagesManager;
import com.mcplugin.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Anvil GUI for world selection on /mp chgdim.
 */
public class ChgDimGUI implements Listener {

    private static final Map<UUID, String> openMenus = new ConcurrentHashMap<>();
    private static final Map<UUID, BukkitTask> resetTasks = new ConcurrentHashMap<>();
    private static boolean registered = false;

    private static final Pattern VALID_WORLD_NAME = Pattern.compile("[^a-zA-Z0-9_-]");

    private static final ItemStack CONFIRM_ITEM;
    static {
        CONFIRM_ITEM = createConfirmItem();
    }

    public static void open(Player player) {
        register();

        FileConfiguration config = Main.getInstance().getConfig();
        ConfigurationSection worldsSection = config.getConfigurationSection("changedimmension.worlds");

        if (worldsSection == null || worldsSection.getKeys(false).isEmpty()) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.worlds_not_configured", "<red>❌ Worlds not configured in config!</red>")));
            return;
        }

        InventoryView view = MenuType.ANVIL.builder()
                .title(MessageUtil.parse(MessagesManager.getString("changedimmension.gui.title", "<dark_gray>✦ Change dimension</dark_gray>")))
                .build(player);

        view.open();

        Inventory topInv = view.getTopInventory();

        ItemStack infoItem = createInfoItem(worldsSection);
        topInv.setItem(0, infoItem);

        topInv.setItem(1, createReturnItem());
        topInv.setItem(2, CONFIRM_ITEM);

        startResetTask(player);

        openMenus.put(player.getUniqueId(), "chgdim");

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.4f, 1.0f);
        player.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.enter_world_name_hint", "<yellow>✦</yellow> <gray>Enter world name in the field, then click</gray> <green>✔</green>")));
    }

    private static ItemStack tagChgdimItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(Keys.CHGDIM_GUI, PersistentDataType.BOOLEAN, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createInfoItem(ConfigurationSection worldsSection) {
        ItemStack infoItem = new ItemStack(Material.PAPER);
        ItemMeta meta = infoItem.getItemMeta();
        meta.displayName(Component.text("").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(MessageUtil.parse("<dark_gray>┌────────────────────────────┐</dark_gray>")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));

        Set<String> worldNames = worldsSection.getKeys(false);
        int count = 0;

        for (String worldName : worldNames) {
            count++;
            ConfigurationSection wc = worldsSection.getConfigurationSection(worldName);
            double x = wc != null ? wc.getDouble("x", 0) : 0;
            double y = wc != null ? wc.getDouble("y", 64) : 64;
            double z = wc != null ? wc.getDouble("z", 0) : 0;
            lore.add(MessageUtil.parse("<dark_gray>│</dark_gray> <yellow>" + worldName + "</yellow>")
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            lore.add(MessageUtil.parse("<dark_gray>│</dark_gray> <dark_gray>»</dark_gray> <gray>" + Math.round(x) + " " + Math.round(y) + " " + Math.round(z) + "</gray>")
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        }

        lore.add(MessageUtil.parse("<dark_gray>│</dark_gray>")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        lore.add(MessageUtil.parse("<dark_gray>│</dark_gray> <gray>Total worlds: </gray><white>" + count + "</white>")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        lore.add(MessageUtil.parse("<dark_gray>└────────────────────────────┘</dark_gray>")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        lore.add(Component.text("").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        lore.add(MessageUtil.parse("<yellow>💡 Enter the world name in the field</yellow>")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        lore.add(MessageUtil.parse("<yellow>and click on the item that appears</yellow>")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));

        meta.lore(lore);
        infoItem.setItemMeta(meta);
        return tagChgdimItem(infoItem);
    }

    private static ItemStack createConfirmItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse("<green><bold>✔ Teleport</bold></green>")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(List.of(
                MessageUtil.parse("<gray>Click to confirm teleportation</gray>")
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
        ));
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return tagChgdimItem(item);
    }

    private static void startResetTask(Player player) {
        UUID uuid = player.getUniqueId();
        cancelResetTask(uuid);

        BukkitTask task = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    resetTasks.remove(uuid);
                    return;
                }
                try {
                    var openInv = player.getOpenInventory();
                    if (!openMenus.containsKey(uuid)) {
                        cancel();
                        resetTasks.remove(uuid);
                        return;
                    }
                    var topInv = openInv.getTopInventory();
                    ItemStack cursor = player.getItemOnCursor();
                    if (cursor != null && cursor.getItemMeta() != null &&
                            cursor.getItemMeta().getPersistentDataContainer()
                                    .has(Keys.CHGDIM_GUI, PersistentDataType.BOOLEAN)) {
                        player.setItemOnCursor(null);
                    }
                    topInv.setItem(1, createReturnItem());
                    topInv.setItem(2, CONFIRM_ITEM);
                    removeChgdimItemsFromPlayer(player);
                } catch (Exception e) {
                    cancel();
                    resetTasks.remove(uuid);
                }
            }
        }.runTaskTimer(Main.getInstance(), 1L, 1L);

        resetTasks.put(uuid, task);
    }

    private static void cancelResetTask(UUID uuid) {
        BukkitTask task = resetTasks.remove(uuid);
        if (task != null) task.cancel();
    }

    public static void removeChgdimItemsFromPlayer(Player player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getItemMeta() != null) {
                var pdc = item.getItemMeta().getPersistentDataContainer();
                if (pdc.has(Keys.CHGDIM_GUI, PersistentDataType.BOOLEAN)) {
                    inv.setItem(i, null);
                }
            }
        }
    }

    private static ItemStack createReturnItem() {
        ItemStack item = new ItemStack(Material.ENDER_PEARL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse("<aqua><bold>↩ Return Back</bold></aqua>")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(List.of(
                MessageUtil.parse("<gray>Click to return to your starting point</gray>")
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
        ));
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return tagChgdimItem(item);
    }

    private static String getAnvilRenameText(Player player) {
        try {
            InventoryView view = player.getOpenInventory();
            Object craftView = view;
            Class<?> craftViewClass = craftView.getClass();

            java.lang.reflect.Method getHandle = craftViewClass.getMethod("getHandle");
            Object handle = getHandle.invoke(craftView);

            Class<?> anvilClass = handle.getClass();
            java.lang.reflect.Field itemNameField;

            try {
                itemNameField = anvilClass.getDeclaredField("itemName");
            } catch (NoSuchFieldException e) {
                itemNameField = anvilClass.getSuperclass().getDeclaredField("itemName");
            }

            itemNameField.setAccessible(true);
            String text = (String) itemNameField.get(handle);
            itemNameField.setAccessible(false);
            return text;
        } catch (Exception e) {
            return null;
        }
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        ItemStack item = event.getEntity().getItemStack();
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        if (meta.getPersistentDataContainer().has(Keys.CHGDIM_GUI, PersistentDataType.BOOLEAN)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!openMenus.containsKey(player.getUniqueId())) return;

        int slot = e.getSlot();

        if (slot < 0) {
            e.setCancelled(true);
            return;
        }

        if (slot >= 3) return;

        if (slot == 0) {
            e.setCancelled(true);
            return;
        }

        if (slot == 1) {
            e.setCancelled(true);
            player.closeInventory();
            ChgDimCommand.teleportBack(player);
            return;
        }

        e.setCancelled(true);

        String rawText = getAnvilRenameText(player);

        if (rawText == null || rawText.trim().isEmpty()) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.enter_world_name_field", "<red>❌ Enter world name in the rename field!</red>")));
            player.closeInventory();
            return;
        }

        String worldName = VALID_WORLD_NAME.matcher(rawText.trim()).replaceAll("");

        if (worldName.isEmpty()) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.invalid_world_name", "<red>❌ World name contains only invalid characters!</red>")));
            player.closeInventory();
            return;
        }

        if (!player.hasPermission("mcplugin.command.chgdim." + worldName)) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.no_permission",
                            "<dark_red>❌</dark_red> <red>You do not have permission to use this command!</red>")));
            player.closeInventory();
            return;
        }

        ChgDimCommand.teleport(player, worldName);
        player.closeInventory();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        openMenus.remove(uuid);
        cancelResetTask(uuid);
        if (e.getPlayer() instanceof Player player) {
            removeChgdimItemsFromPlayer(player);
        }
    }

    private static void register() {
        if (registered) return;
        registered = true;
        Bukkit.getPluginManager().registerEvents(new ChgDimGUI(), Main.getInstance());
    }
}
