package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.command.SubCommand;
import com.ultimateimprovments.command.clan.ClanDatabase;
import com.ultimateimprovments.command.clan.ClanManager;
import com.ultimateimprovments.command.clan.ClanRoles;
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
import java.util.Map;
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
 *   <li>{@code leave [-confirm]} — leave your clan (leader cannot leave — transfer or disband first)</li>
 *   <li>{@code info} — clan info (name, description, owner, members, home)</li>
 *   <li>{@code online} — who is online right now</li>
 *   <li>{@code role set <nick> <role>|role remove <nick>} — manage roles (organizer+/leader)</li>
 *   <li>{@code transfer <nick>} — pass leadership (leader, with confirmation)</li>
 *   <li>{@code rename <name>} — rename the clan (leader)</li>
 *   <li>{@code description <text>} — set/clear the clan description (leader)</li>
 *   <li>{@code settings [key value]} — view/change clan settings (leader)</li>
 * </ul>
 *
 * <p>Role hierarchy: member → moderator → organizer → leader (see {@link ClanRoles}).</p>
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
            case "info" -> cmdInfo(player, rest);
            case "online" -> cmdOnline(player, rest);
            case "role" -> cmdRole(player, rest);
            case "transfer" -> cmdTransfer(player, rest);
            case "rename" -> cmdRename(player, rest);
            case "description" -> cmdDescription(player, rest);
            case "settings" -> cmdSettings(player, rest);
            case "depinvite" -> cmdDepInvite(player, rest);
            case "depaccept" -> cmdDepAccept(player, rest);
            case "depdecline" -> cmdDepDecline(player, rest);
            case "depdisband" -> cmdDepDisband(player, rest);
            case "depremove" -> cmdDepRemove(player, rest);
            case "depinfo" -> cmdDepInfo(player, rest);
            case "depstatus" -> cmdDepStatus(player, rest);
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
        if (!ClanRoles.isLeader(role)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Only the clan leader can disband the clan.</white>"));
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
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan edit <add|remove|list|home|selfpvp></yellow>"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] rest = slice(args, 1);
        return switch (sub) {
            case "add" -> cmdEditAdd(player, rest);
            case "remove" -> cmdEditRemove(player, rest);
            case "list" -> cmdEditList(player, rest);
            case "home" -> cmdEditHome(player, rest);
            case "selfpvp" -> cmdEditSelfPvp(player, rest);
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
        if (!ClanRoles.hasRole(myRole, ClanRoles.W_ORGANIZER)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You need at least the organizer role to add players.</white>"));
            return true;
        }

        String role = args[1].toLowerCase(Locale.ROOT);
        if (!List.of(ClanRoles.ROLE_MEMBER, ClanRoles.ROLE_MODERATOR, ClanRoles.ROLE_ORGANIZER).contains(role)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Invalid role. Choose: </white><yellow>member, moderator, organizer</yellow>"));
            return true;
        }
        // You can only grant a role strictly below your own.
        if (!ClanRoles.canGrantRole(myRole, role)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You cannot assign the </white><yellow>" + role
                    + "</yellow><white> role — only roles below your own.</white>"));
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
        // Member limit.
        String maxStr = ClanDatabase.getClanSettings(key).get("max_members");
        if (maxStr != null) {
            try {
                int max = Integer.parseInt(maxStr);
                if (ClanDatabase.countMembers(key) >= max) {
                    player.sendMessage(MessageUtil.parse("<red>✖ <white>The clan is full (max </white><yellow>" + max
                            + "</yellow><white> members).</white>"));
                    return true;
                }
            } catch (NumberFormatException ignored) {}
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
        if (!ClanRoles.hasRole(myRole, ClanRoles.W_MODERATOR)) {
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
        // Cannot kick the leader or anyone at/above your own level.
        if (!ClanRoles.canKick(myRole, targetRole)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You cannot remove a player with a role at or above your own.</white>"));
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
                case ClanDatabase.ROLE_LEADER -> "<dark_red>";
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
    // EDIT SELF-PVP
    // ============================================================

    private boolean cmdEditSelfPvp(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan edit selfpvp <on|off></yellow>"));
            return true;
        }
        UUID uuid = player.getUniqueId();
        String key = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (key == null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>"));
            return true;
        }
        String myRole = ClanDatabase.getRole(key, uuid.toString());
        if (!ClanRoles.hasRole(myRole, ClanRoles.W_ORGANIZER)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You need at least the organizer role to change clan settings.</white>"));
            return true;
        }
        String v = args[0].toLowerCase(Locale.ROOT);
        if (!v.equals("on") && !v.equals("off")) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>selfpvp must be </white><yellow>on</yellow><white> or </white><yellow>off</yellow><white>.</white>"));
            return true;
        }
        if (ClanDatabase.setClanSetting(key, "selfpvp", v)) {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Friendly fire is now </white><yellow>" + v
                    + "</yellow><white>.</white>" + (v.equals("on")
                    ? " <gray>Clan members cannot attack each other.</gray>"
                    : " <gray>Clan members can attack each other.</gray>")));
        } else {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed to change the setting.</white>"));
        }
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
        if (!ClanRoles.hasRole(myRole, ClanRoles.W_ORGANIZER)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You need at least the organizer role to set a clan home.</white>"));
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
        if (!ClanRoles.hasRole(myRole, ClanRoles.W_ORGANIZER)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You need at least the organizer role to set a clan home.</white>"));
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
        if (!ClanRoles.hasRole(myRole, ClanRoles.W_ORGANIZER)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You need at least the organizer role to delete the clan home.</white>"));
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
        // Clan settings: join policy and member limit.
        Map<String, String> settings = ClanDatabase.getClanSettings(key);
        if (settings.getOrDefault("join_policy", "open").equalsIgnoreCase("closed")) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>This clan does not accept join requests.</white>"));
            return true;
        }
        String maxStr = settings.get("max_members");
        if (maxStr != null) {
            try {
                int max = Integer.parseInt(maxStr);
                if (ClanDatabase.countMembers(key) >= max) {
                    player.sendMessage(MessageUtil.parse("<red>✖ <white>The clan is full (max </white><yellow>" + max
                            + "</yellow><white> members).</white>"));
                    return true;
                }
            } catch (NumberFormatException ignored) {}
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

        // Notify moderators+ online
        for (ClanDatabase.MemberData m : ClanDatabase.getMembers(key)) {
            if (ClanRoles.hasRole(m.role(), ClanRoles.W_MODERATOR)) {
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
        if (!ClanRoles.hasRole(myRole, ClanRoles.W_MODERATOR)) {
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
            // Member limit.
            String maxStr = ClanDatabase.getClanSettings(key).get("max_members");
            if (maxStr != null) {
                try {
                    int max = Integer.parseInt(maxStr);
                    if (ClanDatabase.countMembers(key) >= max) {
                        player.sendMessage(MessageUtil.parse("<red>✖ <white>The clan is full (max </white><yellow>" + max
                                + "</yellow><white> members).</white>"));
                        return true;
                    }
                } catch (NumberFormatException ignored) {}
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
        String role = ClanDatabase.getRole(key, uuid.toString());
        if (ClanRoles.isLeader(role)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>As the clan leader you cannot leave.</white>"
                    + " <gray>Use </gray><yellow>/ui clan transfer <nick></yellow><gray> to pass leadership or </gray>"
                    + "<yellow>/ui clan disband</yellow><gray> to disband the clan.</gray>"));
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
    // INFO
    // ============================================================

    private boolean cmdInfo(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        String key = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (key == null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>"));
            return true;
        }
        ClanDatabase.ClanData clan = ClanDatabase.getClan(key);
        if (clan == null) return true;
        String ownerName = ClanDatabase.getMemberName(key, clan.ownerUuid());
        int members = ClanDatabase.countMembers(key);

        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        player.sendMessage(MessageUtil.parse("<gold>  ✦ </gold><white>Clan info </white><yellow>" + clan.displayName() + "</yellow>"));
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        player.sendMessage(MessageUtil.parse("<gray>Owner: </gray><white>" + (ownerName != null ? ownerName : "unknown") + "</white>"));
        player.sendMessage(MessageUtil.parse("<gray>Members: </gray><white>" + members + "</white>"));
        String desc = clan.description();
        if (desc != null && !desc.isBlank()) {
            player.sendMessage(MessageUtil.parse("<gray>Description: </gray><white>" + desc + "</white>"));
        }
        if (clan.hasHome()) {
            player.sendMessage(MessageUtil.parse("<gray>Home: </gray><white>" + clan.homeWorld() + " "
                    + Math.round(clan.homeX()) + " " + Math.round(clan.homeY()) + " " + Math.round(clan.homeZ()) + "</white>"));
        } else {
            player.sendMessage(MessageUtil.parse("<gray>Home: </gray><yellow>not set</yellow>"));
        }
        // Dependency info
        String mainKey2 = ClanDatabase.getMainClan(key);
        String depKey2 = ClanDatabase.getDependentClan(key);
        if (mainKey2 != null) {
            ClanDatabase.ClanData mc2 = ClanDatabase.getClan(mainKey2);
            String mn2 = mc2 != null ? MessageUtil.toPlainText(mc2.displayName()) : mainKey2;
            player.sendMessage(MessageUtil.parse("<gray>--- </gray><white>Alliance</white>"));
            player.sendMessage(MessageUtil.parse("<white>Main: </white><yellow>" + mn2 + "</yellow>"));
            player.sendMessage(MessageUtil.parse("<white>Dep: </white><yellow>" + clan.displayName() + "</yellow> <dark_gray>[N]</dark_gray>"));
        } else if (depKey2 != null) {
            ClanDatabase.ClanData dc2 = ClanDatabase.getClan(depKey2);
            String dn2 = dc2 != null ? MessageUtil.toPlainText(dc2.displayName()) : depKey2;
            player.sendMessage(MessageUtil.parse("<gray>--- </gray><white>Alliance</white>"));
            player.sendMessage(MessageUtil.parse("<white>Main: </white><yellow>" + clan.displayName() + "</yellow>"));
            player.sendMessage(MessageUtil.parse("<white>Dep: </white><yellow>" + dn2 + "</yellow> <dark_gray>[N]</dark_gray>"));
        }
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        return true;
    }

    // ============================================================
    // ONLINE
    // ============================================================

    private boolean cmdOnline(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        String key = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (key == null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>"));
            return true;
        }
        List<ClanDatabase.MemberData> members = ClanDatabase.getMembers(key);
        List<String> online = new ArrayList<>();
        for (ClanDatabase.MemberData m : members) {
            Player p = Bukkit.getPlayerExact(m.playerName());
            if (p != null && p.isOnline()) online.add(p.getName());
        }
        player.sendMessage(MessageUtil.parse("<gold>  ✦ </gold><white>Online </white><gray>(" + online.size() + "/" + members.size() + ")</gray>"));
        player.sendMessage(MessageUtil.parse("<white>" + (online.isEmpty()
                ? "<gray>Nobody is online.</gray>"
                : String.join("<gray>, </gray>", online)) + "</white>"));
        return true;
    }

    // ============================================================
    // ROLE
    // ============================================================

    private boolean cmdRole(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan role set <nick> <role>"
                    + " | /ui clan role remove <nick></yellow>"));
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "set" -> cmdRoleSet(player, slice(args, 1));
            case "remove" -> cmdRoleRemove(player, slice(args, 1));
            default -> {
                player.sendMessage(MessageUtil.parse("<red>✖ <white>Unknown role action: </white><yellow>" + action + "</yellow>"));
                yield true;
            }
        };
    }

    private boolean cmdRoleSet(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan role set <nick> <role></yellow>"));
            return true;
        }
        UUID uuid = player.getUniqueId();
        String key = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (key == null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>"));
            return true;
        }
        String myRole = ClanDatabase.getRole(key, uuid.toString());
        if (!ClanRoles.hasRole(myRole, ClanRoles.W_ORGANIZER)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You need at least the organizer role to manage roles.</white>"));
            return true;
        }
        String newRole = args[1].toLowerCase(Locale.ROOT);
        if (!ClanRoles.grantableRoles(myRole).contains(newRole)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You can only assign: </white><yellow>"
                    + String.join(", ", ClanRoles.grantableRoles(myRole)) + "</yellow>"));
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
        if (ClanRoles.isLeader(targetRole)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You cannot change the leader's role.</white>"));
            return true;
        }
        if (ClanDatabase.setRole(key, targetUuid, newRole)) {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Player </white><yellow>" + target.getName()
                    + "</yellow> <white>is now </white><yellow>" + newRole + "</yellow><white>.</white>"));
        } else {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed to change the role.</white>"));
        }
        return true;
    }

    private boolean cmdRoleRemove(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan role remove <nick></yellow>"));
            return true;
        }
        UUID uuid = player.getUniqueId();
        String key = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (key == null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>"));
            return true;
        }
        String myRole = ClanDatabase.getRole(key, uuid.toString());
        if (!ClanRoles.hasRole(myRole, ClanRoles.W_ORGANIZER)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You need at least the organizer role to manage roles.</white>"));
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
        if (ClanRoles.ROLE_MEMBER.equals(targetRole)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Player </white><yellow>" + target.getName()
                    + "</yellow><white> already has the base member role.</white>"));
            return true;
        }
        if (!ClanRoles.canKick(myRole, targetRole)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You cannot remove a role at or above your own.</white>"));
            return true;
        }
        if (ClanDatabase.setRole(key, targetUuid, ClanRoles.ROLE_MEMBER)) {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Player </white><yellow>" + target.getName()
                    + "</yellow> <white>is now </white><yellow>member</yellow><white>.</white>"));
        } else {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed to change the role.</white>"));
        }
        return true;
    }

    // ============================================================
    // TRANSFER
    // ============================================================

    private boolean cmdTransfer(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan transfer <nick></yellow>"));
            return true;
        }
        UUID uuid = player.getUniqueId();
        String key = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (key == null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>"));
            return true;
        }
        String myRole = ClanDatabase.getRole(key, uuid.toString());
        if (!ClanRoles.isLeader(myRole)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Only the clan leader can transfer leadership.</white>"));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String targetUuid = target.getUniqueId().toString();
        if (targetUuid.equals(uuid.toString())) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You are already the leader.</white>"));
            return true;
        }
        String targetRole = ClanDatabase.getRole(key, targetUuid);
        if (targetRole == null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Player </white><yellow>" + args[0]
                    + "</yellow><white> is not in your clan.</white>"));
            return true;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("-confirm")) {
            if (!ClanManager.consumeConfirm(player, ClanManager.CONFIRM_TRANSFER)) {
                player.sendMessage(MessageUtil.parse("<red>✖ <white>Confirmation expired. Run </white>"
                        + "<yellow>/ui clan transfer " + args[0] + "</yellow><white> again.</white>"));
                return true;
            }
            if (ClanDatabase.transferLeader(key, targetUuid, target.getName(), uuid.toString())) {
                player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Leadership transferred to </white><yellow>"
                        + target.getName() + "</yellow><white>.</white>"));
                Player online = Bukkit.getPlayer(target.getUniqueId());
                if (online != null) {
                    online.sendMessage(MessageUtil.parse("<green>✔</green> <white>You are now the leader of the clan!</white>"));
                }
            } else {
                player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed to transfer leadership.</white>"));
            }
            return true;
        }
        ClanManager.armConfirm(player, ClanManager.CONFIRM_TRANSFER);
        player.sendMessage(MessageUtil.parse("<red>⚠ <white>Transfer leadership to </white><yellow>" + target.getName()
                + "</yellow><white>? You will become an organizer.</white>"));
        sendConfirmButton(player, "/ui clan transfer " + args[0] + " -confirm", "Transfer leadership");
        return true;
    }

    // ============================================================
    // RENAME
    // ============================================================

    private boolean cmdRename(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan rename <name></yellow>"));
            return true;
        }
        UUID uuid = player.getUniqueId();
        String key = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (key == null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>"));
            return true;
        }
        String myRole = ClanDatabase.getRole(key, uuid.toString());
        if (!ClanRoles.isLeader(myRole)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Only the clan leader can rename the clan.</white>"));
            return true;
        }
        String newName = String.join(" ", args);
        String newKey = normalizeKey(newName);
        int min = cfgInt("clan.name_min_length", 2);
        int max = cfgInt("clan.name_max_length", 32);
        if (newKey.length() < min || newKey.length() > max) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Clan name must be </white><yellow>" + min
                    + "-" + max + "</yellow><white> characters long.</white>"));
            return true;
        }
        if (ClanDatabase.renameClan(key, newName)) {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Clan renamed to </white><yellow>" + newName + "</yellow><white>.</white>"));
        } else {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed to rename the clan.</white>"));
        }
        return true;
    }

    // ============================================================
    // DESCRIPTION
    // ============================================================

    private boolean cmdDescription(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        String key = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (key == null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>"));
            return true;
        }
        String myRole = ClanDatabase.getRole(key, uuid.toString());
        if (!ClanRoles.isLeader(myRole)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Only the clan leader can change the description.</white>"));
            return true;
        }
        String text = args.length >= 1 ? String.join(" ", args) : "";
        if (ClanDatabase.setDescription(key, text)) {
            player.sendMessage(MessageUtil.parse(text.isEmpty()
                    ? "<green>✔</green> <white>Clan description cleared.</white>"
                    : "<green>✔</green> <white>Clan description set.</white>"));
        } else {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed to change the description.</white>"));
        }
        return true;
    }

    // ============================================================
    // SETTINGS
    // ============================================================

    private boolean cmdSettings(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        String key = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (key == null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>"));
            return true;
        }
        String myRole = ClanDatabase.getRole(key, uuid.toString());
        if (!ClanRoles.isLeader(myRole)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Only the clan leader can change clan settings.</white>"));
            return true;
        }
        // View
        if (args.length < 2) {
            Map<String, String> settings = ClanDatabase.getClanSettings(key);
            player.sendMessage(MessageUtil.parse("<gold>  ✦ </gold><white>Clan settings</white>"));
            if (settings.isEmpty()) {
                player.sendMessage(MessageUtil.parse("<gray>No custom settings.</gray>"));
            } else {
                for (Map.Entry<String, String> e : settings.entrySet()) {
                    player.sendMessage(MessageUtil.parse("<gray>┌─ </gray><yellow>" + e.getKey()
                            + "</yellow><gray>: </gray><white>" + e.getValue() + "</white>"));
                }
            }
            player.sendMessage(MessageUtil.parse("<gray>Available keys: </gray><white>join_policy (open|closed), max_members (N), selfpvp (on|off)</white>"));
            return true;
        }
        String k = args[0].toLowerCase(Locale.ROOT);
        String v = args[1];
        switch (k) {
            case "join_policy" -> {
                if (!v.equalsIgnoreCase("open") && !v.equalsIgnoreCase("closed")) {
                    player.sendMessage(MessageUtil.parse("<red>✖ <white>join_policy must be </white><yellow>open</yellow>"
                            + "<white> or </white><yellow>closed</yellow><white>.</white>"));
                    return true;
                }
                v = v.toLowerCase(Locale.ROOT);
            }
            case "max_members" -> {
                try {
                    if (Integer.parseInt(v) < 2) {
                        player.sendMessage(MessageUtil.parse("<red>✖ <white>max_members must be at least 2.</white>"));
                        return true;
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(MessageUtil.parse("<red>✖ <white>max_members must be a number.</white>"));
                    return true;
                }
            }
            case "selfpvp" -> {
                if (!v.equalsIgnoreCase("on") && !v.equalsIgnoreCase("off")) {
                    player.sendMessage(MessageUtil.parse("<red>✖ <white>selfpvp must be </white><yellow>on</yellow>"
                            + "<white> or </white><yellow>off</yellow><white>.</white>"));
                    return true;
                }
                v = v.toLowerCase(Locale.ROOT);
            }
            default -> {
                player.sendMessage(MessageUtil.parse("<red>✖ <white>Unknown setting: </white><yellow>" + k
                        + "</yellow><white>. Available: </white><yellow>join_policy, max_members</yellow>"));
                return true;
            }
        }
        if (ClanDatabase.setClanSetting(key, k, v)) {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Setting </white><yellow>" + k
                    + "</yellow> <white>set to </white><yellow>" + v + "</yellow><white>.</white>"));
        } else {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed to change the setting.</white>"));
        }
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
                    "request", "reqest", "leave", "info", "online", "role",
                    "transfer", "rename", "description", "settings",
                    "depinvite", "depaccept", "depdecline", "depdisband",
                    "depremove", "depinfo", "depstatus"));
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
                    for (String s : List.of("add", "remove", "list", "home", "selfpvp")) {
                        if (s.startsWith(last)) out.add(s);
                    }
                } else if (args.length == 4) {
                    String edit = args[2].toLowerCase(Locale.ROOT);
                    if (edit.equals("home")) {
                        for (String s : List.of("add", "set", "delhome")) {
                            if (s.startsWith(last)) out.add(s);
                        }
                    } else if (edit.equals("selfpvp")) {
                        for (String s : List.of("on", "off")) {
                            if (s.startsWith(last)) out.add(s);
                        }
                    } else if (edit.equals("add")) {
                        // Position 4: online player names
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (p.getName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(p.getName());
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
                        // Position 5: roles
                        for (String s : List.of("member", "moderator", "organizer")) {
                            if (s.startsWith(last)) out.add(s);
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
            case "role" -> {
                if (args.length == 3) {
                    for (String s : List.of("set", "remove")) {
                        if (s.startsWith(last)) out.add(s);
                    }
                } else if (args.length == 4 && args[2].equalsIgnoreCase("set")) {
                    // roles the sender may grant
                    if (sender instanceof Player p) {
                        String key = ClanDatabase.getClanKeyByPlayer(p.getUniqueId().toString());
                        if (key != null) {
                            String myRole = ClanDatabase.getRole(key, p.getUniqueId().toString());
                            for (String s : ClanRoles.grantableRoles(myRole)) {
                                if (s.startsWith(last)) out.add(s);
                            }
                        }
                    }
                } else if (args.length == 4 && args[2].equalsIgnoreCase("remove")) {
                    suggestMemberNames(sender, out, last);
                } else if (args.length == 5 && args[2].equalsIgnoreCase("set")) {
                    suggestMemberNames(sender, out, last);
                }
            }
            case "transfer" -> {
                if (args.length == 3) {
                    suggestMemberNames(sender, out, last);
                }
            }
            case "settings" -> {
                if (args.length == 3) {
                    for (String s : List.of("join_policy", "max_members")) {
                        if (s.startsWith(last)) out.add(s);
                    }
                } else if (args.length == 4 && args[2].equalsIgnoreCase("join_policy")) {
                    for (String s : List.of("open", "closed")) {
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


    // ============================================================
    // DEPENDENT CLANS
    // ============================================================

    private boolean cmdDepInvite(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(MessageUtil.parse("<red>\u2716 <white>Usage: </white><yellow>/ui clan depinvite <clan></yellow>"));
            return true;
        }
        UUID uuid = player.getUniqueId();
        String myKey = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (myKey == null) { player.sendMessage(MessageUtil.parse("<red>\u2716 <white>You are not in a clan.</white>")); return true; }
        String myRole = ClanDatabase.getRole(myKey, uuid.toString());
        if (!ClanRoles.isLeader(myRole)) { player.sendMessage(MessageUtil.parse("<red>\u2716 <white>Only the clan leader can send dependency invites.</white>")); return true; }
        if (ClanDatabase.getDependentClan(myKey) != null) { player.sendMessage(MessageUtil.parse("<red>\u2716 <white>Your clan already has a dependent clan.</white>")); return true; }
        if (!ClanManager.canDepInvite(myKey)) { player.sendMessage(MessageUtil.parse("<red>\u2716 <white>Cooldown. Try again later.</white>")); return true; }
        String targetName = String.join(" ", args);
        String targetKey = normalizeKey(targetName);
        if (!ClanDatabase.clanExists(targetKey)) { player.sendMessage(MessageUtil.parse("<red>\u2716 <white>Clan </white><yellow>" + targetName + "</yellow><white> not found.</white>")); return true; }
        if (targetKey.equals(myKey)) { player.sendMessage(MessageUtil.parse("<red>\u2716 <white>You cannot invite your own clan.</white>")); return true; }
        if (ClanDatabase.isDependent(targetKey)) { player.sendMessage(MessageUtil.parse("<red>\u2716 <white>This clan is already dependent on another clan.</white>")); return true; }
        if (ClanDatabase.getMainClan(targetKey) != null) { player.sendMessage(MessageUtil.parse("<red>\u2716 <white>This clan already has a main clan.</white>")); return true; }
        ClanDatabase.addDepRequest(myKey, targetKey);
        ClanManager.markDepInvite(myKey);
        ClanDatabase.ClanData myClan = ClanDatabase.getClan(myKey);
        ClanDatabase.ClanData targetClan = ClanDatabase.getClan(targetKey);
        String myName = myClan != null ? MessageUtil.toPlainText(myClan.displayName()) : myKey;
        String tgtName = targetClan != null ? MessageUtil.toPlainText(targetClan.displayName()) : targetKey;
        player.sendMessage(MessageUtil.parse("<green>\u2714</green> <white>Dependency invite sent to </white><yellow>" + tgtName + "</yellow><white>.</white>"));
        if (targetClan != null) {
            Player tl = Bukkit.getPlayer(UUID.fromString(targetClan.ownerUuid()));
            if (tl != null) {
                tl.sendMessage(MessageUtil.parse("<gold>\u2726</gold> <white>Clan </white><yellow>" + myName + "</yellow><white> invites your clan to become dependent.</white>"));
                tl.sendMessage(MessageUtil.parse("<gray>Use </gray><yellow>/ui clan depaccept</yellow><gray> or </gray><yellow>/ui clan depdecline</yellow>"));
            }
        }
        return true;
    }

    private boolean cmdDepAccept(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        String myKey = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (myKey == null) { player.sendMessage(MessageUtil.parse("<red>\u2716 <white>You are not in a clan.</white>")); return true; }
        String myRole = ClanDatabase.getRole(myKey, uuid.toString());
        if (!ClanRoles.isLeader(myRole)) { player.sendMessage(MessageUtil.parse("<red>\u2716 <white>Only the clan leader can accept dependency invites.</white>")); return true; }
        var request = ClanDatabase.getDepRequest(myKey);
        if (request == null) { player.sendMessage(MessageUtil.parse("<red>\u2716 <white>No pending dependency invite.</white>")); return true; }
        if (ClanDatabase.getDependentClan(request.fromClan()) != null) { player.sendMessage(MessageUtil.parse("<red>\u2716 <white>The inviting clan already has a dependent.</white>")); ClanDatabase.removeDepRequest(request.fromClan(), myKey); return true; }
        if (ClanDatabase.setDependency(request.fromClan(), myKey)) {
            ClanDatabase.removeDepRequest(request.fromClan(), myKey);
            ClanDatabase.ClanData fromClan = ClanDatabase.getClan(request.fromClan());
            String fromName = fromClan != null ? MessageUtil.toPlainText(fromClan.displayName()) : request.fromClan();
            ClanDatabase.ClanData myClan = ClanDatabase.getClan(myKey);
            String myName = myClan != null ? MessageUtil.toPlainText(myClan.displayName()) : myKey;
            player.sendMessage(MessageUtil.parse("<green>\u2714</green> <white>Your clan is now dependent on </white><yellow>" + fromName + "</yellow><white>!</white>"));
            if (fromClan != null) {
                Player ml = Bukkit.getPlayer(UUID.fromString(fromClan.ownerUuid()));
                if (ml != null) ml.sendMessage(MessageUtil.parse("<green>\u2714</green> <white>Clan </white><yellow>" + myName + "</yellow><white> accepted the dependency invite!</white>"));
            }
        } else { player.sendMessage(MessageUtil.parse("<red>\u2716 <white>Failed to create dependency.</white>")); }
        return true;
    }

    private boolean cmdDepDecline(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        String myKey = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (myKey == null) { player.sendMessage(MessageUtil.parse("<red>\u2716 <white>You are not in a clan.</white>")); return true; }
        String myRole = ClanDatabase.getRole(myKey, uuid.toString());
        if (!ClanRoles.isLeader(myRole)) { player.sendMessage(MessageUtil.parse("<red>\u2716 <white>Only the clan leader can decline dependency invites.</white>")); return true; }
        var request = ClanDatabase.getDepRequest(myKey);
        if (request == null) { player.sendMessage(MessageUtil.parse("<red>\u2716 <white>No pending dependency invite.</white>")); return true; }
        ClanDatabase.removeDepRequest(request.fromClan(), myKey);
        player.sendMessage(MessageUtil.parse("<yellow>\u2716 Dependency invite declined.</yellow>"));
        ClanDatabase.ClanData fromClan = ClanDatabase.getClan(request.fromClan());
        if (fromClan != null) {
            Player ml = Bukkit.getPlayer(UUID.fromString(fromClan.ownerUuid()));
            if (ml != null) {
                String myName = MessageUtil.toPlainText(ClanDatabase.getClan(myKey).displayName());
                ml.sendMessage(MessageUtil.parse("<red>\u2716</red> <white>Clan </white><yellow>" + myName + "</yellow><white> declined the dependency invite.</white>"));
            }
        }
        return true;
    }

    private boolean cmdDepDisband(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        String myKey = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (myKey == null) { player.sendMessage(MessageUtil.parse("<red>\u2716 <white>You are not in a clan.</white>")); return true; }
        String myRole = ClanDatabase.getRole(myKey, uuid.toString());
        if (!ClanRoles.isLeader(myRole)) { player.sendMessage(MessageUtil.parse("<red>\u2716 <white>Only the main clan leader can disband a dependent clan.</white>")); return true; }
        String depKey = ClanDatabase.getDependentClan(myKey);
        if (depKey == null) { player.sendMessage(MessageUtil.parse("<red>\u2716 <white>Your clan does not have a dependent clan.</white>")); return true; }
        if (args.length >= 1 && args[0].equalsIgnoreCase("-confirm")) {
            if (!ClanManager.consumeConfirm(player, ClanManager.CONFIRM_DEP_DISBAND)) { player.sendMessage(MessageUtil.parse("<red>\u2716 <white>Confirmation expired.</white>")); return true; }
            ClanDatabase.ClanData depClan = ClanDatabase.getClan(depKey);
            String depName = depClan != null ? MessageUtil.toPlainText(depClan.displayName()) : depKey;
            if (ClanDatabase.removeDependency(myKey)) {
                player.sendMessage(MessageUtil.parse("<green>\u2714</green> <white>Dependent clan </white><yellow>" + depName + "</yellow><white> has been disbanded.</white>"));
                if (depClan != null) { Player dl = Bukkit.getPlayer(UUID.fromString(depClan.ownerUuid())); if (dl != null) { String mn = MessageUtil.toPlainText(ClanDatabase.getClan(myKey).displayName()); dl.sendMessage(MessageUtil.parse("<red>\u2716</red> <white>Your clan has been removed as dependent of </white><yellow>" + mn + "</yellow><white>.</white>")); } }
            } else { player.sendMessage(MessageUtil.parse("<red>\u2716 <white>Failed.</white>")); }
            return true;
        }
        ClanManager.armConfirm(player, ClanManager.CONFIRM_DEP_DISBAND);
        player.sendMessage(MessageUtil.parse("<red>\u26a0 <white>Are you sure? </white><red>This cannot be undone!</red>"));
        sendConfirmButton(player, "/ui clan depdisband -confirm", "Disband dependent clan");
        return true;
    }

    private boolean cmdDepRemove(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        String myKey = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (myKey == null) { player.sendMessage(MessageUtil.parse("<red>\u2716 <white>You are not in a clan.</white>")); return true; }
        String myRole = ClanDatabase.getRole(myKey, uuid.toString());
        if (!ClanRoles.isLeader(myRole)) { player.sendMessage(MessageUtil.parse("<red>\u2716 <white>Only the clan leader can break the dependency.</white>")); return true; }
        String mainKey = ClanDatabase.getMainClan(myKey);
        if (mainKey == null) { player.sendMessage(MessageUtil.parse("<red>\u2716 <white>Your clan is not dependent on any clan.</white>")); return true; }
        if (args.length >= 1 && args[0].equalsIgnoreCase("-confirm")) {
            if (!ClanManager.consumeConfirm(player, ClanManager.CONFIRM_DEP_DISBAND)) { player.sendMessage(MessageUtil.parse("<red>\u2716 <white>Confirmation expired.</white>")); return true; }
            ClanDatabase.ClanData mainClan = ClanDatabase.getClan(mainKey);
            String mainName = mainClan != null ? MessageUtil.toPlainText(mainClan.displayName()) : mainKey;
            if (ClanDatabase.removeDependency(mainKey)) {
                player.sendMessage(MessageUtil.parse("<green>\u2714</green> <white>Dependency removed from </white><yellow>" + mainName + "</yellow><white>.</white>"));
                if (mainClan != null) { Player ml = Bukkit.getPlayer(UUID.fromString(mainClan.ownerUuid())); if (ml != null) { String mn = MessageUtil.toPlainText(ClanDatabase.getClan(myKey).displayName()); ml.sendMessage(MessageUtil.parse("<red>\u2716</red> <white>Clan </white><yellow>" + mn + "</yellow><white> has broken the dependency.</white>")); } }
            } else { player.sendMessage(MessageUtil.parse("<red>\u2716 <white>Failed.</white>")); }
            return true;
        }
        ClanManager.armConfirm(player, ClanManager.CONFIRM_DEP_DISBAND);
        player.sendMessage(MessageUtil.parse("<red>\u26a0 <white>Are you sure?</white> <red>This cannot be undone!</red>"));
        sendConfirmButton(player, "/ui clan depremove -confirm", "Break dependency");
        return true;
    }

    private boolean cmdDepInfo(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        String myKey = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (myKey == null) { player.sendMessage(MessageUtil.parse("<red>\u2716 <white>You are not in a clan.</white>")); return true; }
        String mainKey = ClanDatabase.getMainClan(myKey);
        String depKey = ClanDatabase.getDependentClan(myKey);
        if (mainKey == null && depKey == null) { player.sendMessage(MessageUtil.parse("<gray>Your clan is not in a dependency relationship.</gray>")); return true; }
        player.sendMessage(MessageUtil.parse("<gold>\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550</gold>"));
        player.sendMessage(MessageUtil.parse("<gold>  \u2666 </gold><white>Dependency Info</white>"));
        player.sendMessage(MessageUtil.parse("<gold>\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550</gold>"));
        if (mainKey != null) {
            ClanDatabase.ClanData mc = ClanDatabase.getClan(mainKey);
            String mn = mc != null ? MessageUtil.toPlainText(mc.displayName()) : mainKey;
            ClanDatabase.ClanData ic = ClanDatabase.getClan(myKey);
            String in2 = ic != null ? MessageUtil.toPlainText(ic.displayName()) : myKey;
            player.sendMessage(MessageUtil.parse("<white>Main clan: </white><yellow>" + mn + "</yellow>"));
            player.sendMessage(MessageUtil.parse("<white>Dependent: </white><yellow>" + in2 + "</yellow> <dark_gray>[N]</dark_gray>"));
        } else {
            ClanDatabase.ClanData ic = ClanDatabase.getClan(myKey);
            String in2 = ic != null ? MessageUtil.toPlainText(ic.displayName()) : myKey;
            ClanDatabase.ClanData dc = ClanDatabase.getClan(depKey);
            String dn = dc != null ? MessageUtil.toPlainText(dc.displayName()) : depKey;
            player.sendMessage(MessageUtil.parse("<white>Main clan: </white><yellow>" + in2 + "</yellow>"));
            player.sendMessage(MessageUtil.parse("<white>Dependent: </white><yellow>" + dn + "</yellow> <dark_gray>[N]</dark_gray>"));
        }
        player.sendMessage(MessageUtil.parse("<gold>\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550</gold>"));
        return true;
    }

    private boolean cmdDepStatus(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        String myKey = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (myKey == null) { player.sendMessage(MessageUtil.parse("<red>\u2716 <white>You are not in a clan.</white>")); return true; }
        String mainKey = ClanDatabase.getMainClan(myKey);
        String depKey = ClanDatabase.getDependentClan(myKey);
        var pending = ClanDatabase.getDepRequest(myKey);
        if (mainKey != null) {
            ClanDatabase.ClanData mc = ClanDatabase.getClan(mainKey);
            String mn = mc != null ? MessageUtil.toPlainText(mc.displayName()) : mainKey;
            player.sendMessage(MessageUtil.parse("<gray>Status: </gray><yellow>dependent</yellow><gray> of </gray><yellow>" + mn + "</yellow>"));
        } else if (depKey != null) {
            ClanDatabase.ClanData dc = ClanDatabase.getClan(depKey);
            String dn = dc != null ? MessageUtil.toPlainText(dc.displayName()) : depKey;
            player.sendMessage(MessageUtil.parse("<gray>Status: </gray><yellow>main</yellow><gray> \u2014 dependent: </gray><yellow>" + dn + "</yellow>"));
        } else {
            player.sendMessage(MessageUtil.parse("<gray>Status: </gray><white>independent</white>"));
        }
        if (pending != null) {
            ClanDatabase.ClanData fc = ClanDatabase.getClan(pending.fromClan());
            String fn = fc != null ? MessageUtil.toPlainText(fc.displayName()) : pending.fromClan();
            player.sendMessage(MessageUtil.parse("<gray>Pending invite from: </gray><yellow>" + fn + "</yellow>"));
        }
        return true;
    }

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
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan info</yellow> <gray>— clan info</gray>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan online</yellow> <gray>— who is online</gray>"));
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        player.sendMessage(MessageUtil.parse("<gold>  ✦ </gold><white>Organizer+</white>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan role set <nick> <role></yellow> <gray>— manage roles</gray>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan role remove <nick></yellow> <gray>— demote to member</gray>"));
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        player.sendMessage(MessageUtil.parse("<gold>  ✦ </gold><white>Leader</white>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan transfer <nick></yellow> <gray>— pass leadership</gray>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan rename <name></yellow> <gray>— rename the clan</gray>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan description <text></yellow> <gray>— set/clear description</gray>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan settings [key value]</yellow> <gray>— clan settings</gray>"));
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

    /** Adds member names of the sender's clan to the tab-complete output. */
    private static void suggestMemberNames(CommandSender sender, List<String> out, String last) {
        if (!(sender instanceof Player p)) return;
        String key = ClanDatabase.getClanKeyByPlayer(p.getUniqueId().toString());
        if (key == null) return;
        for (ClanDatabase.MemberData m : ClanDatabase.getMembers(key)) {
            if (m.playerName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(m.playerName());
        }
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
