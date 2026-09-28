package com.ultimateimprovments.mechanics.features.world;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks <b>active command blocks</b> — command blocks that have executed
 * their command within the last {@link #ACTIVE_WINDOW_MS} (1 minute).
 * <p>
 * {@code /ui cmdblocklist} shows them as a paged chat list (like {@code /ui help}):
 * each entry is numbered ({@code #1}, {@code #2}, ...) with the world and
 * coordinates, sorted by most recent execution. The page footer has clickable
 * {@code [<]} / {@code [>]} buttons.
 */
public final class CmdBlockTracker implements Listener {

    /** A command block counts as "active" if it executed within this window. */
    private static final long ACTIVE_WINDOW_MS = 60_000L;

    /** Permission required to use /ui cmdblocklist. */
    public static final String PERMISSION = "ui.command.cmdblocklist";

    /** Command blocks per page. */
    private static final int PER_PAGE = 8;

    /** Block key (world|x|y|z) → last execution time (epoch ms). */
    private static final Map<String, Long> LAST_EXEC = new ConcurrentHashMap<>();

    private CmdBlockTracker() {}

    // =========================
    // TRACKING
    // =========================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerCommand(ServerCommandEvent e) {
        if (!(e.getSender() instanceof BlockCommandSender sender)) return;
        LAST_EXEC.put(key(sender.getBlock().getWorld().getName(),
                        sender.getBlock().getX(),
                        sender.getBlock().getY(),
                        sender.getBlock().getZ()),
                System.currentTimeMillis());
    }

    private static String key(String world, int x, int y, int z) {
        return world + "|" + x + "|" + y + "|" + z;
    }

    // =========================
    // ACTIVE LIST
    // =========================

    /** Active command block entry. */
    public record ActiveCmdBlock(String world, int x, int y, int z, long lastExecMs) {}

    /** Returns active command blocks (executed within the window), most recent first. */
    public static List<ActiveCmdBlock> getActive() {
        long now = System.currentTimeMillis();

        // Lazy cleanup of stale entries
        LAST_EXEC.entrySet().removeIf(e -> now - e.getValue() > ACTIVE_WINDOW_MS);

        List<ActiveCmdBlock> result = new ArrayList<>();
        for (Map.Entry<String, Long> e : LAST_EXEC.entrySet()) {
            String[] parts = e.getKey().split("\\|");
            if (parts.length != 4) continue;
            result.add(new ActiveCmdBlock(
                    parts[0],
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]),
                    e.getValue()));
        }
        result.sort(Comparator.comparingLong(ActiveCmdBlock::lastExecMs).reversed());
        return result;
    }

    // =========================
    // COMMAND DISPLAY (like /ui help pages)
    // =========================

    /**
     * Executes {@code /ui cmdblocklist [page]}. Checks the permission and shows
     * the page, or the unified "no permission" message via {@link CommandErrors}.
     *
     * @return true if handled
     */
    public static boolean execute(CommandSender sender, String[] args) {
        if (sender instanceof Player p && !p.hasPermission(PERMISSION)) {
            CommandErrors.noPermission(p);
            return true;
        }

        int page = 1;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
                sender.sendMessage(MessageUtil.parse("<yellow>Usage: /ui cmdblocklist <page>"));
            }
        }
        return showPage(sender, page);
    }

    private static boolean showPage(CommandSender sender, int requestedPage) {
        List<ActiveCmdBlock> all = getActive();

        int totalPages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        int page = Math.max(1, Math.min(requestedPage, totalPages));

        int from = (page - 1) * PER_PAGE;
        int to = Math.min(from + PER_PAGE, all.size());
        List<ActiveCmdBlock> pageBlocks = all.subList(from, to);

        // ─── Header ───
        sender.sendMessage(MessageUtil.parse("<dark_gray>┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓"));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃  <gold>✦ <white>Active Command Blocks <gray>— <dark_gray>(" + page + "/" + totalPages + ")"));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫"));

        // ─── Rows: #N  world  X Y Z ───
        if (pageBlocks.isEmpty()) {
            sender.sendMessage(MessageUtil.parse("<dark_gray>┃  <gray>No active command blocks right now."));
        } else {
            int base = (page - 1) * PER_PAGE;
            for (int i = 0; i < pageBlocks.size(); i++) {
                ActiveCmdBlock b = pageBlocks.get(i);
                long agoSec = Math.max(0, (System.currentTimeMillis() - b.lastExecMs()) / 1000);
                sender.sendMessage(MessageUtil.parse("<dark_gray>┃  <yellow>#" + (base + i + 1) + " <gray>— <white>" + b.world()
                        + " <gray>(<white>" + b.x() + "<gray>, <white>" + b.y() + "<gray>, <white>" + b.z()
                        + "<gray>) <dark_gray>" + agoSec + "s ago"));
            }
        }

        // ─── Footer: page indicator + [<] / [>] ───
        sender.sendMessage(MessageUtil.parse("<dark_gray>┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫"));

        net.kyori.adventure.text.Component footer = MessageUtil.parse(
                "<dark_gray>┃  <gray>Page <yellow>" + page + "<gray>/" + totalPages + "   ");

        if (page > 1) {
            net.kyori.adventure.text.Component prev = net.kyori.adventure.text.Component.text("[<]")
                    .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW)
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(
                            "/ui cmdblocklist " + (page - 1)))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                            MessageUtil.parse("<gray>Previous page")));
            footer = footer.append(prev);
        } else {
            footer = footer.append(MessageUtil.parse("<dark_gray>[<]"));
        }

        footer = footer.append(MessageUtil.parse("  "));

        if (page < totalPages) {
            net.kyori.adventure.text.Component next = net.kyori.adventure.text.Component.text("[>]")
                    .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW)
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(
                            "/ui cmdblocklist " + (page + 1)))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                            MessageUtil.parse("<gray>Next page")));
            footer = footer.append(next);
        } else {
            footer = footer.append(MessageUtil.parse("<dark_gray>[>]"));
        }

        sender.sendMessage(footer);
        sender.sendMessage(MessageUtil.parse("<dark_gray>┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛"));
        return true;
    }

    // =========================
    // REGISTRATION
    // =========================

    public static void register(Main plugin) {
        plugin.getServer().getPluginManager().registerEvents(new CmdBlockTracker(), plugin);
        ConsoleLogger.info("[CmdBlockTracker] Registered (tracks active command blocks).");
    }
}
