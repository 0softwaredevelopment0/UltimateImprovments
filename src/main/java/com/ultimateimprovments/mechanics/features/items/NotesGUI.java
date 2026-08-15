package com.ultimateimprovments.mechanics.features.items;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.component.WritableBookContent;

import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.Materials;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Notes GUI: a double chest (54 slots) with note books.
 * Each book = one note, stored in the DB via NotesDatabase.
 */
public class NotesGUI {

    public static final String GUI_TITLE = "Ваши заметки";
    public static final int GUI_SIZE = 54;

    // Players with the notes GUI open
    static final Set<UUID> openPlayers = ConcurrentHashMap.newKeySet();
    // Players currently editing a book (UUID → note number)
    static final Map<UUID, Integer> editingSlots = new ConcurrentHashMap<>();
    // Players transitioning from the main GUI into the book editor
    static final Set<UUID> transitioningToBook = ConcurrentHashMap.newKeySet();
    // Items held before opening the editor — restored after Done/Quit
    static final Map<UUID, ItemStack> pendingRestores = new ConcurrentHashMap<>();

    private NotesGUI() {}

    // =========================
    // OPEN MAIN GUI (54 slots)
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
        String content = NotesDatabase.loadNote(uuid, noteNumber);
        boolean hasText = content != null && !content.isEmpty();

        // Texture: a note with text — a written book, empty — a book and quill.
        // Clicking either still opens the editor (writable_book).
        ItemStack book = new ItemStack(hasText ? Materials.WRITTEN_BOOK : Materials.WRITABLE_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) return book;

        meta.setDisplayName("§fЗаметка #" + noteNumber);

        if (hasText) {
            String preview = content.length() > 30 ? content.substring(0, 30) + "..." : content;
            List<String> lore = new ArrayList<>();
            lore.add("§7" + preview.replace("\n", " "));
            try {
                meta.setLore(lore);
            } catch (Exception ignored) {
                // Paper 1.21.x may restrict lore on WRITABLE_BOOK
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
    // OPEN BOOK EDITOR — via ClientboundOpenBookPacket (1.21.4+)
    //
    // ⚠ In Paper 1.21.x setting pages via BookMeta.setPages() on a WRITABLE_BOOK
    //    can open the book as read-only (completed book).
    //    We use NBT directly to bypass this behavior.
    // =========================
    public static void openBookEditor(Player player, int noteNumber) {
        UUID uuid = player.getUniqueId();
        editingSlots.put(uuid, noteNumber);

        ItemStack book = new ItemStack(Materials.WRITABLE_BOOK);

        // ⚡ In Paper 1.21.4+ BookMeta.setPages() on WRITABLE_BOOK triggers a read-only bug.
        // We use the Data Component API directly via NMS: WritableBookContent + DataComponents.
        String content = NotesDatabase.loadNote(uuid, noteNumber);
        try {
            net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(book);

            List<Filterable<String>> pagesList = new ArrayList<>();
            if (content != null && !content.isEmpty()) {
                for (String page : splitPages(content)) {
                    pagesList.add(Filterable.passThrough(page));
                }
            } else {
                // Empty note: one empty page so the book opens for editing
                pagesList.add(Filterable.passThrough(""));
            }

            WritableBookContent bookContent = new WritableBookContent(pagesList);
            nms.set(DataComponents.WRITABLE_BOOK_CONTENT, bookContent);
            ItemStack bukkitCopy = nmsToBukkit(nms);
            if (bukkitCopy != null) {
                book = bukkitCopy;
            } else {
                ConsoleLogger.warn("[Notes] NMS→Bukkit conversion failed — opening empty editor for note #" + noteNumber);
            }
        } catch (Throwable e) {
            // Fallback: if the Data Component API failed — open an empty book
            // (content saves to the DB on Done and shows in the main GUI lore)
        }

        // In Paper 26.x (1.21.4+):
        // - player.openBook() requires WRITTEN_BOOK, doesn't work for WRITABLE_BOOK
        // - NMS methods openItemGui/openBook may have changed
        // - The correct way: temporarily put the book in the player's hand
        //   and send ClientboundOpenBookPacket to the player

        // ⚠ Do NOT restore the old item right away — it must stay in hand so that
        // when the player clicks "Done", the server finds the book in the main hand
        // slot and fires PlayerEditBookEvent. Otherwise the server won't find the book
        // and the note won't save (ServerboundEditBookPacket gets ignored).
        // The old item gets restored in onBookEdit (or onPlayerQuit for cleanup).

        // If there was a previous pending (the player left the book via Escape
        // without Done), restore it before overwriting — otherwise the
        // original item is lost forever.
        // WARNING: don't call restorePending() — it clears editingSlots
        // and transitioningToBook, which are already set for the current opening!
        ItemStack oldPending = pendingRestores.remove(uuid);
        if (oldPending != null && player.isOnline()) {
            player.getInventory().setItemInMainHand(oldPending);
        }

        ItemStack oldMainHand = player.getInventory().getItemInMainHand();
        pendingRestores.put(uuid, oldMainHand);

        try {
            // Put the book in the main hand
            player.getInventory().setItemInMainHand(book);

            // Method 1: ClientboundOpenBookPacket (Paper 26.x / 1.21.4+)
            if (openBookViaPacket(player)) {
                return;
            }

            // Method 2: NMS openItemGui/openBook via reflection (older versions)
            if (openBookViaNmsReflection(player, book)) {
                return;
            }

            // Method 3: Paper API fallback (very old versions where this worked)
            try {
                player.openBook(book);
            } catch (Exception ignored) {
                // No method worked — clean up the state
                restorePending(player, uuid);
            }

        } catch (Exception e) {
            // On any error clean up the state
            restorePending(player, uuid);
            try {
                player.openBook(book);
            } catch (Exception ignored) {}
        }
    }

    /**
     * NMS → Bukkit conversion. {@code CraftItemStack.asBukkitCopy} became private
     * in Leaf 26.2, so we call it via reflection (a pattern used across the
     * whole plugin). Returns null on any error.
     */
    private static ItemStack nmsToBukkit(net.minecraft.world.item.ItemStack nms) {
        try {
            Method m = CraftItemStack.class.getDeclaredMethod("asBukkitCopy",
                    net.minecraft.world.item.ItemStack.class);
            m.setAccessible(true);
            return (ItemStack) m.invoke(null, nms);
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Restores the old item to hand and clears the pending state.
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
     * Method 1: ClientboundOpenBookPacket (Paper 26.x / 1.21.4+).
     * The book must already be in the player's hand.
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
     * Method 2: NMS openItemGui / openBook via reflection.
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
