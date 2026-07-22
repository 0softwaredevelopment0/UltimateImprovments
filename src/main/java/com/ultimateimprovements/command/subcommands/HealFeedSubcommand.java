package com.ultimateimprovements.command.subcommands;

import com.ultimateimprovements.config.MessagesManager;
import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Команды /mp heal и /mp feed — восстанавливают здоровье/голод игроку.
 * <p>
 * Настройки в config.yml → heal_feed:
 * <ul>
 *   <li>enabled — вкл/выкл команды</li>
 *   <li>heal_amount — сколько хп восстанавливать (0 = всё)</li>
 *   <li>feed_amount — сколько сытости восстанавливать (0 = всё)</li>
 * </ul>
 */
public final class HealFeedSubcommand {

    private HealFeedSubcommand() {}

    // =========================
    // HEAL
    // =========================
    public static boolean heal(CommandSender sender, String[] args) {
        if (!isEnabled()) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Heal/Feed commands are disabled in config!</red>"));
            return true;
        }
        if (!sender.hasPermission("mcplugin.command.heal")) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("general.no_permission", "<red>❌ You don't have permission to use this command!</red>")));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/mp heal <player></white>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.vanish_player_not_found",
                    "<red>❌ Player</red> <yellow>%player%</yellow> <red>not found!</red>")
                    .replace("%player%", args[1])));
            return true;
        }

        double amount = getHealAmount();
        if (amount <= 0) {
            // Всё хп
            target.setHealth(target.getAttribute(Attribute.MAX_HEALTH).getDefaultValue());
        } else {
            double newHealth = Math.min(target.getHealth() + amount,
                    target.getAttribute(Attribute.MAX_HEALTH).getDefaultValue());
            target.setHealth(newHealth);
        }

        String confirm = "<green>✔</green> <white>Player</white> <yellow>" + target.getName() + "</yellow> <white>has been healed.</white>";
        sender.sendMessage(MessageUtil.parse(confirm));
        if (!sender.equals(target)) {
            target.sendMessage(MessageUtil.parse("<green>✔</green> <white>You have been healed by</white> <yellow>" + sender.getName() + "</yellow><white>.</white>"));
        }
        return true;
    }

    // =========================
    // FEED
    // =========================
    public static boolean feed(CommandSender sender, String[] args) {
        if (!isEnabled()) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Heal/Feed commands are disabled in config!</red>"));
            return true;
        }
        if (!sender.hasPermission("mcplugin.command.feed")) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("general.no_permission", "<red>❌ You don't have permission to use this command!</red>")));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/mp feed <player></white>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.vanish_player_not_found",
                    "<red>❌ Player</red> <yellow>%player%</yellow> <red>not found!</red>")
                    .replace("%player%", args[1])));
            return true;
        }

        double amount = getFeedAmount();
        if (amount <= 0) {
            // Всю сытость
            target.setFoodLevel(20);
            target.setSaturation(20);
            target.setExhaustion(0);
        } else {
            int newFood = Math.min((int) (target.getFoodLevel() + amount), 20);
            target.setFoodLevel(newFood);
        }

        String confirm = "<green>✔</green> <white>Player</white> <yellow>" + target.getName() + "</yellow> <white>has been fed.</white>";
        sender.sendMessage(MessageUtil.parse(confirm));
        if (!sender.equals(target)) {
            target.sendMessage(MessageUtil.parse("<green>✔</green> <white>You have been fed by</white> <yellow>" + sender.getName() + "</yellow><white>.</white>"));
        }
        return true;
    }

    // =========================
    // CONFIG HELPERS
    // =========================
    /**
     * Возвращает количество хп для heal из config.yml.
     * 0 = всё хп.
     */
    private static ConfigurationSection getConfig() {
        return Main.getInstance().getConfig().getConfigurationSection("heal_feed");
    }

    /**
     * Возвращает количество хп для heal из config.yml.
     * 0 = всё хп.
     */
    public static double getHealAmount() {
        var cfg = getConfig();
        if (cfg == null) return 0;
        return cfg.getDouble("heal_amount", 0);
    }

    /**
     * Возвращает количество сытости для feed из config.yml.
     * 0 = вся сытость.
     */
    public static double getFeedAmount() {
        var cfg = getConfig();
        if (cfg == null) return 0;
        return cfg.getDouble("feed_amount", 0);
    }

    /**
     * Проверяет, включена ли команда.
     */
    public static boolean isEnabled() {
        var cfg = getConfig();
        if (cfg == null) return true;
        return cfg.getBoolean("enabled", true);
    }

    /**
     * Таб-комплишн: подсказывает имена игроков.
     */
    public static List<String> tabComplete(String[] args) {
        if (args.length == 2) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
