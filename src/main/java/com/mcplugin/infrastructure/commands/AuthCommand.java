package com.mcplugin.infrastructure.commands;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.mechanics.security.auth.AuthDatabase;
import com.mcplugin.mechanics.security.auth.AuthGUI;
import com.mcplugin.mechanics.security.auth.AuthManager;
import com.mcplugin.infrastructure.config.MessagesManager;
import com.mcplugin.infrastructure.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Обрабатывает команду /mp auth — управление системой авторизации.
 */
public class AuthCommand {

    @SuppressWarnings("deprecation")
    private static UUID getOfflineUuid(String playerName) {
        return Bukkit.getOfflinePlayer(playerName).getUniqueId();
    }

    public static boolean execute(CommandSender sender, String[] args) {
        if (!Main.getInstance().getConfig().getBoolean("auth.enabled", true)) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.admin.system_disabled", "<red>❌ Authorization system is disabled in config!</red>")));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/mp auth login|register|forcelogin|resetauth|chgpass|delsession|logout</white>"));
            return true;
        }

        String subCmd = args[1].toLowerCase();
        switch (subCmd) {
            case "login" -> handlePlayerLogin(sender, args);
            case "register" -> handlePlayerRegister(sender, args);
            case "forcelogin" -> handleForceLogin(sender, args);
            case "resetauth" -> handleResetAuth(sender, args);
            case "delsession" -> handleDelSession(sender, args);
            case "logout" -> handleLogout(sender);
            case "chgpass" -> handleChgPass(sender, args);
            default -> sender.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.admin.unknown_subcommand", "<red>❌ Unknown subcommand: </red><white>{subcommand}</white>").replace("{subcommand}", args[1])));
        }
        return true;
    }

    private static void handlePlayerLogin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Only players can use this command!</red>"));
            return;
        }
        if (args.length < 3) {
            player.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/mp auth login <password></white>"));
            return;
        }
        String password = args[2];
        AuthManager manager = AuthManager.getInstance();
        if (manager == null) {
            player.sendMessage(MessageUtil.parse("<red>❌ Authorization system is not initialized!</red>"));
            return;
        }
        manager.handlePasswordSubmit(player, password);
    }

    private static void handlePlayerRegister(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Only players can use this command!</red>"));
            return;
        }
        if (args.length < 3) {
            player.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/mp auth register <password></white>"));
            return;
        }
        String password = args[2];
        AuthManager manager = AuthManager.getInstance();
        if (manager == null) {
            player.sendMessage(MessageUtil.parse("<red>❌ Authorization system is not initialized!</red>"));
            return;
        }
        manager.handlePasswordSubmit(player, password);
    }

    private static void handleForceLogin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcplugin.command.auth.forcelogin")) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.admin.forcelogin_no_permission", "<red>❌ You don't have permission to force log in!</red>")));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.admin.forcelogin_usage", "<red>❌ Usage: </red><white>/mp auth forcelogin <nick></white>")));
            return;
        }

        String targetName = args[2];
        UUID targetUuid = getOfflineUuid(targetName);

        if (!AuthDatabase.isRegistered(targetUuid)) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.admin.forcelogin_not_registered", "<red>❌ Player</red> <yellow>{player}</yellow> <red>is not registered!</red>").replace("{player}", targetName)));
            return;
        }

        AuthManager manager = AuthManager.getInstance();
        if (manager == null) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.admin.forcelogin_not_initialized", "<red>❌ Authorization system is not initialized!</red>")));
            return;
        }

        if (manager.forceLogin(targetUuid)) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.admin.forcelogin_success", "<green>✔</green> <white>Player</white> <yellow>{player}</yellow> <white>force authorized.</white>").replace("{player}", targetName)));
        } else {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.admin.forcelogin_fail", "<red>❌ Failed to authorize player</red> <yellow>{player}</yellow>").replace("{player}", targetName)));
        }
    }

    private static void handleResetAuth(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcplugin.command.auth.resetauth")) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.admin.resetauth_no_permission", "<red>❌ You don't have permission to reset authorization!</red>")));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.admin.resetauth_usage", "<red>❌ Usage: </red><white>/mp auth resetauth <nick></white>")));
            return;
        }

        String targetName = args[2];
        UUID targetUuid = getOfflineUuid(targetName);

        if (!AuthDatabase.isRegistered(targetUuid)) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.admin.resetauth_not_registered", "<red>❌ Player</red> <yellow>{player}</yellow> <red>is not registered!</red>").replace("{player}", targetName)));
            return;
        }

        AuthManager manager = AuthManager.getInstance();
        if (manager == null) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.admin.resetauth_not_initialized", "<red>❌ Authorization system is not initialized!</red>")));
            return;
        }

        if (manager.resetAuth(targetUuid)) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.admin.resetauth_success", "<green>✔</green> <white>Registration of player</white> <yellow>{player}</yellow> <white>completely removed.</white>").replace("{player}", targetName)));
        } else {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.admin.resetauth_fail", "<red>❌ Failed to delete registration of player</red> <yellow>{player}</yellow>").replace("{player}", targetName)));
        }
    }

    private static void handleDelSession(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcplugin.command.auth.delsession")) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.admin.delsession_no_permission", "<red>❌ You don't have permission to reset sessions!</red>")));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.admin.delsession_usage", "<red>❌ Usage: </red><white>/mp auth delsession <nick></white>")));
            return;
        }

        String targetName = args[2];
        UUID targetUuid = getOfflineUuid(targetName);

        if (!AuthDatabase.isRegistered(targetUuid)) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.admin.delsession_not_registered", "<red>❌ Player</red> <yellow>{player}</yellow> <red>is not registered!</red>").replace("{player}", targetName)));
            return;
        }

        AuthManager manager = AuthManager.getInstance();
        if (manager == null) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.admin.delsession_not_initialized", "<red>❌ Authorization system is not initialized!</red>")));
            return;
        }

        if (manager.deleteSession(targetUuid)) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.admin.delsession_success", "<green>✔</green> <white>Session of player</white> <yellow>{player}</yellow> <white>reset (logout).</white>").replace("{player}", targetName)));
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.admin.delsession_info", "<gray>They will need to enter their password again on next login.</gray>")));
        } else {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.admin.delsession_fail", "<red>❌ Failed to reset session of player</red> <yellow>{player}</yellow>").replace("{player}", targetName)));
        }
    }

    private static void handleLogout(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.admin.player_only", "<red>❌ Only players can use this command!</red>")));
            return;
        }

        if (!AuthManager.getInstance().isAuthenticated(player.getUniqueId())) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.admin.not_authenticated", "<red>❌ You are not logged in!</red>")));
            return;
        }

        AuthGUI.openLogout(player);
    }

    private static void handleChgPass(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcplugin.command.auth.chgpass")) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.admin.chgpass_no_permission", "<red>❌ You don't have permission to change passwords!</red>")));
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.admin.chgpass_usage", "<red>❌ Usage: </red><white>/mp auth chgpass <nick> <new_password></white>")));
            return;
        }

        String targetName = args[2];
        String newPassword = args[3];

        int minLen = Main.getInstance().getConfig().getInt("auth.min_password_length", 8);
        int maxLen = Main.getInstance().getConfig().getInt("auth.max_password_length", 32);
        if (newPassword.length() < minLen) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.password_too_short", "<red>❌ Password must be at least </red><yellow>{min}</yellow><red> characters!</red>").replace("{min}", String.valueOf(minLen))));
            return;
        }
        if (newPassword.length() > maxLen) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.password_too_long", "<red>❌ Password must not exceed </red><yellow>{max}</yellow><red> characters!</red>").replace("{max}", String.valueOf(maxLen))));
            return;
        }

        UUID targetUuid = getOfflineUuid(targetName);

        if (!AuthDatabase.isRegistered(targetUuid)) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.admin.chgpass_not_registered", "<red>❌ Player</red> <yellow>{player}</yellow> <red>is not registered!</red>").replace("{player}", targetName)));
            return;
        }

        AuthManager manager = AuthManager.getInstance();
        if (manager == null) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.admin.chgpass_not_initialized", "<red>❌ Authorization system is not initialized!</red>")));
            return;
        }

        if (manager.changePassword(targetUuid, newPassword)) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.admin.chgpass_success", "<green>✔</green> <white>Password of player</white> <yellow>{player}</yellow> <white>changed to</white> <green>{password}</green><white>.</white>").replace("{player}", targetName).replace("{password}", newPassword)));
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.admin.chgpass_session_reset", "<gray>Session reset — player must log in again.</gray>")));
        } else {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.admin.chgpass_fail", "<red>❌ Failed to change password of player</red> <yellow>{player}</yellow>").replace("{player}", targetName)));
        }
    }
}
