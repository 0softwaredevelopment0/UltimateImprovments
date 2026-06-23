package com.mcplugin.mechanics.features.items;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.component.WritableBookContent;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GUI заметок: двойной сундук (54 слота) с книгами-заметками.
 * Каждая книга = одна заметка, хранится в БД через NotesDatabase.
 */
public class NotesGUI {

    public static final String GUI_TITLE = "Ваши заметки";
    public static final int GUI_SIZE = 54;

    // Игроки, у которых открыт GUI заметок
    static final Set<UUID> openPlayers = ConcurrentHashMap.newKeySet();
    // Игроки, которые сейчас редактируют книгу (UUID → номер заметки)
    static final Map<UUID, Integer> editingSlots = new ConcurrentHashMap<>();
    // Игроки в процессе перехода из главного GUI в редактор книги
    static final Set<UUID> transitioningToBook = ConcurrentHashMap.newKeySet();
    // Предметы, которые были в руке до открытия редактора — восстанавливаем после Done/Quit
    static final Map<UUID, ItemStack> pendingRestores = new ConcurrentHashMap<>();

    private NotesGUI() {}

    // =========================
    // OPEN MAIN GUI (54 слота)
    // =========================
    public static void openMainGUI(Player player) {
        UUID uuid = player.getUniqueId();
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE);

        for (int slot = 0; slot < GUI_SIZE; slot++) {
            int noteNumber = slot + 1;
            ItemStack book = createNoteBook(uuid, noteNumber);
            inv.setItem(slot, book);
        }

        openPlayers.add(uuid);
        player.openInventory(inv);
    }

    // =========================
    // CREATE NOTE BOOK (helper)
    // =========================
    private static ItemStack createNoteBook(UUID uuid, int noteNumber) {
        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) return book;

        meta.setDisplayName("§fЗаметка #" + noteNumber);

        String content = NotesDatabase.loadNote(uuid, noteNumber);
        if (content != null && !content.isEmpty()) {
            String preview = content.length() > 30 ? content.substring(0, 30) + "..." : content;
            List<String> lore = new ArrayList<>();
            lore.add("§7" + preview.replace("\n", " "));
            try {
                meta.setLore(lore);
            } catch (Exception ignored) {
                // Paper 1.21.x may restrict lore on WRITABLE_BOOK
            }
            try {
                meta.setPages(splitPages(content));
            } catch (Exception ignored) {
                // Page content may not be settable on WRITABLE_BOOK in newer Paper
            }
        } else {
            List<String> lore = new ArrayList<>();
            lore.add("§8(пусто)");
            try {
                meta.setLore(lore);
            } catch (Exception ignored) {}
        }

        book.setItemMeta(meta);
        return book;
    }

    // =========================
    // OPEN BOOK EDITOR — через ClientboundOpenBookPacket (1.21.4+)
    //
    // ⚠ В Paper 1.21.x установка страниц через BookMeta.setPages() на WRITABLE_BOOK
    //    может привести к открытию книги как read-only (completed book).
    //    Используем NBT напрямую, чтобы обойти это поведение.
    // =========================
    public static void openBookEditor(Player player, int noteNumber) {
        UUID uuid = player.getUniqueId();
        editingSlots.put(uuid, noteNumber);

        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);

        // ⚡ В Paper 1.21.4+ BookMeta.setPages() на WRITABLE_BOOK вызывает read-only баг.
        // Используем Data Component API напрямую через NMS: WritableBookContent + DataComponents.
        String content = NotesDatabase.loadNote(uuid, noteNumber);
        try {
            net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(book);

            List<Filterable<String>> pagesList = new ArrayList<>();
            if (content != null && !content.isEmpty()) {
                for (String page : splitPages(content)) {
                    pagesList.add(Filterable.passThrough(page));
                }
            } else {
                // Пустая заметка: одна пустая страница, чтобы книга открылась для редактирования
                pagesList.add(Filterable.passThrough(""));
            }

            WritableBookContent bookContent = new WritableBookContent(pagesList);
            nms.set(DataComponents.WRITABLE_BOOK_CONTENT, bookContent);
            book = CraftItemStack.asBukkitCopy(nms);
        } catch (Exception e) {
            // Fallback: если Data Component API не сработал — открываем пустую книгу
            // (контент сохранится в БД при Done и будет виден в главном GUI через lore)
        }

        // В Paper 26.x (1.21.4+):
        // - player.openBook() требует WRITTEN_BOOK, не работает для WRITABLE_BOOK
        // - NMS методы openItemGui/openBook могли измениться
        // - Правильный способ: временно поместить книгу в руку игроку
        //   и отправить ClientboundOpenBookPacket на плеер

        // ⚠ НЕ восстанавливаем старый предмет сразу — он нужен в руке,
        // чтобы когда игрок нажмёт "Done", сервер нашёл книгу в слоте main hand
        // и вызвал PlayerEditBookEvent. Иначе сервер не найдёт книгу и
        // заметка не сохранится (ServerboundEditBookPacket будет проигнорирован).
        // Старый предмет восстановится в onBookEdit (или onPlayerQuit для очистки).

        // Если был предыдущий pending (игрок вышел из книги через Escape
        // без Done), восстанавливаем его прежде чем перезаписать — иначе
        // оригинальный предмет будет безвозвратно потерян.
        // ВНИМАНИЕ: не вызываем restorePending() — он чистит editingSlots
        // и transitioningToBook, которые уже установлены для текущего открытия!
        ItemStack oldPending = pendingRestores.remove(uuid);
        if (oldPending != null && player.isOnline()) {
            player.getInventory().setItemInMainHand(oldPending);
        }

        ItemStack oldMainHand = player.getInventory().getItemInMainHand();
        pendingRestores.put(uuid, oldMainHand);

        try {
            // Кладём книгу в main hand
            player.getInventory().setItemInMainHand(book);

            // Способ 1: ClientboundOpenBookPacket (Paper 26.x / 1.21.4+)
            if (openBookViaPacket(player)) {
                return;
            }

            // Способ 2: NMS openItemGui/openBook через reflection (старые версии)
            if (openBookViaNmsReflection(player, book)) {
                return;
            }

            // Способ 3: Paper API fallback (совсем старые версии, где это работало)
            try {
                player.openBook(book);
            } catch (Exception ignored) {
                // Ни один способ не сработал — чистим состояние
                restorePending(player, uuid);
            }

        } catch (Exception e) {
            // При любой ошибке чистим состояние
            restorePending(player, uuid);
            try {
                player.openBook(book);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Восстанавливает старый предмет в руку и чистит pending-состояние.
     */
    static void restorePending(Player player, UUID uuid) {
        ItemStack old = pendingRestores.remove(uuid);
        if (old != null && player.isOnline()) {
            player.getInventory().setItemInMainHand(old);
        }
        editingSlots.remove(uuid);
        transitioningToBook.remove(uuid);
    }

    /**
     * Способ 1: ClientboundOpenBookPacket (Paper 26.x / 1.21.4+).
     * Книга уже должна быть в руке игрока.
     */
    private static boolean openBookViaPacket(Player player) {
        try {
            ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
            serverPlayer.connection.send(new ClientboundOpenBookPacket(InteractionHand.MAIN_HAND));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Способ 2: NMS openItemGui / openBook через reflection.
     */
    private static boolean openBookViaNmsReflection(Player player, ItemStack book) {
        try {
            CraftPlayer craftPlayer = (CraftPlayer) player;
            net.minecraft.world.item.ItemStack nmsStack = CraftItemStack.asNMSCopy(book);
            Object handle = craftPlayer.getHandle();

            for (String methodName : new String[]{"openItemGui", "openBook"}) {
                try {
                    java.lang.reflect.Method method = handle.getClass()
                        .getMethod(methodName, net.minecraft.world.item.ItemStack.class, InteractionHand.class);
                    method.invoke(handle, nmsStack, InteractionHand.MAIN_HAND);
                    return true;
                } catch (NoSuchMethodException ignored) {
                    // Method doesn't exist in this version, try next
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    // =========================
    // HELPERS
    // =========================
    static List<String> splitPages(String text) {
        List<String> pages = new ArrayList<>();
        if (text == null || text.isEmpty()) return pages;

        int maxPerPage = 200;
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxPerPage, text.length());
            if (end < text.length()) {
                int lastBreak = -1;
                for (int i = end; i > start; i--) {
                    char c = text.charAt(i);
                    if (c == '\n' || c == ' ') {
                        lastBreak = i;
                        break;
                    }
                }
                if (lastBreak > start) {
                    end = lastBreak + 1;
                }
            }
            pages.add(text.substring(start, end));
            start = end;
        }
        return pages;
    }

    static String joinPages(List<String> pages) {
        if (pages == null || pages.isEmpty()) return "";
        return String.join("", pages);
    }
}
