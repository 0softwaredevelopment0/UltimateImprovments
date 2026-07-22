package com.ultimateimprovements.command.subcommands;

import com.ultimateimprovements.enchantment.AOEEnchantment;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Subcommand: /mp enchant aoe <level>
 * <p>
 * Применяет AoE зачарование к предмету в руке игрока.
 * Требуется permission: mcplugin.command.enchant
 */
public class EnchantSubcommand {

    private EnchantSubcommand() {}

    /**
     * Обрабатывает /mp enchant aoe <level>
     */
    public static boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Только игрок может использовать эту команду!", NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("mcplugin.command.enchant")) {
            player.sendMessage(Component.text("❌ У вас нет прав на использование этой команды!", NamedTextColor.RED));
            return true;
        }

        // /mp enchant aoe <level>
        if (args.length < 3) {
            player.sendMessage(Component.text("Использование: /mp enchant aoe <уровень>", NamedTextColor.YELLOW));
            return true;
        }

        if (!args[1].equalsIgnoreCase("aoe")) {
            player.sendMessage(Component.text("Неизвестное зачарование: " + args[1], NamedTextColor.RED));
            return true;
        }

        int level;
        try {
            level = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Уровень должен быть числом от 1 до 255!", NamedTextColor.RED));
            return true;
        }

        if (level < 1 || level > 255) {
            player.sendMessage(Component.text("Уровень должен быть от 1 до 255!", NamedTextColor.RED));
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            player.sendMessage(Component.text("Возьмите инструмент в руку!", NamedTextColor.RED));
            return true;
        }

        if (!AOEEnchantment.isValidTool(item)) {
            player.sendMessage(Component.text("Это зачарование можно применить только на инструменты (кирка, лопата, топор, мотыга)!", NamedTextColor.RED));
            return true;
        }

        AOEEnchantment.setLevel(item, level);
        player.sendMessage(
                Component.text("✔ ", NamedTextColor.GREEN)
                        .append(Component.text("AoE " + level, NamedTextColor.AQUA))
                        .append(Component.text(" нанесён на ", NamedTextColor.GRAY))
                        .append(Component.text(item.getType().name().toLowerCase().replace('_', ' '), NamedTextColor.WHITE))
        );

        return true;
    }

    /**
     * Tab-complete для /mp enchant aoe <level>
     */
    public static List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> result = new ArrayList<>();

        if (args.length == 2) {
            // Подсказываем "aoe"
            if ("aoe".startsWith(args[1].toLowerCase())) {
                result.add("aoe");
            }
        } else if (args.length == 3 && args[1].equalsIgnoreCase("aoe")) {
            // Подсказываем уровни 1-255 (только самые популярные)
            if (args[2].isEmpty()) {
                result.add("1");
                result.add("2");
                result.add("3");
                result.add("4");
                result.add("5");
                result.add("10");
                result.add("50");
                result.add("100");
                result.add("255");
            } else {
                // Если уже начали вводить число, подсказываем его
                result.add(args[2]);
            }
        }

        return result;
    }
}
