package com.mcplugin.command.subcommands;

import com.mcplugin.core.Main;
import com.mcplugin.util.MessageUtil;
import com.mcplugin.mechanics.features.blocks.EnderChestManager;
import com.mcplugin.util.ConsoleLogger;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;


import java.util.*;

public final class InvseeCommand {

    private static final NamespacedKey PLACEHOLDER_KEY = new NamespacedKey(Main.getInstance(), "invsee_placeholder");
    private static final Map<UUID, UUID> INVSEE_OPENERS = new HashMap<>();
    private static boolean registered = false;

    private InvseeCommand() {}

    // =========================
    // REGISTER LISTENER
    // =========================
    private static void ensureRegistered() {
        if (!registered) {
            Bukkit.getPluginManager().registerEvents(new InvseeListener(), Main.getInstance());
            registered = true;
        }
    }

    // =========================
    // /MP INVSEE <PLAYER>
    // =========================
    public static boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Only players can use this command!</red>"));
            return true;
        }
        if (!player.hasPermission("mcplugin.command.invsee")) {
            player.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to use this command!</red>"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/mp invsee <player></white>"));
            return true;
        }

        @SuppressWarnings("deprecation")
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(MessageUtil.parse("<red>❌ Player</red> <yellow>" + args[1] + "</yellow> <red>not found or not online!</red>"));
            return true;
        }

        ensureRegistered();
        openInvseeGUI(player, target);
        return true;
    }

    // =========================
    // /MP ENDERSEE <PLAYER>
    // =========================
    public static boolean executeEnder(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Only players can use this command!</red>"));
            return true;
        }
        if (!player.hasPermission("mcplugin.command.endersee")) {
            player.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to use this command!</red>"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/mp endersee <player></white>"));
            return true;
        }

        @SuppressWarnings("deprecation")
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(MessageUtil.parse("<red>❌ Player</red> <yellow>" + args[1] + "</yellow> <red>not found or not online!</red>"));
            return true;
        }

        ensureRegistered();
        openEnderSeeGUI(player, target);
        return true;
    }

    // =========================
    // BUILD INVSEE GUI
    // =========================
    private static void openInvseeGUI(Player viewer, Player target) {
        String title = MessageUtil.legacy("<dark_gray>" + target.getName() + "'s inventory overview</dark_gray>");
        Inventory gui = Bukkit.createInventory(null, 54, title);

        PlayerInventory inv = target.getInventory();
        ItemStack[] storage = inv.getStorageContents(); // 36 slots: 0-8 hotbar, 9-35 inventory

        // ── Row 0: Helmet, Leggings, MainHand, Cursor, then glass ──
        gui.setItem(0, placeholderOrItem(inv.getHelmet(), createPlaceholder(Material.CHAINMAIL_HELMET, "Helmet")));
        gui.setItem(1, placeholderOrItem(inv.getLeggings(), createPlaceholder(Material.CHAINMAIL_LEGGINGS, "Leggings")));
        gui.setItem(2, placeholderOrItem(inv.getItemInMainHand(), createPlaceholder(Material.STONE_SWORD, "Main Hand")));
        gui.setItem(3, createPlaceholder(Material.SPECTRAL_ARROW, "Cursor"));
        for (int i = 4; i <= 8; i++) {
            gui.setItem(i, createGlassPane());
        }

        // ── Row 1: Chestplate, Boots, OffHand, Book, then glass ──
        gui.setItem(9, placeholderOrItem(inv.getChestplate(), createPlaceholder(Material.CHAINMAIL_CHESTPLATE, "Chestplate")));
        gui.setItem(10, placeholderOrItem(inv.getBoots(), createPlaceholder(Material.CHAINMAIL_BOOTS, "Boots")));
        gui.setItem(11, placeholderOrItem(inv.getItemInOffHand(), createPlaceholder(Material.SHIELD, "Off Hand")));
        gui.setItem(12, createPlayerDataBook(target));
        for (int i = 13; i <= 17; i++) {
            gui.setItem(i, createGlassPane());
        }

        // ── Rows 2-4: Main inventory (storage slots 9-35) ──
        for (int i = 0; i < 27; i++) {
            gui.setItem(18 + i, storage[9 + i] != null ? storage[9 + i].clone() : null);
        }

        // ── Row 5: Hotbar (storage slots 0-8) ──
        for (int i = 0; i < 9; i++) {
            gui.setItem(45 + i, storage[i] != null ? storage[i].clone() : null);
        }

        INVSEE_OPENERS.put(viewer.getUniqueId(), target.getUniqueId());
        viewer.openInventory(gui);
    }

    // =========================
    // OPEN REAL ENDER CHEST
    // =========================
    private static void openEnderSeeGUI(Player viewer, Player target) {
        var config = Main.getInstance().getConfig();
        boolean enabled = config.getBoolean("endersee.enabled", false);
        if (!enabled) {
            viewer.sendMessage(MessageUtil.parse("<red>❌ EnderSee is disabled in the config!</red>"));
            return;
        }

        // Открываем НАСТОЯЩИЙ эндер-сундук цели — Paper сам синхронизирует все изменения
        // Помечаем зрителя, чтобы EnderChestManager не нанёс ему урон при закрытии
        EnderChestManager.addEnderseeViewer(viewer.getUniqueId());
        viewer.openInventory(target.getEnderChest());
    }

    // =========================
    // SYNC METHODS
    // =========================
    private static void syncInvsee(Inventory gui, Player target) {
        if (target == null || !target.isOnline()) return;
        PlayerInventory inv = target.getInventory();

        // Sync armor + hands
        inv.setHelmet(unwrapPlaceholder(gui.getItem(0)));
        inv.setLeggings(unwrapPlaceholder(gui.getItem(1)));
        inv.setChestplate(unwrapPlaceholder(gui.getItem(9)));
        inv.setBoots(unwrapPlaceholder(gui.getItem(10)));
        inv.setItemInMainHand(unwrapPlaceholder(gui.getItem(2)));
        inv.setItemInOffHand(unwrapPlaceholder(gui.getItem(11)));

        // Cursor slot — drop item at target's feet if something is there
        ItemStack cursorItem = unwrapPlaceholder(gui.getItem(3));
        if (cursorItem != null && cursorItem.getType() != Material.AIR) {
            target.getWorld().dropItemNaturally(target.getLocation(), cursorItem);
        }

        // Sync storage
        ItemStack[] storage = inv.getStorageContents(); // 36 slots
        for (int i = 0; i < 27; i++) {
            storage[9 + i] = gui.getItem(18 + i);
        }
        for (int i = 0; i < 9; i++) {
            storage[i] = gui.getItem(45 + i);
        }
        inv.setStorageContents(storage);
    }



    // =========================
    // ITEM HELPERS
    // =========================
    private static ItemStack placeholderOrItem(ItemStack real, ItemStack placeholder) {
        if (real == null || real.getType() == Material.AIR) {
            return placeholder;
        }
        return real.clone();
    }

    private static ItemStack unwrapPlaceholder(ItemStack item) {
        if (item == null) return null;
        if (isPlaceholder(item)) return null;
        return item;
    }

    private static boolean isPlaceholder(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(PLACEHOLDER_KEY);
    }

    private static ItemStack createGlassPane() {
        ItemStack glass = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.displayName(MessageUtil.parse("<reset>").decoration(TextDecoration.ITALIC, false));
        meta.setHideTooltip(true);
        glass.setItemMeta(meta);
        return glass;
    }

    private static ItemStack createPlaceholder(Material type, String displayName) {
        ItemStack item = new ItemStack(type);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse("<gray>" + displayName + "</gray>")
                .decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(PLACEHOLDER_KEY, org.bukkit.persistence.PersistentDataType.BOOLEAN, true);
        meta.setHideTooltip(true);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createPlayerDataBook(Player target) {
        ItemStack book = new ItemStack(Material.BOOK);
        ItemMeta meta = book.getItemMeta();

        String ip = "Unknown";
        if (target.getAddress() != null) {
            ip = target.getAddress().getAddress().getHostAddress();
        }

        String loc = String.format("%.1f, %.1f, %.1f",
                target.getLocation().getX(),
                target.getLocation().getY(),
                target.getLocation().getZ());

        meta.displayName(MessageUtil.parse("<gold>✦ Player Info</gold>")
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                MessageUtil.parse("<gray>UUID: <white>" + target.getUniqueId() + "</white></gray>"),
                MessageUtil.parse("<gray>Nick: <white>" + target.getName() + "</white></gray>"),
                MessageUtil.parse("<gray>IP: <white>" + ip + "</white></gray>"),
                MessageUtil.parse("<gray>World: <white>" + target.getWorld().getName() + "</white></gray>"),
                MessageUtil.parse("<gray>Location: <white>" + loc + "</white></gray>"),
                MessageUtil.parse("<gray>Health: <white>" + String.format("%.1f ❤", target.getHealth()) + "</white></gray>"),
                MessageUtil.parse("<gray>Food: <white>" + target.getFoodLevel() + " 🍖</white></gray>"),
                MessageUtil.parse("<gray>Level: <white>" + target.getLevel() + " ⭐</white></gray>"),
                MessageUtil.parse("<gray>XP Progress: <white>" + String.format("%.1f%%", target.getExp() * 100) + "</white></gray>"),
                MessageUtil.parse("<gray>Gamemode: <white>" + target.getGameMode().name() + "</white></gray>")
        ));
        book.setItemMeta(meta);
        return book;
    }

    // =========================
    // CHECK IF GUI TITLE MATCHES
    // =========================
    private static boolean isInvseeTitle(String title) {
        return title != null && title.contains("'s inventory overview");
    }



    // =========================
    // LISTENER
    // =========================
    private static class InvseeListener implements Listener {

        @EventHandler(priority = EventPriority.HIGH)
        public void onInventoryClick(InventoryClickEvent event) {
            if (!(event.getWhoClicked() instanceof Player player)) return;
            Inventory top = event.getView().getTopInventory();
            if (top == null) return;

            String title = event.getView().getTitle();

            if (isInvseeTitle(title)) {
                handleInvseeClick(event, player, top);
            }
        }

        @EventHandler(priority = EventPriority.HIGH)
        public void onInventoryDrag(InventoryDragEvent event) {
            if (!(event.getWhoClicked() instanceof Player player)) return;
            String title = event.getView().getTitle();

            if (isInvseeTitle(title)) {
                Inventory top = event.getView().getTopInventory();

                // Cancel if drag involves bottom inventory (GUI ↔ player inv)
                for (int slot : event.getRawSlots()) {
                    if (slot >= top.getSize()) {
                        event.setCancelled(true);
                        return;
                    }
                }

                // Cancel if drag touches glass/book/placeholder slots
                for (int slot : event.getRawSlots()) {
                    if (slot < top.getSize()) {
                        if ((slot >= 4 && slot <= 8) || (slot >= 13 && slot <= 17) || slot == 12) {
                            event.setCancelled(true);
                            return;
                        }
                        ItemStack current = top.getItem(slot);
                        if (isPlaceholder(current)) {
                            event.setCancelled(true);
                            return;
                        }
                    }
                }

                // Allow drag within allowed slots — sync immediately
                UUID targetId = INVSEE_OPENERS.get(player.getUniqueId());
                if (targetId != null) {
                    Player target = Bukkit.getPlayer(targetId);
                    if (target != null) {
                        Inventory finalTop = top;
                        Bukkit.getScheduler().runTask(Main.getInstance(), () -> syncInvsee(finalTop, target));
                    }
                }
            }
        }

        @EventHandler(priority = EventPriority.HIGH)
        public void onInventoryClose(InventoryCloseEvent event) {
            if (!(event.getPlayer() instanceof Player player)) return;
            Inventory top = event.getInventory();
            String title = event.getView().getTitle();

            UUID viewerId = player.getUniqueId();

            if (isInvseeTitle(title)) {
                UUID targetId = INVSEE_OPENERS.remove(viewerId);
                if (targetId != null) {
                    Player target = Bukkit.getPlayer(targetId);
                    syncInvsee(top, target);
                }
            }
        }

        private void handleInvseeClick(InventoryClickEvent event, Player player, Inventory top) {
            int slot = event.getRawSlot();

            // Bottom inventory (player's own inventory) — allow freely
            if (slot >= top.getSize()) return;

            // Glass panes (slots 4-8, 13-17)
            if ((slot >= 4 && slot <= 8) || (slot >= 13 && slot <= 17)) {
                event.setCancelled(true);
                return;
            }

            // Book slot (12) — no interaction
            if (slot == 12) {
                event.setCancelled(true);
                return;
            }

            // Placeholder items — can't be taken
            ItemStack current = event.getCurrentItem();
            if (isPlaceholder(current)) {
                event.setCancelled(true);
                return;
            }

            // Allow interaction — sync immediately after event processes
            UUID targetId = INVSEE_OPENERS.get(player.getUniqueId());
            if (targetId != null) {
                Player target = Bukkit.getPlayer(targetId);
                if (target != null) {
                    Inventory finalTop = top;
                    Bukkit.getScheduler().runTask(Main.getInstance(), () -> syncInvsee(finalTop, target));
                }
            }
        }


    }
}
