package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.ConsoleLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 🛡 ChgOpSubcommand — handler for /ui chgop.
 * <p>
 * Shows the list of online players with their OP status.
 * Each player is clickable — OP can be granted or revoked with confirmation.
 * <p>
 * Usage:
 *   /ui chgop                  — show the player list
 *   /ui chgop toggle <player>  — show confirmation for changing OP
 *   /ui chgop confirm <player> — confirm and perform the OP change
 */
public final class ChgOpSubcommand {

    // Players awaiting confirmation for an OP change (player → target)
    private static final Map<UUID, String> pendingConfirmations = new HashMap<>();

    private ChgOpSubcommand() {}

    // ════════════════════════════════════════
    // EXECUTE
    // ════════════════════════════════════════
    public static boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ui.command.chgop")) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ You don't have permission to manage operator status!</red>"
            ));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Only players can use this command!</red>"
            ));
            return true;
        }

        // /ui chgop → show the list
        if (args.length < 2) {
            showPlayerList(player);
            return true;
        }

        String action = args[1].toLowerCase();

        return switch (action) {
            case "toggle" -> showConfirmation(player, args);
            case "confirm" -> executeToggle(player, args);
            default -> {
                showPlayerList(player);
                yield true;
            }
        };
    }

    // ════════════════════════════════════════
    // SHOW PLAYER LIST (clickable names)
    // ════════════════════════════════════════
    private static void showPlayerList(Player player) {
        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        int opCount = 0;

        // Sort: OP first, then regular, alphabetically within each group
        List<Player> sorted = onlinePlayers.stream()
                .sorted(Comparator.comparing(Player::isOp).reversed()
                        .thenComparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        for (Player p : sorted) {
            if (p.isOp()) opCount++;
        }

        // Top border
        player.sendMessage(MessageUtil.parse(
                "<dark_gray>┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"
        ));
        player.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray> <white>⚡ Operator Management</white>"
        ));
        player.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray> <gray>Online: <white>" + onlinePlayers.size() +
                "</white> | OP: <gold>" + opCount + "</gold></gray>"
        ));
        player.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray>"
        ));

        // Each player — a clickable row
        for (Player target : sorted) {
            boolean isSelf = target.equals(player);
            String dot;
            String nameOpen;
            String nameClose;
            String opTag;
            String youTag;

            if (isSelf) {
                // Own nick — bright cyan with the ★ YOU tag
                dot = target.isOp() ? "<aqua>●</aqua>" : "<dark_aqua>●</dark_aqua>";
                nameOpen = "<aqua><bold>";
                nameClose = "</bold></aqua>";
                opTag = target.isOp() ? " <gold>[OP]</gold>" : "";
                youTag = " <dark_aqua>★ YOU</dark_aqua>";
            } else {
                dot = target.isOp() ? "<gold>●</gold>" : "<dark_gray>●</dark_gray>";
                nameOpen = target.isOp() ? "<white>" : "<gray>";
                nameClose = "</" + nameOpen.substring(1);
                opTag = target.isOp() ? " <gold>[OP]</gold>" : "";
                youTag = "";
            }

            String mmString = "  <dark_gray>┃</dark_gray> " + dot + " " + nameOpen + target.getName() + nameClose + opTag + youTag;

            Component line = MessageUtil.parse(mmString)
                    .clickEvent(ClickEvent.runCommand("/ui chgop toggle " + target.getName()));

            Component hover;
            if (isSelf) {
                hover = MessageUtil.parse(target.isOp()
                        ? "<aqua>● You are OP</aqua>\n<gray>You cannot remove OP from yourself</gray>"
                        : "<dark_aqua>● You are not OP</dark_aqua>\n<gray>Click to <green>grant</green> OP to yourself</gray>");
            } else {
                hover = MessageUtil.parse(target.isOp()
                        ? "<yellow>● " + target.getName() + " <yellow>is OP</yellow>\n<gray>Click to <red>remove</red> <gray>OP rights</gray>"
                        : "<gray>● " + target.getName() + " <gray>is not OP</gray>\n<gray>Click to <green>grant</green> <gray>OP rights</gray>");
            }
            line = line.hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT, hover));

            player.sendMessage(line);
        }

        // Bottom border with a hint
        player.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray>"
        ));
        player.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray> <gray>Click a player to toggle their OP status</gray>"
        ));
        player.sendMessage(MessageUtil.parse(
                "<dark_gray>┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"
        ));
    }

    // ════════════════════════════════════════
    // SHOW CONFIRMATION
    // ════════════════════════════════════════
    private static boolean showConfirmation(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/ui chgop toggle <player></white>"
            ));
            return true;
        }

        String targetName = args[2];
        @SuppressWarnings("deprecation")
        Player target = Bukkit.getPlayerExact(targetName);

        if (target == null) {
            player.sendMessage(MessageUtil.parse(
                    "<red>❌ Player</red> <yellow>" + targetName + "</yellow> <red>not found or not online!</red>"
            ));
            return true;
        }

        // 🚫 Failsafe: you cannot remove OP from yourself
        if (target.equals(player) && target.isOp()) {
            player.sendMessage(MessageUtil.parse(
                    "<red>❌ You cannot remove operator status from yourself!</red>"
            ));
            return true;
        }

        boolean currentlyOp = target.isOp();
        String actionRu = currentlyOp ? "снять" : "выдать";

        // Save the confirmation
        pendingConfirmations.put(player.getUniqueId(), target.getName());

        // Show the confirmation dialog
        player.sendMessage("");
        player.sendMessage(MessageUtil.parse(
                "<dark_gray>┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓</dark_gray>"
        ));
        player.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray> <gold>⚡ Operator Status Change</gold>"
        ));
        player.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray>"
        ));
        player.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray> <gray>Player:</gray> <white>" + target.getName() + "</white>"
        ));
        player.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray> <gray>Current:</gray> " + (currentlyOp ? "<yellow>[OP]</yellow>" : "<gray>[NOT OP]</gray>")
        ));
        player.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray> <gray>Action:</gray> " + (currentlyOp ? "<red>REVOKE</red>" : "<green>GRANT</green>")
        ));
        player.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray>"
        ));
        player.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray> <gray>Are you sure you want to " + actionRu + " OP</gray>"
        ));
        player.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray> <gray>for</gray> <white>" + target.getName() + "</white><gray>?</gray>"
        ));
        player.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray>"
        ));

        // Confirm button — clickable
        Component confirmBtn = MessageUtil.parse(
                "<dark_gray>┃     <dark_green>[</dark_green><green>✔ Confirm " + (currentlyOp ? "Revoke" : "Grant") + "</green><dark_green>]</dark_green>"
        ).clickEvent(ClickEvent.runCommand("/ui chgop confirm " + target.getName()))
         .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT,
                 MessageUtil.parse("<green>Click to confirm</green>\n<red>This action cannot be undone!</red>")));
        player.sendMessage(confirmBtn);

        // Cancel button — clickable
        Component cancelBtn = MessageUtil.parse(
                "<dark_gray>┃     <dark_red>[</dark_red><red>✕ Cancel</red><dark_red>]</dark_red>"
        ).clickEvent(ClickEvent.runCommand("/ui chgop"))
         .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT,
                 MessageUtil.parse("<gray>Click to cancel and return to player list</gray>")));
        player.sendMessage(cancelBtn);

        player.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray>"
        ));
        player.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray> <gray>Or type again:</gray> <white>/ui chgop toggle " + target.getName() + "</white>"
        ));
        player.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray> <gray>to confirm and " + actionRu + " OP rights.</gray>"
        ));
        player.sendMessage(MessageUtil.parse(
                "<dark_gray>┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛</dark_gray>"
        ));
        player.sendMessage("");

        return true;
    }

    // ════════════════════════════════════════
    // EXECUTE TOGGLE
    // ════════════════════════════════════════
    private static boolean executeToggle(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(MessageUtil.parse(
                    "<red>❌ Invalid confirmation. Use </red><white>/ui chgop</white><red> to see the player list.</red>"
            ));
            return true;
        }

        String targetName = args[2];
        UUID uuid = player.getUniqueId();

        // Check whether there is a pending confirmation
        String pending = pendingConfirmations.get(uuid);
        if (pending == null || !pending.equalsIgnoreCase(targetName)) {
            player.sendMessage(MessageUtil.parse(
                    "<red>❌ No pending confirmation for</red> <yellow>" + targetName + "</yellow><red>.</red> " +
                            "<gray>Use </gray><white>/ui chgop</white><gray> first.</gray>"
            ));
            return true;
        }

        // Remove the confirmation
        pendingConfirmations.remove(uuid);

        @SuppressWarnings("deprecation")
        Player target = Bukkit.getPlayerExact(targetName);

        if (target == null) {
            player.sendMessage(MessageUtil.parse(
                    "<red>❌ Player</red> <yellow>" + targetName + "</yellow> <red>is no longer online!</red>"
            ));
            return true;
        }

        // 🚫 Failsafe: double protection against removing your own OP
        if (target.equals(player) && target.isOp()) {
            player.sendMessage(MessageUtil.parse(
                    "<red>❌ You cannot remove operator status from yourself!</red>"
            ));
            return true;
        }

        // Perform the OP change
        boolean newOp = !target.isOp();
        target.setOp(newOp);

        if (newOp) {
            // OP granted
            player.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <gold>Operator</gold> <white>status granted to</white> <yellow>" + target.getName() + "</yellow><white>.</white>"
            ));
            target.sendMessage(MessageUtil.parse(
                    "<gold>⚡</gold> <white>You are now an</white> <gold>operator</gold><white>!</white>"
            ));
            ConsoleLogger.info("[OpManager] " + player.getName() + " granted OP to " + target.getName());
        } else {
            // OP revoked
            player.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <gold>Operator</gold> <white>status revoked from</white> <yellow>" + target.getName() + "</yellow><white>.</white>"
            ));
            target.sendMessage(MessageUtil.parse(
                    "<red>⛔</red> <white>Your</white> <gold>operator</gold> <white>status has been revoked.</white>"
            ));
            ConsoleLogger.info("[OpManager] " + player.getName() + " revoked OP from " + target.getName());
        }

        return true;
    }

    // ════════════════════════════════════════
    // TAB COMPLETION
    // ════════════════════════════════════════
    public static List<String> tabComplete(String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            for (String action : List.of("toggle")) {
                if (action.startsWith(prefix)) {
                    completions.add(action);
                }
            }
        } else if (args.length == 3 && args[1].equalsIgnoreCase("toggle")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                completions.add(p.getName());
            }
        }

        String last = args[args.length - 1].toLowerCase();
        return completions.stream().filter(s -> s.toLowerCase().startsWith(last)).collect(Collectors.toList());
    }
}
