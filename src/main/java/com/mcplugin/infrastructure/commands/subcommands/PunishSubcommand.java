package com.mcplugin.infrastructure.commands.subcommands;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.punish.PunishmentManager;
import com.mcplugin.infrastructure.punish.PunishJoinListener;
import com.mcplugin.infrastructure.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 🛡 PunishSubcommand — обработчик /mp punish.
 * <p>
 * Команды:
 * <pre>
 * /mp punish ban <player> <reason> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw]
 * /mp punish mute <player> <reason> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw]
 * /mp punish kick <player> <reason> [-ip] [-hw]
 * /mp punish warn <player> <reason> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw]
 * /mp punish listwarns <player>
 * /mp punish unban <player>
 * /mp punish unmute <player>
 * </pre>
 * <p>
 * Правила флагов:
 * <ul>
 *   <li>-time и -permanent несовместимы (оба — ошибка)</li>
 *   <li>Если не указан ни -time, ни -permanent — ошибка (для ban/mute/warn)</li>
 *   <li>-ip и -hw несовместимы</li>
 * </ul>
 */
public final class PunishSubcommand {

    private PunishSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcplugin.command.punish")) {
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
        if (!sender.hasPermission("mcplugin.command.punish.ban")) {
            sender.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to ban!</red>"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/mp punish ban <player> <reason> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw]</white>"
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
            // Офлайн-игрок — используем ники из ввода
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

        // Кикаем если онлайн
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

        // Если -ip или -hw — кикаем всех подходящих онлайн
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
        return true;
    }

    // =========================
    // MUTE
    // =========================
    private static boolean handleMute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcplugin.command.punish.mute")) {
            sender.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to mute!</red>"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/mp punish mute <player> <reason> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw]</white>"
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

        // Уведомление цели если онлайн
        if (target != null && target.isOnline()) {
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
        return true;
    }

    // =========================
    // KICK
    // =========================
    private static boolean handleKick(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcplugin.command.punish.kick")) {
            sender.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to kick!</red>"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/mp punish kick <player> <reason> [-ip] [-hw]</white>"
            ));
            return true;
        }

        // Парсим флаги (kick не требует -time/-permanent)
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

        // Кикаем цель
        PunishmentManager.kickPlayer(target, reason, sender.getName());
        sender.sendMessage(MessageUtil.parse(
                "<green>✔</green> <white>Player</white> <yellow>" + target.getName() + "</yellow> <white>has been kicked.</white>"
        ));

        // Если -ip или -hw — кикаем всех подходящих
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
        if (!sender.hasPermission("mcplugin.command.punish.warn")) {
            sender.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to warn!</red>"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/mp punish warn <player> <reason> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw]</white>"
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

        // Если -ip/-hw — варним всех подходящих
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
        return true;
    }

    // =========================
    // LISTWARNS
    // =========================
    private static boolean handleListWarns(CommandSender sender, String[] args) {
        if (args.length < 3) {
            // Показываем свои варны
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MessageUtil.parse(
                        "<red>❌ Usage: </red><white>/mp punish listwarns <player></white>"
                ));
                return true;
            }
            if (!sender.hasPermission("mcplugin.command.punish.listwarns.self")) {
                sender.sendMessage(MessageUtil.parse("<red>❌ You don't have permission!</red>"));
                return true;
            }
            showWarns(sender, player.getUniqueId().toString(), player.getName());
            return true;
        }

        String targetName = args[2];

        // Проверяем, смотрит ли на себя
        if (sender instanceof Player player && player.getName().equalsIgnoreCase(targetName)) {
            if (!sender.hasPermission("mcplugin.command.punish.listwarns.self")) {
                sender.sendMessage(MessageUtil.parse("<red>❌ You don't have permission!</red>"));
                return true;
            }
            showWarns(sender, player.getUniqueId().toString(), player.getName());
            return true;
        }

        // Смотрит на другого
        if (!sender.hasPermission("mcplugin.command.punish.listwarns.other")) {
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
            // Поищем по нику в БД
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
        try (java.sql.Connection con = com.mcplugin.infrastructure.database.DatabaseManager.getConnection();
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
        if (!sender.hasPermission("mcplugin.command.punish.ban")) {
            sender.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to unban!</red>"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/mp punish unban <player></white>"
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
        } else {
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>⚠</yellow> <white>No active ban found for</white> <yellow>" + targetName + "</yellow>"
            ));
        }
        return true;
    }

    private static boolean handleUnmute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcplugin.command.punish.mute")) {
            sender.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to unmute!</red>"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/mp punish unmute <player></white>"
            ));
            return true;
        }

        String targetName = args[2];
        @SuppressWarnings("deprecation")
        Player target = Bukkit.getPlayerExact(targetName);
        String uuid;

        if (target != null) {
            uuid = target.getUniqueId().toString();
            // Очищаем кеш мута
            PunishJoinListener.removeMuteCache(target);
            // Уведомляем игрока
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
        if (!sender.hasPermission("mcplugin.command.punish.warn")) {
            sender.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to unwarn!</red>"));
            return true;
        }
        if (args.length < 5) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/mp punish unwarn <player> <reason> <warnId></white>"
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
        } else {
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>⚠</yellow> <white>No active warn with ID</white> <yellow>" + warnId + "</yellow> <white>found for</white> <yellow>" + targetName + "</yellow>"
            ));
        }
        return true;
    }

    // =========================
    // FLAG PARSING
    // =========================

    /**
     * Результат парсинга флагов для наказаний, требующих time/permanent.
     */
    private static class PunishArgs {
        String reason;
        long expiresAt;
        boolean isPermanent;
        boolean ip;
        boolean hw;
        String timeStr; // для отображения
    }

    /**
     * Результат парсинга для kick (без time/permanent).
     */
    private static class FlagParseResult {
        String reason;
        boolean ip;
        boolean hw;
    }

    /**
     * Парсит аргументы для ban/mute/warn (требуют -time или -permanent).
     * Отправляет сообщения об ошибках sender при невалидных флагах.
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
                    // -time без значения — ошибка
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

        // Валидация
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
     * Парсит аргументы для kick (без time/permanent).
     * Отправляет сообщения об ошибках sender при невалидных флагах.
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
                // Для kick эти флаги игнорируются
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
    // USAGE
    // =========================
    private static void sendUsage(CommandSender sender) {
        sender.sendMessage(MessageUtil.parse(
                "<red>❌ Usage:</red>\n" +
                "<white>/mp punish ban <player> <reason> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw]</white>\n" +
                "<white>/mp punish mute <player> <reason> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw]</white>\n" +
                "<white>/mp punish kick <player> <reason> [-ip] [-hw]</white>\n" +
                "<white>/mp punish warn <player> <reason> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw]</white>\n" +
                "<white>/mp punish listwarns [player]</white>\n" +
                "<white>/mp punish unban <player></white>\n" +
                "<white>/mp punish unmute <player></white>\n" +
                "<white>/mp punish unwarn <player> <reason> <warnId></white>\n" +
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
            for (String action : List.of("ban", "mute", "kick", "warn", "listwarns", "unban", "unmute", "unwarn")) {
                if (action.startsWith(prefix)) {
                    completions.add(action);
                }
            }
        } else if (args.length == 3) {
            String action = args[1].toLowerCase();
            if (List.of("ban", "mute", "kick", "warn", "unban", "unmute", "unwarn", "listwarns").contains(action)) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    completions.add(p.getName());
                }
            }
        } else if (args.length >= 4) {
            // Флаги
            String action = args[1].toLowerCase();
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

        String last = args[args.length - 1].toLowerCase();
        return completions.stream().filter(s -> s.toLowerCase().startsWith(last)).collect(Collectors.toList());
    }
}
