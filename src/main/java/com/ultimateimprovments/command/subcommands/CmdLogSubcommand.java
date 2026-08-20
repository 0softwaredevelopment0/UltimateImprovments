package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.chat.CmdLogger;
import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.command.SubCommand;
import com.ultimateimprovments.core.Permissions;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;

/**
 * /ui cmdlog <on|off> — toggles the in-chat command logger.
 * <p>
 * When on, every player command is logged to chat in the format:
 * <pre>
 * [UI] &lt;player&gt; uses /command
 *     with error: &lt;error&gt; / but don't have permission (perm) / and it runs correctly.
 * </pre>
 * The toggle is persisted in the DB ({@code cmdlog_meta}) and survives restarts.
 * Default is <b>off</b>.
 * <p>
 * Permission: {@code ui.command.cmdlog} (registered in {@code Permissions} — in code, not plugin.yml).
 */
public final class CmdLogSubcommand implements SubCommand {

    @Override
    public String getName() {
        return "cmdlog";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.CMD_CMDLOG)) {
            CommandErrors.noPermission(sender);
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Usage:</red> <white>/ui cmdlog <on|off></white>"));
            sender.sendMessage(MessageUtil.parse("<gray>Current: <yellow>"
                    + (CmdLogger.isEnabled() ? "on" : "off") + "</yellow></gray>"));
            return true;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "on" -> {
                CmdLogger.setEnabled(true);
                sender.sendMessage(MessageUtil.parse(
                        "<green>✔ <white>Command logging is now <green>enabled</green>.</white>"));
            }
            case "off" -> {
                CmdLogger.setEnabled(false);
                sender.sendMessage(MessageUtil.parse(
                        "<green>✔ <white>Command logging is now <red>disabled</red>.</white>"));
            }
            default -> sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Unknown state: <yellow>" + args[1]
                            + "</yellow>. Use <white>on</white> or <white>off</white>.</red>"));
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return List.of("on", "off");
        }
        return List.of();
    }
}
