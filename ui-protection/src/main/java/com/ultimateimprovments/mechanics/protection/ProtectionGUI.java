package com.ultimateimprovments.mechanics.protection;

import com.ultimateimprovments.core.Keys;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * «Protection Block» GUI (chest-sized, custom title).
 * <p>
 * Mini scenarios:
 * <ol>
 *   <li>{@link #openMainMenu} — the main menu</li>
 *   <li>{@link #openWhitelistMenu} — whitelist management</li>
 *   <li>{@link #openAddPlayerMenu} — add a player (chat input)</li>
 * </ol>
 * <p>
 * Every visible text comes from {@code messages.protection.gui.*}
 * (RU/EN sections of {@code configs/UI-Protection.toml}) — before, the whole menu
 * was hardcoded Russian and ignored the message config entirely.
 * <p>
 * All GUI marker items carry the PDC tag {@link Keys#PROTECTION_GUI},
 * so they can't be stolen via inventory-move.
 */
public final class ProtectionGUI {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public static final String MAIN_TITLE_KEY       = "gui.main_title";
    public static final String WHITELIST_TITLE_KEY  = "gui.whitelist_title";
    public static final String ADDPLAYER_TITLE_KEY  = "gui.addplayer_title";

    private static final int SLOT_BACK = 0;
    private static final int SLOT_WHITELIST = 11;
    private static final int SLOT_RADIUS = 13;
    private static final int SLOT_REPAIR = 15;
    private static final int SLOT_TOGGLE = 22;

    private ProtectionGUI() {}

    // =========================
    // MESSAGE HELPERS
    // =========================
    /** Resolves one message line + placeholder pairs. */
    private static String msg(String path, String def, String... placeholders) {
        return applyPlaceholders(ProtectionConfig.getMessage(path, def), placeholders);
    }

    /** Resolves a message list (multi-line lore) + placeholder pairs. */
    private static List<String> msgList(String path, List<String> def, String... placeholders) {
        List<String> raw = ProtectionConfig.getMessageList(path, def);
        List<String> out = new ArrayList<>(raw.size());
        for (String line : raw) out.add(applyPlaceholders(line, placeholders));
        return out;
    }

    private static String applyPlaceholders(String text, String... placeholders) {
        if (text == null) return "";
        String result = text;
        if (placeholders != null) {
            for (int i = 0; i + 1 < placeholders.length; i += 2) {
                result = result.replace(placeholders[i], placeholders[i + 1]);
            }
        }
        return result;
    }

    // =========================
    // HOLDER — stores ProtectionBlock + menu type
    // =========================
    public static class MenuHolder implements InventoryHolder {
        private final ProtectionBlock block;
        private final MenuType type;
        public MenuHolder(ProtectionBlock block, MenuType type) {
            this.block = block;
            this.type = type;
        }
        public ProtectionBlock block() { return block; }
        public MenuType type() { return type; }
        @Override public Inventory getInventory() { return null; }
    }

    public enum MenuType { MAIN, WHITELIST, ADD_PLAYER }

    // =========================
    // MAIN MENU
    // =========================
    public static void openMainMenu(Player player, ProtectionBlock block) {
        String titleMM = ProtectionConfig.getMessage(MAIN_TITLE_KEY,
                "<white>Блок защиты — GUI</white>");
        Inventory inv = Bukkit.createInventory(new MenuHolder(block, MenuType.MAIN), 27,
                MM.deserialize(titleMM));

        // Back / Decoration borders
        inv.setItem(SLOT_BACK, deco(Material.ARROW,
                msg("gui.back", "<gray>← Назад</gray>")));

        // Whitelist button
        int count = block.getWhitelist().size();
        inv.setItem(SLOT_WHITELIST, button(Material.PLAYER_HEAD,
                msg("gui.whitelist_name", "<gold>Whitelist игроков</gold>"),
                msgList("gui.whitelist_lore", List.of(
                        "<gray>Кто может открыть этот блок</gray>",
                        "<gray>и взаимодействовать с зоной.</gray>",
                        "",
                        "<white>Игроков:</white> <gold>%count%</gold>"),
                        "%count%", String.valueOf(count))
        ));

        // Radius button
        inv.setItem(SLOT_RADIUS, button(Material.ENDER_PEARL,
                msg("gui.radius_name", "<aqua>Радиус: </aqua><white>%radius%</white>",
                        "%radius%", String.valueOf(block.getRadius())),
                msgList("gui.radius_lore", List.of(
                        "<gray>+1 блок за клик.</gray>",
                        "<gray>Стоимость в очках × %growth% каждый раз.</gray>",
                        "",
                        "<white>Стоимость:</white> <gold>%cost%</gold>",
                        "<white>Доступно очков:</white> <gold>%points%</gold>",
                        "<white>Макс. радиус:</white> <aqua>%max%</aqua>"),
                        "%cost%", ProtectionFormat.points(block.getRadiusUpgradeCost()),
                        "%points%", ProtectionFormat.points(block.getPoints()),
                        "%max%", String.valueOf(ProtectionConfig.getMaxRadius()),
                        "%growth%", String.valueOf(ProtectionConfig.getPointsGrowthMultiplier()),
                        "%radius%", String.valueOf(block.getRadius()))
        ));

        // Repair button
        inv.setItem(SLOT_REPAIR, button(Material.ANVIL,
                msg("gui.repair_name", "<green>Целостность: </green><white>%integrity%</white>",
                        "%integrity%", String.format("%.1f%%", block.getIntegrity())),
                msgList("gui.repair_lore", List.of(
                        "<gray>+1% за клик.</gray>",
                        "<gray>Стоимость в очках × %growth% каждый раз.</gray>",
                        "",
                        "<white>Стоимость:</white> <gold>%cost%</gold>",
                        "<white>Доступно очков:</white> <gold>%points%</gold>"),
                        "%cost%", ProtectionFormat.points(block.getRepairCost()),
                        "%growth%", String.valueOf(ProtectionConfig.getPointsGrowthMultiplier()),
                        "%points%", ProtectionFormat.points(block.getPoints()),
                        "%integrity%", String.format("%.1f%%", block.getIntegrity()))
        ));

        // Toggle on/off
        String toggleName = block.isEnabled()
                ? msg("gui.toggle_on", "<green>Блок активен — выключить</green>")
                : msg("gui.toggle_off", "<red>Блок выключен — включить</red>");
        List<String> toggleLore = block.isEnabled()
                ? msgList("gui.toggle_lore_on", List.of("<gray>Защита территории вкл.</gray>"))
                : msgList("gui.toggle_lore_off", List.of(
                        "<gray>Защита территории выкл.</gray>",
                        "<dark_gray>Включите, чтобы блок начал работать.</dark_gray>"));
        inv.setItem(SLOT_TOGGLE, button(Material.REDSTONE_TORCH, toggleName, toggleLore));

        // Fill unused slots with gray glass
        fillEmpty(inv, Material.GRAY_STAINED_GLASS_PANE);

        player.openInventory(inv);
    }

    // =========================
    // WHITELIST MENU
    // =========================
    public static void openWhitelistMenu(Player player, ProtectionBlock block) {
        String titleMM = ProtectionConfig.getMessage(WHITELIST_TITLE_KEY,
                "<white>Блок защиты — Whitelist</white>");
        Inventory inv = Bukkit.createInventory(new MenuHolder(block, MenuType.WHITELIST), 54,
                MM.deserialize(titleMM));

        inv.setItem(0, deco(Material.ARROW,
                msg("gui.back_to_main", "<gray>← Назад к меню</gray>")));
        inv.setItem(53, button(Material.LIME_DYE,
                msg("gui.add_player_name", "<green>Добавить игрока</green>"),
                msgList("gui.add_player_lore", List.of(
                        "<gray>Откроет форму ввода ника.</gray>"))));

        // Fill whitelist heads
        int slot = 10;
        for (UUID pid : block.getWhitelist()) {
            if (slot >= 44) break;
            OfflinePlayer op = Bukkit.getOfflinePlayer(pid);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            PlayerProfile profile = op.getName() != null
                    ? Bukkit.createProfile(pid, op.getName())
                    : Bukkit.createProfile(pid);
            head.editMeta(SkullMeta.class, meta -> {
                meta.setPlayerProfile(profile);
                meta.displayName(MM.deserialize("<yellow>"
                        + (op.getName() != null ? op.getName() : pid.toString()) + "</yellow>"));
                List<Component> lore = new ArrayList<>();
                for (String line : msgList("gui.remove_hint",
                        List.of("<gray>Кликните, чтобы удалить.</gray>"))) {
                    lore.add(MM.deserialize(line));
                }
                meta.lore(lore);
                meta.getPersistentDataContainer().set(
                        Keys.PROTECTION_GUI, PersistentDataType.STRING, "wh:" + pid.toString());
            });
            inv.setItem(slot, head);
            slot++;
            if (slot % 9 == 8) slot += 2; // skip the border
        }

        fillEmpty(inv, Material.BLACK_STAINED_GLASS_PANE);
        player.openInventory(inv);
    }

    // =========================
    // ADD PLAYER FORM (chat input)
    // <p>
    // Map is ConcurrentHashMap because it is read/written from the
    // netty thread (AsyncChatEvent) AND from the main thread
    // (openAddPlayerMenu / cancelAwaiting / quit cleanup) — HashMap would race
    // and throw ConcurrentModificationException under load.
    // =========================
    private static final Map<UUID, ProtectionBlock> awaitingPlayerName = new ConcurrentHashMap<>();

    public static void openAddPlayerMenu(Player player, ProtectionBlock block) {
        awaitingPlayerName.put(player.getUniqueId(), block);
        player.closeInventory();
        player.sendMessage("");
        player.sendMessage(MessageUtil.parse(
                msg("gui.addplayer_header", "<dark_gray>╔ <gold>✦ <bold>Добавить игрока<reset> <dark_gray>╗")));
        player.sendMessage(MessageUtil.parse(
                msg("gui.addplayer_hint", "<gray>Введите ник игрока в чат.")));
        player.sendMessage(MessageUtil.parse(
                msg("gui.addplayer_cancel_hint", "<gray>Или напишите <red>cancel<gray> для отмены.")));
        player.sendMessage("");
    }

    /**
     * Atomically fetches and removes the awaiting block entry.
     * Returns the block, or null if the player isn't awaiting.
     */
    public static ProtectionBlock consumeAwaitingPlayerName(Player player) {
        return awaitingPlayerName.remove(player.getUniqueId());
    }

    public static void cancelAwaiting(Player player) {
        awaitingPlayerName.remove(player.getUniqueId());
    }

    /** Drops the pending chat-input state of a player (logout / reload). */
    public static void clearAwaiting() {
        awaitingPlayerName.clear();
    }

    // =========================
    // ITEM HELPERS
    // =========================
    private static ItemStack deco(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            meta.displayName(MM.deserialize(name));
            meta.getPersistentDataContainer().set(
                    Keys.PROTECTION_GUI, PersistentDataType.STRING, "deco");
        });
        return stack;
    }

    private static ItemStack button(Material material, String name, List<String> loreLinesMini) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(MM.deserialize(name));
            List<Component> lore = new ArrayList<>();
            for (String s : loreLinesMini) lore.add(MM.deserialize(s));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(
                    Keys.PROTECTION_GUI, PersistentDataType.STRING, "btn");
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static void fillEmpty(Inventory inv, Material material) {
        ItemStack filler = new ItemStack(material);
        filler.editMeta(meta -> {
            meta.displayName(Component.text(" "));
            meta.getPersistentDataContainer().set(
                    Keys.PROTECTION_GUI, PersistentDataType.STRING, "filler");
        });
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, filler);
        }
    }

    // =========================
    // LISTENER (single instance, registered by ProtectionModule)
    // =========================
    public static class GUIListener implements Listener {

        private final ProtectionManager manager;

        public GUIListener(ProtectionManager manager, JavaPlugin holder) {
            this.manager = manager;
            JavaPlugin plugin = holder != null ? holder : Main.getInstance();
            // Registered on the addon plugin so /ui reload unregisters it.
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onClick(InventoryClickEvent e) {
            if (!(e.getWhoClicked() instanceof Player player)) return;
            if (!(e.getInventory().getHolder() instanceof MenuHolder holder)) return;

            // 🛡 The GUI owns every slot of the top inventory. Cancel clicks in it
            // unconditionally, and cancel shift-clicks / number-key swaps coming from
            // the player's own inventory — otherwise items could be inserted into the
            // menu and vanish when it closes (dupe/loss hole).
            boolean clickedTop = e.getClickedInventory() == e.getInventory();
            boolean shiftIntoTop = !clickedTop
                    && (e.isShiftClick() || e.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY
                        || e.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND);
            if (clickedTop || shiftIntoTop || e.getClick() == org.bukkit.event.inventory.ClickType.DOUBLE_CLICK) {
                e.setCancelled(true);
            }

            ProtectionBlock pb = holder.block();
            if (pb == null) return;
            if (!clickedTop) return; // player's own inventory — nothing else to do

            int slot = e.getRawSlot();
            if (slot < 0 || slot >= e.getInventory().getSize()) return;

            player.setItemOnCursor(null);

            switch (holder.type()) {
                case MAIN -> handleMainClick(player, pb, slot);
                case WHITELIST -> handleWhitelistClick(player, pb, slot, e);
                case ADD_PLAYER -> { /* not used (chat input) */ }
            }
        }

        /** Left the server while typing a nickname — don't keep the block reference forever. */
        @EventHandler(priority = EventPriority.MONITOR)
        public void onQuit(PlayerQuitEvent e) {
            cancelAwaiting(e.getPlayer());
        }

        // =========================
        // MAIN MENU HANDLER
        // =========================
        private void handleMainClick(Player player, ProtectionBlock pb, int slot) {
            if (slot == SLOT_BACK) {
                player.closeInventory();
                return;
            }
            if (slot == SLOT_WHITELIST) {
                openWhitelistMenu(player, pb);
                return;
            }
            if (slot == SLOT_RADIUS) {
                handleRadiusUpgrade(player, pb);
                openMainMenu(player, pb);
                return;
            }
            if (slot == SLOT_REPAIR) {
                handleRepair(player, pb);
                openMainMenu(player, pb);
                return;
            }
            if (slot == SLOT_TOGGLE) {
                toggleBlock(player, pb);
                openMainMenu(player, pb);
            }
        }

        // =========================
        // WHITELIST MENU HANDLER
        // =========================
        private void handleWhitelistClick(Player player, ProtectionBlock pb, int slot, InventoryClickEvent e) {
            if (slot == 0) {
                openMainMenu(player, pb);
                return;
            }
            if (slot == 53) {
                openAddPlayerMenu(player, pb);
                return;
            }
            // Otherwise it's a whitelist head
            ItemStack headStack = e.getCurrentItem();
            if (headStack == null || headStack.getType() != Material.PLAYER_HEAD) return;
            ItemMeta meta = headStack.getItemMeta();
            if (meta == null) return;
            String tag = meta.getPersistentDataContainer().get(
                    Keys.PROTECTION_GUI, PersistentDataType.STRING);
            if (tag == null || !tag.startsWith("wh:")) return;
            String pidStr = tag.substring(3);
            try {
                UUID pid = UUID.fromString(pidStr);
                if (pid.equals(pb.getOwner())) {
                    // Removing the owner would lock the block for good — refuse.
                    player.sendMessage(MessageUtil.parse(ProtectionConfig.getMessage(
                            "cant_remove_owner",
                            "<red>Нельзя удалить владельца из whitelist.</red>")));
                    return;
                }
                pb.removeFromWhitelist(pid);
                manager.saveWhitelistToDb(pb);
                openWhitelistMenu(player, pb);
                player.sendMessage(MessageUtil.parse(ProtectionConfig.getMessage(
                        "whitelist_removed", "<yellow>Игрок удалён из whitelist.</yellow>")));
            } catch (Exception ignored) {
                // malformed UUID in the PDC — nothing to do
            }
        }

        // =========================
        // RADIUS UPGRADE
        // =========================
        private void handleRadiusUpgrade(Player player, ProtectionBlock pb) {
            double cost = pb.getRadiusUpgradeCost();
            if (pb.getPoints() < cost) {
                player.sendMessage(MessageUtil.parse(ProtectionConfig.getMessage(
                        "not_enough_points",
                        "<red>Недостаточно очков! Нужно: </red><gold>%cost%</gold>")
                        .replace("%cost%", ProtectionFormat.points(cost))));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
                return;
            }
            int maxR = ProtectionConfig.getMaxRadius();
            if (pb.getRadius() >= maxR) {
                player.sendMessage(MessageUtil.parse(ProtectionConfig.getMessage(
                        "radius_max",
                        "<yellow>Радиус уже максимальный: </yellow>" + maxR)));
                return;
            }
            pb.setPoints(pb.getPoints() - cost);
            pb.setRadius(Math.min(maxR, pb.getRadius() + 1));
            pb.setRadiusUpgradeCount(pb.getRadiusUpgradeCount() + 1);
            ProtectionDatabase.saveBlock(pb);
            player.sendMessage(MessageUtil.parse(ProtectionConfig.getMessage(
                    "radius_upgraded",
                    "<green>✔</green> <white>Радиус увеличен до </white><aqua>%r%</aqua>")
                    .replace("%r%", String.valueOf(pb.getRadius()))));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.5f);
        }

        // =========================
        // REPAIR
        // =========================
        private void handleRepair(Player player, ProtectionBlock pb) {
            double cost = pb.getRepairCost();
            if (pb.getPoints() < cost) {
                player.sendMessage(MessageUtil.parse(ProtectionConfig.getMessage(
                        "not_enough_points",
                        "<red>Недостаточно очков! Нужно: </red><gold>%cost%</gold>")
                        .replace("%cost%", ProtectionFormat.points(cost))));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
                return;
            }
            if (pb.getIntegrity() >= 100.0) {
                player.sendMessage(MessageUtil.parse(ProtectionConfig.getMessage(
                        "integrity_full",
                        "<yellow>Целостность уже максимальная.</yellow>")));
                return;
            }
            pb.setPoints(pb.getPoints() - cost);
            pb.setIntegrity(pb.getIntegrity() + 1.0);
            pb.setRepairCount(pb.getRepairCount() + 1);
            ProtectionDatabase.saveBlock(pb);
            player.sendMessage(MessageUtil.parse(ProtectionConfig.getMessage(
                    "repaired",
                    "<green>✔</green> <white>Целостность:</white> <green>%integrity%</green>")
                    .replace("%integrity%", String.format("%.1f%%", pb.getIntegrity()))));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.4f, 1.4f);
        }

        // =========================
        // TOGGLE ON/OFF
        // =========================
        private void toggleBlock(Player player, ProtectionBlock pb) {
            pb.setEnabled(!pb.isEnabled());
            ProtectionDatabase.saveBlock(pb);
            player.sendMessage(MessageUtil.parse(ProtectionConfig.getMessage(
                    pb.isEnabled() ? "block_enabled" : "block_disabled",
                    pb.isEnabled()
                            ? "<green>✔</green> <white>Блок защиты ВКЛЮЧЁН!</white>"
                            : "<red>❌</red> <white>Блок защиты ВЫКЛЮЧЕН.</white>")));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
        }
    }
}
