package com.mcplugin.infrastructure.commands;

import com.mcplugin.infrastructure.config.MessagesManager;
import com.mcplugin.infrastructure.util.MessageUtil;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

public class PowerCommand extends Command {

    private final boolean isRestart;

    public PowerCommand(String name, boolean isRestart) {
        super(name);
        this.isRestart = isRestart;
        if (isRestart) {
            setDescription("Request server restart (requires console confirmation)");
            setUsage("/restart");
        } else {
            setDescription("Request server shutdown (requires console confirmation)");
            setUsage("/stop");
        }
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (isRestart) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("power.restart_message",
                    "<dark_gray>[<dark_red>⚠</dark_red>] <red>The /restart command is disabled. Use:</red> <white>/mp power reboot</white>")));
        } else {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("power.stop_message",
                    "<dark_gray>[<dark_red>⚠</dark_red>] <red>The /stop command is disabled. Use:</red> <white>/mp power off</white>")));
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
        return Collections.emptyList();
    }
}
