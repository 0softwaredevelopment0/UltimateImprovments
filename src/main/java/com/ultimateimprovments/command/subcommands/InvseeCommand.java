package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.mechanics.features.blocks.EnderChestManager;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Inventory editor: /ui invsee &lt;player&gt; and /ui endersee &lt;player&gt;.
 *
 * <p>The GUI is identified by session tracking ({@link #INVSEE_SESSIONS}), NOT by
 * {@code InventoryView.getTitle()} — in Paper 1.21.x getTitle() returns a Component
 * and comparing it to a String is always false (see NotesGUIListener), which let
 * players take the glass panels and broke sync.
 *
 * <p>Anti-dupe: the GUI is a live mirror, not a snapshot. While the window is open, a
 * periodic task pulls the target's real inventory state into the GUI (when the admin
 * isn't interacting), and every click/drag instantly pushes changes to the target.
 * This way items the target dropped/picked up during editing don't "resurrect" on close.
 */
public final class InvseeCommand {

    private static final NamespacedKey PLACEHOLDER_KEY = new NamespacedKey(Main.getInstance(), "invsee_placeholder");
    private static final Map<UUID, InvseeSession> INVSEE_SESSIONS = new HashMap<>();
    private static boolean registered = false;

    private InvseeCommand() {}

    /** One open invsee session: who is viewing → whose GUI and who is the target. */
    private static final class InvseeSession {
        final UUID viewerId;
        final UUID targetId;
        final Inventory gui;
        BukkitTask refreshTask;
        /** Server tick of the admin's last action — protection against a pull/push race. */
        volatile long lastAdminTick;

        InvseeSession(UUID viewerId, UUID targetId, Inventory gui) {
            this.viewerId = viewerId;
            this.targetId = targetId;
            this.gui = gui;
        }
    }

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
    // /UI INVSEE <PLAYER>
    // =========================
    public static boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Only players can use this command!</red>"));
            return true;
        }
        if (!player.hasPermission("ui.command.invsee")) {
            player.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to use this command!</red>"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui invsee <player></white>"));
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
    // /UI ENDERSEE <PLAYER>
    // =========================
    public static boolean executeEnder(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Only players can use this command!</red>"));
            return true;
        }
        if (!player.hasPermission("ui.command.endersee")) {
            player.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to use this command!</red>"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui endersee <player></white>"));
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

        // Replace the stale session (if the admin was already editing someone)
        endSession(viewer.getUniqueId());

        InvseeSession session = new InvseeSession(viewer.getUniqueId(), target.getUniqueId(), gui);
        INVSEE_SESSIONS.put(viewer.getUniqueId(), session);
        viewer.openInventory(gui);
        startRefresh(viewer, session);
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

        // Open the target's REAL ender chest — Paper syncs all changes itself
        // Mark the viewer so EnderChestManager doesn't damage them on close
        EnderChestManager.addEnderseeViewer(viewer.getUniqueId());
        viewer.openInventory(target.getEnderChest());
    }

    // =========================
    // LIVE REFRESH (target → GUI)
    // =========================
    /**
     * While the GUI is open, periodically pull the target's real inventory state into it.
     * Skip the update if the admin is currently dragging an item (cursor busy)
     * or just clicked (last 2 ticks) — to not conflict with the push.
     * This makes the GUI a live mirror and eliminates dupes from a stale snapshot.
     */
    private static void startRefresh(Player viewer, InvseeSession session) {
        session.refreshTask = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), () -> {
            if (INVSEE_SESSIONS.get(viewer.getUniqueId()) != session) return; // closed/replaced
            if (!viewer.isOnline()) {
                endSession(viewer.getUniqueId());
                return;
            }
            Player target = Bukkit.getPlayer(session.targetId);
            if (target == null || !target.isOnline()) return;

            maybePull(session, viewer, target);
        }, 10L, 10L);
    }

    /**
     * Safe pull target → GUI. Skipped while the admin is dragging an item on the cursor
     * (otherwise you could "resurrect" in the GUI an item already taken to the cursor) and
     * for the first 2 ticks after the admin's click (to not conflict with the push).
     */
    private static void maybePull(InvseeSession session, Player viewer, Player target) {
        ItemStack cursor = viewer.getItemOnCursor();
        if (cursor != null && cursor.getType() != Material.AIR) return; // admin is dragging
        if (Bukkit.getCurrentTick() - session.lastAdminTick < 2) return; // fresh click — let the push land
        pullTargetToGui(session, target);
    }

    private static void pullTargetToGui(InvseeSession session, Player target) {
        PlayerInventory inv = target.getInventory();
        Inventory gui = session.gui;

        pullEquipped(gui, 0, inv.getHelmet(), Material.CHAINMAIL_HELMET, "Helmet");
        pullEquipped(gui, 1, inv.getLeggings(), Material.CHAINMAIL_LEGGINGS, "Leggings");
        pullEquipped(gui, 2, inv.getItemInMainHand(), Material.STONE_SWORD, "Main Hand");
        pullEquipped(gui, 9, inv.getChestplate(), Material.CHAINMAIL_CHESTPLATE, "Chestplate");
        pullEquipped(gui, 10, inv.getBoots(), Material.CHAINMAIL_BOOTS, "Boots");
        pullEquipped(gui, 11, inv.getItemInOffHand(), Material.SHIELD, "Off Hand");

        ItemStack[] storage = inv.getStorageContents();
        for (int i = 0; i < 27; i++) {
            int guiSlot = 18 + i;
            ItemStack real = storage[9 + i];
            if (!itemEquals(gui.getItem(guiSlot), real)) {
                gui.setItem(guiSlot, real == null || real.getType() == Material.AIR ? null : real.clone());
            }
        }
        for (int i = 0; i < 9; i++) {
            int guiSlot = 45 + i;
            ItemStack real = storage[i];
            if (!itemEquals(gui.getItem(guiSlot), real)) {
                gui.setItem(guiSlot, real == null || real.getType() == Material.AIR ? null : real.clone());
            }
        }
    }

    private static void pullEquipped(Inventory gui, int guiSlot, ItemStack real, Material placeholderType, String placeholderName) {
        ItemStack shown = (real == null || real.getType() == Material.AIR)
                ? createPlaceholder(placeholderType, placeholderName)
                : real.clone();
        if (!itemEquals(gui.getItem(guiSlot), shown)) {
            gui.setItem(guiSlot, shown);
        }
    }

    // =========================
    // SYNC METHODS (GUI → target)
    // =========================
    private static void scheduleSync(Player viewer, Inventory top, InvseeSession session) {
        session.lastAdminTick = Bukkit.getCurrentTick();
        UUID viewerId = viewer.getUniqueId();
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            // Guard: the session could have closed/changed before the task ran
            if (INVSEE_SESSIONS.get(viewerId) != session) return;
            Player target = Bukkit.getPlayer(session.targetId);
            syncInvsee(top, target);
        });
    }

    private static void syncInvsee(Inventory gui, Player target) {
        if (target == null || !target.isOnline()) return;
        PlayerInventory inv = target.getInventory();

        // Sync armor + hands (clone to not share references with the GUI)
        inv.setHelmet(cloneOrNull(unwrapPlaceholder(gui.getItem(0))));
        inv.setLeggings(cloneOrNull(unwrapPlaceholder(gui.getItem(1))));
        inv.setChestplate(cloneOrNull(unwrapPlaceholder(gui.getItem(9))));
        inv.setBoots(cloneOrNull(unwrapPlaceholder(gui.getItem(10))));
        inv.setItemInMainHand(cloneOrNull(unwrapPlaceholder(gui.getItem(2))));
        inv.setItemInOffHand(cloneOrNull(unwrapPlaceholder(gui.getItem(11))));

        // Cursor slot — if the admin somehow put something there, drop it at the target's feet
        ItemStack cursorItem = unwrapPlaceholder(gui.getItem(3));
        if (cursorItem != null && cursorItem.getType() != Material.AIR) {
            target.getWorld().dropItemNaturally(target.getLocation(), cursorItem);
            gui.setItem(3, createPlaceholder(Material.SPECTRAL_ARROW, "Cursor"));
        }

        // Sync storage
        ItemStack[] storage = inv.getStorageContents(); // 36 slots
        for (int i = 0; i < 27; i++) {
            storage[9 + i] = cloneOrNull(gui.getItem(18 + i));
        }
        for (int i = 0; i < 9; i++) {
            storage[i] = cloneOrNull(gui.getItem(45 + i));
        }
        inv.setStorageContents(storage);
    }

    // =========================
    // SESSION LIFECYCLE
    // =========================
    private static void endSession(UUID viewerId) {
        InvseeSession session = INVSEE_SESSIONS.remove(viewerId);
        if (session != null && session.refreshTask != null) {
            session.refreshTask.cancel();
        }
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

    private static ItemStack cloneOrNull(ItemStack item) {
        if (item == null) return null;
        return item.clone();
    }

    private static boolean isPlaceholder(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(PLACEHOLDER_KEY);
    }

    /** Slot comparison: empty = empty, placeholder = placeholder, otherwise ItemStack.equals. */
    private static boolean itemEquals(ItemStack a, ItemStack b) {
        boolean aEmpty = a == null || a.getType() == Material.AIR;
        boolean bEmpty = b == null || b.getType() == Material.AIR;
        if (aEmpty && bEmpty) return true;
        if (aEmpty || bEmpty) return false;
        boolean aPh = isPlaceholder(a);
        boolean bPh = isPlaceholder(b);
        if (aPh && bPh) return true;
        if (aPh || bPh) return false;
        return a.equals(b);
    }

    private static ItemStack createGlassPane() {
        ItemStack glass = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.displayName(MessageUtil.parse("<reset>").decoration(TextDecoration.ITALIC, false));
        meta.setHideTooltip(true);
        // Mark as a placeholder: isPlaceholder() blocks taking it ANY way
        meta.getPersistentDataContainer().set(PLACEHOLDER_KEY, PersistentDataType.BOOLEAN, true);
        glass.setItemMeta(meta);
        return glass;
    }

    private static ItemStack createPlaceholder(Material type, String displayName) {
        ItemStack item = new ItemStack(type);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse("<gray>" + displayName + "</gray>")
                .decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(PLACEHOLDER_KEY, PersistentDataType.BOOLEAN, true);
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
        meta.lore(java.util.List.of(
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
        // Block taking the book any way
        meta.getPersistentDataContainer().set(PLACEHOLDER_KEY, PersistentDataType.BOOLEAN, true);
        book.setItemMeta(meta);
        return book;
    }

    // =========================
    // LISTENER
    // =========================
    private static class InvseeListener implements Listener {

        @EventHandler(priority = EventPriority.HIGH)
        public void onInventoryClick(InventoryClickEvent event) {
            if (!(event.getWhoClicked() instanceof Player player)) return;
            InvseeSession session = INVSEE_SESSIONS.get(player.getUniqueId());
            if (session == null) return;
            Inventory top = event.getView().getTopInventory();
            if (session.gui != top) return; // not our GUI (e.g. the real ender chest in endersee)

            int slot = event.getRawSlot();

            // Bottom inventory (the admin's own) — allow freely,
            // but still push (a shift-click from own inventory into the GUI = giving the item to the target)
            if (slot >= top.getSize()) {
                scheduleSync(player, top, session);
                return;
            }

            // Glass panels (4-8, 13-17) — decoration, can't be taken
            if ((slot >= 4 && slot <= 8) || (slot >= 13 && slot <= 17)) {
                event.setCancelled(true);
                return;
            }

            // Book (12) — no interaction
            if (slot == 12) {
                event.setCancelled(true);
                return;
            }

            // Placeholders (armor/cursor/glass) — can't be taken by click or shift
            if (isPlaceholder(event.getCurrentItem())) {
                event.setCancelled(true);
                return;
            }

            // Allow the interaction — instant sync after the event is handled
            scheduleSync(player, top, session);
        }

        @EventHandler(priority = EventPriority.HIGH)
        public void onInventoryDrag(InventoryDragEvent event) {
            if (!(event.getWhoClicked() instanceof Player player)) return;
            InvseeSession session = INVSEE_SESSIONS.get(player.getUniqueId());
            if (session == null) return;
            Inventory top = event.getView().getTopInventory();
            if (session.gui != top) return;

            // Drag involving the bottom inventory (GUI ↔ admin inventory) — cancel (anti-dupe)
            for (int slot : event.getRawSlots()) {
                if (slot >= top.getSize()) {
                    event.setCancelled(true);
                    return;
                }
            }

            // Drag through glass/book/placeholder — cancel
            for (int slot : event.getRawSlots()) {
                if (slot < top.getSize()) {
                    if ((slot >= 4 && slot <= 8) || (slot >= 13 && slot <= 17) || slot == 12) {
                        event.setCancelled(true);
                        return;
                    }
                    if (isPlaceholder(top.getItem(slot))) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }

            // Allowed drag inside the GUI — instant sync
            scheduleSync(player, top, session);
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onInventoryClose(InventoryCloseEvent event) {
            if (!(event.getPlayer() instanceof Player player)) return;
            UUID viewerId = player.getUniqueId();
            InvseeSession session = INVSEE_SESSIONS.get(viewerId);
            if (session == null) return;
            if (session.gui != event.getInventory()) return;

            endSession(viewerId);
            Player target = Bukkit.getPlayer(session.targetId);
            if (target != null && target.isOnline()) {
                // Pull the target's live state so the final sync doesn't overwrite
                // fresh target changes made during the editing.
                maybePull(session, player, target);
            }
            syncInvsee(session.gui, target);
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onPlayerQuit(PlayerQuitEvent event) {
            endSession(event.getPlayer().getUniqueId());
        }
    }
}
