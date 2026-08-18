package com.ultimateimprovments.punish;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * PunishmentManager — punishment system (ban/mute/kick/warn).
 * <p>
 * Supported flags:
 * <ul>
 *   <li>{@code -time:<N>s|m|h|d} — temporary punishment</li>
 *   <li>{@code -permanent} — permanent punishment</li>
 *   <li>{@code -ip} — punishment by IP</li>
 *   <li>{@code -hw} — punishment by hardware (IP + host)</li>
 * </ul>
 * <p>
 * -ip and -hw are incompatible. -time and -permanent are incompatible.
 * <p>
 * HW ID = SHA256(IP + player_name), which allows banning "hardware"
 * regardless of the account.
 */
public class PunishmentManager {

    private static final String HW_SALT = "UltimateImprovments-HW-FINGERPRINT";
    private static final long MS_PER_S = 1000L;
    private static final long MS_PER_M = MS_PER_S * 60;
    private static final long MS_PER_H = MS_PER_M * 60;
    private static final long MS_PER_D = MS_PER_H * 24;

    // =========================
    // HW ID
    // =========================

    /**
     * Computes a player's HW ID: SHA256(IP + name + salt).
     */
    public static String computeHwId(String ip, String playerName) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String raw = (ip != null ? ip : "0.0.0.0") + "|"
                    + (playerName != null ? playerName.toLowerCase() : "") + "|"
                    + HW_SALT;
            byte[] hash = md.digest(raw.getBytes());
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) {
                hex.append(String.format("%02x", b & 0xFF));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            ConsoleLogger.warn("[Punish] SHA-256 not available!");
            return ip != null ? ip : "unknown";
        }
    }

    // =========================
    // PARSE TIME FLAG (-time:<N>s|m|h|d)
    // =========================

    /**
     * Parses the time flag. Returns the expiration unix timestamp (millis) or 0 if permanent.
     *
     * @param timeStr a string like "30s", "5m", "2h", "7d"
     * @return the expiration unixMillis, 0 on error
     */
    public static long parseTimeFlag(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return 0;
        String lower = timeStr.toLowerCase().trim();

        char unit = lower.charAt(lower.length() - 1);
        String numStr = lower.substring(0, lower.length() - 1);
        long amount;
        try {
            amount = Long.parseLong(numStr);
        } catch (NumberFormatException e) {
            return 0;
        }
        if (amount <= 0) return 0;

        long durationMs;
        switch (unit) {
            case 's': durationMs = amount * MS_PER_S; break;
            case 'm': durationMs = amount * MS_PER_M; break;
            case 'h': durationMs = amount * MS_PER_H; break;
            case 'd': durationMs = amount * MS_PER_D; break;
            default: return 0;
        }

        return System.currentTimeMillis() + durationMs;
    }

    // =========================
    // PUNISH TYPES
    // =========================

    public enum PunishType {
        BAN, MUTE, KICK, WARN
    }

    // =========================
    // PUNISH — common method
    // =========================

    /**
     * Applies a punishment to a player.
     *
     * @param type      punishment type
     * @param targetUuid  target UUID
     * @param targetName  target name
     * @param reason      the reason
     * @param punisher    who punishes
     * @param expiresAt   expiration unixMillis (0 = permanent)
     * @param ip          the IP for an IP-ban (null if not IP)
     * @param hwId        the HW ID for an HW-ban (null if not HW)
     * @return true on success
     */
    public static boolean punish(PunishType type, String targetUuid, String targetName,
                                  String reason, String punisher, long expiresAt,
                                  String ip, String hwId) {
        long now = System.currentTimeMillis();

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement("""
                     INSERT INTO punishments (type, player_uuid, player_name, reason,
                         ip_address, hw_id, punished_by, punished_at, expires_at, active)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                     """)) {
            st.setString(1, type.name().toLowerCase());
            st.setString(2, targetUuid);
            st.setString(3, targetName);
            st.setString(4, reason);
            st.setString(5, ip != null ? ip : "");
            st.setString(6, hwId != null ? hwId : "");
            st.setString(7, punisher);
            st.setLong(8, now);
            st.setLong(9, expiresAt);
            st.executeUpdate();

            ConsoleLogger.info("[Punish] " + type.name() + " " + targetName
                    + " by " + punisher + " reason: " + reason);
            return true;
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[Punish] Failed to punish " + targetName, e);
            return false;
        }
    }

    // =========================
    // CHECK ACTIVE PUNISHMENT
    // =========================

    /**
     * Checks whether the player has an active punishment of the given type.
     * Checks by UUID, and if IP/HW are given — by those as well.
     *
     * @param type     the punishment type
     * @param uuid     the player UUID
     * @param ip       the player IP (may be null)
     * @param hwId     the player HW ID (may be null)
     * @return the punishment record or null
     */
    public static PunishmentRecord getActivePunishment(PunishType type, String uuid,
                                                        String ip, String hwId) {
        long now = System.currentTimeMillis();

        StringBuilder sql = new StringBuilder("""
                SELECT * FROM punishments
                WHERE type = ? AND active = 1
                AND (expires_at = 0 OR expires_at > ?)
                AND (
                    player_uuid = ?
                """);

        if (ip != null && !ip.isEmpty()) {
            sql.append(" OR ip_address = ?");
        }
        if (hwId != null && !hwId.isEmpty()) {
            sql.append(" OR hw_id = ?");
        }
        sql.append(") ORDER BY id DESC LIMIT 1");

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(sql.toString())) {
            st.setString(1, type.name().toLowerCase());
            st.setLong(2, now);
            st.setString(3, uuid);

            int idx = 4;
            if (ip != null && !ip.isEmpty()) {
                st.setString(idx++, ip);
            }
            if (hwId != null && !hwId.isEmpty()) {
                st.setString(idx, hwId);
            }

            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return mapRecord(rs);
                }
            }
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.FINE, "[Punish] Check error for " + uuid, e);
        }
        return null;
    }

    // =========================
    // UNPUNISH (remove a punishment)
    // =========================

    /**
     * Deactivates all active punishments of the given type for UUID/IP/HW.
     * If uuid starts with "offline:" — also searches by player name.
     *
     * @param playerName the player name (used for the search if uuid starts with "offline:")
     */
    public static boolean unpunish(PunishType type, String uuid, String ip, String hwId, String playerName) {
        boolean isOffline = uuid != null && uuid.startsWith("offline:");

        StringBuilder sql = new StringBuilder("""
                UPDATE punishments SET active = 0
                WHERE type = ? AND active = 1
                AND (player_uuid = ?
                """);

        // For offline players add a name search (a real UUID may have been used at ban time)
        if (isOffline && playerName != null && !playerName.isEmpty()) {
            sql.append(" OR LOWER(player_name) = ?");
        }

        if (ip != null && !ip.isEmpty()) {
            sql.append(" OR ip_address = ?");
        }
        if (hwId != null && !hwId.isEmpty()) {
            sql.append(" OR hw_id = ?");
        }
        sql.append(")");

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(sql.toString())) {
            st.setString(1, type.name().toLowerCase());
            st.setString(2, uuid);

            int idx = 3;
            if (isOffline && playerName != null && !playerName.isEmpty()) {
                st.setString(idx++, playerName.toLowerCase());
            }
            if (ip != null && !ip.isEmpty()) {
                st.setString(idx++, ip);
            }
            if (hwId != null && !hwId.isEmpty()) {
                st.setString(idx, hwId);
            }

            int rows = st.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[Punish] Failed to unpunish " + uuid, e);
            return false;
        }
    }

    /**
     * Deactivates a punishment by ID.
     */
    public static boolean unpunishById(int id) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "UPDATE punishments SET active = 0 WHERE id = ?")) {
            st.setInt(1, id);
            return st.executeUpdate() > 0;
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[Punish] Failed to unpunish id=" + id, e);
            return false;
        }
    }

    // =========================
    // ALL ACTIVE PUNISHMENTS (for /ui punish actionlist)
    // =========================

    /**
     * Returns ALL active punishments of every player, unified into one list:
     * bans/mutes/kicks from the {@code punishments} table and warns from the
     * {@code warns} table (warns live in a separate table).
     * Expired records are excluded. Newest first.
     */
    public static List<PunishmentRecord> getAllActivePunishments() {
        List<PunishmentRecord> result = new ArrayList<>();
        long now = System.currentTimeMillis();

        // bans / mutes / kicks
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement("""
                     SELECT * FROM punishments
                     WHERE active = 1 AND (expires_at = 0 OR expires_at > ?)
                     ORDER BY punished_at DESC
                     """)) {
            st.setLong(1, now);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRecord(rs));
                }
            }
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.FINE, "[Punish] Failed to list active punishments", e);
        }

        // warns (separate table)
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement("""
                     SELECT * FROM warns
                     WHERE (expires_at = 0 OR expires_at > ?)
                     ORDER BY warned_at DESC
                     """)) {
            st.setLong(1, now);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    result.add(new PunishmentRecord(
                            rs.getInt("id"),
                            PunishType.WARN,
                            rs.getString("player_uuid"),
                            rs.getString("player_name"),
                            rs.getString("reason"),
                            "",   // ipAddress — warns have no IP/HW scope
                            "",   // hwId
                            rs.getString("warned_by"),
                            rs.getLong("warned_at"),
                            rs.getLong("expires_at"),
                            true
                    ));
                }
            }
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.FINE, "[Punish] Failed to list active warns", e);
        }

        result.sort((a, b) -> Long.compare(b.punishedAt, a.punishedAt));
        return result;
    }

    // =========================
    // KICK CLEANUP
    // =========================

    /** Kicks older than this are purged from the database (milliseconds). */
    public static final long KICK_RETENTION_MS = 24L * 60 * 60 * 1000;

    /**
     * Deletes kick records older than {@link #KICK_RETENTION_MS} (24 hours) from the
     * database. Kicks are logged with {@code active=1} and never expire on their own,
     * so without this cleanup they would accumulate forever. Returns the number of
     * deleted rows (0 if nothing to clean).
     */
    public static int deleteOldKicks() {
        long cutoff = System.currentTimeMillis() - KICK_RETENTION_MS;
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "DELETE FROM punishments WHERE type = ? AND punished_at < ?")) {
            st.setString(1, PunishType.KICK.name().toLowerCase());
            st.setLong(2, cutoff);
            int rows = st.executeUpdate();
            if (rows > 0) {
                ConsoleLogger.info("[Punish] Purged " + rows + " kick record(s) older than 24h.");
            }
            return rows;
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[Punish] Failed to purge old kicks", e);
            return 0;
        }
    }

    // =========================
    // WARNS
    // =========================

    /**
     * Issues a warning to a player.
     */
    public static boolean warn(String targetUuid, String targetName, String reason,
                                String warner, long expiresAt) {
        long now = System.currentTimeMillis();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement("""
                     INSERT INTO warns (player_uuid, player_name, reason,
                         warned_by, warned_at, expires_at)
                     VALUES (?, ?, ?, ?, ?, ?)
                     """)) {
            st.setString(1, targetUuid);
            st.setString(2, targetName);
            st.setString(3, reason);
            st.setString(4, warner);
            st.setLong(5, now);
            st.setLong(6, expiresAt);
            st.executeUpdate();
            return true;
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[Punish] Failed to warn " + targetName, e);
            return false;
        }
    }

    /**
     * Deactivates a warning by its ID.
     * Sets expires_at = 1 (a past time) so the warn is no longer active.
     *
     * @param id the warning ID from the warns table
     * @return true on success
     */
    public static boolean removeWarnById(int id) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "UPDATE warns SET expires_at = 1 WHERE id = ?")) {
            st.setInt(1, id);
            int rows = st.executeUpdate();
            if (rows > 0) {
                ConsoleLogger.info("[Punish] Warn id=" + id + " removed.");
                return true;
            }
            return false;
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[Punish] Failed to remove warn id=" + id, e);
            return false;
        }
    }

    /**
     * Returns the player's list of active warnings.
     */
    public static List<WarnRecord> getActiveWarns(String uuid) {
        List<WarnRecord> result = new ArrayList<>();
        long now = System.currentTimeMillis();

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement("""
                     SELECT * FROM warns
                     WHERE player_uuid = ?
                     AND (expires_at = 0 OR expires_at > ?)
                     ORDER BY warned_at DESC
                     """)) {
            st.setString(1, uuid);
            st.setLong(2, now);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    result.add(new WarnRecord(
                            rs.getInt("id"),
                            rs.getString("player_uuid"),
                            rs.getString("player_name"),
                            rs.getString("reason"),
                            rs.getString("warned_by"),
                            rs.getLong("warned_at"),
                            rs.getLong("expires_at")
                    ));
                }
            }
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.FINE, "[Punish] Failed to list warns for " + uuid, e);
        }
        return result;
    }

    // =========================
    // KICK
    // =========================

    /**
     * Kicks a player from the server.
     */
    public static void kickPlayer(Player player, String reason, String kicker) {
        String message = PunishmentMessages.buildKickMessage(
                player.getName(), kicker, reason,
                PunishmentMessages.getDiscordUrl());
        player.kickPlayer(message);

        // Log the kick to the DB
        punish(PunishType.KICK, player.getUniqueId().toString(), player.getName(),
                reason, kicker, 0, null, null);
    }

    // =========================
    // ACTIVE PUNISHMENT CHECK FOR LOGIN
    // =========================

    /**
     * Checks whether the player is banned and returns the ban record if so.
     */
    public static PunishmentRecord getActiveBan(String uuid, String ip, String hwId) {
        return getActivePunishment(PunishType.BAN, uuid, ip, hwId);
    }

    /**
     * Checks whether the player is muted and returns the mute record if so.
     */
    public static PunishmentRecord getActiveMute(String uuid, String ip, String hwId) {
        return getActivePunishment(PunishType.MUTE, uuid, ip, hwId);
    }

    // =========================
    // FIND & KICK PLAYERS BY IP/HW (for punish with -ip/-hw flags)
    // =========================

    /**
     * Finds all online players matching the IP/HW criteria.
     */
    public static List<Player> findPlayersByIpOrHw(String ip, String hwId) {
        List<Player> result = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            String pIp = p.getAddress() != null ? p.getAddress().getAddress().getHostAddress() : "";
            String pHw = computeHwId(pIp, p.getName());

            if (ip != null && !ip.isEmpty() && pIp.equals(ip)) {
                result.add(p);
            } else if (hwId != null && !hwId.isEmpty() && pHw.equals(hwId)) {
                result.add(p);
            }
        }
        return result;
    }

    // =========================
    // MUTE CHECK UTILITY
    // =========================

    /**
     * Checks whether the player has an active mute.
     * Convenient to call from a chat listener.
     */
    public static boolean isMuted(String uuid, String ip, String hwId) {
        return getActiveMute(uuid, ip, hwId) != null;
    }

    // =========================
    // RECORDS
    // =========================

    public static class PunishmentRecord {
        public final int id;
        public final PunishType type;
        public final String playerUuid;
        public final String playerName;
        public final String reason;
        public final String ipAddress;
        public final String hwId;
        public final String punishedBy;
        public final long punishedAt;
        public final long expiresAt; // 0 = permanent
        public final boolean active;

        public PunishmentRecord(int id, PunishType type, String playerUuid, String playerName,
                                 String reason, String ipAddress, String hwId,
                                 String punishedBy, long punishedAt, long expiresAt, boolean active) {
            this.id = id;
            this.type = type;
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.reason = reason;
            this.ipAddress = ipAddress;
            this.hwId = hwId;
            this.punishedBy = punishedBy;
            this.punishedAt = punishedAt;
            this.expiresAt = expiresAt;
            this.active = active;
        }

        public boolean isPermanent() {
            return expiresAt == 0;
        }

        public boolean isExpired() {
            return expiresAt > 0 && System.currentTimeMillis() > expiresAt;
        }

        public long getRemainingMs() {
            if (isPermanent()) return -1;
            return Math.max(0, expiresAt - System.currentTimeMillis());
        }

        public boolean isIpScope() {
            return ipAddress != null && !ipAddress.isEmpty();
        }

        public boolean isHwScope() {
            return hwId != null && !hwId.isEmpty();
        }
    }

    private static PunishmentRecord mapRecord(ResultSet rs) throws SQLException {
        return new PunishmentRecord(
                rs.getInt("id"),
                PunishType.valueOf(rs.getString("type").toUpperCase()),
                rs.getString("player_uuid"),
                rs.getString("player_name"),
                rs.getString("reason"),
                rs.getString("ip_address"),
                rs.getString("hw_id"),
                rs.getString("punished_by"),
                rs.getLong("punished_at"),
                rs.getLong("expires_at"),
                rs.getInt("active") == 1
        );
    }

    public static class WarnRecord {
        public final int id;
        public final String playerUuid;
        public final String playerName;
        public final String reason;
        public final String warnedBy;
        public final long warnedAt;
        public final long expiresAt;

        public WarnRecord(int id, String playerUuid, String playerName, String reason,
                           String warnedBy, long warnedAt, long expiresAt) {
            this.id = id;
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.reason = reason;
            this.warnedBy = warnedBy;
            this.warnedAt = warnedAt;
            this.expiresAt = expiresAt;
        }

        public boolean isPermanent() {
            return expiresAt == 0;
        }

        public String formatRemaining() {
            if (isPermanent()) return "permanent";
            long remaining = expiresAt - System.currentTimeMillis();
            if (remaining <= 0) return "expired";
            return PunishmentManager.formatRemaining(remaining);
        }
    }

    /**
     * Formats a remaining duration as a compact string: 45s / 2m 30s / 5h 10m / 3d 4h.
     * Shared by {@link WarnRecord#formatRemaining()} and the actionlist view.
     */
    public static String formatRemaining(long remainingMs) {
        long secs = remainingMs / 1000;
        if (secs < 60) return secs + "s";
        long mins = secs / 60;
        if (mins < 60) return mins + "m " + (secs % 60) + "s";
        long hours = mins / 60;
        if (hours < 24) return hours + "h " + (mins % 60) + "m";
        long days = hours / 24;
        return days + "d " + (hours % 24) + "h";
    }
}
