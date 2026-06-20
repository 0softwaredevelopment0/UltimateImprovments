package com.mcplugin.features.notes;

import com.mcplugin.Main;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/**
 * Работа с таблицей notes в SQLite.
 * Хранит заметки игроков: player_uuid + slot_number → content.
 */
public class NotesDatabase {

    private NotesDatabase() {}

    // =========================
    // ENSURE TABLE
    // =========================
    static void ensureTable() {
        try (Connection con = com.mcplugin.database.DatabaseManager.getConnection();
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
    // LOAD NOTE
    // =========================
    static String loadNote(UUID playerUuid, int noteNumber) {
        ensureTable();
        try (Connection con = com.mcplugin.database.DatabaseManager.getConnection();
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
        try (Connection con = com.mcplugin.database.DatabaseManager.getConnection();
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
