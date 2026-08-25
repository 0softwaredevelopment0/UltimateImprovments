package com.ultimateimprovments.command.clan;

import com.ultimateimprovments.core.Main;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ClanManager — in-memory state for the clan system:
 * pending confirmations (with timeout) and request cooldowns.
 *
 * <p>All persistent data lives in {@link ClanDatabase}.</p>
 */
public final class ClanManager {

    /** Action names used for confirmations. */
    public static final String CONFIRM_DISBAND = "disband";
    public static final String CONFIRM_HOME_SET = "home_set";
    public static final String CONFIRM_HOME_DEL = "home_del";
    public static final String CONFIRM_LEAVE = "leave";
    public static final String CONFIRM_TRANSFER = "transfer";
    public static final String CONFIRM_DEP_DISBAND = "dep_disband";

    /** A pending confirmation: action + expiry timestamp. */
    public record ConfirmRequest(String action, long expiresAt) {}

    private static final Map<UUID, ConfirmRequest> pending = new HashMap<>();
    private static final Map<UUID, Long> requestCooldowns = new HashMap<>();

    private ClanManager() {}

    // ============================================================
    // CONFIRMATIONS
    // ============================================================

    /** Arms a confirmation for the player with the configured timeout (seconds). */
    public static void armConfirm(Player player, String action) {
        int timeout = Main.getInstance().getConfig().getInt("clan.confirm_timeout", 30);
        long expiresAt = System.currentTimeMillis() + timeout * 1000L;
        pending.put(player.getUniqueId(), new ConfirmRequest(action, expiresAt));

        // Auto-clear after the timeout
        new BukkitRunnable() {
            @Override
            public void run() {
                ConfirmRequest cur = pending.get(player.getUniqueId());
                if (cur != null && cur.action().equals(action) && cur.expiresAt() == expiresAt) {
                    pending.remove(player.getUniqueId());
                }
            }
        }.runTaskLater(Main.getInstance(), timeout * 20L);
    }

    /**
     * Checks and consumes a pending confirmation.
     *
     * @param player the player
     * @param action the expected action
     * @return true if a valid, non-expired confirmation was pending and is now consumed
     */
    public static boolean consumeConfirm(Player player, String action) {
        UUID uuid = player.getUniqueId();
        ConfirmRequest req = pending.get(uuid);
        if (req == null) return false;
        if (!req.action().equals(action)) return false;
        if (System.currentTimeMillis() > req.expiresAt()) {
            pending.remove(uuid);
            return false;
        }
        pending.remove(uuid);
        return true;
    }

    /** Removes any pending confirmation for the player (e.g. on quit). */
    public static void clearConfirm(Player player) {
        pending.remove(player.getUniqueId());
    }

    // ============================================================
    // REQUEST COOLDOWN
    // ============================================================

    /** Returns true if the player can send a join request right now (10s cooldown). */
    public static boolean canRequest(UUID uuid) {
        Long last = requestCooldowns.get(uuid);
        if (last == null) return true;
        int cd = Main.getInstance().getConfig().getInt("clan.request_cooldown_seconds", 10);
        return System.currentTimeMillis() - last >= cd * 1000L;
    }

    /** Records that the player just sent a join request. */
    public static void markRequest(UUID uuid) {
        requestCooldowns.put(uuid, System.currentTimeMillis());
    }

    // ============================================================
    // DEPENDENCY INVITE COOLDOWN
    // ============================================================

    private static final Map<String, Long> depInviteCooldowns = new HashMap<>();

    /** Returns true if the main clan can send a dep invite right now. */
    public static boolean canDepInvite(String mainClanKey) {
        Long last = depInviteCooldowns.get(mainClanKey);
        if (last == null) return true;
        int cd = Main.getInstance().getConfig().getInt("clan.dependent.invite_cooldown_seconds", 60);
        return System.currentTimeMillis() - last >= cd * 1000L;
    }

    /** Records that the main clan just sent a dep invite. */
    public static void markDepInvite(String mainClanKey) {
        depInviteCooldowns.put(mainClanKey, System.currentTimeMillis());
    }
}
