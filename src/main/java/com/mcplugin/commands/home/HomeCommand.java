package com.mcplugin.commands.home;

import com.mcplugin.Main;
import com.mcplugin.config.MessagesManager;
import com.mcplugin.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
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
        return com.mcplugin.Main.getInstance().getConfig().getInt("home.name_min_length", 1);
    }

    private static int getNameMax() {
        return com.mcplugin.Main.getInstance().getConfig().getInt("home.name_max_length", 16);
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
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.name_length_error", "<red>❌ Home name must be between {min} and {max} characters!</red>").replace("{min}", String.valueOf(nameMin)).replace("{max}", String.valueOf(nameMax))));
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.name_current_length", "<gray> Current length: </gray><white>{length}</white><gray> characters.</gray>").replace("{length}", String.valueOf(homeName.length()))));
            return true;
        }

        UUID uuid = player.getUniqueId();

        int maxHomes = HomeDatabase.getMaxHomes();
        if (!HomeDatabase.homeExists(uuid, homeName) && HomeDatabase.countHomes(uuid) >= maxHomes) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.limit_reached", "<red>❌ You have reached the limit of</red> <yellow>{max}</yellow> <red>home points!</red>").replace("{max}", String.valueOf(maxHomes))));
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.limit_hint", "<gray> Use </gray><white>/mp delhome <name></white><gray> to remove unwanted ones.</gray>")));
            return true;
        }

        Location loc = player.getLocation();

        if (HomeDatabase.saveHome(uuid, homeName, loc)) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.save_success", "<green>✅</green> <white>Home</white> <yellow>{name}</yellow> <white>saved!</white>").replace("{name}", homeName)));
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.save_info_world", "<gray>World:</gray> <white>{world}</white>").replace("{world}", loc.getWorld().getName())));
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.save_info_coords", "<gray>Coordinates:</gray> <white>{x} / {y} / {z}</white>")
                    .replace("{x}", String.format("%.1f", loc.getX()))
                    .replace("{y}", String.format("%.1f", loc.getY()))
                    .replace("{z}", String.format("%.1f", loc.getZ()))));
            int used = HomeDatabase.countHomes(uuid);
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.save_info_used", "<gray>Used:</gray> <white>{used}<gray>/{max}</gray></white>").replace("{used}", String.valueOf(used)).replace("{max}", String.valueOf(HomeDatabase.getMaxHomes()))));
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
        HomeDatabase.HomeData home = HomeDatabase.getHome(uuid, homeName);

        if (home == null) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.not_found", "<yellow>ℹ</yellow> <white>Home</white> <yellow>{name}</yellow> <white>not found.</white>").replace("{name}", homeName)));
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("home.not_found_hint", "<gray> Use </gray><white>/mp sethome {name}</white><gray> to save it.</gray>").replace("{name}", homeName)));
            return true;
        }

        player.sendMessage("§6═══════════════════════════════════");
        player.sendMessage("§6  ✦ §fДом: §e" + home.homeName());
        player.sendMessage("§6═══════════════════════════════════");
        player.sendMessage("§7Мир: §f" + home.world());
        player.sendMessage("§7X: §f" + String.format("%.1f", home.x()));
        player.sendMessage("§7Y: §f" + String.format("%.1f", home.y()));
        player.sendMessage("§7Z: §f" + String.format("%.1f", home.z()));
        player.sendMessage("§6═══════════════════════════════════");
        return true;
    }

    // ============================================================
    // DELHOME
    // ============================================================
    private static boolean executeDelHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§4❌ §cТолько игрок может использовать эту команду!");
            return true;
        }
        if (!player.hasPermission("mcplugin.command.delhome")) {
            player.sendMessage("§4❌ §cУ вас нет прав на удаление домашних точек!");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage("§4❌ §cИспользование: §f/mp delhome §7<название>");
            return true;
        }

        String homeName = args[0].trim();

        if (!HomeDatabase.homeExists(player.getUniqueId(), homeName)) {
            player.sendMessage("§4❌ §cДом §e" + homeName + "§c не найден!");
            return true;
        }

        if (HomeDatabase.deleteHome(player.getUniqueId(), homeName)) {
            player.sendMessage("§a✅ §fДом §e" + homeName + "§f удалён.");
        } else {
            player.sendMessage("§4❌ §cОшибка при удалении дома!");
        }
        return true;
    }

    // ============================================================
    // LISTHOMES
    // ============================================================
    private static boolean executeListHomes(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§4❌ §cТолько игрок может использовать эту команду!");
            return true;
        }
        if (!player.hasPermission("mcplugin.command.listhomes")) {
            player.sendMessage("§4❌ §cУ вас нет прав на просмотр списка домов!");
            return true;
        }

        UUID uuid = player.getUniqueId();
        List<HomeDatabase.HomeData> homes = HomeDatabase.listHomes(uuid);

        if (homes.isEmpty()) {
            player.sendMessage("§eℹ §fУ вас нет сохранённых домашних точек.");
            player.sendMessage("§7┃ Используйте §f/mp sethome <название>§7 чтобы сохранить.");
            return true;
        }

        int used = homes.size();
        player.sendMessage("§6═══════════════════════════════════");
        player.sendMessage("§6  ✦ §fВаши дома §7(" + used + "§7/§f" + HomeDatabase.getMaxHomes() + "§7)");
        player.sendMessage("§6═══════════════════════════════════");

        for (int i = 0; i < homes.size(); i++) {
            HomeDatabase.HomeData h = homes.get(i);
            player.sendMessage("§8┌─ §e" + (i + 1) + ". §f" + h.homeName());
            player.sendMessage("§8│ §7Мир: §f" + h.world());
            player.sendMessage("§8│ §7X: §f" + String.format("%.1f", h.x())
                    + " §7Y: §f" + String.format("%.1f", h.y())
                    + " §7Z: §f" + String.format("%.1f", h.z()));
            if (i < homes.size() - 1) {
                player.sendMessage("§8│");
            }
        }

        player.sendMessage("§6═══════════════════════════════════");
        return true;
    }

    // ============================================================
    // OPHOMELS — оператор: список домов игрока
    // ============================================================
    private static boolean executeOpHomeLs(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcplugin.command.ophomels")) {
            sender.sendMessage("§4❌ §cУ вас нет прав на просмотр домов других игроков!");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§4❌ §cИспользование: §f/mp ophomels §7<игрок>");
            return true;
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);

        List<HomeDatabase.HomeData> homes = HomeDatabase.listHomes(target.getUniqueId());

        if (homes.isEmpty()) {
            sender.sendMessage("§eℹ §fУ игрока §e" + target.getName() + "§f нет сохранённых домов.");
            return true;
        }

        sender.sendMessage("§6═══════════════════════════════════");
        sender.sendMessage("§6  ✦ §fДома игрока §e" + target.getName() + " §7(" + homes.size() + "§7)");
        sender.sendMessage("§6═══════════════════════════════════");

        for (int i = 0; i < homes.size(); i++) {
            HomeDatabase.HomeData h = homes.get(i);
            sender.sendMessage("§8┌─ §e" + (i + 1) + ". §f" + h.homeName());
            sender.sendMessage("§8│ §7Мир: §f" + h.world());
            sender.sendMessage("§8│ §7X: §f" + String.format("%.1f", h.x())
                    + " §7Y: §f" + String.format("%.1f", h.y())
                    + " §7Z: §f" + String.format("%.1f", h.z()));
            if (i < homes.size() - 1) {
                sender.sendMessage("§8│");
            }
        }

        sender.sendMessage("§6═══════════════════════════════════");
        return true;
    }

    // ============================================================
    // OPDELHOME — оператор: удалить дом игрока
    // ============================================================
    private static boolean executeOpDelHome(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcplugin.command.opdelhome")) {
            sender.sendMessage("§4❌ §cУ вас нет прав на удаление домов других игроков!");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§4❌ §cИспользование: §f/mp opdelhome §7<игрок> <название дома>");
            return true;
        }

        String targetName = args[0];
        String homeName = args[1].trim();

        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (!HomeDatabase.homeExists(target.getUniqueId(), homeName)) {
            sender.sendMessage("§4❌ §cУ игрока §e" + target.getName() + "§c нет дома §e" + homeName + "§c!");
            return true;
        }

        if (HomeDatabase.deleteHome(target.getUniqueId(), homeName)) {
            sender.sendMessage("§a✅ §fДом §e" + homeName + "§f игрока §e" + target.getName() + "§f удалён.");
        } else {
            sender.sendMessage("§4❌ §cОшибка при удалении дома!");
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
