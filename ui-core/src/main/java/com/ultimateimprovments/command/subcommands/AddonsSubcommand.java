package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.addon.AddonRegistry;
import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.core.Permissions;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * /ui addons — shows all addons of the UltimateImprovments family.
 * <p>
 * Core is the main (mandatory) part; everything else is an addon recognized
 * by the {@code addon-for: UI-Core} marker in its plugin.yml. The command
 * reports which addons are loaded, disabled or missing.
 */
public class AddonsSubcommand implements com.ultimateimprovments.command.SubCommand {

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (sender instanceof org.bukkit.entity.Player player
                && !player.hasPermission(Permissions.CMD_ADDONS)) {
            CommandErrors.noPermission(player);
            return true;
        }

        var addons = AddonRegistry.getAddons();
        long loaded = addons.stream().filter(a -> a.isInstalled()).count();

        sender.sendMessage(MessageUtil.parse(
                "<gold>════ <white>UltimateImprovments addons</white> <gold>════"));
        sender.sendMessage(MessageUtil.parse(
                "<gray>Core: <green>UI-Core</green> <dark_gray>(main, mandatory)</dark_gray> <gray>— "
                        + loaded + "/" + addons.size() + " addons loaded"));

        if (addons.isEmpty()) {
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>No addons found. Addons declare</yellow> <white>addon-for: UI-Core</white> <yellow>in plugin.yml.</yellow>"));
            return true;
        }

        for (var addon : addons) {
            String status;
            if (addon.isInstalled()) {
                Plugin p = org.bukkit.Bukkit.getPluginManager().getPlugin(addon.getPluginName());
                boolean enabled = p != null && p.isEnabled();
                status = enabled
                        ? "<green>✔ loaded"
                        : "<yellow>⚠ loaded (disabled)";
            } else {
                status = "<red>✖ not installed";
            }
            sender.sendMessage(MessageUtil.parse(
                    "<gray> • <white>" + addon.getPluginName() + "</white> " + status));
        }

        if (!AddonRegistry.isDiscovered()) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_gray>Addon discovery has not run yet — list may be incomplete.</dark_gray>"));
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
