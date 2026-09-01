package com.ultimateimprovments.op;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /ui op — grant operator status.
 * <p>
 * Usage:
 *   /ui op <player>    — grant OP to a specific player (must be online, not in OP list)
 *   /ui op all         — grant OP to all online players not in the OP list
 *   /ui op confirm     — confirm a pending op action
 */
public final class OpSubcommand {

    private static final String PERMISSION = "ui.command.op";

    private OpSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            CommandErrors.noPermission(sender);
            return true;
        }

        // /ui op alone — usage
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>Usage: </red><white>/ui op <player|all|confirm></white>"
            ));
            return true;
        }

        String arg = args[1].toLowerCase();

        // ── /ui op confirm ──
        if (arg.equals("confirm")) {
            return executeConfirm(sender);
        }

        // ── /ui op all ──
        if (arg.equals("all")) {
            return showConfirmAll(sender);
        }

        // ── /ui op <player> ──
        return showConfirmSingle(sender, args[1]);
    }

    // ════════════════════════════════════════
    // SHOW CONFIRMATION — single player
    // ════════════════════════════════════════
    private static boolean showConfirmSingle(CommandSender sender, String targetName) {
        @SuppressWarnings("deprecation")
        Player target = Bukkit.getPlayerExact(targetName);

        if (target == null) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>Player </red><yellow>" + targetName + "</yellow><red> is not online.</red>"
            ));
            return true;
        }

        if (OpManager.isInList(target.getName())) {
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>" + target.getName() + "</yellow><red> is already in the OP list.</red>"
            ));
            return true;
        }

        if (target.isOp()) {
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>" + target.getName() + "</yellow><red> already has OP status.</red>"
            ));
            return true;
        }

        // Store pending
        OpManager.setPending(OpManager.getSenderId(sender), "op", target.getName());

        // Show confirmation dialog
        sendConfirmDialog(sender,
                "Grant OP",
                "<green>GRANT</green>",
                target.getName(),
                "/ui op confirm");
        return true;
    }

    // ════════════════════════════════════════
    // SHOW CONFIRMATION — all
    // ════════════════════════════════════════
    private static boolean showConfirmAll(CommandSender sender) {
        List<Player> eligible = getEligibleOnline();

        if (eligible.isEmpty()) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>No eligible players found. All online players are already in the OP list or have OP.</red>"
            ));
            return true;
        }

        // Store pending
        OpManager.setPending(OpManager.getSenderId(sender), "op", "all");

        // Show confirmation dialog
        String names = eligible.stream()
                .map(Player::getName)
                .collect(Collectors.joining("<gray>, </gray>"));

        sender.sendMessage(MessageUtil.parse(""));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓</dark_gray>"
        ));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray> <gold>⚡ Operator Status Change</gold>"
        ));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray>"
        ));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray> <gray>Action:</gray> <green>GRANT</green>"
        ));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray> <gray>Targets:</gray> <white>" + eligible.size() + " players</white>"
        ));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray> <gray>Players:</gray> <white>" + names + "</white>"
        ));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray>"
        ));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray> <gray>Are you sure you want to grant OP to all?</gray>"
        ));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray>"
        ));

        if (sender instanceof Player) {
            // Player: show clickable buttons
            Component confirmBtn = MessageUtil.parse(
                    "<dark_gray>┃     </dark_gray><dark_green>[</dark_green><green>✔ Confirm Grant</green><dark_green>]</dark_green>"
            ).clickEvent(ClickEvent.runCommand("/ui op confirm"))
             .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT,
                     MessageUtil.parse("<green>Click to confirm</green>\n<gray>Expires in 30 seconds</gray>")));
            sender.sendMessage(confirmBtn);

            Component cancelBtn = MessageUtil.parse(
                    "<dark_gray>┃     </dark_gray><dark_red>[</dark_red><red>✕ Cancel</red><dark_red>]</dark_red>"
            ).clickEvent(ClickEvent.runCommand("/ui oplist"))
             .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT,
                     MessageUtil.parse("<gray>Click to cancel</gray>")));
            sender.sendMessage(cancelBtn);

            sender.sendMessage(MessageUtil.parse(
                    "<dark_gray>┃</dark_gray>"
            ));
            sender.sendMessage(MessageUtil.parse(
                    "<dark_gray>┃</dark_gray> <gray>Click a button or type </gray><white>/ui op confirm</white><gray> to confirm.</gray>"
            ));
        } else {
            // Console / Command Block: text-only instructions
            sender.sendMessage(MessageUtil.parse(
                    "<dark_gray>┃</dark_gray> <gray>Type </gray><white>/ui op confirm</white><gray> to confirm.</gray>"
            ));
            sender.sendMessage(MessageUtil.parse(
                    "<dark_gray>┃</dark_gray> <gray>This action expires in </gray><white>30 seconds</white><gray>.</gray>"
            ));
        }

        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛</dark_gray>"
        ));
        sender.sendMessage(MessageUtil.parse(""));

        return true;
    }

    // ════════════════════════════════════════
    // EXECUTE CONFIRM
    // ════════════════════════════════════════
    private static boolean executeConfirm(CommandSender sender) {
        String senderId = OpManager.getSenderId(sender);
        OpManager.PendingAction pending = OpManager.consumePending(senderId, "op");

        if (pending == null) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>No pending /ui op action found.</red> <gray>Use </gray><white>/ui op <player|all></white><gray> first.</gray>"
            ));
            return true;
        }

        if ("all".equals(pending.target())) {
            return executeGrantAll(sender);
        }

        return executeGrantSingle(sender, pending.target());
    }

    private static boolean executeGrantSingle(CommandSender sender, String targetName) {
        @SuppressWarnings("deprecation")
        Player target = Bukkit.getPlayerExact(targetName);

        if (target == null) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>Player </red><yellow>" + targetName + "</yellow><red> is no longer online!</red>"
            ));
            return true;
        }

        if (target.isOp()) {
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>" + target.getName() + "</yellow><red> already has OP status.</red>"
            ));
            return true;
        }

        target.setOp(true);
        OpManager.add(target.getName(), sender.getName());

        sender.sendMessage(MessageUtil.parse(
                "<green>✔</green> <gold>Operator</gold> <white>status granted to</white> <yellow>" + target.getName() + "</yellow><white>.</white>"
        ));
        target.sendMessage(MessageUtil.parse(
                "<gold>⚡</gold> <white>You are now an</white> <gold>operator</gold><white>!</white>"
        ));
        ConsoleLogger.info("[OpManager] " + sender.getName() + " granted OP to " + target.getName());
        return true;
    }

    private static boolean executeGrantAll(CommandSender sender) {
        List<Player> eligible = getEligibleOnline();

        if (eligible.isEmpty()) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>No eligible players found. All online players already have OP or are in the list.</red>"
            ));
            return true;
        }

        int count = 0;
        for (Player target : eligible) {
            target.setOp(true);
            OpManager.add(target.getName(), sender.getName());
            target.sendMessage(MessageUtil.parse(
                    "<gold>⚡</gold> <white>You are now an</white> <gold>operator</gold><white>!</white>"
            ));
            count++;
        }

        sender.sendMessage(MessageUtil.parse(
                "<green>✔</green> <gold>Operator</gold> <white>status granted to </white><yellow>" + count + "</yellow><white> players.</white>"
        ));
        ConsoleLogger.info("[OpManager] " + sender.getName() + " granted OP to " + count + " players (all)");
        return true;
    }

    // ════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════
    private static List<Player> getEligibleOnline() {
        List<Player> result = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.isOp() && !OpManager.isInList(p.getName())) {
                result.add(p);
            }
        }
        return result;
    }

    // ════════════════════════════════════════
    // SHARED CONFIRMATION DIALOG
    // ════════════════════════════════════════
    static void sendConfirmDialog(CommandSender sender, String title, String actionTag, String targetName, String confirmCmd) {
        sender.sendMessage(MessageUtil.parse(""));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓</dark_gray>"
        ));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray> <gold>⚡ Operator Status Change</gold>"
        ));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray>"
        ));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray> <gray>Player:</gray> <white>" + targetName + "</white>"
        ));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray> <gray>Action:</gray> " + actionTag
        ));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray>"
        ));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray> <gray>Are you sure?</gray>"
        ));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>┃</dark_gray>"
        ));

        if (sender instanceof Player) {
            // Player: show clickable buttons
            Component confirmBtn = MessageUtil.parse(
                    "<dark_gray>┃     </dark_gray><dark_green>[</dark_green><green>✔ Confirm " + title + "</green><dark_green>]</dark_green>"
            ).clickEvent(ClickEvent.runCommand(confirmCmd))
             .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT,
                     MessageUtil.parse("<green>Click to confirm</green>\n<gray>Expires in 30 seconds</gray>")));
            sender.sendMessage(confirmBtn);

            Component cancelBtn = MessageUtil.parse(
                    "<dark_gray>┃     </dark_gray><dark_red>[</dark_red><red>✕ Cancel</red><dark_red>]</dark_red>"
            ).clickEvent(ClickEvent.runCommand("/ui oplist"))
             .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT,
                     MessageUtil.parse("<gray>Click to cancel</gray>")));
            sender.sendMessage(cancelBtn);

            sender.sendMessage(MessageUtil.parse(
                    "<dark_gray>┃</dark_gray>"
            ));
            sender.sendMessage(MessageUtil.parse(
                    "<dark_gray>┃</dark_gray> <gray>Click a button or type </gray><white>" + confirmCmd + "</white><gray> to confirm.</gray>"
            ));
        } else {
            // Console / Command Block: text-only instructions
            sender.sendMessage(MessageUtil.parse(
                    "<dark_gray>┃</dark_gray> <gray>Type </gray><white>" + confirmCmd + "</white><gray> to confirm.</gray>"
            ));
            sender.sendMessage(MessageUtil.parse(
                    "<dark_gray>┃</dark_gray> <gray>This action expires in </gray><white>30 seconds</white><gray>.</gray>"
            ));
        }

        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛</dark_gray>"
        ));
        sender.sendMessage(MessageUtil.parse(""));
    }

    // ════════════════════════════════════════
    // TAB COMPLETION
    // ════════════════════════════════════════
    public static List<String> tabComplete(String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            List<String> completions = new ArrayList<>();
            completions.add("all");
            completions.add("confirm");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.isOp() && !OpManager.isInList(p.getName())) {
                    completions.add(p.getName());
                }
            }
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
