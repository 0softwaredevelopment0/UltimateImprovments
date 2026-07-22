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
 * 🛡 PunishJoinListener — проверяет баны/муты при входе игрока.
 * <p>
 * - На PlayerLoginEvent проверяет активные баны (UUID, IP, HW)
 * - На PlayerJoinEvent проверяет активные муты (сохраняет в память)
 * <p>
 * Муты хранятся в памяти (Map) для быстрой проверки из чат-листенера.
 */
public class PunishJoinListener implements Listener {

    /** Карта замученных игроков: UUID -> запись мута */
    private static final Map<UUID, PunishmentManager.PunishmentRecord> mutedPlayers = new HashMap<>();

    // =========================
    // LOGIN — проверка бана
    // =========================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerLogin(PlayerLoginEvent e) {
        Player player = e.getPlayer();
        String uuid = player.getUniqueId().toString();
        String ip = e.getAddress() != null ? e.getAddress().getHostAddress() : "";
        String hwId = PunishmentManager.computeHwId(ip, player.getName());

        // Проверяем бан
        PunishmentManager.PunishmentRecord ban = PunishmentManager.getActiveBan(uuid, ip, hwId);
        if (ban != null) {
            e.disallow(PlayerLoginEvent.Result.KICK_BANNED, buildBanMessage(ban));
            return;
        }

        // Проверяем, не истёк ли мут (чтобы снять при входе)
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
    // JOIN — обновление статуса мута
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
     * Проверяет, замучен ли игрок.
     */
    public static boolean isMuted(Player player) {
        PunishmentManager.PunishmentRecord record = mutedPlayers.get(player.getUniqueId());
        if (record == null) return false;

        // Проверяем, не истёк ли мут
        if (record.isExpired()) {
            PunishmentManager.unpunishById(record.id);
            mutedPlayers.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    /**
     * Возвращает запись мута игрока.
     */
    public static PunishmentManager.PunishmentRecord getMuteRecord(Player player) {
        return mutedPlayers.get(player.getUniqueId());
    }

    /**
     * Добавляет игрока в кеш мута (вызывается из PunishSubcommand.handleMute()).
     */
    public static void addMuteCache(Player player, PunishmentManager.PunishmentRecord record) {
        mutedPlayers.put(player.getUniqueId(), record);
    }

    /**
     * Снимает мут с игрока (очищает кеш).
     */
    public static void removeMuteCache(Player player) {
        mutedPlayers.remove(player.getUniqueId());
    }

    // =========================
    // BUILD MESSAGES
    // =========================

    private static String buildBanMessage(PunishmentManager.PunishmentRecord ban) {
        String duration;
        if (ban.isPermanent()) {
            duration = "Permanent";
        } else {
            long remaining = ban.getRemainingMs() / 1000;
            if (remaining < 60) duration = remaining + "s";
            else if (remaining < 3600) duration = (remaining / 60) + "m " + (remaining % 60) + "s";
            else if (remaining < 86400) duration = (remaining / 3600) + "h " + ((remaining % 3600) / 60) + "m";
            else duration = (remaining / 86400) + "d " + ((remaining % 86400) / 3600) + "h";
        }

        return MessageUtil.legacy(
                "<red>⛔ You are banned from this server!</red>\n" +
                "<gray>Reason:</gray> <white>" + ban.reason + "</white>\n" +
                "<gray>Duration:</gray> <white>" + duration + "</white>\n" +
                "<dark_gray>By: " + ban.punishedBy + "</dark_gray>"
        );
    }
}
