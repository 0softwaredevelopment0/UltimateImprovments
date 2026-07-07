package com.mcplugin.command.subcommands;

import com.mcplugin.command.SubCommand;
import com.mcplugin.util.MessageUtil;
import org.bukkit.command.CommandSender;

/**
 * /mp help — список доступных команд.
 */
public class HelpSubCommand implements SubCommand {

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        sender.sendMessage("");
        sender.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        sender.sendMessage(MessageUtil.parse("<gold>  ✦ </gold><white>MC-Plugin </white><gray>— Available Commands</gray>"));
        sender.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        sender.sendMessage("");

        addCmd(sender, "/mp help", "Command list");
        addCmd(sender, "/mp reload", "Reload plugin");
        addCmd(sender, "/mp checkver", "Check for updates");
        addCmd(sender, "/mp updatejar", "Download & install update");
        addCmd(sender, "/mp modules list|enable|disable", "Manage modules");
        sender.sendMessage("");

        addCmd(sender, "/mp auth forcelogin|resetauth|chgpass|delsession|logout", "Auth management");
        addCmd(sender, "/mp chgdim", "Teleportation menu");
        addCmd(sender, "/mp sethome|home|delhome|listhomes", "Home management");
        addCmd(sender, "/mp codepane key add|list|remove|modify", "Code panel keys");
        sender.sendMessage("");

        addCmd(sender, "/mp str dfc assemble|stats", "Dark fusion reactor");
        addCmd(sender, "/mp str magnet assemble|stats", "Magnet");
        addCmd(sender, "/mp str lightning enable|disable|stats", "Lightning structure");
        addCmd(sender, "/mp power off|reboot|confirm|undo", "Server power management");
        addCmd(sender, "/mp suicide", "Commit suicide");
        addCmd(sender, "/mp vanish <nick>", "Vanish player");
        addCmd(sender, "/mp notes", "Open notes");
        sender.sendMessage("");

        addCmd(sender, "/mp vote create|delete|change|stats", "Voting system");
        addCmd(sender, "/mp punish ban|mute|kick|warn|crash", "Punishment system");
        addCmd(sender, "/mp check|uncheck <nick>", "Anti-cheat check");
        addCmd(sender, "/mp ac overview|checks|players|player", "Anti-cheat stats");
        sender.sendMessage("");

        addCmd(sender, "/mp item int list|set|add", "Item integrity");
        addCmd(sender, "/mp setrad <nick> <value>", "Set radiation");
        addCmd(sender, "/mp invsee|endersee <player>", "View inventory");
        addCmd(sender, "/mp setspawn|spawn", "Spawn management");
        addCmd(sender, "/mp bc <message>", "Broadcast");
        addCmd(sender, "/mp report|reports|modreport", "Report system");
        sender.sendMessage("");

        addCmd(sender, "/mp opwhitelist add|remove|list|on|off", "OP whitelist");
        addCmd(sender, "/mp whitelist|blacklist", "Access lists");
        addCmd(sender, "/mp plugin <name> on|off|restart|info", "Plugin management");
        addCmd(sender, "/mp money give|list|remove|set", "Economy");
        addCmd(sender, "/mp swapjar confirm|cancel", "Jar swap");
        addCmd(sender, "/mp near [radius]", "Find nearby players");
        addCmd(sender, "/mp rtp [player]", "Random teleport");
        sender.sendMessage("");

        addCmd(sender, "/mp togglebb|togglesb|toggleping", "Toggle bossbar/scoreboard/ping");
        addCmd(sender, "/mp togglespeed|togglefly|toggleautocraft", "Toggle features");
        addCmd(sender, "/mp togglebind|toggleradview", "Toggle bind/radview");
        addCmd(sender, "/mp unlock book|sign", "Unlock book or sign");
        addCmd(sender, "/mp fly|god on|off", "Flight/God mode");
        addCmd(sender, "/mp heal|feed [player]", "Heal/Feed");
        sender.sendMessage("");

        sender.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        sender.sendMessage("");
        return true;
    }

    private static void addCmd(CommandSender sender, String cmd, String desc) {
        sender.sendMessage(MessageUtil.parse("<yellow>" + cmd + "</yellow>"));
        sender.sendMessage(MessageUtil.parse(" <gray>└ " + desc + "</gray>"));
    }
}
