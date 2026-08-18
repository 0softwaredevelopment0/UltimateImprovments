package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.SubCommand;
import com.ultimateimprovments.command.SubCommandRegistry;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /ui help — paginated list of ALL plugin commands.
 * <p>
 * Features:
 * <ul>
 *   <li>All registered subcommands (including aliases) come from {@link SubCommandRegistry} —\n *       new commands show up in help automatically.</li>
 *   <li>5 commands per page, each with a description.</li>
 *   <li>White command = has access, red = no permission ({@code ui.command.<name>}).</li>
 *   <li>At the bottom — page indicator and clickable {@code [<]} / {@code [>]} buttons.</li>
 *   <li>{@code /ui help <number>} opens a specific page.</li>
 *   <li>The command name is clickable — runs it, and shows the description on hover.</li>
 * </ul>
 */
public class HelpSubCommand implements SubCommand {

    /** How many commands are shown per page. */
    private static final int PER_PAGE = 5;

    /** Command descriptions by canonical name (lowercase). */
    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();

    static {
        put("help", "List of available commands");
        put("reload", "Reload the plugin");
        put("checkver", "Check for plugin updates");
        put("updatejar", "Download & install update");
        put("modules", "Manage modules (list/enable/disable)");
        put("auth", "Auth management (forcelogin/resetauth/...)");
        put("chgdim", "Teleport between dimensions (menu)");
        put("home", "Home management (home/sethome/delhome/...)");
        put("sethome", "Set a home point");
        put("delhome", "Delete a home point");
        put("listhomes", "List your homes");
        put("ophomels", "List OP homes");
        put("opdelhome", "Delete an OP home");
        put("codepane", "Code panel keys management");
        put("item", "Item integrity management");
        put("power", "Server power management (off/reboot/...)");
        put("suicide", "Commit suicide");
        put("forcesuicide", "Force-suicide a player");
        put("vanish", "Vanish a player");
        put("notes", "Open your notes");
        put("vote", "Voting system");
        put("punish", "Punishment system (ban/mute/kick/crash/...)");
        put("check", "Anti-cheat check on player");
        put("uncheck", "Remove anti-cheat check");
        put("ac", "Anti-cheat stats");
        put("invsee", "View/edit player inventory (online and offline)");
        put("endersee", "View/edit player ender chest (online and offline)");
        put("setspawn", "Set server spawn");
        put("spawn", "Teleport to spawn");
        put("broadcast", "Broadcast a message (-clean = no prefix)");
        put("clearchat", "Clear the chat (player or all)");
        put("report", "Report a player");
        put("reports", "List reports");
        put("modreport", "Moderate reports");
        put("repstatus", "Report status");
        put("redstone", "Blocked redstone chunks management");
        put("togglebb", "Toggle bossbar");
        put("togglesb", "Toggle scoreboard");
        put("toggleping", "Toggle ping display");
        put("togglespeed", "Toggle speed");
        put("togglefly", "Toggle fly");
        put("toggleautocraft", "Toggle autocraft");
        put("togglebind", "Toggle bind");
        put("toggleradview", "Toggle radiation view");
        put("unlock", "Unlock book or sign");
        put("askpos", "Request player's coordinates (dialog)");
        put("enchant", "Enchant manager (give/take/check, custom AoE)");
        put("pdc", "PDC manager (add/modify/remove/list/clear/container)");
        put("str", "Structures (dfc/magnet/lightning/...)");
        put("protection", "Protection block admin ops");
        put("menu", "Open admin menu");
        put("sudo", "Sudo mode (dangerous actions)");
        put("money", "Economy management");
        put("whitelist", "Whitelist management");
        put("opwhitelist", "OP whitelist management");
        put("blacklist", "Blacklist management");
        put("maint", "Maintenance mode");
        put("fly", "Toggle fly for player");
        put("flyspeed", "Set fly speed");
        put("uuid", "Get player UUID");
        put("getpos", "Get a player's coordinates");
        put("turret", "Configure end crystal turret (shift+RMB on crystal)");
        put("near", "Find nearby players");
        put("rtp", "Random teleport");
        put("meteor", "Meteor module");
        put("plugin", "Plugin management");
        put("heal", "Heal a player");
        put("feed", "Feed a player");
        put("expsplit", "Split experience");
        put("swapjar", "Swap plugin jar");
        put("god", "Toggle god mode");
        put("op", "Grant OP (console)");
        put("deop", "Revoke OP (console)");
        put("chgop", "Change OP status");
        put("setrad", "Set player radiation");
        put("cilist", "Custom item list");
        put("dont_run_this_command", "Get the impossible achievement (don't run it!)");
        put("advancement", "Start a timed advancement challenge (/ui advancement start woodcutter|teleport)");
        put("cmdblocklist", "List active command blocks (#, world, coordinates)");
    }

    private static void put(String name, String desc) {
        DESCRIPTIONS.put(name, desc);
    }

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
                sender.sendMessage("§eUsage: /ui help <page>");
            }
        }
        return showPage(sender, page);
    }

    /**
     * Shows a help page.
     */
    private static boolean showPage(CommandSender sender, int requestedPage) {
        SubCommandRegistry registry = SubCommandRegistry.getInstance();

        List<String> all = new ArrayList<>(registry.getAllCommandNames());
        Collections.sort(all);

        int totalPages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        int page = Math.max(1, Math.min(requestedPage, totalPages));

        int from = (page - 1) * PER_PAGE;
        int to = Math.min(from + PER_PAGE, all.size());
        List<String> pageCommands = all.subList(from, to);

        // ─── Header ───
        sender.sendMessage("§8┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓");
        sender.sendMessage("§8┃  §6✦ §fUltimateImprovments §7— Help §8(" + page + "/" + totalPages + ")");
        sender.sendMessage("§8┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫");

        // ─── Command rows ───
        for (String name : pageCommands) {
            String canonical = registry.resolveName(name);
            String canonicalName = canonical != null ? canonical : name;
            String desc = DESCRIPTIONS.getOrDefault(canonicalName, "Manage " + name);
            boolean access = hasAccess(sender, canonicalName);

            TextComponent cmd = new TextComponent((access ? "§f" : "§c") + "/ui " + name);
            cmd.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ui " + name));
            cmd.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(desc).create()));

            TextComponent line = new TextComponent("§8┃  ");
            line.addExtra(cmd);
            line.addExtra(new TextComponent(" §7— " + desc + (access ? "" : " §c(no permission)")));
            sender.spigot().sendMessage(line);
        }

        // ─── Footer: page indicator + [<] / [>] ───
        sender.sendMessage("§8┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫");

        TextComponent footer = new TextComponent("§8┃  §7Page §e" + page + "§7/" + totalPages + "   ");

        // [<] — previous page
        if (page > 1) {
            TextComponent prev = new TextComponent("§e[<]");
            prev.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ui help " + (page - 1)));
            prev.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder("§7Previous page").create()));
            footer.addExtra(prev);
        } else {
            footer.addExtra(new TextComponent("§8[<]"));
        }

        footer.addExtra(new TextComponent("  "));

        // [>] — next page
        if (page < totalPages) {
            TextComponent next = new TextComponent("§e[>]");
            next.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ui help " + (page + 1)));
            next.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder("§7Next page").create()));
            footer.addExtra(next);
        } else {
            footer.addExtra(new TextComponent("§8[>]"));
        }

        sender.spigot().sendMessage(footer);
        sender.sendMessage("§8┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛");
        return true;
    }

    /**
     * Whether the sender has access to the command.
     * Console and OP see everything; for players we check ui.command.<name> and wildcards.
     */
    private static boolean hasAccess(CommandSender sender, String name) {
        if (!(sender instanceof Player)) return true; // console — full access
        if (sender.hasPermission("ui.command." + name)) return true;
        if (sender.hasPermission("ui.command.*")) return true;
        if (sender.hasPermission("ui.*")) return true;
        return sender.isOp();
    }
}
