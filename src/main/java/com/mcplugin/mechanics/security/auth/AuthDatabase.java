package com.mcplugin.mechanics.security.auth;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.database.DatabaseManager;
import com.mcplugin.infrastructure.util.ConsoleLogger;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

public class AuthDatabase {

    // =========================
    // ARGON2ID (primary)
    // =========================
    // Parameters: 2 iterations, 32 MB memory, 1 thread
    // These are tuned for reasonable speed in a Minecraft server environment.
    private static final int ARGON2_ITERATIONS = 2;
    private static final int ARGON2_MEMORY_KIB = 32768;  // 32 MB
    private static final int ARGON2_PARALLELISM = 1;

    private static final Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);

    // =========================
    // PBKDF2 (legacy, for migration)
    // =========================
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int PBKDF2_ITERATIONS = 65536;
    private static final int PBKDF2_KEY_LENGTH = 256;

    private static volatile boolean tableReady = false;

    // =========================
    // IS TABLE READY
    // =========================
    public static boolean isTableReady() {
        return tableReady;
    }

    // =========================
    // INIT TABLE
    // =========================
    public static void initTable() {
        try (Connection con = DatabaseManager.getConnection();
             var st = con.createStatement()) {

            st.execute("""
                CREATE TABLE IF NOT EXISTS auth (
                    uuid TEXT PRIMARY KEY,
                    password_hash TEXT NOT NULL,
                    salt TEXT NOT NULL,
                    last_login INTEGER DEFAULT 0,
                    ip_address TEXT DEFAULT ''
                );
            """);

            // Migration for old databases without last_login column
            try {
                st.execute("ALTER TABLE auth ADD COLUMN last_login INTEGER DEFAULT 0");
            } catch (Exception ignored) {}

            // Migration for old databases without ip_address column
            try {
                st.execute("ALTER TABLE auth ADD COLUMN ip_address TEXT DEFAULT ''");
            } catch (Exception ignored) {}

            tableReady = true;

        } catch (Exception e) {
            ConsoleLogger.error("[Auth] DB init failed: " + e.getMessage());
            ConsoleLogger.error("[Auth] DB init stack: " + java.util.Arrays.toString(e.getStackTrace()));
            tableReady = false;
        }
    }

    // =========================
    // IS REGISTERED
    // =========================
    public static boolean isRegistered(UUID uuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT 1 FROM auth WHERE uuid = ?")) {

            ps.setString(1, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }

        } catch (Exception e) {
            ConsoleLogger.error("[Auth] Check failed: " + e.getMessage());
            return false;
        }
    }

    // =========================
    // HAS VALID SESSION (within session duration)
    // =========================
    public static boolean hasValidSession(UUID uuid, long sessionDurationMs) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT last_login FROM auth WHERE uuid = ?")) {

            ps.setString(1, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;

                long lastLogin = rs.getLong("last_login");
                if (lastLogin <= 0) return false;

                return (System.currentTimeMillis() - lastLogin) < sessionDurationMs;
            }

        } catch (Exception e) {
            ConsoleLogger.error("[Auth] Session check failed: " + e.getMessage());
            return false;
        }
    }

    // =========================
    // GET LAST IP
    // =========================
    public static String getLastIp(UUID uuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT ip_address FROM auth WHERE uuid = ?")) {

            ps.setString(1, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return "";
                String ip = rs.getString("ip_address");
                return ip != null ? ip : "";
            }

        } catch (Exception e) {
            ConsoleLogger.error("[Auth] Get IP failed: " + e.getMessage());
            return "";
        }
    }

    // =========================
    // UPDATE LAST IP
    // =========================
    public static void updateLastIp(UUID uuid, String ip) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE auth SET ip_address = ? WHERE uuid = ?")) {

            ps.setString(1, ip);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();

        } catch (Exception e) {
            ConsoleLogger.error("[Auth] Update IP failed: " + e.getMessage());
        }
    }

    // =========================
    // UPDATE LAST LOGIN
    // =========================
    public static void updateLastLogin(UUID uuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE auth SET last_login = ? WHERE uuid = ?")) {

            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, uuid.toString());
            ps.executeUpdate();

        } catch (Exception e) {
            ConsoleLogger.error("[Auth] Update last_login failed: " + e.getMessage());
        }
    }

    // =========================
    // REGISTER
    // =========================
    public static void register(UUID uuid, String password, String ip) {
        String hash = hashArgon2(password);

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT OR REPLACE INTO auth (uuid, password_hash, salt, last_login, ip_address) VALUES (?, ?, ?, ?, ?)")) {

            ps.setString(1, uuid.toString());
            ps.setString(2, hash);
            ps.setString(3, "");  // Argon2id hash is self-contained (salt embedded)
            ps.setLong(4, System.currentTimeMillis());
            ps.setString(5, ip);
            ps.executeUpdate();

        } catch (Exception e) {
            ConsoleLogger.error("[Auth] Register failed: " + e.getMessage());
            ConsoleLogger.error("[Auth] Register stack: " + java.util.Arrays.toString(e.getStackTrace()));
        }
    }

    // =========================
    // CHECK PASSWORD
    // Supports both Argon2id (new) and PBKDF2 (legacy, auto-upgrades)
    // =========================
    public static boolean checkPassword(UUID uuid, String password) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT password_hash, salt FROM auth WHERE uuid = ?")) {

            ps.setString(1, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;

                String storedHash = rs.getString("password_hash");
                String salt = rs.getString("salt");

                // Argon2id hash — self-contained format: $argon2id$v=19$...
                if (storedHash != null && storedHash.startsWith("$argon2id$")) {
                    boolean valid = verifyArgon2(storedHash, password);
                    return valid;
                }

                // Legacy PBKDF2 hash — verify and auto-upgrade to Argon2id
                if (salt != null && !salt.isEmpty() && verifyPbkdf2(password, salt, storedHash)) {
                    // Auto-upgrade: re-hash with Argon2id on successful login
                    upgradeToArgon2(uuid, password);
                    return true;
                }

                return false;
            }

        } catch (Exception e) {
            ConsoleLogger.error("[Auth] Check password failed: " + e.getMessage());
            return false;
        }
    }

    // =========================
    // CHANGE PASSWORD (admin command)
    // =========================
    public static boolean changePassword(UUID uuid, String newPassword) {
        String hash = hashArgon2(newPassword);

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE auth SET password_hash = ?, salt = ?, last_login = 0, ip_address = '' WHERE uuid = ?")) {

            ps.setString(1, hash);
            ps.setString(2, "");
            ps.setString(3, uuid.toString());
            int updated = ps.executeUpdate();
            return updated > 0;

        } catch (Exception e) {
            ConsoleLogger.error("[Auth] Change password failed: " + e.getMessage());
            return false;
        }
    }

    // =========================
    // RESET AUTH (logout) — removes authenticated session, keeps registration
    // =========================
    public static boolean resetAuth(UUID uuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE auth SET last_login = 0, ip_address = '' WHERE uuid = ?")) {

            ps.setString(1, uuid.toString());
            int updated = ps.executeUpdate();
            return updated > 0;

        } catch (Exception e) {
            ConsoleLogger.error("[Auth] Reset auth failed: " + e.getMessage());
            return false;
        }
    }

    // =========================
    // CHANGE PASSWORD SELF (player self-service)
    // =========================
    public static boolean changePasswordSelf(UUID uuid, String newPassword) {
        String hash = hashArgon2(newPassword);

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE auth SET password_hash = ?, salt = ?, last_login = ? WHERE uuid = ?")) {

            ps.setString(1, hash);
            ps.setString(2, "");
            ps.setLong(3, System.currentTimeMillis());
            ps.setString(4, uuid.toString());
            int updated = ps.executeUpdate();
            return updated > 0;

        } catch (Exception e) {
            ConsoleLogger.error("[Auth] Self change password failed: " + e.getMessage());
            return false;
        }
    }

    // =========================
    // GET ALL REGISTERED UUIDs
    // =========================
    public static List<UUID> getAllRegisteredUuids() {
        List<UUID> uuids = new ArrayList<>();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT uuid FROM auth");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                try {
                    uuids.add(UUID.fromString(rs.getString("uuid")));
                } catch (Exception ignored) {}
            }

        } catch (Exception e) {
            ConsoleLogger.error("[Auth] Get all UUIDs failed: " + e.getMessage());
        }
        return uuids;
    }

    // =========================
    // COUNT ACCOUNTS BY IP
    // =========================
    public static int countAccountsByIp(String ip) {
        if (ip == null || ip.isEmpty()) return 0;

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COUNT(*) FROM auth WHERE ip_address = ?")) {

            ps.setString(1, ip);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }

        } catch (Exception e) {
            ConsoleLogger.error("[Auth] Count by IP failed: " + e.getMessage());
        }
        return 0;
    }

    // =========================
    // DELETE REGISTRATION
    // =========================
    public static boolean deleteRegistration(UUID uuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM auth WHERE uuid = ?")) {

            ps.setString(1, uuid.toString());
            int deleted = ps.executeUpdate();
            return deleted > 0;

        } catch (Exception e) {
            ConsoleLogger.error("[Auth] Delete registration failed: " + e.getMessage());
            return false;
        }
    }

    // =========================
    // ARGON2ID HASHING
    // =========================
    private static String hashArgon2(String password) {
        try {
            char[] chars = password.toCharArray();
            String hash = argon2.hash(ARGON2_ITERATIONS, ARGON2_MEMORY_KIB, ARGON2_PARALLELISM, chars);
            argon2.wipeArray(chars);
            return hash;
        } catch (Exception e) {
            ConsoleLogger.error("[Auth] Argon2 hash failed: " + e.getMessage());
            return "";
        }
    }

    private static boolean verifyArgon2(String hash, String password) {
        try {
            char[] chars = password.toCharArray();
            boolean valid = argon2.verify(hash, chars);
            argon2.wipeArray(chars);
            return valid;
        } catch (Exception e) {
            ConsoleLogger.error("[Auth] Argon2 verify failed: " + e.getMessage());
            return false;
        }
    }

    // =========================
    // PBKDF2 (LEGACY — for migration only)
    // =========================
    private static String hashPbkdf2(String password, String salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(
                    password.toCharArray(),
                    salt.getBytes(StandardCharsets.UTF_8),
                    PBKDF2_ITERATIONS,
                    PBKDF2_KEY_LENGTH
            );
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            ConsoleLogger.error("[Auth] PBKDF2 hash failed: " + e.getMessage());
            return "";
        }
    }

    private static boolean verifyPbkdf2(String password, String salt, String storedHash) {
        if (salt == null || storedHash == null) return false;
        String computedHash = hashPbkdf2(password, salt);
        return storedHash.equals(computedHash);
    }

    // =========================
    // AUTO-UPGRADE: PBKDF2 → Argon2id
    // =========================
    private static void upgradeToArgon2(UUID uuid, String password) {
        try {
            String newHash = hashArgon2(password);
            try (Connection con = DatabaseManager.getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         "UPDATE auth SET password_hash = ?, salt = ? WHERE uuid = ?")) {

                ps.setString(1, newHash);
                ps.setString(2, "");
                ps.setString(3, uuid.toString());
                ps.executeUpdate();

                ConsoleLogger.info("[Auth] Upgraded password to Argon2id for UUID: " + uuid);
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[Auth] Auto-upgrade to Argon2id failed for UUID " + uuid + ": " + e.getMessage());
        }
    }
}
