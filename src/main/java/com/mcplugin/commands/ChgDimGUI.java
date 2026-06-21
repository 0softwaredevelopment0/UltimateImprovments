package com.mcplugin.commands;

import com.mcplugin.Keys;
import com.mcplugin.Main;
import com.mcplugin.config.MessagesManager;
import com.mcplugin.util.MessageUtil;
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
 * Anvil GUI для выбора мира при /mp chgdim.
 * <p>
 * Левый слот (0) — информационный предмет со списком миров.
 * Поле переименования — ввод названия мира.
 * Результат (слот 2) — подтверждение телепортации.
 * Центр (слот 1) — пустой.
 * Escape — закрывает GUI без последствий.
 */
public class ChgDimGUI implements Listener {

    private static final Map<UUID, String> openMenus = new ConcurrentHashMap<>();
    private static final Map<UUID, BukkitTask> resetTasks = new ConcurrentHashMap<>();
    private static boolean registered = false;

    // Regex for valid world name characters: only alphanumeric, underscore, hyphen
    private static final Pattern VALID_WORLD_NAME = Pattern.compile("[^a-zA-Z0-9_-]");

    // Cached confirm item (never changes)
    private static final ItemStack CONFIRM_ITEM;
    static {
        CONFIRM_ITEM = createConfirmItem();
    }

    /**
     * Открывает Anvil GUI для выбора мира телепортации.
     */
    public static void open(Player player) {
        register();

        FileConfiguration config = Main.getInstance().getConfig();
        ConfigurationSection worldsSection = config.getConfigurationSection("changedimmension.worlds");

        if (worldsSection == null || worldsSection.getKeys(false).isEmpty()) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.worlds_not_configured", "<red>❌ Worlds not configured in config!</red>")));
            return;
        }

        // Use MenuType.ANVIL.builder() — same as AuthGUI
        InventoryView view = MenuType.ANVIL.builder()
                .title(net.kyori.adventure.text.Component.text(MessagesManager.getString("changedimmension.gui.title", "§8✦ Change dimension")))
                .build(player);

        Inventory topInv = view.getTopInventory();

        // ===== SLOT 0: информационный предмет со списком миров =====
        ItemStack infoItem = createInfoItem(worldsSection);
        topInv.setItem(0, infoItem);

        // Slot 1 — пустой (центр)
        // Slot 2 — confirm item (will be kept by reset task)
        topInv.setItem(2, CONFIRM_ITEM);

        view.open();

        // Start repeating task to keep slot 2 as our confirm item
        startResetTask(player);

        openMenus.put(player.getUniqueId(), "chgdim");

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.4f, 1.0f);
        player.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.enter_world_name_hint", "<yellow>✦</yellow> <gray>Enter world name in the field, then click</gray> <green>✔</green>")));
    }

    // =========================
    // TAG ITEM WITH CHGDIM_GUI PDC KEY (anti-dup)
    // =========================
    private static ItemStack tagChgdimItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(Keys.CHGDIM_GUI, PersistentDataType.BOOLEAN, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    // =========================
    // CREATE INFO ITEM (slot 0)
    // =========================
    private static ItemStack createInfoItem(ConfigurationSection worldsSection) {
        ItemStack infoItem = new ItemStack(Material.PAPER);
        ItemMeta meta = infoItem.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(net.kyori.adventure.text.Component.text("§8┌────────────────────────────┐")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));

        Set<String> worldNames = worldsSection.getKeys(false);
        int count = 0;

        for (String worldName : worldNames) {
            count++;
            ConfigurationSection wc = worldsSection.getConfigurationSection(worldName);
            double x = wc != null ? wc.getDouble("x", 0) : 0;
            double y = wc != null ? wc.getDouble("y", 64) : 64;
            double z = wc != null ? wc.getDouble("z", 0) : 0;
            lore.add(net.kyori.adventure.text.Component.text("§8│ §e" + worldName)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            lore.add(net.kyori.adventure.text.Component.text("§8│ §8» §7" + Math.round(x) + " " + Math.round(y) + " " + Math.round(z))
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        }

        lore.add(net.kyori.adventure.text.Component.text("§8│")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        lore.add(net.kyori.adventure.text.Component.text("§8│ §7Всего миров: §f" + count)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        lore.add(net.kyori.adventure.text.Component.text("§8└────────────────────────────┘")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        lore.add(net.kyori.adventure.text.Component.text("")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        lore.add(net.kyori.adventure.text.Component.text("§e💡 Введите название мира в поле")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        lore.add(net.kyori.adventure.text.Component.text("§eи нажмите на появившийся предмет")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));

        meta.lore(lore);
        infoItem.setItemMeta(meta);
        return tagChgdimItem(infoItem);
    }

    // =========================
    // CREATE CONFIRM ITEM (slot 2)
    // =========================
    private static ItemStack createConfirmItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("§a§l✔ Телепортироваться")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(List.of(
                net.kyori.adventure.text.Component.text("§7Нажмите, чтобы подтвердить телепортацию")
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
        ));
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return tagChgdimItem(item);
    }

    // =========================
    // REPEATING TASK — KEEP RESULT SLOT AS NETHER STAR
    // =========================
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
                    // Use openMenus instead of getMenuType() == ANVIL for Leaf fork compatibility
                    if (!openMenus.containsKey(uuid)) {
                        cancel();
                        resetTasks.remove(uuid);
                        return;
                    }
                    openInv.getTopInventory().setItem(2, CONFIRM_ITEM);
                    // 🛡 Anti-dup: purge any ChgDimGUI items from player inventory
                    removeChgdimItemsFromPlayer(player);
                } catch (Exception e) {
                    cancel();
                    resetTasks.remove(uuid);
                }
            }
        }.runTaskTimer(Main.getInstance(), 3L, 3L);

        resetTasks.put(uuid, task);
    }

    private static void cancelResetTask(UUID uuid) {
        BukkitTask task = resetTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    // =========================
    // 🛡 ANTI-DUP: Remove any ChgDimGUI items from player inventory
    // =========================
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

    // =========================
    // GET RENAME TEXT FROM ANVIL (via NMS reflection — same as AuthGUI)
    // =========================
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

    // =========================
    // 🛡 ANTI-DUP: Cancel item spawn if it has CHGDIM_GUI PDC tag
    // Prevents GUI items from dropping on the ground when inventory is full
    // =========================
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
        // Use openMenus instead of getMenuType() == ANVIL for Leaf fork compatibility
        if (!openMenus.containsKey(player.getUniqueId())) return;

        // Блокируем клики по любым слотам, кроме результата (слот 2)
        if (e.getSlot() != 2) {
            e.setCancelled(true);
            return;
        }

        e.setCancelled(true);

        String rawText = getAnvilRenameText(player);

        if (rawText == null || rawText.trim().isEmpty()) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.enter_world_name_field", "<red>❌ Enter world name in the rename field!</red>")));
            player.closeInventory();
            return;
        }

        // Убираем все символы, кроме a-z, A-Z, 0-9, _, -
        String worldName = VALID_WORLD_NAME.matcher(rawText.trim()).replaceAll("");

        if (worldName.isEmpty()) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.invalid_world_name", "<red>❌ World name contains only invalid characters!</red>")));
            player.closeInventory();
            return;
        }

        // ===== CHECK PER-WORLD PERMISSION =====
        if (!player.hasPermission("mcplugin.command.chgdim." + worldName)) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.no_permission",
                            "<dark_red>❌</dark_red> <red>You do not have permission to use this command!</red>")));
            player.closeInventory();
            return;
        }

        // ===== TELEPORT (обрабатывает cooldown, world not found, success) =====
        ChgDimCommand.teleport(player, worldName);
        player.closeInventory();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        openMenus.remove(uuid);
        cancelResetTask(uuid);
        // 🛡 Anti-dup: remove any ChgDimGUI items from player inventory
        if (e.getPlayer() instanceof Player player) {
            removeChgdimItemsFromPlayer(player);
        }
    }

    /**
     * Регистрирует слушатель один раз при первом вызове open().
     */
    private static void register() {
        if (registered) return;
        registered = true;
        Bukkit.getPluginManager().registerEvents(new ChgDimGUI(), Main.getInstance());
    }
}
