package com.mcplugin.infrastructure.punish;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.database.DatabaseManager;
import com.mcplugin.infrastructure.util.MessageUtil;
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
 * 🛡 PunishmentManager — система наказаний (бан/мут/кик/варн).
 * <p>
 * Поддерживает флаги:
 * <ul>
 *   <li>{@code -time:<N>s|m|h|d} — временное наказание</li>
 *   <li>{@code -permanent} — перманентное наказание</li>
 *   <li>{@code -ip} — наказание по IP</li>
 *   <li>{@code -hw} — наказание по железу (IP + хост)</li>
 * </ul>
 * <p>
 * -ip и -hw несовместимы. -time и -permanent несовместимы.
 * <p>
 * HW ID = SHA256(IP + имя_игрока), что позволяет банить "железо"
 * независимо от аккаунта.
 */
public class PunishmentManager {

    private static final String HW_SALT = "MC-Plugin-HW-FINGERPRINT";
    private static final long MS_PER_S = 1000L;
    private static final long MS_PER_M = MS_PER_S * 60;
    private static final long MS_PER_H = MS_PER_M * 60;
    private static final long MS_PER_D = MS_PER_H * 24;

    // =========================
    // HW ID
    // =========================

    /**
     * Вычисляет HW ID для игрока: SHA256(IP + имя + соль).
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
            Main.getInstance().getLogger().warning("[Punish] SHA-256 not available!");
            return ip != null ? ip : "unknown";
        }
    }

    // =========================
    // PARSE TIME FLAG (-time:<N>s|m|h|d)
    // =========================

    /**
     * Парсит флаг времени. Возвращает unix timestamp истечения (millis) или 0 если permanent.
     *
     * @param timeStr строка вида "30s", "5m", "2h", "7d"
     * @return unixMillis истечения, 0 если ошибка
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
    // PUNISH — общий метод
    // =========================

    /**
     * Применяет наказание к игроку.
     *
     * @param type      тип наказания
     * @param targetUuid  UUID цели
     * @param targetName  имя цели
     * @param reason      причина
     * @param punisher    кто наказывает
     * @param expiresAt   unixMillis истечения (0 = permanent)
     * @param ip          IP для IP-бана (null если не IP)
     * @param hwId        HW ID для HW-бана (null если не HW)
     * @return true если успешно
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

            Main.getInstance().getLogger().info("[Punish] " + type.name() + " " + targetName
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
     * Проверяет, есть ли активное наказание данного типа для игрока.
     * Проверяет по UUID, а если указан IP/HW — то и по ним.
     *
     * @param type     тип наказания
     * @param uuid     UUID игрока
     * @param ip       IP игрока (может быть null)
     * @param hwId     HW ID игрока (может быть null)
     * @return запись о наказании или null
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
    // UNPUNISH (снять наказание)
    // =========================

    /**
     * Деактивирует все активные наказания данного типа для UUID/IP/HW.
     */
    public static boolean unpunish(PunishType type, String uuid, String ip, String hwId) {
        StringBuilder sql = new StringBuilder("""
                UPDATE punishments SET active = 0
                WHERE type = ? AND active = 1
                AND (player_uuid = ?
                """);

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
     * Деактивирует наказание по ID.
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
    // WARNS
    // =========================

    /**
     * Выдаёт предупреждение игроку.
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
     * Возвращает список активных предупреждений игрока.
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
     * Кикает игрока с сервера.
     */
    public static void kickPlayer(Player player, String reason, String kicker) {
        String message = MessageUtil.legacy(
                "<red>⛔ You have been kicked!</red>\n" +
                "<gray>Reason:</gray> <white>" + reason + "</white>\n" +
                "<dark_gray>By: " + kicker + "</dark_gray>"
        );
        player.kickPlayer(message);

        // Логируем кик в БД
        punish(PunishType.KICK, player.getUniqueId().toString(), player.getName(),
                reason, kicker, 0, null, null);
    }

    // =========================
    // ACTIVE PUNISHMENT CHECK FOR LOGIN
    // =========================

    /**
     * Проверяет, забанен ли игрок, и возвращает запись бана если да.
     */
    public static PunishmentRecord getActiveBan(String uuid, String ip, String hwId) {
        return getActivePunishment(PunishType.BAN, uuid, ip, hwId);
    }

    /**
     * Проверяет, замучен ли игрок, и возвращает запись мута если да.
     */
    public static PunishmentRecord getActiveMute(String uuid, String ip, String hwId) {
        return getActivePunishment(PunishType.MUTE, uuid, ip, hwId);
    }

    // =========================
    // FIND & KICK PLAYERS BY IP/HW (для punish с флагами -ip/-hw)
    // =========================

    /**
     * Находит всех онлайн-игроков, подходящих под IP/HW критерии.
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
     * Проверяет, есть ли у игрока активный мут.
     * Удобно вызывать из чат-листенера.
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
            long secs = remaining / 1000;
            if (secs < 60) return secs + "s";
            long mins = secs / 60;
            if (mins < 60) return mins + "m " + (secs % 60) + "s";
            long hours = mins / 60;
            if (hours < 24) return hours + "h " + (mins % 60) + "m";
            long days = hours / 24;
            return days + "d " + (hours % 24) + "h";
        }
    }
}
