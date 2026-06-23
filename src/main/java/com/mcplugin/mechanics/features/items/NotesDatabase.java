package com.mcplugin.mechanics.features.items;

import com.mcplugin.infrastructure.core.Main;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Работа с таблицей notes в SQLite.
 * Хранит заметки игроков: player_uuid + slot_number → content.
 */
public class NotesDatabase {

    private NotesDatabase() {}

    // =========================
    // SAVE COOLDOWN (5 seconds between saves)
    // =========================
    private static final long SAVE_COOLDOWN_MS = 5000;
    private static final Map<UUID, Long> lastSaveTimes = new ConcurrentHashMap<>();

    /**
     * Проверяет, прошло ли 5 секунд с последнего сохранения для игрока.
     * Если прошло — обновляет время и возвращает true (можно сохранять).
     * Если нет — возвращает false (сохранение отклонено).
     */
    static boolean checkSaveCooldown(UUID playerUuid) {
        long now = System.currentTimeMillis();
        Long last = lastSaveTimes.get(playerUuid);
        if (last != null && (now - last) < SAVE_COOLDOWN_MS) {
            return false;
        }
        lastSaveTimes.put(playerUuid, now);
        return true;
    }

    // =========================
    // ENSURE TABLE
    // =========================
    static void ensureTable() {
        try (Connection con = com.mcplugin.infrastructure.database.DatabaseManager.getConnection();
             var st = con.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS notes (
                    player_uuid TEXT NOT NULL,
                    slot_number INTEGER NOT NULL,
                    content TEXT NOT NULL DEFAULT '',
                    PRIMARY KEY (player_uuid, slot_number)
                );
            """);
        } catch (Exception e) {
            Main.getInstance().getLogger().severe("[Notes] DB init failed: " + e.getMessage());
        }
    }

    // =========================
    // RESET SAVE COOLDOWN (for player quit)
    // =========================
    static void resetCooldown(UUID playerUuid) {
        lastSaveTimes.remove(playerUuid);
    }

    // =========================
    // LOAD NOTE
    // =========================
    static String loadNote(UUID playerUuid, int noteNumber) {
        ensureTable();
        try (Connection con = com.mcplugin.infrastructure.database.DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT content FROM notes WHERE player_uuid = ? AND slot_number = ?")) {
            ps.setString(1, playerUuid.toString());
            ps.setInt(2, noteNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("content");
                }
            }
        } catch (Exception e) {
            Main.getInstance().getLogger().severe("[Notes] Load failed: " + e.getMessage());
        }
        return null;
    }

    // =========================
    // SAVE NOTE
    // =========================
    static void saveNote(UUID playerUuid, int noteNumber, String content) {
        ensureTable();
        try (Connection con = com.mcplugin.infrastructure.database.DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT OR REPLACE INTO notes (player_uuid, slot_number, content) VALUES (?, ?, ?)")) {
            ps.setString(1, playerUuid.toString());
            ps.setInt(2, noteNumber);
            ps.setString(3, content != null ? content : "");
            ps.executeUpdate();
        } catch (Exception e) {
            Main.getInstance().getLogger().severe("[Notes] Save failed: " + e.getMessage());
        }
    }
}
