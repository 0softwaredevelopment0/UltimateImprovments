package com.ultimateimprovments.mechanics.security.sudo;

import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.util.ConsoleLogger;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/**
 * SudoDatabase — хранение sudo-паролей администраторов.
 * <p>
 * Отдельная таблица {@code sudo_auth} (не зависит от системы auth — sudo работает
 * даже если авторизация отключена или заменена другим плагином).
 * Хэширование — Argon2id, идентично {@code AuthDatabase}.
 */
public class SudoDatabase {

    // =========================
    // ARGON2ID
    // =========================
    private static final int ARGON2_ITERATIONS = 2;
    private static final int ARGON2_MEMORY_KIB = 32768;  // 32 MB
    private static final int ARGON2_PARALLELISM = 1;

    private static final Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);

    private static volatile boolean tableReady = false;

    private SudoDatabase() {}

    // =========================
    // INIT TABLE
    // =========================
    public static void initTable() {
        try (Connection con = DatabaseManager.getConnection();
             var st = con.createStatement()) {

            st.execute("""
                CREATE TABLE IF NOT EXISTS sudo_auth (
                    uuid TEXT PRIMARY KEY,
                    password_hash TEXT NOT NULL,
                    salt TEXT NOT NULL
                );
            """);

            tableReady = true;

        } catch (Exception e) {
            ConsoleLogger.error("[Sudo] DB init failed: " + e.getMessage());
            tableReady = false;
        }
    }

    public static boolean isTableReady() {
        return tableReady;
    }

    // =========================
    // IS REGISTERED
    // =========================
    public static boolean isRegistered(UUID uuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT 1 FROM sudo_auth WHERE uuid = ?")) {

            ps.setString(1, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }

        } catch (Exception e) {
            ConsoleLogger.error("[Sudo] Check failed: " + e.getMessage());
            return false;
        }
    }

    // =========================
    // REGISTER (INSERT OR REPLACE)
    // =========================
    public static boolean register(UUID uuid, String password) {
        String hash = hashArgon2(password);
        if (hash == null || hash.isEmpty()) return false;

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT OR REPLACE INTO sudo_auth (uuid, password_hash, salt) VALUES (?, ?, ?)")) {

            ps.setString(1, uuid.toString());
            ps.setString(2, hash);
            ps.setString(3, "");  // Argon2id salt embedded in hash
            ps.executeUpdate();
            return true;

        } catch (Exception e) {
            ConsoleLogger.error("[Sudo] Register failed: " + e.getMessage());
            return false;
        }
    }

    // =========================
    // CHECK PASSWORD
    // =========================
    public static boolean checkPassword(UUID uuid, String password) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT password_hash FROM sudo_auth WHERE uuid = ?")) {

            ps.setString(1, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;

                String storedHash = rs.getString("password_hash");
                if (storedHash == null || !storedHash.startsWith("$argon2id$")) return false;

                return verifyArgon2(storedHash, password);
            }

        } catch (Exception e) {
            ConsoleLogger.error("[Sudo] Check password failed: " + e.getMessage());
            return false;
        }
    }

    // =========================
    // DELETE (сброс пароля)
    // =========================
    public static boolean deleteRegistration(UUID uuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM sudo_auth WHERE uuid = ?")) {

            ps.setString(1, uuid.toString());
            return ps.executeUpdate() > 0;

        } catch (Exception e) {
            ConsoleLogger.error("[Sudo] Delete failed: " + e.getMessage());
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
            ConsoleLogger.error("[Sudo] Argon2 hash failed: " + e.getMessage());
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
            ConsoleLogger.error("[Sudo] Argon2 verify failed: " + e.getMessage());
            return false;
        }
    }
}
