package com.mcplugin.infrastructure.commands.subcommands;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class NearSubcommand {

    private NearSubcommand() {}

    /**
     * /mp near [radius]
     * <p>
     * Показывает игроков в радиусе {@code radius} блоков от отправителя.
     * Команда выключена по умолчанию (near.enabled: false в config.yml).
     * Максимальный радиус задаётся в конфиге (near.max_radius, по умолчанию 128).
     */
    public static boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Only players can use this command!</red>"));
            return true;
        }

        // Проверяем, включена ли команда в конфиге
        if (!Main.getInstance().getConfig().getBoolean("near.enabled", false)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ This command is disabled on this server.</red>"));
            return true;
        }

        int maxRadius = Main.getInstance().getConfig().getInt("near.max_radius", 128);

        // Парсим радиус
        int radius = maxRadius;
        if (args.length > 1) {
            try {
                radius = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(MessageUtil.parse("<red>❌ Invalid radius! Usage: </red><white>/mp near [radius]</white>"));
                return true;
            }
            if (radius < 1) radius = 1;
            if (radius > maxRadius) radius = maxRadius;
        }

        // Ищем игроков поблизости
        Location loc = player.getLocation();
        List<NearbyPlayer> nearby = new ArrayList<>();

        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(player)) continue;
            if (!target.getWorld().equals(loc.getWorld())) continue;

            double distance = loc.distance(target.getLocation());
            if (distance <= radius) {
                nearby.add(new NearbyPlayer(target.getName(), distance));
            }
        }

        // Сортируем по расстоянию
        nearby.sort(Comparator.comparingDouble(NearbyPlayer::distance));

        // Выводим результат
        if (nearby.isEmpty()) {
            sender.sendMessage(MessageUtil.parse(
                    "<gray>No players found within </gray><yellow>" + radius + "</yellow><gray> blocks.</gray>"
            ));
        } else {
            sender.sendMessage(MessageUtil.parse(
                    "<gray>Players within </gray><yellow>" + radius + "</yellow><gray> blocks (</gray><yellow>" + nearby.size() + "</yellow><gray>):</gray>"
            ));
            int index = 1;
            for (NearbyPlayer np : nearby) {
                String distStr = formatDistance(np.distance());
                sender.sendMessage(MessageUtil.parse(
                        "  <gray>" + index + ".</gray> <white>" + np.name() + "</white> <gray>—</gray> <yellow>" + distStr + "</yellow>"
                ));
                index++;
            }
        }

        return true;
    }

    private static String formatDistance(double distance) {
        int rounded = (int) Math.round(distance);
        if (rounded <= 1) {
            return "1 block";
        }
        return rounded + " blocks";
    }

    private record NearbyPlayer(String name, double distance) {}
}
