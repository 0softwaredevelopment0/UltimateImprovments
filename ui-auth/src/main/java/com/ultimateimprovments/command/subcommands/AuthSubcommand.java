package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;

import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.mechanics.security.auth.AuthDatabase;
import com.ultimateimprovments.mechanics.security.auth.AuthDialogs;
import com.ultimateimprovments.mechanics.security.auth.AuthManager;
import com.ultimateimprovments.mechanics.security.auth.AuthPrompt;
import com.ultimateimprovments.mechanics.security.auth.PasswordPolicy;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class AuthSubcommand {

    private AuthSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!Main.getInstance().getConfig().getBoolean("auth.enabled", true)) {
            CommandErrors.moduleDisabled(sender, "auth");
            return true;
        }
        if (args.length < 2) {
            // Bare /ui auth: before login it is the "show me the login window again"
            // shortcut (the only auth command allowed while frozen), otherwise usage.
            if (sender instanceof Player player && !isAuthenticated(player)) {
                return handleLogin(player);
            }
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.admin.usage",
                    "<red>❌ Usage: </red><white>/ui auth login|2fa|logout|changepassword|forcelogin|resetauth|chgpass|delsession</white>")));
            return true;
        }

        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "login" -> handleLogin(sender);
            case "2fa" -> handle2FA(sender, args);
            case "forcelogin" -> handleForceLogin(sender, args);
            case "resetauth" -> handleResetAuth(sender, args);
            case "delsession" -> handleDelSession(sender, args);
            case "logout" -> handleLogout(sender, args);
            case "chgpass" -> handleChgPass(sender, args);
            case "changepassword" -> handleSelfChangePassword(sender, args);
            default -> {
                sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Unknown subcommand: </red><white>" + args[1] + "</white>"));
                yield true;
            }
        };
    }

    /** True when the player passed authentication (or auth is off). */
    static boolean isAuthenticated(Player player) {
        AuthManager manager = AuthManager.getInstance();
        if (manager == null) return true;
        return manager.isAuthenticated(player.getUniqueId());
    }

    /**
     * {@code /ui auth login} — re-opens the auth dialog (the window is a client-side
     * Custom Screen, so the player may have lost it; this is the only auth command
     * allowed before login).
     */
    private static boolean handleLogin(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            CommandErrors.playerOnly(sender);
            return true;
        }
        if (AuthManager.getInstance() == null) {
            player.sendMessage(MessageUtil.parse("<red>❌ Authentication system is not initialized!</red>"));
            return true;
        }
        if (isAuthenticated(player)) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.already_authenticated",
                    "<gold>✦</gold> <white>You are already logged in!</white>")));
            return true;
        }
        player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.opening_auth_window",
                "<yellow>✦</yellow> <gray>Opening authorization window...</gray>")));
        AuthPrompt.openNow(player);
        return true;
    }

    // =========================================================================
    // TAB-COMPLETE
    // Suggestions are per-sender: before login only {@code login} is allowed,
    // after login the set depends on the permission nodes. An empty-string entry
    // is the SubCommandRegistry convention for "suggest nothing, do not fall back
    // to online player names".
    // =========================================================================
    public static List<String> tabComplete(CommandSender sender, String[] args) {
        if (args == null || args.length < 2) return List.of();
        String sub = args[1].toLowerCase(Locale.ROOT);

        if (args.length == 2) {
            return subcommandsFor(sender);
        }
        switch (sub) {
            case "2fa" -> {
                return args.length == 3 ? List.of("setup", "disable") : List.of("");
            }
            case "forcelogin", "resetauth", "delsession", "chgpass" -> {
                if (args.length == 3) return onlinePlayerNames();
                return List.of(""); // password field — never suggest player names
            }
            default -> {
                return List.of("");
            }
        }
    }

    /** Subcommand names the sender may actually use right now. */
    private static List<String> subcommandsFor(CommandSender sender) {
        if (sender instanceof Player player && !isAuthenticated(player)) {
            // Frozen, unauthenticated player: only the dialog re-open shortcut.
            return List.of("login");
        }
        List<String> names = new ArrayList<>(List.of("2fa", "logout", "changepassword"));
        if (sender.hasPermission("ui.command.auth.forcelogin")) names.add("forcelogin");
        if (sender.hasPermission("ui.command.auth.resetauth")) names.add("resetauth");
        if (sender.hasPermission("ui.command.auth.delsession")) names.add("delsession");
        if (sender.hasPermission("ui.command.auth.chgpass")) names.add("chgpass");
        return names;
    }

    private static List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) names.add(online.getName());
        return names;
    }

    @SuppressWarnings("deprecation")
    private static UUID getOfflineUuid(String name) {
        return Bukkit.getOfflinePlayer(name).getUniqueId();
    }

    private static boolean handle2FA(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            CommandErrors.playerOnly(sender);
            return true;
        }

        UUID uuid = player.getUniqueId();
        AuthManager mgr = AuthManager.getInstance();
        if (mgr == null) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Authorization system not initialized!</red>"));
            return true;
        }

        // /ui auth 2fa setup <github_username>
        if (args.length >= 3 && args[2].equalsIgnoreCase("setup")) {
            if (args.length < 4) {
                player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Usage: </red><white>/ui auth 2fa setup <github_username></white>"));
                return true;
            }
            if (!mgr.isAuthenticated(uuid)) {
                player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>You must be logged in to set up 2FA!</red>"));
                return true;
            }
            mgr.setupGithub2FA(uuid, args[3]);
            player.sendMessage("");
            player.sendMessage(MessageUtil.parse("<gold>✦ <white>2FA <dark_gray>— <gray>Setup"));
            player.sendMessage(MessageUtil.parse("<gray>━━━━━━━━━━━━━━━━━━━━━"));
            player.sendMessage(MessageUtil.parse("<green>✔ 2FA enabled!"));
            player.sendMessage(MessageUtil.parse("<gray>GitHub account: <white>" + args[3]));
            player.sendMessage(MessageUtil.parse("<gray>On next login you'll receive a clickable GitHub authorization link."));
            player.sendMessage(MessageUtil.parse("<gray>━━━━━━━━━━━━━━━━━━━━━"));
            player.sendMessage("");
            return true;
        }

        // /ui auth 2fa disable
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
            player.sendMessage(MessageUtil.parse("<red>❌ 2FA disabled."));
            return true;
        }

        // /ui auth 2fa — codes are no longer used, show the status
        if (args.length >= 3) {
            if (mgr.is2FAEnabled(uuid)) {
                player.sendMessage(MessageUtil.parse("<green>✔ 2FA enabled"));
                player.sendMessage(MessageUtil.parse("<gray>GitHub account: <white>" + mgr.getGithubUsername(uuid)));
                if (!com.ultimateimprovments.mechanics.security.auth.AuthConfig.isGithub2FAEnabled()) {
                    player.sendMessage(MessageUtil.parse("<red>⚠ GitHub 2FA is disabled in the config (auth.twofa.github.enabled: false)"));
                } else {
                    player.sendMessage(MessageUtil.parse("<gray>On login you'll get a clickable GitHub authorization link."));
                }
                player.sendMessage(MessageUtil.parse("<gray>Disable: <yellow>/ui auth 2fa disable"));
            } else {
                player.sendMessage(MessageUtil.parse("<red>❌ 2FA disabled"));
                player.sendMessage(MessageUtil.parse("<gray>Enable: <yellow>/ui auth 2fa setup <github_username>"));
            }
            return true;
        }

        // /ui auth 2fa — status
        if (mgr.is2FAEnabled(uuid)) {
            player.sendMessage(MessageUtil.parse("<green>✔ 2FA enabled"));
            player.sendMessage(MessageUtil.parse("<gray>GitHub account: <white>" + mgr.getGithubUsername(uuid)));
            if (!com.ultimateimprovments.mechanics.security.auth.AuthConfig.isGithub2FAEnabled()) {
                player.sendMessage(MessageUtil.parse("<red>⚠ GitHub 2FA is disabled in the config (auth.twofa.github.enabled: false)"));
            } else {
                player.sendMessage(MessageUtil.parse("<gray>On login you'll get a clickable GitHub authorization link."));
            }
            player.sendMessage(MessageUtil.parse("<gray>Disable: <yellow>/ui auth 2fa disable"));
        } else {
            player.sendMessage(MessageUtil.parse("<red>❌ 2FA disabled"));
            player.sendMessage(MessageUtil.parse("<gray>Enable: <yellow>/ui auth 2fa setup <github_username>"));
        }
        return true;
    }

    private static boolean handleForceLogin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ui.command.auth.forcelogin")) {
            CommandErrors.noPermission(sender, "ui.command.auth.forcelogin");
            return true;
        }
        if (args.length < 3) { sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Usage: </red><white>/ui auth forcelogin </white><gray><nick></gray>")); return true; }
        String name = args[2];
        UUID uuid = getOfflineUuid(name);
        if (!AuthDatabase.isRegistered(uuid)) { sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Player </red><yellow>" + name + "</yellow><red> is not registered!</red>")); return true; }
        AuthManager mgr = AuthManager.getInstance();
        if (mgr == null) { sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Authentication system is not initialized!</red>")); return true; }
        sender.sendMessage(mgr.forceLogin(uuid, name, sender.getName())
                ? MessageUtil.parse("<green>✔</green> <white>Player </white><yellow>" + name + "</yellow><white> force logged in.</white>")
                : MessageUtil.parse("<dark_red>❌</dark_red> <red>Failed to force login player </red><yellow>" + name + "</yellow>"));
        return true;
    }

    private static boolean handleResetAuth(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ui.command.auth.resetauth")) {
            CommandErrors.noPermission(sender, "ui.command.auth.resetauth");
            return true;
        }
        if (args.length < 3) { sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Usage: </red><white>/ui auth resetauth </white><gray><nick></gray>")); return true; }
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
        if (!sender.hasPermission("ui.command.auth.delsession")) {
            CommandErrors.noPermission(sender, "ui.command.auth.delsession");
            return true;
        }
        if (args.length < 3) { sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Usage: </red><white>/ui auth delsession </white><gray><nick></gray>")); return true; }
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

    /**
     * Chat-based logout: /ui auth logout — opens the confirmation dialog.
     * Confirming resets the stored session and kicks the player; no password is
     * involved, so nothing secret can leak through chat.
     */
    private static boolean handleLogout(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            CommandErrors.playerOnly(sender);
            return true;
        }
        AuthManager mgr = AuthManager.getInstance();
        if (mgr == null) {
            player.sendMessage(MessageUtil.parse("<red>❌ Authentication system is not initialized!</red>"));
            return true;
        }
        if (!mgr.isAuthenticated(player.getUniqueId())) {
            player.sendMessage(MessageUtil.parse("<red>❌ You are not logged in!</red>"));
            return true;
        }
        AuthDialogs.openLogoutConfirm(player);
        return true;
    }

    /**
     * Self-service password change: opens the dialog with the old and the new
     * password fields. Nothing is accepted through chat — command arguments would
     * end up in the console, chat-plugin logs and the client's command history.
     */
    private static boolean handleSelfChangePassword(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            CommandErrors.playerOnly(sender);
            return true;
        }
        AuthManager mgr = AuthManager.getInstance();
        if (mgr == null) {
            player.sendMessage(MessageUtil.parse("<red>❌ Authentication system is not initialized!</red>"));
            return true;
        }
        if (args.length > 2) {
            player.sendMessage(MessageUtil.parse(
                    "<yellow>⚠ Не пишите пароли в чат — откройте окно смены пароля.</yellow>"));
        }
        AuthDialogs.openChangePassword(player);
        return true;
    }

    /**
     * Admin password reset. A player with {@code ui.command.auth.chgpass} gets the
     * dialog (nickname + new password); the console keeps plain arguments, since
     * console input is not broadcast to chat logs.
     */
    private static boolean handleChgPass(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ui.command.auth.chgpass")) {
            CommandErrors.noPermission(sender, "ui.command.auth.chgpass");
            return true;
        }
        if (sender instanceof Player player) {
            if (args.length > 2) {
                player.sendMessage(MessageUtil.parse(
                        "<yellow>⚠ Не пишите пароли в чат — откройте окно смены пароля.</yellow>"));
            }
            AuthDialogs.openAdminChangePassword(player);
            return true;
        }
        if (args.length < 4) { sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Usage: </red><white>/ui auth chgpass </white><gray><nick> <new_password></gray>")); return true; }
        String name = args[2], newPass = args[3];
        String policyError = PasswordPolicy.validate(newPass);
        if (policyError != null) {
            sender.sendMessage(MessageUtil.parse(policyError));
            return true;
        }
        UUID uuid = getOfflineUuid(name);
        if (!AuthDatabase.isRegistered(uuid)) { sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Player </red><yellow>" + name + "</yellow><red> is not registered!</red>")); return true; }
        AuthManager mgr = AuthManager.getInstance();
        if (mgr == null) { sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Authentication system is not initialized!</red>")); return true; }
        boolean changed = mgr.changePassword(uuid, newPass);
        sender.sendMessage(changed
                ? MessageUtil.parse("<green>✔</green> <white>Password for player </white><yellow>" + name + "</yellow><white> changed.</white>")
                : MessageUtil.parse("<dark_red>❌</dark_red> <red>Failed to change password!</red>"));
        return true;
    }
}
