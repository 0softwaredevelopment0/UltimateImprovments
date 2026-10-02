package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.command.SubCommand;
import com.ultimateimprovments.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /ui clear — unified cleanup command (replaces the old /ui clearchat).
 * <pre>
 *   /ui clear chat <player|all>               — clears the chat window
 *   /ui clear attributes <entity|UUID|all>    — resets entity attributes to base values
 * </pre>
 * <b>chat</b> sends 200 empty lines to push the chat history out of view
 * (same behavior as the removed {@code /ui clearchat}).
 * <p>
 * <b>attributes</b> resets every attribute of the target back to its DEFAULT
 * (base) value by calling {@link AttributeInstance#setBaseValue(Object)} with
 * {@link Attribute#getValue()}... — precisely: {@code setBaseValue(attr.getDefaultValue())}
 * and removes all non-vanilla {@link org.bukkit.attribute.AttributeModifier}s
 * added at runtime, so any inflated values (speed, damage, health, ...) return
 * to vanilla defaults. Targets: a player nickname, an Entity UUID (any living
 * entity), or {@code all} (every online player).
 * <p>
 * Permissions: {@code ui.command.clear.chat} and {@code ui.command.clear.attributes}
 * (registered in {@code Permissions} — in code, not plugin.yml).
 */
public final class ClearSubcommand implements SubCommand {

    private static final String PERM_CHAT = "ui.command.clear.chat";
    private static final String PERM_ATTRIBUTES = "ui.command.clear.attributes";

    /** How many empty lines are sent to scroll the chat history out of view. */
    private static final int EMPTY_LINES = 200;

    @Override
    public String getName() {
        return "clear";
    }

    @Override
    public List<String> getAliases() {
        return List.of("clearchat"); // backward-compatibility alias maps to the chat branch
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // /ui clear <chat|attributes> ...
        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        switch (args[1].toLowerCase()) {
            case "chat" -> {
                return clearChat(sender, args);
            }
            case "attributes" -> {
                return clearAttributes(sender, args);
            }
            default -> {
                sendUsage(sender);
                return true;
            }
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(MessageUtil.parse(
                "<yellow>Usage:</yellow>\n"
                + "<white>/ui clear chat <player|all></white>\n"
                + "<white>/ui clear attributes <player|UUID|all></white>"));
    }

    // =========================
    // CHAT
    // =========================

    private boolean clearChat(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERM_CHAT)) {
            CommandErrors.noPermission(sender, PERM_CHAT);
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>Usage: </yellow><white>/ui clear chat <player|all></white>"));
            return true;
        }

        String target = args[2];

        if (target.equalsIgnoreCase("all")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                clearChatWindow(player);
            }
            sender.sendMessage(MessageUtil.parse("<green>✔</green> <white>Chat cleared for everyone.</white>"));
            return true;
        }

        Player targetPlayer = Bukkit.getPlayerExact(target);
        if (targetPlayer == null) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Player </red><yellow>" + target + "</yellow><red> is not online!</red>"));
            return true;
        }

        clearChatWindow(targetPlayer);
        sender.sendMessage(MessageUtil.parse(
                "<green>✔</green> <white>Chat cleared for </white><yellow>" + targetPlayer.getName()
                        + "</yellow><white>.</white>"));
        return true;
    }

    /** Sends {@value EMPTY_LINES} empty lines, scrolling the old history out of view. */
    private void clearChatWindow(Player player) {
        for (int i = 0; i < EMPTY_LINES; i++) {
            player.sendMessage("");
        }
    }

    // =========================
    // ATTRIBUTES
    // =========================

    private boolean clearAttributes(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERM_ATTRIBUTES)) {
            CommandErrors.noPermission(sender, PERM_ATTRIBUTES);
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>Usage: </yellow><white>/ui clear attributes <player|UUID|all></white>"));
            return true;
        }

        String target = args[2];

        // all — every online player
        if (target.equalsIgnoreCase("all")) {
            int count = 0;
            for (Player player : Bukkit.getOnlinePlayers()) {
                resetAttributes(player);
                count++;
            }
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Attributes reset for </white><yellow>" + count
                            + "</yellow><white> player(s).</white>"));
            return true;
        }

        // Try a player nickname first
        Player targetPlayer = Bukkit.getPlayerExact(target);
        if (targetPlayer != null) {
            resetAttributes(targetPlayer);
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Attributes reset for </white><yellow>" + targetPlayer.getName()
                            + "</yellow><white>.</white>"));
            return true;
        }

        // Fall back to an entity UUID (any living entity on any world)
        UUID uuid = parseUuid(target);
        if (uuid != null) {
            Entity found = findEntityByUuid(uuid);
            if (found instanceof LivingEntity living) {
                resetAttributes(living);
                sender.sendMessage(MessageUtil.parse(
                        "<green>✔</green> <white>Attributes reset for entity </white><gray>"
                                + found.getType().name().toLowerCase() + "</gray><white> (</white><yellow>"
                                + target + "</yellow><white>).</white>"));
                return true;
            }
            if (found != null) {
                sender.sendMessage(MessageUtil.parse(
                        "<red>❌ Entity </yellow><yellow>" + target + "</yellow><red> has no attributes ("
                                + found.getType().name().toLowerCase() + ").</red>"));
                return true;
            }
        }

        sender.sendMessage(MessageUtil.parse(
                "<red>❌ Player </red><yellow>" + target + "</yellow><red> is not online and no entity "
                        + "with this UUID was found (UUID form: 00000000-0000-0000-0000-000000000000).</red>"));
        return true;
    }

    /**
     * Resets every attribute of the entity: base value → {@code getDefaultValue()}
     * and all non-vanilla modifiers removed, so any runtime inflation
     * (speed/damage/health/...) returns to vanilla defaults.
     */
    private void resetAttributes(LivingEntity entity) {
        for (Attribute attribute : org.bukkit.Registry.ATTRIBUTE) {
            AttributeInstance instance = entity.getAttribute(attribute);
            if (instance == null) continue;

            // Remove every modifier (vanilla items re-apply their own on next tick)
            instance.getModifiers().forEach(instance::removeModifier);
            instance.setBaseValue(instance.getAttribute().getDefaultValue());
        }
        // Recompute health after max-health reset (clamped to the new max)
        if (entity.getHealth() > entity.getAttribute(Attribute.MAX_HEALTH).getValue()) {
            entity.setHealth(entity.getAttribute(Attribute.MAX_HEALTH).getValue());
        }
    }

    private UUID parseUuid(String input) {
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Looks up an entity by UUID across all worlds. */
    private Entity findEntityByUuid(UUID uuid) {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            Entity entity = world.getEntity(uuid);
            if (entity != null) return entity;
        }
        return null;
    }

    // =========================
    // TAB-COMPLETE
    // =========================

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            List<String> result = new ArrayList<>();
            for (String s : List.of("chat", "attributes")) {
                if (s.startsWith(args[1].toLowerCase())) result.add(s);
            }
            return result;
        }

        String branch = args[1].toLowerCase();
        if (args.length == 3) {
            List<String> result = new ArrayList<>();
            if (branch.equals("chat") && sender.hasPermission(PERM_CHAT)) {
                addIfMatches(result, "all", args[2]);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    addIfMatches(result, player.getName(), args[2]);
                }
            } else if (branch.equals("attributes") && sender.hasPermission(PERM_ATTRIBUTES)) {
                addIfMatches(result, "all", args[2]);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    addIfMatches(result, player.getName(), args[2]);
                }
            }
            return result;
        }
        return List.of();
    }

    private void addIfMatches(List<String> result, String candidate, String partial) {
        if (candidate.toLowerCase().startsWith(partial.toLowerCase()) && !result.contains(candidate)) {
            result.add(candidate);
        }
    }
}
