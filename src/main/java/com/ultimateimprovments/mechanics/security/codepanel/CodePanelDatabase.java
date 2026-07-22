package com.ultimateimprovments.mechanics.security.codepanel;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.util.ConsoleLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Работа с таблицей code_panel_keys в SQLite.
 * Все ключи теперь хранятся в БД, а не в config.yml.
 */
public class CodePanelDatabase {

    // =========================
    // DATA CLASS
    // =========================
    public static class CodePanelKey {
        public String keyName;
        public String code;
        public String command;
        public int maxAttempts;    // -1 = без лимита
        public int attemptsUsed;
        public long expiresAt;     // 0 = не истекает
        public List<String> whitelist;
        public List<String> blacklist;

        public boolean isExpired() {
            return expiresAt > 0 && System.currentTimeMillis() >= expiresAt;
        }

        public boolean isMaxAttemptsReached() {
            return maxAttempts > 0 && attemptsUsed >= maxAttempts;
        }

        public boolean isPlayerAllowed(String playerName) {
            if (!whitelist.isEmpty()) {
                return whitelist.contains(playerName.toLowerCase());
            }
            if (!blacklist.isEmpty()) {
                return !blacklist.contains(playerName.toLowerCase());
            }
            return true; // нет ограничений
        }

        // Парсит "player1,player2" или "(player1,player2)" в список
        private static List<String> parseNameList(String str) {
            List<String> result = new ArrayList<>();
            if (str == null || str.isBlank()) return result;
            // Strip wrapping parentheses if present: (value) -> value
            String cleaned = str.trim();
            if (cleaned.startsWith("(") && cleaned.endsWith(")")) {
                cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
            }
            if (cleaned.isEmpty()) return result;
            for (String part : cleaned.split(",")) {
                String trimmed = part.trim().toLowerCase();
                if (!trimmed.isEmpty()) result.add(trimmed);
            }
            return result;
        }

        public static CodePanelKey fromResultSet(ResultSet rs) throws Exception {
            CodePanelKey key = new CodePanelKey();
            key.keyName = rs.getString("key_name");
            key.code = rs.getString("code");
            key.command = rs.getString("command");
            key.maxAttempts = rs.getInt("max_attempts");
            key.attemptsUsed = rs.getInt("attempts_used");
            key.expiresAt = rs.getLong("expires_at");
            key.whitelist = parseNameList(rs.getString("whitelist"));
            key.blacklist = parseNameList(rs.getString("blacklist"));
            return key;
        }
    }

    // =========================
    // CREATE TABLE (если вдруг не создалась в DatabaseInit)
    // =========================
    public static void initTable() {
        try (Connection con = DatabaseManager.getConnection();
             var st = con.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS code_panel_keys (
                    key_name TEXT PRIMARY KEY,
                    code TEXT NOT NULL,
                    command TEXT NOT NULL DEFAULT '',
                    max_attempts INTEGER DEFAULT -1,
                    attempts_used INTEGER DEFAULT 0,
                    expires_at INTEGER DEFAULT 0,
                    whitelist TEXT DEFAULT '',
                    blacklist TEXT DEFAULT ''
                );
            """);
        } catch (Exception e) {
            ConsoleLogger.error("[CodePanel] DB init failed: " + e.getMessage());
        }
    }

    // =========================
    // ADD KEY
    // =========================
    public static boolean addKey(String keyName, String code, String command,
                                  int maxAttempts, long expiresAt,
                                  String whitelistStr, String blacklistStr) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                INSERT OR REPLACE INTO code_panel_keys
                (key_name, code, command, max_attempts, attempts_used, expires_at, whitelist, blacklist)
                VALUES (?, ?, ?, ?, 0, ?, ?, ?)
             """)) {
            ps.setString(1, keyName);
            ps.setString(2, code);
            ps.setString(3, command);
            ps.setInt(4, maxAttempts);
            ps.setLong(5, expiresAt);
            ps.setString(6, whitelistStr != null ? whitelistStr : "");
            ps.setString(7, blacklistStr != null ? blacklistStr : "");
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            ConsoleLogger.error("[CodePanel] Add key failed: " + e.getMessage());
            return false;
        }
    }

    // =========================
    // REMOVE KEY
    // =========================
    public static boolean removeKey(String keyName) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM code_panel_keys WHERE key_name = ?")) {
            ps.setString(1, keyName);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            ConsoleLogger.error("[CodePanel] Remove key failed: " + e.getMessage());
            return false;
        }
    }

    // =========================
    // UPDATE KEY
    // =========================
    public static boolean updateKey(String keyName, String code, String command,
                                     int maxAttempts, long expiresAt,
                                     String whitelistStr, String blacklistStr) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                UPDATE code_panel_keys SET
                    code = ?, command = ?,
                    max_attempts = ?, attempts_used = 0,
                    expires_at = ?,
                    whitelist = ?, blacklist = ?
                WHERE key_name = ?
             """)) {
            ps.setString(1, code);
            ps.setString(2, command);
            ps.setInt(3, maxAttempts);
            ps.setLong(4, expiresAt);
            ps.setString(5, whitelistStr != null ? whitelistStr : "");
            ps.setString(6, blacklistStr != null ? blacklistStr : "");
            ps.setString(7, keyName);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            ConsoleLogger.error("[CodePanel] Update key failed: " + e.getMessage());
            return false;
        }
    }

    // =========================
    // EXISTS
    // =========================
    public static boolean keyExists(String keyName) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT 1 FROM code_panel_keys WHERE key_name = ?")) {
            ps.setString(1, keyName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            return false;
        }
    }

    // =========================
    // GET ALL KEY NAMES
    // =========================
    public static List<String> getAllKeyNames() {
        List<String> names = new ArrayList<>();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT key_name FROM code_panel_keys ORDER BY key_name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                names.add(rs.getString("key_name"));
            }
        } catch (Exception e) {
            ConsoleLogger.error("[CodePanel] Get key names failed: " + e.getMessage());
        }
        return names;
    }

    // =========================
    // GET ALL KEYS (for code checking)
    // =========================
    public static List<CodePanelKey> getAllKeys() {
        List<CodePanelKey> keys = new ArrayList<>();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT * FROM code_panel_keys ORDER BY key_name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                keys.add(CodePanelKey.fromResultSet(rs));
            }
        } catch (Exception e) {
            ConsoleLogger.error("[CodePanel] Get all keys failed: " + e.getMessage());
        }
        return keys;
    }

    // =========================
    // GET KEY BY NAME
    // =========================
    public static CodePanelKey getKey(String keyName) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT * FROM code_panel_keys WHERE key_name = ?")) {
            ps.setString(1, keyName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return CodePanelKey.fromResultSet(rs);
                }
            }
        } catch (Exception e) {
            ConsoleLogger.error("[CodePanel] Get key failed: " + e.getMessage());
        }
        return null;
    }

    // =========================
    // INCREMENT ATTEMPTS USED
    // =========================
    public static void incrementAttemptsUsed(String keyName) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE code_panel_keys SET attempts_used = attempts_used + 1 WHERE key_name = ?")) {
            ps.setString(1, keyName);
            ps.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.error("[CodePanel] Increment attempts failed: " + e.getMessage());
        }
    }

    // =========================
    // CLEANUP EXPIRED KEYS — удаляет ключи с истёкшим expires_at ИЛИ с превышенными попытками
    // =========================
    public static List<String> cleanupExpiredKeys() {
        List<String> removed = new ArrayList<>();
        long now = System.currentTimeMillis();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                DELETE FROM code_panel_keys
                WHERE (expires_at > 0 AND expires_at <= ?)
                   OR (max_attempts > 0 AND attempts_used >= max_attempts)
             """)) {
            ps.setLong(1, now);
            // Сначала выберем удаляемые ключи для лога
            try (PreparedStatement sel = con.prepareStatement("""
                    SELECT key_name FROM code_panel_keys
                    WHERE (expires_at > 0 AND expires_at <= ?)
                       OR (max_attempts > 0 AND attempts_used >= max_attempts)
                 """)) {
                sel.setLong(1, now);
                try (ResultSet rs = sel.executeQuery()) {
                    while (rs.next()) {
                        removed.add(rs.getString("key_name"));
                    }
                }
            }
            ps.executeUpdate();
            for (String name : removed) {
                ConsoleLogger.info("[CodePanel] Cleaned up key: " + name);
            }
        } catch (Exception e) {
            ConsoleLogger.error("[CodePanel] Cleanup failed: " + e.getMessage());
        }
        return removed;
    }
}
