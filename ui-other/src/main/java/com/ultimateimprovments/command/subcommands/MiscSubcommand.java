package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;

import com.ultimateimprovments.command.home.HomeCommand;
import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.database.PlayerSettingsDB;
import com.ultimateimprovments.mechanics.features.items.NotesGUI;
import com.ultimateimprovments.mechanics.features.player.VanishManager;
import com.ultimateimprovments.mechanics.environment.radiation.RadiationManager;
import com.ultimateimprovments.mechanics.features.player.ElytraBoostManager;
import com.ultimateimprovments.mechanics.features.world.MinecartSpeedManager;
import com.ultimateimprovments.util.Materials;
import com.ultimateimprovments.util.MessageUtil;
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
        if (sender instanceof Player p && !p.hasPermission("ui.command.vanish")) {
            CommandErrors.noPermission(p); return true;
        }
        if (args.length < 2) { sender.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.vanish_usage", "<red>❌ Usage: </red><white>/ui vanish <nick></white>"))); return true; }
        String targetName = args[1];
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.vanish_player_not_found", "<red>❌ Player</red> <yellow>%player%</yellow> <red>not found!</red>").replace("%player%", targetName))); return true;
        }
        UUID uuid = target.getUniqueId();
        VanishManager.toggleVanish(target);
        boolean isVanished = VanishManager.isVanished(uuid);
        if (isVanished) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.vanish_enabled", "<green>✔</green> <white>Player</white> <yellow>%player%</yellow> <white>is now hidden (vanished).</white>").replace("%player%", targetName)));
            if (!target.isOnline()) sender.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.vanish_offline_hint", "<gray>Player is offline — vanish will apply on next login.</gray>")));
        } else {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.vanish_disabled", "<red>❌</red> <white>Player</white> <yellow>%player%</yellow> <white>is no longer hidden.</white>").replace("%player%", targetName)));
        }
        return true;
    }

    // =========================
    // NOTES
    // =========================
    public static boolean notes(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.notes_player_only", "<red>❌ Only players can use notes!</red>"))); return true; }
        if (!player.hasPermission("ui.command.notes")) { CommandErrors.noPermission(player); return true; }
        NotesGUI.openMainGUI(player);
        return true;
    }

    // =========================
    // SHOWSPEED — minecart speed display, /ui showspeed <on|off>
    // =========================
    private static final String PERM_SHOWSPEED = "ui.command.showspeed";

    public static boolean showSpeed(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(MessageUtil.parse(MessagesManager.getString("general.player_only", "<red>❌ Only players can use this command!</red>"))); return true; }
        if (!player.hasPermission(PERM_SHOWSPEED)) { CommandErrors.noPermission(player); return true; }
        Boolean want = parseOnOff(args);
        if (want == null) { player.sendMessage(MessageUtil.parse(MessagesManager.getString("general.on_off_usage", "<red>❌ Usage: </red><white>/%cmd% <on|off></white>").replace("%cmd%", "ui showspeed"))); return true; }
        UUID uuid = player.getUniqueId();
        if (MinecartSpeedManager.isSpeedDisplayEnabled(uuid) != want) {
            MinecartSpeedManager.toggleSpeedDisplay(uuid);
        }
        if (want) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.speed_enabled", "<green>⚡</green> <white>Speed display: </white><green>ON</green>")));
        } else {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.speed_disabled", "<red>⚡</red> <white>Speed display: </white><red>OFF</red>")));
        }
        return true;
    }

    // =========================
    // ELYTRABOOST — elytra boost on jump, /ui elytraboost <on|off> (default OFF)
    // =========================
    private static final String PERM_ELYTRABOOST = "ui.command.elytraboost";

    public static boolean elytraBoost(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(MessageUtil.parse(MessagesManager.getString("general.player_only", "<red>❌ Only players can use this command!</red>"))); return true; }
        if (!player.hasPermission(PERM_ELYTRABOOST)) { CommandErrors.noPermission(player); return true; }
        Boolean want = parseOnOff(args);
        if (want == null) { player.sendMessage(MessageUtil.parse(MessagesManager.getString("general.on_off_usage", "<red>❌ Usage: </red><white>/%cmd% <on|off></white>").replace("%cmd%", "ui elytraboost"))); return true; }
        UUID uuid = player.getUniqueId();
        boolean nowEnabled = ElytraBoostManager.isFlyEnabled(uuid);
        if (nowEnabled != want) {
            ElytraBoostManager.toggleFlyEnabled(uuid);
        }
        if (want) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.elytra_enabled", "<green>✦</green> <white>Elytra boost on jump: </white><green>ON</green>")));
        } else {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.elytra_disabled", "<red>✦</red> <white>Elytra boost on jump: </white><red>OFF</red>")));
        }
        return true;
    }

    // =========================
    // TOGGLERADVIEW
    // =========================
    public static boolean toggleRadView(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(MessageUtil.parse("<red>❌ Only players can use this command!</red>")); return true; }
        if (!player.hasPermission("ui.command.viewrad")) { CommandErrors.noPermission(player); return true; }
        RadiationManager.toggleRadView(player);
        if (RadiationManager.isRadViewEnabled(player)) {
            player.sendMessage(MessageUtil.parse("<green>☢</green> <white>Radiation display: </white><green>ON</green>"));
        } else {
            player.sendMessage(MessageUtil.parse("<red>☢</red> <white>Radiation display: </white><red>OFF</red>"));
        }
        return true;
    }

    // =========================
    // (toggleautocraft removed — replaced by /ui craftrecipe)
    // =========================

    // =========================
    // BOSSBAR — /ui bossbar <on|off> (default ON; config gate: bossbar.enabled)
    // =========================
    private static final String PERM_BOSSBAR = "ui.command.bossbar";

    public static boolean bossbar(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(MessageUtil.parse(MessagesManager.getString("general.player_only", "<red>❌ Only players can use this command!</red>"))); return true; }
        if (!player.hasPermission(PERM_BOSSBAR)) { CommandErrors.noPermission(player); return true; }
        Boolean want = parseOnOff(args);
        if (want == null) { player.sendMessage(MessageUtil.parse(MessagesManager.getString("general.on_off_usage", "<red>❌ Usage: </red><white>/%cmd% <on|off></white>").replace("%cmd%", "ui bossbar"))); return true; }
        if (want && !com.ultimateimprovments.core.Main.getInstance().getConfig().getBoolean("bossbar.enabled", false)) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.feature_disabled_in_config",
                    "<yellow>⚠ %feature% is disabled in the config (%key%: false) — enable it there first.</yellow>")
                    .replace("%feature%", "BossBar").replace("%key%", "bossbar.enabled")));
            return true;
        }
        PlayerSettingsDB.setBossbarEnabled(player.getUniqueId(), want);
        if (want) {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>BossBar: </white><green>ON</green>"));
        } else {
            player.sendMessage(MessageUtil.parse("<red>❌</red> <white>BossBar: </white><red>OFF</red>"));
        }
        return true;
    }

    // =========================
    // PINGSOUND — /ui pingsound <on|off> (default ON; config gate: chat_ping.enabled)
    // =========================
    private static final String PERM_PINGSOUND = "ui.command.pingsound";

    public static boolean pingsound(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(MessageUtil.parse(MessagesManager.getString("general.player_only", "<red>❌ Only players can use this command!</red>"))); return true; }
        if (!player.hasPermission(PERM_PINGSOUND)) { CommandErrors.noPermission(player); return true; }
        Boolean want = parseOnOff(args);
        if (want == null) { player.sendMessage(MessageUtil.parse(MessagesManager.getString("general.on_off_usage", "<red>❌ Usage: </red><white>/%cmd% <on|off></white>").replace("%cmd%", "ui pingsound"))); return true; }
        if (want && !com.ultimateimprovments.core.Main.getInstance().getConfig().getBoolean("chat_ping.enabled", true)) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.feature_disabled_in_config",
                    "<yellow>⚠ %feature% is disabled in the config (%key%: false) — enable it there first.</yellow>")
                    .replace("%feature%", "Ping sound").replace("%key%", "chat_ping.enabled")));
            return true;
        }
        PlayerSettingsDB.setPingEnabled(player.getUniqueId(), want);
        if (want) {
            player.sendMessage(MessageUtil.parse("<green>🔔</green> <white>Ping sound: </white><green>ON</green>"));
        } else {
            player.sendMessage(MessageUtil.parse("<red>🔕</red> <white>Ping sound: </white><red>OFF</red>"));
        }
        return true;
    }

    // =========================
    // SCOREBOARD — /ui scoreboard <on|off> (default ON; config gate: scoreboard.enabled)
    // =========================
    private static final String PERM_SCOREBOARD = "ui.command.scoreboard";

    public static boolean scoreboard(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(MessageUtil.parse(MessagesManager.getString("general.player_only", "<red>❌ Only players can use this command!</red>"))); return true; }
        if (!player.hasPermission(PERM_SCOREBOARD)) { CommandErrors.noPermission(player); return true; }
        Boolean want = parseOnOff(args);
        if (want == null) { player.sendMessage(MessageUtil.parse(MessagesManager.getString("general.on_off_usage", "<red>❌ Usage: </red><white>/%cmd% <on|off></white>").replace("%cmd%", "ui scoreboard"))); return true; }
        if (want && !com.ultimateimprovments.core.Main.getInstance().getConfig().getBoolean("scoreboard.enabled", false)) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.feature_disabled_in_config",
                    "<yellow>⚠ %feature% is disabled in the config (%key%: false) — enable it there first.</yellow>")
                    .replace("%feature%", "Scoreboard").replace("%key%", "scoreboard.enabled")));
            return true;
        }
        PlayerSettingsDB.setScoreboardEnabled(player.getUniqueId(), want);
        if (want) {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Scoreboard: </white><green>ON</green>"));
        } else {
            player.sendMessage(MessageUtil.parse("<red>❌</red> <white>Scoreboard: </white><red>OFF</red>"));
        }
        return true;
    }

    // =========================
    // WIRELESSBIND — /ui wirelessbind <on|off> (default OFF)
    // =========================
    private static final String PERM_WIRELESSBIND = "ui.command.wirelessbind";

    public static boolean wirelessbind(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Only players can use this command!</red>"));
            return true;
        }
        if (!player.hasPermission(PERM_WIRELESSBIND)) {
            CommandErrors.noPermission(player);
            return true;
        }
        Boolean want = parseOnOff(args);
        if (want == null) { player.sendMessage(MessageUtil.parse(MessagesManager.getString("general.on_off_usage", "<red>❌ Usage: </red><white>/%cmd% <on|off></white>").replace("%cmd%", "ui wirelessbind"))); return true; }
        PlayerSettingsDB.setWirelessBindEnabled(player.getUniqueId(), want);
        if (want) {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Wireless redstone binding: </white><green>ON</green>"));
        } else {
            player.sendMessage(MessageUtil.parse("<red>❌</red> <white>Wireless redstone binding: </white><red>OFF</red>"));
        }
        return true;
    }

    /** Parses args[1] as on/off. Returns null when missing/invalid. */
    private static Boolean parseOnOff(String[] args) {
        if (args.length < 2) return null;
        return switch (args[1].toLowerCase()) {
            case "on", "enable", "true", "1" -> Boolean.TRUE;
            case "off", "disable", "false", "0" -> Boolean.FALSE;
            default -> null;
        };
    }

    // =========================
    // HOME (delegates to HomeCommand)
    // =========================
    public static boolean home(CommandSender sender, String[] args) {
        return HomeCommand.dispatch(sender, args);
    }

    // =========================
    // FLY — enables/disables flight (even in survival)
    // /ui fly on|off [player]
    // =========================
    public static boolean fly(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui fly <on|off> [player]</white>"));
            return true;
        }

        boolean enable;
        switch (args[1].toLowerCase()) {
            case "on" -> enable = true;
            case "off" -> enable = false;
            default -> {
                sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui fly <on|off> [player]</white>"));
                return true;
            }
        }

        Player target;
        if (args.length >= 3) {
            // Apply to another player
            if (!sender.hasPermission("ui.command.fly.other")) {
                CommandErrors.noPermission(sender);
                return true;
            }
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(MessageUtil.parse("<red>❌ Player</red> <yellow>" + args[2] + "</yellow> <red>not found!</red>"));
                return true;
            }
        } else {
            // Apply to self
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MessageUtil.parse("<red>❌ Only players can use this command on themselves!</red>"));
                return true;
            }
            if (!player.hasPermission("ui.command.fly")) {
                CommandErrors.noPermission(player);
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
    // GOD — enables/disables invulnerability
    // /ui god on|off [player]
    // =========================
    public static boolean god(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui god <on|off> [player]</white>"));
            return true;
        }

        boolean enable;
        switch (args[1].toLowerCase()) {
            case "on" -> enable = true;
            case "off" -> enable = false;
            default -> {
                sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui god <on|off> [player]</white>"));
                return true;
            }
        }

        Player target;
        if (args.length >= 3) {
            // Apply to another player
            if (!sender.hasPermission("ui.command.god.other")) {
                CommandErrors.noPermission(sender);
                return true;
            }
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(MessageUtil.parse("<red>❌ Player</red> <yellow>" + args[2] + "</yellow> <red>not found!</red>"));
                return true;
            }
        } else {
            // Apply to self
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MessageUtil.parse("<red>❌ Only players can use this command on themselves!</red>"));
                return true;
            }
            if (!player.hasPermission("ui.command.god")) {
                CommandErrors.noPermission(player);
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
    // UNLOCK BOOK — converts a signed book into a book-and-quill
    // =========================
    public static boolean unlockBook(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Only players can use this command!</red>"));
            return true;
        }
        if (!player.hasPermission("ui.command.unlock")) {
            CommandErrors.noPermission(player);
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

        // Copy pages from the signed book (List<String>) and create a book-and-quill
        var pages = oldMeta.getPages();
        ItemStack newBook = new ItemStack(Materials.WRITABLE_BOOK, item.getAmount());
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
    // UNLOCK SIGN — removes the waxed component from a sign
    // =========================
    public static boolean unlockSign(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Only players can use this command!</red>"));
            return true;
        }
        if (!player.hasPermission("ui.command.unlock")) {
            CommandErrors.noPermission(player);
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

        // Create a new sign without the waxed component (a fresh item has no waxed)
        ItemStack newSign = new ItemStack(item.getType(), item.getAmount());
        if (item.hasItemMeta()) {
            var oldMeta = item.getItemMeta();
            var newMeta = newSign.getItemMeta();
            if (newMeta == null) {
                player.sendMessage(MessageUtil.parse("<red>❌ Failed to create new sign!</red>"));
                return true;
            }
            // Copy display name and lore
            if (oldMeta.hasDisplayName()) newMeta.displayName(oldMeta.displayName());
            if (oldMeta.hasLore()) newMeta.lore(oldMeta.lore());
            // Copy PDC
            oldMeta.getPersistentDataContainer().copyTo(newMeta.getPersistentDataContainer(), true);
            newSign.setItemMeta(newMeta);
        }

        player.getInventory().setItemInMainHand(newSign);
        player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Sign unwaxed! You can now edit it after placing.</white>"));
        return true;
    }
}
