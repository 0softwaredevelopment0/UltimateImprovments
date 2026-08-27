package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;

import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Commands /ui heal and /ui feed — restore health/hunger to a player.
 * <p>
 * Settings in config.yml → heal_feed:
 * <ul>
 *   <li>enabled — enable/disable the commands</li>
 *   <li>heal_amount — how much HP to restore (0 = all)</li>
 *   <li>feed_amount — how much saturation to restore (0 = all)</li>
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
        if (!sender.hasPermission("ui.command.heal")) {
            CommandErrors.noPermission(sender);
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui heal <player></white>"));
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
            // Full HP
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
        if (!sender.hasPermission("ui.command.feed")) {
            CommandErrors.noPermission(sender);
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui feed <player></white>"));
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
            // Full saturation
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
     * Returns the heal/feed config section from config.yml.
     */
    private static ConfigurationSection getConfig() {
        return Main.getInstance().getConfig().getConfigurationSection("heal_feed");
    }

    /**
     * Returns the HP amount for heal from config.yml.
     * 0 = all HP.
     */
    public static double getHealAmount() {
        var cfg = getConfig();
        if (cfg == null) return 0;
        return cfg.getDouble("heal_amount", 0);
    }

    /**
     * Returns the saturation amount for feed from config.yml.
     * 0 = all saturation.
     */
    public static double getFeedAmount() {
        var cfg = getConfig();
        if (cfg == null) return 0;
        return cfg.getDouble("feed_amount", 0);
    }

    /**
     * Checks whether the command is enabled.
     */
    public static boolean isEnabled() {
        var cfg = getConfig();
        if (cfg == null) return true;
        return cfg.getBoolean("enabled", true);
    }

    /**
     * Tab-completion: suggests player names.
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
