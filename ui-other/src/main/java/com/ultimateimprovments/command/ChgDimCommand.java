package com.ultimateimprovments.command;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.UUID;

/**
 * Handles the /ui chgdim command — teleportation between worlds.
 * <p>
 * Opens a Custom Screen (Dialog) on /ui chgdim, where the player enters
 * the world name and presses Teleport / Cancel.
 */
public class ChgDimCommand {

    /** Cooldown map: UUID → timestamp of the last teleport. Package-private for ChgDimDialogHandler. */
    static final HashMap<UUID, Long> cooldowns = new HashMap<>();

    /**
     * Teleports to the specified world.
     */
    public static boolean teleport(Player player, String worldName) {
        // =========================
        // COOLDOWN CHECK
        // =========================
        UUID playerUuid = player.getUniqueId();
        long now = System.currentTimeMillis() / 1000;
        int cooldownSecs = Main.getInstance().getConfig()
                .getInt("changedimmension.cooldown_seconds", 10);

        if (cooldowns.containsKey(playerUuid)) {
            long lastUse = cooldowns.get(playerUuid);
            long elapsed = now - lastUse;
            if (elapsed < cooldownSecs) {
                long remaining = cooldownSecs - elapsed;
                player.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.cooldown",
                                "<dark_red>❌</dark_red> <red>Please wait</red> <yellow>%seconds%</yellow><red> seconds before using this again!</red>")
                        .replace("%seconds%", String.valueOf(remaining))));
                return true;
            }
        }

        // =========================
        // GET WORLD CONFIG
        // =========================
        FileConfiguration config = Main.getInstance().getConfig();
        ConfigurationSection worldsSection = config.getConfigurationSection("changedimmension.worlds");

        if (worldsSection == null || !worldsSection.contains(worldName)) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.world_not_configured", "<red>❌ World</red> <yellow>%world%</yellow> <red>not configured!</red>").replace("%world%", worldName)));
            return true;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.world_not_found",
                            "<dark_red>❌</dark_red> <red>World</red> <yellow>%world%</yellow> <red>not found!</red>")
                    .replace("%world%", worldName)));
            return true;
        }

        ConfigurationSection worldConfig = worldsSection.getConfigurationSection(worldName);

        double teleportX = worldConfig != null ? worldConfig.getDouble("x", 0) : 0;
        double teleportY = worldConfig != null ? worldConfig.getDouble("y", 64) : 64;
        double teleportZ = worldConfig != null ? worldConfig.getDouble("z", 0) : 0;
        float teleportYaw = worldConfig != null ? (float) worldConfig.getDouble("yaw", 0.0) : 0.0f;
        float teleportPitch = worldConfig != null ? (float) worldConfig.getDouble("pitch", 0.0) : 0.0f;

        // =========================
        // SAVE THE CURRENT POSITION IN THE DB (always, before teleporting)
        // =========================
        DimensionManager.saveReturnLocation(player);

        Location targetLocation = new Location(world, teleportX, teleportY, teleportZ, teleportYaw, teleportPitch);
        player.teleportAsync(targetLocation);
        cooldowns.put(playerUuid, now);
        player.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.success",
                        "<green>✔</green> <white>Teleportation to</white> <yellow>%world%</yellow> <white>completed!</white>")
                .replace("%world%", worldName)));

        return true;
    }

    /**
     * Teleports back to the original point.
     */
    public static boolean teleportBack(Player player) {
        if (!DimensionManager.hasReturnLocation(player)) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.no_return_point", "<red>❌ No saved return point!</red>")));
            return true;
        }

        Location returnLoc = DimensionManager.getReturnLocation(player);
        if (returnLoc == null) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.return_error",
                            "<dark_red>❌</dark_red> <red>Error: Return point corrupted!</red>")));
            DimensionManager.removeReturnLocation(player);
            return true;
        }

        player.teleportAsync(returnLoc);
        DimensionManager.removeReturnLocation(player);
        player.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.return_success",
                        "<green>✔</green> <white>You have returned to your starting point!</white>")));

        return true;
    }

    public static void clearCooldown(UUID uuid) {
        cooldowns.remove(uuid);
    }
}
