package com.ultimateimprovments.command.subcommands;

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
 * /ui redstone — управление вечно заблокированными редстоун-чанками.
 * <p>
 *   /ui redstone list [страница]  — список заблокированных чанков (5 на страницу).
 *       Координаты центра чанка кликабельны (выполняют /tp @s).
 *       Кнопка «Разблокировать» кликабельна (выполняет /ui redstone unlock <номер>).
 *       Кнопки [<] и [>] листают страницы.
 *   /ui redstone unlock <номер>   — разблокировать чанк.
 * <p>
 * Право: ui.command.redstone.list
 */
public final class RedstoneSubcommand {

    private static final String PERMISSION = "ui.command.redstone.list";
    private static final int PER_PAGE = 5;

    private RedstoneSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ You don't have permission to manage redstone chunks!</red>"
            ));
            return true;
        }

        RedstoneGuard guard = RedstoneGuard.getInstance();
        if (guard == null) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Redstone guard is not initialized!</red>"
            ));
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
                        // невалидная страница — покажем первую
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
                "<dark_gray>┃</dark_gray> <red>🔴 Blocked Redstone Chunks</red> <gray>(<white>" + chunks.size() + "</white>)</gray>"
        ));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray>"
        ));

        if (chunks.isEmpty()) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_gray>┃</dark_gray>   <dark_gray>(empty — no blocked chunks)</dark_gray>"
            ));
        } else {
            int start = (page - 1) * PER_PAGE;
            int end = Math.min(start + PER_PAGE, chunks.size());

            for (int i = start; i < end; i++) {
                BlockedChunk bc = chunks.get(i);
                ChunkKey key = bc.key();

                // Координаты центра чанка — кликабельны (/tp @s)
                Component coords = MessageUtil.parse(
                        "<aqua>" + key.centerX() + "</aqua><gray>,</gray> <aqua>" + key.centerZ() + "</aqua>"
                ).clickEvent(ClickEvent.runCommand("/tp @s " + key.centerX() + " " + safeY(key) + " " + key.centerZ()))
                 .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT,
                         MessageUtil.parse("<green>Click to teleport</green>\n<gray>/tp @s " + key.centerX() + " " + safeY(key) + " " + key.centerZ() + "</gray>")));

                // Кнопка разблокировки — кликабельна
                Component unlockBtn = MessageUtil.parse(
                        "<dark_green>[</dark_green><green>✔ Unlock</green><dark_green>]</dark_green>"
                ).clickEvent(ClickEvent.runCommand("/ui redstone unlock " + bc.number()))
                 .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT,
                         MessageUtil.parse("<green>Click to unlock chunk #" + bc.number() + "</green>")));

                sender.sendMessage(
                        MessageUtil.parse("<dark_gray>┃</dark_gray> <yellow>#" + bc.number() + "</yellow> <gray>" + key.world() + "</gray> ")
                                .append(coords)
                                .append(MessageUtil.parse("  "))
                                .append(unlockBtn)
                );
            }
        }

        // Пагинация: [<] страница [>]
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray>"
        ));

        Component prevBtn = page > 1
                ? MessageUtil.parse("<white>[</white><yellow>◀</yellow><white>]</white>")
                        .clickEvent(ClickEvent.runCommand("/ui redstone list " + (page - 1)))
                        .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT,
                                MessageUtil.parse("<gray>Previous page</gray>")))
                : MessageUtil.parse("<dark_gray>[◀]</dark_gray>");

        Component nextBtn = page < totalPages
                ? MessageUtil.parse("<white>[</white><yellow>▶</yellow><white>]</white>")
                        .clickEvent(ClickEvent.runCommand("/ui redstone list " + (page + 1)))
                        .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT,
                                MessageUtil.parse("<gray>Next page</gray>")))
                : MessageUtil.parse("<dark_gray>[▶]</dark_gray>");

        Component pageInfo = MessageUtil.parse(
                "<dark_gray>┃</dark_gray> <gray>Page</gray> <white>" + page + "/" + totalPages + "</white> <dark_gray>|</dark_gray> <gray>/ui redstone list [страница]</gray>"
        );

        sender.sendMessage(prevBtn.append(pageInfo).append(nextBtn));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"
        ));
        sender.sendMessage("");
    }

    /**
     * Безопасная Y-координата для телепортации: высшая точка мира над центром чанка,
     * fallback на 320 если мир не найден.
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
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/ui redstone unlock <номер чанка></white>"
            ));
            return true;
        }

        int number;
        try {
            number = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Invalid chunk number: </red><yellow>" + args[2] + "</yellow>"
            ));
            return true;
        }

        if (guard.unlock(number)) {
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Chunk</white> <yellow>#" + number + "</yellow> <white>unlocked.</white>"
            ));
            ConsoleLogger.info("[RedstoneGuard] Chunk #" + number + " unlocked by " + sender.getName());
        } else {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Chunk</red> <yellow>#" + number + "</yellow> <red>is not blocked!</red>"
            ));
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
