package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;

import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.mechanics.security.auth.AuthConfig;
import com.ultimateimprovments.mechanics.security.auth.AuthDatabase;
import com.ultimateimprovments.mechanics.security.auth.AuthDialogs;
import com.ultimateimprovments.mechanics.security.auth.AuthManager;
import com.ultimateimprovments.mechanics.security.auth.AuthPrompt;
import com.ultimateimprovments.mechanics.security.auth.PasswordPolicy;
import com.ultimateimprovments.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class AuthSubcommand {

    private AuthSubcommand() {}

    /**
     * Config message for the current language, parsed to a Component. Optional
     * {@code replacements} are (placeholder, value) pairs substituted before parsing.
     */
    private static Component msg(String path, String def, String... replacements) {
        String text = MessagesManager.getString(path, def);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        return MessageUtil.parse(text);
    }

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
                sender.sendMessage(msg("auth.admin.unknown_subcommand",
                        "<red>❌ Unknown subcommand: </red><white>%subcommand%</white>",
                        "%subcommand%", args[1]));
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
            player.sendMessage(msg("auth.messages.not_initialized",
                    "<red>❌ Authorization system is not initialized!</red>"));
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
            player.sendMessage(msg("auth.messages.not_initialized",
                    "<red>❌ Authorization system is not initialized!</red>"));
            return true;
        }

        // /ui auth 2fa setup <github_username>
        if (args.length >= 3 && args[2].equalsIgnoreCase("setup")) {
            if (args.length < 4) {
                player.sendMessage(msg("auth.twofa.usage_setup",
                        "<dark_red>❌</dark_red> <red>Usage: </red><white>/ui auth 2fa setup <github_username></white>"));
                return true;
            }
            if (!mgr.isAuthenticated(uuid)) {
                player.sendMessage(msg("auth.twofa.login_required_setup",
                        "<dark_red>❌</dark_red> <red>You must be logged in to set up 2FA!</red>"));
                return true;
            }
            mgr.setupGithub2FA(uuid, args[3]);
            player.sendMessage("");
            player.sendMessage(msg("auth.twofa.setup_title",
                    "<gold>✦ <white>2FA <dark_gray>— <gray>Setup"));
            player.sendMessage(msg("auth.twofa.divider", "<gray>━━━━━━━━━━━━━━━━━━━━━"));
            player.sendMessage(msg("auth.twofa.setup_enabled", "<green>✔ 2FA enabled!"));
            player.sendMessage(msg("auth.twofa.github_account",
                    "<gray>GitHub account: <white>%account%", "%account%", args[3]));
            player.sendMessage(msg("auth.twofa.login_link_hint",
                    "<gray>On the next login you will receive a GitHub authorization link."));
            player.sendMessage(msg("auth.twofa.divider", "<gray>━━━━━━━━━━━━━━━━━━━━━"));
            player.sendMessage("");
            return true;
        }

        // /ui auth 2fa disable
        if (args.length >= 3 && args[2].equalsIgnoreCase("disable")) {
            if (!mgr.isAuthenticated(uuid)) {
                player.sendMessage(msg("auth.twofa.login_required_disable",
                        "<dark_red>❌</dark_red> <red>You must be logged in to disable 2FA!</red>"));
                return true;
            }
            if (!mgr.is2FAEnabled(uuid)) {
                player.sendMessage(msg("auth.twofa.not_enabled",
                        "<dark_red>❌</dark_red> <red>2FA is not enabled!</red>"));
                return true;
            }
            mgr.disable2FA(uuid);
            player.sendMessage(msg("auth.twofa.status_disabled", "<red>❌ 2FA disabled"));
            return true;
        }

        // /ui auth 2fa — the code flow is gone; with or without a 3rd argument show the status
        send2FAStatus(player, mgr);
        return true;
    }

    /**
     * 2FA status block for {@code /ui auth 2fa}: whether 2FA is on, which GitHub
     * account is linked and how to flip the switch.
     */
    private static void send2FAStatus(Player player, AuthManager mgr) {
        UUID uuid = player.getUniqueId();
        if (!mgr.is2FAEnabled(uuid)) {
            player.sendMessage(msg("auth.twofa.status_disabled", "<red>❌ 2FA disabled"));
            player.sendMessage(msg("auth.twofa.enable_hint",
                    "<gray>Enable: <yellow>/ui auth 2fa setup <github_username>"));
            return;
        }
        String account = mgr.getGithubUsername(uuid);
        player.sendMessage(msg("auth.twofa.status_enabled", "<green>✔ 2FA enabled"));
        player.sendMessage(msg("auth.twofa.github_account",
                "<gray>GitHub account: <white>%account%", "%account%", account == null ? "-" : account));
        player.sendMessage(AuthConfig.isGithub2FAEnabled()
                ? msg("auth.twofa.login_link_hint_short",
                        "<gray>On login you will get a GitHub authorization link.")
                : msg("auth.twofa.config_disabled",
                        "<red>⚠ GitHub 2FA is disabled in the config (auth.twofa.github.enabled: false)"));
        player.sendMessage(msg("auth.twofa.disable_hint",
                "<gray>Disable: <yellow>/ui auth 2fa disable"));
    }

    private static boolean handleForceLogin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ui.command.auth.forcelogin")) {
            CommandErrors.noPermission(sender, "ui.command.auth.forcelogin");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(msg("auth.admin.forcelogin_usage",
                    "<red>❌ Usage: </red><white>/ui auth forcelogin <nick></white>"));
            return true;
        }
        String name = args[2];
        UUID uuid = getOfflineUuid(name);
        if (!AuthDatabase.isRegistered(uuid)) {
            sender.sendMessage(msg("auth.admin.forcelogin_not_registered",
                    "<red>❌ Player</red> <yellow>%player%</yellow> <red>is not registered!</red>",
                    "%player%", name));
            return true;
        }
        AuthManager mgr = AuthManager.getInstance();
        if (mgr == null) {
            sender.sendMessage(msg("auth.admin.forcelogin_not_initialized",
                    "<red>❌ Authorization system is not initialized!</red>"));
            return true;
        }
        sender.sendMessage(mgr.forceLogin(uuid, name, sender.getName())
                ? msg("auth.admin.forcelogin_success",
                        "<green>✔</green> <white>Player</white> <yellow>%player%</yellow> <white>force logged in.</white>",
                        "%player%", name)
                : msg("auth.admin.forcelogin_fail",
                        "<red>❌ Failed to force login player</red> <yellow>%player%</yellow>",
                        "%player%", name));
        return true;
    }

    private static boolean handleResetAuth(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ui.command.auth.resetauth")) {
            CommandErrors.noPermission(sender, "ui.command.auth.resetauth");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(msg("auth.admin.resetauth_usage",
                    "<red>❌ Usage: </red><white>/ui auth resetauth <nick></white>"));
            return true;
        }
        String name = args[2];
        UUID uuid = getOfflineUuid(name);
        if (!AuthDatabase.isRegistered(uuid)) {
            sender.sendMessage(msg("auth.admin.resetauth_not_registered",
                    "<red>❌ Player</red> <yellow>%player%</yellow> <red>is not registered!</red>",
                    "%player%", name));
            return true;
        }
        AuthManager mgr = AuthManager.getInstance();
        if (mgr == null) {
            sender.sendMessage(msg("auth.admin.resetauth_not_initialized",
                    "<red>❌ Authorization system is not initialized!</red>"));
            return true;
        }
        sender.sendMessage(mgr.resetAuth(uuid)
                ? msg("auth.admin.resetauth_success",
                        "<green>✔</green> <white>Registration of player</white> <yellow>%player%</yellow> <white>completely removed.</white>",
                        "%player%", name)
                : msg("auth.admin.resetauth_fail",
                        "<red>❌ Failed to delete registration of player</red> <yellow>%player%</yellow>",
                        "%player%", name));
        return true;
    }

    private static boolean handleDelSession(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ui.command.auth.delsession")) {
            CommandErrors.noPermission(sender, "ui.command.auth.delsession");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(msg("auth.admin.delsession_usage",
                    "<red>❌ Usage: </red><white>/ui auth delsession <nick></white>"));
            return true;
        }
        String name = args[2];
        UUID uuid = getOfflineUuid(name);
        if (!AuthDatabase.isRegistered(uuid)) {
            sender.sendMessage(msg("auth.admin.delsession_not_registered",
                    "<red>❌ Player</red> <yellow>%player%</yellow> <red>is not registered!</red>",
                    "%player%", name));
            return true;
        }
        AuthManager mgr = AuthManager.getInstance();
        if (mgr == null) {
            sender.sendMessage(msg("auth.admin.delsession_not_initialized",
                    "<red>❌ Authorization system is not initialized!</red>"));
            return true;
        }
        sender.sendMessage(mgr.deleteSession(uuid)
                ? msg("auth.admin.delsession_success",
                        "<green>✔</green> <white>Session of player</white> <yellow>%player%</yellow> <white>reset (logout).</white>",
                        "%player%", name)
                : msg("auth.admin.delsession_fail",
                        "<red>❌ Failed to reset session of player</red> <yellow>%player%</yellow>",
                        "%player%", name));
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
            player.sendMessage(msg("auth.messages.not_initialized",
                    "<red>❌ Authorization system is not initialized!</red>"));
            return true;
        }
        if (!mgr.isAuthenticated(player.getUniqueId())) {
            player.sendMessage(msg("auth.admin.not_authenticated",
                    "<red>❌ You are not logged in!</red>"));
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
            player.sendMessage(msg("auth.messages.not_initialized",
                    "<red>❌ Authorization system is not initialized!</red>"));
            return true;
        }
        if (args.length > 2) {
            player.sendMessage(msg("auth.messages.no_password_in_chat",
                    "<yellow>⚠ Do not type passwords in chat — open the password change window.</yellow>"));
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
                player.sendMessage(msg("auth.messages.no_password_in_chat",
                        "<yellow>⚠ Do not type passwords in chat — open the password change window.</yellow>"));
            }
            AuthDialogs.openAdminChangePassword(player);
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(msg("auth.admin.chgpass_usage",
                    "<red>❌ Usage: </red><white>/ui auth chgpass <nick> <new_password></white>"));
            return true;
        }
        String name = args[2], newPass = args[3];
        String policyError = PasswordPolicy.validate(newPass);
        if (policyError != null) {
            sender.sendMessage(MessageUtil.parse(policyError));
            return true;
        }
        UUID uuid = getOfflineUuid(name);
        if (!AuthDatabase.isRegistered(uuid)) {
            sender.sendMessage(msg("auth.admin.chgpass_not_registered",
                    "<red>❌ Player</red> <yellow>%player%</yellow> <red>is not registered!</red>",
                    "%player%", name));
            return true;
        }
        AuthManager mgr = AuthManager.getInstance();
        if (mgr == null) {
            sender.sendMessage(msg("auth.admin.chgpass_not_initialized",
                    "<red>❌ Authorization system is not initialized!</red>"));
            return true;
        }
        boolean changed = mgr.changePassword(uuid, newPass);
        sender.sendMessage(changed
                ? msg("auth.admin.chgpass_dialog_success",
                        "<green>✔</green> <white>Password of player</white> <yellow>%player%</yellow> <white>changed.</white>",
                        "%player%", name)
                : msg("auth.admin.chgpass_dialog_fail",
                        "<red>❌ Failed to change the password (check the length).</red>"));
        return true;
    }
}
