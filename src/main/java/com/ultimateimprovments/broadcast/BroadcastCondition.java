package com.ultimateimprovments.broadcast;

import com.ultimateimprovments.util.AlertBroadcast;
import com.ultimateimprovments.util.PlaceholderResolver;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 📡 A single parsed auto-broadcast condition.
 * <p>
 * A condition is a string like {@code "<name>=<value>"}, e.g.
 * {@code is-op=true} or {@code height-above=25}. Conditions with a comparison
 * operator use the {@code -is} / {@code -above} / {@code -below} suffixes.
 * <p>
 * Two kinds of conditions:
 * <ul>
 *   <li><b>Player</b> ({@code is-op}, {@code is-gamemode}, {@code height-*},
 *       {@code health-*}, {@code hunger-*}, {@code is-alert}, {@code is-group},
 *       {@code xp-lvl-*}) — checked separately for each online player.</li>
 *   <li><b>Global</b> ({@code online-*}) — checked once per cycle against the
 *       total number of players on the server, independent of any player.</li>
 * </ul>
 * <p>
 * Same-name conditions (identical name) with different values = OR.
 * Conditions of different names = AND. Duplicate «name=value» pairs are dropped
 * with a console warning at parse time.
 */
public final class BroadcastCondition {

    /** Comparison operator for numeric conditions. */
    public enum Op { IS, ABOVE, BELOW }

    /** Pattern for conditions with an operator: height-above=25, xp-lvl-below=10 etc. */
    private static final Pattern PREFIXED =
            Pattern.compile("^(height|health|hunger|online|xp-lvl)-(is|above|below)$");

    private final String name;        // normalized name, e.g. "height-above", "is-op"
    private final Op op;              // IS for simple (boolean/string) conditions
    private final String rawValue;    // the value as a config string (lowercase)
    private final boolean global;     // true for online-* (server condition)
    private final boolean boolValue;  // for is-op / is-alert
    private final int intValue;       // for height / health / hunger / xp-lvl / online
    private final GameMode gameMode;  // for is-gamemode

    private BroadcastCondition(String name, Op op, String rawValue, boolean global,
                               boolean boolValue, int intValue, GameMode gameMode) {
        this.name = name;
        this.op = op;
        this.rawValue = rawValue;
        this.global = global;
        this.boolValue = boolValue;
        this.intValue = intValue;
        this.gameMode = gameMode;
    }

    /**
     * Parses one condition entry from a config string.
     *
     * @param section  the section name (for error messages)
     * @param entry    an entry like "name=value" (already trimmed)
     * @param warnings a warning accumulator (may be null)
     * @return the parsed condition or {@code null} if the entry is invalid (already warned)
     */
    public static BroadcastCondition parse(String section, String entry, List<String> warnings) {
        int eq = entry.indexOf('=');
        if (eq <= 0) {
            warn(warnings, "Section '" + section + "': invalid condition '" + entry
                    + "' — expected \"name=value\", ignored");
            return null;
        }
        String left = entry.substring(0, eq).trim().toLowerCase(Locale.ROOT);
        String value = entry.substring(eq + 1).trim().toLowerCase(Locale.ROOT);
        if (left.isEmpty() || value.isEmpty()) {
            warn(warnings, "Section '" + section + "': invalid condition '" + entry
                    + "' — name and value cannot be empty, ignored");
            return null;
        }

        // Conditions with an operator: height-above=25, xp-lvl-is=10 ...
        Matcher m = PREFIXED.matcher(left);
        if (m.matches()) {
            String base = m.group(1);
            Op op = Op.valueOf(m.group(2).toUpperCase(Locale.ROOT));
            boolean global = "online".equals(base);
            int intValue;
            try {
                intValue = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                warn(warnings, "Section '" + section + "': condition '" + left
                        + "' requires an integer value, got '" + value + "', ignored");
                return null;
            }
            if (global && intValue < 0) {
                warn(warnings, "Section '" + section + "': condition 'online-*' requires value >= 0, got '"
                        + value + "', ignored");
                return null;
            }
            return new BroadcastCondition(left, op, value, global, false, intValue, null);
        }

        // Simple conditions: is-op, is-gamemode, is-alert, is-group
        Op op = Op.IS;
        switch (left) {
            case "is-op":
            case "is-alert": {
                if (!value.equals("true") && !value.equals("false")) {
                    warn(warnings, "Section '" + section + "': condition '" + left
                            + "' requires true/false, got '" + value + "', ignored");
                    return null;
                }
                return new BroadcastCondition(left, op, value, false,
                        Boolean.parseBoolean(value), 0, null);
            }
            case "is-gamemode": {
                GameMode gm;
                try {
                    gm = GameMode.valueOf(value.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    warn(warnings, "Section '" + section + "': condition 'is-gamemode' requires one of "
                            + "survival/creative/spectator/adventure, got '" + value + "', ignored");
                    return null;
                }
                return new BroadcastCondition(left, op, value, false, false, 0, gm);
            }
            case "is-group": {
                return new BroadcastCondition(left, op, value, false, false, 0, null);
            }
            default:
                warn(warnings, "Section '" + section + "': unknown condition '" + left + "', ignored");
                return null;
        }
    }

    /**
     * Checks the condition for a specific player. Don't call for global conditions
     * (online-*) — those have {@link #matchesGlobal()}.
     */
    public boolean matches(Player player) {
        if (player == null) return false;
        switch (name) {
            case "is-op":
                return player.isOp() == boolValue;
            case "is-alert":
                return AlertBroadcast.hasAlertPermission(player) == boolValue;
            case "is-gamemode":
                return player.getGameMode() == gameMode;
            case "is-group": {
                // Main LuckPerms group (like the %group% placeholder)
                String group = PlaceholderResolver.resolveBuiltin(player, "group");
                if (group != null && !group.isEmpty() && group.equalsIgnoreCase(rawValue)) {
                    return true;
                }
                // Fallback: LuckPerms grants the group.<name> permission for the main AND
                // inherited groups, so this check also covers inheritance.
                return player.hasPermission("group." + rawValue);
            }
            case "height":
                return compareInt(player.getLocation().getBlockY());
            case "health":
                return switch (op) {
                    // IS compares the INTEGER part of health (19.5 → 19),
                    // ABOVE/BELOW use the raw value (19.5 > 18 → true).
                    case IS -> (int) player.getHealth() == intValue;
                    case ABOVE -> player.getHealth() > intValue;
                    case BELOW -> player.getHealth() < intValue;
                };
            case "hunger":
                return compareInt(player.getFoodLevel());
            case "xp-lvl":
                return compareInt(player.getLevel());
            default:
                return false;
        }
    }

    /**
     * Checks the global (server) condition {@code online-*}.
     * Returns {@code false} for all other conditions.
     */
    public boolean matchesGlobal() {
        if (!global) return false;
        return compareInt(Bukkit.getOnlinePlayers().size());
    }

    /** @return the condition name (normalized), e.g. "height-above" */
    public String getName() {
        return name;
    }

    /** @return the raw condition value (lowercase) */
    public String getRawValue() {
        return rawValue;
    }

    /** @return true if the condition is global (online-*) and doesn't depend on a player */
    public boolean isGlobal() {
        return global;
    }

    /** Deduplication key: name=value (case-insensitive). */
    public String dedupeKey() {
        return name + "=" + rawValue;
    }

    private boolean compareInt(int actual) {
        return switch (op) {
            case IS -> actual == intValue;
            case ABOVE -> actual > intValue;
            case BELOW -> actual < intValue;
        };
    }

    private static void warn(List<String> warnings, String message) {
        if (warnings != null) {
            warnings.add(message);
        }
    }
}
