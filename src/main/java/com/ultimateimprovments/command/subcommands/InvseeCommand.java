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
 * Инвентарь-редактор: /ui invsee &lt;player&gt; и /ui endersee &lt;player&gt;.
 *
 * <p>GUI идентифицируется по трекингу сессий ({@link #INVSEE_SESSIONS}), а НЕ по
 * {@code InventoryView.getTitle()} — в Paper 1.21.x getTitle() возвращает Component
 * и сравнение со String всегда ложно (см. NotesGUIListener), из-за чего стеклянные
 * панели было можно забирать и синк не происходил.
 *
 * <p>Анти-дюп: GUI — живое зеркало, а не снимок. Пока окно открыто, периодический
 * таск тянет реальное состояние инвентаря цели в GUI (если админ не взаимодействует),
 * а каждый клик/драг мгновенно пушит изменения цели. Так предметы, которые цель
 * выкинула/подобрала во время редактирования, не «воскресают» при закрытии.
 */
public final class InvseeCommand {

    private static final NamespacedKey PLACEHOLDER_KEY = new NamespacedKey(Main.getInstance(), "invsee_placeholder");
    private static final Map<UUID, InvseeSession> INVSEE_SESSIONS = new HashMap<>();
    private static boolean registered = false;

    private InvseeCommand() {}

    /** Одна открытая сессия invsee: кто смотрит → чей GUI и кто цель. */
    private static final class InvseeSession {
        final UUID viewerId;
        final UUID targetId;
        final Inventory gui;
        BukkitTask refreshTask;
        /** Тик сервера последнего действия админа — защита от гонки pull/push. */
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

        // Заменяем устаревшую сессию (если админ уже редактировал кого-то)
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

        // Открываем НАСТОЯЩИЙ эндер-сундук цели — Paper сам синхронизирует все изменения
        // Помечаем зрителя, чтобы EnderChestManager не нанёс ему урон при закрытии
        EnderChestManager.addEnderseeViewer(viewer.getUniqueId());
        viewer.openInventory(target.getEnderChest());
    }

    // =========================
    // LIVE REFRESH (target → GUI)
    // =========================
    /**
     * Пока GUI открыт, периодически тянем реальное состояние инвентаря цели в GUI.
     * Пропускаем обновление, если админ прямо сейчас тащит предмет (курсор занят)
     * или только что кликнул (последние 2 тика) — чтобы не конфликтовать с push'ем.
     * Это делает GUI живым зеркалом и исключает дюп из-за устаревшего снимка.
     */
    private static void startRefresh(Player viewer, InvseeSession session) {
        session.refreshTask = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), () -> {
            if (INVSEE_SESSIONS.get(viewer.getUniqueId()) != session) return; // закрыта/заменена
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
     * Безопасный pull target → GUI. Скипается, пока админ тащит предмет на курсоре
     * (иначе можно «воскресить» в GUI предмет, который уже взят в курсор) и в первые
     * 2 тика после клика админа (чтобы не конфликтовать с push'ем).
     */
    private static void maybePull(InvseeSession session, Player viewer, Player target) {
        ItemStack cursor = viewer.getItemOnCursor();
        if (cursor != null && cursor.getType() != Material.AIR) return; // админ тащит
        if (Bukkit.getCurrentTick() - session.lastAdminTick < 2) return; // свежий клик — дадим push'у приземлиться
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
            // Guard: сессия могла закрыться/смениться до запуска таска
            if (INVSEE_SESSIONS.get(viewerId) != session) return;
            Player target = Bukkit.getPlayer(session.targetId);
            syncInvsee(top, target);
        });
    }

    private static void syncInvsee(Inventory gui, Player target) {
        if (target == null || !target.isOnline()) return;
        PlayerInventory inv = target.getInventory();

        // Sync armor + hands (клонируем, чтобы не шарить ссылки с GUI)
        inv.setHelmet(cloneOrNull(unwrapPlaceholder(gui.getItem(0))));
        inv.setLeggings(cloneOrNull(unwrapPlaceholder(gui.getItem(1))));
        inv.setChestplate(cloneOrNull(unwrapPlaceholder(gui.getItem(9))));
        inv.setBoots(cloneOrNull(unwrapPlaceholder(gui.getItem(10))));
        inv.setItemInMainHand(cloneOrNull(unwrapPlaceholder(gui.getItem(2))));
        inv.setItemInOffHand(cloneOrNull(unwrapPlaceholder(gui.getItem(11))));

        // Cursor slot — если админ каким-то образом что-то туда положил, выбрасываем у ног цели
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

    /** Сравнение двух слотов: пустое = пустое, плейсхолдер = плейсхолдер, иначе ItemStack.equals. */
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
        // Маркируем как плейсхолдер: isPlaceholder() блокирует взятие ЛЮБЫМ способом
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
        // Блокируем взятие книги любым способом
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
            if (session.gui != top) return; // не наш GUI (например, реальный эндер-сундук в endersee)

            int slot = event.getRawSlot();

            // Нижний инвентарь (свой инвентарь админа) — разрешаем свободно,
            // но всё равно пушим (сдвиг-клик из своего инвентаря в GUI = передача предмета цели)
            if (slot >= top.getSize()) {
                scheduleSync(player, top, session);
                return;
            }

            // Стеклянные панели (4-8, 13-17) — декоратив, брать нельзя
            if ((slot >= 4 && slot <= 8) || (slot >= 13 && slot <= 17)) {
                event.setCancelled(true);
                return;
            }

            // Книга (12) — без взаимодействия
            if (slot == 12) {
                event.setCancelled(true);
                return;
            }

            // Плейсхолдеры (броня/курсор/стекло) — нельзя брать ни кликом, ни сдвигом
            if (isPlaceholder(event.getCurrentItem())) {
                event.setCancelled(true);
                return;
            }

            // Разрешаем взаимодействие — мгновенный синк после обработки события
            scheduleSync(player, top, session);
        }

        @EventHandler(priority = EventPriority.HIGH)
        public void onInventoryDrag(InventoryDragEvent event) {
            if (!(event.getWhoClicked() instanceof Player player)) return;
            InvseeSession session = INVSEE_SESSIONS.get(player.getUniqueId());
            if (session == null) return;
            Inventory top = event.getView().getTopInventory();
            if (session.gui != top) return;

            // Драг с участием нижнего инвентаря (GUI ↔ инвентарь админа) — отменяем (анти-дюп)
            for (int slot : event.getRawSlots()) {
                if (slot >= top.getSize()) {
                    event.setCancelled(true);
                    return;
                }
            }

            // Драг через стекло/книгу/плейсхолдер — отменяем
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

            // Разрешённый драг внутри GUI — мгновенный синк
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
                // Подтягиваем живое состояние цели, чтобы финальный синк не затёр
                // свежие изменения цели, сделанные за время редактирования.
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
