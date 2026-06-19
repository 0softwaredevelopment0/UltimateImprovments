package com.mcplugin.commands;

import com.mcplugin.features.integrity.IntegrityManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Обрабатывает команду /mp item — управление целостностью предметов.
 */
public class ItemCommand {

    public static boolean execute(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§4❌ §cИспользование: §f/mp item int <set|add|list> [значение]");
            return true;
        }

        if (args[1].equalsIgnoreCase("int")) {
            return handleIntegrity(player, args);
        }

        player.sendMessage("§4❌ §cНеизвестная подкоманда: §f" + args[1]);
        player.sendMessage("§cИспользование: §f/mp item int set|add|list");
        return true;
    }

    private static boolean handleIntegrity(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§4❌ §cИспользование: §f/mp item int set|add|list");
            return true;
        }

        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem == null || heldItem.getType() == Material.AIR) {
            player.sendMessage("§4❌ §cВы должны держать предмет в руке!");
            return true;
        }

        if (!IntegrityManager.hasIntegrity(heldItem)) {
            IntegrityManager.ensureInitialized(heldItem);
            if (!IntegrityManager.hasIntegrity(heldItem)) {
                player.sendMessage("§4❌ §cЭтот предмет не имеет системы целостности!");
                return true;
            }
        }

        switch (args[2].toLowerCase()) {
            case "list" -> handleList(player, heldItem);
            case "set" -> handleSet(player, heldItem, args);
            case "add" -> handleAdd(player, heldItem, args);
            default -> {
                player.sendMessage("§4❌ §cНеизвестная подкоманда: §f" + args[2]);
                player.sendMessage("§cИспользование: §f/mp item int set|add|list");
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
        player.sendMessage("§6═══════════════════════════════════");
        player.sendMessage("§6  ✦ §fИнформация о целостности предмета");
        player.sendMessage("§6═══════════════════════════════════");
        player.sendMessage("§7Предмет: §f" + itemName);
        player.sendMessage("§7Текущая: §a" + IntegrityManager.formatPercent(pctCurrent) + "%");
        player.sendMessage("§7Макс:    §a100.000%");
        player.sendMessage("§6═══════════════════════════════════");
    }

    private static void handleSet(Player player, ItemStack heldItem, String[] args) {
        if (args.length < 4) {
            player.sendMessage("§4❌ §cИспользование: §f/mp item int set §7<значение>");
            return;
        }
        try {
            double value = Double.parseDouble(args[3]);
            if (value < 0 || value > 100) {
                player.sendMessage("§4❌ §cЗначение должно быть от 0 до 100!");
                return;
            }
            IntegrityManager.setCurrentIntegrity(heldItem, value);
            player.sendMessage("§a✅ §fЦелостность предмета установлена на §e" + IntegrityManager.formatPercent(value) + "%");
        } catch (NumberFormatException e) {
            player.sendMessage("§4❌ §cНеверный формат числа! Используйте дробное число (например: 75.500)");
        }
    }

    private static void handleAdd(Player player, ItemStack heldItem, String[] args) {
        if (args.length < 4) {
            player.sendMessage("§4❌ §cИспользование: §f/mp item int add §7<значение>");
            return;
        }
        try {
            double value = Double.parseDouble(args[3]);
            if (value <= 0) {
                player.sendMessage("§4❌ §cЗначение должно быть больше 0!");
                return;
            }
            double current = IntegrityManager.getCurrentIntegrity(heldItem);
            double newVal = Math.min(100.0, current + value);
            IntegrityManager.setCurrentIntegrity(heldItem, newVal);
            player.sendMessage("§a✅ §fДобавлено §e" + IntegrityManager.formatPercent(value) + "%§f. Текущая: §e" + IntegrityManager.formatPercent(newVal) + "%");
        } catch (NumberFormatException e) {
            player.sendMessage("§4❌ §cНеверный формат числа! Используйте дробное число (например: 25.500)");
        }
    }
}
