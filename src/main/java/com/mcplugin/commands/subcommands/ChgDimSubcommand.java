package com.mcplugin.commands.subcommands;

import com.mcplugin.Main;
import com.mcplugin.commands.ChgDimGUI;
import com.mcplugin.commands.DimensionManager;
import com.mcplugin.config.MessagesManager;
import com.mcplugin.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.UUID;

public final class ChgDimSubcommand {

    private ChgDimSubcommand() {}

    private static final HashMap<UUID, Long> cooldowns = new HashMap<>();

    public static boolean execute(CommandSender sender, String[] args) {
        // chgdim - open menu
        if (args[0].equalsIgnoreCase("chgdim")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.player_only",
                                "<dark_red>❌</dark_red> <red>Только игрок может использовать эту команду!</red>")));
                return true;
            }
            if (!player.hasPermission("mcplugin.command.chgdim")) {
                player.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.no_permission",
                                "<dark_red>❌</dark_red> <red>У вас нет прав на эту команду!</red>")));
                return true;
            }
            ChgDimGUI.open(player);
            return true;
        }

        // chgdim_teleport <world>
        if (args[0].equalsIgnoreCase("chgdim_teleport")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.player_only", "<dark_red>❌</dark_red> <red>Only players can use this command!</red>")));
                return true;
            }
            if (!player.hasPermission("mcplugin.command.chgdim")) {
                player.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.no_permission", "<dark_red>❌</dark_red> <red>You do not have permission to use this command!</red>")));
                return true;
            }
            if (args.length < 2) return true;

            String worldName = args[1];
            UUID uuid = player.getUniqueId();
            long now = System.currentTimeMillis() / 1000;
            int cooldownSecs = Main.getInstance().getConfig().getInt("changedimmension.cooldown_seconds", 10);

            if (cooldowns.containsKey(uuid)) {
                long elapsed = now - cooldowns.get(uuid);
                if (elapsed < cooldownSecs) {
                    long remaining = cooldownSecs - elapsed;
                    player.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.cooldown",
                                    "<dark_red>❌</dark_red> <red>Подождите</red> <yellow>{seconds}</yellow><red> сек перед повторным использованием!</red>")
                            .replace("{seconds}", String.valueOf(remaining))));
                    return true;
                }
            }

            var worldsSection = Main.getInstance().getConfig().getConfigurationSection("changedimmension.worlds");
            if (worldsSection == null || !worldsSection.contains(worldName)) {
                player.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.world_not_configured", "<red>❌ World</red> <yellow>{world}</yellow> <red>not configured!</red>").replace("{world}", worldName)));
                return true;
            }

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                player.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.world_not_found",
                                "<dark_red>❌</dark_red> <red>Мир</red> <yellow>{world}</yellow> <red>не найден!</red>")
                        .replace("{world}", worldName)));
                return true;
            }

            var worldConfig = worldsSection.getConfigurationSection(worldName);
            double x = worldConfig != null ? worldConfig.getDouble("x", 0) : 0;
            double y = worldConfig != null ? worldConfig.getDouble("y", 64) : 64;
            double z = worldConfig != null ? worldConfig.getDouble("z", 0) : 0;
            float yaw = worldConfig != null ? (float) worldConfig.getDouble("yaw", 0) : 0;
            float pitch = worldConfig != null ? (float) worldConfig.getDouble("pitch", 0) : 0;

            if (!DimensionManager.hasReturnLocation(player)) {
                DimensionManager.saveReturnLocation(player);
            }

            player.teleportAsync(new Location(world, x, y, z, yaw, pitch));
            cooldowns.put(uuid, now);

            player.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.success",
                            "<green>✅</green> <white>Телепортация в мир</white> <yellow>{world}</yellow> <white>завершена!</white>")
                    .replace("{world}", worldName)));
            return true;
        }

        // chgdim_return
        if (args[0].equalsIgnoreCase("chgdim_return")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.player_only", "<dark_red>❌</dark_red> <red>Only players can use this command!</red>")));
                return true;
            }
            if (!player.hasPermission("mcplugin.command.chgdim")) {
                player.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.no_permission", "<dark_red>❌</dark_red> <red>You do not have permission to use this command!</red>")));
                return true;
            }
            if (!DimensionManager.hasReturnLocation(player)) {
                player.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.no_return_point", "<red>❌ No saved return point!</red>")));
                return true;
            }
            Location returnLoc = DimensionManager.getReturnLocation(player);
            if (returnLoc == null) {
                player.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.return_error",
                                "<dark_red>❌</dark_red> <red>Ошибка: точка возврата повреждена!</red>")));
                DimensionManager.removeReturnLocation(player);
                return true;
            }
            player.teleportAsync(returnLoc);
            DimensionManager.removeReturnLocation(player);
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.return_success",
                            "<green>✅</green> <white>Вы вернулись в исходную точку!</white>")));
            return true;
        }
        return false;
    }

    public static void tabCompleteChgdimWorlds(java.util.List<String> completions) {
        var worldsSection = Main.getInstance().getConfig().getConfigurationSection("changedimmension.worlds");
        if (worldsSection != null) {
            completions.addAll(worldsSection.getKeys(false));
        }
    }
}
