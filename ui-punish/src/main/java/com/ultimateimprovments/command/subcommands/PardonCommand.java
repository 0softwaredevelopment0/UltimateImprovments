package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.core.Permissions;
import com.ultimateimprovments.punish.PardonManager;
import com.ultimateimprovments.punish.PardonMessages;
import com.ultimateimprovments.punish.PunishmentManager;
import com.ultimateimprovments.util.AlertBroadcast;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 🕊 PardonCommand — {@code /ui punish pardon}, the unified way to LIFT punishments.
 * <pre>
 * /ui punish pardon &lt;ban|mute|block|warn|all&gt; &lt;player|all&gt; [-r:reason] [-id:N] [-s] [-dry] [-confirm]
 * </pre>
 * <p>
 * This command REPLACES {@code /ui punish unban|unmute|unblock|unwarn} (removed).
 * All removal logic lives in {@link PardonManager} — the command only parses
 * arguments, checks permissions and renders the outcome.
 * <p>
 * Permissions ({@code ui.command.punish.pardon.<action>}):
 * <ul>
 *   <li>{@code unban} / {@code unmute} / {@code unblock} / {@code unwarn} — the
 *       matching punishment type (a {@code pardon all} requires all four);</li>
 *   <li>{@code mass} — target {@code all} (every online player);</li>
 *   <li>{@code confirm} — confirming a batch above {@code punish.pardon.confirm_threshold}.</li>
 * </ul>
 */
public final class PardonCommand {

    private PardonCommand() {}

    /** Selectable punishment types, in suggestion order. */
    public static final List<String> TYPES = List.of("ban", "mute", "block", "warn", "all");

    /** How many matched records are printed in a preview / result list. */
    private static final int MAX_LINES = 20;

    /** Every type a {@code pardon all} selects (kicks are log entries, never pardoned). */
    private static final List<PunishmentManager.PunishType> ALL_TYPES = List.of(
            PunishmentManager.PunishType.BAN,
            PunishmentManager.PunishType.MUTE,
            PunishmentManager.PunishType.BLOCK,
            PunishmentManager.PunishType.WARN);

    public static boolean execute(CommandSender sender, String[] args) {
        // args[0] = "punish", args[1] = "pardon", args[2] = type, args[3] = player
        if (args.length < 4) {
            sender.sendMessage(MessageUtil.parse(PardonMessages.usage()));
            return true;
        }

        String typeArg = args[2].toLowerCase(Locale.ROOT);
        if ("kick".equals(typeArg)) {
            sender.sendMessage(MessageUtil.parse(PardonMessages.kickNotPardonable()));
            return true;
        }

        Set<PunishmentManager.PunishType> types = typesOf(typeArg);
        if (types == null) {
            sender.sendMessage(MessageUtil.parse(PardonMessages.unknownType(typeArg)));
            return true;
        }

        // Per-action permissions: lifting a type needs the matching pardon node.
        for (PunishmentManager.PunishType type : types) {
            String node = actionPermission(type);
            if (!sender.hasPermission(node)) {
                CommandErrors.noPermission(sender, node);
                return true;
            }
        }

        String targetArg = args[3];
        boolean allOnline = targetArg.equalsIgnoreCase("all") || targetArg.equals("*");
        if (allOnline && !sender.hasPermission(Permissions.CMD_PUNISH_PARDON_MASS)) {
            CommandErrors.noPermission(sender, Permissions.CMD_PUNISH_PARDON_MASS);
            return true;
        }

        Flags flags = parseFlags(sender, args, 4);
        if (flags == null) return true;
        if (flags.confirm && !sender.hasPermission(Permissions.CMD_PUNISH_PARDON_CONFIRM)) {
            CommandErrors.noPermission(sender, Permissions.CMD_PUNISH_PARDON_CONFIRM);
            return true;
        }

        PardonManager.Target target = allOnline
                ? PardonManager.Target.online()
                : resolveTarget(targetArg);
        if (flags.reason == null || flags.reason.isBlank()) {
            sender.sendMessage(MessageUtil.parse(PardonMessages.reasonRequired()));
            return true;
        }
        String reason = flags.reason.trim();

        PardonManager.Outcome outcome = PardonManager.pardon(new PardonManager.Request(
                types, target, reason, sender.getName(),
                flags.silent, flags.dryRun, flags.confirm, flags.id));

        render(sender, args, typeArg, targetArg, types, target, reason, outcome, flags);
        return true;
    }

    // =========================
    // TYPE RESOLUTION
    // =========================

    /** Maps {@code ban|mute|block|warn|all} to the punish types; null for an unknown type. */
    private static Set<PunishmentManager.PunishType> typesOf(String typeArg) {
        return switch (typeArg) {
            case "ban" -> Set.of(PunishmentManager.PunishType.BAN);
            case "mute" -> Set.of(PunishmentManager.PunishType.MUTE);
            case "block" -> Set.of(PunishmentManager.PunishType.BLOCK);
            case "warn" -> Set.of(PunishmentManager.PunishType.WARN);
            case "all" -> new LinkedHashSet<>(ALL_TYPES);
            default -> null;
        };
    }

    /** The {@code ui.command.punish.pardon.<action>} node guarding one punishment type. */
    private static String actionPermission(PunishmentManager.PunishType type) {
        return switch (type) {
            case BAN -> Permissions.CMD_PUNISH_PARDON_UNBAN;
            case MUTE -> Permissions.CMD_PUNISH_PARDON_UNMUTE;
            case BLOCK -> Permissions.CMD_PUNISH_PARDON_UNBLOCK;
            case WARN -> Permissions.CMD_PUNISH_PARDON_UNWARN;
            case KICK -> Permissions.CMD_PUNISH_PARDON;
        };
    }

    /** Resolves an online player, or an offline target via the shared name→UUID resolver. */
    private static PardonManager.Target resolveTarget(String targetName) {
        @SuppressWarnings("deprecation")
        Player online = Bukkit.getPlayerExact(targetName);
        if (online != null) {
            return PardonManager.Target.of(online.getUniqueId().toString(), online.getName());
        }
        UUID resolved = com.ultimateimprovments.util.PlayerDataIO.resolveUuidByName(targetName);
        return PardonManager.Target.of(
                resolved != null ? resolved.toString() : "offline:" + targetName.toLowerCase(Locale.ROOT),
                targetName);
    }

    // =========================
    // FLAGS
    // =========================

    /** Parsed {@code /ui punish pardon} flags. */
    private static final class Flags {
        String reason;
        boolean silent;
        boolean dryRun;
        boolean confirm;
        Integer id;
    }

    /**
     * Parses {@code -r:<reason>} (aliases {@code -reason:}), {@code -s}/{@code -silent},
     * {@code -dry}/{@code -dryrun}, {@code -confirm} and {@code -id:<n>}.
     * Returns null after printing an error for a malformed flag.
     */
    private static Flags parseFlags(CommandSender sender, String[] args, int start) {
        Flags flags = new Flags();
        for (int i = start; i < args.length; i++) {
            String arg = args[i];
            String lower = arg.toLowerCase(Locale.ROOT);

            if (lower.startsWith("-r:") || lower.startsWith("-reason:")) {
                String value = unquote(arg.substring(arg.indexOf(':') + 1)).trim();
                if (value.isEmpty()) {
                    sender.sendMessage(MessageUtil.parse(PardonMessages.reasonRequired()));
                    return null;
                }
                flags.reason = flags.reason == null ? value : flags.reason + " " + value;
            } else if (lower.equals("-r") || lower.equals("-reason")) {
                // -r "<text>" — the value is the NEXT argument (quotes kept by the tokenizer)
                if (i + 1 >= args.length) {
                    sender.sendMessage(MessageUtil.parse(PardonMessages.reasonRequired()));
                    return null;
                }
                String value = unquote(args[++i]).trim();
                if (value.isEmpty()) {
                    sender.sendMessage(MessageUtil.parse(PardonMessages.reasonRequired()));
                    return null;
                }
                flags.reason = flags.reason == null ? value : flags.reason + " " + value;
            } else if (lower.equals("-s") || lower.equals("-silent")) {
                flags.silent = true;
            } else if (lower.equals("-dry") || lower.equals("-dryrun") || lower.equals("-dry-run")) {
                flags.dryRun = true;
            } else if (lower.equals("-confirm") || lower.equals("-yes")) {
                flags.confirm = true;
            } else if (lower.startsWith("-id:")) {
                String raw = unquote(arg.substring(4)).trim();
                try {
                    int id = Integer.parseInt(raw);
                    if (id <= 0) throw new NumberFormatException("non-positive");
                    flags.id = id;
                } catch (NumberFormatException e) {
                    sender.sendMessage(MessageUtil.parse(PardonMessages.invalidId(raw)));
                    return null;
                }
            } else {
                sender.sendMessage(MessageUtil.parse(PardonMessages.unknownFlag(arg)));
                return null;
            }
        }
        return flags;
    }

    /** Strips surrounding double quotes kept by the dispatcher's tokenizer. */
    private static String unquote(String value) {
        if (value == null || value.length() < 2) return value == null ? "" : value;
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    // =========================
    // RENDERING
    // =========================

    private static void render(CommandSender sender, String[] args, String typeArg, String targetArg,
                               Set<PunishmentManager.PunishType> types, PardonManager.Target target,
                               String reason, PardonManager.Outcome outcome, Flags flags) {
        String targetLabel = target.label();
        String typesLabel = PardonMessages.typesLabel(types);

        // Nothing matched — not an error.
        if (outcome.isEmpty()) {
            sender.sendMessage(MessageUtil.parse(PardonMessages.nothingFound(typesLabel, targetLabel)));
            return;
        }

        // Preview: too many records, a mass pardon, or an explicit -dry.
        if (outcome.previewOnly()) {
            sender.sendMessage(MessageUtil.parse(
                    PardonMessages.previewHeader(outcome.matched().size(), targetLabel)));
            printLines(sender, outcome);
            if (outcome.needsConfirmation()) {
                sender.sendMessage(MessageUtil.parse(
                        PardonMessages.confirmHint(confirmCommand(args, typeArg, targetArg))));
            }
            if (outcome.dryRun()) {
                sender.sendMessage(MessageUtil.parse(PardonMessages.dryRun()));
            }
            return;
        }

        // Applied.
        sender.sendMessage(MessageUtil.parse(PardonMessages.result(outcome.lifted(), targetLabel)));
        printLines(sender, outcome);

        notifyTargets(outcome);

        if (!flags.silent) {
            AlertBroadcast.send(PardonMessages.broadcast(
                    targetLabel, typesLabel, sender.getName(), reason));
        }
    }

    /** Prints up to {@value #MAX_LINES} matched records, then a "… and N more" line. */
    private static void printLines(CommandSender sender, PardonManager.Outcome outcome) {
        List<PardonManager.Entry> matched = outcome.matched();
        int shown = Math.min(matched.size(), MAX_LINES);
        for (int i = 0; i < shown; i++) {
            PardonManager.Entry entry = matched.get(i);
            sender.sendMessage(MessageUtil.parse(PardonMessages.line(
                    PardonMessages.typeLabel(entry.type()), entry.id(), entry.playerName(), entry.reason())));
        }
        if (matched.size() > shown) {
            sender.sendMessage(MessageUtil.parse(PardonMessages.previewMore(matched.size() - shown)));
        }
    }

    /** Tells every online pardoned player what was lifted (one message per type). */
    private static void notifyTargets(PardonManager.Outcome outcome) {
        Set<String> notified = new HashSet<>();
        for (PardonManager.Entry entry : outcome.matched()) {
            Player player = onlinePlayer(entry.playerUuid(), entry.playerName());
            if (player == null) continue;
            String key = player.getUniqueId() + "|" + entry.type();
            if (!notified.add(key)) continue;
            player.sendMessage(MessageUtil.parse(PardonMessages.noticeFor(entry.type())));
        }
    }

    /** Online player by UUID, falling back to the recorded name. */
    private static Player onlinePlayer(String uuid, String name) {
        if (uuid != null && !uuid.startsWith("offline:")) {
            try {
                Player byUuid = Bukkit.getPlayer(UUID.fromString(uuid));
                if (byUuid != null && byUuid.isOnline()) return byUuid;
            } catch (IllegalArgumentException ignored) {
                // not a UUID — fall through to the name lookup
            }
        }
        @SuppressWarnings("deprecation")
        Player byName = name != null ? Bukkit.getPlayerExact(name) : null;
        return byName != null && byName.isOnline() ? byName : null;
    }

    /**
     * Rebuilds the same command with {@code -confirm} for the clickable suggestion
     * of a preview. The reason is quoted when it contains spaces so the dispatcher's
     * tokenizer keeps it as one argument.
     */
    private static String confirmCommand(String[] args, String typeArg, String targetArg) {
        StringBuilder command = new StringBuilder("/ui punish pardon ")
                .append(typeArg).append(' ').append(targetArg);

        // Keep -id / -r / -s from the original invocation.
        for (int i = 4; i < args.length; i++) {
            String arg = args[i];
            String lower = arg.toLowerCase(Locale.ROOT);
            if (lower.equals("-confirm") || lower.equals("-yes")
                    || lower.equals("-dry") || lower.equals("-dryrun") || lower.equals("-dry-run")) {
                continue;
            }
            if (lower.startsWith("-r:") || lower.startsWith("-reason:")) {
                String value = unquote(arg.substring(arg.indexOf(':') + 1));
                command.append(" -r:\"").append(value.replace("\"", "")).append('"');
                continue;
            }
            if (lower.equals("-r") || lower.equals("-reason")) {
                String value = i + 1 < args.length ? unquote(args[++i]) : "";
                command.append(" -r:\"").append(value.replace("\"", "")).append('"');
                continue;
            }
            command.append(' ').append(arg);
        }
        return command.append(" -confirm").toString();
    }

    // =========================
    // TAB COMPLETION
    // =========================

    /**
     * Suggestions for the CURRENT (last) argument. The caller (PunishSubcommand)
     * filters them by the typed prefix.
     */
    public static List<String> tabComplete(String[] args) {
        List<String> completions = new ArrayList<>();
        // args[0] = "punish", args[1] = "pardon"
        if (args.length == 3) {
            completions.addAll(TYPES);
        } else if (args.length == 4) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
            completions.add("all");
        } else if (args.length >= 5) {
            boolean hasReason = false, hasId = false, hasSilent = false, hasDry = false, hasConfirm = false;
            for (int i = 4; i < args.length; i++) {
                String lower = args[i].toLowerCase(Locale.ROOT);
                if (lower.startsWith("-r:") || lower.startsWith("-reason:")) hasReason = true;
                else if (lower.startsWith("-id:")) hasId = true;
                else if (lower.equals("-s") || lower.equals("-silent")) hasSilent = true;
                else if (lower.equals("-dry") || lower.equals("-dryrun") || lower.equals("-dry-run")) hasDry = true;
                else if (lower.equals("-confirm") || lower.equals("-yes")) hasConfirm = true;
            }
            if (!hasReason) completions.add("-r:");
            if (!hasId) completions.add("-id:");
            if (!hasSilent) completions.add("-s");
            if (!hasDry) completions.add("-dry");
            if (!hasConfirm) completions.add("-confirm");
        }
        return completions;
    }
}
