package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.SubCommand;
import com.ultimateimprovments.command.SubCommandRegistry;
import com.ultimateimprovments.util.MessageUtil;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * /ui help — paginated list of ALL plugin commands with a per-command detail view.
 * <p>
 * Two levels:
 * <ul>
 *   <li><b>/ui help [page]</b> — pages of {@code /ui <cmd> — <short usage>} rows.
 *       The command name is clickable (LEFT CLICK) → opens
 *       {@code /ui help <cmd>} — the detail view.</li>
 *   <li><b>/ui help &lt;cmd&gt; [sub]</b> — detailed card: description, full usage
 *       lines (one per subcommand / flag variant), permission node. Every usage
 *       line is clickable → puts the command into the chat input.</li>
 * </ul>
 * Non-registered names fall back to the detail view too (graceful "unknown").
 */
public class HelpSubCommand implements SubCommand {

    /** How many command rows fit on one list page. */
    private static final int PER_PAGE = 8;

    // =========================================================================
    // COMMAND DATABASE: canonical name → {short usage, description, detailed
    // usage lines, subcommands with their own usage/description}
    // =========================================================================

    /** One usage line: the args part after "/ui <name> " (may be empty). */
    record Usage(String args, String note) {
        String full(String cmd) {
            return args == null || args.isEmpty() ? "/ui " + cmd : "/ui " + cmd + " " + args;
        }
    }

    /** A subcommand row shown on the detail screen (clickable). */
    record Sub(String name, String args, String desc) {}

    static final class Cmd {
        final String desc;
        final List<Usage> usages = new ArrayList<>();
        final List<Sub> subs = new ArrayList<>();
        Cmd(String desc) { this.desc = desc; }
        Cmd u(String args, String note) { usages.add(new Usage(args, note)); return this; }
        Cmd sub(String name, String args, String desc) { subs.add(new Sub(name, args, desc)); return this; }
    }

    private static final Map<String, Cmd> COMMANDS = new LinkedHashMap<>();

    private static Cmd cmd(String name, String desc) {
        Cmd c = new Cmd(desc);
        COMMANDS.put(name, c);
        return c;
    }

    static {
        // ── Core ──
        cmd("help", "List of all plugin commands (this screen)")
                .u("[page]", "open a specific page")
                .u("<command>", "detailed help for a command")
                .u("<command> <subcommand>", "detailed help for a subcommand");
        cmd("reload", "Reload the plugin configuration")
                .u("", "reload config.yml, messages and modules");
        cmd("checkver", "Check for plugin updates")
                .u("", "compare the current version with the latest release");
        cmd("updatejar", "Download & install the plugin update")
                .u("", "downloads the latest JAR and schedules a swap");
        cmd("modules", "Manage modules")
                .u("list", "show all modules and their state")
                .u("enable <module>", "enable a module")
                .u("disable <module>", "disable a module");
        cmd("auth", "Auth management")
                .u("forcelogin <player>", "log the player in server-side")
                .u("resetauth <player>", "reset the player's password")
                .u("unregister <player>", "remove the player's auth data");
        cmd("chgdim", "Teleport between dimensions (menu)")
                .u("", "opens the dimension picker menu");
        cmd("dont_run_this_command", "Get the impossible achievement (don't run it!)")
                .u("", "seriously, don't");
        cmd("advancement", "Timed advancement challenges")
                .u("start <woodcutter|teleport>", "start a challenge")
                .u("stop", "stop the active challenge");
        cmd("cmdblocklist", "List active command blocks")
                .u("[page]", "paginated list (#, world, coordinates)");

        // ── Essentials: homes / teleport ──
        cmd("home", "Home management")
                .u("", "teleport to your home")
                .u("sethome", "set your home point")
                .u("delhome", "delete your home point")
                .u("listhomes", "list your homes")
                .u("ophomels <player>", "list a player's homes (OP)")
                .u("opdelhome <player>", "delete a player's home (OP)")
                .sub("sethome", "", "set your home point")
                .sub("delhome", "", "delete your home point")
                .sub("listhomes", "", "list your homes")
                .sub("ophomels", "<player>", "list a player's homes (OP)")
                .sub("opdelhome", "<player>", "delete a player's home (OP)");
        cmd("sethome", "Set your home point").u("", "one home per player");
        cmd("delhome", "Delete your home point").u("", "");
        cmd("listhomes", "List your homes").u("", "");
        cmd("ophomels", "List OP homes").u("[player]", "");
        cmd("opdelhome", "Delete an OP home").u("<player>", "");
        cmd("spawn", "Teleport to spawn").u("", "warps you to the server spawn");
        cmd("setspawn", "Set the server spawn").u("", "spawn = your current position");
        cmd("rtp", "Random teleport").u("", "random safe location in the wild");
        cmd("near", "Find nearby players").u("[radius]", "lists players within the radius");
        cmd("getpos", "Get a player's coordinates").u("<player>", "");
        cmd("uuid", "Get a player's UUID").u("[player]", "defaults to yourself");
        cmd("askpos", "Request a player's coordinates")
                .u("<player>", "sends a dialog; the player decides to share or not");
        cmd("chgdim", null); // duplicate guard — remove below
        COMMANDS.remove("chgdim"); // keep the core entry only

        // ── Essentials: player state ──
        cmd("heal", "Heal a player").u("[player]", "defaults to yourself");
        cmd("feed", "Feed a player").u("[player]", "restores hunger");
        cmd("fly", "Toggle fly").u("[player]", "");
        cmd("flyspeed", "Set fly speed").u("<0-10>", "0.1 steps, 1 = normal");
        cmd("god", "Toggle god mode").u("[player]", "");
        cmd("suicide", "Commit suicide").u("", "kills your character");
        cmd("forcesuicide", "Force-suicide a player").u("<player>", "");
        cmd("expsplit", "Split experience").u("<player>", "splits your XP with a player");
        cmd("togglespeed", "Toggle speed").u("", "");
        cmd("togglefly", "Toggle fly (legacy)").u("", "");
        cmd("vanish", "Vanish a player").u("[player]", "");
        cmd("notes", "Open your notes").u("", "personal notepad GUI");

        // ── Items / enchant / PDC ──
        cmd("enchant", "Enchant manager (incl. custom AoE)")
                .u("give <enchant> <level>", "add an enchantment to the held item")
                .u("take <enchant>", "remove an enchantment")
                .u("check", "list enchantments on the held item")
                .u("aoe <level>", "set the custom AoE level");
        cmd("pdc", "PDC manager")
                .u("list", "show PDC keys on the held item")
                .u("add <key> <type> <value>", "add a key")
                .u("modify <key> <type> <value>", "change a key")
                .u("remove <key>", "remove a key")
                .u("clear", "remove all keys")
                .u("container", "inspect the container PDC");
        cmd("item", "Item NBT editor (online and offline)")
                .u("<player>", "open the item editor for a player");
        cmd("cilist", "Custom item list").u("", "all plugin custom items with ids");
        cmd("unlock", "Unlockables")
                .u("book", "unlock the book crafting")
                .u("sign", "unlock the sign crafting");

        // ── Admin / moderation ──
        cmd("rep", "Reputation: numeric scale + Discord Account Standing (staff-issued)")
                .u("[player]", "view your or another player's reputation and status")
                .u("give <player> <±N> [reason]", "change reputation (staff)")
                .u("set <player> <N>", "set an absolute value (admin)")
                .u("status <player> <allgood|limited|verylimited|atrisk|suspended|none>", "issue a Discord Account Standing (moderator)")
                .u("top [limit]", "top players by reputation")
                .u("history [player]", "last reputation changes")
                .sub("give", "<player> <±N> [reason]", "change reputation (staff)")
                .sub("set", "<player> <N>", "set an absolute value (admin)")
                .sub("status", "<player> <allgood|limited|verylimited|atrisk|suspended|none>", "issue a Discord Account Standing")
                .sub("top", "[limit]", "top players by reputation")
                .sub("history", "[player]", "last reputation changes");
        cmd("punish", "Punishment system")
                .u("ban <player> <reason> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw]", "ban a player")
                .u("mute <player> <reason> [-time:...] [-permanent] [-ip] [-hw]", "mute a player")
                .u("kick <player> <reason> [-ip] [-hw]", "kick a player")
                .u("warn <player> <reason> [-time:...] [-permanent]", "warn a player")
                .u("listwarns [player]", "list warns")
                .u("unban <player>", "remove a ban")
                .u("unmute <player>", "remove a mute")
                .u("unwarn <player> <warnId>", "remove a warn")
                .u("actionlist <player>", "punishment history")
                .u("crash <effect> <player>", "troll-crash effect")
                .sub("ban", "<player> <reason> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw]", "ban a player")
                .sub("mute", "<player> <reason> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw]", "mute a player")
                .sub("kick", "<player> <reason> [-ip] [-hw]", "kick a player")
                .sub("warn", "<player> <reason> [-time:<N>s|m|h|d] [-permanent]", "warn a player")
                .sub("listwarns", "[player]", "list warnings")
                .sub("unban", "<player>", "remove a ban")
                .sub("unmute", "<player>", "remove a mute")
                .sub("unwarn", "<player> <warnId>", "remove a warning")
                .sub("actionlist", "<player>", "full punishment history")
                .sub("crash", "<particle|entity|bossbar|chat|scoreboard|team|chunk|explosion|title> <player>", "fake crash effect");
        cmd("whitelist", "Custom whitelist management")
                .u("on", "enable the whitelist")
                .u("off", "disable the whitelist")
                .u("add <player>", "add a player")
                .u("remove <player>", "remove a player")
                .u("list", "show whitelisted players")
                .sub("on", "", "enable the whitelist")
                .sub("off", "", "disable the whitelist")
                .sub("add", "<player>", "add a player")
                .sub("remove", "<player>", "remove a player")
                .sub("list", "", "show the list");
        cmd("blacklist", "Blacklist management")
                .u("add <player> [reason]", "blacklist a player")
                .u("remove <player>", "remove from the blacklist")
                .u("list", "show blacklisted players")
                .sub("add", "<player> [reason]", "blacklist a player")
                .sub("remove", "<player>", "remove from the blacklist")
                .sub("list", "", "show the list");
        cmd("opwhitelist", "OP whitelist management")
                .u("on", "only OP-whitelisted players can be OP")
                .u("off", "disable the OP whitelist")
                .u("add <player>", "allow a player to be OP")
                .u("remove <player>", "disallow a player")
                .u("list", "show the OP whitelist")
                .sub("on", "", "enable")
                .sub("off", "", "disable")
                .sub("add", "<player>", "add a player")
                .sub("remove", "<player>", "remove a player")
                .sub("list", "", "show the list");
        cmd("check", "Anti-cheat check on a player").u("<player>", "freezes and flags for a moderator");
        cmd("uncheck", "End an anti-cheat check").u("<player>", "");
        cmd("ac", "Anti-cheat stats").u("[player]", "");
        cmd("sudo", "Sudo mode (dangerous)").u("", "execute as console after confirm");
        cmd("execchat", "Execute a chat command as another").u("<player> <text>", "");
        cmd("op", "Grant operator (with confirmation)").u("<player>", "");
        cmd("deop", "Revoke operator (with confirmation)").u("<player>", "");
        cmd("oplist", "View the operator list").u("[page]", "");
        cmd("maint", "Maintenance mode")
                .u("on", "only the maintenance whitelist can join")
                .u("off", "open the server back")
                .u("add <player>", "whitelist for maintenance")
                .u("remove <player>", "");
        cmd("protection", "Protection block admin ops").u("", "admin utilities");
        cmd("plugin", "Plugin management").u("list|enable|disable|reload ...", "");
        cmd("swapjar", "Swap the plugin JAR").u("", "hot-swap after an update");
        cmd("menu", "Open the admin menu").u("", "");
        cmd("cmdblocklist", null);
        COMMANDS.remove("cmdblocklist"); // already in Core section

        // ── Chat / social ──
        cmd("broadcast", "Broadcast a message").u("<text> [-clean]", "-clean = without the plugin prefix");
        cmd("clearchat", "Clear the chat").u("[player|all]", "");
        cmd("chatchnl", "Switch your chat channel")
                .u("<local|global|world|private|admin|check|console|linux>", "");
        cmd("report", "Report a player").u("<player> <reason>", "");
        cmd("reports", "List reports").u("[page]", "");
        cmd("modreport", "Moderate reports").u("<id> accept|decline", "");
        cmd("repstatus", "Report status").u("<id>", "");
        cmd("money", "Economy management")
                .u("give <player> <amount>", "")
                .u("take <player> <amount>", "")
                .u("set <player> <amount>", "")
                .u("balance [player]", "");
        cmd("vote", "Voting system")
                .u("create <name> <option1> <option2> [...]", "create a vote")
                .u("vote <name> <option>", "cast a vote")
                .u("view <name>", "live results")
                .u("stats <name>", "final stats")
                .u("change <name> <add|remove> <option>", "edit options")
                .u("delete <name>", "delete a vote");
        cmd("msg", "Private message (overrides /msg)").u("<player> <text>", "");

        // ── Toggles / UI ──
        cmd("togglebb", "Toggle the bossbar").u("", "");
        cmd("togglesb", "Toggle the scoreboard").u("", "");
        cmd("toggleping", "Toggle the ping display").u("", "");
        cmd("toggleautocraft", "Toggle autocraft").u("", "");
        cmd("togglebind", "Toggle bind mode").u("", "");
        cmd("viewrad", "Toggle the radiation view").u("", "shows radiation sources around you");

        // ── World / structures ──
        cmd("redstone", "Blocked redstone chunks").u("list|clear", "lagged chunks management");
        cmd("turret", "End-crystal turret config").u("", "shift+RMB on a crystal");
        cmd("meteor", "Meteor module").u("", "");
        cmd("space", "Space dimension").u("enter|exit", "");

        // ── Misc ──
        cmd("codepane", "Code panel keys")
                .u("give <player> <key> [flags]", "issue a key")
                .u("list", "list issued keys");
        cmd("power", "Server power management").u("off|reboot|...", "fake admin powers (troll)");
        cmd("setrad", "Set a player's radiation").u("<player> <mSv>", "debug tool");
    }

    // =========================================================================
    // EXECUTE
    // =========================================================================

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // /ui help [<page>]  |  /ui help <command> [subcommand]
        if (args.length >= 2) {
            String first = args[1];
            // Pure number → page
            try {
                int page = Integer.parseInt(first);
                return showPage(sender, page);
            } catch (NumberFormatException ignored) {
                // fall through → command detail
            }
            String sub = args.length >= 3 ? args[2] : null;
            return showDetail(sender, first.toLowerCase(), sub == null ? null : sub.toLowerCase());
        }
        return showPage(sender, 1);
    }

    // =========================================================================
    // LIST: /ui help [page]
    // =========================================================================

    private static boolean showPage(CommandSender sender, int requestedPage) {
        SubCommandRegistry registry = SubCommandRegistry.getInstance();

        // Only canonical names, sorted — aliases are NOT separate rows.
        List<String> all = new ArrayList<>(registry.getAllCommandNames());
        Collections.sort(all, String.CASE_INSENSITIVE_ORDER);
        // Dedup aliases → canonical
        List<String> canonical = new ArrayList<>();
        for (String n : all) {
            String c = registry.resolveName(n);
            String cn = c != null ? c : n;
            if (!canonical.contains(cn)) canonical.add(cn);
        }
        all = canonical;

        int totalPages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        int page = Math.max(1, Math.min(requestedPage, totalPages));

        int from = (page - 1) * PER_PAGE;
        int to = Math.min(from + PER_PAGE, all.size());

        // ─── Header ───
        sender.sendMessage(MessageUtil.parse("<dark_gray>┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓"));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃ <gold>✦ <white>UltimateImprovments <gray>— Help <dark_gray>("
                + page + "/" + totalPages + ")"));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫"));

        // ─── Rows: /ui cmd — short usage ───
        for (String name : all.subList(from, to)) {
            Cmd info = COMMANDS.get(name);
            String desc = info != null ? info.desc : "Manage " + name;
            // Short usage: the first usage line, or the first subcommand
            String shortUsage;
            if (info != null && !info.usages.isEmpty()) {
                Usage u = info.usages.get(0);
                shortUsage = u.args == null || u.args.isEmpty() ? "" : " " + u.args;
            } else if (info != null && !info.subs.isEmpty()) {
                shortUsage = " <" + info.subs.get(0).name + "> ...";
            } else {
                shortUsage = "";
            }
            boolean access = hasAccess(sender, name);

            Component cmdPart = Component.text("/ui " + name)
                    .color(access ? NamedTextColor.WHITE : NamedTextColor.RED)
                    .clickEvent(ClickEvent.runCommand("/ui help " + name))
                    .hoverEvent(HoverEvent.showText(MessageUtil.parse(
                            "<gray>Click for details")));

            Component line = MessageUtil.parse("<dark_gray>┃ ")
                    .append(cmdPart)
                    .append(MessageUtil.parse("<white>" + shortUsage))
                    .append(MessageUtil.parse(" <dark_gray>— <gray>" + desc
                            + (access ? "" : " <red>(no permission)")));
            sender.sendMessage(line);
        }

        // ─── Footer: page indicator + [<] / [>] ───
        sender.sendMessage(MessageUtil.parse("<dark_gray>┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫"));

        Component footer = MessageUtil.parse(
                "<dark_gray>┃ <gray>Page <yellow>" + page + "<gray>/" + totalPages + "   ");

        if (page > 1) {
            footer = footer.append(Component.text("[<]")
                    .color(NamedTextColor.YELLOW)
                    .clickEvent(ClickEvent.runCommand("/ui help " + (page - 1)))
                    .hoverEvent(HoverEvent.showText(MessageUtil.parse("<gray>Previous page"))));
        } else {
            footer = footer.append(MessageUtil.parse("<dark_gray>[<]"));
        }
        footer = footer.append(MessageUtil.parse("  "));
        if (page < totalPages) {
            footer = footer.append(Component.text("[>]")
                    .color(NamedTextColor.YELLOW)
                    .clickEvent(ClickEvent.runCommand("/ui help " + (page + 1)))
                    .hoverEvent(HoverEvent.showText(MessageUtil.parse("<gray>Next page"))));
        } else {
            footer = footer.append(MessageUtil.parse("<dark_gray>[>]"));
        }
        sender.sendMessage(footer);
        sender.sendMessage(MessageUtil.parse("<dark_gray>┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛"));
        return true;
    }

    // =========================================================================
    // DETAIL: /ui help <command> [subcommand]
    // =========================================================================

    private static boolean showDetail(CommandSender sender, String name, String sub) {
        SubCommandRegistry registry = SubCommandRegistry.getInstance();
        String canonical = registry.resolveName(name);
        String cmdName = canonical != null ? canonical : name;
        Cmd info = COMMANDS.get(cmdName);

        String title = "/ui " + cmdName + (sub != null ? " " + sub : "");
        sender.sendMessage(MessageUtil.parse("<dark_gray>┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓"));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃ <gold>✦ <white>" + title));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫"));

        if (sub != null && info != null) {
            // ── Subcommand detail ──
            Sub s = info.subs.stream()
                    .filter(x -> x.name.equalsIgnoreCase(sub))
                    .findFirst().orElse(null);
            if (s == null) {
                // Try the usage list
                Usage u = info.usages.stream()
                        .filter(x -> x.args != null && x.args.toLowerCase().startsWith(sub))
                        .findFirst().orElse(null);
                sender.sendMessage(MessageUtil.parse("<dark_gray>┃ <gray>Description: <white>"
                        + (info.desc != null ? info.desc : "Manage " + cmdName)));
                if (u != null) {
                    sendClickableUsage(sender, cmdName, u);
                } else {
                    sender.sendMessage(MessageUtil.parse("<dark_gray>┃ <red>Unknown subcommand: <white>"
                            + sub + "<red> — showing the command overview."));
                }
                sendUsageList(sender, cmdName, info);
                sender.sendMessage(MessageUtil.parse("<dark_gray>┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛"));
                return true;
            }

            sender.sendMessage(MessageUtil.parse("<dark_gray>┃ <gray>Description: <white>"
                    + (s.desc == null || s.desc.isEmpty() ? info.desc : s.desc)));
            Usage su = new Usage(s.name + (s.args.isEmpty() ? "" : " " + s.args), "");
            sendClickableUsage(sender, cmdName, su);
            // Suggest related subcommands
            sendUsageList(sender, cmdName, info);
            sender.sendMessage(MessageUtil.parse("<dark_gray>┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛"));
            return true;
        }

        // ── Command detail ──
        String desc = info != null && info.desc != null ? info.desc : "Manage " + cmdName;
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃ <gray>Description: <white>" + desc));

        if (info != null) {
            if (!info.usages.isEmpty()) {
                sender.sendMessage(MessageUtil.parse("<dark_gray>┃ <gray>Usage:"));
                for (Usage u : info.usages) {
                    sendClickableUsage(sender, cmdName, u);
                }
            }
            if (!info.subs.isEmpty()) {
                sender.sendMessage(MessageUtil.parse("<dark_gray>┃ <gray>Subcommands <dark_gray>(click for details)"));
                for (Sub s : info.subs) {
                    Component subPart = Component.text("┃   " + s.name
                                    + (s.args.isEmpty() ? "" : " " + s.args))
                            .color(NamedTextColor.WHITE)
                            .clickEvent(ClickEvent.runCommand("/ui help " + cmdName + " " + s.name))
                            .hoverEvent(HoverEvent.showText(MessageUtil.parse(
                                    "<gray>Click for details")));
                    sender.sendMessage(MessageUtil.parse("<dark_gray>")
                            .append(subPart)
                            .append(MessageUtil.parse(" <dark_gray>— <gray>" + s.desc)));
                }
            }
            if (info.usages.isEmpty() && info.subs.isEmpty()) {
                sender.sendMessage(MessageUtil.parse("<dark_gray>┃ <gray>Usage: <white>/ui " + cmdName));
            }
        } else {
            // Unknown to the database — generic card
            sender.sendMessage(MessageUtil.parse(
                    "<dark_gray>┃ <red>No detailed info recorded for this command."));
            sender.sendMessage(MessageUtil.parse(
                    "<dark_gray>┃ <gray>Try: <white>/ui " + cmdName + " <gray>— or ask the developer."));
        }

        // Permission + back link
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃ <gray>Permission: <white>ui.command."
                + cmdName + (hasAccess(sender, cmdName) ? " <green>(you have it)" : " <red>(missing)")));
        Component back = Component.text("┃ [< Back to the list]")
                .color(NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.runCommand("/ui help"))
                .hoverEvent(HoverEvent.showText(MessageUtil.parse("<gray>Back")));
        sender.sendMessage(MessageUtil.parse("<dark_gray>").append(back));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛"));
        return true;
    }

    /** One usage row; clicking suggests the command in the chat input. */
    private static void sendClickableUsage(CommandSender sender, String cmdName, Usage u) {
        String full = u.full(cmdName);
        Component usagePart = Component.text(full)
                .color(NamedTextColor.WHITE)
                .clickEvent(ClickEvent.suggestCommand(full))
                .hoverEvent(HoverEvent.showText(MessageUtil.parse(
                        "<gray>Click to put into chat input"
                                + (u.note == null || u.note.isEmpty() ? "" : "\\n<dark_gray>" + u.note))));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃   ")
                .append(usagePart)
                .append(u.note == null || u.note.isEmpty()
                        ? Component.empty()
                        : MessageUtil.parse(" <dark_gray>— <gray>" + u.note)));
    }

    /** Compact overview of all usage lines (used in subcommand detail). */
    private static void sendUsageList(CommandSender sender, String cmdName, Cmd info) {
        if (info.usages.isEmpty() && info.subs.isEmpty()) return;
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃ <gray>All usage:"));
        for (Usage u : info.usages) {
            sendClickableUsage(sender, cmdName, u);
        }
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

    // =========================================================================
    // TAB COMPLETE
    // =========================================================================

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            List<String> names = new ArrayList<>(COMMANDS.keySet());
            Collections.sort(names);
            return names;
        }
        if (args.length == 3) {
            Cmd info = COMMANDS.get(args[1].toLowerCase());
            if (info == null) return List.of();
            List<String> subs = new ArrayList<>();
            for (Sub s : info.subs) subs.add(s.name);
            for (Usage u : info.usages) {
                if (u.args != null && !u.args.isEmpty()) {
                    String first = u.args.split(" ")[0];
                    if (first.startsWith("<") || first.startsWith("[")) continue;
                    if (!subs.contains(first)) subs.add(first);
                }
            }
            Collections.sort(subs);
            return subs;
        }
        return List.of();
    }
}
