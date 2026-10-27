package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.core.Permissions;
import com.ultimateimprovments.punish.CrashExecutor;
import com.ultimateimprovments.punish.PunishmentManager;
import com.ultimateimprovments.punish.PunishJoinListener;
import com.ultimateimprovments.util.AlertBroadcast;
import com.ultimateimprovments.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 🛡 PunishSubcommand — handler for /ui punish.
 * <p>
 * Commands:
 * <pre>
 * /ui punish ban <player> <reason> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw]
 * /ui punish mute <player> <reason> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw]
 * /ui punish kick <player> <reason> [-ip] [-hw]
 * /ui punish warn <player> <reason> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw]
 * /ui punish block <player> <reason> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw]
 * /ui punish listwarns <player>
 * /ui punish pardon <ban|mute|block|warn|all> <player|all> [-r:<reason>] [-id:<n>] [-s] [-dry] [-confirm]
 * /ui punish logs view [-p <page>] [-t <type>] | purge -a|-t <time> — punishment event log
 * </pre>
 * <p>
 * Flag rules:
 * <ul>
 *   <li>-time and -permanent are incompatible (both — error)</li>
 *   <li>If neither -time nor -permanent is given — error (for ban/mute/warn)</li>
 *   <li>-ip and -hw are incompatible</li>
 * </ul>
 */
public final class PunishSubcommand {

    private PunishSubcommand() {}

    // =========================
    // CRASH CONFIRMATION — crashes awaiting confirmation
    // =========================

    /** Players awaiting crash confirmation (sender UUID → data). */
    private static final Map<UUID, PendingCrash> pendingCrashes = new HashMap<>();
    /** Crash confirmation timeout (ms). */
    private static final long CONFIRM_TIMEOUT_MS = 30_000L;

    private record PendingCrash(String method, String targetName, long createdAt) {}

    // =========================
    // BROADCAST TO MODERATORS — sends punishment notifications
    // =========================

    /**
     * Sends a punishment notification via {@link AlertBroadcast}
     * to all players with the {@code ui.alerts} permission (or legacy {@code ui.punish.notify}).
     * Console logging already happens in {@link PunishmentManager#punish}.
     */
    private static void broadcastToModerators(String message) {
        AlertBroadcast.send(message);
    }

    public static boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ui.command.punish")) {
            CommandErrors.noPermission(sender, "ui.command.punish");
            return true;
        }

        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        String action = args[1].toLowerCase();

        return switch (action) {
            case "ban" -> handleBan(sender, args);
            case "block" -> handleBlock(sender, args);
            case "mute" -> handleMute(sender, args);
            case "kick" -> handleKick(sender, args);
            case "warn" -> handleWarn(sender, args);
            case "listwarns" -> handleListWarns(sender, args);
            case "pardon" -> PardonCommand.execute(sender, args);
            case "logs" -> LogsCommand.execute(sender, args);
            case "crash" -> handleCrash(sender, args);
            default -> {
                sendUsage(sender);
                yield true;
            }
        };
    }

    // =========================
    // TARGET RESOLUTION
    // =========================
    /**
     * Resolves a target name to the UUID string used for storing/checking
     * punishments. Online players → their real UUID. Offline targets → the
     * resolver scans Bukkit caches and playerdata files (bukkit.lastKnownName);
     * only when nothing is found does it fall back to the legacy
     * {@code offline:<name>} pseudo-UUID (matched later by player_name).
     * The old code ALWAYS used the pseudo-UUID for offline targets, so a
     * ban/mute issued while the player was offline never matched the real
     * UUID on login — the punishment silently never applied.
     */
    private static String resolveTargetUuid(String targetName) {
        java.util.UUID resolved = com.ultimateimprovments.util.PlayerDataIO.resolveUuidByName(targetName);
        return resolved != null ? resolved.toString() : "offline:" + targetName.toLowerCase();
    }

    // =========================
    // BAN
    // =========================
    private static boolean handleBan(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ui.command.punish.ban")) {
            CommandErrors.noPermission(sender, "ui.command.punish.ban");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/ui punish ban <player> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw] -r \"<reason>\"</white>"
            ));
            return true;
        }

        PunishArgs parsed = parsePunishArgs(sender, args, 3);
        if (parsed == null) return true;

        String targetName = args[2];
        @SuppressWarnings("deprecation")
        Player target = Bukkit.getPlayerExact(targetName);
        String uuid;
        String name;

        if (target != null) {
            uuid = target.getUniqueId().toString();
            name = target.getName();
        } else {
            // Offline player — use the input names
            uuid = resolveTargetUuid(targetName);
            name = targetName;
        }

        String ip = null;
        String hwId = null;

        if (parsed.ip) {
            ip = target != null && target.getAddress() != null
                    ? target.getAddress().getAddress().getHostAddress()
                    : "0.0.0.0";
        }
        if (parsed.hw) {
            String targetIp = target != null && target.getAddress() != null
                    ? target.getAddress().getAddress().getHostAddress()
                    : "0.0.0.0";
            hwId = PunishmentManager.computeHwId(targetIp, name);
        }

        boolean ok = PunishmentManager.punish(
                PunishmentManager.PunishType.BAN,
                uuid, name, parsed.reason,
                sender.getName(), parsed.expiresAt,
                ip, hwId
        );

        if (!ok) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Failed to ban " + name + "</red>"));
            return true;
        }

        // Kick if online
        if (target != null && target.isOnline()) {
            PunishmentManager.PunishmentRecord ban = PunishmentManager.getActiveBan(
                    uuid, ip, hwId);
            if (ban != null) {
                Component kickMsg = MessageUtil.parse(
                        "<red>⛔ You have been banned!</red>\n" +
                        "<gray>Reason:</gray> <white>" + parsed.reason + "</white>\n" +
                        "<dark_gray>By: " + sender.getName() + "</dark_gray>"
                );
                target.kick(kickMsg);
            }
        }

        // If -ip or -hw — kick all matching online players
        if (parsed.ip || parsed.hw) {
            for (Player p : PunishmentManager.findPlayersByIpOrHw(ip, hwId)) {
                if (!p.getName().equalsIgnoreCase(name)) {
                    p.kick(MessageUtil.parse(
                            "<red>⛔ You have been banned (IP/HW)!</red>\n" +
                            "<gray>Reason:</gray> <white>" + parsed.reason + "</white>"
                    ));
                }
            }
        }

        String scope = parsed.ip ? " [IP]" : parsed.hw ? " [HW]" : "";
        sender.sendMessage(MessageUtil.parse(
                "<green>✔</green> <white>Player</white> <yellow>" + name + "</yellow> <white>has been banned.</white>" + scope
        ));

        // Notify operators
        String duration = parsed.isPermanent ? "<red>permanent</red>" : "<yellow>" + parsed.timeStr + "</yellow>";
        broadcastToModerators(
                "<red>⛔</red> <yellow>" + name + "</yellow> <gray>banned by</gray> <white>" + sender.getName() + "</white>" +
                "<gray> | Reason:</gray> <white>" + parsed.reason + "</white>" +
                "<gray> | Duration:</gray> " + duration +
                scope
        );
        return true;
    }

    // =========================
    // MUTE
    // =========================
    private static boolean handleMute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ui.command.punish.mute")) {
            CommandErrors.noPermission(sender, "ui.command.punish.mute");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/ui punish mute <player> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw] -r \"<reason>\"</white>"
            ));
            return true;
        }

        PunishArgs parsed = parsePunishArgs(sender, args, 3);
        if (parsed == null) return true;

        String targetName = args[2];
        @SuppressWarnings("deprecation")
        Player target = Bukkit.getPlayerExact(targetName);
        String uuid;
        String name;

        if (target != null) {
            uuid = target.getUniqueId().toString();
            name = target.getName();
        } else {
            uuid = resolveTargetUuid(targetName);
            name = targetName;
        }

        String ip = null;
        String hwId = null;

        if (parsed.ip) {
            ip = target != null && target.getAddress() != null
                    ? target.getAddress().getAddress().getHostAddress()
                    : "0.0.0.0";
        }
        if (parsed.hw) {
            String targetIp = target != null && target.getAddress() != null
                    ? target.getAddress().getAddress().getHostAddress()
                    : "0.0.0.0";
            hwId = PunishmentManager.computeHwId(targetIp, name);
        }

        boolean ok = PunishmentManager.punish(
                PunishmentManager.PunishType.MUTE,
                uuid, name, parsed.reason,
                sender.getName(), parsed.expiresAt,
                ip, hwId
        );

        if (!ok) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Failed to mute " + name + "</red>"));
            return true;
        }

        // Add to the mute cache (if the target is online)
        if (target != null && target.isOnline()) {
            PunishmentManager.PunishmentRecord muteRecord = PunishmentManager.getActiveMute(
                    uuid, ip, hwId);
            if (muteRecord != null) {
                PunishJoinListener.addMuteCache(target, muteRecord);
            }

            // Notify the target
            String durationFmt = com.ultimateimprovments.punish.PunishmentMessages
                    .durationLabel(parsed.expiresAt);
            target.sendMessage(com.ultimateimprovments.punish.PunishmentMessages.buildMuteChatMessage(
                    sender.getName(), parsed.reason, durationFmt,
                    com.ultimateimprovments.punish.PunishmentMessages.getDiscordUrl()));
        }

        String scope = parsed.ip ? " [IP]" : parsed.hw ? " [HW]" : "";
        sender.sendMessage(MessageUtil.parse(
                "<green>✔</green> <white>Player</white> <yellow>" + name + "</yellow> <white>has been muted.</white>" + scope
        ));

        // Notify operators
        String duration = parsed.isPermanent ? "<red>permanent</red>" : "<yellow>" + parsed.timeStr + "</yellow>";
        broadcastToModerators(
                "<red>🔇</red> <yellow>" + name + "</yellow> <gray>muted by</gray> <white>" + sender.getName() + "</white>" +
                "<gray> | Reason:</gray> <white>" + parsed.reason + "</white>" +
                "<gray> | Duration:</gray> " + duration +
                scope
        );
        return true;
    }

    // =========================
    // KICK
    // =========================
    private static boolean handleKick(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ui.command.punish.kick")) {
            CommandErrors.noPermission(sender, "ui.command.punish.kick");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/ui punish kick <player> [-ip] [-hw] -r \"<reason>\"</white>"
            ));
            return true;
        }

        // Parse the flags (kick doesn't require -time/-permanent)
        FlagParseResult flags = parseFlags(sender, args, 3);
        if (flags == null) return true;

        String targetName = args[2];
        String reason = flags.reason;

        @SuppressWarnings("deprecation")
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Player</red> <yellow>" + targetName + "</yellow> <red>not found or not online!</red>"
            ));
            return true;
        }

        String ip = null;
        String hwId = null;

        if (flags.ip) {
            ip = target.getAddress() != null ? target.getAddress().getAddress().getHostAddress() : "";
        }
        if (flags.hw) {
            String targetIp = target.getAddress() != null ? target.getAddress().getAddress().getHostAddress() : "";
            hwId = PunishmentManager.computeHwId(targetIp, target.getName());
        }

        // Kick the target
        PunishmentManager.kickPlayer(target, reason, sender.getName());
        sender.sendMessage(MessageUtil.parse(
                "<green>✔</green> <white>Player</white> <yellow>" + target.getName() + "</yellow> <white>has been kicked.</white>"
        ));

        // Notify operators
        broadcastToModerators(
                "<red>👢</red> <yellow>" + target.getName() + "</yellow> <gray>kicked by</gray> <white>" + sender.getName() + "</white>" +
                "<gray> | Reason:</gray> <white>" + reason + "</white>"
        );

        // If -ip or -hw — kick all matching
        if (flags.ip || flags.hw) {
            for (Player p : PunishmentManager.findPlayersByIpOrHw(ip, hwId)) {
                if (!p.getUniqueId().equals(target.getUniqueId())) {
                    PunishmentManager.kickPlayer(p, reason + " (IP/HW match)", sender.getName());
                    sender.sendMessage(MessageUtil.parse(
                            "<yellow>⚠</yellow> <white>Also kicked</white> <yellow>" + p.getName() + "</yellow> <white>(IP/HW match)</white>"
                    ));
                }
            }
        }

        return true;
    }

    // =========================
    // WARN
    // =========================
    private static boolean handleWarn(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ui.command.punish.warn")) {
            CommandErrors.noPermission(sender, "ui.command.punish.warn");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/ui punish warn <player> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw] -r \"<reason>\"</white>"
            ));
            return true;
        }

        PunishArgs parsed = parsePunishArgs(sender, args, 3);
        if (parsed == null) return true;

        String targetName = args[2];
        @SuppressWarnings("deprecation")
        Player target = Bukkit.getPlayerExact(targetName);
        String uuid;
        String name;

        if (target != null) {
            uuid = target.getUniqueId().toString();
            name = target.getName();
        } else {
            uuid = resolveTargetUuid(targetName);
            name = targetName;
        }

        boolean ok = PunishmentManager.warn(uuid, name, parsed.reason,
                sender.getName(), parsed.expiresAt);
        if (!ok) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Failed to warn " + name + "</red>"));
            return true;
        }

        if (target != null && target.isOnline()) {
            String durationFmt = com.ultimateimprovments.punish.PunishmentMessages
                    .durationLabel(parsed.expiresAt);
            target.sendMessage(com.ultimateimprovments.punish.PunishmentMessages.buildWarnChatMessage(
                    sender.getName(), parsed.reason, durationFmt,
                    com.ultimateimprovments.punish.PunishmentMessages.getDiscordUrl()));
        }

        // If -ip/-hw — warn all matching
        if (parsed.ip || parsed.hw) {
            String ip = null;
            String hwId = null;
            if (parsed.ip && target != null) {
                ip = target.getAddress() != null ? target.getAddress().getAddress().getHostAddress() : "";
            }
            if (parsed.hw && target != null) {
                String targetIp = target.getAddress() != null ? target.getAddress().getAddress().getHostAddress() : "";
                hwId = PunishmentManager.computeHwId(targetIp, name);
            }
            for (Player p : PunishmentManager.findPlayersByIpOrHw(ip, hwId)) {
                if (!p.getUniqueId().toString().equals(uuid)) {
                    PunishmentManager.warn(
                            p.getUniqueId().toString(), p.getName(),
                            parsed.reason + " (IP/HW match)",
                            sender.getName(), parsed.expiresAt
                    );
                    sender.sendMessage(MessageUtil.parse(
                            "<yellow>⚠</yellow> <white>Also warned</white> <yellow>" + p.getName() + "</yellow> <white>(IP/HW match)</white>"
                    ));
                }
            }
        }

        sender.sendMessage(MessageUtil.parse(
                "<green>✔</green> <white>Player</white> <yellow>" + name + "</yellow> <white>has been warned.</white>"
        ));

        // Notify operators
        String duration = parsed.isPermanent ? "<red>permanent</red>" : "<yellow>" + parsed.timeStr + "</yellow>";
        broadcastToModerators(
                "<yellow>⚠</yellow> <yellow>" + name + "</yellow> <gray>warned by</gray> <white>" + sender.getName() + "</white>" +
                "<gray> | Reason:</gray> <white>" + parsed.reason + "</white>" +
                "<gray> | Duration:</gray> " + duration +
                (parsed.ip ? " [IP]" : parsed.hw ? " [HW]" : "")
        );
        return true;
    }

    // =========================
    // BLOCK
    // =========================
    /**
     * /ui punish block &lt;player&gt; &lt;reason&gt; [-time:&lt;N&gt;s|m|h|d] [-permanent] [-ip] [-hw]
     * <p>
     * <b>Soft</b> punishment with the same flags as the ban: the player is NOT kicked.
     * They stay online behind a modal dialog (see
     * {@link com.ultimateimprovments.punish.BlockEnforcer}) and cannot move, interact,
     * chat or run commands until the block expires or an admin lifts it. The dialog is
     * removed when the player closes it and immediately shown again.
     */
    private static boolean handleBlock(CommandSender sender, String[] args) {
        if (!canBlock(sender)) return true;
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/ui punish block <player> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw] -r \"<reason>\"</white>"
            ));
            return true;
        }

        PunishArgs parsed = parsePunishArgs(sender, args, 3);
        if (parsed == null) return true;

        String targetName = args[2];
        @SuppressWarnings("deprecation")
        Player target = Bukkit.getPlayerExact(targetName);
        String uuid;
        String name;

        if (target != null) {
            uuid = target.getUniqueId().toString();
            name = target.getName();
        } else {
            uuid = resolveTargetUuid(targetName);
            name = targetName;
        }

        String ip = null;
        String hwId = null;

        if (parsed.ip) {
            ip = target != null && target.getAddress() != null
                    ? target.getAddress().getAddress().getHostAddress()
                    : "0.0.0.0";
        }
        if (parsed.hw) {
            String targetIp = target != null && target.getAddress() != null
                    ? target.getAddress().getAddress().getHostAddress()
                    : "0.0.0.0";
            hwId = PunishmentManager.computeHwId(targetIp, name);
        }

        boolean ok = PunishmentManager.punish(
                PunishmentManager.PunishType.BLOCK,
                uuid, name, parsed.reason,
                sender.getName(), parsed.expiresAt,
                ip, hwId
        );

        if (!ok) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Failed to block " + name + "</red>"));
            return true;
        }

        // Lock the target right away (the dialog pops up on their screen).
        if (target != null && target.isOnline()) {
            com.ultimateimprovments.punish.BlockEnforcer.refresh(target);
        }

        // If -ip or -hw — block every matching online player as well.
        if (parsed.ip || parsed.hw) {
            for (Player p : PunishmentManager.findPlayersByIpOrHw(ip, hwId)) {
                if (target != null && p.getUniqueId().equals(target.getUniqueId())) continue;
                com.ultimateimprovments.punish.BlockEnforcer.refresh(p);
                sender.sendMessage(MessageUtil.parse(
                        "<yellow>⚠</yellow> <white>Also blocked</white> <yellow>" + p.getName() + "</yellow> <white>(IP/HW match)</white>"
                ));
            }
        }

        String scope = parsed.ip ? " [IP]" : parsed.hw ? " [HW]" : "";
        sender.sendMessage(MessageUtil.parse(
                "<green>✔</green> <white>Player</white> <yellow>" + name + "</yellow> <white>has been blocked.</white>" + scope
        ));

        // Notify operators
        String duration = parsed.isPermanent ? "<red>permanent</red>" : "<yellow>" + parsed.timeStr + "</yellow>";
        broadcastToModerators(
                "<red>🚫</red> <yellow>" + name + "</yellow> <gray>blocked by</gray> <white>" + sender.getName() + "</white>" +
                "<gray> | Reason:</gray> <white>" + parsed.reason + "</white>" +
                "<gray> | Duration:</gray> " + duration +
                scope
        );
        return true;
    }

    /**
     * Block permission: the dedicated {@code ui.command.punish.block} node is
     * granted implicitly to everyone who can already ban.
     */
    private static boolean canBlock(CommandSender sender) {
        if (sender.hasPermission(Permissions.CMD_PUNISH_BLOCK)) return true;
        if (sender.hasPermission(Permissions.CMD_PUNISH_BAN)) return true;
        CommandErrors.noPermission(sender, Permissions.CMD_PUNISH_BLOCK);
        return false;
    }

    // =========================
    // LISTWARNS
    // =========================
    private static boolean handleListWarns(CommandSender sender, String[] args) {
        if (args.length < 3) {
            // Show own warns
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MessageUtil.parse(
                        "<red>❌ Usage: </red><white>/ui punish listwarns <player></white>"
                ));
                return true;
            }
            if (!sender.hasPermission("ui.command.punish.listwarns.self")) {
                CommandErrors.noPermission(sender, "ui.command.punish.listwarns.self");
                return true;
            }
            showWarns(sender, player.getUniqueId().toString(), player.getName());
            return true;
        }

        String targetName = args[2];

        // Check whether they're viewing themselves
        if (sender instanceof Player player && player.getName().equalsIgnoreCase(targetName)) {
            if (!sender.hasPermission("ui.command.punish.listwarns.self")) {
                CommandErrors.noPermission(sender, "ui.command.punish.listwarns.self");
                return true;
            }
            showWarns(sender, player.getUniqueId().toString(), player.getName());
            return true;
        }

        // Viewing someone else
        if (!sender.hasPermission("ui.command.punish.listwarns.other")) {
            CommandErrors.noPermission(sender, "ui.command.punish.listwarns.other");
            return true;
        }

        @SuppressWarnings("deprecation")
        Player target = Bukkit.getPlayerExact(targetName);
        String uuid;
        String name;

        if (target != null) {
            uuid = target.getUniqueId().toString();
            name = target.getName();
            showWarns(sender, uuid, name);
        } else {
            // Look up by name in the DB
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>⚠</yellow> <white>Player</white> <yellow>" + targetName + "</yellow> <white>not online. Showing warns by name...</white>"
            ));
            showWarnsByName(sender, targetName);
        }

        return true;
    }

    private static void showWarns(CommandSender sender, String uuid, String name) {
        List<PunishmentManager.WarnRecord> warns = PunishmentManager.getActiveWarns(uuid);

        sender.sendMessage(MessageUtil.parse(
                "<gray>═══ <white>Warns: " + name + "</white> ═══</gray>"
        ));

        if (warns.isEmpty()) {
            sender.sendMessage(MessageUtil.parse("  <dark_gray>(no active warns)</dark_gray>"));
            return;
        }

        for (int i = 0; i < warns.size(); i++) {
            PunishmentManager.WarnRecord w = warns.get(i);
            String duration = w.isPermanent() ? "<red>permanent</red>" : "<yellow>" + w.formatRemaining() + "</yellow>";
            sender.sendMessage(MessageUtil.parse(
                    "  <white>#" + w.id + ".</white> <gray>" + w.reason + "</gray>\n" +
                    "    <dark_gray>By: " + w.warnedBy + " | Duration: " + duration + "</dark_gray>"
            ));
        }
    }

    private static void showWarnsByName(CommandSender sender, String name) {
        String lower = name.toLowerCase().trim();
        List<PunishmentManager.WarnRecord> warns = new ArrayList<>();
        try (java.sql.Connection con = com.ultimateimprovments.database.DatabaseManager.getConnection();
             java.sql.PreparedStatement st = con.prepareStatement(
                     "SELECT * FROM warns WHERE LOWER(player_name) = ? ORDER BY warned_at DESC")) {
            st.setString(1, lower);
            try (java.sql.ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    warns.add(new PunishmentManager.WarnRecord(
                            rs.getInt("id"),
                            rs.getString("player_uuid"),
                            rs.getString("player_name"),
                            rs.getString("reason"),
                            rs.getString("warned_by"),
                            rs.getLong("warned_at"),
                            rs.getLong("expires_at")
                    ));
                }
            }
        } catch (java.sql.SQLException e) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Database error!</red>"));
            return;
        }

        showWarns(sender, "name:" + lower, name);
    }

    // =========================
    // FLAG PARSING
    // =========================

    /**
     * Flag parse result for punishments requiring time/permanent.
     */
    private static class PunishArgs {
        String reason;
        long expiresAt;
        boolean isPermanent;
        boolean ip;
        boolean hw;
        String timeStr; // for display
    }

    /**
     * Parse result for kick (no time/permanent).
     */
    private static class FlagParseResult {
        String reason;
        boolean ip;
        boolean hw;
    }

    /**
     * Parses arguments for ban/mute/warn/block.
     * <p>
     * The reason is <b>mandatory</b> and comes only from {@code -r "text"} /
     * {@code -r:<text>} (alias {@code -reason:}) — loose words are rejected, so a
     * punishment can never be issued without a reason. The value keeps its
     * MiniMessage markup (it is stored and rendered as-is); surrounding quotes are
     * stripped.
     * <p>
     * Sends error messages to the sender on invalid flags and returns null.
     */
    private static PunishArgs parsePunishArgs(CommandSender sender, String[] args, int startIndex) {
        PunishArgs result = new PunishArgs();
        String reason = null;
        boolean hasTime = false;
        boolean hasPermanent = false;
        boolean hasIp = false;
        boolean hasHw = false;
        String timeStr = null;

        for (int i = startIndex; i < args.length; i++) {
            String arg = args[i];
            String lower = arg.toLowerCase();

            if (lower.startsWith("-time:")) {
                String timeVal = arg.substring(6).trim();
                if (timeVal.isEmpty()) {
                    sender.sendMessage(MessageUtil.parse("<red>❌ -time flag requires a value (e.g. -time:30m)</red>"));
                    return null;
                }
                timeStr = timeVal;
                hasTime = true;
            } else if (lower.equals("-permanent")) {
                hasPermanent = true;
            } else if (lower.equals("-ip")) {
                hasIp = true;
            } else if (lower.equals("-hw")) {
                hasHw = true;
            } else if (lower.startsWith("-r:") || lower.startsWith("-reason:")) {
                reason = appendReason(reason, unquote(arg.substring(arg.indexOf(':') + 1)));
            } else if (lower.equals("-r") || lower.equals("-reason")) {
                if (i + 1 >= args.length) {
                    sender.sendMessage(MessageUtil.parse(
                            "<red>❌ </red><white>-r</white><red> requires a reason: </red><white>-r \"<reason>\"</white>"));
                    return null;
                }
                reason = appendReason(reason, unquote(args[++i]));
            } else {
                sender.sendMessage(MessageUtil.parse(
                        "<red>❌ Unexpected argument: </red><yellow>" + esc(arg) + "</yellow>"
                        + "<red>. The reason must be given with </red><white>-r \"<reason>\"</white>"));
                return null;
            }
        }

        // Validation
        if (reason == null || reason.isBlank()) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ You must specify a reason: </red><white>-r \"<reason>\"</white>"
            ));
            return null;
        }
        if (!hasTime && !hasPermanent) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ You must specify either </red><white>-time:<N>s|m|h|d</white><red> or </red><white>-permanent</white><red>.</red>"
            ));
            return null;
        }
        if (hasTime && hasPermanent) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Flags </red><white>-time</white><red> and </red><white>-permanent</white><red> cannot be used together!</red>"
            ));
            return null;
        }
        if (hasIp && hasHw) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Flags </red><white>-ip</white><red> and </red><white>-hw</white><red> cannot be used together!</red>"
            ));
            return null;
        }

        result.reason = reason.trim();
        result.ip = hasIp;
        result.hw = hasHw;
        result.timeStr = timeStr != null ? timeStr : "permanent";

        if (hasTime && timeStr != null) {
            result.expiresAt = PunishmentManager.parseTimeFlag(timeStr);
            if (result.expiresAt == 0) {
                sender.sendMessage(MessageUtil.parse(
                        "<red>❌ Invalid time format! Use </red><white>-time:<N>s|m|h|d</white>"
                ));
                return null;
            }
        } else {
            result.expiresAt = 0; // permanent
        }
        result.isPermanent = hasPermanent;

        return result;
    }

    /**
     * Parses arguments for kick. The reason is mandatory ({@code -r "text"}), the
     * same rules as {@link #parsePunishArgs}; {@code -time}/{@code -permanent} are
     * ignored because a kick has no duration.
     */
    private static FlagParseResult parseFlags(CommandSender sender, String[] args, int startIndex) {
        FlagParseResult result = new FlagParseResult();
        String reason = null;
        boolean hasIp = false;
        boolean hasHw = false;

        for (int i = startIndex; i < args.length; i++) {
            String arg = args[i];
            String lower = arg.toLowerCase();
            if (lower.equals("-ip")) {
                hasIp = true;
            } else if (lower.equals("-hw")) {
                hasHw = true;
            } else if (lower.startsWith("-time:") || lower.equals("-permanent")) {
                // Kick has no duration — these flags are ignored
            } else if (lower.startsWith("-r:") || lower.startsWith("-reason:")) {
                reason = appendReason(reason, unquote(arg.substring(arg.indexOf(':') + 1)));
            } else if (lower.equals("-r") || lower.equals("-reason")) {
                if (i + 1 >= args.length) {
                    sender.sendMessage(MessageUtil.parse(
                            "<red>❌ </red><white>-r</white><red> requires a reason: </red><white>-r \"<reason>\"</white>"));
                    return null;
                }
                reason = appendReason(reason, unquote(args[++i]));
            } else {
                sender.sendMessage(MessageUtil.parse(
                        "<red>❌ Unexpected argument: </red><yellow>" + esc(arg) + "</yellow>"
                        + "<red>. The reason must be given with </red><white>-r \"<reason>\"</white>"));
                return null;
            }
        }

        if (hasIp && hasHw) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Flags </red><white>-ip</white><red> and </red><white>-hw</white><red> cannot be used together!</red>"
            ));
            return null;
        }

        if (reason == null || reason.isBlank()) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ You must specify a reason: </red><white>-r \"<reason>\"</white>"
            ));
            return null;
        }

        result.reason = reason.trim();
        result.ip = hasIp;
        result.hw = hasHw;
        return result;
    }

    /** Appends a reason fragment (repeated -r flags are space-joined). */
    private static String appendReason(String current, String fragment) {
        String value = fragment == null ? "" : fragment.trim();
        if (value.isEmpty()) return current;
        return current == null || current.isEmpty() ? value : current + " " + value;
    }

    /** Strips surrounding double quotes kept by the dispatcher's tokenizer. */
    private static String unquote(String value) {
        if (value == null || value.length() < 2) return value == null ? "" : value;
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    /** Escapes plain text (raw arguments) for MiniMessage. */
    private static String esc(String text) {
        return text == null ? "" : MiniMessage.miniMessage().escapeTags(text);
    }

    // =========================
    // CRASH (with confirmation)
    // =========================
    private static boolean handleCrash(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ui.command.punish.crash")) {
            CommandErrors.noPermission(sender, "ui.command.punish.crash");
            return true;
        }

        // Confirm/cancel (legacy: /ui punish crash confirm|cancel)
        if (args.length >= 3) {
            String sub = args[2].toLowerCase();
            if (sub.equals("confirm")) {
                return confirmCrash(sender);
            }
            if (sub.equals("cancel")) {
                return cancelCrash(sender);
            }
        }

        if (args.length < 4) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/ui punish crash <particle|entity|bossbar|chat|scoreboard|team|chunk|explosion|title> <player></white>"
            ));
            return true;
        }

        String method = args[2].toLowerCase();
        if (!CRASH_METHODS.contains(method)) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Unknown crash method: </red><yellow>" + args[2] + "</yellow>\n" +
                    "<gray>Available: </gray><white>particle, entity, bossbar, chat, scoreboard, team, chunk, explosion, title, blockupdate</white>"
            ));
            return true;
        }

        String targetName = args[3];
        @SuppressWarnings("deprecation")
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Player</red> <yellow>" + targetName + "</yellow> <red>not found or not online!</red>"
            ));
            return true;
        }

        // 🛡 Failsafe: can't crash yourself
        if (sender instanceof Player p && p.getUniqueId().equals(target.getUniqueId())) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ You cannot crash yourself!</red>"
            ));
            return true;
        }

        // Lazy cleanup of expired confirmations (no separate task — the map is bounded)
        long now = System.currentTimeMillis();
        pendingCrashes.values().removeIf(p -> now - p.createdAt() > CONFIRM_TIMEOUT_MS);

        // Save the pending confirmation (together with the chosen method)
        pendingCrashes.put(getSenderId(sender), new PendingCrash(method, target.getName(), now));

        sender.sendMessage(MessageUtil.parse(
                "<red>⚠</red> <white>Are you sure you want to crash</white> <yellow>" + target.getName() + "</yellow>" +
                " <dark_gray>via</dark_gray> <gold>" + method + "</gold><red>?</red>"
        ));
        sender.sendMessage(MessageUtil.parse(
                "<red>This will overload the player's client with " + describeMethod(method) + "</red>"
        ));
        sender.sendMessage(MessageUtil.parse(
                "<red>and may crash their game.</red>"
        ));
        sender.sendMessage(MessageUtil.parse(
                "<click:run_command:/ui punish crash confirm><dark_green>[</dark_green><green>✔ Confirm</green><dark_green>]</dark_green></click>"
                + " <dark_gray>|</dark_gray> "
                + "<click:run_command:/ui punish crash cancel><dark_red>[</dark_red><red>❌ Cancel</red><dark_red>]</dark_red></click>"
        ));
        return true;
    }

    /** Valid crash methods. */
    private static final Set<String> CRASH_METHODS = Set.of(
            "particle", "entity", "bossbar", "chat", "scoreboard", "team", "chunk", "explosion", "title", "blockupdate"
    );

    /** Short method description for the confirmation message. */
    private static String describeMethod(String method) {
        return switch (method) {
            case "particle" -> "a massive particle overload";
            case "entity" -> "thousands of virtual entities";
            case "bossbar" -> "hundreds of huge boss bars (always-rendered OOM)";
            case "chat" -> "dozens of 2MB chat messages (immediate-render OOM)";
            case "scoreboard" -> "a huge sidebar objective with giant scores";
            case "team" -> "huge scoreboard teams rendered over nametags";
            case "chunk" -> "thousands of fake chunk packets (re-parse flood)";
            case "explosion" -> "explosions with billions of block particles";
            case "title" -> "huge title/action-bar texts (instant render)";
            case "blockupdate" -> "millions of block changes in your section (re-mesh storm)";
            default -> "a client overload";
        };
    }

    /** Executes the confirmed crash. */
    private static boolean confirmCrash(CommandSender sender) {
        PendingCrash pending = pendingCrashes.remove(getSenderId(sender));

        if (pending == null) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ No pending crash confirmation. Use </red><white>/ui punish crash <method> <player></white><red> first.</red>"
            ));
            return true;
        }

        if (System.currentTimeMillis() - pending.createdAt() > CONFIRM_TIMEOUT_MS) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Confirmation expired (30s). Use </red><white>/ui punish crash <method> <player></white><red> again.</red>"
            ));
            return true;
        }

        @SuppressWarnings("deprecation")
        Player target = Bukkit.getPlayerExact(pending.targetName());
        if (target == null) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Player</red> <yellow>" + pending.targetName() + "</yellow> <red>is no longer online!</red>"
            ));
            return true;
        }

        // Execute the chosen crash method (all packets go ONLY to the target's client)
        switch (pending.method()) {
            case "particle" -> CrashExecutor.crashWithParticles(target);
            case "entity" -> CrashExecutor.crashWithEntities(target);
            case "bossbar" -> CrashExecutor.crashWithBossBar(target);
            case "chat" -> CrashExecutor.crashWithChat(target);
            case "scoreboard" -> CrashExecutor.crashWithScoreboard(target);
            case "team" -> CrashExecutor.crashWithTeam(target);
            case "chunk" -> CrashExecutor.crashWithChunk(target);
            case "explosion" -> CrashExecutor.crashWithExplosion(target);
            case "title" -> CrashExecutor.crashWithTitle(target);
            case "blockupdate" -> CrashExecutor.crashWithBlockUpdate(target);
            default -> {
                sender.sendMessage(MessageUtil.parse(
                        "<red>❌ Unknown crash method: </red><yellow>" + pending.method() + "</yellow>"
                ));
                return true;
            }
        }

        sender.sendMessage(MessageUtil.parse(
                "<green>✔</green> <white>Player</white> <yellow>" + target.getName() + "</yellow>" +
                " <white>has been crashed via</white> <gold>" + pending.method() + "</gold><white>.</white>"
        ));

        // Notify operators
        broadcastToModerators(
                "<red>💥</red> <yellow>" + target.getName() + "</yellow> <gray>crashed by</gray> <white>" + sender.getName() + "</white>" +
                " <gray>| Method:</gray> <gold>" + pending.method() + "</gold>"
        );

        return true;
    }

    /** Cancels a pending crash. */
    private static boolean cancelCrash(CommandSender sender) {
        PendingCrash removed = pendingCrashes.remove(getSenderId(sender));

        if (removed == null) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ No pending crash confirmation to cancel.</red>"
            ));
            return true;
        }

        sender.sendMessage(MessageUtil.parse(
                "<green>✔</green> <gray>Crash of</gray> <yellow>" + removed.targetName() + "</yellow> <gray>cancelled.</gray>"
        ));
        return true;
    }

    /** Sender UUID (player or sentinel for console). */
    private static UUID getSenderId(CommandSender sender) {
        if (sender instanceof Player p) {
            return p.getUniqueId();
        }
        return UUID.nameUUIDFromBytes(("console:" + sender.getName()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    // =========================
    // USAGE
    // =========================
    private static void sendUsage(CommandSender sender) {
        sender.sendMessage(MessageUtil.parse(
                "<red>❌ Usage:</red>\n" +
                "<white>/ui punish ban <player> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw] -r \"<reason>\"</white>\n" +
                "<white>/ui punish mute <player> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw] -r \"<reason>\"</white>\n" +
                "<white>/ui punish kick <player> [-ip] [-hw] -r \"<reason>\"</white>\n" +
                "<white>/ui punish warn <player> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw] -r \"<reason>\"</white>\n" +
                "<white>/ui punish block <player> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw] -r \"<reason>\"</white>\n" +
                "<white>/ui punish listwarns [player]</white>\n" +
                "<white>/ui punish pardon <ban|mute|block|warn|all> <player|all> [-r:<reason>] [-id:<n>] [-s] [-dry] [-confirm]</white>\n" +
                "<white>/ui punish logs view [-p <page>] [-t <type>]</white> <gray>(types: all, ban, mute, block, warn, kick)</gray>\n" +
                "<white>/ui punish logs purge -a|-t <1h|30m|7d|2w> [-confirm]</white>\n" +
                "<white>/ui punish crash <method> <player></white> <gray>(requires confirm)</gray>\n" +
                "<gray>Flags: -time:<N>s|m|h|d, -permanent, -ip, -hw</gray>"
        ));
    }

    // =========================
    // TAB COMPLETION
    // =========================
    public static List<String> tabComplete(String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            for (String action : List.of("ban", "block", "mute", "kick", "warn", "listwarns", "pardon", "logs", "crash")) {
                if (action.startsWith(prefix)) {
                    completions.add(action);
                }
            }
        } else if (args.length == 3) {
            String action = args[1].toLowerCase();
            if (action.equals("crash")) {
                // crash methods + confirm/cancel
                completions.addAll(CRASH_METHODS);
                completions.add("confirm");
                completions.add("cancel");
            } else if (action.equals("logs")) {
                // /ui punish logs <view|purge> [flags]
                completions.addAll(LogsCommand.tabComplete(args));
            } else if (action.equals("pardon")) {
                // /ui punish pardon <type> <player> [flags] — types, players, then flags
                completions.addAll(PardonCommand.tabComplete(args));
            } else if (List.of("ban", "block", "mute", "kick", "warn", "listwarns").contains(action)) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    completions.add(p.getName());
                }
            }
        } else if (args.length == 4) {
            String action = args[1].toLowerCase();
            if (action.equals("crash")) {
                String method = args[2].toLowerCase();
                if (CRASH_METHODS.contains(method)) {
                    // /ui punish crash <method> <tab> → player names
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        completions.add(p.getName());
                    }
                } else if (!method.equals("confirm") && !method.equals("cancel")) {
                    // method not chosen yet → suggest methods
                    completions.addAll(CRASH_METHODS);
                }
            } else if (action.equals("pardon")) {
                // /ui punish pardon <type> <tab> → player names + "all"
                completions.addAll(PardonCommand.tabComplete(args));
            } else if (action.equals("logs")) {
                // /ui punish logs <view|purge> <tab> → flags / values
                completions.addAll(LogsCommand.tabComplete(args));
            } else {
                // after the player name → punishment flags
                addFlagCompletions(action, args, completions);
            }
        } else if (args.length >= 5) {
            // Flags
            String action = args[1].toLowerCase();
            if (action.equals("logs")) {
                completions.addAll(LogsCommand.tabComplete(args));
            } else if (action.equals("pardon")) {
                completions.addAll(PardonCommand.tabComplete(args));
            } else {
                addFlagCompletions(action, args, completions);
            }
        }

        String last = args[args.length - 1].toLowerCase();
        return completions.stream().filter(s -> s.toLowerCase().startsWith(last)).collect(Collectors.toList());
    }

    /**
     * Suggests flags for ban/mute/warn/kick (crash has no flags).
     * Already-used flags are not duplicated.
     */
    private static void addFlagCompletions(String action, String[] args, List<String> completions) {
        if (action.equals("crash")) {
            return;
        }
        if (List.of("ban", "block", "mute", "warn").contains(action)) {
            boolean hasTime = false, hasPerm = false, hasIp = false, hasHw = false;
            for (int i = 3; i < args.length; i++) {
                String a = args[i].toLowerCase();
                if (a.startsWith("-time:")) hasTime = true;
                else if (a.equals("-permanent")) hasPerm = true;
                else if (a.equals("-ip")) hasIp = true;
                else if (a.equals("-hw")) hasHw = true;
            }
            if (!hasTime) completions.add("-time:");
            if (!hasPerm) completions.add("-permanent");
            if (!hasIp) completions.add("-ip");
            if (!hasHw) completions.add("-hw");
            if (!hasReason(args)) completions.add("-r:");
        } else if (action.equals("kick")) {
            boolean hasIp = false, hasHw = false;
            for (int i = 3; i < args.length; i++) {
                String a = args[i].toLowerCase();
                if (a.equals("-ip")) hasIp = true;
                else if (a.equals("-hw")) hasHw = true;
            }
            if (!hasIp) completions.add("-ip");
            if (!hasHw) completions.add("-hw");
            if (!hasReason(args)) completions.add("-r:");
        }
    }

    /** Whether a reason flag (-r / -r: / -reason:) is already present. */
    private static boolean hasReason(String[] args) {
        for (int i = 3; i < args.length; i++) {
            String a = args[i].toLowerCase();
            if (a.equals("-r") || a.equals("-reason")
                    || a.startsWith("-r:") || a.startsWith("-reason:")) {
                return true;
            }
        }
        return false;
    }
}
