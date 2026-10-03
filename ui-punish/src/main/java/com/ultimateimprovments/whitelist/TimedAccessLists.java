package com.ultimateimprovments.whitelist;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.punish.PunishmentManager;
import com.ultimateimprovments.punish.PunishmentMessages;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TimedAccessLists — shared timed-scheduling engine for the custom whitelist
 * and blacklist (/ui whitelist|blacklist).
 * <p>
 * Two kinds of deferred operations are supported:
 * <ul>
 *   <li><b>Delayed</b> — apply a change after a delay (the optional
 *       {@code -t <time>} flag on add/remove/on/off).</li>
 *   <li><b>Temp</b> ({@code *-temp} subcommands) — apply instantly, then
 *       automatically revert after a delay.</li>
 * </ul>
 * Time format: {@code <N>s|m|h|d} (seconds, minutes, hours, days); both
 * {@code -t 5m} and {@code -t5m} forms are accepted (same as /ui server lockdown).
 * <p>
 * Schedules are in-memory only: pending operations are lost on restart.
 * A newer schedule for the same target (same player and effect, or the list
 * state) replaces the older one.
 * <p>
 * Feedback messages come from {@code messages[_en].punishment.timed.*}
 * (multiline MiniMessage lists, placeholders: %player%, %duration%, %list%).
 */
public final class TimedAccessLists {

    private TimedAccessLists() {}

    // =========================
    // ACCESS LIST BRIDGE
    // =========================

    /** The two managed access lists, bridged to their managers. */
    public enum AccessList {
        WHITELIST {
            @Override public boolean contains(String name) { return WhitelistManager.isWhitelisted(name); }
            @Override public boolean add(String name) { return WhitelistManager.add(name); }
            @Override public boolean remove(String name) { return WhitelistManager.remove(name); }
            @Override public boolean setEnabled(boolean value) { return WhitelistManager.setEnabled(value); }
            @Override public boolean isEnabled() { return WhitelistManager.isEnabled(); }
            @Override public String logTag() { return "Whitelist"; }
        },
        BLACKLIST {
            @Override public boolean contains(String name) { return BlacklistManager.isBlacklisted(name); }
            @Override public boolean add(String name) { return BlacklistManager.add(name); }
            @Override public boolean remove(String name) { return BlacklistManager.remove(name); }
            @Override public boolean setEnabled(boolean value) { return BlacklistManager.setEnabled(value); }
            @Override public boolean isEnabled() { return BlacklistManager.isEnabled(); }
            @Override public String logTag() { return "Blacklist"; }
        };

        public abstract boolean contains(String name);
        public abstract boolean add(String name);
        public abstract boolean remove(String name);
        public abstract boolean setEnabled(boolean value);
        public abstract boolean isEnabled();
        public abstract String logTag();

        /** Config/log key of the list ("whitelist"/"blacklist"). */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Localized display name for the %list% message placeholder. */
        public String displayName() {
            return PunishmentMessages.listDisplayName(this == WHITELIST);
        }
    }

    // =========================
    // PENDING SCHEDULES
    // =========================

    /** Pending tasks by key: {@code <list>:p:<name>:add|remove} or {@code <list>:state}. */
    private static final Map<String, BukkitTask> PENDING = new ConcurrentHashMap<>();

    private static void replace(String key, long delayMs, Runnable action) {
        BukkitTask prev = PENDING.remove(key);
        if (prev != null) prev.cancel();

        long delayTicks = Math.max(1L, delayMs / 50L);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            PENDING.remove(key);
            action.run();
        }, delayTicks);
        PENDING.put(key, task);
    }

    private static void schedulePlayer(AccessList list, String player, boolean addEffect, long delayMs) {
        String key = list.key() + ":p:" + player.toLowerCase(Locale.ROOT) + ":" + (addEffect ? "add" : "remove");
        replace(key, delayMs, () -> {
            boolean ok = addEffect ? list.add(player) : list.remove(player);
            ConsoleLogger.info("[" + list.logTag() + "] Scheduled " + (addEffect ? "add" : "remove")
                    + (ok ? " done: " : " skipped (already applied): ") + player);
        });
    }

    private static void scheduleState(AccessList list, boolean enableEffect, long delayMs) {
        String key = list.key() + ":state";
        replace(key, delayMs, () -> {
            boolean ok = list.setEnabled(enableEffect);
            ConsoleLogger.info("[" + list.logTag() + "] Scheduled "
                    + (enableEffect ? "ENABLE" : "DISABLE")
                    + (ok ? " done." : " skipped (already applied)."));
        });
    }

    private static boolean hasStatePending(AccessList list) {
        return PENDING.containsKey(list.key() + ":state");
    }

    // =========================
    // TIME PARSING
    // =========================

    /**
     * Parses a delay like "30s", "5m", "2h", "7d" into millis.
     *
     * @return the delay in millis, or -1 when the format is invalid
     */
    public static long parseDelayMillis(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return -1;
        String lower = timeStr.toLowerCase(Locale.ROOT).trim();

        char unit = lower.charAt(lower.length() - 1);
        String numStr = lower.substring(0, lower.length() - 1);
        long amount;
        try {
            amount = Long.parseLong(numStr);
        } catch (NumberFormatException e) {
            return -1;
        }
        if (amount <= 0) return -1;

        return switch (unit) {
            case 's' -> amount * 1000L;
            case 'm' -> amount * 60_000L;
            case 'h' -> amount * 3_600_000L;
            case 'd' -> amount * 86_400_000L;
            default -> -1;
        };
    }

    /**
     * Extracts the optional {@code -t <time>} flag from args.
     *
     * @param fromIndex first arg index to scan (3 for player commands, 2 for on/off)
     * @return the delay in millis, 0 when the flag is absent, or null on a
     *         malformed flag (an error message has been sent to the sender)
     */
    public static Long parseTimeFlag(CommandSender sender, String[] args, int fromIndex) {
        for (int i = fromIndex; i < args.length; i++) {
            String lower = args[i].toLowerCase(Locale.ROOT);
            if (lower.equals("-t")) {
                if (i + 1 >= args.length) {
                    sendInvalidTime(sender);
                    return null;
                }
                long millis = parseDelayMillis(args[i + 1]);
                if (millis <= 0) {
                    sendInvalidTime(sender);
                    return null;
                }
                return millis;
            }
            if (lower.startsWith("-t") && lower.length() > 2) {
                long millis = parseDelayMillis(lower.substring(2));
                if (millis <= 0) {
                    sendInvalidTime(sender);
                    return null;
                }
                return millis;
            }
            if (lower.startsWith("-")) {
                sender.sendMessage(MessageUtil.parse(
                        "<yellow>⚠</yellow> <white>Unknown flag: </white><yellow>" + args[i] + "</yellow>"));
                return null;
            }
        }
        return 0L;
    }

    private static void sendInvalidTime(CommandSender sender) {
        sender.sendMessage(buildTimed("invalid_time", null, null, null));
    }

    // =========================
    // ACTION DISPATCH
    // =========================

    /**
     * Handles the timed whitelist/blacklist actions:
     * add/remove/on/off with the {@code -t} flag and the add-temp/remove-temp/
     * on-temp/off-temp variants.
     *
     * @return true when the action was fully handled,
     *         null when the caller must fall back to the instant (legacy) handler
     *         (no -t flag, unknown action, or missing player argument)
     */
    public static Boolean handle(CommandSender sender, AccessList list, String action, String[] args) {
        // Normalize remove aliases to "remove"
        String act = switch (action) {
            case "rm", "del" -> "remove";
            default -> action;
        };

        boolean playerAction = switch (act) {
            case "add", "remove", "add-temp", "remove-temp" -> true;
            default -> false;
        };
        boolean known = playerAction || switch (act) {
            case "on", "off", "on-temp", "off-temp" -> true;
            default -> false;
        };
        if (!known) return null;

        Long delay = parseTimeFlag(sender, args, playerAction ? 3 : 2);
        if (delay == null) return true; // malformed flag — error already sent
        if (delay == 0) return null;    // no -t flag — instant (legacy) behavior
        if (playerAction && args.length < 3) return null; // missing player — caller shows usage

        String player = playerAction ? args[2] : null;
        switch (act) {
            case "add" -> {
                if (list.contains(player)) {
                    sender.sendMessage(buildTimed("already_in_list", list, player, null));
                    return true;
                }
                schedulePlayer(list, player, true, delay);
                sender.sendMessage(buildTimed("add_scheduled", list, player, delay));
            }
            case "remove" -> {
                if (!list.contains(player)) {
                    sender.sendMessage(buildTimed("not_in_list", list, player, null));
                    return true;
                }
                schedulePlayer(list, player, false, delay);
                sender.sendMessage(buildTimed("remove_scheduled", list, player, delay));
            }
            case "add-temp" -> {
                list.add(player); // no-op when already present — the auto-revert is what matters
                schedulePlayer(list, player, false, delay);
                sender.sendMessage(buildTimed("add_temp", list, player, delay));
            }
            case "remove-temp" -> {
                list.remove(player);
                schedulePlayer(list, player, true, delay);
                sender.sendMessage(buildTimed("remove_temp", list, player, delay));
            }
            case "on" -> {
                if (list.isEnabled() && !hasStatePending(list)) {
                    sender.sendMessage(buildTimed("already_enabled", list, null, null));
                    return true;
                }
                scheduleState(list, true, delay);
                sender.sendMessage(buildTimed("on_scheduled", list, null, delay));
            }
            case "off" -> {
                if (!list.isEnabled() && !hasStatePending(list)) {
                    sender.sendMessage(buildTimed("already_disabled", list, null, null));
                    return true;
                }
                scheduleState(list, false, delay);
                sender.sendMessage(buildTimed("off_scheduled", list, null, delay));
            }
            case "on-temp" -> {
                list.setEnabled(true);
                scheduleState(list, false, delay);
                sender.sendMessage(buildTimed("on_temp", list, null, delay));
            }
            case "off-temp" -> {
                list.setEnabled(false);
                scheduleState(list, true, delay);
                sender.sendMessage(buildTimed("off_temp", list, null, delay));
            }
            default -> {
                return null;
            }
        }
        return true;
    }

    // =========================
    // MESSAGES
    // =========================

    /** Builds a localized timed-operation message from messages[_en].punishment.timed.<key>. */
    private static net.kyori.adventure.text.Component buildTimed(String key, AccessList list,
                                                                  String player, Long delayMs) {
        String listName = list != null ? list.displayName() : "";
        String duration = delayMs != null ? PunishmentManager.formatRemaining(delayMs) : "";
        return PunishmentMessages.buildTimedListMessage(key, listName, player, duration);
    }
}
