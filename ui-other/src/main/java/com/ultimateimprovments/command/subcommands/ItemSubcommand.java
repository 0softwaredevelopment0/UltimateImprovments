package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;

import com.ultimateimprovments.mechanics.features.integrity.IntegrityManager;
import com.ultimateimprovments.mechanics.features.integrity.ItemIntegrityAPI;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ItemSubcommand {

    private ItemSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Только игрок может использовать эту команду!")); return true; }
        Player player = (Player) sender;
        if (!player.hasPermission("ui.command.item")) { CommandErrors.noPermission(player); return true; }
        if (args.length < 2) { player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Использование: <white>/ui item int <set|add|list> [значение]")); return true; }

        if (args[1].equalsIgnoreCase("int")) {
            if (args.length < 3) { player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Использование: <white>/ui item int set|add|list")); return true; }

            ItemStack held = player.getInventory().getItemInMainHand();
            if (held == null || held.getType() == Material.AIR) { player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Вы должны держать предмет в руке!")); return true; }

            if (!ItemIntegrityAPI.hasItemIntegrity(held)) {
                ItemIntegrityAPI.initializeItemIntegrity(held);
                if (!ItemIntegrityAPI.hasItemIntegrity(held)) { player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Этот предмет не имеет системы целостности!")); return true; }
            }

            switch (args[2].toLowerCase()) {
                case "list" -> handleList(player, held);
                case "set" -> handleSet(player, args, held);
                case "add" -> handleAdd(player, args, held);
                default -> player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Неизвестная подкоманда: <white>" + args[2]));
            }
            return true;
        }
        player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Неизвестная подкоманда: <white>" + args[1]));
        return true;
    }

    private static void handleList(Player player, ItemStack held) {
        // getItemIntegrityPercent already returns % (0.0–100.0) — the source of truth
        double current = ItemIntegrityAPI.getItemIntegrityPercent(held);
        double pct = Math.max(0.0, current);
        String name = held.hasItemMeta() && held.getItemMeta().hasDisplayName()
                ? held.getItemMeta().getDisplayName()
                : capitalize(held.getType().name().toLowerCase().replace("_", " "));
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════"));
        player.sendMessage(MessageUtil.parse("<gold>  ✦ <white>Информация о целостности"));
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════"));
        player.sendMessage(MessageUtil.parse("<gray>Предмет: <white>" + name));
        player.sendMessage(MessageUtil.parse("<gray>Текущая: <green>" + IntegrityManager.formatPercent(pct) + "%"));
        player.sendMessage(MessageUtil.parse("<gray>Макс:    <green>" + IntegrityManager.formatPercent(Math.max(0.0, ItemIntegrityAPI.getItemMaxIntegrityPercent(held))) + "%"));
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════"));
    }

    private static void handleSet(Player player, String[] args, ItemStack held) {
        if (args.length < 4) { player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Использование: <white>/ui item int set <gray><значение>")); return; }
        try {
            double value = Double.parseDouble(args[3]);
            if (value < 0 || value > 100) { player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Значение должно быть от 0 до 100!")); return; }
            double actual = Math.max(0.0, ItemIntegrityAPI.setItemIntegrity(held, value));
            player.sendMessage(MessageUtil.parse("<green>✔ <white>Целостность установлена на <yellow>" + IntegrityManager.formatPercent(actual) + "%"));
        } catch (NumberFormatException e) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Неверный формат числа!"));
        }
    }

    private static void handleAdd(Player player, String[] args, ItemStack held) {
        if (args.length < 4) { player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Использование: <white>/ui item int add <gray><значение>")); return; }
        try {
            double value = Double.parseDouble(args[3]);
            if (value <= 0) { player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Значение должно быть больше 0!")); return; }
            // The actual value is returned by the API itself — we don't recalculate locally
            double newVal = Math.max(0.0, ItemIntegrityAPI.increaseItemIntegrityPercent(held, value));
            player.sendMessage(MessageUtil.parse("<green>✔ <white>Добавлено <yellow>" + IntegrityManager.formatPercent(value) + "%<white>. Текущая: <yellow>" + IntegrityManager.formatPercent(newVal) + "%"));
        } catch (NumberFormatException e) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Неверный формат числа!"));
        }
    }

    private static String capitalize(String s) {
        if (s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
