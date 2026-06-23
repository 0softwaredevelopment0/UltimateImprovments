package com.mcplugin.infrastructure.commands;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.config.MessagesManager;
import com.mcplugin.infrastructure.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.UUID;

/**
 * Обрабатывает команды /mp chgdim — телепортацию между мирами.
 */
public class ChgDimCommand {

    private static final HashMap<UUID, Long> cooldowns = new HashMap<>();

    /**
     * Показывает меню телепортации (список миров).
     */
    public static void showMenu(Player player) {
        player.sendMessage("");
        player.sendMessage("");
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        player.sendMessage(MessageUtil.parse("<gold>  ✦ </gold><white>Change Dimension</white>"));
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        player.sendMessage("");

        FileConfiguration config = Main.getInstance().getConfig();
        ConfigurationSection worldsSection = config.getConfigurationSection("changedimmension.worlds");

        if (worldsSection == null) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Worlds not configured in config!</red>"));
            return;
        }

        for (String worldName : worldsSection.getKeys(false)) {
            String displayName = worldsSection.getString(worldName + ".display_name", worldName);

            player.sendMessage(MessageUtil.parse("<dark_gray>┃ </dark_gray><yellow>[" + worldName + "]</yellow><white> " + displayName + "</white>"));
            player.sendMessage(MessageUtil.parse("<dark_gray>┃ </dark_gray><gray>Click to teleport:</gray>"));
            player.sendMessage(MessageUtil.parse("<dark_gray>┃   </dark_gray><white>/mp chgdim_teleport " + worldName + "</white>"));
            player.sendMessage("");
        }

        if (DimensionManager.hasReturnLocation(player)) {
            player.sendMessage(MessageUtil.parse("<dark_gray>┃ </dark_gray><yellow>[/mp chgdim_return]</yellow><white> — return back</white>"));
            player.sendMessage("");
        }

        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        player.sendMessage("");
    }

    /**
     * Выполняет телепортацию в указанный мир.
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
                                "<dark_red>❌</dark_red> <red>Please wait</red> <yellow>{seconds}</yellow><red> seconds before using this again!</red>")
                        .replace("{seconds}", String.valueOf(remaining))));
                return true;
            }
        }

        // =========================
        // GET WORLD CONFIG
        // =========================
        FileConfiguration config = Main.getInstance().getConfig();
        ConfigurationSection worldsSection = config.getConfigurationSection("changedimmension.worlds");

        if (worldsSection == null || !worldsSection.contains(worldName)) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.world_not_configured", "<red>❌ World</red> <yellow>{world}</yellow> <red>not configured!</red>").replace("{world}", worldName)));
            return true;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.world_not_found",
                            "<dark_red>❌</dark_red> <red>World</red> <yellow>{world}</yellow> <red>not found!</red>")
                    .replace("{world}", worldName)));
            return true;
        }

        ConfigurationSection worldConfig = worldsSection.getConfigurationSection(worldName);

        double teleportX = worldConfig != null ? worldConfig.getDouble("x", 0) : 0;
        double teleportY = worldConfig != null ? worldConfig.getDouble("y", 64) : 64;
        double teleportZ = worldConfig != null ? worldConfig.getDouble("z", 0) : 0;
        float teleportYaw = worldConfig != null ? (float) worldConfig.getDouble("yaw", 0.0) : 0.0f;
        float teleportPitch = worldConfig != null ? (float) worldConfig.getDouble("pitch", 0.0) : 0.0f;

        // =========================
        // СОХРАНЯЕМ ТЕКУЩУЮ ПОЗИЦИЮ В БД (всегда, перед телепортацией)
        // =========================
        DimensionManager.saveReturnLocation(player);

        Location targetLocation = new Location(world, teleportX, teleportY, teleportZ, teleportYaw, teleportPitch);
        player.teleportAsync(targetLocation);
        cooldowns.put(playerUuid, now);            player.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.success",
                        "<green>✅</green> <white>Teleportation to</white> <yellow>{world}</yellow> <white>completed!</white>")
                .replace("{world}", worldName)));

        return true;
    }

    /**
     * Выполняет возврат в исходную точку.
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
        DimensionManager.removeReturnLocation(player);            player.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.return_success",
                        "<green>✅</green> <white>You have returned to your starting point!</white>")));

        return true;
    }

    public static void clearCooldown(UUID uuid) {
        cooldowns.remove(uuid);
    }
}
