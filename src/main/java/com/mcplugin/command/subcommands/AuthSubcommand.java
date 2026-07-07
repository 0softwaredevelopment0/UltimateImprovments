package com.mcplugin.command.subcommands;

import com.mcplugin.core.Main;
import com.mcplugin.mechanics.security.auth.AuthDatabase;
import com.mcplugin.mechanics.security.auth.AuthGUI;
import com.mcplugin.mechanics.security.auth.AuthManager;
import com.mcplugin.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class AuthSubcommand {

    private AuthSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!Main.getInstance().getConfig().getBoolean("auth.enabled", true)) {
            sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Authentication system is disabled in config!</red>"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Usage: </red><white>/mp auth login|register|2fa|forcelogin|resetauth|chgpass|delsession|logout</white>"));
            return true;
        }

        return switch (args[1].toLowerCase()) {
            case "login" -> handlePlayerLogin(sender, args);
            case "register" -> handlePlayerRegister(sender, args);
            case "2fa" -> handle2FA(sender, args);
            case "forcelogin" -> handleForceLogin(sender, args);
            case "resetauth" -> handleResetAuth(sender, args);
            case "delsession" -> handleDelSession(sender, args);
            case "logout" -> handleLogout(sender);
            case "chgpass" -> handleChgPass(sender, args);
            default -> {
                sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Unknown subcommand: </red><white>" + args[1] + "</white>"));
                yield true;
            }
        };
    }

    @SuppressWarnings("deprecation")
    private static UUID getOfflineUuid(String name) {
        return Bukkit.getOfflinePlayer(name).getUniqueId();
    }

    private static boolean handle2FA(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Only players can use this command!</red>"));
            return true;
        }

        UUID uuid = player.getUniqueId();
        AuthManager mgr = AuthManager.getInstance();
        if (mgr == null) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Authorization system not initialized!</red>"));
            return true;
        }

        // /mp auth 2fa setup <chat_id>
        if (args.length >= 3 && args[2].equalsIgnoreCase("setup")) {
            if (args.length < 4) {
                player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Usage: </red><white>/mp auth 2fa setup <telegram_chat_id></white>"));
                return true;
            }
            if (!mgr.isAuthenticated(uuid)) {
                player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>You must be logged in to set up 2FA!</red>"));
                return true;
            }
            mgr.setup2FA(uuid, args[3]);
            player.sendMessage("");
            player.sendMessage("§6✦ §f2FA §8— §7Setup");
            player.sendMessage("§7━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("§a✔ 2FA enabled!");
            player.sendMessage("§7Chat ID: §f" + args[3]);
            player.sendMessage("§7You'll receive a confirmation request in Telegram on next login.");
            player.sendMessage("§7━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("");
            return true;
        }

        // /mp auth 2fa disable
        if (args.length >= 3 && args[2].equalsIgnoreCase("disable")) {
            if (!mgr.isAuthenticated(uuid)) {
                player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>You must be logged in to disable 2FA!</red>"));
                return true;
            }
            if (!mgr.is2FAEnabled(uuid)) {
                player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>2FA is not enabled!</red>"));
                return true;
            }
            mgr.disable2FA(uuid);
            player.sendMessage("§c✖ 2FA disabled.");
            return true;
        }

        // /mp auth 2fa — коды больше не используются, показываем статус
        if (args.length >= 3) {
            if (mgr.is2FAEnabled(uuid)) {
                player.sendMessage("§a✔ 2FA enabled");
                player.sendMessage("§7Chat ID: §f" + mgr.get2FAChatId(uuid));
                player.sendMessage("§7Click \"Confirm\" in Telegram when logging in.");
                player.sendMessage("§7Disable: §e/mp auth 2fa disable");
            } else {
                player.sendMessage("§c✖ 2FA disabled");
                player.sendMessage("§7Enable: §e/mp auth 2fa setup <telegram_chat_id>");
            }
            return true;
        }

        // /mp auth 2fa — статус
        if (mgr.is2FAEnabled(uuid)) {
            player.sendMessage("§a✔ 2FA enabled");
            player.sendMessage("§7Chat ID: §f" + mgr.get2FAChatId(uuid));
            player.sendMessage("§7Disable: §e/mp auth 2fa disable");
        } else {
            player.sendMessage("§c✖ 2FA disabled");
            player.sendMessage("§7Enable: §e/mp auth 2fa setup <telegram_chat_id>");
        }
        return true;
    }

    private static boolean handlePlayerLogin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Only players can use this command!</red>"));
            return true;
        }
        AuthManager mgr = AuthManager.getInstance();
        if (mgr == null) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Authorization system is not initialized!</red>"));
            return true;
        }
        if (mgr.isAuthenticated(player.getUniqueId())) {
            player.sendMessage(MessageUtil.parse("<gold>✦</gold> <white>You are already logged in!</white>"));
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Usage: </red><white>/mp auth login <password></white>"));
            return true;
        }
        String password = args[2];
        mgr.handlePasswordSubmit(player, password);
        return true;
    }

    private static boolean handlePlayerRegister(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Only players can use this command!</red>"));
            return true;
        }
        AuthManager mgr = AuthManager.getInstance();
        if (mgr == null) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Authorization system is not initialized!</red>"));
            return true;
        }
        if (mgr.isAuthenticated(player.getUniqueId())) {
            player.sendMessage(MessageUtil.parse("<gold>✦</gold> <white>You are already logged in!</white>"));
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Usage: </red><white>/mp auth register <password></white>"));
            return true;
        }
        String password = args[2];
        mgr.handlePasswordSubmit(player, password);
        return true;
    }

    private static boolean handleForceLogin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcplugin.command.auth.forcelogin")) {
            sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>You don't have permission to force login!</red>"));
            return true;
        }
        if (args.length < 3) { sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Usage: </red><white>/mp auth forcelogin </white><gray><nick></gray>")); return true; }
        String name = args[2];
        UUID uuid = getOfflineUuid(name);
        if (!AuthDatabase.isRegistered(uuid)) { sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Player </red><yellow>" + name + "</yellow><red> is not registered!</red>")); return true; }
        AuthManager mgr = AuthManager.getInstance();
        if (mgr == null) { sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Authentication system is not initialized!</red>")); return true; }
        sender.sendMessage(mgr.forceLogin(uuid)
                ? MessageUtil.parse("<green>✔</green> <white>Player </white><yellow>" + name + "</yellow><white> force logged in.</white>")
                : MessageUtil.parse("<dark_red>❌</dark_red> <red>Failed to force login player </red><yellow>" + name + "</yellow>"));
        return true;
    }

    private static boolean handleResetAuth(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcplugin.command.auth.resetauth")) {
            sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>You don't have permission to reset authentication!</red>"));
            return true;
        }
        if (args.length < 3) { sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Usage: </red><white>/mp auth resetauth </white><gray><nick></gray>")); return true; }
        String name = args[2];
        UUID uuid = getOfflineUuid(name);
        if (!AuthDatabase.isRegistered(uuid)) { sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Player </red><yellow>" + name + "</yellow><red> is not registered!</red>")); return true; }
        AuthManager mgr = AuthManager.getInstance();
        if (mgr == null) { sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Authentication system is not initialized!</red>")); return true; }
        sender.sendMessage(mgr.resetAuth(uuid)
                ? MessageUtil.parse("<green>✔</green> <white>Player </white><yellow>" + name + "</yellow><white>'s registration has been completely deleted.</white>")
                : MessageUtil.parse("<dark_red>❌</dark_red> <red>Failed to delete registration!</red>"));
        return true;
    }

    private static boolean handleDelSession(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcplugin.command.auth.delsession")) {
            sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>You don't have permission to reset a session!</red>"));
            return true;
        }
        if (args.length < 3) { sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Usage: </red><white>/mp auth delsession </white><gray><nick></gray>")); return true; }
        String name = args[2];
        UUID uuid = getOfflineUuid(name);
        if (!AuthDatabase.isRegistered(uuid)) { sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Player </red><yellow>" + name + "</yellow><red> is not registered!</red>")); return true; }
        AuthManager mgr = AuthManager.getInstance();
        if (mgr == null) { sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Authentication system is not initialized!</red>")); return true; }
        sender.sendMessage(mgr.deleteSession(uuid)
                ? MessageUtil.parse("<green>✔</green> <white>Session for player </white><yellow>" + name + "</yellow><white> has been reset.</white>")
                : MessageUtil.parse("<dark_red>❌</dark_red> <red>Failed to reset session!</red>"));
        return true;
    }

    private static boolean handleLogout(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Only players can use this command!</red>")); return true; }
        if (!AuthManager.getInstance().isAuthenticated(player.getUniqueId())) { player.sendMessage(MessageUtil.parse("<red>❌ You are not authenticated!</red>")); return true; }
        AuthGUI.openLogout(player);
        return true;
    }

    private static boolean handleChgPass(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcplugin.command.auth.chgpass")) {
            sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>You don't have permission to change passwords!</red>"));
            return true;
        }
        if (args.length < 4) { sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Usage: </red><white>/mp auth chgpass </white><gray><nick> <new_password></gray>")); return true; }
        String name = args[2], newPass = args[3];
        int minLen = Main.getInstance().getConfig().getInt("auth.min_password_length", 8);
        int maxLen = Main.getInstance().getConfig().getInt("auth.max_password_length", 32);
        if (newPass.length() < minLen) { sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Password must be at least " + minLen + " characters!</red>")); return true; }
        if (newPass.length() > maxLen) { sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Password must not exceed " + maxLen + " characters!</red>")); return true; }
        UUID uuid = getOfflineUuid(name);
        if (!AuthDatabase.isRegistered(uuid)) { sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Player </red><yellow>" + name + "</yellow><red> is not registered!</red>")); return true; }
        AuthManager mgr = AuthManager.getInstance();
        if (mgr == null) { sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Authentication system is not initialized!</red>")); return true; }
        sender.sendMessage(mgr.changePassword(uuid, newPass)
                ? MessageUtil.parse("<green>✔</green> <white>Password for player </white><yellow>" + name + "</yellow><white> changed.</white>")
                : MessageUtil.parse("<dark_red>❌</dark_red> <red>Failed to change password!</red>"));
        return true;
    }
}
