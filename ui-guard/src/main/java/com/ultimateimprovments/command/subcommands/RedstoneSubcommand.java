package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;

import com.ultimateimprovments.server.RedstoneGuard;
import com.ultimateimprovments.server.RedstoneGuard.BlockedChunk;
import com.ultimateimprovments.server.RedstoneGuard.ChunkKey;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /ui redstone — manage permanently redstone-blocked chunks.
 * <p>
 *   /ui redstone list [page]  — list of blocked chunks (5 per page).
 *       Chunk center coordinates are clickable (run /tp @s).
 *       The "Unlock" button is clickable (runs /ui redstone unlock <number>).
 *       The [<] and [>] buttons flip pages.
 *   /ui redstone unlock <number>   — unblock a chunk.
 * <p>
 * Permission: ui.command.redstone.list
 */
public final class RedstoneSubcommand {

    private static final String PERMISSION = "ui.command.redstone.list";
    private static final int PER_PAGE = 5;

    private RedstoneSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            CommandErrors.noPermission(sender, PERMISSION);
            return true;
        }

        RedstoneGuard guard = RedstoneGuard.getInstance();
        if (guard == null) {
            sender.sendMessage(MessageUtil.parse(MessageUtil.raw(
                    "redstone_guard.not_initialized",
                    "<red>❌ Redstone guard is not initialized!</red>")));
            return true;
        }

        if (args.length < 2) {
            showList(sender, guard, 1);
            return true;
        }

        String action = args[1].toLowerCase();

        return switch (action) {
            case "list" -> {
                int page = 1;
                if (args.length >= 3) {
                    try {
                        page = Math.max(1, Integer.parseInt(args[2]));
                    } catch (NumberFormatException ignored) {
                        // Invalid page — show the first one
                    }
                }
                showList(sender, guard, page);
                yield true;
            }
            case "unlock" -> unlockChunk(sender, guard, args);
            default -> {
                showList(sender, guard, 1);
                yield true;
            }
        };
    }

    // =========================
    // LIST
    // =========================

    private static void showList(CommandSender sender, RedstoneGuard guard, int page) {
        List<BlockedChunk> chunks = guard.getBlockedChunks();

        int totalPages = Math.max(1, (int) Math.ceil(chunks.size() / (double) PER_PAGE));
        page = Math.min(page, totalPages);

        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"
        ));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray> " + MessageUtil.raw(
                        "redstone_guard.list_header",
                        "<red>🔴 Blocked Redstone Chunks</red> <gray>(<white>%count%</white>)</gray>")
                        .replace("%count%", String.valueOf(chunks.size()))
        ));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray>"
        ));

        if (chunks.isEmpty()) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_gray>┃</dark_gray>   " + MessageUtil.raw(
                            "redstone_guard.list_empty",
                            "<dark_gray>(empty — no blocked chunks)</dark_gray>")
            ));
        } else {
            int start = (page - 1) * PER_PAGE;
            int end = Math.min(start + PER_PAGE, chunks.size());

            for (int i = start; i < end; i++) {
                BlockedChunk bc = chunks.get(i);
                ChunkKey key = bc.key();

                // Chunk center coordinates — clickable (/tp @s)
                String tpCommand = "/tp @s " + key.centerX() + " " + safeY(key) + " " + key.centerZ();
                Component coords = MessageUtil.parse(
                        "<aqua>" + key.centerX() + "</aqua><gray>,</gray> <aqua>" + key.centerZ() + "</aqua>"
                ).clickEvent(ClickEvent.runCommand(tpCommand))
                 .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT,
                         MessageUtil.parse(MessageUtil.raw(
                                 "redstone_guard.tp_hover",
                                 "<green>Click to teleport</green>\n<gray>%command%</gray>")
                                 .replace("%command%", tpCommand))));

                // Unlock button — clickable
                Component unlockBtn = MessageUtil.parse(MessageUtil.raw(
                        "redstone_guard.unlock_button",
                        "<dark_green>[</dark_green><green>✔ Unlock</green><dark_green>]</dark_green>")
                ).clickEvent(ClickEvent.runCommand("/ui redstone unlock " + bc.number()))
                 .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT,
                         MessageUtil.parse(MessageUtil.raw(
                                 "redstone_guard.unlock_hover",
                                 "<green>Click to unlock chunk #%number%</green>")
                                 .replace("%number%", String.valueOf(bc.number())))));

                sender.sendMessage(
                        MessageUtil.parse("<dark_gray>┃</dark_gray> <yellow>#" + bc.number() + "</yellow> <gray>" + key.world() + "</gray> ")
                                .append(coords)
                                .append(MessageUtil.parse("  "))
                                .append(unlockBtn)
                );
            }
        }

        // Pagination: [<] page [>]
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray>"
        ));

        Component prevBtn = page > 1
                ? MessageUtil.parse("<white>[</white><yellow>◀</yellow><white>]</white>")
                        .clickEvent(ClickEvent.runCommand("/ui redstone list " + (page - 1)))
                        .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT,
                                MessageUtil.parse(MessageUtil.raw(
                                        "redstone_guard.page_prev_hover",
                                        "<gray>Previous page</gray>"))))
                : MessageUtil.parse("<dark_gray>[◀]</dark_gray>");

        Component nextBtn = page < totalPages
                ? MessageUtil.parse("<white>[</white><yellow>▶</yellow><white>]</white>")
                        .clickEvent(ClickEvent.runCommand("/ui redstone list " + (page + 1)))
                        .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT,
                                MessageUtil.parse(MessageUtil.raw(
                                        "redstone_guard.page_next_hover",
                                        "<gray>Next page</gray>"))))
                : MessageUtil.parse("<dark_gray>[▶]</dark_gray>");

        Component pageInfo = MessageUtil.parse(
                "<dark_gray>┃</dark_gray> " + MessageUtil.raw(
                        "redstone_guard.page_info",
                        "<gray>Page</gray> <white>%page%/%total%</white> <dark_gray>|</dark_gray> <gray>/ui redstone list [page]</gray>")
                        .replace("%page%", String.valueOf(page))
                        .replace("%total%", String.valueOf(totalPages))
        );

        sender.sendMessage(prevBtn.append(pageInfo).append(nextBtn));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"
        ));
        sender.sendMessage("");
    }

    /**
     * Safe Y coordinate for teleportation: the highest world point above the chunk center,
     * falls back to 320 if the world is not found.
     */
    private static int safeY(ChunkKey key) {
        World world = Bukkit.getWorld(key.world());
        if (world != null) {
            try {
                return world.getHighestBlockYAt(key.centerX(), key.centerZ()) + 1;
            } catch (Exception ignored) {
                // fall through
            }
        }
        return 320;
    }

    // =========================
    // UNLOCK
    // =========================

    private static boolean unlockChunk(CommandSender sender, RedstoneGuard guard, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(MessageUtil.raw(
                    "redstone_guard.usage_unlock",
                    "<red>❌ Usage: </red><white>/ui redstone unlock <number></white>")));
            return true;
        }

        int number;
        try {
            number = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtil.parse(MessageUtil.raw(
                    "redstone_guard.invalid_number",
                    "<red>❌ Invalid chunk number: </red><yellow>%value%</yellow>")
                    .replace("%value%", args[2])));
            return true;
        }

        if (guard.unlock(number)) {
            sender.sendMessage(MessageUtil.parse(MessageUtil.raw(
                    "redstone_guard.unlock_success",
                    "<green>✔</green> <white>Chunk</white> <yellow>#%number%</yellow> <white>unlocked.</white>")
                    .replace("%number%", String.valueOf(number))));
            ConsoleLogger.info("[RedstoneGuard] Chunk #" + number + " unlocked by " + sender.getName());
        } else {
            sender.sendMessage(MessageUtil.parse(MessageUtil.raw(
                    "redstone_guard.not_blocked",
                    "<red>❌ Chunk</red> <yellow>#%number%</yellow> <red>is not blocked!</red>")
                    .replace("%number%", String.valueOf(number))));
        }
        return true;
    }

    // =========================
    // TAB COMPLETION
    // =========================

    public static List<String> tabComplete(String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            for (String action : List.of("list", "unlock")) {
                if (action.startsWith(prefix)) completions.add(action);
            }
        } else if (args.length == 3 && args[1].equalsIgnoreCase("unlock")) {
            RedstoneGuard guard = RedstoneGuard.getInstance();
            if (guard != null) {
                for (BlockedChunk bc : guard.getBlockedChunks()) {
                    completions.add(String.valueOf(bc.number()));
                }
            }
        }

        String last = args[args.length - 1].toLowerCase();
        return completions.stream().filter(s -> s.toLowerCase().startsWith(last)).collect(Collectors.toList());
    }
}
