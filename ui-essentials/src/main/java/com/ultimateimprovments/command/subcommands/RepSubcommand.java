package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.command.RepDialogScreen;
import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.reputation.ReputationManager;
import com.ultimateimprovments.reputation.ReputationManager.Status;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.PlayerDataIO;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /ui rep — two-scale reputation system:
 * <ul>
 *   <li>numeric reputation (integer, staff-issued);</li>
 *   <li>Account Standing status — Discord account standings (All good,
 *       Limited, Very limited, At risk, Suspended), issued separately by a
 *       moderator.</li>
 * </ul>
 * <pre>
 *   /ui rep [player]              — view rep + status (all players)
 *   /ui rep give &lt;player&gt; &lt;±N&gt; [reason] — change rep (ui.command.rep.give)
 *   /ui rep set &lt;player&gt; &lt;N&gt;      — absolute set (ui.command.rep.set)
 *   /ui rep status &lt;player&gt; &lt;allgood|limited|verylimited|atrisk|suspended|none&gt; — Account Standing (ui.command.rep.status)
 *   /ui rep top [limit]           — top by numeric rep
 *   /ui rep history [player]      — last changes (own for players, any for staff)
 * </pre>
 * Permissions: view {@code ui.command.rep}, give {@code ui.command.rep.give},
 * set {@code ui.command.rep.set}, status {@code ui.command.rep.status}.
 */
public final class RepSubcommand {

    private RepSubcommand() {}

    private static final String PERM_VIEW = "ui.command.rep";
    private static final String PERM_GIVE = "ui.command.rep.give";
    private static final String PERM_SET = "ui.command.rep.set";
    private static final String PERM_STATUS = "ui.command.rep.status";
    private static final String PERM_HISTORY_OTHER = "ui.command.rep.history.other";

    public static boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERM_VIEW)) {
            CommandErrors.noPermission(sender);
            return true;
        }

        // /ui rep [player]
        if (args.length < 2) {
            if (sender instanceof Player p) {
                showSelf(p, p.getUniqueId().toString(), p.getName());
            } else {
                usage(sender);
            }
            return true;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> give(sender, args);
            case "set" -> set(sender, args);
            case "status" -> status(sender, args);
            case "top" -> top(sender, args);
            case "history" -> history(sender, args);
            default -> viewOther(sender, args[1]);
        }
        return true;
    }

    // =========================================================================
    // VIEW
    // =========================================================================

    private static void showSelf(Player p, String uuid, String name) {
        ReputationManager.get(uuid, data ->
                RepDialogScreen.open(p, name, data.rep(), data.status()));
    }

    private static void viewOther(CommandSender sender, String name) {
        UUID uuid = PlayerDataIO.resolveUuidByName(name);
        if (uuid == null) {
            sender.sendMessage(MessageUtil.parse(msg("reputation.errors.unknown_player",
                    "<red>❌ Player </red><yellow>%player%</yellow> <red>not found!</red>")
                    .replace("%player%", name)));
            return;
        }
        String targetName = displayName(uuid, name);
        ReputationManager.get(uuid.toString(), data -> {
            if (sender instanceof Player viewer) {
                RepDialogScreen.open(viewer, targetName, data.rep(), data.status());
            } else {
                // Console — no screen, print to chat
                sender.sendMessage(MessageUtil.parse(header(targetName)));
                sender.sendMessage(MessageUtil.parse(lineRep(data.rep())));
                sender.sendMessage(MessageUtil.parse(lineStatus(data.status())));
            }
        });
    }

    private static String header(String name) {
        return msg("reputation.view.header",
                "<gray>═══ <white>Reputation — </white><yellow>%player%</yellow> <gray>═══</gray>")
                .replace("%player%", name);
    }

    private static String lineRep(int rep) {
        String color = RepDialogScreen.repColor(rep);
        return msg("reputation.view.rep", "<gray>Reputation:</gray> " + color + "%rep%</gray>")
                .replace("%rep%", String.valueOf(rep));
    }

    private static String lineStatus(Status status) {
        return msg("reputation.view.status", "<gray>Status:</gray> %status%")
                .replace("%status%", status.fullMini());
    }

    // =========================================================================
    // GIVE
    // =========================================================================

    private static void give(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERM_GIVE)) {
            CommandErrors.noPermission(sender);
            return;
        }
        if (args.length < 4) {
            usage(sender);
            return;
        }
        String name = args[2];
        int amount;
        try {
            amount = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtil.parse(msg("reputation.errors.invalid_amount",
                    "<red>❌ Amount must be an integer (±N)!</red>")));
            return;
        }
        if (amount == 0) {
            sender.sendMessage(MessageUtil.parse(msg("reputation.errors.zero_amount",
                    "<red>❌ Amount must not be zero!</red>")));
            return;
        }
        if (amount > 100 || amount < -100) {
            sender.sendMessage(MessageUtil.parse(msg("reputation.errors.too_large",
                    "<red>❌ Single change is limited to ±100!</red>")));
            return;
        }
        UUID uuid = PlayerDataIO.resolveUuidByName(name);
        if (uuid == null) {
            unknownPlayer(sender, name);
            return;
        }
        String actor = sender.getName();
        String reason = join(args, 4);
        String targetName = displayName(uuid, name);
        ReputationManager.change(uuid.toString(), targetName, amount, "manual", reason,
                actor, true, newVal -> sender.sendMessage(MessageUtil.parse(
                        msg(amount >= 0 ? "reputation.given" : "reputation.taken",
                                amount >= 0
                                        ? "<green>✔</green> <white>Reputation </white><yellow>%player%</yellow><white>: </white><green>+%amount%</green> <gray>(now %new%)</gray>"
                                        : "<green>✔</green> <white>Reputation </white><yellow>%player%</yellow><white>: </white><red>%amount%</red> <gray>(now %new%)</gray>")
                                .replace("%player%", targetName)
                                .replace("%amount%", String.valueOf(amount))
                                .replace("%new%", String.valueOf(newVal)))));
    }

    // =========================================================================
    // SET
    // =========================================================================

    private static void set(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERM_SET)) {
            CommandErrors.noPermission(sender);
            return;
        }
        if (args.length < 4) {
            usage(sender);
            return;
        }
        String name = args[2];
        int value;
        try {
            value = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtil.parse(msg("reputation.errors.invalid_amount",
                    "<red>❌ Amount must be an integer (±N)!</red>")));
            return;
        }
        UUID uuid = PlayerDataIO.resolveUuidByName(name);
        if (uuid == null) {
            unknownPlayer(sender, name);
            return;
        }
        String actor = sender.getName();
        String reason = join(args, 4);
        String targetName = displayName(uuid, name);
        ReputationManager.set(uuid.toString(), targetName, value, reason, actor, true,
                newVal -> sender.sendMessage(MessageUtil.parse(
                        msg("reputation.set", "<green>✔</green> <white>Reputation of </white><yellow>%player%</yellow><white> set to </white><green>%new%</green>")
                                .replace("%player%", targetName)
                                .replace("%new%", String.valueOf(newVal)))));
    }

    // =========================================================================
    // STATUS (Discord-style, second scale)
    // =========================================================================

    private static void status(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERM_STATUS)) {
            CommandErrors.noPermission(sender);
            return;
        }
        if (args.length < 4) {
            usage(sender);
            return;
        }
        String name = args[2];
        Status status = Status.fromString(args[3]);
        if (status == null) {
            sender.sendMessage(MessageUtil.parse(msg("reputation.errors.unknown_status",
                    "<red>❌ Unknown status! Available: </red><white>allgood, limited, verylimited, atrisk, suspended, none</white>")));
            return;
        }
        UUID uuid = PlayerDataIO.resolveUuidByName(name);
        if (uuid == null) {
            unknownPlayer(sender, name);
            return;
        }
        String actor = sender.getName();
        String targetName = displayName(uuid, name);
        ReputationManager.setStatus(uuid.toString(), status, actor, true, applied ->
                sender.sendMessage(MessageUtil.parse(
                        msg("reputation.status_set", "<green>✔</green> <white>Status of </white><yellow>%player%</yellow><white>: </white>%status%")
                                .replace("%player%", targetName)
                                .replace("%status%", applied.fullMini()))));
    }

    // =========================================================================
    // TOP
    // =========================================================================

    private static void top(CommandSender sender, String[] args) {
        int limit = 10;
        if (args.length >= 3) {
            try {
                limit = Math.max(1, Math.min(25, Integer.parseInt(args[2])));
            } catch (NumberFormatException ignored) {
                // keep default
            }
        }
        final int finLimit = limit;
        ReputationManager.getTop(finLimit, rows -> {
            sender.sendMessage(MessageUtil.parse(msg("reputation.top.header",
                    "<gray>═══ <white>Reputation Top</white> ═══</gray>")));
            if (rows.isEmpty()) {
                sender.sendMessage(MessageUtil.parse(msg("reputation.top.empty",
                        "<gray>No reputation records yet.</gray>")));
                return;
            }
            for (int i = 0; i < rows.size(); i++) {
                String[] row = rows.get(i);
                int place = i + 1;
                String medal = switch (place) {
                    case 1 -> "<gold>🥇";
                    case 2 -> "<white>🥈";
                    case 3 -> "<gold>🥉";
                    default -> "<dark_gray>" + place + ".";
                };
                sender.sendMessage(MessageUtil.parse(
                        msg("reputation.top.row", "%place% <yellow>%player%</yellow><gray>:</gray> <white>%rep%</white>")
                                .replace("%place%", medal)
                                .replace("%player%", row[0])
                                .replace("%rep%", row[1])));
            }
        });
    }

    // =========================================================================
    // HISTORY
    // =========================================================================

    private static void history(CommandSender sender, String[] args) {
        String name;
        String uuidStr;
        if (args.length >= 3) {
            // other player — staff only
            if (!(sender instanceof Player p) || !p.hasPermission(PERM_HISTORY_OTHER)) {
                CommandErrors.noPermission(sender);
                return;
            }
            name = args[2];
            UUID uuid = PlayerDataIO.resolveUuidByName(name);
            if (uuid == null) {
                unknownPlayer(sender, name);
                return;
            }
            uuidStr = uuid.toString();
            name = displayName(uuid, name);
        } else {
            if (!(sender instanceof Player p)) {
                usage(sender);
                return;
            }
            uuidStr = p.getUniqueId().toString();
            name = p.getName();
        }
        final String finName = name;
        final String finUuid = uuidStr;
        ReputationManager.getHistory(finUuid, 10, entries -> {
            sender.sendMessage(MessageUtil.parse(
                    msg("reputation.history.header",
                            "<gray>═══ <white>Reputation History — </white><yellow>%player%</yellow> <gray>═══</gray>")
                            .replace("%player%", finName)));
            if (entries.isEmpty()) {
                sender.sendMessage(MessageUtil.parse(msg("reputation.history.empty",
                        "<gray>No changes recorded.</gray>")));
                return;
            }
            for (ReputationManager.RepLogEntry e : entries) {
                String arrow = e.amount() >= 0 ? "<green>+" : "<red>";
                sender.sendMessage(MessageUtil.parse(
                        msg("reputation.history.row",
                                "<dark_gray>#%id%</dark_gray> %arrow%%amount%</reset> <gray>— %actor% (%source%)</gray><dark_gray> — %reason%</dark_gray>")
                                .replace("%id%", String.valueOf(e.id()))
                                .replace("%arrow%", arrow)
                                .replace("%amount%", String.valueOf(e.amount()))
                                .replace("%actor%", e.actorName())
                                .replace("%source%", e.source())
                                .replace("%reason%", e.reason().isEmpty() ? "—" : e.reason())));
            }
        });
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private static void unknownPlayer(CommandSender sender, String name) {
        sender.sendMessage(MessageUtil.parse(msg("reputation.errors.unknown_player",
                "<red>❌ Player </red><yellow>%player%</yellow> <red>not found!</red>")
                .replace("%player%", name)));
    }

    private static void usage(CommandSender sender) {
        sender.sendMessage(MessageUtil.parse(msg("reputation.usage",
                "<red>❌ Usage: </red><white>/ui rep [player] | give &lt;player&gt; &lt;±N&gt; [reason] | "
                        + "set &lt;player&gt; &lt;N&gt; | status &lt;player&gt; &lt;online|idle|dnd|invisible|none&gt; | "
                        + "top [limit] | history [player]</white>")));
    }

    private static String join(String[] args, int from) {
        if (args.length <= from) return "";
        return String.join(" ", java.util.Arrays.copyOfRange(args, from, args.length));
    }

    /** Best display name: online, then offline (by uuid), then the typed name. */
    private static String displayName(UUID uuid, String fallback) {
        org.bukkit.entity.Player online = org.bukkit.Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        org.bukkit.OfflinePlayer off = org.bukkit.Bukkit.getOfflinePlayer(uuid);
        String n = off.getName();
        return n != null ? n : fallback;
    }

    private static String msg(String path, String def) {
        return MessagesManager.getString(path, def);
    }

    // =========================================================================
    // TAB COMPLETE
    // =========================================================================

    public static List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 2) {
            String token = args[1].toLowerCase(Locale.ROOT);
            for (String s : List.of("give", "set", "status", "top", "history")) {
                if (s.startsWith(token)) out.add(s);
            }
            // player names for the default view
            if (!out.contains(args[1]) && !args[1].isEmpty()) out.add(args[1]);
            for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(token)) out.add(p.getName());
            }
        } else if (args.length == 3 && !args[1].equalsIgnoreCase("top")) {
            String token = args[2].toLowerCase(Locale.ROOT);
            for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(token)) out.add(p.getName());
            }
        } else if (args.length == 4 && args[1].equalsIgnoreCase("status")) {
            String token = args[3].toLowerCase(Locale.ROOT);
            for (String s : List.of("allgood", "limited", "verylimited", "atrisk", "suspended", "none")) {
                if (s.startsWith(token)) out.add(s);
            }
        }
        return out;
    }
}
