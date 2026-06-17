package com.mcplugin.commands;

import com.mcplugin.util.MessageUtil;

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
            setDescription("Запросить перезапуск сервера (требует подтверждения из консоли)");
            setUsage("/restart");
        } else {
            setDescription("Запросить выключение сервера (требует подтверждения из консоли)");
            setUsage("/stop");
        }
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (isRestart) {
            sender.sendMessage(MessageUtil.parse("<dark_gray>[<dark_red>⚠</dark_red>] <red>Команда /restart отключена. Используйте: <white>/mp power reboot</white></dark_gray>"));
        } else {
            sender.sendMessage(MessageUtil.parse("<dark_gray>[<dark_red>⚠</dark_red>] <red>Команда /stop отключена. Используйте: <white>/mp power off</white></dark_gray>"));
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
        return Collections.emptyList();
    }
}
