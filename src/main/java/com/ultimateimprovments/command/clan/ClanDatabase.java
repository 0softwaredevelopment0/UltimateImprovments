package com.ultimateimprovments.command.clan;

import com.ultimateimprovments.database.DatabaseManager;

import org.bukkit.Location;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * ClanDatabase — shared SQLite access for the clan system.
 *
 * <p>Tables: {@code clans}, {@code clan_members}, {@code clan_requests}.
 * The clan {@code name} column is the plain-text key (lowercased, no MiniMessage);
 * {@code display_name} stores the original string (may contain MiniMessage).</p>
 */
public final class ClanDatabase {

    private ClanDatabase() {}

    /** Role constants. */
    public static final String ROLE_ORGANIZER = "organizer";
    public static final String ROLE_MODERATOR = "moderator";
    public static final String ROLE_MEMBER = "member";

    /** Immutable clan row. */
    public record ClanData(
            String key,
            String displayName,
            String ownerUuid,
            long createdAt,
            boolean hasHome,
            String homeWorld,
            double homeX, double homeY, double homeZ,
            float homeYaw, float homePitch
    ) {}

    /** Immutable member row. */
    public record MemberData(
            String clanKey,
            String playerUuid,
            String playerName,
            String role,
            long joinedAt
    ) {}

    /** Immutable join request row. */
    public record RequestData(
            String clanKey,
            String playerUuid,
            String playerName,
            long requestedAt
    ) {}

    // ============================================================
    // CLANS
    // ============================================================

    public static boolean createClan(String key, String displayName, String ownerUuid, String ownerName) {
        try (Connection con = DatabaseManager.getConnection(); PreparedStatement ps = con.prepareStatement("""
                INSERT INTO clans (name, display_name, owner_uuid, created_at)
                VALUES (?, ?, ?, ?)
                """)) {
            ps.setString(1, key);
            ps.setString(2, displayName);
            ps.setString(3, ownerUuid);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
            // The creator is automatically a member with the organizer role.
            return addMember(key, ownerUuid, ownerName, ROLE_ORGANIZER);
        } catch (Exception e) {
            return false;
        }
    }

    /** Deletes a clan together with all its members and requests. */
    public static boolean deleteClan(String key) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM clans WHERE name = ?")) {
            ps.setString(1, key);
            int removed = ps.executeUpdate();
            deleteMembers(key);
            deleteRequests(key);
            return removed > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean clanExists(String key) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT 1 FROM clans WHERE name = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            return false;
        }
    }

    /** Returns the clan key a player belongs to (as member or owner), or null. */
    public static String getClanKeyByPlayer(String playerUuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT clan_name FROM clan_members WHERE player_uuid = ? LIMIT 1")) {
            ps.setString(1, playerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("clan_name") : null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Returns the role of a player inside a clan, or null if not a member. */
    public static String getRole(String key, String playerUuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT role FROM clan_members WHERE clan_name = ? AND player_uuid = ?")) {
            ps.setString(1, key);
            ps.setString(2, playerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("role") : null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static ClanData getClan(String key) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                     SELECT name, display_name, owner_uuid, created_at, has_home,
                            home_world, home_x, home_y, home_z, home_yaw, home_pitch
                     FROM clans WHERE name = ?
                     """)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new ClanData(
                        rs.getString("name"),
                        rs.getString("display_name"),
                        rs.getString("owner_uuid"),
                        rs.getLong("created_at"),
                        rs.getInt("has_home") == 1,
                        rs.getString("home_world"),
                        rs.getDouble("home_x"), rs.getDouble("home_y"), rs.getDouble("home_z"),
                        rs.getFloat("home_yaw"), rs.getFloat("home_pitch")
                );
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static List<ClanData> getAllClans() {
        List<ClanData> list = new ArrayList<>();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                     SELECT name, display_name, owner_uuid, created_at, has_home,
                            home_world, home_x, home_y, home_z, home_yaw, home_pitch
                     FROM clans ORDER BY created_at
                     """)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ClanData(
                            rs.getString("name"),
                            rs.getString("display_name"),
                            rs.getString("owner_uuid"),
                            rs.getLong("created_at"),
                            rs.getInt("has_home") == 1,
                            rs.getString("home_world"),
                            rs.getDouble("home_x"), rs.getDouble("home_y"), rs.getDouble("home_z"),
                            rs.getFloat("home_yaw"), rs.getFloat("home_pitch")
                    ));
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    public static int countClans() {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM clans")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    // ============================================================
    // MEMBERS
    // ============================================================

    public static boolean addMember(String key, String playerUuid, String playerName, String role) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                     INSERT OR REPLACE INTO clan_members (clan_name, player_uuid, player_name, role, joined_at)
                     VALUES (?, ?, ?, ?, ?)
                     """)) {
            ps.setString(1, key);
            ps.setString(2, playerUuid);
            ps.setString(3, playerName);
            ps.setString(4, role);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean removeMember(String key, String playerUuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM clan_members WHERE clan_name = ? AND player_uuid = ?")) {
            ps.setString(1, key);
            ps.setString(2, playerUuid);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean setRole(String key, String playerUuid, String role) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE clan_members SET role = ? WHERE clan_name = ? AND player_uuid = ?")) {
            ps.setString(1, role);
            ps.setString(2, key);
            ps.setString(3, playerUuid);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static List<MemberData> getMembers(String key) {
        List<MemberData> list = new ArrayList<>();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT clan_name, player_uuid, player_name, role, joined_at FROM clan_members WHERE clan_name = ? ORDER BY joined_at")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new MemberData(
                            rs.getString("clan_name"),
                            rs.getString("player_uuid"),
                            rs.getString("player_name"),
                            rs.getString("role"),
                            rs.getLong("joined_at")
                    ));
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    public static int countMembers(String key) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COUNT(*) FROM clan_members WHERE clan_name = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    public static String getMemberName(String key, String playerUuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT player_name FROM clan_members WHERE clan_name = ? AND player_uuid = ?")) {
            ps.setString(1, key);
            ps.setString(2, playerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("player_name") : null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static void deleteMembers(String key) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM clan_members WHERE clan_name = ?")) {
            ps.setString(1, key);
            ps.executeUpdate();
        } catch (Exception ignored) {}
    }

    // ============================================================
    // HOME
    // ============================================================

    public static boolean setHome(String key, Location loc) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                     UPDATE clans SET has_home = 1, home_world = ?, home_x = ?, home_y = ?, home_z = ?,
                                      home_yaw = ?, home_pitch = ? WHERE name = ?
                     """)) {
            ps.setString(1, loc.getWorld().getName());
            ps.setDouble(2, loc.getX());
            ps.setDouble(3, loc.getY());
            ps.setDouble(4, loc.getZ());
            ps.setFloat(5, loc.getYaw());
            ps.setFloat(6, loc.getPitch());
            ps.setString(7, key);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean deleteHome(String key) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                     UPDATE clans SET has_home = 0, home_world = '', home_x = 0, home_y = 0, home_z = 0,
                                      home_yaw = 0, home_pitch = 0 WHERE name = ?
                     """)) {
            ps.setString(1, key);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ============================================================
    // REQUESTS
    // ============================================================

    public static boolean addRequest(String key, String playerUuid, String playerName) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                     INSERT OR REPLACE INTO clan_requests (clan_name, player_uuid, player_name, requested_at)
                     VALUES (?, ?, ?, ?)
                     """)) {
            ps.setString(1, key);
            ps.setString(2, playerUuid);
            ps.setString(3, playerName);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static RequestData getRequest(String key, String playerUuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT clan_name, player_uuid, player_name, requested_at FROM clan_requests WHERE clan_name = ? AND player_uuid = ?")) {
            ps.setString(1, key);
            ps.setString(2, playerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new RequestData(
                        rs.getString("clan_name"),
                        rs.getString("player_uuid"),
                        rs.getString("player_name"),
                        rs.getLong("requested_at")
                );
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean removeRequest(String key, String playerUuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM clan_requests WHERE clan_name = ? AND player_uuid = ?")) {
            ps.setString(1, key);
            ps.setString(2, playerUuid);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static List<RequestData> getRequests(String key) {
        List<RequestData> list = new ArrayList<>();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT clan_name, player_uuid, player_name, requested_at FROM clan_requests WHERE clan_name = ? ORDER BY requested_at")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new RequestData(
                            rs.getString("clan_name"),
                            rs.getString("player_uuid"),
                            rs.getString("player_name"),
                            rs.getLong("requested_at")
                    ));
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    private static void deleteRequests(String key) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM clan_requests WHERE clan_name = ?")) {
            ps.setString(1, key);
            ps.executeUpdate();
        } catch (Exception ignored) {}
    }
}
