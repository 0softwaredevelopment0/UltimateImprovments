package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.mechanics.protection.ProtectionBlock;
import com.ultimateimprovments.mechanics.protection.ProtectionConfig;
import com.ultimateimprovments.mechanics.protection.ProtectionItem;
import com.ultimateimprovments.mechanics.protection.ProtectionManager;
import com.ultimateimprovments.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.UUID;

/**
 * /ui protection &lt;give|list|info|delete|reload&gt; — admin operations on protection blocks.
 * <p>
 * Every user-facing line comes from {@code messages.protection.command.*}
 * (RU/EN), instead of the previously hardcoded Russian literals.
 */
public final class ProtectionSubcommand {

    private ProtectionSubcommand() {}

    /** Reads {@code messages.protection.command.<key>} with the built-in fallback. */
    private static String msg(String key, String def, String... placeholders) {
        String text = ProtectionConfig.getMessage("command." + key, def);
        if (placeholders != null) {
            for (int i = 0; i + 1 < placeholders.length; i += 2) {
                text = text.replace(placeholders[i], placeholders[i + 1]);
            }
        }
        return text;
    }

    public static boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ui.command.protection")) {
            CommandErrors.noPermission(sender, "ui.command.protection");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse(msg("usage",
                    "<red>❌ Usage: </red><white>/ui protection <give|list|info|delete|reload> [args]</white>")));
            return true;
        }
        return switch (args[1].toLowerCase()) {
            case "give" -> handleGive(sender, args);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, args);
            case "delete" -> handleDelete(sender, args);
            case "reload" -> handleReload(sender);
            default -> {
                sender.sendMessage(MessageUtil.parse(msg("unknown",
                        "<red>❌ Unknown subcommand: </red><white>%subcommand%</white>",
                        "%subcommand%", args[1])));
                yield true;
            }
        };
    }

    private static boolean handleGive(CommandSender sender, String[] args) {
        if (!ProtectionConfig.allowAdminGive()) {
            CommandErrors.moduleDisabled(sender, "protection.admin.give_allowed");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(msg("give_usage",
                    "<red>❌ Usage: </red><white>/ui protection give <player> [amount]</white>")));
            return true;
        }
        @SuppressWarnings("deprecation")
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(MessageUtil.parse(msg("player_not_found",
                    "<red>❌ Player </red><yellow>%player%</yellow><red> not online.</red>",
                    "%player%", args[2])));
            return true;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(MessageUtil.parse(msg("invalid_amount",
                        "<red>❌ Invalid amount: </red><white>%amount%</white>",
                        "%amount%", args[3])));
                return true;
            }
            if (amount <= 0) amount = 1;
        }
        if (ProtectionManager.getInstance().giveItemTo(target, amount)) {
            sender.sendMessage(MessageUtil.parse(msg("give_success",
                    "<green>✔</green> <white>Gave <gold>%amount%</gold>× Protection Block to "
                            + "<yellow>%player%</yellow>.</white>",
                    "%amount%", String.valueOf(amount),
                    "%player%", target.getName() != null ? target.getName() : args[2])));
            target.sendMessage(MessageUtil.parse(msg("give_received",
                    "<green>✔</green> <white>You received <gold>%amount%</gold>× "
                            + "<white>Блок защиты</white>.</white>",
                    "%amount%", String.valueOf(amount))));
        }
        return true;
    }

    private static boolean handleList(CommandSender sender) {
        Collection<ProtectionBlock> all = ProtectionManager.getInstance().allBlocks();
        sender.sendMessage(MessageUtil.parse(msg("list_header",
                "<gold>✦ <white>Защитные блоки: <gray>%count%",
                "%count%", String.valueOf(all.size()))));
        int idx = 0;
        for (ProtectionBlock b : all) {
            idx++;
            String status = b.isEnabled()
                    ? msg("status_on", "<green>[ON]")
                    : msg("status_off", "<red>[OFF]");
            sender.sendMessage(MessageUtil.parse(msg("list_entry",
                    "<dark_gray>[%index%] <white>%id% <dark_gray>@ <gray>%world% %x% %y% %z% %status% "
                            + "<gray>(r=%radius%, i=%integrity%, p=%points%)",
                    "%index%", String.valueOf(idx),
                    "%id%", b.getId().toString(),
                    "%world%", b.getWorld().getName(),
                    "%x%", String.valueOf(b.getX()),
                    "%y%", String.valueOf(b.getY()),
                    "%z%", String.valueOf(b.getZ()),
                    "%status%", status,
                    "%radius%", String.valueOf(b.getRadius()),
                    "%integrity%", String.format("%.1f%%", b.getIntegrity()),
                    "%points%", String.valueOf(b.getPoints()))));
        }
        return true;
    }

    private static boolean handleInfo(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            CommandErrors.playerOnly(sender);
            return true;
        }
        if (args.length < 3) {
            // Self — look at block at crosshair target
            ProtectionBlock target = targetBlockLookingAt(player);
            if (target == null) {
                player.sendMessage(MessageUtil.parse(msg("no_target",
                        "<red>❌ Aim at a protection block or specify coords.</red>")));
                return true;
            }
            sendBlockInfo(player, target);
            return true;
        }
        // Try coords
        try {
            int x = Integer.parseInt(args[2]);
            int y = Integer.parseInt(args[3]);
            int z = Integer.parseInt(args[4]);
            ProtectionBlock target = ProtectionManager.getInstance()
                    .getBlockAt(new Location(player.getWorld(), x, y, z));
            if (target == null) {
                player.sendMessage(MessageUtil.parse(msg("no_block_at_coords",
                        "<red>❌ No protection block at those coords.</red>")));
                return true;
            }
            sendBlockInfo(player, target);
            return true;
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            player.sendMessage(MessageUtil.parse(msg("invalid_coords",
                    "<red>❌ Invalid coords.</red>")));
            return true;
        }
    }

    private static ProtectionBlock targetBlockLookingAt(Player player) {
        Location eye = player.getEyeLocation();
        org.bukkit.block.Block target = null;
        for (int i = 0; i < 5; i++) {
            target = eye.clone().add(eye.getDirection().multiply(i)).getBlock();
            ProtectionBlock pb = ProtectionManager.getInstance().getBlockAt(target.getLocation());
            if (pb != null) return pb;
        }
        return null;
    }

    private static void sendBlockInfo(Player player, ProtectionBlock b) {
        player.sendMessage(MessageUtil.parse(msg("info_header", "<gold>✦ <white>Блок защиты")));
        player.sendMessage(MessageUtil.parse(msg("info_id",
                " <gray>ID: <white>%id%", "%id%", b.getId().toString())));
        player.sendMessage(MessageUtil.parse(msg("info_coords",
                " <gray>Координаты: <white>%world% %x% %y% %z%",
                "%world%", b.getWorld().getName(),
                "%x%", String.valueOf(b.getX()),
                "%y%", String.valueOf(b.getY()),
                "%z%", String.valueOf(b.getZ()))));
        player.sendMessage(MessageUtil.parse(msg("info_status",
                " <gray>Статус: %status%",
                "%status%", b.isEnabled()
                        ? msg("status_on", "<green>[ON]")
                        : msg("status_off", "<red>[OFF]"))));
        player.sendMessage(MessageUtil.parse(msg("info_radius",
                " <gray>Радиус: <white>%radius%/%max%",
                "%radius%", String.valueOf(b.getRadius()),
                "%max%", String.valueOf(ProtectionConfig.getMaxRadius()))));
        player.sendMessage(MessageUtil.parse(msg("info_integrity",
                " <gray>Целостность: <white>%integrity%",
                "%integrity%", String.format("%.1f%%", b.getIntegrity()))));
        player.sendMessage(MessageUtil.parse(msg("info_points",
                " <gray>Очки: <white>%points%", "%points%", String.valueOf(b.getPoints()))));
        player.sendMessage(MessageUtil.parse(msg("info_whitelist",
                " <gray>Whitelist (size): <white>%size%",
                "%size%", String.valueOf(b.getWhitelist().size()))));
        player.sendMessage(MessageUtil.parse(msg("info_next_radius",
                " <gray>След. upgrade radius: <white>%cost% очков",
                "%cost%", String.valueOf(b.getRadiusUpgradeCost()))));
        player.sendMessage(MessageUtil.parse(msg("info_next_repair",
                " <gray>След. repair: <white>%cost% очков",
                "%cost%", String.valueOf(b.getRepairCost()))));
    }

    private static boolean handleDelete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            CommandErrors.playerOnly(sender);
            return true;
        }
        if (args.length < 3) {
            ProtectionBlock target = targetBlockLookingAt(player);
            if (target == null) {
                player.sendMessage(MessageUtil.parse(msg("no_target",
                        "<red>❌ Aim at a protection block or specify coords.</red>")));
                return true;
            }
            ProtectionManager.getInstance().destroyBlock(target, true);
            player.sendMessage(MessageUtil.parse(msg("deleted",
                    "<yellow>Блок защиты удалён.</yellow>")));
            return true;
        }
        // Coord delete
        try {
            int x = Integer.parseInt(args[2]);
            int y = Integer.parseInt(args[3]);
            int z = Integer.parseInt(args[4]);
            ProtectionBlock target = ProtectionManager.getInstance()
                    .getBlockAt(new Location(player.getWorld(), x, y, z));
            if (target == null) {
                player.sendMessage(MessageUtil.parse(msg("no_block_at_coords",
                        "<red>❌ No protection block at those coords.</red>")));
                return true;
            }
            ProtectionManager.getInstance().destroyBlock(target, true);
            player.sendMessage(MessageUtil.parse(msg("deleted",
                    "<yellow>Блок защиты удалён.</yellow>")));
            return true;
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            player.sendMessage(MessageUtil.parse(msg("invalid_coords",
                    "<red>❌ Invalid coords.</red>")));
            return true;
        }
    }

    private static boolean handleReload(CommandSender sender) {
        // shutdown() cancels the hologram/dirty tasks and clears the cache; init()
        // rebuilds them. Without the shutdown the second init started a DUPLICATE
        // hologram task and re-registered every chunk key.
        ProtectionManager.getInstance().shutdown();
        ProtectionManager.getInstance().init();
        sender.sendMessage(MessageUtil.parse(msg("reload_done",
                "<green>✔</green> <white>Protection block system reloaded.</white>")));
        return true;
    }
}
