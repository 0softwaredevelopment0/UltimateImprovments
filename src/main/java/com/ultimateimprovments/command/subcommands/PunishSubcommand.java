package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.core.Permissions;
import com.ultimateimprovments.punish.CrashExecutor;
import com.ultimateimprovments.punish.PunishmentManager;
import com.ultimateimprovments.punish.PunishJoinListener;
import com.ultimateimprovments.util.AlertBroadcast;
import com.ultimateimprovments.util.MessageUtil;
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
 * /ui punish listwarns <player>
 * /ui punish unban <player>
 * /ui punish unmute <player>
 * /ui punish actionlist [tab] [page] — all active punishments with tabs & pages
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
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ You don't have permission to use punish commands!</red>"
            ));
            return true;
        }

        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        String action = args[1].toLowerCase();

        return switch (action) {
            case "ban" -> handleBan(sender, args);
            case "mute" -> handleMute(sender, args);
            case "kick" -> handleKick(sender, args);
            case "warn" -> handleWarn(sender, args);
            case "listwarns" -> handleListWarns(sender, args);
            case "unban" -> handleUnban(sender, args);
            case "unmute" -> handleUnmute(sender, args);
            case "unwarn" -> handleUnwarn(sender, args);
            case "actionlist" -> handleActionList(sender, args);
            case "crash" -> handleCrash(sender, args);
            default -> {
                sendUsage(sender);
                yield true;
            }
        };
    }

    // =========================
    // BAN
    // =========================
    private static boolean handleBan(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ui.command.punish.ban")) {
            sender.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to ban!</red>"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/ui punish ban <player> <reason> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw]</white>"
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
            uuid = "offline:" + targetName.toLowerCase();
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
                String kickMsg = MessageUtil.legacy(
                        "<red>⛔ You have been banned!</red>\n" +
                        "<gray>Reason:</gray> <white>" + parsed.reason + "</white>\n" +
                        "<dark_gray>By: " + sender.getName() + "</dark_gray>"
                );
                target.kickPlayer(kickMsg);
            }
        }

        // If -ip or -hw — kick all matching online players
        if (parsed.ip || parsed.hw) {
            for (Player p : PunishmentManager.findPlayersByIpOrHw(ip, hwId)) {
                if (!p.getName().equalsIgnoreCase(name)) {
                    p.kickPlayer(MessageUtil.legacy(
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
            sender.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to mute!</red>"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/ui punish mute <player> <reason> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw]</white>"
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
            uuid = "offline:" + targetName.toLowerCase();
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
            String duration = parsed.isPermanent ? "permanent" : parsed.timeStr;
            target.sendMessage(MessageUtil.parse(
                    "<red>🔇 You have been muted!</red>\n" +
                    "<gray>Reason:</gray> <white>" + parsed.reason + "</white>\n" +
                    "<gray>Duration:</gray> <white>" + duration + "</white>\n" +
                    "<dark_gray>By: " + sender.getName() + "</dark_gray>"
            ));
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
            sender.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to kick!</red>"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/ui punish kick <player> <reason> [-ip] [-hw]</white>"
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
            sender.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to warn!</red>"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/ui punish warn <player> <reason> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw]</white>"
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
            uuid = "offline:" + targetName.toLowerCase();
            name = targetName;
        }

        boolean ok = PunishmentManager.warn(uuid, name, parsed.reason,
                sender.getName(), parsed.expiresAt);
        if (!ok) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Failed to warn " + name + "</red>"));
            return true;
        }

        if (target != null && target.isOnline()) {
            String duration = parsed.isPermanent ? "permanent" : parsed.timeStr;
            target.sendMessage(MessageUtil.parse(
                    "<yellow>⚠ You have been warned!</yellow>\n" +
                    "<gray>Reason:</gray> <white>" + parsed.reason + "</white>\n" +
                    "<gray>Duration:</gray> <white>" + duration + "</white>\n" +
                    "<dark_gray>By: " + sender.getName() + "</dark_gray>"
            ));
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
                sender.sendMessage(MessageUtil.parse("<red>❌ You don't have permission!</red>"));
                return true;
            }
            showWarns(sender, player.getUniqueId().toString(), player.getName());
            return true;
        }

        String targetName = args[2];

        // Check whether they're viewing themselves
        if (sender instanceof Player player && player.getName().equalsIgnoreCase(targetName)) {
            if (!sender.hasPermission("ui.command.punish.listwarns.self")) {
                sender.sendMessage(MessageUtil.parse("<red>❌ You don't have permission!</red>"));
                return true;
            }
            showWarns(sender, player.getUniqueId().toString(), player.getName());
            return true;
        }

        // Viewing someone else
        if (!sender.hasPermission("ui.command.punish.listwarns.other")) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ You don't have permission to view other players' warns!</red>"
            ));
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
    // UNBAN / UNMUTE
    // =========================
    private static boolean handleUnban(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ui.command.punish.ban")) {
            sender.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to unban!</red>"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/ui punish unban <player></white>"
            ));
            return true;
        }

        String targetName = args[2];
        @SuppressWarnings("deprecation")
        Player target = Bukkit.getPlayerExact(targetName);
        String uuid;

        if (target != null) {
            uuid = target.getUniqueId().toString();
        } else {
            uuid = "offline:" + targetName.toLowerCase();
        }

        boolean ok = PunishmentManager.unpunish(PunishmentManager.PunishType.BAN, uuid, null, null, targetName);
        if (ok) {
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Player</white> <yellow>" + targetName + "</yellow> <white>has been unbanned.</white>"
            ));
            broadcastToModerators(
                    "<green>✔</green> <yellow>" + targetName + "</yellow> <gray>unbanned by</gray> <white>" + sender.getName() + "</white>"
            );
        } else {
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>⚠</yellow> <white>No active ban found for</white> <yellow>" + targetName + "</yellow>"
            ));
        }
        return true;
    }

    private static boolean handleUnmute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ui.command.punish.mute")) {
            sender.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to unmute!</red>"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/ui punish unmute <player></white>"
            ));
            return true;
        }

        String targetName = args[2];
        @SuppressWarnings("deprecation")
        Player target = Bukkit.getPlayerExact(targetName);
        String uuid;

        if (target != null) {
            uuid = target.getUniqueId().toString();
            // Clear the mute cache
            PunishJoinListener.removeMuteCache(target);
            // Notify the player
            target.sendMessage(MessageUtil.parse(
                    "<green>🔊 You have been unmuted!</green>"
            ));
        } else {
            uuid = "offline:" + targetName.toLowerCase();
        }

        boolean ok = PunishmentManager.unpunish(PunishmentManager.PunishType.MUTE, uuid, null, null, targetName);
        if (ok) {
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Player</white> <yellow>" + targetName + "</yellow> <white>has been unmuted.</white>"
            ));
            broadcastToModerators(
                    "<green>🔊</green> <yellow>" + targetName + "</yellow> <gray>unmuted by</gray> <white>" + sender.getName() + "</white>"
            );
        } else {
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>⚠</yellow> <white>No active mute found for</white> <yellow>" + targetName + "</yellow>"
            ));
        }
        return true;
    }

    // =========================
    // UNWARN
    // =========================
    private static boolean handleUnwarn(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ui.command.punish.warn")) {
            sender.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to unwarn!</red>"));
            return true;
        }
        if (args.length < 5) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/ui punish unwarn <player> <reason> <warnId></white>"
            ));
            return true;
        }

        String targetName = args[2];
        String reason = args[3];
        int warnId;
        try {
            warnId = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Invalid warn ID: </red><yellow>" + args[4] + "</yellow>"
            ));
            return true;
        }

        if (warnId <= 0) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Warn ID must be a positive number!</red>"
            ));
            return true;
        }

        boolean ok = PunishmentManager.removeWarnById(warnId);
        if (ok) {
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Warn</white> <yellow>#" + warnId + "</yellow> <white>for</white> <yellow>" + targetName + "</yellow> <white>has been removed.</white>\n" +
                    "<gray>Reason: " + reason + "</gray>"
            ));

            // Notify the target (if online)
            @SuppressWarnings("deprecation")
            Player warnTarget = Bukkit.getPlayerExact(targetName);
            if (warnTarget != null && warnTarget.isOnline()) {
                warnTarget.sendMessage(MessageUtil.parse(
                        "<green>✔</green> <white>Your warn</white> <yellow>#" + warnId + "</yellow> <white>has been removed.</white>\n" +
                        "<gray>Reason: " + reason + "</gray>"
                ));
            }

            broadcastToModerators(
                    "<green>✔</green> <yellow>" + targetName + "</yellow> <gray>warn</gray> <yellow>#" + warnId + "</yellow> <gray>removed by</gray> <white>" + sender.getName() + "</white>" +
                    "<gray> | Reason:</gray> <white>" + reason + "</white>"
            );
        } else {
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>⚠</yellow> <white>No active warn with ID</white> <yellow>" + warnId + "</yellow> <white>found for</white> <yellow>" + targetName + "</yellow>"
            ));
        }
        return true;
    }

    // =========================
    // ACTIONLIST — all active punishments (tabs + pages)
    // =========================

    /** How many punishments fit on one actionlist page. */
    private static final int ACTIONLIST_PAGE_SIZE = 5;
    /** Valid actionlist tabs (dynamic switching via clickable tabs). */
    private static final List<String> ACTIONLIST_TABS = List.of("all", "ban", "mute", "warn", "kick");

    /**
     * Shows ALL active punishments of every player with tabs and pagination:
     * {@code /ui punish actionlist [tab] [page]}. Tabs and arrows are clickable for players
     * (the state is encoded in the command arguments — no in-memory session needed).
     */
    private static boolean handleActionList(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.CMD_PUNISH_ACTIONLIST)) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ You don't have permission to view the punishment list!</red>"
            ));
            return true;
        }

        String tab = "all";
        int page = 1;
        if (args.length >= 3) tab = args[2].toLowerCase();
        if (args.length >= 4) {
            try {
                page = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException ignored) {
                // keep page 1
            }
        }
        if (!ACTIONLIST_TABS.contains(tab)) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Unknown tab: </red><yellow>" + escape(args[2]) + "</yellow><red>.</red> "
                    + "<gray>Available: </gray><white>all, ban, mute, warn, kick</white>"
            ));
            return true;
        }

        List<PunishmentManager.PunishmentRecord> all = PunishmentManager.getAllActivePunishments();
        List<PunishmentManager.PunishmentRecord> filtered = switch (tab) {
            case "ban" -> filterByType(all, PunishmentManager.PunishType.BAN);
            case "mute" -> filterByType(all, PunishmentManager.PunishType.MUTE);
            case "warn" -> filterByType(all, PunishmentManager.PunishType.WARN);
            case "kick" -> filterByType(all, PunishmentManager.PunishType.KICK);
            default -> all;
        };

        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / (double) ACTIONLIST_PAGE_SIZE));
        page = Math.min(page, totalPages);
        int from = (page - 1) * ACTIONLIST_PAGE_SIZE;
        int to = Math.min(from + ACTIONLIST_PAGE_SIZE, filtered.size());
        boolean clickable = sender instanceof Player;

        // Header
        sender.sendMessage(MessageUtil.parse(
                "<gray>═══ <white>⚖ Active punishments</white> "
                + "<dark_gray>(" + filtered.size() + " shown, " + all.size() + " total)</dark_gray> ═══</gray>"
        ));

        // Tabs — the active one is highlighted, the rest are clickable
        StringBuilder tabs = new StringBuilder();
        for (String t : ACTIONLIST_TABS) {
            String label = switch (t) {
                case "ban" -> "<red>⛔ Bans</red>";
                case "mute" -> "<red>🔇 Mutes</red>";
                case "warn" -> "<yellow>⚠ Warns</yellow>";
                case "kick" -> "<gray>👢 Kicks</gray>";
                default -> "<white>All</white>";
            };
            if (t.equals(tab)) {
                tabs.append("<dark_aqua>[</dark_aqua>").append(label).append("<dark_aqua>]</dark_aqua> ");
            } else if (clickable) {
                tabs.append("<click:run_command:/ui punish actionlist ").append(t).append(" 1>")
                        .append("<gray>[</gray>").append(label).append("<gray>]</gray></click> ");
            } else {
                tabs.append("<gray>[</gray>").append(label).append("<gray>]</gray> ");
            }
        }
        sender.sendMessage(MessageUtil.parse(tabs.toString()));

        // Items
        if (filtered.isEmpty()) {
            sender.sendMessage(MessageUtil.parse("  <dark_gray>(no active punishments)</dark_gray>"));
        } else {
            for (int i = from; i < to; i++) {
                sender.sendMessage(MessageUtil.parse(actionListItem(filtered.get(i))));
            }
        }

        // Footer: prev / page / next
        String prev = page > 1
                ? (clickable
                    ? "<click:run_command:/ui punish actionlist " + tab + " " + (page - 1) + "><dark_aqua>[« Prev]</dark_aqua></click>"
                    : "<dark_aqua>[« Prev]</dark_aqua>")
                : "<dark_gray>[« Prev]</dark_gray>";
        String next = page < totalPages
                ? (clickable
                    ? "<click:run_command:/ui punish actionlist " + tab + " " + (page + 1) + "><dark_aqua>[Next »]</dark_aqua></click>"
                    : "<dark_aqua>[Next »]</dark_aqua>")
                : "<dark_gray>[Next »]</dark_gray>";
        sender.sendMessage(MessageUtil.parse(prev + "  <white>Page " + page + "/" + totalPages + "</white>  " + next));
        return true;
    }

    /** Filters a record list by punishment type. */
    private static List<PunishmentManager.PunishmentRecord> filterByType(
            List<PunishmentManager.PunishmentRecord> records, PunishmentManager.PunishType type) {
        return records.stream().filter(r -> r.type == type).collect(Collectors.toList());
    }

    /** Renders one punishment item (badge, player, reason, duration, scope). */
    private static String actionListItem(PunishmentManager.PunishmentRecord r) {
        String badge = switch (r.type) {
            case BAN -> "<red>⛔ Ban</red>";
            case MUTE -> "<red>🔇 Mute</red>";
            case WARN -> "<yellow>⚠ Warn</yellow>";
            case KICK -> "<gray>👢 Kick</gray>";
        };
        String scope = r.isIpScope() && r.isHwScope() ? " [IP+HW]"
                : r.isIpScope() ? " [IP]"
                : r.isHwScope() ? " [HW]" : "";
        return "  <white>#" + r.id + ".</white> " + badge + " <yellow>" + r.playerName + "</yellow>" + scope
                + "\n      <gray>" + actionListReason(r.reason) + "</gray>\n"
                + "      <dark_gray>By: " + r.punishedBy + " | " + actionListDuration(r) + "</dark_gray>";
    }

    /** Truncates long reasons for a tidy list. */
    private static String actionListReason(String reason) {
        if (reason == null || reason.isEmpty()) return "(no reason)";
        reason = reason.length() > 100 ? reason.substring(0, 100) + "…" : reason;
        // Reasons are arbitrary admin text — escape MiniMessage tags so they render as plain text
        return MiniMessage.miniMessage().escapeTags(reason);
    }

    /** Escapes MiniMessage tags in user-supplied text. */
    private static String escape(String text) {
        return text == null ? "" : MiniMessage.miniMessage().escapeTags(text);
    }

    /** Duration text: permanent / instant (kick) / remaining time. */
    private static String actionListDuration(PunishmentManager.PunishmentRecord r) {
        if (r.type == PunishmentManager.PunishType.KICK) return "<gray>instant</gray>";
        if (r.isPermanent()) return "<red>permanent</red>";
        long remaining = r.getRemainingMs();
        if (remaining <= 0) return "<dark_gray>expired</dark_gray>";
        return "<yellow>" + PunishmentManager.formatRemaining(remaining) + " left</yellow>";
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
     * Parses arguments for ban/mute/warn (require -time or -permanent).
     * Sends error messages to the sender on invalid flags.
     */
    private static PunishArgs parsePunishArgs(CommandSender sender, String[] args, int startIndex) {
        PunishArgs result = new PunishArgs();
        StringBuilder reasonBuilder = new StringBuilder();
        boolean hasTime = false;
        boolean hasPermanent = false;
        boolean hasIp = false;
        boolean hasHw = false;
        String timeStr = null;

        for (int i = startIndex; i < args.length; i++) {
            String arg = args[i];

            if (arg.toLowerCase().startsWith("-time:")) {
                String timeVal = arg.substring(6);
                if (timeVal.isEmpty()) {
                    // -time without a value — error
                    sender.sendMessage(MessageUtil.parse("<red>❌ -time flag requires a value (e.g. -time:30m)</red>"));
                    return null;
                }
                timeStr = timeVal;
                hasTime = true;
            } else if (arg.equalsIgnoreCase("-permanent")) {
                hasPermanent = true;
            } else if (arg.equalsIgnoreCase("-ip")) {
                hasIp = true;
            } else if (arg.equalsIgnoreCase("-hw")) {
                hasHw = true;
            } else {
                if (reasonBuilder.length() > 0) reasonBuilder.append(" ");
                reasonBuilder.append(arg);
            }
        }

        // Validation
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

        if (reasonBuilder.isEmpty()) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ You must specify a reason!</red>"
            ));
            return null;
        }

        result.reason = reasonBuilder.toString().trim();
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
     * Parses arguments for kick (no time/permanent).
     * Sends error messages to the sender on invalid flags.
     */
    private static FlagParseResult parseFlags(CommandSender sender, String[] args, int startIndex) {
        FlagParseResult result = new FlagParseResult();
        StringBuilder reasonBuilder = new StringBuilder();
        boolean hasIp = false;
        boolean hasHw = false;

        for (int i = startIndex; i < args.length; i++) {
            String arg = args[i];
            if (arg.equalsIgnoreCase("-ip")) {
                hasIp = true;
            } else if (arg.equalsIgnoreCase("-hw")) {
                hasHw = true;
            } else if (arg.toLowerCase().startsWith("-time:") || arg.equalsIgnoreCase("-permanent")) {
                // These flags are ignored for kick
                continue;
            } else {
                if (reasonBuilder.length() > 0) reasonBuilder.append(" ");
                reasonBuilder.append(arg);
            }
        }

        if (hasIp && hasHw) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Flags </red><white>-ip</white><red> and </red><white>-hw</white><red> cannot be used together!</red>"
            ));
            return null;
        }

        if (reasonBuilder.isEmpty()) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ You must specify a reason!</red>"
            ));
            return null;
        }

        result.reason = reasonBuilder.toString().trim();
        result.ip = hasIp;
        result.hw = hasHw;
        return result;
    }

    // =========================
    // CRASH (with confirmation)
    // =========================
    private static boolean handleCrash(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ui.command.punish.crash")) {
            sender.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to crash players!</red>"));
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
                + "<click:run_command:/ui punish crash cancel><dark_red>[</dark_red><red>✖ Cancel</red><dark_red>]</dark_red></click>"
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
                "<white>/ui punish ban <player> <reason> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw]</white>\n" +
                "<white>/ui punish mute <player> <reason> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw]</white>\n" +
                "<white>/ui punish kick <player> <reason> [-ip] [-hw]</white>\n" +
                "<white>/ui punish warn <player> <reason> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw]</white>\n" +
                "<white>/ui punish listwarns [player]</white>\n" +
                "<white>/ui punish unban <player></white>\n" +
                "<white>/ui punish unmute <player></white>\n" +
                "<white>/ui punish unwarn <player> <reason> <warnId></white>\n" +
                "<white>/ui punish actionlist [tab] [page]</white> <gray>(tabs: all, ban, mute, warn, kick)</gray>\n" +
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
            for (String action : List.of("ban", "mute", "kick", "warn", "listwarns", "unban", "unmute", "unwarn", "actionlist", "crash")) {
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
            } else if (action.equals("actionlist")) {
                // actionlist tabs
                completions.addAll(ACTIONLIST_TABS);
            } else if (List.of("ban", "mute", "kick", "warn", "unban", "unmute", "unwarn", "listwarns").contains(action)) {
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
            } else {
                // after the player name → punishment flags
                addFlagCompletions(action, args, completions);
            }
        } else if (args.length >= 5) {
            // Flags
            String action = args[1].toLowerCase();
            addFlagCompletions(action, args, completions);
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
        if (List.of("ban", "mute", "warn").contains(action)) {
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
        } else if (action.equals("kick")) {
            boolean hasIp = false, hasHw = false;
            for (int i = 3; i < args.length; i++) {
                String a = args[i].toLowerCase();
                if (a.equals("-ip")) hasIp = true;
                else if (a.equals("-hw")) hasHw = true;
            }
            if (!hasIp) completions.add("-ip");
            if (!hasHw) completions.add("-hw");
        }
    }
}
