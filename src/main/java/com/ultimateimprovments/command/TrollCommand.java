package com.ultimateimprovments.command;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.SoundUtil;

import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collections;
import java.util.List;

/**
 * Fake commands to troll hackers.
 * <p>
 * Registered as regular commands {@code /forceop} and {@code /crash}
 * (without the /ui prefix) and do NOTHING real:
 * <ul>
 *   <li><b>/forceop</b> — sends the player fake messages as if the server
 *       granted them OP. Real OP is NOT granted (setOp is never called).</li>
 *   <li><b>/crash</b> — sends the player fake server-crash messages
 *       and after {@code troll.crash.delay_seconds} kicks them with the
 *       {@code troll.crash.kick_message} text (default "Server closed").</li>
 * </ul>
 * All settings (enable, messages, sounds, delay, permissions) are in config.yml
 * under the {@code troll:} section. Works only for players.
 */
public class TrollCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        FileConfiguration cfg = Main.getInstance().getConfig();
        if (!cfg.getBoolean("troll.enabled", true)) {
            player.sendMessage(MessageUtil.parse("<red>❌ This command is disabled.</red>"));
            return true;
        }

        return switch (cmd.getName().toLowerCase()) {
            case "forceop" -> handleForceOp(player);
            case "crash" -> handleCrash(player);
            default -> false;
        };
    }

    // =========================
    // /forceop — fake OP grant
    // =========================
    private boolean handleForceOp(Player player) {
        FileConfiguration cfg = Main.getInstance().getConfig();
        if (!cfg.getBoolean("troll.forceop.enabled", true)) return true;

        if (!checkPermission(player, cfg.getString("troll.forceop.permission", ""))) return true;

        // Fake messages — look like the server granted OP
        for (String msg : getMessageList(cfg, "troll.forceop.messages")) {
            if (msg == null || msg.isEmpty()) continue;
            player.sendMessage(MessageUtil.parse(msg.replace("%player%", player.getName()), player));
        }

        // ActionBar (optional)
        String actionbar = cfg.getString("troll.forceop.actionbar", "");
        if (actionbar != null && !actionbar.isEmpty()) {
            player.sendActionBar(MessageUtil.parse(actionbar.replace("%player%", player.getName()), player));
        }

        // Sound (optional)
        Sound sound = SoundUtil.getSound(cfg.getString("troll.forceop.sound", ""));
        SoundUtil.playSound(player, sound, 1.0f, 1.0f);

        if (cfg.getBoolean("troll.forceop.log_to_console", true)) {
            ConsoleLogger.info("[Troll] " + player.getName() + " used /forceop (fake OP — no OP granted).");
        }
        return true;
    }

    // =========================
    // /crash — fake server crash + kick
    // =========================
    private boolean handleCrash(Player player) {
        FileConfiguration cfg = Main.getInstance().getConfig();
        if (!cfg.getBoolean("troll.crash.enabled", true)) return true;

        if (!checkPermission(player, cfg.getString("troll.crash.permission", ""))) return true;

        // Fake server-crash messages
        for (String msg : getMessageList(cfg, "troll.crash.messages")) {
            if (msg == null || msg.isEmpty()) continue;
            player.sendMessage(MessageUtil.parse(msg, player));
        }

        // Sound (optional)
        Sound sound = SoundUtil.getSound(cfg.getString("troll.crash.sound", ""));
        SoundUtil.playSound(player, sound, 1.0f, 1.0f);

        int delaySeconds = Math.max(0, cfg.getInt("troll.crash.delay_seconds", 2));
        String rawKick = cfg.getString("troll.crash.kick_message", "Server closed");
        final String kickMessage = (rawKick == null || rawKick.isEmpty()) ? "Server closed" : rawKick;

        if (cfg.getBoolean("troll.crash.log_to_console", true)) {
            ConsoleLogger.info("[Troll] " + player.getName()
                    + " used /crash (fake crash — kicking in " + delaySeconds + "s).");
        }

        // Kick the player after delay seconds with the "Server closed" text
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    player.kickPlayer(MessageUtil.legacy(kickMessage));
                }
            }
        }.runTaskLater(Main.getInstance(), delaySeconds * 20L);

        return true;
    }

    /**
     * Checks the command permission. Empty permission = available to everyone.
     */
    private boolean checkPermission(Player player, String permission) {
        if (permission == null || permission.isEmpty()) return true;
        if (player.hasPermission(permission)) return true;
        CommandErrors.noPermission(player);
        return false;
    }

    /**
     * Reads the message list, accepting either a YAML list or a single string.
     */
    private static List<String> getMessageList(FileConfiguration cfg, String path) {
        if (cfg.isString(path)) {
            return List.of(cfg.getString(path, ""));
        }
        return cfg.getStringList(path);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        return Collections.emptyList();
    }
}
