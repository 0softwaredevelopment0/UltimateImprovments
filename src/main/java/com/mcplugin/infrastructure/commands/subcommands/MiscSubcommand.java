package com.mcplugin.infrastructure.commands.subcommands;

import com.mcplugin.infrastructure.commands.home.HomeCommand;
import com.mcplugin.infrastructure.config.MessagesManager;
import com.mcplugin.infrastructure.database.PlayerSettingsDB;
import com.mcplugin.mechanics.features.items.NotesGUI;
import com.mcplugin.mechanics.features.player.VanishManager;
import com.mcplugin.mechanics.environment.radiation.RadiationManager;
import com.mcplugin.mechanics.features.player.ElytraBoostManager;
import com.mcplugin.mechanics.features.items.AutoCraftManager;
import com.mcplugin.mechanics.features.world.MinecartSpeedManager;
import com.mcplugin.infrastructure.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
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
            p.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.vanish_no_permission", "<red>❌ You don't have permission to use vanish!</red>"))); return true;
        }
        if (args.length < 2) { sender.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.vanish_usage", "<red>❌ Usage: </red><white>/mp vanish <nick></white>"))); return true; }
        String targetName = args[1];
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.vanish_player_not_found", "<red>❌ Player</red> <yellow>{player}</yellow> <red>not found!</red>").replace("{player}", targetName))); return true;
        }
        UUID uuid = target.getUniqueId();
        VanishManager.toggleVanish(target);
        boolean isVanished = VanishManager.isVanished(uuid);
        if (isVanished) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.vanish_enabled", "<green>✔</green> <white>Player</white> <yellow>{player}</yellow> <white>is now hidden (vanished).</white>").replace("{player}", targetName)));
            if (!target.isOnline()) sender.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.vanish_offline_hint", "<gray>Player is offline — vanish will apply on next login.</gray>")));
        } else {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.vanish_disabled", "<red>❌</red> <white>Player</white> <yellow>{player}</yellow> <white>is no longer hidden.</white>").replace("{player}", targetName)));
        }
        return true;
    }

    // =========================
    // NOTES
    // =========================
    public static boolean notes(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.notes_player_only", "<red>❌ Only players can use notes!</red>"))); return true; }
        if (!player.hasPermission("mcplugin.command.notes")) { player.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.notes_no_permission", "<red>❌ You don't have permission to use notes!</red>"))); return true; }
        NotesGUI.openMainGUI(player);
        return true;
    }

    // =========================
    // TOGGLESPEED
    // =========================
    public static boolean toggleSpeed(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.speed_player_only", "<red>❌ Only players can use this command!</red>"))); return true; }
        if (!player.hasPermission("mcplugin")) { player.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.speed_no_permission", "<red>❌ You don't have permission!</red>"))); return true; }
        UUID uuid = player.getUniqueId();
        MinecartSpeedManager.toggleSpeedDisplay(uuid);
        if (MinecartSpeedManager.isSpeedDisplayEnabled(uuid)) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.speed_enabled", "<green>⚡</green> <white>Speed display: </white><green>ON</green>")));
        } else {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.speed_disabled", "<red>⚡</red> <white>Speed display: </white><red>OFF</red>")));
        }
        return true;
    }

    // =========================
    // TOGGLEFLY
    // =========================
    public static boolean toggleFly(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(MessageUtil.parse(MessagesManager.getString("general.player_only", "<red>❌ Only players can use this command!</red>"))); return true; }
        UUID uuid = player.getUniqueId();
        ElytraBoostManager.toggleFlyEnabled(uuid);
        if (ElytraBoostManager.isFlyEnabled(uuid)) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.fly_enabled", "<green>✦</green> <white>Elytra boost on jump: </white><green>ON</green>")));
        } else {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.fly_disabled", "<red>✦</red> <white>Elytra boost on jump: </white><red>OFF</red>")));
        }
        return true;
    }

    // =========================
    // TOGGLERADVIEW
    // =========================
    public static boolean toggleRadView(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(MessageUtil.parse("<red>❌ Only players can use this command!</red>")); return true; }
        if (!player.hasPermission("mcplugin.command.toggleradview")) { player.sendMessage(MessageUtil.parse("<red>❌ You don't have permission!</red>")); return true; }
        RadiationManager.toggleRadView(player);
        if (RadiationManager.isRadViewEnabled(player)) {
            player.sendMessage(MessageUtil.parse("<green>☢</green> <white>Radiation display: </white><green>ON</green>"));
        } else {
            player.sendMessage(MessageUtil.parse("<red>☢</red> <white>Radiation display: </white><red>OFF</red>"));
        }
        return true;
    }

    // =========================
    // TOGGLEAUTOCRAFT
    // =========================
    public static boolean toggleAutoCraft(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(MessageUtil.parse(MessagesManager.getString("general.player_only", "<red>❌ Only players can use this command!</red>"))); return true; }
        if (!player.hasPermission("mcplugin.autocraft")) { player.sendMessage(MessageUtil.parse(MessagesManager.getString("general.no_permission", "<red>❌ You don't have permission!</red>"))); return true; }
        AutoCraftManager.toggleAutoCraft(player);
        return true;
    }

    // =========================
    // TOGGLEBB — bossbar per-player
    // =========================
    public static boolean toggleBossBar(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(MessageUtil.parse(MessagesManager.getString("general.player_only", "<red>❌ Only players can use this command!</red>"))); return true; }
        if (!player.hasPermission("mcplugin.command.togglebb")) { player.sendMessage(MessageUtil.parse(MessagesManager.getString("general.no_permission", "<red>❌ You don't have permission!</red>"))); return true; }
        UUID uuid = player.getUniqueId();
        boolean enabled = PlayerSettingsDB.toggleBossbar(uuid);
        if (enabled) {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>BossBar: </white><green>ON</green>"));
        } else {
            player.sendMessage(MessageUtil.parse("<red>❌</red> <white>BossBar: </white><red>OFF</red>"));
        }
        return true;
    }

    // =========================
    // TOGGLEPING — ping sound per-player
    // =========================
    public static boolean togglePing(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(MessageUtil.parse(MessagesManager.getString("general.player_only", "<red>❌ Only players can use this command!</red>"))); return true; }
        if (!player.hasPermission("mcplugin.command.toggleping")) { player.sendMessage(MessageUtil.parse(MessagesManager.getString("general.no_permission", "<red>❌ You don't have permission!</red>"))); return true; }
        UUID uuid = player.getUniqueId();
        boolean enabled = PlayerSettingsDB.togglePing(uuid);
        if (enabled) {
            player.sendMessage(MessageUtil.parse("<green>🔔</green> <white>Ping sound: </white><green>ON</green>"));
        } else {
            player.sendMessage(MessageUtil.parse("<red>🔕</red> <white>Ping sound: </white><red>OFF</red>"));
        }
        return true;
    }

    // =========================
    // TOGGLESB — scoreboard per-player
    // =========================
    public static boolean toggleScoreboard(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(MessageUtil.parse(MessagesManager.getString("general.player_only", "<red>❌ Only players can use this command!</red>"))); return true; }
        if (!player.hasPermission("mcplugin.command.togglesb")) { player.sendMessage(MessageUtil.parse(MessagesManager.getString("general.no_permission", "<red>❌ You don't have permission!</red>"))); return true; }
        UUID uuid = player.getUniqueId();
        boolean enabled = PlayerSettingsDB.toggleScoreboard(uuid);
        if (enabled) {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Scoreboard: </white><green>ON</green>"));
        } else {
            player.sendMessage(MessageUtil.parse("<red>❌</red> <white>Scoreboard: </white><red>OFF</red>"));
        }
        return true;
    }

    // =========================
    // HOME (delegates to HomeCommand)
    // =========================
    public static boolean home(CommandSender sender, String[] args) {
        return HomeCommand.dispatch(sender, args);
    }

    // =========================
    // FLY — включает/выключает полёт (даже в выживании)
    // /mp fly on|off [player]
    // =========================
    public static boolean fly(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/mp fly <on|off> [player]</white>"));
            return true;
        }

        boolean enable;
        switch (args[1].toLowerCase()) {
            case "on" -> enable = true;
            case "off" -> enable = false;
            default -> {
                sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/mp fly <on|off> [player]</white>"));
                return true;
            }
        }

        Player target;
        if (args.length >= 3) {
            // Применяем к другому игроку
            if (!sender.hasPermission("mcplugin.command.fly.other")) {
                sender.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to toggle flight for other players!</red>"));
                return true;
            }
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(MessageUtil.parse("<red>❌ Player</red> <yellow>" + args[2] + "</yellow> <red>not found!</red>"));
                return true;
            }
        } else {
            // Применяем к себе
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MessageUtil.parse("<red>❌ Only players can use this command on themselves!</red>"));
                return true;
            }
            if (!player.hasPermission("mcplugin.command.fly")) {
                player.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to toggle flight!</red>"));
                return true;
            }
            target = player;
        }

        target.setAllowFlight(enable);
        target.setFlying(enable);

        String state = enable ? "<green>ON</green>" : "<red>OFF</red>";
        String msg = "<green>✔</green> <white>Flight for</white> <yellow>" + target.getName() + "</yellow> <white>:</white> " + state;
        sender.sendMessage(MessageUtil.parse(msg));
        if (!sender.equals(target)) {
            target.sendMessage(MessageUtil.parse("<white>Your flight has been toggled:</white> " + state));
        }
        return true;
    }

    // =========================
    // GOD — включает/выключает неуязвимость
    // /mp god on|off [player]
    // =========================
    public static boolean god(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/mp god <on|off> [player]</white>"));
            return true;
        }

        boolean enable;
        switch (args[1].toLowerCase()) {
            case "on" -> enable = true;
            case "off" -> enable = false;
            default -> {
                sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/mp god <on|off> [player]</white>"));
                return true;
            }
        }

        Player target;
        if (args.length >= 3) {
            // Применяем к другому игроку
            if (!sender.hasPermission("mcplugin.command.god.other")) {
                sender.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to toggle god mode for other players!</red>"));
                return true;
            }
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(MessageUtil.parse("<red>❌ Player</red> <yellow>" + args[2] + "</yellow> <red>not found!</red>"));
                return true;
            }
        } else {
            // Применяем к себе
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MessageUtil.parse("<red>❌ Only players can use this command on themselves!</red>"));
                return true;
            }
            if (!player.hasPermission("mcplugin.command.god")) {
                player.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to toggle god mode!</red>"));
                return true;
            }
            target = player;
        }

        target.setInvulnerable(enable);

        String state = enable ? "<green>ON</green>" : "<red>OFF</red>";
        String msg = "<green>✔</green> <white>God mode for</white> <yellow>" + target.getName() + "</yellow> <white>:</white> " + state;
        sender.sendMessage(MessageUtil.parse(msg));
        if (!sender.equals(target)) {
            target.sendMessage(MessageUtil.parse("<white>Your god mode has been toggled:</white> " + state));
        }
        return true;
    }

    // =========================
    // UNLOCK BOOK — превращает подписанную книгу в книгу с пером
    // =========================
    public static boolean unlockBook(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Only players can use this command!</red>"));
            return true;
        }
        if (!player.hasPermission("mcplugin.command.unlock")) {
            player.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to unlock items!</red>"));
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() != Material.WRITTEN_BOOK) {
            player.sendMessage(MessageUtil.parse("<red>❌ You must hold a signed book in your hand!</red>"));
            return true;
        }

        BookMeta oldMeta = (BookMeta) item.getItemMeta();
        if (oldMeta == null) {
            player.sendMessage(MessageUtil.parse("<red>❌ Failed to read book data!</red>"));
            return true;
        }

        // Копируем страницы из подписанной книги (List<String>) и создаём книгу с пером
        var pages = oldMeta.getPages();
        ItemStack newBook = new ItemStack(Material.WRITABLE_BOOK, item.getAmount());
        BookMeta newMeta = (BookMeta) newBook.getItemMeta();
        if (newMeta == null) {
            player.sendMessage(MessageUtil.parse("<red>❌ Failed to create new book!</red>"));
            return true;
        }

        newMeta.setPages(pages);
        newBook.setItemMeta(newMeta);
        player.getInventory().setItemInMainHand(newBook);
        player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Book unlocked! You can now edit it.</white>"));
        return true;
    }

    // =========================
    // UNLOCK SIGN — убирает намазанность воском с таблички
    // =========================
    public static boolean unlockSign(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Only players can use this command!</red>"));
            return true;
        }
        if (!player.hasPermission("mcplugin.command.unlock")) {
            player.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to unlock items!</red>"));
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(MessageUtil.parse("<red>❌ You must hold a sign in your hand!</red>"));
            return true;
        }

        String typeName = item.getType().name();
        if (!typeName.endsWith("_SIGN")) {
            player.sendMessage(MessageUtil.parse("<red>❌ You must hold a sign in your hand!</red>"));
            return true;
        }

        // Создаём новую табличку без компонента waxed (свежий предмет не имеет waxed)
        ItemStack newSign = new ItemStack(item.getType(), item.getAmount());
        if (item.hasItemMeta()) {
            var oldMeta = item.getItemMeta();
            var newMeta = newSign.getItemMeta();
            if (newMeta == null) {
                player.sendMessage(MessageUtil.parse("<red>❌ Failed to create new sign!</red>"));
                return true;
            }
            // Копируем display name и lore
            if (oldMeta.hasDisplayName()) newMeta.displayName(oldMeta.displayName());
            if (oldMeta.hasLore()) newMeta.lore(oldMeta.lore());
            // Копируем PDC
            oldMeta.getPersistentDataContainer().copyTo(newMeta.getPersistentDataContainer(), true);
            newSign.setItemMeta(newMeta);
        }

        player.getInventory().setItemInMainHand(newSign);
        player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Sign unwaxed! You can now edit it after placing.</white>"));
        return true;
    }
}
