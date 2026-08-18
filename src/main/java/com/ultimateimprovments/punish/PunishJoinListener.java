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

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerLogin(PlayerLoginEvent e) {
        Player player = e.getPlayer();
        String uuid = player.getUniqueId().toString();
        String ip = e.getAddress() != null ? e.getAddress().getHostAddress() : "";
        String hwId = PunishmentManager.computeHwId(ip, player.getName());

        // Check the ban
        PunishmentManager.PunishmentRecord ban = PunishmentManager.getActiveBan(uuid, ip, hwId);
        if (ban != null) {
            e.disallow(PlayerLoginEvent.Result.KICK_BANNED, buildBanMessage(ban));
            return;
        }

        // Check whether the mute expired (to remove it on join)
        PunishmentManager.PunishmentRecord mute = PunishmentManager.getActiveMute(uuid, ip, hwId);
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

        PunishmentManager.PunishmentRecord mute = PunishmentManager.getActiveMute(uuid.toString(), ip, hwId);
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

    // =========================
    // BUILD MESSAGES
    // =========================

    private static String buildBanMessage(PunishmentManager.PunishmentRecord ban) {
        String duration = ban.isPermanent()
                ? "Permanent"
                : PunishmentMessages.formatTime(ban.getRemainingMs());
        return PunishmentMessages.buildBanKickMessage(
                ban.playerName, ban.punishedBy, ban.reason, duration,
                PunishmentMessages.getDiscordUrl());
    }
}
