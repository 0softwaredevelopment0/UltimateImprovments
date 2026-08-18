package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.PlayerDataIO;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.nbt.CompoundTag;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Offline inventory editor — opens a player's {@code <uuid>.dat} inventory in a GUI.
 *
 * <p>Online players are handled by {@link InvseeCommand}; this class covers offline
 * players. The GUI is a snapshot: on close (when the viewer has
 * {@code ui.command.inv.edit}) the changes are written back to the .dat file, which is
 * backed up to {@code <uuid>-backup.dat} first. Without the edit permission the GUI is
 * read-only.</p>
 */
public final class OfflineInvEditor implements Listener {

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static boolean registered = false;

    private static final class Session {
        final UUID viewerId;
        final UUID targetUuid;
        final String targetName;
        final boolean ender;
        final boolean edit;
        final Inventory gui;
        final File file;

        Session(UUID viewerId, UUID targetUuid, String targetName, boolean ender,
                boolean edit, Inventory gui, File file) {
            this.viewerId = viewerId;
            this.targetUuid = targetUuid;
            this.targetName = targetName;
            this.ender = ender;
            this.edit = edit;
            this.gui = gui;
            this.file = file;
        }
    }

    private OfflineInvEditor() {}

    private static void ensureRegistered() {
        if (!registered) {
            Bukkit.getPluginManager().registerEvents(new OfflineInvEditor(), Main.getInstance());
            registered = true;
        }
    }

    /** Returns true if a playerdata file exists for this UUID (any world). */
    public static boolean hasDataFile(UUID uuid) {
        File file = PlayerDataIO.locate(uuid);
        return file != null && file.isFile();
    }

    // ═══════════════════════════════════════════════════════════════
    // OPEN
    // ═══════════════════════════════════════════════════════════════

    public static void open(Player viewer, UUID targetUuid, String targetName, boolean ender) {
        File file = PlayerDataIO.locate(targetUuid);
        if (file == null || !file.isFile()) {
            viewer.sendMessage(MessageUtil.parse(
                    "<red>❌ No player data file found for </red><yellow>" + targetName + "</yellow><red>.</red>"));
            return;
        }

        CompoundTag root;
        try {
            root = PlayerDataIO.readData(file);
        } catch (Exception e) {
            viewer.sendMessage(MessageUtil.parse(
                    "<red>❌ Failed to read player data for </red><yellow>" + targetName + "</yellow><red>.</red>"));
            ConsoleLogger.warn("[Inv] Failed to read " + file.getName() + ": " + e.getMessage());
            return;
        }

        boolean edit = viewer.hasPermission("ui.command.inv.edit");
        Inventory gui = buildGui(root, targetUuid, targetName, ender, edit);

        endSession(viewer.getUniqueId());
        SESSIONS.put(viewer.getUniqueId(),
                new Session(viewer.getUniqueId(), targetUuid, targetName, ender, edit, gui, file));
        viewer.openInventory(gui);
        ensureRegistered();

        viewer.sendMessage(MessageUtil.parse(
                (edit ? "<green>✏ Editing</green> " : "<gray>👁 Viewing</gray> ")
                + "<white>offline " + (ender ? "ender chest" : "inventory")
                + " of <yellow>" + targetName + "</yellow>"
                + (edit ? " <gray>— changes save on close.</gray>" : " <gray>— read-only.</gray>")));
    }

    // ═══════════════════════════════════════════════════════════════
    // GUI BUILD
    // ═══════════════════════════════════════════════════════════════

    private static Inventory buildGui(CompoundTag root, UUID targetUuid, String targetName,
                                      boolean ender, boolean edit) {
        String title = MessageUtil.legacy("<dark_gray>" + targetName
                + (ender ? "'s ender chest (offline)" : "'s inventory (offline)") + "</dark_gray>");
        Inventory gui = Bukkit.createInventory(null, 45, title);

        if (ender) {
            org.bukkit.inventory.ItemStack[] enderItems = PlayerDataIO.readEnder(root);
            for (int i = 0; i < 27; i++) {
                gui.setItem(i, cloneOrNull(enderItems[i]));
            }
            for (int i = 27; i < 44; i++) {
                gui.setItem(i, glass());
            }
            gui.setItem(44, info(targetUuid, targetName, true, edit));
        } else {
            org.bukkit.inventory.ItemStack[] main = PlayerDataIO.readMain(root);
            PlayerDataIO.EquipmentSnapshot eq = PlayerDataIO.readEquipment(root);

            // 0-8 hotbar, 9-35 main inventory
            for (int i = 0; i < 36; i++) {
                gui.setItem(i, cloneOrNull(main[i]));
            }
            // 36 boots, 37 leggings, 38 chestplate, 39 helmet, 40 offhand
            gui.setItem(36, cloneOrNull(eq.boots));
            gui.setItem(37, cloneOrNull(eq.leggings));
            gui.setItem(38, cloneOrNull(eq.chestplate));
            gui.setItem(39, cloneOrNull(eq.helmet));
            gui.setItem(40, cloneOrNull(eq.offhand));
            gui.setItem(41, info(targetUuid, targetName, false, edit));
            gui.setItem(42, glass());
            gui.setItem(43, glass());
            gui.setItem(44, glass());
        }

        return gui;
    }

    private static ItemStack glass() {
        ItemStack glass = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.displayName(MessageUtil.parse("<reset>").decoration(TextDecoration.ITALIC, false));
        meta.setHideTooltip(true);
        glass.setItemMeta(meta);
        return glass;
    }

    private static ItemStack info(UUID uuid, String name, boolean ender, boolean edit) {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        meta.displayName(MessageUtil.parse("<gold>✦ " + name + " (offline)</gold>")
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(java.util.List.of(
                MessageUtil.parse("<gray>UUID: <white>" + uuid + "</white></gray>")
                        .decoration(TextDecoration.ITALIC, false),
                MessageUtil.parse("<gray>Mode: <white>" + (ender ? "ender chest" : "inventory + armor")
                        + "</white></gray>")
                        .decoration(TextDecoration.ITALIC, false),
                MessageUtil.parse(edit
                        ? "<green>Editable — changes save on close</green>"
                        : "<gray>Read-only</gray>")
                        .decoration(TextDecoration.ITALIC, false)));
        paper.setItemMeta(meta);
        return paper;
    }

    // ═══════════════════════════════════════════════════════════════
    // SAVE
    // ═══════════════════════════════════════════════════════════════

    private static void save(Session session, Player viewer) {
        File file = (session.file != null && session.file.isFile())
                ? session.file
                : PlayerDataIO.locate(session.targetUuid);
        if (file == null || !file.isFile()) {
            viewer.sendMessage(MessageUtil.parse(
                    "<red>❌ Player data file not found — nothing saved.</red>"));
            return;
        }

        CompoundTag root;
        try {
            // Re-read so we only touch the inventory fields and never clobber
            // concurrent changes to the rest of the player data.
            root = PlayerDataIO.readData(file);
        } catch (Exception e) {
            viewer.sendMessage(MessageUtil.parse("<red>❌ Failed to read player data — nothing saved.</red>"));
            ConsoleLogger.warn("[Inv] Re-read failed for " + file.getName() + ": " + e.getMessage());
            return;
        }

        try {
            if (session.ender) {
                org.bukkit.inventory.ItemStack[] ender = new org.bukkit.inventory.ItemStack[27];
                for (int i = 0; i < 27; i++) {
                    ender[i] = cloneOrNull(session.gui.getItem(i));
                }
                PlayerDataIO.writeEnder(root, ender);
            } else {
                org.bukkit.inventory.ItemStack[] main = new org.bukkit.inventory.ItemStack[36];
                for (int i = 0; i < 36; i++) {
                    main[i] = cloneOrNull(session.gui.getItem(i));
                }
                PlayerDataIO.writeMain(root, main);

                PlayerDataIO.EquipmentSnapshot eq = new PlayerDataIO.EquipmentSnapshot();
                eq.boots = cloneOrNull(session.gui.getItem(36));
                eq.leggings = cloneOrNull(session.gui.getItem(37));
                eq.chestplate = cloneOrNull(session.gui.getItem(38));
                eq.helmet = cloneOrNull(session.gui.getItem(39));
                eq.offhand = cloneOrNull(session.gui.getItem(40));
                PlayerDataIO.writeEquipment(root, eq);
            }

            PlayerDataIO.writeData(file, root);
            viewer.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Saved offline "
                    + (session.ender ? "ender chest" : "inventory")
                    + " of <yellow>" + session.targetName + "</yellow>.</white>"));
        } catch (Exception e) {
            viewer.sendMessage(MessageUtil.parse("<red>❌ Failed to save player data.</red>"));
            ConsoleLogger.warn("[Inv] Save failed for " + file.getName() + ": " + e.getMessage());
        }
    }

    private static void endSession(UUID viewerId) {
        SESSIONS.remove(viewerId);
    }

    private static org.bukkit.inventory.ItemStack cloneOrNull(org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        return item.clone();
    }

    private static boolean isDecoration(boolean ender, int slot) {
        return ender ? slot >= 27 : slot >= 41;
    }

    // ═══════════════════════════════════════════════════════════════
    // LISTENER
    // ═══════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Session session = SESSIONS.get(player.getUniqueId());
        if (session == null || session.gui != event.getView().getTopInventory()) return;

        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();

        // The viewer's own inventory (bottom) — always allowed.
        if (rawSlot >= topSize) return;

        // Top inventory: decorations are always blocked; without edit permission
        // every top-inventory click is blocked (read-only).
        if (isDecoration(session.ender, rawSlot) || !session.edit) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Session session = SESSIONS.get(player.getUniqueId());
        if (session == null || session.gui != event.getView().getTopInventory()) return;

        int topSize = event.getView().getTopInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot >= topSize) continue; // viewer's own inventory — allowed
            if (isDecoration(session.ender, slot) || !session.edit) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Session session = SESSIONS.remove(player.getUniqueId());
        if (session == null || session.gui != event.getInventory()) return;
        if (!session.edit) return;

        save(session, player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        endSession(event.getPlayer().getUniqueId());
    }
}
