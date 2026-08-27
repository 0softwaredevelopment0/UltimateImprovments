package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;

import com.ultimateimprovments.command.SubCommand;
import com.ultimateimprovments.combat.turret.TurretManager;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /ui turret &lt;toggle|mode|add|remove|list|clear&gt; — configures the turret
 * (end crystal) the player selected with shift + right-click.
 * <p>
 * The chat GUI buttons run these actions through {@code ClickEvent.runCommand},
 * so the permission defaults to {@code TRUE} for everyone ({@code ui.command.turret}).
 */
public final class TurretSubcommand implements SubCommand {

    private static final String PERMISSION = "ui.command.turret";

    @Override
    public String getName() {
        return "turret";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<red>Only players can configure turrets.</red>"));
            return true;
        }
        if (!player.hasPermission(PERMISSION)) {
            CommandErrors.noPermission(player);
            return true;
        }

        TurretManager manager = TurretManager.getInstance();
        EnderCrystal crystal = manager.getSelected(player);
        if (crystal == null) {
            player.sendMessage(MessageUtil.parse(
                    "<yellow>No turret selected. </yellow><white>Shift + right-click an end crystal first.</white>"));
            return true;
        }

        String action = args.length >= 2 ? args[1].toLowerCase() : "gui";
        switch (action) {
            case "toggle" -> {
                manager.toggle(crystal);
                manager.openGui(player, crystal);
            }
            case "mode" -> {
                manager.toggleMode(crystal);
                manager.openGui(player, crystal);
            }
            case "add" -> {
                if (args.length < 3) {
                    player.sendMessage(MessageUtil.parse(
                            "<yellow>Usage: </yellow><white>/ui turret add <nick|entity_type></white>"));
                    return true;
                }
                manager.addEntry(crystal, args[2]);
                manager.openGui(player, crystal);
            }
            case "remove" -> {
                if (args.length < 3) {
                    player.sendMessage(MessageUtil.parse(
                            "<yellow>Usage: </yellow><white>/ui turret remove <nick|entity_type></white>"));
                    return true;
                }
                manager.removeEntry(crystal, args[2]);
                manager.openGui(player, crystal);
            }
            case "list" -> manager.showList(player, crystal);
            case "clear" -> {
                manager.clearEntries(crystal);
                manager.openGui(player, crystal);
            }
            default -> manager.openGui(player, crystal);
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length != 2) return List.of();
        String partial = args[1].toLowerCase();
        return List.of("toggle", "mode", "add", "remove", "list", "clear", "gui")
                .stream()
                .filter(s -> s.startsWith(partial))
                .toList();
    }
}
