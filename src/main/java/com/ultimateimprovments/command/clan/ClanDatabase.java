package com.ultimateimprovments.command.clan;

import com.ultimateimprovments.database.DatabaseManager;

import org.bukkit.Location;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ClanDatabase — shared SQLite access for the clan system.
 *
 * <p>Tables: {@code clans}, {@code clan_members}, {@code clan_requests}.
 * The clan {@code name} column is the plain-text key (lowercased, no MiniMessage);
 * {@code display_name} stores the original string (may contain MiniMessage).</p>
 */
public final class ClanDatabase {

    private ClanDatabase() {}

    /** Role constants (single source of truth: {@link ClanRoles}). */
    public static final String ROLE_LEADER = ClanRoles.ROLE_LEADER;
    public static final String ROLE_ORGANIZER = ClanRoles.ROLE_ORGANIZER;
    public static final String ROLE_MODERATOR = ClanRoles.ROLE_MODERATOR;
    public static final String ROLE_MEMBER = ClanRoles.ROLE_MEMBER;

    /** Immutable clan row. */
    public record ClanData(
            String key,
            String displayName,
            String ownerUuid,
            long createdAt,
            boolean hasHome,
            String homeWorld,
            double homeX, double homeY, double homeZ,
            float homeYaw, float homePitch,
            String description,
            String settings
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

    /** Immutable dependency request row. */
    public record DepRequestData(String fromClan, String toClan, long requestedAt) {}


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
            // The creator is automatically the clan leader.
            return addMember(key, ownerUuid, ownerName, ROLE_LEADER);
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
            removeDependency(key);
            deleteDepRequests(key);
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
                            home_world, home_x, home_y, home_z, home_yaw, home_pitch,
                            description, settings
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
                        rs.getFloat("home_yaw"), rs.getFloat("home_pitch"),
                        rs.getString("description"),
                        rs.getString("settings")
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
                            home_world, home_x, home_y, home_z, home_yaw, home_pitch,
                            description, settings
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
                            rs.getFloat("home_yaw"), rs.getFloat("home_pitch"),
                            rs.getString("description"),
                            rs.getString("settings")
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
    // LEADER / CLAN INFO
    // ============================================================

    /**
     * Transfers leadership to another member. The new leader gets the leader role,
     * the old leader is demoted to organizer.
     */
    public static boolean transferLeader(String key, String newLeaderUuid, String newLeaderName, String oldLeaderUuid) {
        try (Connection con = DatabaseManager.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement("UPDATE clans SET owner_uuid = ? WHERE name = ?")) {
                ps.setString(1, newLeaderUuid);
                ps.setString(2, key);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE clan_members SET role = ?, player_name = ? WHERE clan_name = ? AND player_uuid = ?")) {
                ps.setString(1, ROLE_LEADER);
                ps.setString(2, newLeaderName);
                ps.setString(3, key);
                ps.setString(4, newLeaderUuid);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE clan_members SET role = ? WHERE clan_name = ? AND player_uuid = ?")) {
                ps.setString(1, ROLE_ORGANIZER);
                ps.setString(2, key);
                ps.setString(3, oldLeaderUuid);
                ps.executeUpdate();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Renames the display name; the lookup key (name) stays unchanged. */
    public static boolean renameClan(String key, String newDisplayName) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE clans SET display_name = ? WHERE name = ?")) {
            ps.setString(1, newDisplayName);
            ps.setString(2, key);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Sets (or clears, if text is empty) the clan description. */
    public static boolean setDescription(String key, String text) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE clans SET description = ? WHERE name = ?")) {
            ps.setString(1, text == null ? "" : text);
            ps.setString(2, key);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Returns the clan settings map (empty if none). */
    public static Map<String, String> getClanSettings(String key) {
        ClanData clan = getClan(key);
        return clan == null ? new LinkedHashMap<>() : parseSettings(clan.settings());
    }

    /**
     * True if the clan has friendly-fire protection enabled (selfpvp=on).
     * Default is off — clanmates may damage each other.
     */
    public static boolean isSelfPvpEnabled(String key) {
        return "on".equalsIgnoreCase(getClanSettings(key).get("selfpvp"));
    }

    /** Sets one setting key (empty value removes it). */
    public static boolean setClanSetting(String key, String k, String v) {
        Map<String, String> settings = getClanSettings(key);
        if (v == null || v.isEmpty()) {
            settings.remove(k);
        } else {
            settings.put(k, v);
        }
        String raw = dumpSettings(settings);
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE clans SET settings = ? WHERE name = ?")) {
            ps.setString(1, raw);
            ps.setString(2, key);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Parses the settings column (lines of key=value) into a map. */
    private static Map<String, String> parseSettings(String raw) {
        Map<String, String> map = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return map;
        for (String line : raw.split("\n")) {
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            map.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
        }
        return map;
    }

    private static String dumpSettings(Map<String, String> map) {
        if (map.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
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

    // ============================================================
    // DEPENDENCIES
    // ============================================================

    /** Get the dependent clan of a main clan (or null). */
    public static String getDependentClan(String mainKey) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT dep_clan FROM clan_dependencies WHERE main_clan = ?")) {
            ps.setString(1, mainKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("dep_clan") : null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Get the main clan of a dependent clan (or null). */
    public static String getMainClan(String depKey) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT main_clan FROM clan_dependencies WHERE dep_clan = ?")) {
            ps.setString(1, depKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("main_clan") : null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Check if a clan is dependent. */
    public static boolean isDependent(String clanKey) {
        return getMainClan(clanKey) != null;
    }

    /** Set dependency between two clans. */
    public static boolean setDependency(String mainKey, String depKey) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO clan_dependencies (main_clan, dep_clan, created_at) VALUES (?, ?, ?)")) {
            ps.setString(1, mainKey);
            ps.setString(2, depKey);
            ps.setLong(3, System.currentTimeMillis());
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Remove dependency (disband dependent relationship). */
    public static boolean removeDependency(String mainKey) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM clan_dependencies WHERE main_clan = ? OR dep_clan = ?")) {
            ps.setString(1, mainKey);
            ps.setString(2, mainKey);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ============================================================
    // DEPENDENCY REQUESTS
    // ============================================================

    /** Add a dependency invite request. */
    public static boolean addDepRequest(String fromClan, String toClan) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT OR REPLACE INTO clan_dep_requests (from_clan, to_clan, requested_at) VALUES (?, ?, ?)")) {
            ps.setString(1, fromClan);
            ps.setString(2, toClan);
            ps.setLong(3, System.currentTimeMillis());
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Get pending dep request for a clan (who invited them). */
    public static DepRequestData getDepRequest(String toClan) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT from_clan, to_clan, requested_at FROM clan_dep_requests WHERE to_clan = ?")) {
            ps.setString(1, toClan);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new DepRequestData(
                        rs.getString("from_clan"),
                        rs.getString("to_clan"),
                        rs.getLong("requested_at")
                );
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Remove a dep request. */
    public static boolean removeDepRequest(String fromClan, String toClan) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM clan_dep_requests WHERE from_clan = ? AND to_clan = ?")) {
            ps.setString(1, fromClan);
            ps.setString(2, toClan);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Remove all dep requests involving a clan. */
    private static void deleteDepRequests(String clanKey) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM clan_dep_requests WHERE from_clan = ? OR to_clan = ?")) {
            ps.setString(1, clanKey);
            ps.setString(2, clanKey);
            ps.executeUpdate();
        } catch (Exception ignored) {}
    }
}
