package com.mcplugin.command;

import com.mcplugin.mechanics.features.integrity.IntegrityManager;
import com.mcplugin.core.Keys;
import com.mcplugin.util.MessageUtil;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Handles /mp item command — item integrity management.
 */
public class ItemCommand {

    public static boolean execute(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Usage: </red><white>/mp item int <set|add|list> [value]</white>"));
            return true;
        }

        if (args[1].equalsIgnoreCase("int")) {
            return handleIntegrity(player, args);
        }

        player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Unknown subcommand: </red><white>" + args[1] + "</white>"));
        player.sendMessage(MessageUtil.parse("<red>Usage: </red><white>/mp item int set|add|list</white>"));
        return true;
    }

    private static boolean handleIntegrity(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Usage: </red><white>/mp item int set|add|list</white>"));
            return true;
        }

        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem == null || heldItem.getType() == Material.AIR) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>You must hold an item in your hand!</red>"));
            return true;
        }

        if (!IntegrityManager.hasIntegrity(heldItem)) {
            IntegrityManager.ensureInitialized(heldItem);
            if (!IntegrityManager.hasIntegrity(heldItem)) {
                player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>This item does not have an integrity system!</red>"));
                return true;
            }
        }

        switch (args[2].toLowerCase()) {
            case "list" -> handleList(player, heldItem);
            case "set" -> handleSet(player, heldItem, args);
            case "add" -> handleAdd(player, heldItem, args);
            case "unbreakable" -> handleUnbreakable(player, heldItem, args);
            default -> {
                player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Unknown subcommand: </red><white>" + args[2] + "</white>"));
                player.sendMessage(MessageUtil.parse("<red>Usage: </red><white>/mp item int set|add|list</white>"));
            }
        }
        return true;
    }

    private static void handleList(Player player, ItemStack heldItem) {
        double current = IntegrityManager.getCurrentIntegrity(heldItem);
        double max = IntegrityManager.getMaxIntegrity(heldItem);
        double pctCurrent = (current / max) * 100.0;
        String itemName = heldItem.hasItemMeta() && heldItem.getItemMeta().hasDisplayName()
                ? heldItem.getItemMeta().getDisplayName()
                : heldItem.getType().name().toLowerCase().replace("_", " ");
        if (itemName.length() > 0) {
            itemName = itemName.substring(0, 1).toUpperCase() + itemName.substring(1);
        }
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        player.sendMessage(MessageUtil.parse("<gold>  ✦ </gold><white>Item Integrity Information</white>"));
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        player.sendMessage(MessageUtil.parse("<gray>Item: </gray><white>" + itemName + "</white>"));
        player.sendMessage(MessageUtil.parse("<gray>Current: </gray><green>" + IntegrityManager.formatPercent(pctCurrent) + "%</green>"));
        player.sendMessage(MessageUtil.parse("<gray>Max:    </gray><green>100.000%</green>"));
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
    }

    private static void handleSet(Player player, ItemStack heldItem, String[] args) {
        if (args.length < 4) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Usage: </red><white>/mp item int set </white><gray><value></gray>"));
            return;
        }
        try {
            double value = Double.parseDouble(args[3]);
            if (value < 0 || value > 100) {
                player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Value must be between 0 and 100!</red>"));
                return;
            }
            IntegrityManager.setCurrentIntegrity(heldItem, value);
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Item integrity set to </white><yellow>" + IntegrityManager.formatPercent(value) + "%</yellow>"));
        } catch (NumberFormatException e) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Invalid number format! Use a decimal number (e.g.: 75.500)</red>"));
        }
    }

    private static void handleUnbreakable(Player player, ItemStack heldItem, String[] args) {
        boolean setUnbreakable;
        if (args.length >= 4) {
            setUnbreakable = Boolean.parseBoolean(args[3]);
        } else {
            // Toggle: если есть тег — снять, если нет — добавить
            ItemMeta meta = heldItem.getItemMeta();
            if (meta == null) {
                player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Cannot modify item meta!</red>"));
                return;
            }
            boolean hasTag = meta.getPersistentDataContainer()
                    .has(Keys.INTEGRITY_UNBREAKABLE, PersistentDataType.BYTE);
            setUnbreakable = !hasTag;
        }

        ItemMeta meta = heldItem.getItemMeta();
        if (meta == null) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Cannot modify item meta!</red>"));
            return;
        }

        if (setUnbreakable) {
            meta.getPersistentDataContainer().set(Keys.INTEGRITY_UNBREAKABLE, PersistentDataType.BYTE, (byte) 1);
            heldItem.setItemMeta(meta);
            // Force 100% integrity and update lore
            IntegrityManager.setCurrentIntegrity(heldItem, 100.0);
            IntegrityManager.updateItemLore(heldItem);
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Item is now </white><aqua>Unbreakable</aqua><white>! Integrity locked at 100%.</white>"));
        } else {
            meta.getPersistentDataContainer().remove(Keys.INTEGRITY_UNBREAKABLE);
            heldItem.setItemMeta(meta);
            IntegrityManager.updateItemLore(heldItem);
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Item is no longer </white><aqua>Unbreakable</aqua><white>.</white>"));
        }
    }

    private static void handleAdd(Player player, ItemStack heldItem, String[] args) {
        if (args.length < 4) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Usage: </red><white>/mp item int add </white><gray><value></gray>"));
            return;
        }
        try {
            double value = Double.parseDouble(args[3]);
            if (value <= 0) {
                player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Value must be greater than 0!</red>"));
                return;
            }
            double current = IntegrityManager.getCurrentIntegrity(heldItem);
            double newVal = Math.min(100.0, current + value);
            IntegrityManager.setCurrentIntegrity(heldItem, newVal);
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Added </white><yellow>" + IntegrityManager.formatPercent(value) + "%</yellow><white>. Current: </white><yellow>" + IntegrityManager.formatPercent(newVal) + "%</yellow>"));
        } catch (NumberFormatException e) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Invalid number format! Use a decimal number (e.g.: 25.500)</red>"));
        }
    }
}
