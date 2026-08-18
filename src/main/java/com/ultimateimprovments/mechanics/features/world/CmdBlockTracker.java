package com.ultimateimprovments.mechanics.features.world;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
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
                sender.sendMessage("§eUsage: /ui cmdblocklist <page>");
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
        sender.sendMessage("§8┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓");
        sender.sendMessage("§8┃  §6✦ §fActive Command Blocks §7— §8(" + page + "/" + totalPages + ")");
        sender.sendMessage("§8┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫");

        // ─── Rows: #N  world  X Y Z ───
        if (pageBlocks.isEmpty()) {
            sender.sendMessage("§8┃  §7No active command blocks right now.");
        } else {
            int base = (page - 1) * PER_PAGE;
            for (int i = 0; i < pageBlocks.size(); i++) {
                ActiveCmdBlock b = pageBlocks.get(i);
                long agoSec = Math.max(0, (System.currentTimeMillis() - b.lastExecMs()) / 1000);
                sender.sendMessage("§8┃  §e#" + (base + i + 1) + " §7— §f" + b.world()
                        + " §7(§f" + b.x() + "§7, §f" + b.y() + "§7, §f" + b.z()
                        + "§7) §8" + agoSec + "s ago");
            }
        }

        // ─── Footer: page indicator + [<] / [>] ───
        sender.sendMessage("§8┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫");

        net.md_5.bungee.api.chat.TextComponent footer =
                new net.md_5.bungee.api.chat.TextComponent("§8┃  §7Page §e" + page + "§7/" + totalPages + "   ");

        if (page > 1) {
            net.md_5.bungee.api.chat.TextComponent prev =
                    new net.md_5.bungee.api.chat.TextComponent("§e[<]");
            prev.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                    "/ui cmdblocklist " + (page - 1)));
            prev.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.ComponentBuilder("§7Previous page").create()));
            footer.addExtra(prev);
        } else {
            footer.addExtra(new net.md_5.bungee.api.chat.TextComponent("§8[<]"));
        }

        footer.addExtra(new net.md_5.bungee.api.chat.TextComponent("  "));

        if (page < totalPages) {
            net.md_5.bungee.api.chat.TextComponent next =
                    new net.md_5.bungee.api.chat.TextComponent("§e[>]");
            next.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                    "/ui cmdblocklist " + (page + 1)));
            next.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.ComponentBuilder("§7Next page").create()));
            footer.addExtra(next);
        } else {
            footer.addExtra(new net.md_5.bungee.api.chat.TextComponent("§8[>]"));
        }

        sender.spigot().sendMessage(footer);
        sender.sendMessage("§8┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛");
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
