package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * SpawnCommand — spawn points.
 * <p>
 * /ui setspawn — set the spawn point (with confirmation on overwrite)
 * /ui spawn — teleport to spawn (standard) or show coords (legit)
 * /ui setspawn confirm — confirm the overwrite
 */
public final class SpawnCommand {

    private SpawnCommand() {}

    private static final Map<UUID, Long> CONFIRM_PENDING = new HashMap<>();
    private static final long CONFIRM_TIMEOUT_MS = 30_000; // 30 seconds

    // ============================================================
    // DISPATCH
    // ============================================================
    public static boolean dispatch(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Only players can use this command!</red>"));
            return true;
        }

        if (args.length == 0) {
            executeSpawn(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "setspawn" -> executeSetSpawn(player, args);
            case "spawn" -> { executeSpawn(player); yield true; }
            default -> false;
        };
    }

    // ============================================================
    // SETSPAWN
    // ============================================================
    private static boolean executeSetSpawn(Player player, String[] args) {
        if (!player.hasPermission("ui.command.setspawn")) {
            player.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to set the spawn point!</red>"));
            return true;
        }

        // Overwrite confirmation
        if (args.length >= 2 && args[1].equalsIgnoreCase("confirm")) {
            UUID uuid = player.getUniqueId();
            Long pending = CONFIRM_PENDING.remove(uuid);
            if (pending == null || System.currentTimeMillis() - pending > CONFIRM_TIMEOUT_MS) {
                player.sendMessage(MessageUtil.parse("<red>❌ Confirmation timed out or no pending request. Use </red><white>/ui setspawn</white><red> first.</red>"));
                return true;
            }
            saveSpawn(player, player.getLocation());
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Spawn point has been overridden.</white>"));
            return true;
        }

        Location loc = player.getLocation();
        FileConfiguration config = Main.getInstance().getConfig();

        // Check whether a spawn already exists
        if (config.contains("spawn.location.world")) {
            // Ask for confirmation
            CONFIRM_PENDING.put(player.getUniqueId(), System.currentTimeMillis());
            player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
            player.sendMessage(MessageUtil.parse("<gold>  ✦ </gold><yellow>Spawn point already exists!</yellow>"));
            player.sendMessage(MessageUtil.parse("<gray>  Current spawn:</gray>"));
            player.sendMessage(MessageUtil.parse(" <gray>World: </gray><white>" + config.getString("spawn.location.world") + "</white>"));
            player.sendMessage(MessageUtil.parse(" <gray>X: </gray><white>" + config.getDouble("spawn.location.x") + "</white> <gray>Y: </gray><white>" + config.getDouble("spawn.location.y") + "</white> <gray>Z: </gray><white>" + config.getDouble("spawn.location.z") + "</white>"));
            player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
            player.sendMessage(MessageUtil.parse("<gold>✦</gold> <white>Are you sure you want to override it?</white>"));
            player.sendMessage(MessageUtil.parse(" <gray>Type </gray><white>/ui setspawn confirm</white><gray> within 30 seconds.</gray>"));
            return true;
        }

        // No spawn yet — just save it
        saveSpawn(player, loc);
        player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Spawn point set at your current location.</white>"));
        showSpawnCoords(player, loc);
        return true;
    }

    private static void saveSpawn(Player player, Location loc) {
        FileConfiguration config = Main.getInstance().getConfig();
        config.set("spawn.location.world", loc.getWorld().getName());
        config.set("spawn.location.x", loc.getX());
        config.set("spawn.location.y", loc.getY());
        config.set("spawn.location.z", loc.getZ());
        config.set("spawn.location.yaw", (double) loc.getYaw());
        config.set("spawn.location.pitch", (double) loc.getPitch());
        Main.getInstance().saveConfig();
        Main.getInstance().reloadConfig();
    }

    // ============================================================
    // SPAWN
    // ============================================================
    private static void executeSpawn(Player player) {
        if (!player.hasPermission("ui.command.spawn")) {
            player.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to use /ui spawn!</red>"));
            return;
        }

        FileConfiguration config = Main.getInstance().getConfig();

        if (!config.contains("spawn.location.world")) {
            player.sendMessage(MessageUtil.parse("<red>❌ Spawn point has not been set yet!</red>"));
            player.sendMessage(MessageUtil.parse("<gray>  Use </gray><white>/ui setspawn</white><gray> to set it.</gray>"));
            return;
        }

        String worldName = config.getString("spawn.location.world");
        double x = config.getDouble("spawn.location.x");
        double y = config.getDouble("spawn.location.y");
        double z = config.getDouble("spawn.location.z");
        float yaw = (float) config.getDouble("spawn.location.yaw", 0.0);
        float pitch = (float) config.getDouble("spawn.location.pitch", 0.0);

        String mode = config.getString("spawn.mode", "legit");

        if (mode.equalsIgnoreCase("standard")) {
            // Teleport
            World world = player.getServer().getWorld(worldName);
            if (world == null) {
                player.sendMessage(MessageUtil.parse("<red>❌ Spawn world</red> <yellow>" + worldName + "</yellow> <red>not found!</red>"));
                return;
            }
            Location spawnLoc = new Location(world, x, y, z, yaw, pitch);
            player.teleport(spawnLoc);
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Teleported to spawn!</white>"));
            showSpawnCoords(player, spawnLoc);
        } else {
            // Legit — only show the coords
            player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
            player.sendMessage(MessageUtil.parse("<gold>  ✦ </gold><white>Spawn Point</white>"));
            player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
            player.sendMessage(MessageUtil.parse(" <gray>World: </gray><white>" + worldName + "</white>"));
            player.sendMessage(MessageUtil.parse(" <gray>X: </gray><white>" + String.format("%.1f", x) + "</white>"));
            player.sendMessage(MessageUtil.parse(" <gray>Y: </gray><white>" + String.format("%.1f", y) + "</white>"));
            player.sendMessage(MessageUtil.parse(" <gray>Z: </gray><white>" + String.format("%.1f", z) + "</white>"));
            player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
            player.sendMessage(MessageUtil.parse("<yellow>⚠</yellow> <gray>Spawn is in </gray><yellow>legit</yellow> <gray>mode — no teleport. Travel manually.</gray>"));
        }
    }

    private static void showSpawnCoords(Player player, Location loc) {
        player.sendMessage(MessageUtil.parse(" <gray>World: </gray><white>" + loc.getWorld().getName() + "</white>"));
        player.sendMessage(MessageUtil.parse(" <gray>X: </gray><white>" + String.format("%.1f", loc.getX()) + "</white> <gray>Y: </gray><white>" + String.format("%.1f", loc.getY()) + "</white> <gray>Z: </gray><white>" + String.format("%.1f", loc.getZ()) + "</white>"));
    }
}
