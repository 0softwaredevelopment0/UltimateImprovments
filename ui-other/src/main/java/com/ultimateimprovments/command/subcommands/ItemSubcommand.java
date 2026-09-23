package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.mechanics.features.integrity.ItemDurabilityUtil;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * /ui item dura — vanilla durability management (points, no percentages).
 */
public final class ItemSubcommand {

    private ItemSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Только игрок может использовать эту команду!")); return true; }
        Player player = (Player) sender;
        if (!player.hasPermission("ui.command.item")) { CommandErrors.noPermission(player); return true; }
        if (args.length < 2) { player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Использование: <white>/ui item dura <info|set|add|unbreakable> [значение]")); return true; }

        if (args[1].equalsIgnoreCase("dura") || args[1].equalsIgnoreCase("int")) {
            if (args.length < 3) { player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Использование: <white>/ui item dura info|set|add|unbreakable")); return true; }

            ItemStack held = player.getInventory().getItemInMainHand();
            if (held == null || held.getType() == Material.AIR) { player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Вы должны держать предмет в руке!")); return true; }
            if (ItemDurabilityUtil.getMaxDurability(held) <= 0) { player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>У этого предмета нет прочности!")); return true; }

            switch (args[2].toLowerCase()) {
                case "info", "list" -> handleInfo(player, held);
                case "set" -> handleSet(player, args, held);
                case "add" -> handleAdd(player, args, held);
                case "unbreakable" -> handleUnbreakable(player, args, held);
                default -> player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Неизвестная подкоманда: <white>" + args[2]));
            }
            return true;
        }
        player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Неизвестная подкоманда: <white>" + args[1]));
        return true;
    }

    private static void handleInfo(Player player, ItemStack held) {
        int max = ItemDurabilityUtil.getMaxDurability(held);
        int damage = ItemDurabilityUtil.getVanillaDamage(held);
        String name = held.hasItemMeta() && held.getItemMeta().hasDisplayName()
                ? held.getItemMeta().getDisplayName()
                : capitalize(held.getType().name().toLowerCase().replace("_", " "));
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════"));
        player.sendMessage(MessageUtil.parse("<gold>  ✦ <white>Информация о прочности"));
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════"));
        player.sendMessage(MessageUtil.parse("<gray>Предмет: <white>" + name));
        player.sendMessage(MessageUtil.parse("<gray>Осталось: <green>" + (max - damage) + "</green><gray>/" + max));
        player.sendMessage(MessageUtil.parse("<gray>Неломаемый: <white>" + ItemDurabilityUtil.isUnbreakable(held)));
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════"));
    }

    private static void handleSet(Player player, String[] args, ItemStack held) {
        if (args.length < 4) { player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Использование: <white>/ui item dura set <gray><остаток>")); return; }
        try {
            int remaining = Integer.parseInt(args[3]);
            int max = ItemDurabilityUtil.getMaxDurability(held);
            if (remaining < 0 || remaining > max) { player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Значение должно быть от 0 до " + max + "!")); return; }
            ItemDurabilityUtil.setItemIntegrity(held, 100.0 * remaining / max);
            player.sendMessage(MessageUtil.parse("<green>✔ <white>Прочность установлена на <yellow>" + remaining + "</yellow><gray>/" + max));
        } catch (NumberFormatException e) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Неверный формат числа!"));
        }
    }

    private static void handleAdd(Player player, String[] args, ItemStack held) {
        if (args.length < 4) { player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Использование: <white>/ui item dura add <gray><очки>")); return; }
        try {
            int points = Integer.parseInt(args[3]);
            if (points <= 0) { player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Значение должно быть больше 0!")); return; }
            int max = ItemDurabilityUtil.getMaxDurability(held);
            ItemDurabilityUtil.increaseItemIntegrity(held, points);
            int damage = ItemDurabilityUtil.getVanillaDamage(held);
            player.sendMessage(MessageUtil.parse("<green>✔ <white>Отремонтировано на <yellow>" + points + "</yellow><white>. Осталось: <yellow>" + (max - damage) + "</yellow><gray>/" + max));
        } catch (NumberFormatException e) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Неверный формат числа!"));
        }
    }

    private static void handleUnbreakable(Player player, String[] args, ItemStack held) {
        boolean set = args.length >= 4
                ? Boolean.parseBoolean(args[3])
                : !ItemDurabilityUtil.isUnbreakable(held);
        ItemDurabilityUtil.setVanillaUnbreakable(held, set);
        if (set) {
            player.sendMessage(MessageUtil.parse("<green>✔ <white>Предмет теперь </white><aqua>неломаемый</aqua><white>!"));
        } else {
            player.sendMessage(MessageUtil.parse("<green>✔ <white>Предмет больше не </white><aqua>неломаемый</aqua><white>."));
        }
    }

    private static String capitalize(String s) {
        if (s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
