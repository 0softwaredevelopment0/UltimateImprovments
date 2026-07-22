package com.ultimateimprovments.command;

import com.ultimateimprovments.mechanics.security.codepanel.CodePanelDatabase;
import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.util.MessageUtil;
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
        if (sender instanceof Player p && !p.hasPermission("ui.command.codepane.key")) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.no_permission", "<red>❌ You don't have permission to manage code panel keys!</red>")));
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
            default -> sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.unknown_subcommand", "<red>❌ Unknown subcommand: </red><white>%subcommand%</white>").replace("%subcommand%", subCmd)));
        }
        return true;
    }

    private static void sendUsage(CommandSender sender) {
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.help.header", "<gold>=== <white>Code Panel Key Management</white> ===")));
        sender.sendMessage("");
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.help.add", "<yellow>/mp codepane key add <gray><name> <code> [flags]</gray></yellow>")));
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.help.add_desc", "<gray> Add a new key</gray>")));
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.help.list", "<yellow>/mp codepane key list</yellow>")));
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.help.list_desc", "<gray> List all keys</gray>")));
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.help.remove", "<yellow>/mp codepane key remove <gray><name></gray></yellow>")));
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.help.remove_desc", "<gray> Remove a key</gray>")));
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.help.modify", "<yellow>/mp codepane key modify <gray><name> <new_code> [flags]</gray></yellow>")));
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.help.modify_desc", "<gray> Modify a key</gray>")));
        sender.sendMessage("");
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.help.permissions_header", "<gray>Required permissions:</gray>")));
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.help.perm_base", "<gray>ui.command.codepane.key — base</gray>")));
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.help.perm_add", "<gray> ui.command.codepane.key.add — add</gray>")));
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.help.perm_list", "<gray> ui.command.codepane.key.list — list</gray>")));
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.help.perm_remove", "<gray> ui.command.codepane.key.remove — remove</gray>")));
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.help.perm_modify", "<gray> ui.command.codepane.key.modify — modify</gray>")));
        sender.sendMessage("");
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.help.flags_header", "<gray>Flags:</gray>")));
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.help.flag_attempts", "<gray> attempts:<N>     — delete key after N successful uses</gray>")));
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.help.flag_time", "<gray> time:<N>s|m|h|d  — delete key after N seconds/minutes/hours/days</gray>")));
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.help.flag_whitelist", "<gray>whitelist:<name1,name2...>  — allow only these players</gray>")));
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.help.flag_whitelist_parens", "<gray>whitelist:(<name1,name2...>)  — same, but in parentheses</gray>")));
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.help.flag_blacklist", "<gray>blacklist:<name1,name2...>  — block these players</gray>")));
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.help.flag_blacklist_parens", "<gray>blacklist:(<name1,name2...>)  — same, but in parentheses</gray>")));
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.help.flag_command", "<gray>command:(<cmd with spaces>),(<cmd 2>)  — commands separated by commas</gray>")));
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.help.flag_command_entity", "<gray>  %entity% — replaced with player's nickname</gray>")));
        sender.sendMessage("");
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.help.examples_header", "<gray>Examples:</gray>")));
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.help.example1", "<gray> /mp codepane key add mydoor 1234 attempts:3 time:1h</gray>")));
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.help.example2", "<gray> /mp codepane key add admin 7777 whitelist:Steve,Alex</gray>")));
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.help.example3", "<gray> /mp codepane key add warp 4321 command:(say %entity% got access)</gray>")));
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.help.example4", "<gray> /mp codepane key add warp 4321 command:(say %entity%),(mvwarp spawn)</gray>")));
    }

    // =========================
    // KEY ADD
    // =========================
    private static void handleAdd(CommandSender sender, String[] args) {
        if (sender instanceof Player p && !p.hasPermission("ui.command.codepane.key.add")) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.no_permission_add", "<red>❌ You don't have permission to add keys!</red>")));
            return;
        }
        if (args.length < 5) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.usage_add", "<red>❌ Usage: </red><white>/mp codepane key add <name> <code> [flags]</white>")));
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
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.duplicate_flag", "<red>❌ Duplicate flag: %flag%! Use each flag only once.</red>").replace("%flag%", "attempts")));
                    return;
                }
                seenFlags.add("attempts");
                try {
                    maxAttempts = Integer.parseInt(flag.substring(9));
                    if (maxAttempts < 1) {
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.invalid_attempts", "<red>❌ attempts must be >= 1</red>")));
                        return;
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.invalid_attempts_format", "<red>❌ Invalid attempts format: %value%</red>").replace("%value%", flag)));
                    return;
                }
            } else if (flag.startsWith("time:")) {
                if (seenFlags.contains("time")) {
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.duplicate_flag", "<red>❌ Duplicate flag: %flag%! Use each flag only once.</red>").replace("%flag%", "time")));
                    return;
                }
                seenFlags.add("time");
                expiresAt = parseTimeFlag(flag.substring(5));
                if (expiresAt == 0) {
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.invalid_time_format", "<red>❌ Invalid time format: </red><gray>%value%</gray><red> (use Ns, Nm, Nh, Nd)</red>").replace("%value%", flag.substring(5))));
                    return;
                }
            } else if (flag.startsWith("whitelist:")) {
                if (seenFlags.contains("whitelist")) {
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.duplicate_flag", "<red>❌ Duplicate flag: %flag%! Use each flag only once.</red>").replace("%flag%", "whitelist")));
                    return;
                }
                seenFlags.add("whitelist");
                whitelistStr = parseListFlag(args, i, "whitelist:");
                int consumed = countListFlagArgs(args, i, "whitelist:");
                if (consumed > 0) i += consumed;
            } else if (flag.startsWith("blacklist:")) {
                if (seenFlags.contains("blacklist")) {
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.duplicate_flag", "<red>❌ Duplicate flag: %flag%! Use each flag only once.</red>").replace("%flag%", "blacklist")));
                    return;
                }
                seenFlags.add("blacklist");
                blacklistStr = parseListFlag(args, i, "blacklist:");
                int consumed = countListFlagArgs(args, i, "blacklist:");
                if (consumed > 0) i += consumed;
            } else if (flag.startsWith("command:")) {
                if (seenFlags.contains("command")) {
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.duplicate_flag", "<red>❌ Duplicate flag: %flag%! Use each flag only once.</red>").replace("%flag%", "command")));
                    return;
                }
                seenFlags.add("command");
                String parsed = parseCommandFlag(args, i);
                if (parsed != null) commandStr = parsed;
                // Count extra args consumed by parenthesized syntax
                int consumed = countCommandFlagArgs(args, i);
                if (consumed > 0) i += consumed;
            } else {
                sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.unknown_flag", "<yellow>⚠</yellow> <gray>Unknown flag: </gray><white>%flag%</white>").replace("%flag%", flag)));
            }
        }

        // Check if key already exists
        if (CodePanelDatabase.keyExists(keyName)) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.already_exists", "<red>❌ Key</red> <yellow>%name%</yellow> <red>already exists!</red>").replace("%name%", keyName)));
            return;
        }

        boolean success = CodePanelDatabase.addKey(keyName, code, commandStr,
                maxAttempts, expiresAt, whitelistStr, blacklistStr);

        if (!success) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.db_add_error", "<red>❌ Error adding key to database!</red>")));
            return;
        }

        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.add_success", "<green>✔</green> <white>Key</white> <yellow>%name%</yellow> <white>added to database!</white>").replace("%name%", keyName)));
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.info_code", "<gray>Code:</gray> <white>%code%</white>").replace("%code%", code)));
        if (maxAttempts > 0) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.info_max_attempts", "<gray>Max attempts:</gray> <white>%max%</white>").replace("%max%", String.valueOf(maxAttempts))));
        }
        if (expiresAt > 0) {
            String dateStr = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss")
                    .format(new java.util.Date(expiresAt));
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.info_expires_at", "<gray>Expires at:</gray> <white>%date%</white>").replace("%date%", dateStr)));
        }
        if (!whitelistStr.isEmpty()) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.info_whitelist", "<gray>Whitelist:</gray> <green>%players%</green>").replace("%players%", whitelistStr)));
        }
        if (!blacklistStr.isEmpty()) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.info_blacklist", "<gray>Blacklist:</gray> <red>%players%</red>").replace("%players%", blacklistStr)));
        }
        if (!commandStr.isEmpty()) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.info_commands", "<gray>Commands:</gray> <white>%commands%</white>").replace("%commands%", commandStr)));
        }
    }

    // =========================
    // KEY LIST
    // =========================
    private static void handleList(CommandSender sender) {
        if (sender instanceof Player p && !p.hasPermission("ui.command.codepane.key.list")) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.no_permission_list", "<red>❌ You don't have permission to list keys!</red>")));
            return;
        }

        List<CodePanelDatabase.CodePanelKey> keys = CodePanelDatabase.getAllKeys();

        if (keys.isEmpty()) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.no_keys", "<yellow>ℹ</yellow> <white>No keys in the database.</white>")));
            return;
        }

        sender.sendMessage("");
        sender.sendMessage(MessageUtil.parse("<gold>══════════════════════════════════</gold>"));
        sender.sendMessage(MessageUtil.parse("<gold>  ✦ </gold><white>Code Panel Keys </white><gray>(" + keys.size() + ")</gray>"));
        sender.sendMessage(MessageUtil.parse("<gold>══════════════════════════════════</gold>"));

        for (CodePanelDatabase.CodePanelKey key : keys) {
            sender.sendMessage("");
            sender.sendMessage(MessageUtil.parse("<gray>┌─ </gray><yellow>" + key.keyName + "</yellow>"));
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.info_code", "<gray>Code:</gray> <white>%code%</white>").replace("%code%", key.code)));

            if (key.command != null && !key.command.isEmpty()) {
                sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.info_commands", "<gray>Commands:</gray> <white>%commands%</white>").replace("%commands%", key.command)));
            }

            if (!key.whitelist.isEmpty()) {
                String players = String.join("<gray>, </gray><green>", key.whitelist);
                sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.info_whitelist", "<gray>Whitelist:</gray> <green>%players%</green>").replace("%players%", players)));
            }
            if (!key.blacklist.isEmpty()) {
                String players = String.join("<gray>, </gray><red>", key.blacklist);
                sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.info_blacklist", "<gray>Blacklist:</gray> <red>%players%</red>").replace("%players%", players)));
            }

            if (key.maxAttempts > 0) {
                int left = key.maxAttempts - key.attemptsUsed;
                String color = left <= 1 ? "<red>" : left <= 3 ? "<yellow>" : "<green>";
                sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.info_attempts", "<gray>Attempts:</gray> %color%%remaining%<gray>/%max%</gray>")
                        .replace("%color%", color)
                        .replace("%remaining%", String.valueOf(left))
                        .replace("%max%", String.valueOf(key.maxAttempts))));
            }

            if (key.expiresAt > 0) {
                long remain = key.expiresAt - System.currentTimeMillis();
                if (remain <= 0) {
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.info_expired", "<gray>Expires:</gray> <red>expired</red>")));
                } else {
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.info_expires_in", "<gray>Expires:</gray> <white>%duration%</white>").replace("%duration%", formatDuration(remain))));
                }
            }
        }

        sender.sendMessage("");
        sender.sendMessage(MessageUtil.parse("<gold>══════════════════════════════════</gold>"));
        sender.sendMessage("");
    }

    // =========================
    // KEY REMOVE
    // =========================
    private static void handleRemove(CommandSender sender, String[] args) {
        if (sender instanceof Player p && !p.hasPermission("ui.command.codepane.key.remove")) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.no_permission_remove", "<red>❌ You don't have permission to remove keys!</red>")));
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.usage_remove", "<red>❌ Usage: </red><white>/mp codepane key remove <name></white>")));
            return;
        }

        String keyName = args[3];

        if (!CodePanelDatabase.keyExists(keyName)) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.key_not_found", "<red>❌ Key</red> <yellow>%name%</yellow> <red>not found!</red>").replace("%name%", keyName)));
            return;
        }

        CodePanelDatabase.removeKey(keyName);
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.remove_success", "<green>✔</green> <white>Key</white> <yellow>%name%</yellow> <white>removed from database.</white>").replace("%name%", keyName)));
    }

    // =========================
    // KEY MODIFY
    // =========================
    private static void handleModify(CommandSender sender, String[] args) {
        if (sender instanceof Player p && !p.hasPermission("ui.command.codepane.key.modify")) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.no_permission_modify", "<red>❌ You don't have permission to modify keys!</red>")));
            return;
        }
        if (args.length < 5) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.usage_modify", "<red>❌ Usage: </red><white>/mp codepane key modify <name> <new_code> [flags]</white>")));
            return;
        }

        String keyName = args[3];
        String newCode = args[4];
        String commandStrOverride = null;

        if (!CodePanelDatabase.keyExists(keyName)) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.db_not_found", "<red>❌ Key</red> <yellow>%name%</yellow> <red>not found in database!</red>").replace("%name%", keyName)));
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
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.duplicate_flag", "<red>❌ Duplicate flag: %flag%! Use each flag only once.</red>").replace("%flag%", "attempts")));
                    return;
                }
                seenFlags.add("attempts");
                try {
                    maxAttempts = Integer.parseInt(flag.substring(9));
                    if (maxAttempts < 1) {
                        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.invalid_attempts", "<red>❌ attempts must be >= 1</red>")));
                        return;
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.invalid_attempts_format", "<red>❌ Invalid attempts format: %value%</red>").replace("%value%", flag)));
                    return;
                }
            } else if (flag.startsWith("time:")) {
                if (seenFlags.contains("time")) {
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.duplicate_flag", "<red>❌ Duplicate flag: %flag%! Use each flag only once.</red>").replace("%flag%", "time")));
                    return;
                }
                seenFlags.add("time");
                expiresAt = parseTimeFlag(flag.substring(5));
                if (expiresAt == 0) {
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.invalid_time_format", "<red>❌ Invalid time format: </red><gray>%value%</gray><red> (use Ns, Nm, Nh, Nd)</red>").replace("%value%", flag.substring(5))));
                    return;
                }
            } else if (flag.startsWith("whitelist:")) {
                if (seenFlags.contains("whitelist")) {
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.duplicate_flag", "<red>❌ Duplicate flag: %flag%! Use each flag only once.</red>").replace("%flag%", "whitelist")));
                    return;
                }
                seenFlags.add("whitelist");
                whitelistStr = parseListFlag(args, i, "whitelist:");
                int consumed = countListFlagArgs(args, i, "whitelist:");
                if (consumed > 0) i += consumed;
            } else if (flag.startsWith("blacklist:")) {
                if (seenFlags.contains("blacklist")) {
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.duplicate_flag", "<red>❌ Duplicate flag: %flag%! Use each flag only once.</red>").replace("%flag%", "blacklist")));
                    return;
                }
                seenFlags.add("blacklist");
                blacklistStr = parseListFlag(args, i, "blacklist:");
                int consumed = countListFlagArgs(args, i, "blacklist:");
                if (consumed > 0) i += consumed;
            } else if (flag.startsWith("command:")) {
                if (seenFlags.contains("command")) {
                    sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.duplicate_flag", "<red>❌ Duplicate flag: %flag%! Use each flag only once.</red>").replace("%flag%", "command")));
                    return;
                }
                seenFlags.add("command");
                hasCommandFlag = true;
                String parsed = parseCommandFlag(args, i);
                if (parsed != null) commandStrOverride = parsed;
                int consumed = countCommandFlagArgs(args, i);
                if (consumed > 0) i += consumed;
            } else {
                sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.unknown_flag", "<yellow>⚠</yellow> <gray>Unknown flag: </gray><white>%flag%</white>").replace("%flag%", flag)));
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

        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.modify_success", "<green>✔</green> <white>Key</white> <yellow>%name%</yellow> <white>modified in database.</white>").replace("%name%", keyName)));
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.info_code", "<gray>Code:</gray> <white>%code%</white>").replace("%code%", newCode)));
        if (maxAttempts > 0) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.info_max_attempts", "<gray>Max attempts:</gray> <white>%max%</white>").replace("%max%", String.valueOf(maxAttempts))));
        }
        if (expiresAt > 0) {
            String dateStr = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss")
                    .format(new java.util.Date(expiresAt));
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.info_expires_at", "<gray>Expires at:</gray> <white>%date%</white>").replace("%date%", dateStr)));
        }
        if (!whitelistStr.isEmpty()) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.info_whitelist", "<gray>Whitelist:</gray> <green>%players%</green>").replace("%players%", whitelistStr)));
        }
        if (!blacklistStr.isEmpty()) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.info_blacklist", "<gray>Blacklist:</gray> <red>%players%</red>").replace("%players%", blacklistStr)));
        }
        if (!commandStr.isEmpty()) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("codepane.key.info_commands", "<gray>Commands:</gray> <white>%commands%</white>").replace("%commands%", commandStr)));
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
