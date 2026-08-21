package com.ultimateimprovments.space;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.advancement.Advancement;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Space dimension manager.
 * <p>
 * Creates the {@code ui_space} void world on startup and provides
 * teleportation with position persistence (overworld ↔ space).
 * <p>
 * Key behaviors:
 * <ul>
 *   <li>Always teleports to space when a player reaches build limit + 1 (elytra, tp, etc.)</li>
 *   <li>First-time visitors spawn at (0, 0, 0) on a deepslate platform</li>
 *   <li>Deepslate platform is restored if broken</li>
 *   <li>Awards "the_space" advancement on first visit</li>
 * </ul>
 */
public class SpaceManager {

    private static final String WORLD_NAME = "ui_space";

    /** Advancement keys */
    private static final String ADV_THE_SPACE = "datapack/the_space";
    private static final String ADV_OXYDEN = "datapack/ah_where_oxyden";
    private static final String ADV_LANDING = "datapack/we_are_landing";

    /** First spawn coordinates */
    private static final double SPAWN_X = 0.5;
    private static final double SPAWN_Y = 1.0;
    private static final double SPAWN_Z = 0.5;

    /** Last overworld position before entering space (uuid → location) */
    private static final Map<UUID, Location> lastOverworldPos = new ConcurrentHashMap<>();

    /** Players on cooldown (uuid → expiry system time millis) */
    private static final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    /** Players currently being teleported to space (to avoid re-entry) */
    private static final Map<UUID, Boolean> teleporting = new ConcurrentHashMap<>();

    private static boolean enabled = false;
    private static World spaceWorld;
    private static Main plugin;
    private static java.util.List<String> spaceEntryWorlds = java.util.List.of("world");

    // ════════════════════════════════════════
    // INIT
    // ════════════════════════════════════════

    public static void init(Main pl) {
        plugin = pl;
        var cfg = pl.getConfig();
        enabled = cfg.getBoolean("space.enabled", true);
        ConsoleLogger.info("[Space] Config space.enabled = " + enabled);
        if (!enabled) {
            ConsoleLogger.info("[Space] Space dimension disabled in config.");
            return;
        }

        spaceEntryWorlds = cfg.getStringList("space.space_entry_worlds");
        if (spaceEntryWorlds.isEmpty()) {
            spaceEntryWorlds = java.util.List.of("world");
        }
        ConsoleLogger.info("[Space] Entry worlds: " + spaceEntryWorlds);

        SpaceGravityListener.reloadConfig();

        ConsoleLogger.info("[Space] Creating world '" + WORLD_NAME + "'...");

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    createSpaceWorld();
                    loadFromDatabase();
                    ensureSpawnPlatform();
                    if (spaceWorld != null) {
                        ConsoleLogger.info("[Space] Space dimension ready. World: " + spaceWorld.getName()
                                + " (maxHeight=" + spaceWorld.getMaxHeight() + ", min_y=" + spaceWorld.getMinHeight() + ")");
                    } else {
                        ConsoleLogger.error("[Space] Space dimension NOT ready — spaceWorld is null after createSpaceWorld()!");
                    }
                } catch (Exception e) {
                    ConsoleLogger.error("[Space] Exception during space init: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskLater(pl, 1L);

        // Periodic auto-teleport + return check (every 1 second)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!enabled) return;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (isInSpace(player)) {
                        checkReturnFromSpace(player);
                    } else {
                        checkAutoTeleport(player);
                    }
                }
            }
        }.runTaskTimer(pl, 20L, 20L);
    }

    public static void shutdown() {
        lastOverworldPos.clear();
        cooldowns.clear();
        teleporting.clear();
    }

    // ════════════════════════════════════════
    // WORLD CREATION
    // ════════════════════════════════════════

    private static void createSpaceWorld() {
        if (Bukkit.getWorld(WORLD_NAME) != null) {
            spaceWorld = Bukkit.getWorld(WORLD_NAME);
            ConsoleLogger.info("[Space] World '" + WORLD_NAME + "' already loaded.");
            return;
        }

        try {
            ConsoleLogger.info("[Space] Building WorldCreator for '" + WORLD_NAME + "'...");
            WorldCreator creator = new WorldCreator(WORLD_NAME)
                    .environment(World.Environment.NORMAL)
                    .generator(new VoidChunkGenerator())
                    .generateStructures(false)
                    .seed(Bukkit.getWorlds().get(0).getSeed());

            ConsoleLogger.info("[Space] Calling creator.createWorld()...");
            spaceWorld = creator.createWorld();
            if (spaceWorld != null) {
                spaceWorld.setKeepSpawnInMemory(false);
                spaceWorld.setAutoSave(true);
                spaceWorld.setGameRuleValue("doMobSpawning", "false");
                spaceWorld.setGameRuleValue("doDaylightCycle", "false");
                spaceWorld.setGameRuleValue("doWeatherCycle", "false");
                spaceWorld.setGameRuleValue("doFireTick", "false");
                spaceWorld.setGameRuleValue("doTileDrops", "false");
                spaceWorld.setGameRuleValue("doEntityDrops", "false");
                spaceWorld.setGameRuleValue("mobGriefing", "false");
                spaceWorld.setGameRuleValue("announceAdvancements", "false");
                spaceWorld.setGameRuleValue("doImmediateRespawn", "true");
                ConsoleLogger.info("[Space] World '" + WORLD_NAME + "' created successfully. "
                        + "Environment=" + spaceWorld.getEnvironment()
                        + " maxHeight=" + spaceWorld.getMaxHeight());
            } else {
                ConsoleLogger.error("[Space] creator.createWorld() returned NULL for '" + WORLD_NAME + "'!");
                ConsoleLogger.error("[Space] Check server logs for datapack errors with dimension_type/ui_space or biome/ui_biome_space.");
            }
        } catch (Exception e) {
            ConsoleLogger.error("[Space] Exception creating world: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ════════════════════════════════════════
    // SPAWN PLATFORM
    // ════════════════════════════════════════

    /**
     * Ensures the deepslate spawn platform exists at (0, -1, 0) in the space world.
     * If it's broken, restores it.
     */
    public static void ensureSpawnPlatform() {
        if (spaceWorld == null) return;

        Block block = spaceWorld.getBlockAt(0, -1, 0);
        if (block.getType() != Material.COBBLED_DEEPSLATE) {
            block.setType(Material.COBBLED_DEEPSLATE);
            ConsoleLogger.info("[Space] Spawn platform restored at (0, -1, 0).");
        }
    }

    // ════════════════════════════════════════
    // TELEPORTATION
    // ════════════════════════════════════════

    /**
     * Teleports the player to space.
     * First-time visitors spawn at (0, 1, 0) on the deepslate platform.
     * Returns the player to their saved overworld position on subsequent visits.
     *
     * @return true if teleportation was successful
     */
    public static boolean teleportToSpace(Player player) {
        if (!enabled || spaceWorld == null) {
            player.sendMessage(MessageUtil.parse("<red>❌ Space dimension is not available.</red>"));
            ConsoleLogger.warn("[Space] Teleport failed for " + player.getName()
                    + ": enabled=" + enabled + ", spaceWorld=" + (spaceWorld != null ? "loaded" : "NULL"));
            return false;
        }

        UUID uuid = player.getUniqueId();
        if (teleporting.getOrDefault(uuid, false)) return false;

        if (isInSpace(player)) return false;

        // Save current position for return (only if coming from non-space world)
        Location loc = player.getLocation();
        lastOverworldPos.put(uuid, loc.clone());
        saveLastPosition(uuid, loc);

        // Determine spawn location
        Location spaceLoc;
        boolean firstVisit = !hasVisitedSpace(uuid);

        if (firstVisit) {
            // First visit: spawn at (0, 1, 0) on the platform
            ensureSpawnPlatform();
            spaceLoc = new Location(spaceWorld, SPAWN_X, SPAWN_Y, SPAWN_Z);
        } else {
            // Return to build limit + 1
            int buildLimit = spaceWorld.getMaxHeight() + 1;
            spaceLoc = new Location(spaceWorld, 0.5, buildLimit, 0.5);
        }

        // Mark as teleporting to prevent re-entry
        teleporting.put(uuid, true);

        // Teleport
        boolean success = player.teleport(spaceLoc);
        teleporting.remove(uuid);

        if (success) {
            recordVisit(uuid);

            // Award advancement on first visit
            if (firstVisit) {
                grantAdvancement(player, ADV_THE_SPACE);
            }

            ConsoleLogger.info("[Space] " + player.getName() + " teleported to space" + (firstVisit ? " (first visit)" : "") + ".");
        }
        return success;
    }

    /**
     * Teleports the player back to their last overworld position.
     */
    public static boolean teleportFromSpace(Player player) {
        UUID uuid = player.getUniqueId();
        if (isOnCooldown(player)) {
            long remaining = (cooldowns.get(uuid) - System.currentTimeMillis()) / 1000;
            player.sendMessage(MessageUtil.parse("<yellow>✦</yellow> <white>Wait " + remaining + "s before teleporting again.</white>"));
            return false;
        }

        Location saved = lastOverworldPos.remove(uuid);
        if (saved == null) {
            saved = loadLastPosition(uuid);
        }

        if (saved == null) {
            World overworld = Bukkit.getWorlds().get(0);
            if (overworld != null) {
                saved = overworld.getSpawnLocation();
            } else {
                player.sendMessage(MessageUtil.parse("<red>❌ No return point found.</red>"));
                return false;
            }
        }

        cooldowns.put(uuid, System.currentTimeMillis() + (100L * 50));

        boolean success = player.teleport(saved);
        if (success) {
            ConsoleLogger.info("[Space] " + player.getName() + " returned from space.");
            clearLastPosition(uuid);
        }
        return success;
    }

    // ════════════════════════════════════════
    // AUTO TELEPORT — always at build limit
    // ════════════════════════════════════════

    /**
     * Checks if the player is at or above the build limit in ANY dimension
     * and auto-teleports them to space. Works with elytra, tp, rockets, etc.
     */
    public static void checkAutoTeleport(Player player) {
        if (!enabled) return;
        if (isInSpace(player)) return;
        if (isOnCooldown(player)) return;
        if (teleporting.getOrDefault(player.getUniqueId(), false)) return;

        Location loc = player.getLocation();
        if (loc.getWorld() == null) return;

        // Only auto-teleport from configured entry worlds
        if (!spaceEntryWorlds.contains(loc.getWorld().getName())) return;

        int buildLimit = loc.getWorld().getMaxHeight();

        if (loc.getBlockY() >= buildLimit) {
            teleportToSpace(player);
        }
    }

    // ════════════════════════════════════════
    // RETURN FROM SPACE — Y = buildLimit - 1
    // ════════════════════════════════════════

    /**
     * When a player in space falls to Y = buildLimit - 1, they land back
     * in the overworld at their saved position (or fallback 0, buildLimit, 0).
     * The +1 ensures the player lands at buildLimit (not buildLimit-1) so
     * they don't get immediately re-teleported to space by checkAutoTeleport.
     */
    private static void checkReturnFromSpace(Player player) {
        if (isOnCooldown(player)) return;
        if (teleporting.getOrDefault(player.getUniqueId(), false)) return;

        Location loc = player.getLocation();
        if (loc.getWorld() == null) return;

        int buildLimit = loc.getWorld().getMaxHeight();

        // Trigger: Y = buildLimit - 1 in space
        if (loc.getBlockY() >= buildLimit - 1) {
            returnFromSpace(player);
        }
    }

    /**
     * Returns the player from space to the overworld.
     * Uses saved position from DB, or fallback at (0, buildLimit, 0).
     */
    private static void returnFromSpace(Player player) {
        UUID uuid = player.getUniqueId();

        // Grant landing advancement
        grantAdvancement(player, ADV_LANDING);

        // Load saved position from DB
        Location saved = lastOverworldPos.get(uuid);
        if (saved == null) {
            saved = loadLastPosition(uuid);
        }

        // Use saved position from DB — return always goes to where the player was
        // before entering space, regardless of entry world list.
        Location target;
        if (saved != null && saved.getWorld() != null) {
            // Use saved position, but ensure Y is at buildLimit (not buildLimit+1)
            // to avoid re-teleporting to space
            int targetBuildLimit = saved.getWorld().getMaxHeight();
            double targetY = Math.min(saved.getY(), targetBuildLimit);
            target = new Location(saved.getWorld(), saved.getX(), targetY, saved.getZ(),
                    saved.getYaw(), saved.getPitch());
        } else {
            // Fallback: first available world at spawn
            World fallbackWorld = Bukkit.getWorlds().get(0);
            target = fallbackWorld.getSpawnLocation();
            ConsoleLogger.warn("[Space] No saved position for " + player.getName() + ", using fallback.");
        }

        // Apply cooldown
        cooldowns.put(uuid, System.currentTimeMillis() + (100L * 50));

        // Teleport
        boolean success = player.teleport(target);
        if (success) {
            ConsoleLogger.info("[Space] " + player.getName() + " landed from space at "
                    + target.getWorld().getName() + " " +
                    target.getBlockX() + " " + target.getBlockY() + " " + target.getBlockZ());
            clearLastPosition(uuid);
        } else {
            // Fallback on failure: teleport to first world spawn
            World fallbackWorld = target.getWorld() != null ? target.getWorld() : Bukkit.getWorlds().get(0);
            Location fallback = fallbackWorld.getSpawnLocation();
            player.teleport(fallback);
            player.sendMessage(MessageUtil.parse("<yellow>⚠</yellow> <white>Return point failed, teleported to spawn.</white>"));
        }
    }

    // ════════════════════════════════════════
    // QUERIES
    // ════════════════════════════════════════

    public static boolean isInSpace(Player player) {
        World w = player.getWorld();
        return w != null && WORLD_NAME.equals(w.getName());
    }

    public static boolean isInSpace(World world) {
        return world != null && WORLD_NAME.equals(world.getName());
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static World getSpaceWorld() {
        return spaceWorld;
    }

    private static boolean isOnCooldown(Player player) {
        Long expiry = cooldowns.get(player.getUniqueId());
        return expiry != null && System.currentTimeMillis() < expiry;
    }

    /**
     * Checks if a player has visited space before (visit_count > 0).
     */
    public static boolean hasVisitedSpace(UUID uuid) {
        try (Connection con = DatabaseManager.getConnection()) {
            PreparedStatement ps = con.prepareStatement(
                "SELECT visit_count FROM space_data WHERE uuid=?"
            );
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("visit_count") > 0;
            }
        } catch (Exception ignored) {}
        return false;
    }

    // ════════════════════════════════════════
    // ADVANCEMENTS
    // ════════════════════════════════════════

    /**
     * Grants an advancement to the player (idempotent — safe if already done).
     */
    public static void grantAdvancement(Player player, String key) {
        try {
            Advancement adv = Bukkit.getAdvancement(new org.bukkit.NamespacedKey("ui", key));
            if (adv == null) return;
            var progress = player.getAdvancementProgress(adv);
            if (!progress.isDone()) {
                progress.awardCriteria("1");
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[Space] Failed to grant advancement " + key + ": " + e.getMessage());
        }
    }

    /**
     * Grants the oxygen death advancement.
     */
    public static void grantOxygenDeathAdvancement(Player player) {
        grantAdvancement(player, ADV_OXYDEN);
    }

    // ════════════════════════════════════════
    // DATABASE
    // ════════════════════════════════════════

    public static void createTable() {
        try (Connection con = DatabaseManager.getConnection()) {
            con.prepareStatement(
                "CREATE TABLE IF NOT EXISTS space_data (" +
                "  uuid TEXT PRIMARY KEY," +
                "  last_x REAL," +
                "  last_y REAL," +
                "  last_z REAL," +
                "  last_world TEXT," +
                "  last_yaw REAL," +
                "  last_pitch REAL," +
                "  visit_count INTEGER DEFAULT 0," +
                "  first_visit INTEGER," +
                "  last_visit INTEGER" +
                ")"
            ).executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.error("[Space] Failed to create space_data table: " + e.getMessage());
        }
    }

    private static void saveLastPosition(UUID uuid, Location loc) {
        try (Connection con = DatabaseManager.getConnection()) {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO space_data (uuid, last_x, last_y, last_z, last_world, last_yaw, last_pitch) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET " +
                "last_x=excluded.last_x, last_y=excluded.last_y, last_z=excluded.last_z, " +
                "last_world=excluded.last_world, last_yaw=excluded.last_yaw, last_pitch=excluded.last_pitch"
            );
            ps.setString(1, uuid.toString());
            ps.setDouble(2, loc.getX());
            ps.setDouble(3, loc.getY());
            ps.setDouble(4, loc.getZ());
            ps.setString(5, loc.getWorld() != null ? loc.getWorld().getName() : "world");
            ps.setFloat(6, loc.getYaw());
            ps.setFloat(7, loc.getPitch());
            ps.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.warn("[Space] Failed to save last position: " + e.getMessage());
        }
    }

    private static Location loadLastPosition(UUID uuid) {
        try (Connection con = DatabaseManager.getConnection()) {
            PreparedStatement ps = con.prepareStatement(
                "SELECT last_x, last_y, last_z, last_world, last_yaw, last_pitch FROM space_data WHERE uuid=?"
            );
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                World w = Bukkit.getWorld(rs.getString("last_world"));
                if (w == null) w = Bukkit.getWorlds().get(0);
                return new Location(w,
                    rs.getDouble("last_x"),
                    rs.getDouble("last_y"),
                    rs.getDouble("last_z"),
                    rs.getFloat("last_yaw"),
                    rs.getFloat("last_pitch")
                );
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[Space] Failed to load last position: " + e.getMessage());
        }
        return null;
    }

    private static void clearLastPosition(UUID uuid) {
        try (Connection con = DatabaseManager.getConnection()) {
            PreparedStatement ps = con.prepareStatement(
                "UPDATE space_data SET last_x=NULL, last_y=NULL, last_z=NULL, last_world=NULL WHERE uuid=?"
            );
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (Exception ignored) {}
    }

    private static void loadFromDatabase() {
        try (Connection con = DatabaseManager.getConnection();
             ResultSet rs = con.prepareStatement("SELECT uuid, last_x, last_y, last_z, last_world, last_yaw, last_pitch FROM space_data WHERE last_x IS NOT NULL").executeQuery()) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                World w = Bukkit.getWorld(rs.getString("last_world"));
                if (w == null) continue;
                Location loc = new Location(w,
                    rs.getDouble("last_x"),
                    rs.getDouble("last_y"),
                    rs.getDouble("last_z"),
                    rs.getFloat("last_yaw"),
                    rs.getFloat("last_pitch")
                );
                lastOverworldPos.put(uuid, loc);
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[Space] Failed to preload positions: " + e.getMessage());
        }
    }

    public static void recordVisit(UUID uuid) {
        long now = System.currentTimeMillis() / 1000;
        try (Connection con = DatabaseManager.getConnection()) {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO space_data (uuid, visit_count, first_visit, last_visit) " +
                "VALUES (?, 1, ?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET " +
                "visit_count=visit_count+1, last_visit=excluded.last_visit"
            );
            ps.setString(1, uuid.toString());
            ps.setLong(2, now);
            ps.setLong(3, now);
            ps.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.warn("[Space] Failed to record visit: " + e.getMessage());
        }
    }
}