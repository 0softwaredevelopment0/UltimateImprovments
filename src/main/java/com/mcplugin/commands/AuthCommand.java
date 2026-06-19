package com.mcplugin.commands;

import com.mcplugin.Main;
import com.mcplugin.auth.AuthDatabase;
import com.mcplugin.auth.AuthGUI;
import com.mcplugin.auth.AuthManager;
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
            sender.sendMessage("§4❌ §cСистема авторизации отключена в конфиге!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§4❌ §cИспользование: §f/mp auth forcelogin|resetauth|chgpass|delsession|logout §7<ник>");
            return true;
        }

        switch (args[1].toLowerCase()) {
            case "forcelogin" -> handleForceLogin(sender, args);
            case "resetauth" -> handleResetAuth(sender, args);
            case "delsession" -> handleDelSession(sender, args);
            case "logout" -> handleLogout(sender);
            case "chgpass" -> handleChgPass(sender, args);
            default -> sender.sendMessage("§4❌ §cНеизвестная подкоманда: §f" + args[1]);
        }
        return true;
    }

    private static void handleForceLogin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcplugin.command.auth.forcelogin")) {
            sender.sendMessage("§4❌ §cУ вас нет прав на принудительную авторизацию!");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("§4❌ §cИспользование: §f/mp auth forcelogin §7<ник>");
            return;
        }

        String targetName = args[2];
        UUID targetUuid = getOfflineUuid(targetName);

        if (!AuthDatabase.isRegistered(targetUuid)) {
            sender.sendMessage("§4❌ §cИгрок §e" + targetName + "§c не зарегистрирован в системе авторизации!");
            return;
        }

        AuthManager manager = AuthManager.getInstance();
        if (manager == null) {
            sender.sendMessage("§4❌ §cСистема авторизации не инициализирована!");
            return;
        }

        if (manager.forceLogin(targetUuid)) {
            sender.sendMessage("§a✅ §fИгрок §e" + targetName + "§f принудительно авторизован.");
        } else {
            sender.sendMessage("§4❌ §cНе удалось авторизовать игрока §e" + targetName);
        }
    }

    private static void handleResetAuth(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcplugin.command.auth.resetauth")) {
            sender.sendMessage("§4❌ §cУ вас нет прав на сброс авторизации!");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("§4❌ §cИспользование: §f/mp auth resetauth §7<ник>");
            return;
        }

        String targetName = args[2];
        UUID targetUuid = getOfflineUuid(targetName);

        if (!AuthDatabase.isRegistered(targetUuid)) {
            sender.sendMessage("§4❌ §cИгрок §e" + targetName + "§c не зарегистрирован в системе авторизации!");
            return;
        }

        AuthManager manager = AuthManager.getInstance();
        if (manager == null) {
            sender.sendMessage("§4❌ §cСистема авторизации не инициализирована!");
            return;
        }

        if (manager.resetAuth(targetUuid)) {
            sender.sendMessage("§a✅ §fРегистрация игрока §e" + targetName + "§f полностью удалена.");
        } else {
            sender.sendMessage("§4❌ §cНе удалось удалить регистрацию игрока §e" + targetName);
        }
    }

    private static void handleDelSession(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcplugin.command.auth.delsession")) {
            sender.sendMessage("§4❌ §cУ вас нет прав на сброс сессии!");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("§4❌ §cИспользование: §f/mp auth delsession §7<ник>");
            return;
        }

        String targetName = args[2];
        UUID targetUuid = getOfflineUuid(targetName);

        if (!AuthDatabase.isRegistered(targetUuid)) {
            sender.sendMessage("§4❌ §cИгрок §e" + targetName + "§c не зарегистрирован в системе авторизации!");
            return;
        }

        AuthManager manager = AuthManager.getInstance();
        if (manager == null) {
            sender.sendMessage("§4❌ §cСистема авторизации не инициализирована!");
            return;
        }

        if (manager.deleteSession(targetUuid)) {
            sender.sendMessage("§a✅ §fСессия игрока §e" + targetName + "§f сброшена (logout).");
            sender.sendMessage("§8┃ §7При следующем входе нужно будет снова ввести пароль.");
        } else {
            sender.sendMessage("§4❌ §cНе удалось сбросить сессию игрока §e" + targetName);
        }
    }

    private static void handleLogout(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§4❌ §cТолько игрок может использовать эту команду!");
            return;
        }

        if (!AuthManager.getInstance().isAuthenticated(player.getUniqueId())) {
            player.sendMessage("§c❌ Вы не авторизованы!");
            return;
        }

        AuthGUI.openLogout(player);
    }

    private static void handleChgPass(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcplugin.command.auth.chgpass")) {
            sender.sendMessage("§4❌ §cУ вас нет прав на смену пароля!");
            return;
        }
        if (args.length < 4) {
            sender.sendMessage("§4❌ §cИспользование: §f/mp auth chgpass §7<ник> <новый пароль>");
            return;
        }

        String targetName = args[2];
        String newPassword = args[3];

        int minLen = Main.getInstance().getConfig().getInt("auth.min_password_length", 8);
        int maxLen = Main.getInstance().getConfig().getInt("auth.max_password_length", 32);
        if (newPassword.length() < minLen) {
            sender.sendMessage("§4❌ §cПароль должен быть не менее " + minLen + " символов!");
            return;
        }
        if (newPassword.length() > maxLen) {
            sender.sendMessage("§4❌ §cПароль не должен превышать " + maxLen + " символов!");
            return;
        }

        UUID targetUuid = getOfflineUuid(targetName);

        if (!AuthDatabase.isRegistered(targetUuid)) {
            sender.sendMessage("§4❌ §cИгрок §e" + targetName + "§c не зарегистрирован в системе авторизации!");
            return;
        }

        AuthManager manager = AuthManager.getInstance();
        if (manager == null) {
            sender.sendMessage("§4❌ §cСистема авторизации не инициализирована!");
            return;
        }

        if (manager.changePassword(targetUuid, newPassword)) {
            sender.sendMessage("§a✅ §fПароль игрока §e" + targetName + "§f успешно изменён на §a" + newPassword + "§f.");
            sender.sendMessage("§8┃ §7Сессия сброшена — игроку нужно заново войти.");
        } else {
            sender.sendMessage("§4❌ §cНе удалось сменить пароль игрока §e" + targetName);
        }
    }
}
