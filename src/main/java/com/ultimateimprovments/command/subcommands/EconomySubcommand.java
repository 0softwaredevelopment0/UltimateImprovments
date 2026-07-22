package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.economy.EconomyManager;
import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /mp money &lt;give|list|remove|set&gt; &lt;player&gt; [currency] [amount]
 * <p>
 * Управление балансом игроков.
 * Требуется пермишен: {@code ui.command.money}.
 */
public final class EconomySubcommand {

    private static final DecimalFormat FMT = new DecimalFormat("#,##0.##");
    private static final String PERMISSION = "ui.command.money";

    private EconomySubcommand() {}

    // ==========================================================================
    // ENTRY POINT
    // ==========================================================================

    public static boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("general.no_permission",
                    "<red>❌ You don't have permission to use this command!</red>")));
            return true;
        }

        if (args.length < 2) {
            usage(sender);
            return true;
        }

        String action = args[1].toLowerCase();
        return switch (action) {
            case "give" -> handleGive(sender, args);
            case "list" -> handleList(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "set" -> handleSet(sender, args);
            default -> {
                usage(sender);
                yield true;
            }
        };
    }

    // ==========================================================================
    // GIVE — /mp money give <player> [currency] <amount>
    // ==========================================================================

    private static boolean handleGive(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/mp money give <player> [currency] <amount></white>"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[2]);
        if (target == null || !target.hasPlayedBefore()) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Player not found: </red><white>" + args[2] + "</white>"));
            return true;
        }

        UUID uuid = target.getUniqueId();
        double amount;
        String currency;

        // Определяем: currency указан или нет
        // /mp money give <player> <amount> — currency = coins
        // /mp money give <player> <currency> <amount>
        try {
            // Пробуем распарсить args[3] как число
            amount = Double.parseDouble(args[3]);
            currency = EconomyManager.getInstance().getPrimaryCurrency();
        } catch (NumberFormatException e) {
            // Значит args[3] — это валюта
            currency = args[3].toLowerCase();
            if (args.length < 5) {
                sender.sendMessage(MessageUtil.parse(
                        "<red>❌ Usage: </red><white>/mp money give <player> <currency> <amount></white>"));
                return true;
            }
            try {
                amount = Double.parseDouble(args[4]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(MessageUtil.parse(
                        "<red>❌ Invalid amount: </red><white>" + args[4] + "</white>"));
                return true;
            }
        }

        if (amount <= 0) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Amount must be positive!</red>"));
            return true;
        }

        EconomyManager.getInstance().addBalance(uuid, currency, amount);
        sender.sendMessage(MessageUtil.parse(
                "<green>✔</green> <white>Gave </white><yellow>" + FMT.format(amount) + " " + currency
                + "</yellow> <white>to </white><yellow>" + target.getName() + "</yellow>"));

        if (target.isOnline() && target.getPlayer() != null) {
            target.getPlayer().sendMessage(MessageUtil.parse(
                    "<green>+</green> <yellow>" + FMT.format(amount) + " " + currency
                    + "</yellow> <white>received!</white>"));
        }

        ConsoleLogger.info("[Economy] " + sender.getName() + " gave " + amount + " " + currency + " to " + target.getName());
        return true;
    }

    // ==========================================================================
    // LIST — /mp money list <player>
    // ==========================================================================

    private static boolean handleList(CommandSender sender, String[] args) {
        if (args.length < 3) {
            // Для игрока — показать свой баланс
            if (sender instanceof Player player) {
                return showBalances(sender, player.getUniqueId(), player.getName());
            }
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/mp money list <player></white>"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[2]);
        if (target == null || !target.hasPlayedBefore()) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Player not found: </red><white>" + args[2] + "</white>"));
            return true;
        }

        return showBalances(sender, target.getUniqueId(), target.getName());
    }

    private static boolean showBalances(CommandSender sender, UUID uuid, String name) {
        var balances = EconomyManager.getInstance().getAllBalances(uuid);

        sender.sendMessage(MessageUtil.parse(""));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));
        sender.sendMessage(MessageUtil.parse(
                "  <gold>💰</gold> <yellow>" + name + "</yellow> <gray>— Balances</gray>"));
        sender.sendMessage(MessageUtil.parse(
                "  <dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));

        if (balances.isEmpty()) {
            sender.sendMessage(MessageUtil.parse(
                    "  <gray>No balances found.</gray>"));
        } else {
            for (var entry : balances.entrySet()) {
                sender.sendMessage(MessageUtil.parse(
                        "  <gray>•</gray> <white>" + entry.getKey() + "</white><gray>: </gray><gold>"
                        + FMT.format(entry.getValue()) + "</gold>"));
            }
        }

        sender.sendMessage(MessageUtil.parse(
                "  <dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));
        sender.sendMessage(MessageUtil.parse(""));
        return true;
    }

    // ==========================================================================
    // REMOVE — /mp money remove <player> [currency] <amount>
    // ==========================================================================

    private static boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/mp money remove <player> [currency] <amount></white>"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[2]);
        if (target == null || !target.hasPlayedBefore()) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Player not found: </red><white>" + args[2] + "</white>"));
            return true;
        }

        UUID uuid = target.getUniqueId();
        double amount;
        String currency;

        try {
            amount = Double.parseDouble(args[3]);
            currency = EconomyManager.getInstance().getPrimaryCurrency();
        } catch (NumberFormatException e) {
            currency = args[3].toLowerCase();
            if (args.length < 5) {
                sender.sendMessage(MessageUtil.parse(
                        "<red>❌ Usage: </red><white>/mp money remove <player> <currency> <amount></white>"));
                return true;
            }
            try {
                amount = Double.parseDouble(args[4]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(MessageUtil.parse(
                        "<red>❌ Invalid amount: </red><white>" + args[4] + "</white>"));
                return true;
            }
        }

        if (amount <= 0) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Amount must be positive!</red>"));
            return true;
        }

        boolean success = EconomyManager.getInstance().removeBalance(uuid, currency, amount);
        if (success) {
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Removed </white><yellow>" + FMT.format(amount) + " " + currency
                    + "</yellow> <white>from </white><yellow>" + target.getName() + "</yellow>"));
            ConsoleLogger.info("[Economy] " + sender.getName() + " removed " + amount + " " + currency + " from " + target.getName());
        } else {
            double current = EconomyManager.getInstance().getBalance(uuid, currency);
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ </red><yellow>" + target.getName() + "</yellow> <red>only has </red><gold>"
                    + FMT.format(current) + " " + currency + "</gold>"));
        }
        return true;
    }

    // ==========================================================================
    // SET — /mp money set <player> [currency] <amount>
    // ==========================================================================

    private static boolean handleSet(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/mp money set <player> [currency] <amount></white>"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[2]);
        if (target == null || !target.hasPlayedBefore()) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Player not found: </red><white>" + args[2] + "</white>"));
            return true;
        }

        UUID uuid = target.getUniqueId();
        double amount;
        String currency;

        try {
            amount = Double.parseDouble(args[3]);
            currency = EconomyManager.getInstance().getPrimaryCurrency();
        } catch (NumberFormatException e) {
            currency = args[3].toLowerCase();
            if (args.length < 5) {
                sender.sendMessage(MessageUtil.parse(
                        "<red>❌ Usage: </red><white>/mp money set <player> <currency> <amount></white>"));
                return true;
            }
            try {
                amount = Double.parseDouble(args[4]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(MessageUtil.parse(
                        "<red>❌ Invalid amount: </red><white>" + args[4] + "</white>"));
                return true;
            }
        }

        if (amount < 0) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Amount cannot be negative!</red>"));
            return true;
        }

        EconomyManager.getInstance().setBalance(uuid, currency, amount);
        sender.sendMessage(MessageUtil.parse(
                "<green>✔</green> <white>Set </white><yellow>" + target.getName() + "</yellow>"
                + "<white>'s </white><gold>" + currency + "</gold> <white>balance to </white><yellow>"
                + FMT.format(amount) + "</yellow>"));
        ConsoleLogger.info("[Economy] " + sender.getName() + " set " + target.getName() + " " + currency + " to " + amount);
        return true;
    }

    // ==========================================================================
    // USAGE
    // ==========================================================================

    private static void usage(CommandSender sender) {
        sender.sendMessage(MessageUtil.parse(
                "<red>❌ Usage: </red><white>/mp money <give|list|remove|set> <player> [currency] <amount></white>"));
        sender.sendMessage(MessageUtil.parse(
                "  <gray>Examples:</gray>"));
        sender.sendMessage(MessageUtil.parse(
                "  <white>/mp money give Steve 100</white> <gray>— give 100 coins to Steve</gray>"));
        sender.sendMessage(MessageUtil.parse(
                "  <white>/mp money give Steve gems 50</white> <gray>— give 50 gems to Steve</gray>"));
        sender.sendMessage(MessageUtil.parse(
                "  <white>/mp money list Steve</white> <gray>— show all balances of Steve</gray>"));
        sender.sendMessage(MessageUtil.parse(
                "  <white>/mp money remove Steve 25</white> <gray>— remove 25 coins from Steve</gray>"));
        sender.sendMessage(MessageUtil.parse(
                "  <white>/mp money set Steve 500</white> <gray>— set Steve's coins to 500</gray>"));
    }

    // ==========================================================================
    // TAB COMPLETE
    // ==========================================================================

    public static List<String> tabComplete(String[] args) {
        List<String> result = new ArrayList<>();

        if (args.length == 2) {
            result.addAll(List.of("give", "list", "remove", "set"));
        } else if (args.length == 3) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                result.add(p.getName());
            }
        } else if (args.length == 4 && !args[1].equalsIgnoreCase("list")) {
            result.add(EconomyManager.getInstance().getPrimaryCurrency());
            result.addAll(List.of("50", "100", "500", "1000", "5000"));
        } else if (args.length == 5 && !args[1].equalsIgnoreCase("list")) {
            result.addAll(List.of("50", "100", "500", "1000", "5000"));
        }

        return result;
    }
}
