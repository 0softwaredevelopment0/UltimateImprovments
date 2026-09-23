package com.ultimateimprovments.command;

import com.ultimateimprovments.mechanics.features.integrity.ItemDurabilityUtil;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

/**
 * Handles /ui item command — vanilla durability management.
 * <p>
 * Values are plain vanilla durability points: the item's real damage
 * component is the single source of truth (the vanilla durability bar is
 * the only visual indicator — no custom lore is written).
 */
public class ItemCommand {

    public static boolean execute(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Usage: </red><white>/ui item dura <info|set|add|unbreakable> [value]</white>"));
            return true;
        }

        if (args[1].equalsIgnoreCase("dura") || args[1].equalsIgnoreCase("int")) {
            return handleDurability(player, args);
        }

        player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Unknown subcommand: </red><white>" + args[1] + "</white>"));
        player.sendMessage(MessageUtil.parse("<red>Usage: </red><white>/ui item dura info|set|add|unbreakable</white>"));
        return true;
    }

    private static boolean handleDurability(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Usage: </red><white>/ui item dura <info|set|add|unbreakable> [value]</white>"));
            return true;
        }

        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem == null || heldItem.getType() == Material.AIR) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>You must hold an item in your hand!</red>"));
            return true;
        }

        if (ItemDurabilityUtil.getMaxDurability(heldItem) <= 0) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>This item has no durability!</red>"));
            return true;
        }

        switch (args[2].toLowerCase()) {
            case "info", "list" -> handleInfo(player, heldItem);
            case "set" -> handleSet(player, heldItem, args);
            case "add" -> handleAdd(player, heldItem, args);
            case "unbreakable" -> handleUnbreakable(player, heldItem, args);
            default -> {
                player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Unknown subcommand: </red><white>" + args[2] + "</white>"));
                player.sendMessage(MessageUtil.parse("<red>Usage: </red><white>/ui item dura info|set|add|unbreakable</white>"));
            }
        }
        return true;
    }

    private static void handleInfo(Player player, ItemStack heldItem) {
        int max = ItemDurabilityUtil.getMaxDurability(heldItem);
        int damage = ItemDurabilityUtil.getVanillaDamage(heldItem);
        String itemName = heldItem.hasItemMeta() && heldItem.getItemMeta().hasDisplayName()
                ? heldItem.getItemMeta().getDisplayName()
                : heldItem.getType().name().toLowerCase().replace("_", " ");
        if (!itemName.isEmpty()) {
            itemName = itemName.substring(0, 1).toUpperCase() + itemName.substring(1);
        }
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        player.sendMessage(MessageUtil.parse("<gold>  ✦ </gold><white>Item Durability Information</white>"));
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        player.sendMessage(MessageUtil.parse("<gray>Item: </gray><white>" + itemName + "</white>"));
        player.sendMessage(MessageUtil.parse("<gray>Remaining: </gray><green>" + (max - damage) + "</green><gray>/" + max + "</gray>"));
        player.sendMessage(MessageUtil.parse("<gray>Unbreakable: </gray><white>" + ItemDurabilityUtil.isUnbreakable(heldItem) + "</white>"));
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
    }

    private static void handleSet(Player player, ItemStack heldItem, String[] args) {
        if (args.length < 4) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Usage: </red><white>/ui item dura set </white><gray><remaining></gray>"));
            return;
        }
        try {
            int remaining = Integer.parseInt(args[3]);
            int max = ItemDurabilityUtil.getMaxDurability(heldItem);
            if (remaining < 0 || remaining > max) {
                player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Value must be between 0 and </red><yellow>" + max + "</yellow><red>!</red>"));
                return;
            }
            ItemDurabilityUtil.setItemIntegrity(heldItem, 100.0 * remaining / max);
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Durability set to </white><yellow>" + remaining + "</yellow><gray>/" + max + "</gray>"));
        } catch (NumberFormatException e) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Invalid number format!</red>"));
        }
    }

    private static void handleUnbreakable(Player player, ItemStack heldItem, String[] args) {
        boolean setUnbreakable = args.length >= 4
                ? Boolean.parseBoolean(args[3])
                : !ItemDurabilityUtil.isUnbreakable(heldItem);

        ItemDurabilityUtil.setVanillaUnbreakable(heldItem, setUnbreakable);
        if (setUnbreakable) {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Item is now </white><aqua>Unbreakable</aqua><white>!</white>"));
        } else {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Item is no longer </white><aqua>Unbreakable</aqua><white>.</white>"));
        }
    }

    private static void handleAdd(Player player, ItemStack heldItem, String[] args) {
        if (args.length < 4) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Usage: </red><white>/ui item dura add </white><gray><points></gray>"));
            return;
        }
        try {
            int points = Integer.parseInt(args[3]);
            if (points <= 0) {
                player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Value must be greater than 0!</red>"));
                return;
            }
            int max = ItemDurabilityUtil.getMaxDurability(heldItem);
            ItemDurabilityUtil.increaseItemIntegrity(heldItem, points);
            int damage = ItemDurabilityUtil.getVanillaDamage(heldItem);
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Repaired </white><yellow>" + points + "</yellow><white>. Remaining: </white><yellow>" + (max - damage) + "</yellow><gray>/" + max + "</gray>"));
        } catch (NumberFormatException e) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Invalid number format!</red>"));
        }
    }
}
