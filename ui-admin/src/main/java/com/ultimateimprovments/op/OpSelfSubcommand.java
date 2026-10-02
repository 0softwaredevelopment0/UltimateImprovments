package com.ultimateimprovments.op;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.core.Permissions;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * /ui opself — a player requests operator status for themselves (DANGEROUS).
 * <p>
 * Flow:
 * <ul>
 *   <li>{@code /ui opself} (player) — forwards the request to the console.</li>
 *   <li>{@code /ui opself confirm} (console only) — grants OP to the requester.</li>
 *   <li>{@code /ui opself cancel} (console only) — denies the request.</li>
 * </ul>
 * The request expires after {@code op_self.ttl_seconds} (default 60 s). The
 * command is listed in {@code sudo.dangerous_commands}, so the sudo password
 * gate (SudoCommandInterceptor, ui-guard) applies before the request is sent.
 * OP is granted WITHOUT touching the operator whitelist — OpWhitelist has its
 * own commands and its own flow.
 */
public final class OpSelfSubcommand {

    private static final String PERMISSION = Permissions.CMD_OPSELF;
    private static final String MSG_PREFIX = "op_self.";

    /** The single pending request (one console — one slot). */
    private static PendingRequest pending;
    /** Bukkit task id of the scheduled expiry notification. */
    private static Integer expiryTaskId;

    private record PendingRequest(UUID uuid, String name, long createdAt) {}

    private OpSelfSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";

        // ── /ui opself confirm — console only ──
        if (sub.equals("confirm")) {
            if (sender instanceof Player) {
                sender.sendMessage(MessageUtil.parse(msg("console_only")));
                return true;
            }
            return confirm();
        }

        // ── /ui opself cancel — console only ──
        if (sub.equals("cancel")) {
            if (sender instanceof Player) {
                sender.sendMessage(MessageUtil.parse(msg("console_only")));
                return true;
            }
            return cancel();
        }

        if (!sub.isEmpty()) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>Usage: </red><white>/ui opself [confirm|cancel]</white>"));
            return true;
        }

        // ── /ui opself — player requests OP for themselves ──
        return request(sender);
    }

    // ════════════════════════════════════════
    // PLAYER REQUEST
    // ════════════════════════════════════════
    private static boolean request(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse(msg("player_only")));
            return true;
        }
        if (!isEnabled()) {
            player.sendMessage(MessageUtil.parse(msg("disabled")));
            return true;
        }
        if (!player.hasPermission(PERMISSION)) {
            CommandErrors.noPermission(player, PERMISSION);
            return true;
        }
        if (player.isOp()) {
            player.sendMessage(MessageUtil.parse(msg("already_op")));
            return true;
        }
        if (activePending() != null) {
            player.sendMessage(MessageUtil.parse(msg("already_pending")));
            return true;
        }

        long ttl = ttlSeconds();
        pending = new PendingRequest(player.getUniqueId(), player.getName(), System.currentTimeMillis());
        scheduleExpiry(player.getUniqueId(), ttl);

        player.sendMessage(MessageUtil.parse(
                msg("request_sent").replace("%seconds%", String.valueOf(ttl))));

        sendConsole(msg("console_request").replace("%player%", player.getName()));
        sendConsole(msg("console_confirm_hint"));
        sendConsole(msg("console_cancel_hint"));
        ConsoleLogger.warn("[OpSelf] " + player.getName()
                + " requested OP self — decide via /ui opself confirm|cancel (" + ttl + "s)");
        return true;
    }

    // ════════════════════════════════════════
    // CONSOLE CONFIRM
    // ════════════════════════════════════════
    private static boolean confirm() {
        PendingRequest req = consumePending();
        if (req == null) {
            sendConsole(msg("console_no_pending"));
            return true;
        }

        Player target = Bukkit.getPlayer(req.uuid());
        if (target == null || !target.isOnline()) {
            sendConsole(msg("console_target_offline").replace("%player%", req.name()));
            return true;
        }
        if (target.isOp()) {
            sendConsole(msg("console_already_op").replace("%player%", req.name()));
            return true;
        }
        // Re-check at decision time: the permission might have been revoked
        // while the request was pending.
        if (!target.hasPermission(PERMISSION)) {
            sendConsole(msg("console_no_permission").replace("%player%", req.name()));
            return true;
        }

        target.setOp(true);
        // Intentionally NOT OpManager.add — the operator whitelist has its own commands.
        target.sendMessage(MessageUtil.parse(msg("console_confirmed")));
        sendConsole(msg("console_result_confirmed").replace("%player%", target.getName()));
        ConsoleLogger.info("[OpSelf] Console confirmed the OP request of " + target.getName() + " — OP granted.");
        return true;
    }

    // ════════════════════════════════════════
    // CONSOLE CANCEL
    // ════════════════════════════════════════
    private static boolean cancel() {
        PendingRequest req = consumePending();
        if (req == null) {
            sendConsole(msg("console_no_pending"));
            return true;
        }

        Player target = Bukkit.getPlayer(req.uuid());
        if (target != null && target.isOnline()) {
            target.sendMessage(MessageUtil.parse(msg("console_denied")));
        }
        sendConsole(msg("console_result_denied").replace("%player%", req.name()));
        ConsoleLogger.info("[OpSelf] Console denied the OP request of " + req.name() + ".");
        return true;
    }

    // ════════════════════════════════════════
    // PENDING STATE
    // ════════════════════════════════════════
    /** @return the pending request if it exists and has not expired, otherwise null. */
    private static PendingRequest activePending() {
        PendingRequest req = pending;
        if (req == null) return null;
        if (System.currentTimeMillis() - req.createdAt() > ttlMillis()) {
            return null;
        }
        return req;
    }

    /** Clears and returns the pending request; null if there was none (or it had already expired). */
    private static PendingRequest consumePending() {
        cancelExpiryTask();
        PendingRequest req = pending;
        pending = null;
        if (req != null && System.currentTimeMillis() - req.createdAt() > ttlMillis()) {
            return null;
        }
        return req;
    }

    private static void scheduleExpiry(UUID uuid, long seconds) {
        cancelExpiryTask();
        expiryTaskId = Integer.valueOf(Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            expiryTaskId = null;
            PendingRequest req = pending;
            if (req == null || !req.uuid().equals(uuid)) return;
            pending = null;
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendMessage(MessageUtil.parse(msg("expired")));
            }
            ConsoleLogger.info("[OpSelf] OP request of " + req.name() + " expired unanswered.");
        }, seconds * 20L).getTaskId());
    }

    private static void cancelExpiryTask() {
        if (expiryTaskId != null) {
            Bukkit.getScheduler().cancelTask(expiryTaskId);
            expiryTaskId = null;
        }
    }

    /** Clears the pending state (plugin disable / reload). */
    public static void shutdown() {
        cancelExpiryTask();
        pending = null;
    }

    // ════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════
    private static void sendConsole(String miniMessage) {
        Bukkit.getConsoleSender().sendMessage(MessageUtil.parse(miniMessage));
    }

    private static boolean isEnabled() {
        return Main.getInstance().getConfig().getBoolean("op_self.enabled", true);
    }

    private static long ttlSeconds() {
        return Math.max(5, Main.getInstance().getConfig().getInt("op_self.ttl_seconds", 60));
    }

    private static long ttlMillis() {
        return ttlSeconds() * 1000L;
    }

    private static String msg(String key) {
        return MessagesManager.getString(MSG_PREFIX + key, "");
    }

    // ════════════════════════════════════════
    // TAB COMPLETION
    // ════════════════════════════════════════
    public static List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2 && !(sender instanceof Player)) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Stream.of("confirm", "cancel")
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
