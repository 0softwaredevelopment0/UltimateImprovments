package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.command.SubCommand;
import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.core.Permissions;
import com.ultimateimprovments.database.PlayerSettingsDB;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.ScoreboardNickValidator;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /ui scoreboardnick "<text>"} — a protected custom nickname that the
 * scoreboard shows through the {@code %player_scoreboard_nick%} placeholder.
 *
 * <h2>Usage</h2>
 * <ul>
 *   <li>{@code /ui scoreboardnick "<text>"} — set your own nickname
 *       (MiniMessage allowed, e.g. {@code <gradient:#00AAFF:#FF55FF>Hero</gradient>}).
 *       Everything after the subcommand is taken as the nickname, with or
 *       without surrounding quotes;</li>
 *   <li>{@code /ui scoreboardnick off} — switch the stored nickname off, the
 *       real name is shown again (permission {@code ui.scoreboardnick.toggle});</li>
 *   <li>{@code /ui scoreboardnick on} — switch the stored nickname back on;</li>
 *   <li>no argument — show the current nickname and its state;</li>
 *   <li>{@code /ui scoreboardnick <player> <text|on|off>} — the same for another
 *       player (permission {@code ui.scoreboardnick.others}).</li>
 * </ul>
 *
 * <p>The nickname is validated by {@link ScoreboardNickValidator}: visible
 * length from the config (MiniMessage tags are not counted, bypass via
 * {@code ui.scoreboardnick.bypass.length[.min|.max]}) and the forbidden-syntax
 * filter ({@code scoreboard.nick.filter}, bypass via
 * {@code ui.scoreboardnick.bypass.filter} / {@code .wildcard} / {@code .regex}).
 * A rejected nickname is reported with its own error code: 019 forbidden
 * syntax, 020 wrong length, 021 unparsable/miniMessage-invalid input.
 */
public final class ScoreboardNickSubcommand implements SubCommand {

    /** 019 — the nickname hit a forbidden-syntax rule. */
    public static final int ERR_FORBIDDEN_SYNTAX = ScoreboardNickValidator.ERR_FORBIDDEN_SYNTAX;
    /** 020 — the nickname length is outside the configured limits. */
    public static final int ERR_BAD_LENGTH = ScoreboardNickValidator.ERR_BAD_LENGTH;
    /** 021 — the nickname cannot be parsed at all (MiniMessage/format error). */
    public static final int ERR_INVALID_FORMAT = ScoreboardNickValidator.ERR_INVALID_FORMAT;

    @Override
    public String getName() {
        return "scoreboardnick";
    }

    @Override
    public List<String> getAliases() {
        return List.of("sbnick");
    }

    // ═══════════ EXECUTE ═══════════

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            CommandErrors.playerOnly(sender);
            return true;
        }
        if (!player.hasPermission(Permissions.CMD_SCOREBOARDNICK)) {
            CommandErrors.noPermission(player, Permissions.CMD_SCOREBOARDNICK);
            return true;
        }
        if (!ScoreboardNickValidator.featureEnabled()) {
            CommandErrors.moduleDisabled(player, "scoreboard.nick.enabled");
            return true;
        }

        // /ui scoreboardnick <player> <text|on|off> — admin form. The first
        // argument is treated as a target only when it is an exactly matching
        // online player (quote a nickname that starts with a player name).
        Player target = player;
        int firstArg = 1;
        if (args.length >= 3) {
            Player other = Bukkit.getPlayerExact(args[1]);
            if (other != null && !other.getUniqueId().equals(player.getUniqueId())) {
                if (!player.hasPermission(Permissions.SCOREBOARDNICK_OTHERS)) {
                    CommandErrors.noPermission(player, Permissions.SCOREBOARDNICK_OTHERS);
                    return true;
                }
                target = other;
                firstArg = 2;
            }
        }

        String typed = joinFrom(args, firstArg);
        if (typed.isEmpty()) {
            showState(player, target);
            return true;
        }

        boolean quoted = typed.length() >= 2 && typed.startsWith("\"") && typed.endsWith("\"");
        String value = quoted ? typed.substring(1, typed.length() - 1).trim() : typed;
        if (value.isEmpty()) {
            player.sendMessage(MessageUtil.parse(message("usage",
                    "<red>❌ Usage: </red><white>/ui scoreboardnick \"<text>\" | <on|off></white>"), player));
            return true;
        }

        if (!quoted && value.equalsIgnoreCase("off")) {
            switchNick(player, target, false);
            return true;
        }
        if (!quoted && value.equalsIgnoreCase("on")) {
            switchNick(player, target, true);
            return true;
        }

        ScoreboardNickValidator.Validation validation = ScoreboardNickValidator.validate(target, value);
        if (!validation.ok()) {
            CommandErrors.custom(player, validation.errorCode(), validation.errorMessage());
            return true;
        }

        String raw = validation.nick().raw();
        PlayerSettingsDB.setScoreboardNick(target.getUniqueId(), raw);
        player.sendMessage(MessageUtil.parse(message("set",
                "<green>✔</green> <white>Scoreboard nickname for</white> <yellow>%player%</yellow><white>:</white> %nick%")
                .replace("%player%", target.getName())
                .replace("%nick%", raw), player));
        return true;
    }

    // ═══════════ TAB COMPLETE ═══════════

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length != 2) return List.of();

        List<String> suggestions = new ArrayList<>();
        suggestions.add("on");
        suggestions.add("off");
        if (sender instanceof Player player && player.hasPermission(Permissions.SCOREBOARDNICK_OTHERS)) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                suggestions.add(online.getName());
            }
        }
        String prefix = args[1].toLowerCase(Locale.ROOT);
        return suggestions.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
    }

    // ═══════════ INTERNALS ═══════════

    /** Switches the stored nickname on/off without touching the stored text. */
    private static void switchNick(Player actor, Player target, boolean enabled) {
        if (!actor.hasPermission(Permissions.SCOREBOARDNICK_TOGGLE)) {
            CommandErrors.noPermission(actor, Permissions.SCOREBOARDNICK_TOGGLE);
            return;
        }
        String stored = PlayerSettingsDB.getScoreboardNick(target.getUniqueId());
        if (stored == null || stored.isBlank()) {
            actor.sendMessage(MessageUtil.parse(message("no_nick",
                    "<red>❌</red> <white>No scoreboard nickname saved for</white> <yellow>%player%</yellow> <white>yet</white>")
                    .replace("%player%", target.getName()), actor));
            return;
        }
        PlayerSettingsDB.setScoreboardNickEnabled(target.getUniqueId(), enabled);
        String text = enabled
                ? message("enabled",
                        "<green>✔</green> <white>Scoreboard nickname for</white> <yellow>%player%</yellow><white>:</white> <green>ON</green> <gray>(</gray>%nick%<gray>)</gray>")
                : message("disabled",
                        "<red>❌</red> <white>Scoreboard nickname for</white> <yellow>%player%</yellow><white>:</white> <red>OFF</red> <gray>(the real name is shown)</gray>");
        actor.sendMessage(MessageUtil.parse(text
                .replace("%player%", target.getName())
                .replace("%nick%", stored), actor));
    }

    /** Shows the current nickname and its state (used without arguments). */
    private static void showState(Player viewer, Player target) {
        String stored = PlayerSettingsDB.getScoreboardNick(target.getUniqueId());
        if (stored == null || stored.isBlank()) {
            viewer.sendMessage(MessageUtil.parse(message("current_none",
                    "<gray>Scoreboard nickname of</gray> <yellow>%player%</yellow><gray>:</gray> <white>not set</white> <gray>(the real name is shown)</gray>")
                    .replace("%player%", target.getName()), viewer));
            return;
        }
        boolean on = PlayerSettingsDB.isScoreboardNickEnabled(target.getUniqueId());
        String state = on
                ? message("state_on", "ON")
                : message("state_off", "OFF");
        viewer.sendMessage(MessageUtil.parse(message("current",
                "<gray>Scoreboard nickname of</gray> <yellow>%player%</yellow><gray>:</gray> %nick% <gray>(</gray><white>%state%</white><gray>, %length% chars)</gray>")
                .replace("%player%", target.getName())
                .replace("%state%", state)
                .replace("%length%", String.valueOf(ScoreboardNickValidator.visibleLength(stored)))
                .replace("%nick%", stored), viewer));
    }

    /** Joins args[start..] with spaces (quotes are kept, they are stripped later). */
    private static String joinFrom(String[] args, int start) {
        if (start >= args.length) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString().trim();
    }

    /** Reads a message from {@code messages.scoreboard.nick.<key>} with an English fallback. */
    private static String message(String key, String def) {
        return MessagesManager.getString("scoreboard.nick." + key, def);
    }
}
