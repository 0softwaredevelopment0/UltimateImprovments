package com.ultimateimprovments.punish;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PardonManager — the single entry point for LIFTING punishments.
 * <p>
 * Every removal path goes through {@link #pardon(Request)}: the
 * {@code /ui punish pardon} command, and (with {@code SYSTEM} as the actor) the
 * automatic expiry inside {@link PunishJoinListener} / {@link BlockEnforcer}.
 * The old per-command removal code ({@code /ui punish unban|unmute|unblock|unwarn})
 * is gone — new punishment types must not grow their own removal branches.
 * <p>
 * What a pardon does, in order:
 * <ol>
 *   <li>resolves the target(s) — one player by UUID/name, or every online player;</li>
 *   <li>collects EVERY active record of the requested types (a player may carry
 *       several stacked bans/mutes/blocks/warns — all of them are matched);</li>
 *   <li>applies the confirmation policy (mass pardons and batches above
 *       {@code punish.pardon.confirm_threshold} need {@code -confirm});</li>
 *   <li>deactivates the rows WITH an audit trail
 *       ({@code removed_at / removed_by / remove_reason});</li>
 *   <li>invalidates the runtime state: the in-memory mute cache and the live
 *       block dialog, keyed by RECORD ID (so alts muted through an IP/HW scope
 *       are released as well).</li>
 * </ol>
 * Presentation (chat feedback, moderator alerts) is deliberately NOT here — the
 * command layer renders {@link Outcome}.
 */
public final class PardonManager {

    /** Default number of records a single pardon may lift without {@code -confirm}. */
    public static final int DEFAULT_CONFIRM_THRESHOLD = 5;

    private PardonManager() {}

    // =========================
    // REQUEST / RESULT TYPES
    // =========================

    /**
     * Pardon target: one player (UUID + name) or every online player.
     */
    public record Target(String uuid, String name, boolean allOnline) {

        public static Target online() {
            return new Target(null, null, true);
        }

        public static Target of(String uuid, String name) {
            return new Target(uuid, name, false);
        }

        /** Display label for feedback ("all online players" or the player name). */
        public String label() {
            if (allOnline) return PardonMessages.allPlayersLabel();
            return name != null ? name : (uuid != null ? uuid : "?");
        }
    }

    /**
     * One pardon request.
     */
    public record Request(Set<PunishmentManager.PunishType> types, Target target,
                          String reason, String actor,
                          boolean silent, boolean dryRun, boolean confirm, Integer singleId) {}

    /**
     * One matched punishment record.
     *
     * @param warn whether the record lives in the {@code warns} table
     */
    public record Entry(PunishmentManager.PunishType type, int id, boolean warn,
                        String playerName, String playerUuid, String reason) {}

    /**
     * Result of a pardon.
     *
     * @param matched           every record the request selected
     * @param lifted            rows actually deactivated (0 for previews/dry runs)
     * @param needsConfirmation the batch is too big / mass — nothing was written
     * @param dryRun            {@code -dry} was given — nothing was written
     * @param threshold         the configured confirmation threshold
     * @param mass              the target was {@code all}
     */
    public record Outcome(List<Entry> matched, int lifted, boolean needsConfirmation,
                          boolean dryRun, int threshold, boolean mass) {

        public boolean isEmpty() {
            return matched.isEmpty();
        }

        /** True when nothing was written because a confirmation (or a dry run) was required. */
        public boolean previewOnly() {
            return needsConfirmation || dryRun;
        }
    }

    // =========================
    // MAIN ENTRY POINT
    // =========================

    /**
     * Lifts the punishments selected by the request (or previews them).
     * Never throws: DB failures are logged and reported as {@code lifted = 0}.
     */
    public static Outcome pardon(Request request) {
        if (request == null || request.types() == null || request.types().isEmpty()
                || request.target() == null) {
            return new Outcome(List.of(), 0, false, false, confirmThreshold(), false);
        }

        Map<String, Entry> matched = new LinkedHashMap<>();

        if (request.singleId() != null) {
            collectById(request, matched);
        } else if (request.target().allOnline()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                collectPlayer(request, player.getUniqueId().toString(), player.getName(), matched);
            }
        } else {
            collectPlayer(request, request.target().uuid(), request.target().name(), matched);
        }

        List<Entry> entries = new ArrayList<>(matched.values());
        int threshold = confirmThreshold();
        boolean mass = request.target().allOnline();
        boolean needsConfirmation = !entries.isEmpty()
                && !request.confirm()
                && (mass || entries.size() > threshold);

        if (entries.isEmpty() || needsConfirmation || request.dryRun()) {
            return new Outcome(entries, 0, needsConfirmation, request.dryRun(), threshold, mass);
        }

        // ── split by table / type ──
        List<Integer> punishmentIds = new ArrayList<>();
        List<Integer> warnIds = new ArrayList<>();
        List<Integer> muteIds = new ArrayList<>();
        List<Integer> blockIds = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.warn()) {
                warnIds.add(entry.id());
                continue;
            }
            punishmentIds.add(entry.id());
            if (entry.type() == PunishmentManager.PunishType.MUTE) muteIds.add(entry.id());
            if (entry.type() == PunishmentManager.PunishType.BLOCK) blockIds.add(entry.id());
        }

        int lifted = PunishmentManager.deactivatePunishments(
                punishmentIds, request.actor(), request.reason());
        lifted += PunishmentManager.deactivateWarns(warnIds, request.actor(), request.reason());

        // ── runtime state: mute cache + live block dialog ──
        PunishJoinListener.removeMuteCacheByRecordIds(muteIds);
        BlockEnforcer.releaseByRecordIds(blockIds);

        ConsoleLogger.info("[Pardon] " + request.actor() + " lifted " + lifted
                + " punishment(s) of " + request.target().label()
                + (request.reason() == null || request.reason().isEmpty()
                    ? "" : " reason: " + request.reason()));

        return new Outcome(entries, lifted, false, false, threshold, mass);
    }

    // =========================
    // SELECTION
    // =========================

    /** Collects the active records of one player (UUID + name fallback). */
    private static void collectPlayer(Request request, String uuid, String name,
                                      Map<String, Entry> out) {
        List<PunishmentManager.PunishType> tableTypes = new ArrayList<>(request.types());
        tableTypes.remove(PunishmentManager.PunishType.WARN);

        if (!tableTypes.isEmpty()) {
            for (PunishmentManager.PunishmentRecord record
                    : PunishmentManager.findActivePunishments(tableTypes, uuid, name)) {
                put(out, new Entry(record.type, record.id, false,
                        record.playerName, record.playerUuid, record.reason));
            }
        }
        if (request.types().contains(PunishmentManager.PunishType.WARN)) {
            for (PunishmentManager.WarnRecord warn
                    : PunishmentManager.findActiveWarns(uuid, name)) {
                put(out, new Entry(PunishmentManager.PunishType.WARN, warn.id, true,
                        warn.playerName, warn.playerUuid, warn.reason));
            }
        }
    }

    /**
     * Collects one record addressed by {@code -id:<n>}. The row must still be active,
     * its type must be selected, and (unless the target is {@code all}) it must belong
     * to the target player — an ID alone never lifts someone else's punishment.
     */
    private static void collectById(Request request, Map<String, Entry> out) {
        int id = request.singleId();

        if (request.types().contains(PunishmentManager.PunishType.WARN)) {
            PunishmentManager.WarnRecord warn = PunishmentManager.findWarnById(id);
            if (warn != null && isActiveWarn(warn)
                    && belongsToTarget(request.target(), warn.playerUuid, warn.playerName)) {
                put(out, new Entry(PunishmentManager.PunishType.WARN, warn.id, true,
                        warn.playerName, warn.playerUuid, warn.reason));
            }
        }

        List<PunishmentManager.PunishType> tableTypes = new ArrayList<>(request.types());
        tableTypes.remove(PunishmentManager.PunishType.WARN);
        if (!tableTypes.isEmpty()) {
            PunishmentManager.PunishmentRecord record = PunishmentManager.findPunishmentById(id);
            if (record != null && record.active && !record.isExpired()
                    && tableTypes.contains(record.type)
                    && belongsToTarget(request.target(), record.playerUuid, record.playerName)) {
                put(out, new Entry(record.type, record.id, false,
                        record.playerName, record.playerUuid, record.reason));
            }
        }
    }

    /** Warns have no {@code active} flag: {@code expires_at = 0} (permanent) or in the future. */
    private static boolean isActiveWarn(PunishmentManager.WarnRecord warn) {
        return warn.expiresAt == 0 || warn.expiresAt > System.currentTimeMillis();
    }

    /** Whether a record belongs to the requested target. */
    private static boolean belongsToTarget(Target target, String recordUuid, String recordName) {
        if (target.allOnline()) return true;
        if (target.uuid() != null && target.uuid().equalsIgnoreCase(recordUuid)) return true;
        if (target.name() != null && recordName != null
                && target.name().equalsIgnoreCase(recordName)) return true;
        // Legacy pseudo-UUID rows: offline:<name>
        return target.name() != null && recordUuid != null
                && recordUuid.equalsIgnoreCase("offline:" + target.name().toLowerCase());
    }

    private static void put(Map<String, Entry> out, Entry entry) {
        out.putIfAbsent((entry.warn() ? "w:" : "p:") + entry.id(), entry);
    }

    // =========================
    // CONFIG
    // =========================

    /** {@code punish.pardon.confirm_threshold} (default {@value #DEFAULT_CONFIRM_THRESHOLD}). */
    public static int confirmThreshold() {
        Main main = Main.getInstance();
        if (main == null) return DEFAULT_CONFIRM_THRESHOLD;
        return Math.max(1, main.getConfig()
                .getInt("punish.pardon.confirm_threshold", DEFAULT_CONFIRM_THRESHOLD));
    }
}
