package com.ultimateimprovments.punish;

import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PunishJoinListener — checks bans/mutes when a player joins.
 * <p>
 * - On PlayerLoginEvent checks active bans (UUID, IP, HW)
 * - On PlayerJoinEvent checks active mutes (stores in memory)
 * <p>
 * Mutes are kept in memory (Map) for fast checks from the chat listener.
 */
public class PunishJoinListener implements Listener {

    /** Map of muted players: UUID -> mute record */
    private static final Map<UUID, PunishmentManager.PunishmentRecord> mutedPlayers = new HashMap<>();

    // =========================
    // LOGIN — ban check
    // =========================

    // PlayerLoginEvent is deprecated in Paper 26.3 (async pre-login is the modern hook) -
    // migration is a threading change and needs a dedicated tested task.
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerLogin(PlayerLoginEvent e) {
        Player player = e.getPlayer();
        String uuid = player.getUniqueId().toString();
        String ip = e.getAddress() != null ? e.getAddress().getHostAddress() : "";
        String hwId = PunishmentManager.computeHwId(ip, player.getName());

        // Check the ban (name fallback catches legacy offline: entries)
        PunishmentManager.PunishmentRecord ban = PunishmentManager.getActivePunishment(
                PunishmentManager.PunishType.BAN, uuid, player.getName(), ip, hwId);
        if (ban != null) {
            e.disallow(PlayerLoginEvent.Result.KICK_BANNED, MessageUtil.parse(buildBanMessage(ban)));
            return;
        }

        // Check whether the mute expired (to remove it on join)
        PunishmentManager.PunishmentRecord mute = PunishmentManager.getActivePunishment(
                PunishmentManager.PunishType.MUTE, uuid, player.getName(), ip, hwId);
        if (mute != null) {
            if (mute.isExpired()) {
                PunishmentManager.unpunishById(mute.id);
                mutedPlayers.remove(player.getUniqueId());
            } else {
                mutedPlayers.put(player.getUniqueId(), mute);
            }
        } else {
            mutedPlayers.remove(player.getUniqueId());
        }
    }

    // =========================
    // JOIN — update mute status
    // =========================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();
        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "";
        String hwId = PunishmentManager.computeHwId(ip, player.getName());

        PunishmentManager.PunishmentRecord mute = PunishmentManager.getActivePunishment(
                PunishmentManager.PunishType.MUTE, uuid.toString(), player.getName(), ip, hwId);
        if (mute != null) {
            if (mute.isExpired()) {
                PunishmentManager.unpunishById(mute.id);
                mutedPlayers.remove(uuid);
            } else {
                mutedPlayers.put(uuid, mute);
            }
        } else {
            mutedPlayers.remove(uuid);
        }
    }

    // =========================
    // MUTE CHECK
    // =========================

    /**
     * Checks whether the player is muted.
     */
    public static boolean isMuted(Player player) {
        PunishmentManager.PunishmentRecord record = mutedPlayers.get(player.getUniqueId());
        if (record == null) return false;

        // Check whether the mute expired
        if (record.isExpired()) {
            PunishmentManager.unpunishById(record.id);
            mutedPlayers.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    /**
     * Returns the player's mute record.
     */
    public static PunishmentManager.PunishmentRecord getMuteRecord(Player player) {
        return mutedPlayers.get(player.getUniqueId());
    }

    /**
     * Adds the player to the mute cache (called from PunishSubcommand.handleMute()).
     */
    public static void addMuteCache(Player player, PunishmentManager.PunishmentRecord record) {
        mutedPlayers.put(player.getUniqueId(), record);
    }

    /**
     * Removes the mute from the player (clears the cache).
     */
    public static void removeMuteCache(Player player) {
        mutedPlayers.remove(player.getUniqueId());
    }

    /**
     * Drops every cached mute whose DATABASE record was just deactivated — the
     * {@code /ui punish pardon} path calls this with the lifted record IDs, so an
     * alt muted through an IP/HW scope is released as well (a per-player call
     * would only clear the single online name).
     *
     * @param recordIds punishment row IDs that were lifted
     * @return how many cached entries were dropped
     */
    public static int removeMuteCacheByRecordIds(java.util.Collection<Integer> recordIds) {
        if (recordIds == null || recordIds.isEmpty()) return 0;
        int removed = 0;
        for (java.util.Iterator<Map.Entry<UUID, PunishmentManager.PunishmentRecord>> it =
                mutedPlayers.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, PunishmentManager.PunishmentRecord> entry = it.next();
            if (entry.getValue() != null && recordIds.contains(entry.getValue().id)) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    // =========================
    // BUILD MESSAGES
    // =========================

    private static String buildBanMessage(PunishmentManager.PunishmentRecord ban) {
        String duration = PunishmentMessages.durationLabel(ban.expiresAt);
        return PunishmentMessages.buildBanKickMessage(
                ban.playerName, ban.punishedBy, ban.reason, duration,
                PunishmentMessages.getDiscordUrl());
    }
}
