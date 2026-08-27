package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /ui rtp — random teleportation with flexible configuration.
 * <p>
 * Disabled by default ({@code rtp.enabled: false}).
 * The config allows setting the radius, area shape (square/circle),
 * black/white block lists, height limits, air/void avoidance,
 * cooldown and a world list.
 */
public final class RtpSubcommand {

    private RtpSubcommand() {}

    /** Cooldown map: player UUID → unix millis when it can be used again. */
    private static final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    /**
     * /ui rtp [player]
     * <p>
     * If an argument is given and the sender has the {@code ui.command.rtp.other} permission,
     * teleports the specified player. Otherwise — teleports the sender (if they're a player).
     */
    public static boolean execute(CommandSender sender, String[] args) {
        // Determine the target
        Player target;
        boolean isSelf;

        if (args.length > 1) {
            // /ui rtp <player>
            if (!sender.hasPermission("ui.command.rtp.other")) {
                CommandErrors.noPermission(sender);
                return true;
            }
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(MessageUtil.parse("<red>❌ Player</red> <yellow>" + args[1] + "</yellow> <red>not found!</red>"));
                return true;
            }
            isSelf = sender.equals(target);
        } else {
            // /ui rtp
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MessageUtil.parse("<red>❌ Only players can use this command!</red>"));
                return true;
            }
            target = player;
            isSelf = true;
        }

        // Check: is the command enabled
        FileConfiguration cfg = Main.getInstance().getConfig();
        if (!cfg.getBoolean("rtp.enabled", false)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Random teleport is disabled on this server.</red>"));
            return true;
        }

        // Check: world
        World world = target.getWorld();
        List<String> allowedWorlds = cfg.getStringList("rtp.worlds");
        if (!allowedWorlds.isEmpty() && !allowedWorlds.contains(world.getName())) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Random teleport is not allowed in this world.</red>"));
            return true;
        }

        // Check: cooldown
        UUID uuid = target.getUniqueId();
        long now = System.currentTimeMillis();
        int cooldownSeconds = cfg.getInt("rtp.cooldown_seconds", 60);
        if (cooldownSeconds > 0 && !sender.hasPermission("ui.command.rtp.bypasscooldown")) {
            Long nextUse = cooldowns.get(uuid);
            if (nextUse != null && now < nextUse) {
                long remaining = (nextUse - now + 999) / 1000;
                sender.sendMessage(MessageUtil.parse(
                        "<red>❌ Please wait </red><yellow>" + remaining + "</yellow><red> seconds before using RTP again.</red>"
                ));
                return true;
            }
        }

        // RTP parameters
        RtpConfig config = loadConfig(cfg);

        // Find a suitable location
        Location location = findSafeLocation(world, config);

        if (location == null) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Could not find a safe location to teleport. Try again.</red>"));
            return true;
        }

        // Teleport
        boolean success = target.teleport(location);
        if (!success) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Teleport failed! Try again.</red>"));
            return true;
        }

        // Set the cooldown
        if (cooldownSeconds > 0 && !sender.hasPermission("ui.command.rtp.bypasscooldown")) {
            cooldowns.put(uuid, now + cooldownSeconds * 1000L);
        }

        // Message
        String coords = "X: " + location.getBlockX() + " Y: " + location.getBlockY() + " Z: " + location.getBlockZ();
        sender.sendMessage(MessageUtil.parse(
                "<green>✔</green> <white>Random teleport complete!</white>"
        ));
        if (!isSelf) {
            sender.sendMessage(MessageUtil.parse(
                    "<gray>Teleported </gray><yellow>" + target.getName() + "</yellow> <gray>→</gray> <white>" + coords + "</white>"
            ));
        }
        target.sendMessage(MessageUtil.parse(
                "<green>✔</green> <white>You have been randomly teleported to:</white> <yellow>" + coords + "</yellow>"
        ));
        ConsoleLogger.info("[RTP] " + target.getName() + " teleported to " + coords + " in world " + world.getName());

        return true;
    }

    // ========================================================================
    // CONFIG
    // ========================================================================

    private record RtpConfig(
            int radiusMin,
            int radiusMax,
            boolean isCircle,
            int heightMin,
            int heightMax,
            int maxAttempts,
            int safeSearchRadius,
            Set<String> avoidBlocks,
            Set<String> onlySurface,
            boolean avoidVoid,
            boolean avoidLiquid
    ) {}

    private static RtpConfig loadConfig(FileConfiguration cfg) {
        String shape = cfg.getString("rtp.shape", "square").toLowerCase();

        return new RtpConfig(
                cfg.getInt("rtp.radius.min", 500),
                cfg.getInt("rtp.radius.max", 10000),
                shape.equals("circle"),
                cfg.getInt("rtp.height.min", 0),
                cfg.getInt("rtp.height.max", 255),
                cfg.getInt("rtp.max_attempts", 50),
                cfg.getInt("rtp.safe_search_radius", 5),
                new HashSet<>(cfg.getStringList("rtp.avoid_blocks")),
                new HashSet<>(cfg.getStringList("rtp.only_surface")),
                cfg.getBoolean("rtp.avoid.void", true),
                cfg.getBoolean("rtp.avoid.liquid", true)
        );
    }

    // ========================================================================
    // LOCATION SEARCH
    // ========================================================================

    /**
     * Tries to find a safe teleport spot.
     * Generates random coordinates within the settings and validates them.
     *
     * @return {@link Location} or {@code null} if not found within {@code maxAttempts}.
     */
    private static Location findSafeLocation(World world, RtpConfig config) {
        Random random = new Random();

        for (int i = 0; i < config.maxAttempts(); i++) {
            int x = generateCoordinate(random, config.radiusMin(), config.radiusMax(), config.isCircle());
            int z = generateCoordinate(random, config.radiusMin(), config.radiusMax(), config.isCircle());

            // Find the surface (Y)
            int surfaceY = findSurfaceY(world, x, z, config);
            if (surfaceY == -1) continue;

            // Check that Y is within limits
            if (surfaceY < config.heightMin() || surfaceY > config.heightMax()) continue;

            // Check the surface block
            Block surfaceBlock = world.getBlockAt(x, surfaceY, z);
            Material surfaceType = surfaceBlock.getType();

            // Check only for specified blocks
            if (!config.onlySurface().isEmpty() && !config.onlySurface().contains(surfaceType.name())) continue;

            // Check blocks to avoid
            if (config.avoidBlocks().contains(surfaceType.name())) continue;

            // Liquid check (isSafeLocation does a more thorough check below)
            if (config.avoidLiquid() && surfaceBlock.isLiquid()) continue;

            // Check the spot's safety (enough room for the player)
            if (!isSafeLocation(world, x, surfaceY, z, config)) continue;

            // Location found
            return new Location(world, x + 0.5, surfaceY + 1, z + 0.5,
                    random.nextFloat() * 360.0f, 0.0f);
        }

        return null;
    }

    /**
     * Generates a random coordinate based on the shape.
     * <p>
     * Square — uniform from -radius to radius.
     * Circle — rejects points outside the radius (the outer loop retries).
     */
    private static int generateCoordinate(Random random, int min, int max, boolean isCircle) {
        if (isCircle) {
            // For a circle use the max distance as the radius
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = min + random.nextDouble() * (max - min);
            return (int) Math.round(Math.cos(angle) * distance);
        } else {
            // Square: independent X and Z
            int coord;
            do {
                coord = random.nextInt(max * 2 + 1) - max;
            } while (Math.abs(coord) < min && min > 0);
            return coord;
        }
    }

    /**
     * Finds the ground surface (first solid block from top to bottom).
     *
     * @return Y of the surface block, or -1 if not found / void.
     */
    private static int findSurfaceY(World world, int x, int z, RtpConfig config) {
        int maxY = Math.min(config.heightMax(), world.getMaxHeight() - 1);
        int minY = Math.max(config.heightMin(), world.getMinHeight());

        // Go from top to bottom, find the first solid block
        for (int y = maxY; y >= minY; y--) {
            Block block = world.getBlockAt(x, y, z);

            // Void check below (if avoidVoid and we're at the bottom of the world)
            if (config.avoidVoid() && y <= world.getMinHeight() + 1) return -1;

            // Skip air and transparent/non-solid blocks
            if (block.isEmpty() || block.isLiquid() || !block.getType().isSolid()) continue;

            // Found a solid block — this is the surface
            return y;
        }

        return -1; // no surface found
    }

    /**
     * Checks whether there's enough room for the player above the surface.
     * <p>
     * A player is 1 block wide and 2 blocks tall (Y+1, Y+2 must be air).
     * Additionally checks {@code safeSearchRadius} neighboring blocks for passability.
     */
    private static boolean isSafeLocation(World world, int x, int surfaceY, int z, RtpConfig config) {
        int radius = config.safeSearchRadius();

        // Main check: is there enough room for the player above the surface
        Block headBlock1 = world.getBlockAt(x, surfaceY + 1, z);
        Block headBlock2 = world.getBlockAt(x, surfaceY + 2, z);

        // At least 2 blocks of air above the surface
        if (!headBlock1.isEmpty() || !headBlock2.isEmpty()) {
            return false;
        }

        // Check neighboring blocks for passability (to not get stuck)
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) continue;

                Block neighborSurface = world.getBlockAt(x + dx, surfaceY, z + dz);
                if (!neighborSurface.getType().isSolid() && !neighborSurface.isEmpty()) {
                    // Half-blocks you can stand on — ok
                    // Ice, glass etc. — ok
                    if (neighborSurface.getType().isOccluding()) return false;
                }

                // Check neighboring blocks above the surface (may not be empty — a wall)
                for (int dy = 1; dy <= 2; dy++) {
                    Block neighbor = world.getBlockAt(x + dx, surfaceY + dy, z + dz);
                    if (neighbor.getType().isSolid() && neighbor.getType().isOccluding()) {
                        // If it fully blocks the passage — unsafe
                        return false;
                    }
                }
            }
        }

        // Check for lava/magma under the feet on the surface
        Block surfaceBlock = world.getBlockAt(x, surfaceY, z);
        if (config.avoidLiquid()) {
            if (surfaceBlock.isLiquid()) return false;

            // Check below the surface for liquids
            for (int dy = 1; dy <= 3; dy++) {
                Block below = world.getBlockAt(x, surfaceY - dy, z);
                if (below.isLiquid()) return false;
            }
        }

        return true;
    }
}
