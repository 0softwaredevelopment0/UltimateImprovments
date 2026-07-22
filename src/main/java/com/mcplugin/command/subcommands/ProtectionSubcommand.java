package com.mcplugin.command.subcommands;

import com.mcplugin.core.Main;
import com.mcplugin.mechanics.protection.ProtectionBlock;
import com.mcplugin.mechanics.protection.ProtectionConfig;
import com.mcplugin.mechanics.protection.ProtectionItem;
import com.mcplugin.mechanics.protection.ProtectionManager;
import com.mcplugin.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.UUID;

/**
 * /mp protection &lt;give|list|info|delete&gt; — админ-операции над блоками защиты.
 */
public final class ProtectionSubcommand {

    private ProtectionSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcplugin.command.protection")) {
            sender.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to manage protection blocks!</red>"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/mp protection <give|list|info|delete> [args]</white>"));
            return true;
        }
        return switch (args[1].toLowerCase()) {
            case "give" -> handleGive(sender, args);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, args);
            case "delete" -> handleDelete(sender, args);
            case "reload" -> handleReload(sender);
            default -> {
                sender.sendMessage(MessageUtil.parse(
                        "<red>❌ Unknown subcommand: </red><white>" + args[1] + "</white>"));
                yield true;
            }
        };
    }

    private static boolean handleGive(CommandSender sender, String[] args) {
        if (!ProtectionConfig.allowAdminGive()) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Admin give is disabled in config (protection.admin.give_allowed).</red>"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/mp protection give <player> [amount]</white>"));
            return true;
        }
        @SuppressWarnings("deprecation")
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Player </red><yellow>" + args[2] + "</yellow><red> not online.</red>"));
            return true;
        }
        int amount = 1;
        if (args.length >= 4) {
            try { amount = Integer.parseInt(args[3]); }
            catch (NumberFormatException e) {
                sender.sendMessage(MessageUtil.parse("<red>❌ Invalid amount: </red><white>" + args[3] + "</white>"));
                return true;
            }
        }
        if (ProtectionManager.getInstance().giveItemTo(target, amount)) {
            sender.sendMessage(MessageUtil.parse("<green>✔</green> <white>Gave <gold>" + amount + "</gold>× Protection Block to <yellow>"
                    + target.getName() + "</yellow>.</white>"));
            target.sendMessage(MessageUtil.parse("<green>✔</green> <white>You received <gold>" + amount + "</gold>× <white>Блок защиты</white>.</white>"));
        }
        return true;
    }

    private static boolean handleList(CommandSender sender) {
        Collection<ProtectionBlock> all = ProtectionManager.getInstance().allBlocks();
        sender.sendMessage("§6✦ §fЗащитные блоки: §7" + all.size());
        int idx = 0;
        for (ProtectionBlock b : all) {
            idx++;
            sender.sendMessage("§8[" + idx + "] §f" + b.getId() + " §8@ §7" + b.getWorld().getName()
                    + " " + b.getX() + " " + b.getY() + " " + b.getZ()
                    + (b.isEnabled() ? " §a[ON]" : " §c[OFF]")
                    + " §7(r=" + b.getRadius() + ", i=" + String.format("%.1f%%", b.getIntegrity())
                    + ", p=" + b.getPoints() + ")");
        }
        return true;
    }

    private static boolean handleInfo(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Only players can use this command!</red>"));
            return true;
        }
        if (args.length < 3) {
            // Self — look at block at crosshair target
            ProtectionBlock target = targetBlockLookingAt(player);
            if (target == null) {
                player.sendMessage(MessageUtil.parse("<red>❌ Aim at a protection block or specify coords.</red>"));
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
                player.sendMessage(MessageUtil.parse("<red>❌ No protection block at those coords.</red>"));
                return true;
            }
            sendBlockInfo(player, target);
            return true;
        } catch (NumberFormatException e) {
            player.sendMessage(MessageUtil.parse("<red>❌ Invalid coords.</red>"));
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
        player.sendMessage("§6✦ §fБлок защиты");
        player.sendMessage(" §7ID: §f" + b.getId());
        player.sendMessage(" §7Координаты: §f" + b.getWorld().getName() + " "
                + b.getX() + " " + b.getY() + " " + b.getZ());
        player.sendMessage(" §7Статус: " + (b.isEnabled() ? "§aON" : "§cOFF"));
        player.sendMessage(" §7Радиус: §f" + b.getRadius() + "/" + ProtectionConfig.getMaxRadius());
        player.sendMessage(" §7Целостность: §f" + String.format("%.1f%%", b.getIntegrity()));
        player.sendMessage(" §7Очки: §f" + b.getPoints());
        player.sendMessage(" §7Whitelist (size): §f" + b.getWhitelist().size());
        player.sendMessage(" §7След. upgrade radius: §f" + b.getRadiusUpgradeCost() + " очков");
        player.sendMessage(" §7След. repair: §f" + b.getRepairCost() + " очков");
    }

    private static boolean handleDelete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Only players can use this command!</red>"));
            return true;
        }
        if (args.length < 3) {
            ProtectionBlock target = targetBlockLookingAt(player);
            if (target == null) {
                player.sendMessage(MessageUtil.parse("<red>❌ Aim at a protection block or specify coords.</red>"));
                return true;
            }
            ProtectionManager.getInstance().destroyBlock(target, true);
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
                player.sendMessage(MessageUtil.parse("<red>❌ No protection block at those coords.</red>"));
                return true;
            }
            ProtectionManager.getInstance().destroyBlock(target, true);
            return true;
        } catch (NumberFormatException e) {
            player.sendMessage(MessageUtil.parse("<red>❌ Invalid coords.</red>"));
            return true;
        }
    }

    private static boolean handleReload(CommandSender sender) {
        ProtectionManager.getInstance().shutdown();
        ProtectionManager.getInstance().init();
        sender.sendMessage(MessageUtil.parse("<green>✔</green> <white>Protection block system reloaded.</white>"));
        return true;
    }
}
