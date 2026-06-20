package com.mcplugin.commands.subcommands;

import com.mcplugin.commands.home.HomeCommand;
import com.mcplugin.features.notes.NotesGUI;
import com.mcplugin.features.vanish.VanishManager;
import com.mcplugin.features.minecartspeed.MinecartSpeedManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class MiscSubcommand {

    private MiscSubcommand() {}

    // =========================
    // VANISH
    // =========================
    public static boolean vanish(CommandSender sender, String[] args) {
        if (sender instanceof Player p && !p.hasPermission("mcplugin.command.vanish")) {
            p.sendMessage("§4❌ §cУ вас нет прав на использование ваниша!"); return true;
        }
        if (args.length < 2) { sender.sendMessage("§4❌ §cИспользование: §f/mp vanish §7<ник>"); return true; }
        String targetName = args[1];
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage("§4❌ §cИгрок §e" + targetName + "§c не найден!"); return true;
        }
        UUID uuid = target.getUniqueId();
        VanishManager.toggleVanish(target);
        boolean isVanished = VanishManager.isVanished(uuid);
        if (isVanished) {
            sender.sendMessage("§a✅ §fИгрок §e" + targetName + "§f теперь скрыт (ваниш).");
            if (!target.isOnline()) sender.sendMessage("§8┃ §7Игрок оффлайн — ваниш применится при входе.");
        } else {
            sender.sendMessage("§c✗ §fИгрок §e" + targetName + "§f больше не скрыт.");
        }
        return true;
    }

    // =========================
    // NOTES
    // =========================
    public static boolean notes(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§4❌ §cТолько игрок может использовать заметки!"); return true; }
        if (!player.hasPermission("mcplugin.command.notes")) { player.sendMessage("§4❌ §cУ вас нет прав на использование заметок!"); return true; }
        NotesGUI.openMainGUI(player);
        return true;
    }

    // =========================
    // TOGGLESPEED
    // =========================
    public static boolean toggleSpeed(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§4❌ §cТолько игрок может использовать эту команду!"); return true; }
        if (!player.hasPermission("mcplugin")) { player.sendMessage("§4❌ §cУ вас нет прав!"); return true; }
        UUID uuid = player.getUniqueId();
        MinecartSpeedManager.toggleSpeedDisplay(uuid);
        if (MinecartSpeedManager.isSpeedDisplayEnabled(uuid)) {
            player.sendMessage("§a⚡ §fОтображение скорости: §aВКЛ");
        } else {
            player.sendMessage("§c⚡ §fОтображение скорости: §cВЫКЛ");
        }
        return true;
    }

    // =========================
    // HOME (delegates to HomeCommand)
    // =========================
    public static boolean home(CommandSender sender, String[] args) {
        return HomeCommand.dispatch(sender, args);
    }
}
