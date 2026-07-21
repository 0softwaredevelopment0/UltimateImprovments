package com.mcplugin.mechanics.protection;

import com.mcplugin.core.Keys;
import com.mcplugin.core.Main;
import com.mcplugin.util.ConsoleLogger;
import com.mcplugin.util.MessageUtil;

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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * GUI «Блока защиты» (chest-sized, custom title).
 * <p>
 * Мини-сценарии:
 * <ol>
 *   <li>{@link #openMainMenu} — главное меню</li>
 *   <li>{@link #openWhitelistMenu} — управление whitelist</li>
 *   <li>{@link #openAddPlayerMenu} — добавить игрока</li>
 * </ol>
 * <p>
 * Все предметы-маркеры GUI имеют PDC tag {@link Keys#PROTECTION_GUI},
 * поэтому их нельзя украсть через inventory-move (см. ниже).
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
    // HOLDER — хранит ProtectionBlock + тип меню
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
        inv.setItem(SLOT_BACK, deco(Material.ARROW, "<gray>← Назад</gray>"));

        // Whitelist button
        int count = block.getWhitelist().size();
        inv.setItem(SLOT_WHITELIST, button(Material.PLAYER_HEAD,
                "<gold>Whitelist игроков</gold>",
                List.of(
                        "<gray>Кто может открыть этот блок</gray>",
                        "<gray>и взаимодействовать с зоной.</gray>",
                        "",
                        "<white>Игроков:</white> <gold>" + count + "</gold>"
                )
        ));

        // Radius button
        inv.setItem(SLOT_RADIUS, button(Material.ENDER_PEARL,
                "<aqua>Радиус: </aqua><white>" + block.getRadius() + "</white>",
                List.of(
                        "<gray>+1 блок за клик.</gray>",
                        "<gray>Стоимость в очках × 2 каждый раз.</gray>",
                        "",
                        "<white>Стоимость:</white> <gold>" + block.getRadiusUpgradeCost() + "</gold>",
                        "<white>Доступно очков:</white> <gold>" + block.getPoints() + "</gold>",
                        "<white>Макс. радиус:</white> <aqua>" + ProtectionConfig.getMaxRadius() + "</aqua>"
                )
        ));

        // Repair button
        inv.setItem(SLOT_REPAIR, button(Material.ANVIL,
                "<green>Целостность: </green><white>"
                        + String.format("%.1f%%", block.getIntegrity()) + "</white>",
                List.of(
                        "<gray>+1% за клик.</gray>",
                        "<gray>Стоимость в очках × 2 каждый раз.</gray>",
                        "",
                        "<white>Стоимость:</white> <gold>" + block.getRepairCost() + "</gold>",
                        "<white>Доступно очков:</white> <gold>" + block.getPoints() + "</gold>"
                )
        ));

        // Toggle on/off
        String toggleName = block.isEnabled()
                ? "<green>Блок активен — выключить</green>"
                : "<red>Блок выключен — включить</red>";
        List<String> toggleLore = block.isEnabled()
                ? List.of("<gray>Защита территории вкл.</gray>")
                : List.of("<gray>Защита территории выкл.</gray>",
                          "<dark_gray>Включите, чтобы блок начал работать.</dark_gray>");
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

        inv.setItem(0, deco(Material.ARROW, "<gray>← Назад к меню</gray>"));
        inv.setItem(53, button(Material.LIME_DYE, "<green>Добавить игрока</green>",
                List.of("<gray>Откроет форму ввода ника.</gray>")));

        // Fill whitelist heads
        int slot = 10;
        for (UUID pid : block.getWhitelist()) {
            if (slot >= 44) break;
            OfflinePlayer op = Bukkit.getOfflinePlayer(pid);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(op);
                meta.displayName(MM.deserialize("<yellow>" + (op.getName() != null ? op.getName() : pid.toString()) + "</yellow>"));
                meta.lore(List.of(
                        MM.deserialize("<gray>Кликните, чтобы удалить.</gray>")));
                meta.getPersistentDataContainer().set(
                        Keys.PROTECTION_GUI, PersistentDataType.STRING, "wh:" + pid.toString());
                head.setItemMeta(meta);
            }
            inv.setItem(slot, head);
            slot++;
            if (slot % 9 == 8) slot += 2; // пропускаем границу
        }

        fillEmpty(inv, Material.BLACK_STAINED_GLASS_PANE);
        player.openInventory(inv);
    }

    // =========================
    // ADD PLAYER FORM (chat input)
    // =========================
    private static final Map<UUID, ProtectionBlock> awaitingPlayerName = new HashMap<>();

    public static void openAddPlayerMenu(Player player, ProtectionBlock block) {
        awaitingPlayerName.put(player.getUniqueId(), block);
        player.closeInventory();
        player.sendMessage("");
        player.sendMessage("§8╔ §6✦ §lДобавить игрока§r §8╗");
        player.sendMessage("§7Введите ник игрока в чат.");
        player.sendMessage("§7Или напишите §ccancel§7 для отмены.");
        player.sendMessage("");
    }

    public static boolean consumeAwaitingPlayerName(Player player) {
        ProtectionBlock b = awaitingPlayerName.remove(player.getUniqueId());
        return b != null;
    }

    public static ProtectionBlock getAwaitingBlock(Player player) {
        return awaitingPlayerName.get(player.getUniqueId());
    }

    public static void cancelAwaiting(Player player) {
        awaitingPlayerName.remove(player.getUniqueId());
    }

    // =========================
    // ITEM HELPERS
    // =========================
    private static ItemStack deco(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(MM.deserialize(name));
            meta.getPersistentDataContainer().set(
                    Keys.PROTECTION_GUI, PersistentDataType.STRING, "deco");
            stack.setItemMeta(meta);
        }
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
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(Component.text(" "));
        meta.getPersistentDataContainer().set(
                Keys.PROTECTION_GUI, PersistentDataType.STRING, "filler");
        filler.setItemMeta(meta);
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, filler);
        }
    }

    // =========================
    // LISTENER (single instance, registered by ProtectionModule)
    // =========================
    public static class GUIListener implements Listener {

        private final ProtectionManager manager;

        public GUIListener(ProtectionManager manager) {
            this.manager = manager;
            Bukkit.getPluginManager().registerEvents(this, Main.getInstance());
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onClick(InventoryClickEvent e) {
            if (!(e.getWhoClicked() instanceof Player player)) return;
            if (!(e.getInventory().getHolder() instanceof MenuHolder holder)) return;

            // Защита от кражи предметов GUI
            ItemStack cursor = e.getCursor();
            ItemStack current = e.getCurrentItem();
            if ((cursor != null && cursor.hasItemMeta()
                    && cursor.getItemMeta().getPersistentDataContainer()
                    .has(Keys.PROTECTION_GUI, PersistentDataType.STRING))
                    || (current != null && current.hasItemMeta()
                    && current.getItemMeta().getPersistentDataContainer()
                    .has(Keys.PROTECTION_GUI, PersistentDataType.STRING))) {
                e.setCancelled(true);
            }

            ProtectionBlock pb = holder.block();
            Inventory clickedInv = e.getClickedInventory();
            if (clickedInv == null) return;

            int slot = e.getRawSlot();
            if (slot < 0 || slot >= e.getInventory().getSize()) return;

            player.setItemOnCursor(null);

            switch (holder.type()) {
                case MAIN -> handleMainClick(player, pb, slot);
                case WHITELIST -> handleWhitelistClick(player, pb, slot, e);
                case ADD_PLAYER -> { /* not used (chat input) */ }
            }
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onClose(InventoryCloseEvent e) {
            // no-op: chat input cancellation handled in command layer
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
                return;
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
                pb.removeFromWhitelist(pid);
                ProtectionDatabase.saveWhitelist(pb);
                openWhitelistMenu(player, pb);
                player.sendMessage(MessageUtil.parse(ProtectionConfig.getMessage(
                        "whitelist_removed", "<yellow>Игрок удалён из whitelist.</yellow>")));
            } catch (Exception ex) { /* ignore */ }
        }

        // =========================
        // RADIUS UPGRADE
        // =========================
        private void handleRadiusUpgrade(Player player, ProtectionBlock pb) {
            int cost = pb.getRadiusUpgradeCost();
            if (pb.getPoints() < cost) {
                player.sendMessage(MessageUtil.parse(ProtectionConfig.getMessage(
                        "not_enough_points",
                        "<red>Недостаточно очков! Нужно: </red><gold>%cost%</gold>")
                        .replace("%cost%", String.valueOf(cost))));
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
            pb.setRadius(pb.getRadius() + 1);
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
            int cost = pb.getRepairCost();
            if (pb.getPoints() < cost) {
                player.sendMessage(MessageUtil.parse(ProtectionConfig.getMessage(
                        "not_enough_points",
                        "<red>Недостаточно очков! Нужно: </red><gold>%cost%</gold>")
                        .replace("%cost%", String.valueOf(cost))));
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
                    "<green>✔</green> <white>Целостность:</white> <green>"
                            + String.format("%.1f%%", pb.getIntegrity()) + "</green>")));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.4f, 1.4f);
        }

        // =========================
        // TOGGLE ON/OFF
        // =========================
        private void toggleBlock(Player player, ProtectionBlock pb) {
            pb.setEnabled(!pb.isEnabled());
            ProtectionDatabase.saveBlock(pb);
            player.sendMessage(MessageUtil.parse(ProtectionConfig.getMessage(
                    pb.isEnabled() ? "block_enabled"
                            : "block_disabled",
                    pb.isEnabled()
                            ? "<green>✔</green> <white>Блок защиты ВКЛЮЧЁН!</white>"
                            : "<red>❌</red> <white>Блок защиты ВЫКЛЮЧЕН.</white>")));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
        }
    }
}
