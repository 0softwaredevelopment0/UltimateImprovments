package com.ultimateimprovements.command.home;

import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.config.MessagesManager;
import com.ultimateimprovements.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * HomeCommand — обрабатывает команды домашних точек:
 * /mp sethome &lt;название&gt;
 * /mp home &lt;название&gt;
 * /mp delhome &lt;название&gt;
 * /mp listhomes
 * /mp ophomels &lt;игрок&gt;
 * /mp opdelhome &lt;игрок&gt; &lt;название&gt;
 */
public final class HomeCommand {

    private HomeCommand() {}

    private static int getNameMin() {
        return Main.getInstance().getConfig().getInt("home.name_min_length", 1);
    }

    private static int getNameMax() {
        return Main.getInstance().getConfig().getInt("home.name_max_length", 16);
    }

    // ============================================================
    // DISPATCH
    // ============================================================
    public static boolean dispatch(CommandSender sender, String[] args) {
        if (args.length == 0) return false;

        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] subArgs = new String[args.length - 1];
        if (subArgs.length > 0) {
            System.arraycopy(args, 1, subArgs, 0, subArgs.length);
        }

        return switch (sub) {
            case "sethome" -> executeSetHome(sender, subArgs);
            case "home" -> executeGetHome(sender, subArgs);
            case "delhome" -> executeDelHome(sender, subArgs);
            case "listhomes" -> executeListHomes(sender, subArgs);
            case "ophomels" -> executeOpHomeLs(sender, subArgs);
            case "opdelhome" -> executeOpDelHome(sender, subArgs);
            default -> false;
        };
    }

    // ============================================================
    // TAB COMPLETE
    // ============================================================
    public static List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 0) return List.of();

        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] subArgs = new String[args.length - 1];
        if (subArgs.length > 0) {
            System.arraycopy(args, 1, subArgs, 0, subArgs.length);
        }

        return switch (sub) {
            case "sethome" -> tabCompleteSetHome(sender, subArgs);
            case "home" -> tabCompleteGetHome(sender, subArgs);
            case "delhome" -> tabCompleteDelHome(sender, subArgs);
            case "listhomes" -> List.of();
            case "ophomels" -> tabCompleteOpHomeLs(sender, subArgs);
            case "opdelhome" -> tabCompleteOpDelHome(sender, subArgs);
            default -> List.of();
        };
    }

    // ============================================================
    // SETHOME
    // ============================================================
    private static boolean executeSetHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("home.player_only", "<red>❌ Only players can use this command!</red>")));
            return true;
        }
        if (!player.hasPermission("mcplugin.command.sethome")) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.no_permission_sethome", "<red>❌ You don't have permission to save home points!</red>")));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.usage_sethome", "<red>❌ Usage: </red><white>/mp sethome <name></white>")));
            return true;
        }

        String homeName = args[0].trim();
        int nameMin = getNameMin();
        int nameMax = getNameMax();
        if (homeName.length() < nameMin || homeName.length() > nameMax) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.name_length_error", "<red>❌ Home name must be between %min% and %max% characters!</red>").replace("%min%", String.valueOf(nameMin)).replace("%max%", String.valueOf(nameMax))));
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.name_current_length", "<gray> Current length: </gray><white>%length%</white><gray> characters.</gray>").replace("%length%", String.valueOf(homeName.length()))));
            return true;
        }

        UUID uuid = player.getUniqueId();

        int maxHomes = HomeDatabase.getMaxHomes();
        if (!HomeDatabase.homeExists(uuid, homeName) && HomeDatabase.countHomes(uuid) >= maxHomes) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.limit_reached", "<red>❌ You have reached the limit of</red> <yellow>%max%</yellow> <red>home points!</red>").replace("%max%", String.valueOf(maxHomes))));
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.limit_hint", "<gray> Use </gray><white>/mp delhome <name></white><gray> to remove unwanted ones.</gray>")));
            return true;
        }

        Location loc = player.getLocation();

        if (HomeDatabase.saveHome(uuid, homeName, loc)) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.save_success", "<green>✔</green> <white>Home</white> <yellow>%name%</yellow> <white>saved!</white>").replace("%name%", homeName)));
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.save_info_world", "<gray>World:</gray> <white>%world%</white>").replace("%world%", loc.getWorld().getName())));
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.save_info_coords", "<gray>Coordinates:</gray> <white>%x% / %y% / %z%</white>")
                    .replace("%x%", String.format("%.1f", loc.getX()))
                    .replace("%y%", String.format("%.1f", loc.getY()))
                    .replace("%z%", String.format("%.1f", loc.getZ()))));
            int used = HomeDatabase.countHomes(uuid);
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.save_info_used", "<gray>Used:</gray> <white>%used%<gray>/%max%</gray></white>").replace("%used%", String.valueOf(used)).replace("%max%", String.valueOf(HomeDatabase.getMaxHomes()))));
        } else {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.save_error", "<red>❌ Error saving home!</red>")));
        }
        return true;
    }

    // ============================================================
    // HOME
    // ============================================================
    private static boolean executeGetHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("home.player_only", "<red>❌ Only players can use this command!</red>")));
            return true;
        }
        if (!player.hasPermission("mcplugin.command.home")) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.no_permission_home", "<red>❌ You don't have permission to view home points!</red>")));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.usage_home", "<red>❌ Usage: </red><white>/mp home <name></white>")));
            return true;
        }

        String homeName = args[0].trim();
        UUID uuid = player.getUniqueId();
        HomeDatabase.HomeData homeEntry = HomeDatabase.getHome(uuid, homeName);

        if (homeEntry == null) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.not_found", "<yellow>ℹ</yellow> <white>Home</white> <yellow>%name%</yellow> <white>not found.</white>").replace("%name%", homeName)));
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.not_found_hint", "<gray> Use </gray><white>/mp sethome %name%</white><gray> to save it.</gray>").replace("%name%", homeName)));
            return true;
        }

        String mode = Main.getInstance().getConfig().getString("home.mode", "legit");

        if (mode.equalsIgnoreCase("standard")) {
            // Teleport to home
            World world = player.getServer().getWorld(homeEntry.world());
            if (world == null) {
                player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.world_not_found", "<red>❌ World</red> <yellow>%world%</yellow> <red>not found!</red>").replace("%world%", homeEntry.world())));
                return true;
            }
            Location homeLoc = new Location(world, homeEntry.x(), homeEntry.y(), homeEntry.z(), homeEntry.yaw(), homeEntry.pitch());
            player.teleport(homeLoc);
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.teleported", "<green>✔</green> <white>Teleported to home</white> <yellow>%name%</yellow><white>!</white>").replace("%name%", homeName)));
        } else {
            // Legit mode — show coordinates
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.info_header", "<gold>═══════════════════════════════════</gold>")));
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.info_name", "<gold>  ✦ </gold><white>Home: </white><yellow>%name%</yellow>").replace("%name%", homeEntry.homeName())));
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.info_header", "<gold>═══════════════════════════════════</gold>")));
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.info_world", "<gray>World: </gray><white>%world%</white>").replace("%world%", homeEntry.world())));
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.info_x", "<gray>X: </gray><white>%x%</white>").replace("%x%", String.format("%.1f", homeEntry.x()))));
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.info_y", "<gray>Y: </gray><white>%y%</white>").replace("%y%", String.format("%.1f", homeEntry.y()))));
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.info_z", "<gray>Z: </gray><white>%z%</white>").replace("%z%", String.format("%.1f", homeEntry.z()))));
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.info_header", "<gold>═══════════════════════════════════</gold>")));
            player.sendMessage(MessageUtil.parse("<yellow>⚠</yellow> <gray>Home is in </gray><yellow>legit</yellow> <gray>mode — no teleport. Travel manually.</gray>"));
        }
        return true;
    }

    // ============================================================
    // DELHOME
    // ============================================================
    private static boolean executeDelHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("home.player_only", "<red>❌ Only players can use this command!</red>")));
            return true;
        }
        if (!player.hasPermission("mcplugin.command.delhome")) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.no_permission_delhome", "<red>❌ You don't have permission to delete home points!</red>")));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.usage_delhome", "<red>❌ Usage: </red><white>/mp delhome <name></white>")));
            return true;
        }

        String homeName = args[0].trim();

        if (!HomeDatabase.homeExists(player.getUniqueId(), homeName)) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.not_found_del", "<red>❌ Home</red> <yellow>%name%</yellow> <red>not found!</red>").replace("%name%", homeName)));
            return true;
        }

        if (HomeDatabase.deleteHome(player.getUniqueId(), homeName)) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.delete_success", "<green>✔</green> <white>Home</white> <yellow>%name%</yellow> <white>deleted.</white>").replace("%name%", homeName)));
        } else {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.delete_error", "<red>❌ Error deleting home!</red>")));
        }
        return true;
    }

    // ============================================================
    // LISTHOMES
    // ============================================================
    private static boolean executeListHomes(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("home.player_only", "<red>❌ Only players can use this command!</red>")));
            return true;
        }
        if (!player.hasPermission("mcplugin.command.listhomes")) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.no_permission_listhomes", "<red>❌ You don't have permission to view the home list!</red>")));
            return true;
        }

        UUID uuid = player.getUniqueId();
        List<HomeDatabase.HomeData> homes = HomeDatabase.listHomes(uuid);

        if (homes.isEmpty()) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.no_homes", "<yellow>ℹ</yellow> <white>You have no saved home points.</white>")));
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.no_homes_hint", "<gray> Use </gray><white>/mp sethome <name></white><gray> to save one.</gray>")));
            return true;
        }

        int used = homes.size();
        player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.info_header", "<gold>═══════════════════════════════════</gold>")));
        player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.list_title", "<gold>  ✦ </gold><white>Your Homes </white><gray>(%used%/%max%)</gray>")
                .replace("%used%", String.valueOf(used))
                .replace("%max%", String.valueOf(HomeDatabase.getMaxHomes()))));
        player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.info_header", "<gold>═══════════════════════════════════</gold>")));

        for (int i = 0; i < homes.size(); i++) {
            HomeDatabase.HomeData h = homes.get(i);
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.list_entry_name", "<gray>┌─ </gray><yellow>%num%. </yellow><white>%name%</white>")
                    .replace("%num%", String.valueOf(i + 1))
                    .replace("%name%", h.homeName())));
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.list_entry_world", "<gray>│ </gray><gray>World: </gray><white>%world%</white>").replace("%world%", h.world())));
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.list_entry_coords", "<gray>│ </gray><gray>X: </gray><white>%x%</white> <gray>Y: </gray><white>%y%</white> <gray>Z: </gray><white>%z%</white>")
                    .replace("%x%", String.format("%.1f", h.x()))
                    .replace("%y%", String.format("%.1f", h.y()))
                    .replace("%z%", String.format("%.1f", h.z()))));
            if (i < homes.size() - 1) {
                player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.list_entry_sep", "<gray>│</gray>")));
            }
        }

        player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.info_header", "<gold>═══════════════════════════════════</gold>")));
        return true;
    }

    // ============================================================
    // OPHOMELS — оператор: список домов игрока
    // ============================================================
    private static boolean executeOpHomeLs(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcplugin.command.ophomels")) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("home.no_permission_ophomels", "<red>❌ You don't have permission to view other players' homes!</red>")));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("home.usage_ophomels", "<red>❌ Usage: </red><white>/mp ophomels <player></white>")));
            return true;
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);

        List<HomeDatabase.HomeData> homes = HomeDatabase.listHomes(target.getUniqueId());

        if (homes.isEmpty()) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("home.no_homes_player", "<yellow>ℹ</yellow> <white>Player</white> <yellow>%player%</yellow> <white>has no saved homes.</white>").replace("%player%", target.getName())));
            return true;
        }

        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("home.info_header", "<gold>═══════════════════════════════════</gold>")));
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("home.op_list_title", "<gold>  ✦ </gold><white>Player's Homes </white><yellow>%player%</yellow> <gray>(%count%)</gray>")
                .replace("%player%", target.getName())
                .replace("%count%", String.valueOf(homes.size()))));
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("home.info_header", "<gold>═══════════════════════════════════</gold>")));

        for (int i = 0; i < homes.size(); i++) {
            HomeDatabase.HomeData h = homes.get(i);
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("home.list_entry_name", "<gray>┌─ </gray><yellow>%num%. </yellow><white>%name%</white>")
                    .replace("%num%", String.valueOf(i + 1))
                    .replace("%name%", h.homeName())));
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("home.list_entry_world", "<gray>│ </gray><gray>World: </gray><white>%world%</white>").replace("%world%", h.world())));
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("home.list_entry_coords", "<gray>│ </gray><gray>X: </gray><white>%x%</white> <gray>Y: </gray><white>%y%</white> <gray>Z: </gray><white>%z%</white>")
                    .replace("%x%", String.format("%.1f", h.x()))
                    .replace("%y%", String.format("%.1f", h.y()))
                    .replace("%z%", String.format("%.1f", h.z()))));
            if (i < homes.size() - 1) {
                sender.sendMessage(MessageUtil.parse(MessagesManager.getString("home.list_entry_sep", "<gray>│</gray>")));
            }
        }

        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("home.info_header", "<gold>═══════════════════════════════════</gold>")));
        return true;
    }

    // ============================================================
    // OPDELHOME — оператор: удалить дом игрока
    // ============================================================
    private static boolean executeOpDelHome(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcplugin.command.opdelhome")) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("home.no_permission_opdelhome", "<red>❌ You don't have permission to delete other players' homes!</red>")));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("home.usage_opdelhome", "<red>❌ Usage: </red><white>/mp opdelhome <player> <home_name></white>")));
            return true;
        }

        String targetName = args[0];
        String homeName = args[1].trim();

        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (!HomeDatabase.homeExists(target.getUniqueId(), homeName)) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("home.not_found_player", "<red>❌ Player</red> <yellow>%player%</yellow><red> has no home</red> <yellow>%name%</yellow><red>!</red>")
                    .replace("%player%", target.getName())
                    .replace("%name%", homeName)));
            return true;
        }

        if (HomeDatabase.deleteHome(target.getUniqueId(), homeName)) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("home.delete_op_success", "<green>✔</green> <white>Home</white> <yellow>%name%</yellow> <white>of player</white> <yellow>%player%</yellow> <white>deleted.</white>")
                    .replace("%name%", homeName)
                    .replace("%player%", target.getName())));
        } else {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("home.delete_error", "<red>❌ Error deleting home!</red>")));
        }
        return true;
    }

    // ============================================================
    // TAB COMPLETE HELPERS
    // ============================================================
    private static List<String> tabCompleteSetHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return List.of();
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String name : HomeDatabase.getHomeNames(player.getUniqueId())) {
                if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(name);
                }
            }
            return out;
        }
        return List.of();
    }

    private static List<String> tabCompleteGetHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return List.of();
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String name : HomeDatabase.getHomeNames(player.getUniqueId())) {
                if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(name);
                }
            }
            return out;
        }
        return List.of();
    }

    private static List<String> tabCompleteDelHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return List.of();
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String name : HomeDatabase.getHomeNames(player.getUniqueId())) {
                if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(name);
                }
            }
            return out;
        }
        return List.of();
    }

    private static List<String> tabCompleteOpHomeLs(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(p.getName());
                }
            }
            return out;
        }
        return List.of();
    }

    private static List<String> tabCompleteOpDelHome(CommandSender sender, String[] args) {
        List<String> out = new ArrayList<>();

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(p.getName());
                }
            }
            return out;
        }

        if (args.length == 2) {
            @SuppressWarnings("deprecation")
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (String name : HomeDatabase.getHomeNames(target.getUniqueId())) {
                if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(name);
                }
            }
            return out;
        }

        return List.of();
    }
}
