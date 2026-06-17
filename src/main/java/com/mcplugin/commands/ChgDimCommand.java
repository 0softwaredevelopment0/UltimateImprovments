package com.mcplugin.commands;

import com.mcplugin.Main;
import com.mcplugin.util.MessageUtil;
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
        player.sendMessage("§6═══════════════════════════════════");
        player.sendMessage("§6  ✦ §fСмена измерения");
        player.sendMessage("§6═══════════════════════════════════");
        player.sendMessage("");

        FileConfiguration config = Main.getInstance().getConfig();
        ConfigurationSection worldsSection = config.getConfigurationSection("changedimmension.worlds");

        if (worldsSection == null) {
            player.sendMessage("§4❌ §cМиры не настроены в конфиге!");
            return;
        }

        for (String worldName : worldsSection.getKeys(false)) {
            String displayName = worldsSection.getString(worldName + ".display_name", worldName);

            player.sendMessage("§8┃ §e[" + worldName + "]§f " + displayName);
            player.sendMessage("§8┃ §7Нажмите чтобы телепортироваться:");
            player.sendMessage("§8┃   §f/mp chgdim_teleport " + worldName);
            player.sendMessage("");
        }

        if (DimensionManager.hasReturnLocation(player)) {
            player.sendMessage("§8┃ §e[/mp chgdim_return]§f — вернуться назад");
            player.sendMessage("");
        }

        player.sendMessage("§6═══════════════════════════════════");
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
                player.sendMessage(MessageUtil.parse(Main.getInstance().getConfig()
                        .getString("changedimmension.messages.cooldown",
                                "<dark_red>❌</dark_red> <red>Подождите</red> <yellow>{seconds}</yellow><red> сек перед повторным использованием!</red>")
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
            player.sendMessage("§4❌ §cМир §e" + worldName + "§c не настроен в конфиге!");
            return true;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage(MessageUtil.parse(Main.getInstance().getConfig()
                    .getString("changedimmension.messages.world_not_found",
                            "<dark_red>❌</dark_red> <red>Мир</red> <yellow>{world}</yellow> <red>не найден!</red>")
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
        // СОХРАНЯЕМ ТЕКУЩУЮ ПОЗИЦИЮ В БД
        // =========================
        if (!DimensionManager.hasReturnLocation(player)) {
            DimensionManager.saveReturnLocation(player);
        }

        Location targetLocation = new Location(world, teleportX, teleportY, teleportZ, teleportYaw, teleportPitch);
        player.teleportAsync(targetLocation);
        cooldowns.put(playerUuid, now);

        player.sendMessage(MessageUtil.parse(Main.getInstance().getConfig()
                .getString("changedimmension.messages.success",
                        "<green>✅</green> <white>Телепортация в мир</white> <yellow>{world}</yellow> <white>завершена!</white>")
                .replace("{world}", worldName)));

        return true;
    }

    /**
     * Выполняет возврат в исходную точку.
     */
    public static boolean teleportBack(Player player) {
        if (!DimensionManager.hasReturnLocation(player)) {
            player.sendMessage("§4❌ §cНет сохранённой точки для возврата!");
            return true;
        }

        Location returnLoc = DimensionManager.getReturnLocation(player);
        if (returnLoc == null) {
            player.sendMessage(MessageUtil.parse(Main.getInstance().getConfig()
                    .getString("changedimmension.messages.return_error",
                            "<dark_red>❌</dark_red> <red>Ошибка: точка возврата повреждена!</red>")));
            DimensionManager.removeReturnLocation(player);
            return true;
        }

        player.teleportAsync(returnLoc);
        DimensionManager.removeReturnLocation(player);

        player.sendMessage(MessageUtil.parse(Main.getInstance().getConfig()
                .getString("changedimmension.messages.return_success",
                        "<green>✅</green> <white>Вы вернулись в исходную точку!</white>")));

        return true;
    }

    public static void clearCooldown(UUID uuid) {
        cooldowns.remove(uuid);
    }
}
