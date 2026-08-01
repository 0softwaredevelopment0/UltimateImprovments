package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.SubCommand;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.command.CommandSender;

/**
 * /ui help — список доступных команд.
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
        sender.sendMessage(MessageUtil.parse("<gold>  ✦ </gold><white>UltimateImprovments </white><gray>— Available Commands</gray>"));
        sender.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        sender.sendMessage("");

        addCmd(sender, "/ui help", "Command list");
        addCmd(sender, "/ui reload", "Reload plugin");
        addCmd(sender, "/ui checkver", "Check for updates");
        addCmd(sender, "/ui updatejar", "Download & install update");
        addCmd(sender, "/ui modules list|enable|disable", "Manage modules");
        sender.sendMessage("");

        addCmd(sender, "/ui auth forcelogin|resetauth|chgpass|delsession|logout", "Auth management");
        addCmd(sender, "/ui chgdim", "Teleportation menu");
        addCmd(sender, "/ui sethome|home|delhome|listhomes", "Home management");
        addCmd(sender, "/ui codepane key add|list|remove|modify", "Code panel keys");
        sender.sendMessage("");

        addCmd(sender, "/ui str dfc assemble|stats", "Dark fusion reactor");
        addCmd(sender, "/ui str magnet assemble|stats", "Magnet");
        addCmd(sender, "/ui str lightning enable|disable|stats", "Lightning structure");
        addCmd(sender, "/ui power off|reboot|confirm|undo", "Server power management");
        addCmd(sender, "/ui suicide", "Commit suicide");
        addCmd(sender, "/ui vanish <nick>", "Vanish player");
        addCmd(sender, "/ui notes", "Open notes");
        sender.sendMessage("");

        addCmd(sender, "/ui vote create|delete|change|stats", "Voting system");
        addCmd(sender, "/ui punish ban|mute|kick|warn|crash", "Punishment system");
        addCmd(sender, "/ui check|uncheck <nick>", "Anti-cheat check");
        addCmd(sender, "/ui ac overview|checks|players|player", "Anti-cheat stats");
        sender.sendMessage("");

        addCmd(sender, "/ui item int list|set|add", "Item integrity");
        addCmd(sender, "/ui setrad <nick> <value>", "Set radiation");
        addCmd(sender, "/ui invsee|endersee <player>", "View inventory");
        addCmd(sender, "/ui setspawn|spawn", "Spawn management");
        addCmd(sender, "/ui bc <message>", "Broadcast");
        addCmd(sender, "/ui report|reports|modreport", "Report system");
        sender.sendMessage("");

        addCmd(sender, "/ui opwhitelist add|remove|list|on|off", "OP whitelist");
        addCmd(sender, "/ui whitelist|blacklist", "Access lists");
        addCmd(sender, "/ui plugin <name> on|off|restart|info", "Plugin management");
        addCmd(sender, "/ui money give|list|remove|set", "Economy");
        addCmd(sender, "/ui swapjar confirm|cancel", "Jar swap");
        addCmd(sender, "/ui near [radius]", "Find nearby players");
        addCmd(sender, "/ui rtp [player]", "Random teleport");
        sender.sendMessage("");

        addCmd(sender, "/ui togglebb|togglesb|toggleping", "Toggle bossbar/scoreboard/ping");
        addCmd(sender, "/ui togglespeed|togglefly|toggleautocraft", "Toggle features");
        addCmd(sender, "/ui togglebind|toggleradview", "Toggle bind/radview");
        addCmd(sender, "/ui unlock book|sign", "Unlock book or sign");
        addCmd(sender, "/ui fly|god on|off", "Flight/God mode");
        addCmd(sender, "/ui heal|feed [player]", "Heal/Feed");
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
