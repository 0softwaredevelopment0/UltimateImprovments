package com.mcplugin.commands;

import com.mcplugin.cp.CodePanelDatabase;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Обрабатывает подкоманду /mp codepane key — управление ключами кодовой панели.
 */
public class CodePaneKeyCommand {

    public static boolean execute(CommandSender sender, String[] args) {
        // Permission check
        if (sender instanceof Player p && !p.hasPermission("mcplugin.command.codepane.key")) {
            sender.sendMessage("§4❌ §cУ вас нет прав на управление ключами кодовой панели!");
            return true;
        }

        if (args.length < 3) {
            sendUsage(sender);
            return true;
        }

        String subCmd = args[2].toLowerCase();

        switch (subCmd) {
            case "add" -> handleAdd(sender, args);
            case "list" -> handleList(sender);
            case "remove" -> handleRemove(sender, args);
            case "modify" -> handleModify(sender, args);
            default -> sender.sendMessage("§4❌ §cНеизвестная подкоманда: §f" + subCmd);
        }
        return true;
    }

    private static void sendUsage(CommandSender sender) {
        sender.sendMessage("§6=== §fУправление ключами кодовой панели §6===");
        sender.sendMessage("");
        sender.sendMessage("§e/mp codepane key add §7<название> <код> [флаги]");
        sender.sendMessage(" §7└ Добавить новый ключ");
        sender.sendMessage("§e/mp codepane key list");
        sender.sendMessage(" §7└ Список всех ключей");
        sender.sendMessage("§e/mp codepane key remove §7<название>");
        sender.sendMessage(" §7└ Удалить ключ");
        sender.sendMessage("§e/mp codepane key modify §7<название> <новый_код> [флаги]");
        sender.sendMessage(" §7└ Изменить ключ");
        sender.sendMessage("");
        sender.sendMessage("§7Необходимые права:");
        sender.sendMessage("§7mcplugin.command.codepane.key — базовое");
        sender.sendMessage(" §7mcplugin.command.codepane.key.add — добавление");
        sender.sendMessage(" §7mcplugin.command.codepane.key.list — список");
        sender.sendMessage(" §7mcplugin.command.codepane.key.remove — удаление");
        sender.sendMessage(" §7mcplugin.command.codepane.key.modify — изменение");
        sender.sendMessage("");
        sender.sendMessage("§7Флаги:");
        sender.sendMessage(" §7attempts:<N>     — удалить ключ после N успешных использований");
        sender.sendMessage(" §7time:<N>s|m|h|d  — удалить ключ через N секунд/минут/часов/дней");
        sender.sendMessage("§7whitelist:<ник1,ник2...>  — разрешить только этим игрокам");
        sender.sendMessage("§7whitelist:(<ник1,ник2...>)  — то же, но в скобках");
        sender.sendMessage("§7blacklist:<ник1,ник2...>  — запретить этим игрокам");
        sender.sendMessage("§7blacklist:(<ник1,ник2...>)  — то же, но в скобках");
        sender.sendMessage("§7command:(<команда с пробелами>),(<команда 2>)  — команды через запятую, пробелы в скобках");
        sender.sendMessage(" §7  %entity% — заменится на ник игрока");
        sender.sendMessage("");
        sender.sendMessage("§7Примеры:");
        sender.sendMessage(" §f/mp codepane key add mydoor 1234 attempts:3 time:1h");
        sender.sendMessage(" §f/mp codepane key add admin 7777 whitelist:Steve,Alex");
        sender.sendMessage(" §f/mp codepane key add warp 4321 command:(say %entity% got access)");
        sender.sendMessage(" §f/mp codepane key add warp 4321 command:(say %entity%),(mvwarp spawn)");
    }

    // =========================
    // KEY ADD
    // =========================
    private static void handleAdd(CommandSender sender, String[] args) {
        if (sender instanceof Player p && !p.hasPermission("mcplugin.command.codepane.key.add")) {
            sender.sendMessage("§4❌ §cУ вас нет прав на добавление ключей!");
            return;
        }
        if (args.length < 5) {
            sender.sendMessage("§4❌ §cИспользование: §f/mp codepane key add §7<название> <код> [флаги]");
            return;
        }

        String keyName = args[3];
        String code = args[4];

        int maxAttempts = -1;
        long expiresAt = 0;
        String whitelistStr = "";
        String blacklistStr = "";
        String commandStr = "say $entity used code: " + keyName;
        Set<String> seenFlags = new HashSet<>();

        for (int i = 5; i < args.length; i++) {
            String flag = args[i];

            if (flag.startsWith("attempts:")) {
                if (seenFlags.contains("attempts")) {
                    sender.sendMessage("§4❌ §cДублирование флага: attempts! Используйте каждый флаг только один раз.");
                    return;
                }
                seenFlags.add("attempts");
                try {
                    maxAttempts = Integer.parseInt(flag.substring(9));
                    if (maxAttempts < 1) {
                        sender.sendMessage("§4❌ §cattempts должен быть >= 1");
                        return;
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage("§4❌ §cНеверный формат attempts: " + flag);
                    return;
                }
            } else if (flag.startsWith("time:")) {
                if (seenFlags.contains("time")) {
                    sender.sendMessage("§4❌ §cДублирование флага: time! Используйте каждый флаг только один раз.");
                    return;
                }
                seenFlags.add("time");
                expiresAt = parseTimeFlag(flag.substring(5));
                if (expiresAt == 0) {
                    sender.sendMessage("§4❌ §cНеверный формат time: §7" + flag.substring(5) + " §c(используйте Ns, Nm, Nh, Nd)");
                    return;
                }
            } else if (flag.startsWith("whitelist:")) {
                if (seenFlags.contains("whitelist")) {
                    sender.sendMessage("§4❌ §cДублирование флага: whitelist! Используйте каждый флаг только один раз.");
                    return;
                }
                seenFlags.add("whitelist");
                whitelistStr = parseListFlag(args, i, "whitelist:");
                int consumed = countListFlagArgs(args, i, "whitelist:");
                if (consumed > 0) i += consumed;
            } else if (flag.startsWith("blacklist:")) {
                if (seenFlags.contains("blacklist")) {
                    sender.sendMessage("§4❌ §cДублирование флага: blacklist! Используйте каждый флаг только один раз.");
                    return;
                }
                seenFlags.add("blacklist");
                blacklistStr = parseListFlag(args, i, "blacklist:");
                int consumed = countListFlagArgs(args, i, "blacklist:");
                if (consumed > 0) i += consumed;
            } else if (flag.startsWith("command:")) {
                if (seenFlags.contains("command")) {
                    sender.sendMessage("§4❌ §cДублирование флага: command! Используйте каждый флаг только один раз.");
                    return;
                }
                seenFlags.add("command");
                String parsed = parseCommandFlag(args, i);
                if (parsed != null) commandStr = parsed;
                // Count extra args consumed by parenthesized syntax
                int consumed = countCommandFlagArgs(args, i);
                if (consumed > 0) i += consumed;
            } else {
                sender.sendMessage("§e⚠ §7Неизвестный флаг: §f" + flag);
            }
        }

        // Check if key already exists
        if (CodePanelDatabase.keyExists(keyName)) {
            sender.sendMessage("§4❌ §cКлюч §e" + keyName + "§c уже существует!");
            return;
        }

        boolean success = CodePanelDatabase.addKey(keyName, code, commandStr,
                maxAttempts, expiresAt, whitelistStr, blacklistStr);

        if (!success) {
            sender.sendMessage("§4❌ §cОшибка при добавлении ключа в БД!");
            return;
        }

        sender.sendMessage("§a✅ §fКлюч §e" + keyName + "§f добавлен в БД!");
        sender.sendMessage("§8┃ §7Код: §f" + code);
        if (maxAttempts > 0) {
            sender.sendMessage("§8┃ §7Макс. использований: §f" + maxAttempts);
        }
        if (expiresAt > 0) {
            String dateStr = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss")
                    .format(new java.util.Date(expiresAt));
            sender.sendMessage("§8┃ §7Истекает: §f" + dateStr);
        }
        if (!whitelistStr.isEmpty()) {
            sender.sendMessage("§8┃ §7Whitelist: §f" + whitelistStr);
        }
        if (!blacklistStr.isEmpty()) {
            sender.sendMessage("§8┃ §7Blacklist: §f" + blacklistStr);
        }
        if (!commandStr.isEmpty()) {
            sender.sendMessage("§8┃ §7Command: §f" + commandStr);
        }
    }

    // =========================
    // KEY LIST
    // =========================
    private static void handleList(CommandSender sender) {
        if (sender instanceof Player p && !p.hasPermission("mcplugin.command.codepane.key.list")) {
            sender.sendMessage("§4❌ §cУ вас нет прав на просмотр списка ключей!");
            return;
        }

        List<CodePanelDatabase.CodePanelKey> keys = CodePanelDatabase.getAllKeys();

        if (keys.isEmpty()) {
            sender.sendMessage("§eℹ §fВ базе нет ни одного ключа.");
            return;
        }

        sender.sendMessage("");
        sender.sendMessage("§6══════════════════════════════════");
        sender.sendMessage("§6  ✦ §fСписок ключей кодовой панели §7(" + keys.size() + ")");
        sender.sendMessage("§6══════════════════════════════════");

        for (CodePanelDatabase.CodePanelKey key : keys) {
            sender.sendMessage("");
            sender.sendMessage("§8┌─ §e" + key.keyName);
            sender.sendMessage("§8│ §7Код: §f" + key.code);

            if (key.command != null && !key.command.isEmpty()) {
                sender.sendMessage("§8│ §7Команды: §f" + key.command);
            }

            if (!key.whitelist.isEmpty()) {
                sender.sendMessage("§8│ §7Whitelist: §a" + String.join("§7, §a", key.whitelist));
            }
            if (!key.blacklist.isEmpty()) {
                sender.sendMessage("§8│ §7Blacklist: §c" + String.join("§7, §c", key.blacklist));
            }

            if (key.maxAttempts > 0) {
                int left = key.maxAttempts - key.attemptsUsed;
                String color = left <= 1 ? "§c" : left <= 3 ? "§e" : "§a";
                sender.sendMessage("§8│ §7Попытки: " + color + left + "§7/" + key.maxAttempts);
            }

            if (key.expiresAt > 0) {
                long remain = key.expiresAt - System.currentTimeMillis();
                if (remain <= 0) {
                    sender.sendMessage("§8│ §7Истекает: §cпросрочен");
                } else {
                    sender.sendMessage("§8│ §7Истекает: §f" + formatDuration(remain));
                }
            }
        }

        sender.sendMessage("");
        sender.sendMessage("§6══════════════════════════════════");
        sender.sendMessage("");
    }

    // =========================
    // KEY REMOVE
    // =========================
    private static void handleRemove(CommandSender sender, String[] args) {
        if (sender instanceof Player p && !p.hasPermission("mcplugin.command.codepane.key.remove")) {
            sender.sendMessage("§4❌ §cУ вас нет прав на удаление ключей!");
            return;
        }
        if (args.length < 4) {
            sender.sendMessage("§4❌ §cИспользование: §f/mp codepane key remove §7<название>");
            return;
        }

        String keyName = args[3];

        if (!CodePanelDatabase.keyExists(keyName)) {
            sender.sendMessage("§4❌ §cКлюч §e" + keyName + "§c не найден!");
            return;
        }

        CodePanelDatabase.removeKey(keyName);
        sender.sendMessage("§a✅ §fКлюч §e" + keyName + "§f удалён из БД.");
    }

    // =========================
    // KEY MODIFY
    // =========================
    private static void handleModify(CommandSender sender, String[] args) {
        if (sender instanceof Player p && !p.hasPermission("mcplugin.command.codepane.key.modify")) {
            sender.sendMessage("§4❌ §cУ вас нет прав на изменение ключей!");
            return;
        }
        if (args.length < 5) {
            sender.sendMessage("§4❌ §cИспользование: §f/mp codepane key modify §7<название> <новый_код> [флаги]");
            return;
        }

        String keyName = args[3];
        String newCode = args[4];
        String commandStrOverride = null;

        if (!CodePanelDatabase.keyExists(keyName)) {
            sender.sendMessage("§4❌ §cКлюч §e" + keyName + "§c не найден в БД!");
            return;
        }

        CodePanelDatabase.CodePanelKey existing = CodePanelDatabase.getKey(keyName);

        int maxAttempts = existing != null ? existing.maxAttempts : -1;
        long expiresAt = existing != null ? existing.expiresAt : 0;
        String whitelistStr = existing != null ? String.join(",", existing.whitelist) : "";
        String blacklistStr = existing != null ? String.join(",", existing.blacklist) : "";

        boolean hasCommandFlag = false;
        Set<String> seenFlags = new HashSet<>();

        for (int i = 5; i < args.length; i++) {
            String flag = args[i];

            if (flag.startsWith("attempts:")) {
                if (seenFlags.contains("attempts")) {
                    sender.sendMessage("§4❌ §cДублирование флага: attempts! Используйте каждый флаг только один раз.");
                    return;
                }
                seenFlags.add("attempts");
                try {
                    maxAttempts = Integer.parseInt(flag.substring(9));
                    if (maxAttempts < 1) {
                        sender.sendMessage("§4❌ §cattempts должен быть >= 1");
                        return;
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage("§4❌ §cНеверный формат attempts: " + flag);
                    return;
                }
            } else if (flag.startsWith("time:")) {
                if (seenFlags.contains("time")) {
                    sender.sendMessage("§4❌ §cДублирование флага: time! Используйте каждый флаг только один раз.");
                    return;
                }
                seenFlags.add("time");
                expiresAt = parseTimeFlag(flag.substring(5));
                if (expiresAt == 0) {
                    sender.sendMessage("§4❌ §cНеверный формат time: §7" + flag.substring(5) + " §c(используйте Ns, Nm, Nh, Nd)");
                    return;
                }
            } else if (flag.startsWith("whitelist:")) {
                if (seenFlags.contains("whitelist")) {
                    sender.sendMessage("§4❌ §cДублирование флага: whitelist! Используйте каждый флаг только один раз.");
                    return;
                }
                seenFlags.add("whitelist");
                whitelistStr = parseListFlag(args, i, "whitelist:");
                int consumed = countListFlagArgs(args, i, "whitelist:");
                if (consumed > 0) i += consumed;
            } else if (flag.startsWith("blacklist:")) {
                if (seenFlags.contains("blacklist")) {
                    sender.sendMessage("§4❌ §cДублирование флага: blacklist! Используйте каждый флаг только один раз.");
                    return;
                }
                seenFlags.add("blacklist");
                blacklistStr = parseListFlag(args, i, "blacklist:");
                int consumed = countListFlagArgs(args, i, "blacklist:");
                if (consumed > 0) i += consumed;
            } else if (flag.startsWith("command:")) {
                if (seenFlags.contains("command")) {
                    sender.sendMessage("§4❌ §cДублирование флага: command! Используйте каждый флаг только один раз.");
                    return;
                }
                seenFlags.add("command");
                hasCommandFlag = true;
                String parsed = parseCommandFlag(args, i);
                if (parsed != null) commandStrOverride = parsed;
                int consumed = countCommandFlagArgs(args, i);
                if (consumed > 0) i += consumed;
            } else {
                sender.sendMessage("§e⚠ §7Неизвестный флаг: §f" + flag);
            }
        }

        // Determine the command
        String commandStr;
        if (commandStrOverride != null) {
            commandStr = commandStrOverride;
        } else if (hasCommandFlag) {
            commandStr = "";
            for (int i = 5; i < args.length; i++) {
                if (args[i].startsWith("command:")) {
                    commandStr = args[i].substring(8);
                    break;
                }
            }
        } else {
            commandStr = existing != null ? existing.command : "say $entity used code: " + keyName;
        }

        CodePanelDatabase.updateKey(keyName, newCode, commandStr,
                maxAttempts, expiresAt, whitelistStr, blacklistStr);

        sender.sendMessage("§a✅ §fКлюч §e" + keyName + "§f изменён в БД.");
        sender.sendMessage("§8┃ §7Новый код: §f" + newCode);
        if (maxAttempts > 0) {
            sender.sendMessage("§8┃ §7Макс. использований: §f" + maxAttempts);
        }
        if (expiresAt > 0) {
            String dateStr = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss")
                    .format(new java.util.Date(expiresAt));
            sender.sendMessage("§8┃ §7Истекает: §f" + dateStr);
        }
        if (!whitelistStr.isEmpty()) {
            sender.sendMessage("§8┃ §7Whitelist: §f" + whitelistStr);
        }
        if (!blacklistStr.isEmpty()) {
            sender.sendMessage("§8┃ §7Blacklist: §f" + blacklistStr);
        }
        if (!commandStr.isEmpty()) {
            sender.sendMessage("§8┃ §7Command: §f" + commandStr);
        }
    }

    // =========================
    // FLAG PARSING HELPERS
    // =========================

    private static String parseListFlag(String[] args, int startIndex, String prefix) {
        String flag = args[startIndex];

        if (flag.startsWith(prefix + "(")) {
            StringBuilder joined = new StringBuilder(flag);
            int depth = 0;
            for (int k = 0; k < flag.length(); k++) {
                char ch = flag.charAt(k);
                if (ch == '(') depth++;
                else if (ch == ')') depth--;
            }
            int j = startIndex;
            while (depth > 0 && j + 1 < args.length) {
                j++;
                joined.append(" ").append(args[j]);
                for (int k = 0; k < args[j].length(); k++) {
                    char ch = args[j].charAt(k);
                    if (ch == '(') depth++;
                    else if (ch == ')') depth--;
                }
            }
            String total = joined.toString();
            int openIdx = total.indexOf('(');
            int closeIdx = total.lastIndexOf(')');
            if (openIdx != -1 && closeIdx != -1 && closeIdx > openIdx) {
                return total.substring(openIdx + 1, closeIdx);
            }
            return total.substring(prefix.length());
        } else {
            return flag.substring(prefix.length());
        }
    }

    private static int countListFlagArgs(String[] args, int startIndex, String prefix) {
        String flag = args[startIndex];
        if (!flag.startsWith(prefix + "(")) return 0;

        int depth = 0;
        for (int k = 0; k < flag.length(); k++) {
            char ch = flag.charAt(k);
            if (ch == '(') depth++;
            else if (ch == ')') depth--;
        }
        if (depth == 0) return 0;

        int count = 0;
        for (int j = startIndex + 1; j < args.length && depth > 0; j++) {
            count++;
            for (int k = 0; k < args[j].length(); k++) {
                char ch = args[j].charAt(k);
                if (ch == '(') depth++;
                else if (ch == ')') depth--;
            }
        }
        return count;
    }

    private static String parseCommandFlag(String[] args, int startIndex) {
        String flag = args[startIndex];

        if (flag.startsWith("command:(")) {
            StringBuilder joined = new StringBuilder(flag);
            int depth = 0;
            for (int k = 0; k < flag.length(); k++) {
                char ch = flag.charAt(k);
                if (ch == '(') depth++;
                else if (ch == ')') depth--;
            }
            int j = startIndex;
            while (depth > 0 && j + 1 < args.length) {
                j++;
                String nextArg = args[j];
                joined.append(" ").append(nextArg);
                for (int k = 0; k < nextArg.length(); k++) {
                    char ch = nextArg.charAt(k);
                    if (ch == '(') depth++;
                    else if (ch == ')') depth--;
                }
            }
            return extractCommandsFromParentheses(joined.toString());
        } else {
            return flag.substring(8);
        }
    }

    private static int countCommandFlagArgs(String[] args, int startIndex) {
        String flag = args[startIndex];
        if (!flag.startsWith("command:(")) return 0;

        int depth = 0;
        for (int k = 0; k < flag.length(); k++) {
            char ch = flag.charAt(k);
            if (ch == '(') depth++;
            else if (ch == ')') depth--;
        }
        if (depth == 0) return 0;

        int count = 0;
        for (int j = startIndex + 1; j < args.length && depth > 0; j++) {
            count++;
            for (int k = 0; k < args[j].length(); k++) {
                char ch = args[j].charAt(k);
                if (ch == '(') depth++;
                else if (ch == ')') depth--;
            }
        }
        return count;
    }

    private static String extractCommandsFromParentheses(String input) {
        int start = input.indexOf('(');
        if (start == -1) return null;
        start++;

        StringBuilder result = new StringBuilder();
        StringBuilder current = new StringBuilder();
        int depth = 1;

        for (int i = start; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '(') {
                if (depth > 0) current.append(c);
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    String trimmed = current.toString().trim();
                    if (!trimmed.isEmpty()) {
                        if (result.length() > 0) result.append(",");
                        result.append(trimmed);
                    }
                    current.setLength(0);
                    if (i + 1 < input.length() && input.charAt(i + 1) == ',') {
                        i++;
                    }
                } else {
                    if (depth > 0) current.append(c);
                }
            } else {
                if (depth > 0) current.append(c);
            }
        }

        return result.length() > 0 ? result.toString() : null;
    }

    private static String formatDuration(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) return days + "д " + (hours % 24) + "ч";
        if (hours > 0) return hours + "ч " + (minutes % 60) + "м";
        if (minutes > 0) return minutes + "м " + (seconds % 60) + "с";
        return seconds + "с";
    }

    private static long parseTimeFlag(String value) {
        if (value == null || value.isEmpty()) return 0;

        char suffix = value.charAt(value.length() - 1);
        String numStr = value.substring(0, value.length() - 1);

        try {
            long amount = Long.parseLong(numStr);
            long multiplier;

            switch (suffix) {
                case 's' -> multiplier = 1000L;
                case 'm' -> multiplier = 60L * 1000L;
                case 'h' -> multiplier = 60L * 60L * 1000L;
                case 'd' -> multiplier = 24L * 60L * 60L * 1000L;
                default -> {
                    amount = Long.parseLong(value);
                    multiplier = 1000L;
                }
            }

            return System.currentTimeMillis() + (amount * multiplier);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
