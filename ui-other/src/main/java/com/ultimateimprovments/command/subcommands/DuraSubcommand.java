package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.mechanics.features.integrity.ItemDurabilityUtil;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.Registries;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * /ui dura — durability scaling on the held item.
 * <p>
 * Two variants for every operation:
 * <ul>
 *   <li><b>mechanics</b> (default) — the vanilla gates apply to reductions:
 *       an unbreakable item loses nothing and the lost points are rolled
 *       through the vanilla Unbreaking chance tables;</li>
 *   <li><b>raw</b> — editor mode ({@code raw} prefix): gates are ignored,
 *       the numbers apply exactly as typed.</li>
 * </ul>
 * Permission: {@code ui.command.dura} (default FALSE).
 */
public final class DuraSubcommand {

    private DuraSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Только игрок может использовать эту команду!"));
            return true;
        }
        if (!player.hasPermission("ui.command.dura")) { CommandErrors.noPermission(player, "ui.command.dura"); return true; }

        String op = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "info";
        switch (op) {
            case "info" -> showInfo(player);
            case "multiply", "divide" -> {
                if (args.length < 3) { sendUsage(player); return true; }
                scale(player, op, args[2], false);
            }
            case "raw" -> {
                if (args.length < 4) { sendUsage(player); return true; }
                scale(player, args[2].toLowerCase(Locale.ROOT), args[3], true);
            }
            default -> sendUsage(player);
        }
        return true;
    }

    private static void scale(Player player, String op, String valueStr, boolean raw) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Вы должны держать предмет в руке!"));
            return;
        }
        if (ItemDurabilityUtil.getMaxDurability(held) <= 0) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>У этого предмета нет прочности!"));
            return;
        }
        if (!op.equals("multiply") && !op.equals("divide")) { sendUsage(player); return; }

        double value;
        try {
            // Accept the Russian decimal comma as well as the dot.
            value = Double.parseDouble(valueStr.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Неверный формат числа: <white>" + valueStr));
            return;
        }
        if (op.equals("multiply") ? value < 0 : value <= 0) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Число должно быть "
                    + (op.equals("multiply") ? "не меньше 0!" : "больше 0!")));
            return;
        }

        int max = ItemDurabilityUtil.getMaxDurability(held);
        int beforeRemaining = max - ItemDurabilityUtil.getVanillaDamage(held);
        boolean unbreakable = ItemDurabilityUtil.isUnbreakable(held);
        org.bukkit.enchantments.Enchantment unbreaking = Registries.unbreaking();
        int unbreakingLevel = unbreaking != null ? held.getEnchantmentLevel(unbreaking) : 0;

        double result = op.equals("multiply")
                ? (raw ? ItemDurabilityUtil.multiplyItemIntegrity(held, value)
                       : ItemDurabilityUtil.multiplyItemIntegrity(held, value, player))
                : (raw ? ItemDurabilityUtil.divideItemIntegrity(held, value)
                       : ItemDurabilityUtil.divideItemIntegrity(held, value, player));
        if (result < 0) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Не удалось изменить прочность."));
            return;
        }

        int afterRemaining = max - ItemDurabilityUtil.getVanillaDamage(held);
        String opWord = op.equals("multiply") ? "× " + value : "÷ " + value;
        StringBuilder msg = new StringBuilder();
        if (afterRemaining <= 0) {
            msg.append("<red>✖ <white>Предмет сломан (<yellow>").append(opWord).append("<white>).");
        } else {
            msg.append("<green>✔ <white>Прочность: <yellow>").append(beforeRemaining)
               .append("</yellow><gray> → </gray><yellow>").append(afterRemaining)
               .append("</yellow><gray>/").append(max)
               .append(" <gray>(<white>").append(String.format(Locale.ROOT, "%.1f", result)).append("%<gray>)");
        }
        if (raw) {
            msg.append(" <dark_gray>[без гейтов]");
        }
        player.sendMessage(MessageUtil.parse(msg.toString()));

        // Mechanics-only feedback for reductions
        boolean reduces = op.equals("multiply") ? value < 1 : value > 1;
        if (!raw && reduces && beforeRemaining > 0 && afterRemaining > 0) {
            if (unbreakable) {
                if (afterRemaining == beforeRemaining) {
                    player.sendMessage(MessageUtil.parse("<gray>Предмет <aqua>неломаемый</aqua><gray> — износ заблокирован."));
                }
            } else if (unbreakingLevel > 0) {
                int expected = expectedRemaining(max, beforeRemaining, op, value);
                int saved = afterRemaining - expected;
                if (saved > 0) {
                    player.sendMessage(MessageUtil.parse("<gray>Прочность <yellow>" + unbreakingLevel
                            + "</yellow> спасла <green>" + saved + "</green><gray> очк. (без неё осталось бы <white>"
                            + expected + "</white><gray>)."));
                }
            }
        }
    }

    /** Remaining points the scaling would give with no Unbreaking roll. */
    private static int expectedRemaining(int max, int beforeRemaining, String op, double value) {
        double factor = op.equals("multiply") ? value : 1.0 / value;
        return Math.max(0, Math.min(max, (int) Math.round(beforeRemaining * factor)));
    }

    private static void showInfo(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Вы должны держать предмет в руке!"));
            return;
        }
        int max = ItemDurabilityUtil.getMaxDurability(held);
        if (max <= 0) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>У этого предмета нет прочности!"));
            return;
        }
        int damage = ItemDurabilityUtil.getVanillaDamage(held);
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════"));
        player.sendMessage(MessageUtil.parse("<gold>  ✦ <white>Прочность предмета <gray>(/ui dura)"));
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════"));
        player.sendMessage(MessageUtil.parse("<gray>Осталось: <green>" + (max - damage) + "</green><gray>/" + max
                + " <gray>(<white>" + String.format(Locale.ROOT, "%.1f", ItemDurabilityUtil.getItemIntegrityPercent(held)) + "%<gray>)"));
        player.sendMessage(MessageUtil.parse("<gray>Неломаемый: <white>" + ItemDurabilityUtil.isUnbreakable(held)));
        player.sendMessage(MessageUtil.parse("<gray>Прочность (чар): <white>"
                + (Registries.unbreaking() != null
                        ? held.getEnchantmentLevel(Registries.unbreaking())
                        : held.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.UNBREAKING))));
        player.sendMessage(MessageUtil.parse("<gray>Варианты: <white>multiply|divide <gray>(гейты) / <white>raw multiply|divide <gray>(без)"));
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════"));
    }

    private static void sendUsage(Player player) {
        player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Использование:"));
        player.sendMessage(MessageUtil.parse("<white>/ui dura <gray>— информация"));
        player.sendMessage(MessageUtil.parse("<white>/ui dura multiply <gray><фактор> <dark_gray>— остаток × фактор (с гейтами)"));
        player.sendMessage(MessageUtil.parse("<white>/ui dura divide <gray><делитель> <dark_gray>— остаток ÷ делитель (с гейтами)"));
        player.sendMessage(MessageUtil.parse("<white>/ui dura raw multiply <gray><фактор> <dark_gray>— без гейтов"));
        player.sendMessage(MessageUtil.parse("<white>/ui dura raw divide <gray><делитель> <dark_gray>— без гейтов"));
    }

    public static List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) return List.of("info", "multiply", "divide", "raw");
        if (args.length == 3 && args[1].equalsIgnoreCase("raw")) return List.of("multiply", "divide");
        if (args.length == 3) return List.of(""); // numeric value — suppress player-name fallback
        if (args.length == 4 && args[1].equalsIgnoreCase("raw")) return List.of("");
        return List.of();
    }
}
