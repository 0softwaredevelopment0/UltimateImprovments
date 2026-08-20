package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.command.SubCommand;
import com.ultimateimprovments.command.clan.ClanDatabase;
import com.ultimateimprovments.command.clan.ClanManager;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /ui clan — clan system.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>{@code create <name>} — create a clan (name may contain MiniMessage)</li>
 *   <li>{@code remove <name>} — admin: delete any clan by its plain name</li>
 *   <li>{@code disband [-confirm]} — disband your own clan (with confirmation)</li>
 *   <li>{@code listclans [page]} — list all clans</li>
 *   <li>{@code edit add <nick> <member|moderator|organizer>} — add a player to your clan</li>
 *   <li>{@code edit remove <nick>} — remove a player from your clan</li>
 *   <li>{@code edit list [page]} — list clan members (10 per page)</li>
 *   <li>{@code edit home add|set [-confirm]|delhome [-confirm]} — clan home management</li>
 *   <li>{@code home} — teleport to the clan home (or show coords in legit mode)</li>
 *   <li>{@code request|reqest <clan>} — apply to join a clan (10s cooldown, 1h expiry)</li>
 *   <li>{@code request|reqest accept|decline <nick>} — handle join requests</li>
 *   <li>{@code leave [-confirm]} — leave your clan (owner cannot leave)</li>
 * </ul>
 *
 * <p>Permission: {@code ui.command.clan} (default true), {@code ui.command.clan.remove}
 * for the admin remove command.</p>
 */
public final class ClanSubcommand implements SubCommand {

    private static final String PERMISSION = "ui.command.clan";
    private static final String PERMISSION_REMOVE = "ui.command.clan.remove";
    private static final int PER_PAGE = 10;

    @Override
    public String getName() {
        return "clan";
    }

    @Override
    public List<String> getAliases() {
        return List.of("clans");
    }

    // ============================================================
    // EXECUTE
    // ============================================================

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<red>✖ <white>Only players can use this command.</white>"));
            return true;
        }
        if (!player.hasPermission(PERMISSION)) {
            CommandErrors.noPermission(player);
            return true;
        }
        if (args.length < 2) {
            sendUsage(player);
            return true;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);
        String[] rest = slice(args, 2);

        return switch (sub) {
            case "create" -> cmdCreate(player, rest);
            case "remove" -> cmdRemove(player, rest);
            case "disband" -> cmdDisband(player, rest);
            case "listclans", "list" -> cmdListClans(player, rest);
            case "edit" -> cmdEdit(player, rest);
            case "home" -> cmdHome(player, rest);
            case "request", "reqest" -> cmdRequest(player, rest);
            case "leave" -> cmdLeave(player, rest);
            default -> {
                sendUsage(player);
                yield true;
            }
        };
    }

    // ============================================================
    // CREATE
    // ============================================================

    private boolean cmdCreate(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan create <name></yellow>"));
            return true;
        }
        UUID uuid = player.getUniqueId();

        // A player can only be in ONE clan (owner or member).
        String existing = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (existing != null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You are already in a clan — leave it first.</white>"));
            return true;
        }

        String rawName = String.join(" ", args);
        String key = normalizeKey(rawName);
        int min = cfgInt("clan.name_min_length", 2);
        int max = cfgInt("clan.name_max_length", 32);
        if (key.length() < min || key.length() > max) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Clan name must be </white><yellow>" + min
                    + "-" + max + "</yellow><white> characters long.</white>"));
            return true;
        }
        if (ClanDatabase.clanExists(key)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>A clan with this name already exists.</white>"));
            return true;
        }

        if (ClanDatabase.createClan(key, rawName, uuid.toString(), player.getName())) {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Clan </white><yellow>" + rawName
                    + "</yellow> <white>has been created!</white>"));
            player.sendMessage(MessageUtil.parse("<gray>Use </gray><yellow>/ui clan edit add <nick> member</yellow>"
                    + "<gray> to invite players.</gray>"));
        } else {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed to create the clan.</white>"));
        }
        return true;
    }

    // ============================================================
    // REMOVE (admin)
    // ============================================================

    private boolean cmdRemove(Player player, String[] args) {
        if (!player.hasPermission(PERMISSION_REMOVE)) {
            CommandErrors.noPermission(player);
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan remove <name></yellow>"));
            return true;
        }
        String key = normalizeKey(String.join(" ", args));
        if (!ClanDatabase.clanExists(key)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Clan not found.</white>"));
            return true;
        }
        ClanDatabase.ClanData clan = ClanDatabase.getClan(key);
        if (ClanDatabase.deleteClan(key)) {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Clan </white><yellow>"
                    + MessageUtil.toPlainText(clan.displayName()) + "</yellow> <white>has been removed.</white>"));
            Bukkit.broadcast(MessageUtil.parse("<red>⚠</red> <white>The clan </white><yellow>"
                    + MessageUtil.toPlainText(clan.displayName()) + "</yellow> <white>has been disbanded by an administrator.</white>"));
        } else {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed to remove the clan.</white>"));
        }
        return true;
    }

    // ============================================================
    // DISBAND
    // ============================================================

    private boolean cmdDisband(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        String key = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (key == null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>"));
            return true;
        }
        String role = ClanDatabase.getRole(key, uuid.toString());
        if (!isOrganizer(role)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Only the clan organizer can disband the clan.</white>"));
            return true;
        }

        // Stage 2 — confirmed
        if (args.length >= 1 && args[0].equalsIgnoreCase("-confirm")) {
            if (!ClanManager.consumeConfirm(player, ClanManager.CONFIRM_DISBAND)) {
                player.sendMessage(MessageUtil.parse("<red>✖ <white>Confirmation expired. Run </white>"
                        + "<yellow>/ui clan disband</yellow><white> again.</white>"));
                return true;
            }
            ClanDatabase.ClanData clan = ClanDatabase.getClan(key);
            String name = clan != null ? MessageUtil.toPlainText(clan.displayName()) : key;
            if (ClanDatabase.deleteClan(key)) {
                player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Your clan </white><yellow>" + name
                        + "</yellow> <white>has been disbanded.</white>"));
                Bukkit.broadcast(MessageUtil.parse("<red>⚠</red> <white>The clan </white><yellow>" + name
                        + "</yellow> <white>has been disbanded.</white>"));
            } else {
                player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed to disband the clan.</white>"));
            }
            return true;
        }

        // Stage 1 — confirmation
        ClanManager.armConfirm(player, ClanManager.CONFIRM_DISBAND);
        player.sendMessage(MessageUtil.parse("<red>⚠ <white>Are you sure you want to disband your clan?</white>"
                + " <red>This cannot be undone!</red>"));
        sendConfirmButton(player, "/ui clan disband -confirm", "Disband the clan");
        return true;
    }

    // ============================================================
    // LIST CLANS
    // ============================================================

    private boolean cmdListClans(Player player, String[] args) {
        int page = 1;
        if (args.length >= 1) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }
        List<ClanDatabase.ClanData> clans = ClanDatabase.getAllClans();
        int totalPages = Math.max(1, (clans.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(1, Math.min(page, totalPages));

        int from = (page - 1) * PER_PAGE;
        int to = Math.min(from + PER_PAGE, clans.size());

        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        player.sendMessage(MessageUtil.parse("<gold>  ✦ </gold><white>Clans </white><gray>(" + page + "/" + totalPages + ")</gray>"));
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));

        if (clans.isEmpty()) {
            player.sendMessage(MessageUtil.parse("<gray>No clans yet. Create one with </gray><yellow>/ui clan create <name></yellow>"));
        } else {
            for (int i = from; i < to; i++) {
                ClanDatabase.ClanData c = clans.get(i);
                int members = ClanDatabase.countMembers(c.key());
                String ownerName = ClanDatabase.getMemberName(c.key(), c.ownerUuid());
                player.sendMessage(MessageUtil.parse("<gray>┌─ </gray><yellow>" + (i + 1) + ".</yellow> "
                        + "<white>" + c.displayName() + "</white> <gray>(" + members + " members)</gray>"));
                player.sendMessage(MessageUtil.parse("<gray>│ </gray><gray>Owner: </gray><white>"
                        + (ownerName != null ? ownerName : "unknown") + "</white>"));
            }
        }

        // Footer with page buttons
        net.kyori.adventure.text.Component footer = MessageUtil.parse(
                "<dark_gray>┃  <gray>Page <yellow>" + page + "<gray>/" + totalPages + "   ");
        if (page > 1) {
            footer = footer.append(net.kyori.adventure.text.Component.text("[<]")
                    .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW)
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(
                            "/ui clan listclans " + (page - 1)))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                            MessageUtil.parse("<gray>Previous page"))));
        } else {
            footer = footer.append(MessageUtil.parse("<dark_gray>[<]"));
        }
        footer = footer.append(MessageUtil.parse("  "));
        if (page < totalPages) {
            footer = footer.append(net.kyori.adventure.text.Component.text("[>]")
                    .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW)
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(
                            "/ui clan listclans " + (page + 1)))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                            MessageUtil.parse("<gray>Next page"))));
        } else {
            footer = footer.append(MessageUtil.parse("<dark_gray>[>]"));
        }
        player.sendMessage(footer);
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        return true;
    }

    // ============================================================
    // EDIT
    // ============================================================

    private boolean cmdEdit(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan edit <add|remove|list|home></yellow>"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] rest = slice(args, 1);
        return switch (sub) {
            case "add" -> cmdEditAdd(player, rest);
            case "remove" -> cmdEditRemove(player, rest);
            case "list" -> cmdEditList(player, rest);
            case "home" -> cmdEditHome(player, rest);
            default -> {
                player.sendMessage(MessageUtil.parse("<red>✖ <white>Unknown edit command: </white><yellow>" + sub + "</yellow>"));
                yield true;
            }
        };
    }

    private boolean cmdEditAdd(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan edit add <nick> <member|moderator|organizer></yellow>"));
            return true;
        }
        UUID uuid = player.getUniqueId();
        String key = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (key == null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>"));
            return true;
        }
        String myRole = ClanDatabase.getRole(key, uuid.toString());
        if (!canManage(myRole)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You need at least the moderator role to add players.</white>"));
            return true;
        }

        String role = args[1].toLowerCase(Locale.ROOT);
        if (!List.of("member", "moderator", "organizer").contains(role)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Invalid role. Choose: </white><yellow>member, moderator, organizer</yellow>"));
            return true;
        }
        // Only organizers can grant moderator/organizer.
        if (!isOrganizer(myRole) && !role.equals("member")) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Only the organizer can assign </white><yellow>" + role + "</yellow><white> role.</white>"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Player </white><yellow>" + args[0] + "</yellow><white> not found.</white>"));
            return true;
        }
        if (target.getUniqueId().equals(uuid)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You are already a member of your clan.</white>"));
            return true;
        }
        String targetUuid = target.getUniqueId().toString();
        String targetClan = ClanDatabase.getClanKeyByPlayer(targetUuid);
        if (targetClan != null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Player </white><yellow>" + args[0]
                    + "</yellow><white> is already in a clan.</white>"));
            return true;
        }

        if (ClanDatabase.addMember(key, targetUuid, target.getName(), role)) {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Player </white><yellow>" + target.getName()
                    + "</yellow> <white>added as </white><yellow>" + role + "</yellow><white>.</white>"));
            Player online = Bukkit.getPlayer(target.getUniqueId());
            if (online != null) {
                online.sendMessage(MessageUtil.parse("<green>✔</green> <white>You joined the clan!</white>"));
            }
        } else {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed to add the player.</white>"));
        }
        return true;
    }

    private boolean cmdEditRemove(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan edit remove <nick></yellow>"));
            return true;
        }
        UUID uuid = player.getUniqueId();
        String key = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (key == null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>"));
            return true;
        }
        String myRole = ClanDatabase.getRole(key, uuid.toString());
        if (!canManage(myRole)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You need at least the moderator role to remove players.</white>"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String targetUuid = target.getUniqueId().toString();
        String targetRole = ClanDatabase.getRole(key, targetUuid);
        if (targetRole == null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Player </white><yellow>" + args[0]
                    + "</yellow><white> is not in your clan.</white>"));
            return true;
        }
        // Cannot kick the creator.
        ClanDatabase.ClanData clan = ClanDatabase.getClan(key);
        if (clan != null && clan.ownerUuid().equals(targetUuid)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You cannot remove the clan creator.</white>"));
            return true;
        }
        // A moderator can only remove members.
        if (!isOrganizer(myRole) && !targetRole.equals(ClanDatabase.ROLE_MEMBER)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Only the organizer can remove moderators or organizers.</white>"));
            return true;
        }

        if (ClanDatabase.removeMember(key, targetUuid)) {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Player </white><yellow>" + target.getName()
                    + "</yellow> <white>removed from the clan.</white>"));
            Player online = Bukkit.getPlayer(target.getUniqueId());
            if (online != null) {
                online.sendMessage(MessageUtil.parse("<red>✖ <white>You were removed from the clan.</white>"));
            }
        } else {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed to remove the player.</white>"));
        }
        return true;
    }

    private boolean cmdEditList(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        String key = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (key == null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>"));
            return true;
        }
        int page = 1;
        if (args.length >= 1) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }
        List<ClanDatabase.MemberData> members = ClanDatabase.getMembers(key);
        int totalPages = Math.max(1, (members.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(1, Math.min(page, totalPages));
        int from = (page - 1) * PER_PAGE;
        int to = Math.min(from + PER_PAGE, members.size());

        ClanDatabase.ClanData clan = ClanDatabase.getClan(key);
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        player.sendMessage(MessageUtil.parse("<gold>  ✦ </gold><white>Clan members </white><yellow>"
                + (clan != null ? clan.displayName() : key) + "</yellow> <gray>(" + page + "/" + totalPages + ")</gray>"));
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));

        for (int i = from; i < to; i++) {
            ClanDatabase.MemberData m = members.get(i);
            String roleColor = switch (m.role()) {
                case ClanDatabase.ROLE_ORGANIZER -> "<red>";
                case ClanDatabase.ROLE_MODERATOR -> "<gold>";
                default -> "<white>";
            };
            player.sendMessage(MessageUtil.parse("<gray>┌─ </gray><yellow>" + (i + 1) + ".</yellow> "
                    + "<white>" + m.playerName() + "</white> <gray>—</gray> " + roleColor + m.role()));
        }

        net.kyori.adventure.text.Component footer = MessageUtil.parse(
                "<dark_gray>┃  <gray>Page <yellow>" + page + "<gray>/" + totalPages + "   ");
        if (page > 1) {
            footer = footer.append(net.kyori.adventure.text.Component.text("[<]")
                    .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW)
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(
                            "/ui clan edit list " + (page - 1)))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                            MessageUtil.parse("<gray>Previous page"))));
        } else {
            footer = footer.append(MessageUtil.parse("<dark_gray>[<]"));
        }
        footer = footer.append(MessageUtil.parse("  "));
        if (page < totalPages) {
            footer = footer.append(net.kyori.adventure.text.Component.text("[>]")
                    .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW)
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(
                            "/ui clan edit list " + (page + 1)))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                            MessageUtil.parse("<gray>Next page"))));
        } else {
            footer = footer.append(MessageUtil.parse("<dark_gray>[>]"));
        }
        player.sendMessage(footer);
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        return true;
    }

    // ============================================================
    // EDIT HOME
    // ============================================================

    private boolean cmdEditHome(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan edit home <add|set|delhome></yellow>"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "add" -> cmdHomeAdd(player, new String[0]);
            case "set" -> cmdHomeSet(player, args.length > 1 && args[1].equalsIgnoreCase("-confirm"));
            case "delhome", "del", "remove" -> cmdHomeDel(player, args.length > 1 && args[1].equalsIgnoreCase("-confirm"));
            default -> {
                player.sendMessage(MessageUtil.parse("<red>✖ <white>Unknown home command: </white><yellow>" + sub + "</yellow>"));
                yield true;
            }
        };
    }

    private boolean cmdHomeAdd(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        String key = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (key == null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>"));
            return true;
        }
        String myRole = ClanDatabase.getRole(key, uuid.toString());
        if (!canManage(myRole)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You need at least the moderator role to set a clan home.</white>"));
            return true;
        }
        ClanDatabase.ClanData clan = ClanDatabase.getClan(key);
        if (clan != null && clan.hasHome()) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Your clan already has a home.</white>"
                    + " <gray>Use </gray><yellow>/ui clan edit home set</yellow><gray> to override it.</gray>"));
            return true;
        }
        if (ClanDatabase.setHome(key, player.getLocation())) {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Clan home has been set at your location.</white>"));
        } else {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed to set the clan home.</white>"));
        }
        return true;
    }

    private boolean cmdHomeSet(Player player, boolean confirmed) {
        UUID uuid = player.getUniqueId();
        String key = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (key == null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>"));
            return true;
        }
        String myRole = ClanDatabase.getRole(key, uuid.toString());
        if (!canManage(myRole)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You need at least the moderator role to set a clan home.</white>"));
            return true;
        }
        if (confirmed) {
            if (!ClanManager.consumeConfirm(player, ClanManager.CONFIRM_HOME_SET)) {
                player.sendMessage(MessageUtil.parse("<red>✖ <white>Confirmation expired. Run </white>"
                        + "<yellow>/ui clan edit home set</yellow><white> again.</white>"));
                return true;
            }
            if (ClanDatabase.setHome(key, player.getLocation())) {
                player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Clan home has been overridden.</white>"));
            } else {
                player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed to set the clan home.</white>"));
            }
            return true;
        }
        ClanManager.armConfirm(player, ClanManager.CONFIRM_HOME_SET);
        player.sendMessage(MessageUtil.parse("<red>⚠ <white>Are you sure you want to override the clan home?</white>"));
        sendConfirmButton(player, "/ui clan edit home set -confirm", "Override the clan home");
        return true;
    }

    private boolean cmdHomeDel(Player player, boolean confirmed) {
        UUID uuid = player.getUniqueId();
        String key = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (key == null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>"));
            return true;
        }
        String myRole = ClanDatabase.getRole(key, uuid.toString());
        if (!canManage(myRole)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You need at least the moderator role to delete the clan home.</white>"));
            return true;
        }
        if (confirmed) {
            if (!ClanManager.consumeConfirm(player, ClanManager.CONFIRM_HOME_DEL)) {
                player.sendMessage(MessageUtil.parse("<red>✖ <white>Confirmation expired. Run </white>"
                        + "<yellow>/ui clan edit delhome</yellow><white> again.</white>"));
                return true;
            }
            if (ClanDatabase.deleteHome(key)) {
                player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Clan home has been deleted.</white>"));
            } else {
                player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed to delete the clan home.</white>"));
            }
            return true;
        }
        ClanManager.armConfirm(player, ClanManager.CONFIRM_HOME_DEL);
        player.sendMessage(MessageUtil.parse("<red>⚠ <white>Are you sure you want to delete the clan home?</white>"));
        sendConfirmButton(player, "/ui clan edit delhome -confirm", "Delete the clan home");
        return true;
    }

    // ============================================================
    // HOME
    // ============================================================

    private boolean cmdHome(Player player, String[] rest) {
        UUID uuid = player.getUniqueId();
        String key = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (key == null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>"));
            return true;
        }
        ClanDatabase.ClanData clan = ClanDatabase.getClan(key);
        if (clan == null || !clan.hasHome()) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Your clan has no home.</white>"
                    + " <gray>Use </gray><yellow>/ui clan edit home add</yellow>"));
            return true;
        }

        String mode = cfgString("clan.home.mode", "legit");

        if (mode.equalsIgnoreCase("standard")) {
            World world = Bukkit.getWorld(clan.homeWorld());
            if (world == null) {
                player.sendMessage(MessageUtil.parse("<red>✖ <white>Clan home world </white><yellow>"
                        + clan.homeWorld() + "</yellow><white> is not loaded.</white>"));
                return true;
            }
            int cd = cfgInt("clan.home.tp_cooldown_seconds", 0);
            if (cd > 0 && !player.hasPermission("ui.command.clan.home.bypasscooldown")) {
                Long last = clanTpCooldowns.get(uuid);
                if (last != null && System.currentTimeMillis() - last < cd * 1000L) {
                    long rem = (cd * 1000L - (System.currentTimeMillis() - last)) / 1000L + 1;
                    player.sendMessage(MessageUtil.parse("<red>✖ <white>Wait </white><yellow>" + rem
                            + "</yellow><white> seconds before teleporting again.</white>"));
                    return true;
                }
            }
            Location loc = new Location(world, clan.homeX(), clan.homeY(), clan.homeZ(),
                    clan.homeYaw(), clan.homePitch());
            player.teleport(loc);
            clanTpCooldowns.put(uuid, System.currentTimeMillis());
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Teleported to the clan home.</white>"));
        } else {
            // Legit mode — no teleport, only show coordinates.
            player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
            player.sendMessage(MessageUtil.parse("<gold>  ✦ </gold><white>Clan Home</white>"));
            player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
            player.sendMessage(MessageUtil.parse("<gray>World: </gray><white>" + clan.homeWorld() + "</white>"));
            player.sendMessage(MessageUtil.parse("<gray>X: </gray><white>" + Math.round(clan.homeX()) + "</white>"));
            player.sendMessage(MessageUtil.parse("<gray>Y: </gray><white>" + Math.round(clan.homeY()) + "</white>"));
            player.sendMessage(MessageUtil.parse("<gray>Z: </gray><white>" + Math.round(clan.homeZ()) + "</white>"));
            player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
            player.sendMessage(MessageUtil.parse("<yellow>⚠</yellow> <gray>Clan home is in </gray><yellow>legit</yellow>"
                    + "<gray> mode — no teleport. Travel manually.</gray>"));
        }
        return true;
    }

    // ============================================================
    // REQUESTS
    // ============================================================

    private boolean cmdRequest(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan request <clan>"
                    + " | /ui clan request accept|decline <nick></yellow>"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("accept") || sub.equals("decline")) {
            return handleRequestDecision(player, sub, slice(args, 1));
        }
        return requestJoin(player, args);
    }

    private boolean requestJoin(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        String key = normalizeKey(String.join(" ", args));
        if (!ClanDatabase.clanExists(key)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Clan not found.</white>"));
            return true;
        }
        // Creator cannot join another clan — must disband first.
        if (ClanDatabase.getClanKeyByPlayer(uuid.toString()) != null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You are already in a clan."
                    + " You cannot be in two clans at once.</white>"));
            return true;
        }
        // 10s cooldown
        if (!ClanManager.canRequest(uuid)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Wait a few seconds before sending another request.</white>"));
            return true;
        }
        // 1h expiry — replace existing request.
        ClanDatabase.addRequest(key, uuid.toString(), player.getName());
        ClanManager.markRequest(uuid);

        player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Join request sent to clan </white><yellow>"
                + MessageUtil.toPlainText(ClanDatabase.getClan(key).displayName()) + "</yellow><white>.</white>"));

        // Notify moderators/organizers online
        for (ClanDatabase.MemberData m : ClanDatabase.getMembers(key)) {
            if (canManage(m.role())) {
                Player mod = Bukkit.getPlayer(UUID.fromString(m.playerUuid()));
                if (mod != null && mod.isOnline()) {
                    mod.sendMessage(MessageUtil.parse("<yellow>ℹ</yellow> <white>Player </white><yellow>"
                            + player.getName() + "</yellow><white> requested to join your clan. </white>"
                            + "<gray>Accept: </gray><yellow>/ui clan request accept " + player.getName() + "</yellow>"));
                }
            }
        }
        return true;
    }

    private boolean handleRequestDecision(Player player, String decision, String[] args) {
        if (args.length < 1) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan request "
                    + decision + " <nick></yellow>"));
            return true;
        }
        UUID uuid = player.getUniqueId();
        String key = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (key == null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>"));
            return true;
        }
        String myRole = ClanDatabase.getRole(key, uuid.toString());
        if (!canManage(myRole)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You need at least the moderator role to handle requests.</white>"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String targetUuid = target.getUniqueId().toString();
        ClanDatabase.RequestData req = ClanDatabase.getRequest(key, targetUuid);
        if (req == null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>No pending request from </white><yellow>"
                    + args[0] + "</yellow><white>.</white>"));
            return true;
        }

        // Check expiry
        int expireSeconds = cfgInt("clan.request_expire_seconds", 3600);
        if (System.currentTimeMillis() - req.requestedAt() > expireSeconds * 1000L) {
            ClanDatabase.removeRequest(key, targetUuid);
            player.sendMessage(MessageUtil.parse("<red>✖ <white>The request has expired.</white>"));
            return true;
        }

        ClanDatabase.removeRequest(key, targetUuid);

        if (decision.equals("accept")) {
            // Target must still be clan-free.
            if (ClanDatabase.getClanKeyByPlayer(targetUuid) != null) {
                player.sendMessage(MessageUtil.parse("<red>✖ <white>Player </white><yellow>" + args[0]
                        + "</yellow><white> already joined another clan.</white>"));
                return true;
            }
            ClanDatabase.addMember(key, targetUuid, target.getName(), ClanDatabase.ROLE_MEMBER);
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Player </white><yellow>" + target.getName()
                    + "</yellow> <white>joined the clan.</white>"));
            Player online = Bukkit.getPlayer(target.getUniqueId());
            if (online != null) {
                online.sendMessage(MessageUtil.parse("<green>✔</green> <white>Your request was accepted! You joined the clan.</white>"));
            }
        } else {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Request from </white><yellow>" + target.getName()
                    + "</yellow> <white>declined.</white>"));
            Player online = Bukkit.getPlayer(target.getUniqueId());
            if (online != null) {
                online.sendMessage(MessageUtil.parse("<red>✖ <white>Your clan request was declined.</white>"));
            }
        }
        return true;
    }

    // ============================================================
    // LEAVE
    // ============================================================

    private boolean cmdLeave(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        String key = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (key == null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>"));
            return true;
        }
        ClanDatabase.ClanData clan = ClanDatabase.getClan(key);
        if (clan != null && clan.ownerUuid().equals(uuid.toString())) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>As the clan creator you cannot leave.</white>"
                    + " <gray>Use </gray><yellow>/ui clan disband</yellow><gray> to disband the clan.</gray>"));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("-confirm")) {
            if (!ClanManager.consumeConfirm(player, ClanManager.CONFIRM_LEAVE)) {
                player.sendMessage(MessageUtil.parse("<red>✖ <white>Confirmation expired. Run </white>"
                        + "<yellow>/ui clan leave</yellow><white> again.</white>"));
                return true;
            }
            if (ClanDatabase.removeMember(key, uuid.toString())) {
                player.sendMessage(MessageUtil.parse("<green>✔</green> <white>You left the clan.</white>"));
            } else {
                player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed to leave the clan.</white>"));
            }
            return true;
        }
        ClanManager.armConfirm(player, ClanManager.CONFIRM_LEAVE);
        player.sendMessage(MessageUtil.parse("<red>⚠ <white>Are you sure you want to leave your clan?</white>"));
        sendConfirmButton(player, "/ui clan leave -confirm", "Leave the clan");
        return true;
    }

    // ============================================================
    // TAB COMPLETE
    // ============================================================

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length <= 2) {
            String prefix = args.length == 2 ? args[1].toLowerCase(Locale.ROOT) : "";
            List<String> out = new ArrayList<>(List.of(
                    "create", "disband", "listclans", "edit", "home",
                    "request", "reqest", "leave"));
            if (sender instanceof Player p && p.hasPermission("ui.command.clan.remove")) {
                out.add("remove");
            }
            return out.stream().filter(s -> s.startsWith(prefix)).toList();
        }

        String sub = args[1].toLowerCase(Locale.ROOT);
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();

        switch (sub) {
            case "remove" -> {
                if (args.length == 3) {
                    for (ClanDatabase.ClanData c : ClanDatabase.getAllClans()) {
                        String plain = MessageUtil.toPlainText(c.displayName());
                        if (plain.toLowerCase(Locale.ROOT).startsWith(last)) out.add(plain);
                    }
                }
            }
            case "edit" -> {
                if (args.length == 3) {
                    for (String s : List.of("add", "remove", "list", "home")) {
                        if (s.startsWith(last)) out.add(s);
                    }
                } else if (args.length == 4) {
                    String edit = args[2].toLowerCase(Locale.ROOT);
                    if (edit.equals("home")) {
                        for (String s : List.of("add", "set", "delhome")) {
                            if (s.startsWith(last)) out.add(s);
                        }
                    } else if (edit.equals("add")) {
                        for (String s : List.of("member", "moderator", "organizer")) {
                            if (s.startsWith(last)) out.add(s);
                        }
                    } else if (edit.equals("remove") || edit.equals("list")) {
                        // suggest members of the sender's clan
                        if (sender instanceof Player p) {
                            String key = ClanDatabase.getClanKeyByPlayer(p.getUniqueId().toString());
                            if (key != null) {
                                for (ClanDatabase.MemberData m : ClanDatabase.getMembers(key)) {
                                    if (m.playerName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(m.playerName());
                                }
                            }
                        }
                    }
                } else if (args.length == 5) {
                    String edit = args[2].toLowerCase(Locale.ROOT);
                    if (edit.equals("add")) {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (p.getName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(p.getName());
                        }
                    }
                }
            }
            case "request", "reqest" -> {
                if (args.length == 3) {
                    if (args[2].equalsIgnoreCase("accept") || args[2].equalsIgnoreCase("decline")) {
                        // suggest requester names from the sender's clan
                        if (sender instanceof Player p) {
                            String key = ClanDatabase.getClanKeyByPlayer(p.getUniqueId().toString());
                            if (key != null) {
                                for (ClanDatabase.RequestData r : ClanDatabase.getRequests(key)) {
                                    if (r.playerName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(r.playerName());
                                }
                            }
                        }
                    } else {
                        for (ClanDatabase.ClanData c : ClanDatabase.getAllClans()) {
                            String plain = MessageUtil.toPlainText(c.displayName());
                            if (plain.toLowerCase(Locale.ROOT).startsWith(last)) out.add(plain);
                        }
                    }
                } else if (args.length == 4) {
                    for (String s : List.of("accept", "decline")) {
                        if (s.startsWith(last)) out.add(s);
                    }
                }
            }
            default -> {}
        }
        return out;
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private static final java.util.Map<UUID, Long> clanTpCooldowns = new java.util.HashMap<>();

    private void sendUsage(Player player) {
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        player.sendMessage(MessageUtil.parse("<gold>  ✦ </gold><white>Clan Commands</white>"));
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        player.sendMessage(MessageUtil.parse("<gray>┌─ </gray><yellow>/ui clan create <name></yellow>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan disband</yellow> <gray>— disband your clan</gray>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan listclans</yellow>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan edit add <nick> <role></yellow>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan edit remove <nick></yellow>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan edit list</yellow>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan edit home <add|set|delhome></yellow>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan home</yellow>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan request <clan></yellow>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan request accept|decline <nick></yellow>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan leave</yellow>"));
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
    }

    private void sendConfirmButton(Player player, String command, String label) {
        player.sendMessage(MessageUtil.parse("<dark_gray>┃     <dark_green>[<green>✔ " + label + "<dark_green>]</dark_green>")
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(command))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                        MessageUtil.parse("<green>" + label + "</green>"))));
    }

    /** Strips MiniMessage formatting and lowercases — the clan lookup key. */
    private static String normalizeKey(String raw) {
        return MessageUtil.toPlainText(raw).trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isOrganizer(String role) {
        return ClanDatabase.ROLE_ORGANIZER.equals(role);
    }

    /** Moderator or organizer can manage members and the home; only organizer can disband. */
    private static boolean canManage(String role) {
        return isOrganizer(role) || ClanDatabase.ROLE_MODERATOR.equals(role);
    }

    private static String[] slice(String[] args, int from) {
        if (from >= args.length) return new String[0];
        String[] out = new String[args.length - from];
        System.arraycopy(args, from, out, 0, out.length);
        return out;
    }

    private static int cfgInt(String path, int def) {
        return Main.getInstance().getConfig().getInt(path, def);
    }

    private static String cfgString(String path, String def) {
        return Main.getInstance().getConfig().getString(path, def);
    }
}
