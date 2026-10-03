package com.ultimateimprovments.command;

import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Central command-error reporter with ERROR CODES.
 * <p>
 * Every message is prefixed with a three-digit code so players and staff can
 * reference errors unambiguously. The five DEFAULT codes are handled here and
 * their texts live in the config (both language sections:
 * {@code messages.general.errors.*} / {@code messages_en.general.errors.*}):
 * <ul>
 *   <li>{@code 001} — invalid command syntax or unknown command;</li>
 *   <li>{@code 002} — insufficient permissions, requires "X";</li>
 *   <li>{@code 003} — command requires a sudo password, but the sender is not
 *       allowed to use sudo mode (requires permission "X");</li>
 *   <li>{@code 004} — wrong command sender type, allowed: "X,X...", got: "X";</li>
 *   <li>{@code 005} — unknown error while executing the command.</li>
 * </ul>
 * <p>
 * COMMAND-SPECIFIC errors (e.g. "amount cannot be negative") stay in the class
 * of the command that raises them, keep their own text and take the NEXT
 * sequential number (006, 007, ...) declared as a constant in that class; they
 * are reported via {@link #custom(CommandSender, int, String)} so the code
 * prefix stays uniform.
 */
public final class CommandErrors {

    /** 001 — invalid syntax or unknown command. */
    public static final int ERR_BAD_SYNTAX = 1;
    /** 002 — insufficient permissions. */
    public static final int ERR_NO_PERMISSION = 2;
    /** 003 — sudo required, but the sender may not use sudo mode. */
    public static final int ERR_SUDO_REQUIRED = 3;
    /** 004 — wrong command sender type. */
    public static final int ERR_WRONG_EXECUTOR = 4;
    /** 005 — unexpected error while executing the command. */
    public static final int ERR_UNKNOWN_FAILURE = 5;
    /** 011 — the command is disabled on this server. */
    public static final int ERR_COMMAND_DISABLED = 11;
    /** 018 — the command belongs to a module that is disabled on this server. */
    public static final int ERR_MODULE_DISABLED = 18;

    private CommandErrors() {}

    // ═══════════ 001 — BAD SYNTAX / UNKNOWN COMMAND ═══════════

    /**
     * 001: invalid command syntax or unknown command.
     * Used by the /ui dispatcher for unknown subcommands and by commands
     * for malformed usage.
     */
    public static void syntaxError(CommandSender sender) {
        send(sender, ERR_BAD_SYNTAX, "general.errors.001",
                "<red>Invalid command syntax or unknown command! </red>"
                        + "<gray>Use </gray><white>/ui help</white><gray> for the command list.</gray>");
    }

    /** 001 for an unknown subcommand (same message, kept for call-site clarity). */
    public static void unknownCommand(CommandSender sender, String sub) {
        syntaxError(sender);
    }

    // ═══════════ 002 — NO PERMISSION ═══════════

    /**
     * 002: insufficient permissions (unknown required permission —
     * the "requires" clause is omitted).
     */
    public static void noPermission(CommandSender sender) {
        noPermission(sender, null);
    }

    /** 002: insufficient permissions, requires the given permission node. */
    public static void noPermission(CommandSender sender, String permission) {
        if (sender == null || !(sender instanceof Player)) return;
        CommandOutcomeTracker.markDenied(sender);
        String body = MessagesManager.getString("general.errors.002",
                "<red>Insufficient permissions to execute this command</red>");
        body = body.replace("%requires%", requiresClause(permission));
        sender.sendMessage(MessageUtil.parse(header(ERR_NO_PERMISSION) + body));
    }

    // ═══════════ 003 — SUDO REQUIRED ═══════════

    /**
     * 003: the command requires a sudo password, but the sender is not allowed
     * to use sudo mode at all (missing the given permission, normally
     * {@code ui.sudo}).
     */
    public static void sudoRequired(CommandSender sender, String permission) {
        if (sender == null || !(sender instanceof Player)) return;
        CommandOutcomeTracker.markDenied(sender);
        String body = MessagesManager.getString("general.errors.003",
                "<red>This command requires a sudo password, but you are not allowed to use sudo mode</red>");
        body = body.replace("%requires%", requiresClause(permission));
        sender.sendMessage(MessageUtil.parse(header(ERR_SUDO_REQUIRED) + body));
    }

    // ═══════════ 004 — WRONG EXECUTOR ═══════════

    /**
     * 004: wrong command sender type.
     *
     * @param allowed comma-separated list of allowed sender types, e.g. "player, console"
     */
    public static void wrongExecutor(CommandSender sender, String allowed) {
        if (sender == null) return;
        String body = MessagesManager.getString("general.errors.004",
                "<red>Wrong command sender type. Allowed: </red><white>%allowed%</white>"
                        + "<red>, got: </red><white>%got%</white>")
                .replace("%allowed%", allowed)
                .replace("%got%", senderType(sender));
        sender.sendMessage(MessageUtil.parse(header(ERR_WRONG_EXECUTOR) + body));
    }

    /** 004 convenience: the command can only be used by players. */
    public static void playerOnly(CommandSender sender) {
        wrongExecutor(sender, "player");
    }

    // ═══════════ 005 — UNKNOWN ERROR ═══════════

    /** 005: an unexpected error occurred while executing the command. */
    public static void unknownError(CommandSender sender) {
        send(sender, ERR_UNKNOWN_FAILURE, "general.errors.005",
                "<red>An unknown error occurred while executing this command. Please report it to the administrator.</red>");
    }

    // ═══════════ 011 — COMMAND DISABLED ═══════════

    /**
     * 011: the command is disabled on this server (e.g. vanilla commands
     * superseded by /ui equivalents: /op|deop|stop|restart — use
     * /ui op|deop, /ui power off|reboot).
     */
    public static void commandDisabled(CommandSender sender) {
        send(sender, ERR_COMMAND_DISABLED, "general.errors.011",
                "<red>This command is disabled on this server.</red>");
    }

    // ═══════════ 018 — MODULE DISABLED ═══════════

    /**
     * 018: the command belongs to a module/feature that is disabled in the
     * config on this server (e.g. /ui opwhitelist with
     * {@code op_lists.whitelist.enabled = false}).
     *
     * @param module module/config key name shown in the appended clause
     *               (null/blank omits the clause)
     */
    public static void moduleDisabled(CommandSender sender, String module) {
        if (sender == null) return;
        String body = MessagesManager.getString("general.errors.018",
                "<red>This command belongs to a module that is disabled on this server</red>");
        if (module != null && !module.isBlank()) {
            String clause = MessagesManager.getString("general.errors.module",
                    "<gray> — module: \"<white>%module%</white>\"</gray>");
            body += clause.replace("%module%", module);
        }
        sender.sendMessage(MessageUtil.parse(header(ERR_MODULE_DISABLED) + body));
    }

    // ═══════════ COMMAND-SPECIFIC ERRORS (006+) ═══════════

    /**
     * Reports a command-specific error with its own sequential code
     * (declared as a constant in the command's class). The message text
     * stays with the command — only the code prefix is uniform.
     */
    public static void custom(CommandSender sender, int code, String miniMessage) {
        if (sender == null) return;
        sender.sendMessage(MessageUtil.parse(header(code) + miniMessage));
    }

    // ═══════════ INTERNALS ═══════════

    private static void send(CommandSender sender, int code, String key, String def) {
        if (sender == null) return;
        String body = MessagesManager.getString(key, def);
        sender.sendMessage(MessageUtil.parse(header(code) + body));
    }

    /** Uniform code prefix: "❌ [002] ". */
    private static String header(int code) {
        return "<red>❌ [<yellow>" + String.format(Locale.ROOT, "%03d", code) + "</yellow>]</red> ";
    }

    /**
     * Localized " — required: X" clause injected into 002/003 via %requires%;
     * empty when the permission is unknown (legacy call sites).
     */
    private static String requiresClause(String permission) {
        if (permission == null || permission.isBlank()) return "";
        String tpl = MessagesManager.getString("general.errors.requires",
                "<gray> — required: \"<white>%permission%</white>\"</gray>");
        return tpl.replace("%permission%", permission);
    }

    /** Human-readable sender type for the 004 message. */
    private static String senderType(CommandSender sender) {
        if (sender instanceof Player) return "player";
        if (sender instanceof ConsoleCommandSender) return "console";
        if (sender instanceof RemoteConsoleCommandSender) return "rcon";
        if (sender instanceof BlockCommandSender) return "command_block";
        return sender.getClass().getSimpleName().toLowerCase(Locale.ROOT);
    }
}
