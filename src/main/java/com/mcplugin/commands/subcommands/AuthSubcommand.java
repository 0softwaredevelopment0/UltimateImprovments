package com.mcplugin.commands.subcommands;

import com.mcplugin.Main;
import com.mcplugin.auth.AuthDatabase;
import com.mcplugin.auth.AuthGUI;
import com.mcplugin.auth.AuthManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class AuthSubcommand {

    private AuthSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!Main.getInstance().getConfig().getBoolean("auth.enabled", true)) {
            sender.sendMessage("§4❌ §cСистема авторизации отключена в конфиге!");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§4❌ §cИспользование: §f/mp auth forcelogin|resetauth|chgpass|delsession|logout §7<ник>");
            return true;
        }

        return switch (args[1].toLowerCase()) {
            case "forcelogin" -> handleForceLogin(sender, args);
            case "resetauth" -> handleResetAuth(sender, args);
            case "delsession" -> handleDelSession(sender, args);
            case "logout" -> handleLogout(sender);
            case "chgpass" -> handleChgPass(sender, args);
            default -> {
                sender.sendMessage("§4❌ §cНеизвестная подкоманда: §f" + args[1]);
                yield true;
            }
        };
    }

    @SuppressWarnings("deprecation")
    private static UUID getOfflineUuid(String name) {
        return Bukkit.getOfflinePlayer(name).getUniqueId();
    }

    private static boolean handleForceLogin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcplugin.command.auth.forcelogin")) {
            sender.sendMessage("§4❌ §cУ вас нет прав на принудительную авторизацию!");
            return true;
        }
        if (args.length < 3) { sender.sendMessage("§4❌ §cИспользование: §f/mp auth forcelogin §7<ник>"); return true; }
        String name = args[2];
        UUID uuid = getOfflineUuid(name);
        if (!AuthDatabase.isRegistered(uuid)) { sender.sendMessage("§4❌ §cИгрок §e" + name + "§c не зарегистрирован!"); return true; }
        AuthManager mgr = AuthManager.getInstance();
        if (mgr == null) { sender.sendMessage("§4❌ §cСистема авторизации не инициализирована!"); return true; }
        sender.sendMessage(mgr.forceLogin(uuid) ? "§a✅ §fИгрок §e" + name + "§f принудительно авторизован." : "§4❌ §cНе удалось авторизовать игрока §e" + name);
        return true;
    }

    private static boolean handleResetAuth(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcplugin.command.auth.resetauth")) {
            sender.sendMessage("§4❌ §cУ вас нет прав на сброс авторизации!");
            return true;
        }
        if (args.length < 3) { sender.sendMessage("§4❌ §cИспользование: §f/mp auth resetauth §7<ник>"); return true; }
        String name = args[2];
        UUID uuid = getOfflineUuid(name);
        if (!AuthDatabase.isRegistered(uuid)) { sender.sendMessage("§4❌ §cИгрок §e" + name + "§c не зарегистрирован!"); return true; }
        AuthManager mgr = AuthManager.getInstance();
        if (mgr == null) { sender.sendMessage("§4❌ §cСистема авторизации не инициализирована!"); return true; }
        sender.sendMessage(mgr.resetAuth(uuid) ? "§a✅ §fРегистрация игрока §e" + name + "§f полностью удалена." : "§4❌ §cНе удалось удалить регистрацию!");
        return true;
    }

    private static boolean handleDelSession(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcplugin.command.auth.delsession")) {
            sender.sendMessage("§4❌ §cУ вас нет прав на сброс сессии!");
            return true;
        }
        if (args.length < 3) { sender.sendMessage("§4❌ §cИспользование: §f/mp auth delsession §7<ник>"); return true; }
        String name = args[2];
        UUID uuid = getOfflineUuid(name);
        if (!AuthDatabase.isRegistered(uuid)) { sender.sendMessage("§4❌ §cИгрок §e" + name + "§c не зарегистрирован!"); return true; }
        AuthManager mgr = AuthManager.getInstance();
        if (mgr == null) { sender.sendMessage("§4❌ §cСистема авторизации не инициализирована!"); return true; }
        sender.sendMessage(mgr.deleteSession(uuid) ? "§a✅ §fСессия игрока §e" + name + "§f сброшена." : "§4❌ §cНе удалось сбросить сессию!");
        return true;
    }

    private static boolean handleLogout(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§4❌ §cТолько игрок может использовать эту команду!"); return true; }
        if (!AuthManager.getInstance().isAuthenticated(player.getUniqueId())) { player.sendMessage("§c❌ Вы не авторизованы!"); return true; }
        AuthGUI.openLogout(player);
        return true;
    }

    private static boolean handleChgPass(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcplugin.command.auth.chgpass")) {
            sender.sendMessage("§4❌ §cУ вас нет прав на смену пароля!");
            return true;
        }
        if (args.length < 4) { sender.sendMessage("§4❌ §cИспользование: §f/mp auth chgpass §7<ник> <новый пароль>"); return true; }
        String name = args[2], newPass = args[3];
        int minLen = Main.getInstance().getConfig().getInt("auth.min_password_length", 8);
        int maxLen = Main.getInstance().getConfig().getInt("auth.max_password_length", 32);
        if (newPass.length() < minLen) { sender.sendMessage("§4❌ §cПароль должен быть не менее " + minLen + " символов!"); return true; }
        if (newPass.length() > maxLen) { sender.sendMessage("§4❌ §cПароль не должен превышать " + maxLen + " символов!"); return true; }
        UUID uuid = getOfflineUuid(name);
        if (!AuthDatabase.isRegistered(uuid)) { sender.sendMessage("§4❌ §cИгрок §e" + name + "§c не зарегистрирован!"); return true; }
        AuthManager mgr = AuthManager.getInstance();
        if (mgr == null) { sender.sendMessage("§4❌ §cСистема авторизации не инициализирована!"); return true; }
        sender.sendMessage(mgr.changePassword(uuid, newPass) ? "§a✅ §fПароль игрока §e" + name + "§f изменён." : "§4❌ §cНе удалось сменить пароль!");
        return true;
    }
}
