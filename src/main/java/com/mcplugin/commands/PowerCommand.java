package com.mcplugin.commands;

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
            sender.sendMessage("§8[§4⚠§8] §cКоманда /restart отключена. Используйте: §f/mp power reboot");
        } else {
            sender.sendMessage("§8[§4⚠§8] §cКоманда /stop отключена. Используйте: §f/mp power off");
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
        return Collections.emptyList();
    }
}
