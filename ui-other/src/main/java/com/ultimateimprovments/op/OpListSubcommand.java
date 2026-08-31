package com.ultimateimprovments.op;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /ui oplist — paginated list of players in the OP list.
 * <p>
 * Usage:
 *   /ui oplist          — show page 1
 *   /ui oplist <page>   — show specific page
 */
public final class OpListSubcommand {

    private static final int PER_PAGE = 10;
    private static final String PERMISSION = "ui.command.oplist";

    private OpListSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            CommandErrors.noPermission(sender);
            return true;
        }

        int page = 1;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
                sender.sendMessage(MessageUtil.parse(
                        "<red>Usage: </red><white>/ui oplist <page></white>"
                ));
                return true;
            }
        }

        return showPage(sender, page);
    }

    private static boolean showPage(CommandSender sender, int requestedPage) {
        List<String> all = OpManager.getList();
        int totalEntries = all.size();
        int totalPages = Math.max(1, (totalEntries + PER_PAGE - 1) / PER_PAGE);
        int page = Math.max(1, Math.min(requestedPage, totalPages));

        int from = (page - 1) * PER_PAGE;
        int to = Math.min(from + PER_PAGE, totalEntries);
        List<String> pageEntries = all.subList(from, to);

        // Count online OPs
        int onlineOpCount = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isOp()) onlineOpCount++;
        }

        // ─── Header ───
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓</dark_gray>"
        ));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray> <gold>✦ <white>Operator List</white> <gray>— Page " + page + "/" + totalPages + "</gray>"
        ));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray> <gray>Total: </gray><white>" + totalEntries + "</white><gray> | Online OPs: </gray><gold>" + onlineOpCount + "</gold>"
        ));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫</dark_gray>"
        ));

        // ─── Entries ───
        if (pageEntries.isEmpty()) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_gray>┃</dark_gray> <gray>No players in the OP list.</gray>"
            ));
        } else {
            int idx = from + 1;
            for (String name : pageEntries) {
                Player online = Bukkit.getPlayerExact(name);
                boolean isOnline = online != null && online.isOnline();
                boolean isOp = isOnline && online.isOp();

                String statusDot = isOnline ? "<green>●</green>" : "<dark_gray>●</dark_gray>";
                String nameColor = isOnline ? "<white>" : "<gray>";
                String opTag = isOp ? " <gold>[OP]</gold>" : "";
                String onlineTag = isOnline ? "" : " <dark_gray>(offline)</dark_gray>";

                Component line = MessageUtil.parse(
                        "<dark_gray>┃</dark_gray> " + statusDot + " " + nameColor + "#" + idx + " " + name + "</gray>" + opTag + onlineTag
                );

                if (isOnline) {
                    line = line.hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT,
                            MessageUtil.parse("<white>" + name + "</white>\n<gray>Click to grant/revoke OP</gray>")));
                    line = line.clickEvent(ClickEvent.runCommand("/ui op " + name));
                }

                sender.sendMessage(line);
                idx++;
            }
        }

        // ─── Footer: page indicator + [<] / [>] ───
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫</dark_gray>"
        ));

        Component footer = MessageUtil.parse(
                "<dark_gray>┃  </dark_gray><gray>Page </gray><yellow>" + page + "</yellow><gray>/</gray><yellow>" + totalPages + "</yellow>   "
        );

        // [<] — previous page
        if (page > 1) {
            Component prev = Component.text("[<]")
                    .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW)
                    .clickEvent(ClickEvent.runCommand("/ui oplist " + (page - 1)))
                    .hoverEvent(HoverEvent.showText(
                            MessageUtil.parse("<gray>Previous page</gray>")));
            footer = footer.append(prev);
        } else {
            footer = footer.append(MessageUtil.parse("<dark_gray>[<]</dark_gray>"));
        }

        footer = footer.append(MessageUtil.parse("  "));

        // [>] — next page
        if (page < totalPages) {
            Component next = Component.text("[>]")
                    .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW)
                    .clickEvent(ClickEvent.runCommand("/ui oplist " + (page + 1)))
                    .hoverEvent(HoverEvent.showText(
                            MessageUtil.parse("<gray>Next page</gray>")));
            footer = footer.append(next);
        } else {
            footer = footer.append(MessageUtil.parse("<dark_gray>[>]</dark_gray>"));
        }

        sender.sendMessage(footer);
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛</dark_gray>"
        ));

        return true;
    }

    // ════════════════════════════════════════
    // TAB COMPLETION
    // ════════════════════════════════════════
    public static List<String> tabComplete(String[] args) {
        if (args.length == 2) {
            int total = OpManager.getCount();
            int totalPages = Math.max(1, (total + PER_PAGE - 1) / PER_PAGE);
            List<String> pages = new java.util.ArrayList<>();
            for (int i = 1; i <= totalPages; i++) {
                pages.add(String.valueOf(i));
            }
            String prefix = args[1].toLowerCase();
            return pages.stream()
                    .filter(s -> s.startsWith(prefix))
                    .collect(java.util.stream.Collectors.toList());
        }
        return List.of();
    }
}
