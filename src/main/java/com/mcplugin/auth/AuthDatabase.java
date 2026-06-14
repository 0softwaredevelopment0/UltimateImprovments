package com.mcplugin.auth;

import com.mcplugin.Main;
import com.mcplugin.database.DatabaseManager;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

public class AuthDatabase {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 65536;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;

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
            Main.getInstance().getLogger().severe("[Auth] DB init failed: " + e.getMessage());
            e.printStackTrace();
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
            Main.getInstance().getLogger().severe("[Auth] Check failed: " + e.getMessage());
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
            Main.getInstance().getLogger().severe("[Auth] Session check failed: " + e.getMessage());
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
            Main.getInstance().getLogger().severe("[Auth] Get IP failed: " + e.getMessage());
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
            Main.getInstance().getLogger().severe("[Auth] Update IP failed: " + e.getMessage());
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
            Main.getInstance().getLogger().severe("[Auth] Update last_login failed: " + e.getMessage());
        }
    }

    // =========================
    // REGISTER
    // =========================
    public static void register(UUID uuid, String password, String ip) {
        String salt = generateSalt();
        String hash = hashPassword(password, salt);            try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT OR REPLACE INTO auth (uuid, password_hash, salt, last_login, ip_address) VALUES (?, ?, ?, ?, ?)")) {

            ps.setString(1, uuid.toString());
            ps.setString(2, hash);
            ps.setString(3, salt);
            ps.setLong(4, System.currentTimeMillis()); // set last_login on register too
            ps.setString(5, ip);
            ps.executeUpdate();

        } catch (Exception e) {
            Main.getInstance().getLogger().severe("[Auth] Register failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =========================
    // CHECK PASSWORD
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
                String computedHash = hashPassword(password, salt);

                return storedHash.equals(computedHash);
            }

        } catch (Exception e) {
            Main.getInstance().getLogger().severe("[Auth] Check password failed: " + e.getMessage());
            return false;
        }
    }

    // =========================
    // CHANGE PASSWORD (admin command)
    // =========================
    public static boolean changePassword(UUID uuid, String newPassword) {
        String salt = generateSalt();
        String hash = hashPassword(newPassword, salt);            try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE auth SET password_hash = ?, salt = ?, last_login = 0, ip_address = '' WHERE uuid = ?")) {

            ps.setString(1, hash);
            ps.setString(2, salt);
            ps.setString(3, uuid.toString());
            int updated = ps.executeUpdate();
            return updated > 0;

        } catch (Exception e) {
            Main.getInstance().getLogger().severe("[Auth] Change password failed: " + e.getMessage());
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
            Main.getInstance().getLogger().severe("[Auth] Reset auth failed: " + e.getMessage());
            return false;
        }
    }

    // =========================
    // CHANGE PASSWORD SELF (player self-service) — сохраняет last_login и IP
    // =========================
    public static boolean changePasswordSelf(UUID uuid, String newPassword) {
        String salt = generateSalt();
        String hash = hashPassword(newPassword, salt);            try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE auth SET password_hash = ?, salt = ?, last_login = ? WHERE uuid = ?")) {

            ps.setString(1, hash);
            ps.setString(2, salt);
            ps.setLong(3, System.currentTimeMillis());
            ps.setString(4, uuid.toString());
            int updated = ps.executeUpdate();
            return updated > 0;

        } catch (Exception e) {
            Main.getInstance().getLogger().severe("[Auth] Self change password failed: " + e.getMessage());
            return false;
        }
    }

    // =========================
    // GET ALL REGISTERED UUIDs (для tab complete)
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
            Main.getInstance().getLogger().severe("[Auth] Get all UUIDs failed: " + e.getMessage());
        }
        return uuids;
    }

    // =========================
    // COUNT ACCOUNTS BY IP — сколько аккаунтов зарегистрировано с данного IP
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
            Main.getInstance().getLogger().severe("[Auth] Count by IP failed: " + e.getMessage());
        }
        return 0;
    }

    // =========================
    // DELETE REGISTRATION — полностью удаляет запись из БД
    // =========================
    public static boolean deleteRegistration(UUID uuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM auth WHERE uuid = ?")) {

            ps.setString(1, uuid.toString());
            int deleted = ps.executeUpdate();
            return deleted > 0;

        } catch (Exception e) {
            Main.getInstance().getLogger().severe("[Auth] Delete registration failed: " + e.getMessage());
            return false;
        }
    }

    // =========================
    // HASH PASSWORD (PBKDF2 + SHA256)
    // =========================
    private static String hashPassword(String password, String salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(
                    password.toCharArray(),
                    salt.getBytes(StandardCharsets.UTF_8),
                    ITERATIONS,
                    KEY_LENGTH
            );
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            Main.getInstance().getLogger().severe("[Auth] Hash failed: " + e.getMessage());
            return "";
        }
    }

    // =========================
    // GENERATE SALT
    // =========================
    private static String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        return HexFormat.of().formatHex(salt);
    }
}
