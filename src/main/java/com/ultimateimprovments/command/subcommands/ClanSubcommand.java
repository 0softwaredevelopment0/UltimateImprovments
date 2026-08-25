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
 * /ui clan — clan system (v2).
 *
 * <p>Subcommands:</p>
 * <ul>
 *   <li>{@code create <name>} — create a clan</li>
 *   <li>{@code list [page]} — list all clans (clickable names → listclans)</li>
 *   <li>{@code listclans <clan>} — detailed clan info</li>
 *   <li>{@code info} — your clan info</li>
 *   <li>{@code online} — who is online</li>
 *   <li>{@code home} — teleport to clan home</li>
 *   <li>{@code request <clan>} — apply to join</li>
 *   <li>{@code request accept|decline <nick>} — handle join requests</li>
 *   <li>{@code invite accept|decline} — handle clan invites</li>
 *   <li>{@code leave [-confirm]} — leave your clan</li>
 *   <li>{@code transfer <nick>} — pass leadership</li>
 *   <li>{@code edit <subcmd>} — edit your own clan</li>
 *   <li>{@code admedit <clan> <subcmd>} — admin edit any clan (ui.command.clan.admin)</li>
 *   <li>{@code depinvite <clan>} — invite clan to be dependent</li>
 *   <li>{@code depaccept|depdecline} — handle dep invites</li>
 *   <li>{@code depinfo|depstatus} — dependency info</li>
 *   <li>{@code depdisband} — disband dependent (→ confirm request)</li>
 *   <li>{@code depremove} — remove dependency (→ confirm request)</li>
 *   <li>{@code depredir <player>} — redirect dep player to main clan</li>
 *   <li>{@code depedit <subcmd>} — edit dependent clan (main organizer)</li>
 * </ul>
 *
 * <p>Role hierarchy: member → moderator → organizer → leader (see {@link ClanRoles}).</p>
 */
public final class ClanSubcommand implements SubCommand {

    private static final String PERMISSION = "ui.command.clan";
    private static final String PERMISSION_ADMIN = "ui.command.clan.admin";
    private static final int PER_PAGE = 10;

    @Override
    public String getName() { return "clan"; }

    @Override
    public List<String> getAliases() { return List.of("clans"); }

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
        if (args.length < 2) { sendUsage(player); return true; }

        String sub = args[1].toLowerCase(Locale.ROOT);
        String[] rest = slice(args, 2);

        return switch (sub) {
            case "create" -> cmdCreate(player, rest);
            case "list" -> cmdListClans(player, rest);
            case "listclans" -> cmdListClansDetail(player, rest);
            case "edit" -> cmdEdit(player, rest);
            case "home" -> cmdHome(player, rest);
            case "request", "reqest" -> cmdRequest(player, rest);
            case "invite" -> cmdInvite(player, rest);
            case "leave" -> cmdLeave(player, rest);
            case "info" -> cmdInfo(player, rest);
            case "online" -> cmdOnline(player, rest);
            case "transfer" -> cmdTransfer(player, rest);
            case "admedit" -> cmdAdmedit(player, rest);
            case "depinvite" -> cmdDepInvite(player, rest);
            case "depaccept" -> cmdDepAccept(player, rest);
            case "depdecline" -> cmdDepDecline(player, rest);
            case "depdisband" -> cmdDepDisband(player, rest);
            case "depremove" -> cmdDepRemove(player, rest);
            case "depinfo" -> cmdDepInfo(player, rest);
            case "depstatus" -> cmdDepStatus(player, rest);
            case "depredir" -> cmdDepRedir(player, rest);
            case "depedit" -> cmdDepEdit(player, rest);
            default -> { sendUsage(player); yield true; }
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
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Clan name must be </white><yellow>" + min + "-" + max + "</yellow><white> characters long.</white>"));
            return true;
        }
        if (ClanDatabase.clanExists(key)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>A clan with this name already exists.</white>"));
            return true;
        }
        if (ClanDatabase.createClan(key, rawName, uuid.toString(), player.getName())) {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Clan </white><yellow>" + rawName + "</yellow> <white>created!</white>"));
        } else {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed to create the clan.</white>"));
        }
        return true;
    }

    // ============================================================
    // LIST CLANS (clickable)
    // ============================================================

    private boolean cmdListClans(Player player, String[] args) {
        int page = 1;
        if (args.length >= 1) { try { page = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {} }
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
                String depMark = ClanDatabase.isDependent(c.key()) ? " <dark_gray>[N]</dark_gray>" : "";
                String plain = MessageUtil.toPlainText(c.displayName());
                // Clickable clan name
                net.kyori.adventure.text.Component line = MessageUtil.parse(
                        "<gray>┌─ </gray><yellow>" + (i + 1) + ".</yellow> ");
                line = line.append(net.kyori.adventure.text.Component.text(plain)
                        .color(net.kyori.adventure.text.format.NamedTextColor.WHITE)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(
                                "/ui clan listclans " + plain))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                MessageUtil.parse("<gray>Click for details</gray>"))));
                line = line.append(MessageUtil.parse(depMark));
                player.sendMessage(line);
            }
        }
        // Footer
        net.kyori.adventure.text.Component footer = MessageUtil.parse(
                "<dark_gray>┃  <gray>Page <yellow>" + page + "<gray>/" + totalPages + "   ");
        if (page > 1) {
            footer = footer.append(net.kyori.adventure.text.Component.text("[<]")
                    .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW)
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/ui clan list " + (page - 1)))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(MessageUtil.parse("<gray>Previous page"))));
        } else {
            footer = footer.append(MessageUtil.parse("<dark_gray>[<]"));
        }
        footer = footer.append(MessageUtil.parse("  "));
        if (page < totalPages) {
            footer = footer.append(net.kyori.adventure.text.Component.text("[>]")
                    .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW)
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/ui clan list " + (page + 1)))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(MessageUtil.parse("<gray>Next page"))));
        } else {
            footer = footer.append(MessageUtil.parse("<dark_gray>[>]"));
        }
        player.sendMessage(footer);
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        return true;
    }

    // ============================================================
    // LIST CLANS DETAIL
    // ============================================================

    private boolean cmdListClansDetail(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan listclans <clan></yellow>"));
            return true;
        }
        String key = normalizeKey(String.join(" ", args));
        if (!ClanDatabase.clanExists(key)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Clan not found.</white>"));
            return true;
        }
        ClanDatabase.ClanData clan = ClanDatabase.getClan(key);
        if (clan == null) return true;
        String ownerName = ClanDatabase.getMemberName(key, clan.ownerUuid());
        int members = ClanDatabase.countMembers(key);
        String mainKey = ClanDatabase.getMainClan(key);

        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        player.sendMessage(MessageUtil.parse("<gold>  ✦ </gold><white>Clan </white><yellow>" + clan.displayName() + "</yellow>"));
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        player.sendMessage(MessageUtil.parse("<gray>Leader: </gray><white>" + (ownerName != null ? ownerName : "unknown") + "</white>"));
        player.sendMessage(MessageUtil.parse("<gray>Members: </gray><white>" + members + "</white>"));
        String desc = clan.description();
        if (desc != null && !desc.isBlank()) {
            player.sendMessage(MessageUtil.parse("<gray>Description: </gray><white>" + desc + "</white>"));
        }
        if (mainKey != null) {
            ClanDatabase.ClanData mc = ClanDatabase.getClan(mainKey);
            String mn = mc != null ? MessageUtil.toPlainText(mc.displayName()) : mainKey;
            player.sendMessage(MessageUtil.parse("<gray>Dependent of: </gray><yellow>" + mn + "</yellow>"));
        } else {
            String depKey = ClanDatabase.getDependentClan(key);
            if (depKey != null) {
                ClanDatabase.ClanData dc = ClanDatabase.getClan(depKey);
                String dn = dc != null ? MessageUtil.toPlainText(dc.displayName()) : depKey;
                player.sendMessage(MessageUtil.parse("<gray>Has dependent: </gray><yellow>" + dn + "</yellow> <dark_gray>[N]</dark_gray>"));
            } else {
                player.sendMessage(MessageUtil.parse("<gray>Alliance: </gray><white>none</white>"));
            }
        }
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        return true;
    }

    // ============================================================
    // EDIT (your own clan)
    // ============================================================

    private boolean cmdEdit(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan edit <add|remove|list|home|selfpvp|rename|descript|role></yellow>"));
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
            case "rename" -> cmdEditRename(player, rest);
            case "descript" -> cmdEditDescript(player, rest);
            case "role" -> cmdEditRole(player, rest);
            default -> {
                player.sendMessage(MessageUtil.parse("<red>✖ <white>Unknown edit command: </white><yellow>" + sub + "</yellow>"));
                yield true;
            }
        };
    }

    // --- edit add (sends invite) ---
    private boolean cmdEditAdd(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan edit add <nick> <member|moderator|organizer></yellow>"));
            return true;
        }
        UUID uuid = player.getUniqueId();
        String key = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (key == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>")); return true; }
        String myRole = ClanDatabase.getRole(key, uuid.toString());
        if (!ClanRoles.hasRole(myRole, ClanRoles.W_ORGANIZER)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You need at least the organizer role to invite players.</white>"));
            return true;
        }

        String role = args[1].toLowerCase(Locale.ROOT);
        if (!List.of(ClanRoles.ROLE_MEMBER, ClanRoles.ROLE_MODERATOR, ClanRoles.ROLE_ORGANIZER).contains(role)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Invalid role. Choose: </white><yellow>member, moderator, organizer</yellow>"));
            return true;
        }
        if (!ClanRoles.canGrantRole(myRole, role)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You cannot assign </white><yellow>" + role + "</yellow><white> — only roles below your own.</white>"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Player </white><yellow>" + args[0] + "</yellow><white> not found.</white>"));
            return true;
        }
        if (target.getUniqueId().equals(uuid)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You can't invite yourself.</white>"));
            return true;
        }
        String targetUuid = target.getUniqueId().toString();
        if (ClanDatabase.getClanKeyByPlayer(targetUuid) != null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Player </white><yellow>" + args[0] + "</yellow><white> is already in a clan.</white>"));
            return true;
        }

        // Check if already invited
        ClanDatabase.InviteData existing = ClanDatabase.getInvite(key, targetUuid);
        if (existing != null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>This player already has a pending invite from your clan.</white>"));
            return true;
        }

        if (ClanDatabase.addInvite(key, targetUuid, target.getName(), role, player.getName())) {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Invite sent to </white><yellow>" + target.getName() + "</yellow><white> as </white><yellow>" + role + "</yellow><white>.</white>"));
            Player online = Bukkit.getPlayer(target.getUniqueId());
            if (online != null) {
                ClanDatabase.ClanData clan = ClanDatabase.getClan(key);
                String clanName = clan != null ? MessageUtil.toPlainText(clan.displayName()) : key;
                online.sendMessage(MessageUtil.parse("<gold>✉</gold> <white>You have been invited to clan </white><yellow>" + clanName + "</yellow><white> as </white><yellow>" + role + "</yellow><white>.</white>"));
                online.sendMessage(MessageUtil.parse("<gray>Accept: </gray><yellow>/ui clan invite accept " + MessageUtil.toPlainText(clan.displayName()) + "</yellow><gray>  Decline: </gray><yellow>/ui clan invite decline " + MessageUtil.toPlainText(clan.displayName()) + "</yellow>"));
            }
        } else {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed to send invite.</white>"));
        }
        return true;
    }

    // --- edit remove ---
    private boolean cmdEditRemove(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan edit remove <nick></yellow>"));
            return true;
        }
        UUID uuid = player.getUniqueId();
        String key = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (key == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>")); return true; }
        String myRole = ClanDatabase.getRole(key, uuid.toString());
        if (!ClanRoles.hasRole(myRole, ClanRoles.W_MODERATOR)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You need at least the moderator role to remove players.</white>"));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String targetUuid = target.getUniqueId().toString();
        String targetRole = ClanDatabase.getRole(key, targetUuid);
        if (targetRole == null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Player </white><yellow>" + args[0] + "</yellow><white> is not in your clan.</white>"));
            return true;
        }
        if (!ClanRoles.canKick(myRole, targetRole)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You cannot remove a player with a role at or above your own.</white>"));
            return true;
        }
        if (ClanDatabase.removeMember(key, targetUuid)) {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Player </white><yellow>" + target.getName() + "</yellow> <white>removed from the clan.</white>"));
            Player online = Bukkit.getPlayer(target.getUniqueId());
            if (online != null) {
                online.sendMessage(MessageUtil.parse("<red>✖ <white>You were removed from the clan.</white>"));
            }
        } else {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed to remove the player.</white>"));
        }
        return true;
    }

    // --- edit list ---
    private boolean cmdEditList(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        String key = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (key == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>")); return true; }
        int page = 1;
        if (args.length >= 1) { try { page = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {} }
        List<ClanDatabase.MemberData> members = ClanDatabase.getMembers(key);
        int totalPages = Math.max(1, (members.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(1, Math.min(page, totalPages));
        int from = (page - 1) * PER_PAGE;
        int to = Math.min(from + PER_PAGE, members.size());
        ClanDatabase.ClanData clan = ClanDatabase.getClan(key);

        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        player.sendMessage(MessageUtil.parse("<gold>  ✦ </gold><white>Members of </white><yellow>" + (clan != null ? clan.displayName() : key) + "</yellow> <gray>(" + page + "/" + totalPages + ")</gray>"));
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        for (int i = from; i < to; i++) {
            ClanDatabase.MemberData m = members.get(i);
            String rc = switch (m.role()) { case "leader" -> "<dark_red>"; case "organizer" -> "<red>"; case "moderator" -> "<gold>"; default -> "<white>"; };
            player.sendMessage(MessageUtil.parse("<gray>┌─ </gray><yellow>" + (i + 1) + ".</yellow> <white>" + m.playerName() + "</white> <gray>—</gray> " + rc + m.role()));
        }
        // Footer
        net.kyori.adventure.text.Component footer = MessageUtil.parse("<dark_gray>┃  <gray>Page <yellow>" + page + "<gray>/" + totalPages + "   ");
        if (page > 1) footer = footer.append(net.kyori.adventure.text.Component.text("[<]").color(net.kyori.adventure.text.format.NamedTextColor.YELLOW).clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/ui clan edit list " + (page - 1))));
        else footer = footer.append(MessageUtil.parse("<dark_gray>[<]"));
        footer = footer.append(MessageUtil.parse("  "));
        if (page < totalPages) footer = footer.append(net.kyori.adventure.text.Component.text("[>]").color(net.kyori.adventure.text.format.NamedTextColor.YELLOW).clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/ui clan edit list " + (page + 1))));
        else footer = footer.append(MessageUtil.parse("<dark_gray>[>]"));
        player.sendMessage(footer);
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        return true;
    }

    // --- edit selfpvp ---
    private boolean cmdEditSelfPvp(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan edit selfpvp <on|off></yellow>"));
            return true;
        }
        UUID uuid = player.getUniqueId();
        String key = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (key == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>")); return true; }
        if (!ClanRoles.hasRole(ClanDatabase.getRole(key, uuid.toString()), ClanRoles.W_ORGANIZER)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You need at least the organizer role.</white>"));
            return true;
        }
        String v = args[0].toLowerCase(Locale.ROOT);
        if (!v.equals("on") && !v.equals("off")) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>selfpvp must be </white><yellow>on</yellow><white> or </white><yellow>off</yellow><white>.</white>"));
            return true;
        }
        if (ClanDatabase.setClanSetting(key, "selfpvp", v)) {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Friendly fire is now </white><yellow>" + v + "</yellow><white>.</white>" + ("on".equals(v) ? " <gray>Clan members cannot attack each other.</gray>" : " <gray>Clan members can attack each other.</gray>")));
        } else {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed to change the setting.</white>"));
        }
        return true;
    }

    // --- edit rename ---
    private boolean cmdEditRename(Player player, String[] args) {
        if (args.length < 1) { player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan edit rename <name></yellow>")); return true; }
        UUID uuid = player.getUniqueId();
        String key = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (key == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>")); return true; }
        if (!ClanRoles.isLeader(ClanDatabase.getRole(key, uuid.toString()))) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Only the leader can rename the clan.</white>"));
            return true;
        }
        String newName = String.join(" ", args);
        if (ClanDatabase.renameClan(key, newName)) {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Clan renamed to </white><yellow>" + newName + "</yellow><white>.</white>"));
        } else {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed to rename.</white>"));
        }
        return true;
    }

    // --- edit descript ---
    private boolean cmdEditDescript(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        String key = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (key == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>")); return true; }
        if (!ClanRoles.isLeader(ClanDatabase.getRole(key, uuid.toString()))) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Only the leader can change the description.</white>"));
            return true;
        }
        String text = args.length >= 1 ? String.join(" ", args) : "";
        if (ClanDatabase.setDescription(key, text)) {
            player.sendMessage(MessageUtil.parse(text.isEmpty() ? "<green>✔</green> <white>Clan description cleared.</white>" : "<green>✔</green> <white>Clan description set.</white>"));
        } else {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed to change the description.</white>"));
        }
        return true;
    }

    // --- edit role <role> <nick> ---
    private boolean cmdEditRole(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan edit role <member|moderator|organizer> <nick></yellow>"));
            return true;
        }
        UUID uuid = player.getUniqueId();
        String key = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (key == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>")); return true; }
        if (!ClanRoles.hasRole(ClanDatabase.getRole(key, uuid.toString()), ClanRoles.W_ORGANIZER)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You need at least the organizer role to manage roles.</white>"));
            return true;
        }
        String newRole = args[0].toLowerCase(Locale.ROOT);
        if (!List.of(ClanRoles.ROLE_MEMBER, ClanRoles.ROLE_MODERATOR, ClanRoles.ROLE_ORGANIZER).contains(newRole)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Invalid role. Choose: </white><yellow>member, moderator, organizer</yellow>"));
            return true;
        }
        String myRole = ClanDatabase.getRole(key, uuid.toString());
        if (!ClanRoles.canGrantRole(myRole, newRole)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You cannot assign </white><yellow>" + newRole + "</yellow><white> — only roles below your own.</white>"));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        String targetUuid = target.getUniqueId().toString();
        String targetRole = ClanDatabase.getRole(key, targetUuid);
        if (targetRole == null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Player </white><yellow>" + args[1] + "</yellow><white> is not in your clan.</white>"));
            return true;
        }
        if (ClanRoles.isLeader(targetRole)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You cannot change the leader's role.</white>"));
            return true;
        }
        if (ClanDatabase.setRole(key, targetUuid, newRole)) {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Player </white><yellow>" + target.getName() + "</yellow> <white>is now </white><yellow>" + newRole + "</yellow><white>.</white>"));
        } else {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed to change the role.</white>"));
        }
        return true;
    }

    // --- edit home ---
    private boolean cmdEditHome(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan edit home <add|set|remove></yellow>"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "add" -> cmdHomeAdd(player);
            case "set" -> cmdHomeSet(player, args.length > 1 && args[1].equalsIgnoreCase("-confirm"));
            case "remove" -> cmdHomeDel(player, args.length > 1 && args[1].equalsIgnoreCase("-confirm"));
            default -> { player.sendMessage(MessageUtil.parse("<red>✖ <white>Unknown home command: </white><yellow>" + sub + "</yellow>")); yield true; }
        };
    }

    private boolean cmdHomeAdd(Player player) {
        UUID uuid = player.getUniqueId();
        String key = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (key == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>")); return true; }
        if (!ClanRoles.hasRole(ClanDatabase.getRole(key, uuid.toString()), ClanRoles.W_ORGANIZER)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You need at least the organizer role.</white>")); return true;
        }
        ClanDatabase.ClanData clan = ClanDatabase.getClan(key);
        if (clan != null && clan.hasHome()) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Your clan already has a home. Use </white><yellow>/ui clan edit home set</yellow><gray> to override.</gray>"));
            return true;
        }
        if (ClanDatabase.setHome(key, player.getLocation())) {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Clan home set at your location.</white>"));
        } else {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed to set the clan home.</white>"));
        }
        return true;
    }

    private boolean cmdHomeSet(Player player, boolean confirmed) {
        UUID uuid = player.getUniqueId();
        String key = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (key == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>")); return true; }
        if (!ClanRoles.hasRole(ClanDatabase.getRole(key, uuid.toString()), ClanRoles.W_ORGANIZER)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You need at least the organizer role.</white>")); return true;
        }
        if (confirmed) {
            if (!ClanManager.consumeConfirm(player, ClanManager.CONFIRM_HOME_SET)) {
                player.sendMessage(MessageUtil.parse("<red>✖ <white>Confirmation expired.</white>")); return true;
            }
            if (ClanDatabase.setHome(key, player.getLocation())) {
                player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Clan home overridden.</white>"));
            } else { player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed.</white>")); }
            return true;
        }
        ClanManager.armConfirm(player, ClanManager.CONFIRM_HOME_SET);
        player.sendMessage(MessageUtil.parse("<red>⚠ <white>Override the clan home?</white>"));
        sendConfirmButton(player, "/ui clan edit home set -confirm", "Override home");
        return true;
    }

    private boolean cmdHomeDel(Player player, boolean confirmed) {
        UUID uuid = player.getUniqueId();
        String key = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (key == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>")); return true; }
        if (!ClanRoles.hasRole(ClanDatabase.getRole(key, uuid.toString()), ClanRoles.W_ORGANIZER)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You need at least the organizer role.</white>")); return true;
        }
        if (confirmed) {
            if (!ClanManager.consumeConfirm(player, ClanManager.CONFIRM_HOME_DEL)) {
                player.sendMessage(MessageUtil.parse("<red>✖ <white>Confirmation expired.</white>")); return true;
            }
            if (ClanDatabase.deleteHome(key)) { player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Clan home deleted.</white>")); }
            else { player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed.</white>")); }
            return true;
        }
        ClanManager.armConfirm(player, ClanManager.CONFIRM_HOME_DEL);
        player.sendMessage(MessageUtil.parse("<red>⚠ <white>Delete the clan home?</white>"));
        sendConfirmButton(player, "/ui clan edit home remove -confirm", "Delete home");
        return true;
    }

    // ============================================================
    // HOME (teleport)
    // ============================================================

    private boolean cmdHome(Player player, String[] rest) {
        UUID uuid = player.getUniqueId();
        String key = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (key == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>")); return true; }
        ClanDatabase.ClanData clan = ClanDatabase.getClan(key);
        if (clan == null || !clan.hasHome()) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Your clan has no home.</white> <gray>Use </gray><yellow>/ui clan edit home add</yellow>"));
            return true;
        }
        String mode = cfgString("clan.home.mode", "legit");
        if (mode.equalsIgnoreCase("standard")) {
            World world = Bukkit.getWorld(clan.homeWorld());
            if (world == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>Clan home world not loaded.</white>")); return true; }
            int cd = cfgInt("clan.home.tp_cooldown_seconds", 0);
            if (cd > 0 && !player.hasPermission("ui.command.clan.home.bypasscooldown")) {
                Long last = clanTpCooldowns.get(uuid);
                if (last != null && System.currentTimeMillis() - last < cd * 1000L) {
                    long rem = (cd * 1000L - (System.currentTimeMillis() - last)) / 1000L + 1;
                    player.sendMessage(MessageUtil.parse("<red>✖ <white>Wait </white><yellow>" + rem + "</yellow><white> seconds.</white>"));
                    return true;
                }
            }
            Location loc = new Location(world, clan.homeX(), clan.homeY(), clan.homeZ(), clan.homeYaw(), clan.homePitch());
            player.teleport(loc);
            clanTpCooldowns.put(uuid, System.currentTimeMillis());
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Teleported to clan home.</white>"));
        } else {
            player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
            player.sendMessage(MessageUtil.parse("<gold>  ✦ </gold><white>Clan Home</white>"));
            player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
            player.sendMessage(MessageUtil.parse("<gray>World: </gray><white>" + clan.homeWorld() + "</white>"));
            player.sendMessage(MessageUtil.parse("<gray>X: </gray><white>" + Math.round(clan.homeX()) + "</white> <gray>Y: </gray><white>" + Math.round(clan.homeY()) + "</white> <gray>Z: </gray><white>" + Math.round(clan.homeZ()) + "</white>"));
            player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
            player.sendMessage(MessageUtil.parse("<yellow>⚠</yellow> <gray>Legit mode — no teleport.</gray>"));
        }
        return true;
    }

    // ============================================================
    // REQUEST (join clan)
    // ============================================================

    private boolean cmdRequest(Player player, String[] args) {
        if (args.length < 1) { player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan request <clan> | accept|decline <nick></yellow>")); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("accept") || sub.equals("decline")) return handleRequestDecision(player, sub, slice(args, 1));
        return requestJoin(player, args);
    }

    private boolean requestJoin(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        String key = normalizeKey(String.join(" ", args));
        if (!ClanDatabase.clanExists(key)) { player.sendMessage(MessageUtil.parse("<red>✖ <white>Clan not found.</white>")); return true; }
        if (ClanDatabase.getClanKeyByPlayer(uuid.toString()) != null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You are already in a clan.</white>"));
            return true;
        }
        if (!ClanManager.canRequest(uuid)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Wait before sending another request.</white>"));
            return true;
        }
        ClanDatabase.addRequest(key, uuid.toString(), player.getName());
        ClanManager.markRequest(uuid);
        player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Join request sent to </white><yellow>" + MessageUtil.toPlainText(ClanDatabase.getClan(key).displayName()) + "</yellow><white>.</white>"));
        for (ClanDatabase.MemberData m : ClanDatabase.getMembers(key)) {
            if (ClanRoles.hasRole(m.role(), ClanRoles.W_MODERATOR)) {
                Player mod = Bukkit.getPlayer(UUID.fromString(m.playerUuid()));
                if (mod != null) {
                    mod.sendMessage(MessageUtil.parse("<yellow>✉</yellow> <white>Player </white><yellow>" + player.getName() + "</yellow><white> requested to join. </white><gray>Accept: </gray><yellow>/ui clan request accept " + player.getName() + "</yellow>"));
                }
            }
        }
        return true;
    }

    private boolean handleRequestDecision(Player player, String decision, String[] args) {
        if (args.length < 1) { player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan request " + decision + " <nick></yellow>")); return true; }
        UUID uuid = player.getUniqueId();
        String key = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (key == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>")); return true; }
        if (!ClanRoles.hasRole(ClanDatabase.getRole(key, uuid.toString()), ClanRoles.W_MODERATOR)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You need at least the moderator role.</white>")); return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String targetUuid = target.getUniqueId().toString();
        ClanDatabase.RequestData req = ClanDatabase.getRequest(key, targetUuid);
        if (req == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>No pending request from </white><yellow>" + args[0] + "</yellow><white>.</white>")); return true; }
        int expireSec = cfgInt("clan.request_expire_seconds", 3600);
        if (System.currentTimeMillis() - req.requestedAt() > expireSec * 1000L) {
            ClanDatabase.removeRequest(key, targetUuid);
            player.sendMessage(MessageUtil.parse("<red>✖ <white>The request has expired.</white>")); return true;
        }
        ClanDatabase.removeRequest(key, targetUuid);
        if (decision.equals("accept")) {
            if (ClanDatabase.getClanKeyByPlayer(targetUuid) != null) {
                player.sendMessage(MessageUtil.parse("<red>✖ <white>Player already joined another clan.</white>")); return true;
            }
            ClanDatabase.addMember(key, targetUuid, target.getName(), ClanDatabase.ROLE_MEMBER);
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Player </white><yellow>" + target.getName() + "</yellow> <white>joined.</white>"));
            Player online = Bukkit.getPlayer(target.getUniqueId());
            if (online != null) online.sendMessage(MessageUtil.parse("<green>✔</green> <white>Your request was accepted! You joined the clan.</white>"));
        } else {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Request from </white><yellow>" + target.getName() + "</yellow> <white>declined.</white>"));
            Player online = Bukkit.getPlayer(target.getUniqueId());
            if (online != null) online.sendMessage(MessageUtil.parse("<red>✖ <white>Your clan request was declined.</white>"));
        }
        return true;
    }

    // ============================================================
    // INVITE (accept/decline)
    // ============================================================

    private boolean cmdInvite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan invite accept|decline <clan></yellow>"));
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        UUID uuid = player.getUniqueId();
        String targetKey = normalizeKey(String.join(" ", slice(args, 1)));
        if (!ClanDatabase.clanExists(targetKey)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Clan not found.</white>")); return true;
        }
        if (ClanDatabase.getClanKeyByPlayer(uuid.toString()) != null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You are already in a clan.</white>")); return true;
        }
        ClanDatabase.InviteData invite = ClanDatabase.getInvite(targetKey, uuid.toString());
        if (invite == null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>No pending invite from that clan.</white>")); return true;
        }
        ClanDatabase.removeInvite(targetKey, uuid.toString());

        if (action.equals("accept")) {
            if (ClanDatabase.addMember(targetKey, uuid.toString(), player.getName(), invite.role())) {
                player.sendMessage(MessageUtil.parse("<green>✔</green> <white>You joined the clan as </white><yellow>" + invite.role() + "</yellow><white>!</white>"));
                Player leader = Bukkit.getPlayer(UUID.fromString(invite.invitedBy()));
                if (leader != null) leader.sendMessage(MessageUtil.parse("<green>✔</green> <white>Player </white><yellow>" + player.getName() + "</yellow><white> accepted the invite.</white>"));
            } else {
                player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed to join.</white>"));
            }
        } else {
            player.sendMessage(MessageUtil.parse("<yellow>✖ Invite declined.</yellow>"));
            Player leader = Bukkit.getPlayer(UUID.fromString(invite.invitedBy()));
            if (leader != null) leader.sendMessage(MessageUtil.parse("<red>✖</red> <white>Player </white><yellow>" + player.getName() + "</yellow><white> declined the invite.</white>"));
        }
        return true;
    }

    // ============================================================
    // LEAVE
    // ============================================================

    private boolean cmdLeave(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        String key = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (key == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>")); return true; }
        if (ClanRoles.isLeader(ClanDatabase.getRole(key, uuid.toString()))) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>As leader, use </white><yellow>/ui clan transfer</yellow><white> or </white><yellow>/ui clan disband</yellow><white>.</white>"));
            return true;
        }
        // Check if dependent clan — need main clan's confirmation
        String mainKey = ClanDatabase.getMainClan(key);
        if (mainKey != null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Your clan is dependent on another clan. You cannot leave.</white> <gray>Use </gray><yellow>/ui clan depremove</yellow><gray> to break the dependency first.</gray>"));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("-confirm")) {
            if (!ClanManager.consumeConfirm(player, ClanManager.CONFIRM_LEAVE)) {
                player.sendMessage(MessageUtil.parse("<red>✖ <white>Confirmation expired.</white>")); return true;
            }
            if (ClanDatabase.removeMember(key, uuid.toString())) {
                player.sendMessage(MessageUtil.parse("<green>✔</green> <white>You left the clan.</white>"));
            } else { player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed.</white>")); }
            return true;
        }
        ClanManager.armConfirm(player, ClanManager.CONFIRM_LEAVE);
        player.sendMessage(MessageUtil.parse("<red>⚠ <white>Are you sure?</white>"));
        sendConfirmButton(player, "/ui clan leave -confirm", "Leave clan");
        return true;
    }

    // ============================================================
    // INFO
    // ============================================================

    private boolean cmdInfo(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        String key = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (key == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>")); return true; }
        ClanDatabase.ClanData clan = ClanDatabase.getClan(key);
        if (clan == null) return true;
        String ownerName = ClanDatabase.getMemberName(key, clan.ownerUuid());
        int members = ClanDatabase.countMembers(key);

        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        player.sendMessage(MessageUtil.parse("<gold>  ✦ </gold><white>Clan info </white><yellow>" + clan.displayName() + "</yellow>"));
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        player.sendMessage(MessageUtil.parse("<gray>Leader: </gray><white>" + (ownerName != null ? ownerName : "unknown") + "</white>"));
        player.sendMessage(MessageUtil.parse("<gray>Members: </gray><white>" + members + "</white>"));
        String desc = clan.description();
        if (desc != null && !desc.isBlank()) player.sendMessage(MessageUtil.parse("<gray>Description: </gray><white>" + desc + "</white>"));
        if (clan.hasHome()) {
            player.sendMessage(MessageUtil.parse("<gray>Home: </gray><white>" + clan.homeWorld() + " " + Math.round(clan.homeX()) + " " + Math.round(clan.homeY()) + " " + Math.round(clan.homeZ()) + "</white>"));
        } else {
            player.sendMessage(MessageUtil.parse("<gray>Home: </gray><yellow>not set</yellow>"));
        }
        // Alliance info
        String mainKey2 = ClanDatabase.getMainClan(key);
        String depKey2 = ClanDatabase.getDependentClan(key);
        if (mainKey2 != null) {
            ClanDatabase.ClanData mc = ClanDatabase.getClan(mainKey2);
            String mn = mc != null ? MessageUtil.toPlainText(mc.displayName()) : mainKey2;
            player.sendMessage(MessageUtil.parse("<gray>Alliance: </gray><white>Dependent of </white><yellow>" + mn + "</yellow>"));
        } else if (depKey2 != null) {
            ClanDatabase.ClanData dc = ClanDatabase.getClan(depKey2);
            String dn = dc != null ? MessageUtil.toPlainText(dc.displayName()) : depKey2;
            player.sendMessage(MessageUtil.parse("<gray>Alliance: </gray><white>Has dependent </white><yellow>" + dn + "</yellow> <dark_gray>[N]</dark_gray>"));
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
        if (key == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>")); return true; }
        List<ClanDatabase.MemberData> members = ClanDatabase.getMembers(key);
        List<String> online = new ArrayList<>();
        for (ClanDatabase.MemberData m : members) {
            Player p = Bukkit.getPlayerExact(m.playerName());
            if (p != null && p.isOnline()) online.add(p.getName());
        }
        player.sendMessage(MessageUtil.parse("<gold>  ✦ </gold><white>Online </white><gray>(" + online.size() + "/" + members.size() + ")</gray>"));
        player.sendMessage(MessageUtil.parse("<white>" + (online.isEmpty() ? "<gray>Nobody is online.</gray>" : String.join("<gray>, </gray>", online)) + "</white>"));
        return true;
    }

    // ============================================================
    // TRANSFER
    // ============================================================

    private boolean cmdTransfer(Player player, String[] args) {
        if (args.length < 1) { player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan transfer <nick></yellow>")); return true; }
        UUID uuid = player.getUniqueId();
        String key = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (key == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>")); return true; }
        if (!ClanRoles.isLeader(ClanDatabase.getRole(key, uuid.toString()))) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Only the leader can transfer.</white>")); return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String targetUuid = target.getUniqueId().toString();
        if (targetUuid.equals(uuid.toString())) { player.sendMessage(MessageUtil.parse("<red>✖ <white>You are already the leader.</white>")); return true; }
        if (ClanDatabase.getRole(key, targetUuid) == null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Player </white><yellow>" + args[0] + "</yellow><white> is not in your clan.</white>")); return true;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("-confirm")) {
            if (!ClanManager.consumeConfirm(player, ClanManager.CONFIRM_TRANSFER)) {
                player.sendMessage(MessageUtil.parse("<red>✖ <white>Confirmation expired.</white>")); return true;
            }
            if (ClanDatabase.transferLeader(key, targetUuid, target.getName(), uuid.toString())) {
                player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Leadership transferred to </white><yellow>" + target.getName() + "</yellow><white>.</white>"));
                Player online = Bukkit.getPlayer(target.getUniqueId());
                if (online != null) online.sendMessage(MessageUtil.parse("<green>✔</green> <white>You are now the leader!</white>"));
            } else { player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed.</white>")); }
            return true;
        }
        ClanManager.armConfirm(player, ClanManager.CONFIRM_TRANSFER);
        player.sendMessage(MessageUtil.parse("<red>⚠ <white>Transfer leadership to </white><yellow>" + target.getName() + "</yellow><white>?</white>"));
        sendConfirmButton(player, "/ui clan transfer " + args[0] + " -confirm", "Transfer leadership");
        return true;
    }

    // ============================================================
    // ADMEDIT (admin commands)
    // ============================================================

    private boolean cmdAdmedit(Player player, String[] args) {
        if (!player.hasPermission(PERMISSION_ADMIN)) { CommandErrors.noPermission(player); return true; }
        if (args.length < 2) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan admedit <clan> <subcmd></yellow>"));
            return true;
        }
        String targetKey = normalizeKey(args[0]);
        if (!ClanDatabase.clanExists(targetKey)) { player.sendMessage(MessageUtil.parse("<red>✖ <white>Clan not found.</white>")); return true; }
        String sub = args[1].toLowerCase(Locale.ROOT);
        String[] rest = slice(args, 2);

        return switch (sub) {
            case "remove" -> { yield args.length > 0 && !args[0].startsWith("-") ? cmdAdmeditRemoveMember(player, targetKey, rest) : cmdAdmeditRemoveClan(player, targetKey, rest); }
            case "edit" -> cmdAdmeditEdit(player, targetKey, rest);
            case "add" -> cmdAdmeditAdd(player, targetKey, rest);
            case "list" -> cmdAdmeditList(player, targetKey, rest);
            case "transfer" -> cmdAdmeditTransfer(player, targetKey, rest);
            default -> { player.sendMessage(MessageUtil.parse("<red>✖ <white>Unknown admedit command.</white>")); yield true; }
        };
    }

    private boolean cmdAdmeditRemoveClan(Player player, String targetKey, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("-confirm")) {
            if (!ClanManager.consumeConfirm(player, ClanManager.CONFIRM_ADMEDIT_REMOVE)) {
                player.sendMessage(MessageUtil.parse("<red>✖ <white>Confirmation expired.</white>")); return true;
            }
            ClanDatabase.ClanData clan = ClanDatabase.getClan(targetKey);
            String name = clan != null ? MessageUtil.toPlainText(clan.displayName()) : targetKey;
            // Notify online members
            for (ClanDatabase.MemberData m : ClanDatabase.getMembers(targetKey)) {
                Player p = Bukkit.getPlayer(UUID.fromString(m.playerUuid()));
                if (p != null) p.sendMessage(MessageUtil.parse("<red>⚠</red> <white>Your clan </white><yellow>" + name + "</yellow><white> has been removed by admin </white><yellow>" + player.getName() + "</yellow><white>.</white>"));
            }
            if (ClanDatabase.deleteClan(targetKey)) {
                player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Clan </white><yellow>" + name + "</yellow> <white>removed.</white>"));
            } else { player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed.</white>")); }
            return true;
        }
        ClanDatabase.ClanData clan = ClanDatabase.getClan(targetKey);
        String name = clan != null ? MessageUtil.toPlainText(clan.displayName()) : targetKey;
        ClanManager.armConfirm(player, ClanManager.CONFIRM_ADMEDIT_REMOVE);
        player.sendMessage(MessageUtil.parse("<red>⚠ <white>Remove clan </white><yellow>" + name + "</yellow><white>?</white>"));
        sendConfirmButton(player, "/ui clan admedit " + MessageUtil.toPlainText(clan.displayName()) + " remove -confirm", "Remove clan");
        return true;
    }

    private boolean cmdAdmeditEdit(Player player, String targetKey, String[] args) {
        if (args.length < 1) { player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan admedit <clan> edit <rename|descript|selfpvp|home|role></yellow>")); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] rest = slice(args, 1);
        // Reuse the edit methods but act on targetKey instead of player's own clan
        return switch (sub) {
            case "rename" -> cmdAdmeditRename(player, targetKey, rest);
            case "descript" -> cmdAdmeditDescript(player, targetKey, rest);
            case "selfpvp" -> cmdAdmeditSelfPvp(player, targetKey, rest);
            case "home" -> cmdAdmeditHome(player, targetKey, rest);
            case "role" -> cmdAdmeditRole(player, targetKey, rest);
            default -> { player.sendMessage(MessageUtil.parse("<red>✖ <white>Unknown edit subcommand.</white>")); yield true; }
        };
    }

    private boolean cmdAdmeditRename(Player player, String targetKey, String[] args) {
        if (args.length < 1) { player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan admedit <clan> edit rename <name></yellow>")); return true; }
        String newName = String.join(" ", args);
        if (ClanDatabase.renameClan(targetKey, newName)) {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Clan renamed to </white><yellow>" + newName + "</yellow><white>.</white>"));
        } else { player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed.</white>")); }
        return true;
    }

    private boolean cmdAdmeditDescript(Player player, String targetKey, String[] args) {
        String text = args.length >= 1 ? String.join(" ", args) : "";
        if (ClanDatabase.setDescription(targetKey, text)) {
            player.sendMessage(MessageUtil.parse(text.isEmpty() ? "<green>✔</green> <white>Description cleared.</white>" : "<green>✔</green> <white>Description set.</white>"));
        } else { player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed.</white>")); }
        return true;
    }

    private boolean cmdAdmeditSelfPvp(Player player, String targetKey, String[] args) {
        if (args.length < 1) { player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan admedit <clan> edit selfpvp <on|off></yellow>")); return true; }
        String v = args[0].toLowerCase(Locale.ROOT);
        if (!v.equals("on") && !v.equals("off")) { player.sendMessage(MessageUtil.parse("<red>✖ <white>Must be on/off.</white>")); return true; }
        if (ClanDatabase.setClanSetting(targetKey, "selfpvp", v)) {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>selfpvp set to </white><yellow>" + v + "</yellow><white>.</white>"));
        } else { player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed.</white>")); }
        return true;
    }

    private boolean cmdAdmeditHome(Player player, String targetKey, String[] args) {
        if (args.length < 1) { player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan admedit <clan> edit home <add|set|remove></yellow>")); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "add" -> { if (ClanDatabase.setHome(targetKey, player.getLocation())) player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Home set.</white>")); else player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed.</white>")); yield true; }
            case "set" -> { if (ClanDatabase.setHome(targetKey, player.getLocation())) player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Home overridden.</white>")); else player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed.</white>")); yield true; }
            case "remove" -> { if (ClanDatabase.deleteHome(targetKey)) player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Home deleted.</white>")); else player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed.</white>")); yield true; }
            default -> { player.sendMessage(MessageUtil.parse("<red>✖ <white>Unknown.</white>")); yield true; }
        };
    }

    private boolean cmdAdmeditRole(Player player, String targetKey, String[] args) {
        if (args.length < 2) { player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan admedit <clan> edit role <role> <nick></yellow>")); return true; }
        String newRole = args[0].toLowerCase(Locale.ROOT);
        if (!List.of("member", "moderator", "organizer").contains(newRole)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Invalid role.</white>")); return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        String targetUuid = target.getUniqueId().toString();
        String currentRole = ClanDatabase.getRole(targetKey, targetUuid);
        if (currentRole == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>Player not in that clan.</white>")); return true; }
        if (ClanRoles.isLeader(currentRole)) { player.sendMessage(MessageUtil.parse("<red>✖ <white>Cannot change leader's role.</white>")); return true; }
        if (ClanDatabase.setRole(targetKey, targetUuid, newRole)) {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Player </white><yellow>" + target.getName() + "</yellow> <white>is now </white><yellow>" + newRole + "</yellow><white>.</white>"));
        } else { player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed.</white>")); }
        return true;
    }

    private boolean cmdAdmeditAdd(Player player, String targetKey, String[] args) {
        if (args.length < 1) { player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan admedit <clan> add <player> [-force]</yellow>")); return true; }
        boolean force = false;
        String playerName = args[0];
        if (args.length >= 2 && args[1].equalsIgnoreCase("-force")) force = true;

        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Player not found.</white>")); return true;
        }
        String targetUuid = target.getUniqueId().toString();
        if (ClanDatabase.getClanKeyByPlayer(targetUuid) != null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Player is already in a clan.</white>")); return true;
        }

        if (force) {
            if (ClanDatabase.addMember(targetKey, targetUuid, target.getName(), ClanDatabase.ROLE_MEMBER)) {
                player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Player </white><yellow>" + target.getName() + "</yellow> <white>force-added.</white>"));
                Player online = Bukkit.getPlayer(target.getUniqueId());
                if (online != null) online.sendMessage(MessageUtil.parse("<green>✔</green> <white>You were added to a clan by an admin.</white>"));
            } else { player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed.</white>")); }
        } else {
            if (ClanDatabase.addInvite(targetKey, targetUuid, target.getName(), "member", player.getName())) {
                player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Invite sent to </white><yellow>" + target.getName() + "</yellow><white>.</white>"));
                Player online = Bukkit.getPlayer(target.getUniqueId());
                if (online != null) {
                    ClanDatabase.ClanData c = ClanDatabase.getClan(targetKey);
                    String cn = c != null ? MessageUtil.toPlainText(c.displayName()) : targetKey;
                    online.sendMessage(MessageUtil.parse("<gold>✉</gold> <white>You have been invited to clan </white><yellow>" + cn + "</yellow><white>. </white><gray>Accept: </gray><yellow>/ui clan invite accept " + cn + "</yellow>"));
                }
            } else { player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed.</white>")); }
        }
        return true;
    }

    private boolean cmdAdmeditRemoveMember(Player player, String targetKey, String[] args) {
        if (args.length < 1) { player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan admedit <clan> remove <player></yellow>")); return true; }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String targetUuid = target.getUniqueId().toString();
        if (ClanDatabase.getRole(targetKey, targetUuid) == null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Player not in that clan.</white>")); return true;
        }
        if (ClanDatabase.removeMember(targetKey, targetUuid)) {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Player </white><yellow>" + target.getName() + "</yellow> <white>removed.</white>"));
            Player online = Bukkit.getPlayer(target.getUniqueId());
            if (online != null) online.sendMessage(MessageUtil.parse("<red>✖ <white>You were removed from the clan by an admin.</white>"));
        } else { player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed.</white>")); }
        return true;
    }

    private boolean cmdAdmeditList(Player player, String targetKey, String[] args) {
        List<ClanDatabase.MemberData> members = ClanDatabase.getMembers(targetKey);
        ClanDatabase.ClanData clan = ClanDatabase.getClan(targetKey);
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        player.sendMessage(MessageUtil.parse("<gold>  ✦ </gold><white>Members of </white><yellow>" + (clan != null ? clan.displayName() : targetKey) + "</yellow> <gray>(admin view)</gray>"));
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        for (int i = 0; i < members.size(); i++) {
            ClanDatabase.MemberData m = members.get(i);
            String rc = switch (m.role()) { case "leader" -> "<dark_red>"; case "organizer" -> "<red>"; case "moderator" -> "<gold>"; default -> "<white>"; };
            player.sendMessage(MessageUtil.parse("<gray>┌─ </gray><yellow>" + (i + 1) + ".</yellow> <white>" + m.playerName() + "</white> <gray>—</gray> " + rc + m.role()));
        }
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        return true;
    }

    private boolean cmdAdmeditTransfer(Player player, String targetKey, String[] args) {
        if (args.length < 1) { player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan admedit <clan> transfer <player></yellow>")); return true; }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String targetUuid = target.getUniqueId().toString();
        ClanDatabase.ClanData clan = ClanDatabase.getClan(targetKey);
        if (clan == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>Clan not found.</white>")); return true; }
        if (ClanDatabase.getRole(targetKey, targetUuid) == null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Player not in that clan.</white>")); return true;
        }
        if (ClanDatabase.transferLeader(targetKey, targetUuid, target.getName(), clan.ownerUuid())) {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Leadership transferred to </white><yellow>" + target.getName() + "</yellow><white>.</white>"));
        } else { player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed.</white>")); }
        return true;
    }

    // ============================================================
    // DEPENDENT CLANS
    // ============================================================

    private boolean cmdDepInvite(Player player, String[] args) {
        if (args.length < 1) { player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan depinvite <clan></yellow>")); return true; }
        UUID uuid = player.getUniqueId();
        String myKey = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (myKey == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>")); return true; }
        if (!ClanRoles.isLeader(ClanDatabase.getRole(myKey, uuid.toString()))) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Only the leader can send dependency invites.</white>")); return true;
        }
        if (ClanDatabase.getDependentClan(myKey) != null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>Your clan already has a dependent.</white>")); return true; }
        if (!ClanManager.canDepInvite(myKey)) { player.sendMessage(MessageUtil.parse("<red>✖ <white>Cooldown.</white>")); return true; }
        String targetKey = normalizeKey(String.join(" ", args));
        if (!ClanDatabase.clanExists(targetKey)) { player.sendMessage(MessageUtil.parse("<red>✖ <white>Clan not found.</white>")); return true; }
        if (targetKey.equals(myKey)) { player.sendMessage(MessageUtil.parse("<red>✖ <white>Cannot invite your own clan.</white>")); return true; }
        if (ClanDatabase.isDependent(targetKey)) { player.sendMessage(MessageUtil.parse("<red>✖ <white>This clan is already dependent.</white>")); return true; }
        if (ClanDatabase.getMainClan(targetKey) != null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>This clan already has a main clan.</white>")); return true; }
        ClanDatabase.addDepRequest(myKey, targetKey);
        ClanManager.markDepInvite(myKey);
        ClanDatabase.ClanData myClan = ClanDatabase.getClan(myKey);
        ClanDatabase.ClanData targetClan = ClanDatabase.getClan(targetKey);
        String myName = myClan != null ? MessageUtil.toPlainText(myClan.displayName()) : myKey;
        String tgtName = targetClan != null ? MessageUtil.toPlainText(targetClan.displayName()) : targetKey;
        player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Dependency invite sent to </white><yellow>" + tgtName + "</yellow><white>.</white>"));
        if (targetClan != null) {
            Player tl = Bukkit.getPlayer(UUID.fromString(targetClan.ownerUuid()));
            if (tl != null) {
                tl.sendMessage(MessageUtil.parse("<gold>✉</gold> <white>Clan </white><yellow>" + myName + "</yellow><white> invites your clan to become dependent.</white>"));
                tl.sendMessage(MessageUtil.parse("<gray>Use </gray><yellow>/ui clan depaccept</yellow><gray> or </gray><yellow>/ui clan depdecline</yellow>"));
            }
        }
        return true;
    }

    private boolean cmdDepAccept(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        String myKey = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (myKey == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>")); return true; }
        if (!ClanRoles.isLeader(ClanDatabase.getRole(myKey, uuid.toString()))) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Only the leader can accept.</white>")); return true;
        }
        var request = ClanDatabase.getDepRequest(myKey);
        if (request == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>No pending invite.</white>")); return true; }
        if (ClanDatabase.getDependentClan(request.fromClan()) != null) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>The inviting clan already has a dependent.</white>"));
            ClanDatabase.removeDepRequest(request.fromClan(), myKey); return true;
        }
        if (ClanDatabase.setDependency(request.fromClan(), myKey)) {
            ClanDatabase.removeDepRequest(request.fromClan(), myKey);
            ClanDatabase.ClanData fromClan = ClanDatabase.getClan(request.fromClan());
            String fromName = fromClan != null ? MessageUtil.toPlainText(fromClan.displayName()) : request.fromClan();
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Your clan is now dependent on </white><yellow>" + fromName + "</yellow><white>!</white>"));
            if (fromClan != null) {
                Player ml = Bukkit.getPlayer(UUID.fromString(fromClan.ownerUuid()));
                if (ml != null) ml.sendMessage(MessageUtil.parse("<green>✔</green> <white>Clan </white><yellow>" + MessageUtil.toPlainText(ClanDatabase.getClan(myKey).displayName()) + "</yellow><white> accepted!</white>"));
            }
        } else { player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed.</white>")); }
        return true;
    }

    private boolean cmdDepDecline(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        String myKey = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (myKey == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>")); return true; }
        if (!ClanRoles.isLeader(ClanDatabase.getRole(myKey, uuid.toString()))) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Only the leader can decline.</white>")); return true;
        }
        var request = ClanDatabase.getDepRequest(myKey);
        if (request == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>No pending invite.</white>")); return true; }
        ClanDatabase.removeDepRequest(request.fromClan(), myKey);
        player.sendMessage(MessageUtil.parse("<yellow>✖ Invite declined.</yellow>"));
        ClanDatabase.ClanData fromClan = ClanDatabase.getClan(request.fromClan());
        if (fromClan != null) {
            Player ml = Bukkit.getPlayer(UUID.fromString(fromClan.ownerUuid()));
            if (ml != null) ml.sendMessage(MessageUtil.parse("<red>✖</red> <white>Clan </white><yellow>" + MessageUtil.toPlainText(ClanDatabase.getClan(myKey).displayName()) + "</yellow><white> declined.</white>"));
        }
        return true;
    }

    // --- depdisband: main leader sends request → dependent leader confirms ---
    private boolean cmdDepDisband(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        String myKey = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (myKey == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>")); return true; }
        if (!ClanRoles.isLeader(ClanDatabase.getRole(myKey, uuid.toString()))) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Only the leader can use this.</white>")); return true;
        }
        String depKey = ClanDatabase.getDependentClan(myKey);
        if (depKey == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>Your clan does not have a dependent.</white>")); return true; }

        // Check if there's a pending confirm from the dependent
        ClanDatabase.DepConfirmData pending = ClanDatabase.getDepConfirm("dep_disband", myKey, depKey);
        if (pending != null) {
            // Dependent leader is confirming
            if (args.length >= 1 && args[0].equalsIgnoreCase("-confirm")) {
                ClanDatabase.ClanData depClan = ClanDatabase.getClan(depKey);
                String depName = depClan != null ? MessageUtil.toPlainText(depClan.displayName()) : depKey;
                // Disband the dependent clan
                if (ClanDatabase.deleteClan(depKey)) {
                    player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Dependent clan </white><yellow>" + depName + "</yellow><white> disbanded.</white>"));
                    ClanDatabase.removeDepConfirm("dep_disband", myKey, depKey);
                } else { player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed.</white>")); }
                return true;
            }
            // Main leader already sent request — tell them to wait
            player.sendMessage(MessageUtil.parse("<yellow>⚠</yellow> <white>Request already sent. Waiting for </white><yellow>" + MessageUtil.toPlainText(ClanDatabase.getClan(depKey).displayName()) + "</yellow><white> to confirm.</white>"));
            return true;
        }

        // Send request to dependent clan leader
        ClanDatabase.addDepConfirm("dep_disband", myKey, depKey);
        ClanDatabase.ClanData depClan = ClanDatabase.getClan(depKey);
        String depName = depClan != null ? MessageUtil.toPlainText(depClan.displayName()) : depKey;
        player.sendMessage(MessageUtil.parse("<yellow>⚠</yellow> <white>Disband request sent to </white><yellow>" + depName + "</yellow><white>. Waiting for confirmation.</white>"));
        // Notify dependent leader
        if (depClan != null) {
            Player dl = Bukkit.getPlayer(UUID.fromString(depClan.ownerUuid()));
            if (dl != null) {
                ClanDatabase.ClanData myClan = ClanDatabase.getClan(myKey);
                String myName = myClan != null ? MessageUtil.toPlainText(myClan.displayName()) : myKey;
                dl.sendMessage(MessageUtil.parse("<red>⚠</red> <white>Clan </white><yellow>" + myName + "</yellow><white> wants to disband your dependency.</white>"));
                dl.sendMessage(MessageUtil.parse("<gray>Confirm: </gray><yellow>/ui clan depdisband -confirm</yellow><gray>  Decline: </gray><yellow>/ui clan depdecline</yellow>"));
            }
        }
        return true;
    }

    // --- depremove: dependent leader sends request → main leader confirms ---
    private boolean cmdDepRemove(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        String myKey = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (myKey == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>")); return true; }
        if (!ClanRoles.isLeader(ClanDatabase.getRole(myKey, uuid.toString()))) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Only the leader can use this.</white>")); return true;
        }
        String mainKey = ClanDatabase.getMainClan(myKey);
        if (mainKey == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>Your clan is not dependent on any clan.</white>")); return true; }

        // Check if there's a pending confirm from the main clan
        ClanDatabase.DepConfirmData pending = ClanDatabase.getDepConfirm("dep_remove", myKey, mainKey);
        if (pending != null) {
            if (args.length >= 1 && args[0].equalsIgnoreCase("-confirm")) {
                if (ClanDatabase.removeDependency(mainKey)) {
                    player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Dependency removed.</white>"));
                    ClanDatabase.removeDepConfirm("dep_remove", myKey, mainKey);
                    Player ml = Bukkit.getPlayer(UUID.fromString(ClanDatabase.getClan(mainKey).ownerUuid()));
                    if (ml != null) ml.sendMessage(MessageUtil.parse("<yellow>⚠</yellow> <white>Clan </white><yellow>" + MessageUtil.toPlainText(ClanDatabase.getClan(myKey).displayName()) + "</yellow><white> broke the dependency.</white>"));
                } else { player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed.</white>")); }
                return true;
            }
            player.sendMessage(MessageUtil.parse("<yellow>⚠</yellow> <white>Request already sent. Waiting for main clan to confirm.</white>"));
            return true;
        }

        // Send request to main clan leader
        ClanDatabase.addDepConfirm("dep_remove", myKey, mainKey);
        ClanDatabase.ClanData mainClan = ClanDatabase.getClan(mainKey);
        String mainName = mainClan != null ? MessageUtil.toPlainText(mainClan.displayName()) : mainKey;
        player.sendMessage(MessageUtil.parse("<yellow>⚠</yellow> <white>Remove request sent to </white><yellow>" + mainName + "</yellow><white>. Waiting for confirmation.</white>"));
        if (mainClan != null) {
            Player ml = Bukkit.getPlayer(UUID.fromString(mainClan.ownerUuid()));
            if (ml != null) {
                ClanDatabase.ClanData myClan = ClanDatabase.getClan(myKey);
                String myName = myClan != null ? MessageUtil.toPlainText(myClan.displayName()) : myKey;
                ml.sendMessage(MessageUtil.parse("<yellow>✉</yellow> <white>Clan </white><yellow>" + myName + "</yellow><white> wants to break the dependency.</white>"));
                ml.sendMessage(MessageUtil.parse("<gray>Confirm: </gray><yellow>/ui clan depremove -confirm</yellow><gray>  Decline: </gray><yellow>/ui clan depdecline</yellow>"));
            }
        }
        return true;
    }

    private boolean cmdDepInfo(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        String myKey = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (myKey == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>")); return true; }
        String mainKey = ClanDatabase.getMainClan(myKey);
        String depKey = ClanDatabase.getDependentClan(myKey);
        if (mainKey == null && depKey == null) { player.sendMessage(MessageUtil.parse("<gray>Your clan is not in a dependency.</gray>")); return true; }
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        player.sendMessage(MessageUtil.parse("<gold>  ✦ </gold><white>Dependency Info</white>"));
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        if (mainKey != null) {
            ClanDatabase.ClanData mc = ClanDatabase.getClan(mainKey);
            String mn = mc != null ? MessageUtil.toPlainText(mc.displayName()) : mainKey;
            ClanDatabase.ClanData ic = ClanDatabase.getClan(myKey);
            String in2 = ic != null ? MessageUtil.toPlainText(ic.displayName()) : myKey;
            player.sendMessage(MessageUtil.parse("<white>Main: </white><yellow>" + mn + "</yellow>"));
            player.sendMessage(MessageUtil.parse("<white>Dep: </white><yellow>" + in2 + "</yellow> <dark_gray>[N]</dark_gray>"));
        } else {
            ClanDatabase.ClanData ic = ClanDatabase.getClan(myKey);
            String in2 = ic != null ? MessageUtil.toPlainText(ic.displayName()) : myKey;
            ClanDatabase.ClanData dc = ClanDatabase.getClan(depKey);
            String dn = dc != null ? MessageUtil.toPlainText(dc.displayName()) : depKey;
            player.sendMessage(MessageUtil.parse("<white>Main: </white><yellow>" + in2 + "</yellow>"));
            player.sendMessage(MessageUtil.parse("<white>Dep: </white><yellow>" + dn + "</yellow> <dark_gray>[N]</dark_gray>"));
        }
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        return true;
    }

    private boolean cmdDepStatus(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        String myKey = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (myKey == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>")); return true; }
        String mainKey = ClanDatabase.getMainClan(myKey);
        String depKey = ClanDatabase.getDependentClan(myKey);
        if (mainKey != null) {
            ClanDatabase.ClanData mc = ClanDatabase.getClan(mainKey);
            String mn = mc != null ? MessageUtil.toPlainText(mc.displayName()) : mainKey;
            player.sendMessage(MessageUtil.parse("<gray>Status: </gray><yellow>dependent</yellow><gray> of </gray><yellow>" + mn + "</yellow>"));
        } else if (depKey != null) {
            ClanDatabase.ClanData dc = ClanDatabase.getClan(depKey);
            String dn = dc != null ? MessageUtil.toPlainText(dc.displayName()) : depKey;
            player.sendMessage(MessageUtil.parse("<gray>Status: </gray><yellow>main</yellow><gray> — dependent: </gray><yellow>" + dn + "</yellow>"));
        } else {
            player.sendMessage(MessageUtil.parse("<gray>Status: </gray><white>independent</white>"));
        }
        return true;
    }

    // --- depredir: redirect player from dependent clan to main clan ---
    private boolean cmdDepRedir(Player player, String[] args) {
        if (args.length < 1) { player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui clan depredir <player></yellow>")); return true; }
        UUID uuid = player.getUniqueId();
        String myKey = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (myKey == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>")); return true; }
        if (!ClanRoles.isLeader(ClanDatabase.getRole(myKey, uuid.toString()))) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Only the leader can use this.</white>")); return true;
        }
        String depKey = ClanDatabase.getDependentClan(myKey);
        if (depKey == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>Your clan does not have a dependent.</white>")); return true; }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String targetUuid = target.getUniqueId().toString();
        String targetClan = ClanDatabase.getClanKeyByPlayer(targetUuid);
        if (targetClan == null || !targetClan.equals(depKey)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Player </white><yellow>" + args[0] + "</yellow><white> is not in your dependent clan.</white>"));
            return true;
        }

        String targetRole = ClanDatabase.getRole(depKey, targetUuid);
        if (ClanRoles.isLeader(targetRole)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Cannot redirect the dependent clan's leader.</white>"));
            return true;
        }

        // Remove from dependent, add to main
        ClanDatabase.removeMember(depKey, targetUuid);
        if (ClanDatabase.addMember(myKey, targetUuid, target.getName(), ClanDatabase.ROLE_MEMBER)) {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Player </white><yellow>" + target.getName() + "</yellow> <white>redirected to your clan.</white>"));
            Player online = Bukkit.getPlayer(target.getUniqueId());
            if (online != null) online.sendMessage(MessageUtil.parse("<green>✔</green> <white>You were transferred to the main clan.</white>"));
        } else {
            // Re-add to dependent on failure
            ClanDatabase.addMember(depKey, targetUuid, target.getName(), targetRole);
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Failed to redirect.</white>"));
        }
        return true;
    }

    // ============================================================
    // DEPEDIT (edit dependent clan — main organizer)
    // ============================================================

    private boolean cmdDepEdit(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        String myKey = ClanDatabase.getClanKeyByPlayer(uuid.toString());
        if (myKey == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>You are not in a clan.</white>")); return true; }
        String myRole = ClanDatabase.getRole(myKey, uuid.toString());
        if (!ClanRoles.hasRole(myRole, ClanRoles.W_ORGANIZER)) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>You need at least the organizer role.</white>")); return true;
        }
        String depKey = ClanDatabase.getDependentClan(myKey);
        if (depKey == null) { player.sendMessage(MessageUtil.parse("<red>✖ <white>Your clan does not have a dependent.</white>")); return true; }
        if (args.length < 1) {
            player.sendMessage(MessageUtil.parse("<red>✖ <white>Usage: </white><yellow>/ui depedit <rename|descript|selfpvp|home|role></yellow>"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] rest = slice(args, 1);
        return switch (sub) {
            case "rename" -> cmdAdmeditRename(player, depKey, rest);
            case "descript" -> cmdAdmeditDescript(player, depKey, rest);
            case "selfpvp" -> cmdAdmeditSelfPvp(player, depKey, rest);
            case "home" -> cmdAdmeditHome(player, depKey, rest);
            case "role" -> cmdAdmeditRole(player, depKey, rest);
            default -> { player.sendMessage(MessageUtil.parse("<red>✖ <white>Unknown command.</white>")); yield true; }
        };
    }

    // ============================================================
    // TAB COMPLETE
    // ============================================================

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length <= 2) {
            String prefix = args.length == 2 ? args[1].toLowerCase(Locale.ROOT) : "";
            List<String> out = new ArrayList<>(List.of(
                    "create", "list", "listclans", "edit", "home",
                    "request", "invite", "leave", "info", "online",
                    "transfer",
                    "depinvite", "depaccept", "depdecline", "depdisband",
                    "depremove", "depinfo", "depstatus", "depredir", "depedit"));
            if (sender instanceof Player p && p.hasPermission(PERMISSION_ADMIN)) {
                out.add("admedit");
            }
            return out.stream().filter(s -> s.startsWith(prefix)).toList();
        }

        String sub = args[1].toLowerCase(Locale.ROOT);
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();

        switch (sub) {
            case "list", "listclans" -> {
                if (args.length == 3) {
                    for (ClanDatabase.ClanData c : ClanDatabase.getAllClans()) {
                        String plain = MessageUtil.toPlainText(c.displayName());
                        if (plain.toLowerCase(Locale.ROOT).startsWith(last)) out.add(plain);
                    }
                }
            }
            case "edit" -> {
                if (args.length == 3) {
                    for (String s : List.of("add", "remove", "list", "home", "selfpvp", "rename", "descript", "role")) {
                        if (s.startsWith(last)) out.add(s);
                    }
                } else if (args.length == 4) {
                    String edit = args[2].toLowerCase(Locale.ROOT);
                    if (edit.equals("home")) {
                        for (String s : List.of("add", "set", "remove")) { if (s.startsWith(last)) out.add(s); }
                    } else if (edit.equals("selfpvp")) {
                        for (String s : List.of("on", "off")) { if (s.startsWith(last)) out.add(s); }
                    } else if (edit.equals("add")) {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (p.getName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(p.getName());
                        }
                    } else if (edit.equals("remove") || edit.equals("role")) {
                        suggestMemberNames(sender, out, last);
                    }
                } else if (args.length == 5) {
                    String edit = args[2].toLowerCase(Locale.ROOT);
                    if (edit.equals("add")) {
                        for (String s : List.of("member", "moderator", "organizer")) { if (s.startsWith(last)) out.add(s); }
                    } else if (edit.equals("role")) {
                        for (String s : List.of("member", "moderator", "organizer")) { if (s.startsWith(last)) out.add(s); }
                    }
                }
            }
            case "admedit" -> {
                if (args.length == 3) {
                    for (ClanDatabase.ClanData c : ClanDatabase.getAllClans()) {
                        String plain = MessageUtil.toPlainText(c.displayName());
                        if (plain.toLowerCase(Locale.ROOT).startsWith(last)) out.add(plain);
                    }
                } else if (args.length == 4) {
                    for (String s : List.of("remove", "edit", "add", "list", "transfer")) { if (s.startsWith(last)) out.add(s); }
                } else if (args.length == 5) {
                    String cmd = args[3].toLowerCase(Locale.ROOT);
                    if (cmd.equals("edit")) {
                        for (String s : List.of("rename", "descript", "selfpvp", "home", "role")) { if (s.startsWith(last)) out.add(s); }
                    } else if (cmd.equals("add")) {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (p.getName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(p.getName());
                        }
                    } else if (cmd.equals("transfer")) {
                        suggestClanMembers(args[2], out, last);
                    }
                } else if (args.length == 6) {
                    String cmd = args[3].toLowerCase(Locale.ROOT);
                    if (cmd.equals("edit")) {
                        String subEdit = args[4].toLowerCase(Locale.ROOT);
                        if (subEdit.equals("home")) {
                            for (String s : List.of("add", "set", "remove")) { if (s.startsWith(last)) out.add(s); }
                        } else if (subEdit.equals("selfpvp")) {
                            for (String s : List.of("on", "off")) { if (s.startsWith(last)) out.add(s); }
                        } else if (subEdit.equals("role")) {
                            for (String s : List.of("member", "moderator", "organizer")) { if (s.startsWith(last)) out.add(s); }
                        }
                    }
                } else if (args.length == 7) {
                    String cmd = args[3].toLowerCase(Locale.ROOT);
                    if (cmd.equals("edit") && args[4].equalsIgnoreCase("role")) {
                        suggestClanMembers(args[2], out, last);
                    }
                }
            }
            case "request", "reqest" -> {
                if (args.length == 3) {
                    if (args[2].equalsIgnoreCase("accept") || args[2].equalsIgnoreCase("decline")) {
                        suggestRequesters(sender, out, last);
                    } else {
                        for (ClanDatabase.ClanData c : ClanDatabase.getAllClans()) {
                            String plain = MessageUtil.toPlainText(c.displayName());
                            if (plain.toLowerCase(Locale.ROOT).startsWith(last)) out.add(plain);
                        }
                    }
                } else if (args.length == 4) {
                    for (String s : List.of("accept", "decline")) { if (s.startsWith(last)) out.add(s); }
                }
            }
            case "invite" -> {
                if (args.length == 3) {
                    for (String s : List.of("accept", "decline")) { if (s.startsWith(last)) out.add(s); }
                } else if (args.length == 4) {
                    // Suggest clan names from player's pending invites
                    if (sender instanceof Player p) {
                        var invites = ClanDatabase.getInvitesForPlayer(p.getUniqueId().toString());
                        for (var inv : invites) {
                            ClanDatabase.ClanData c = ClanDatabase.getClan(inv.clanKey());
                            if (c != null) {
                                String plain = MessageUtil.toPlainText(c.displayName());
                                if (plain.toLowerCase(Locale.ROOT).startsWith(last)) out.add(plain);
                            }
                        }
                    }
                }
            }
            case "transfer" -> {
                if (args.length == 3) suggestMemberNames(sender, out, last);
            }
            case "depredir" -> {
                if (args.length == 3) {
                    // Suggest members of dependent clan
                    if (sender instanceof Player p) {
                        String myKey = ClanDatabase.getClanKeyByPlayer(p.getUniqueId().toString());
                        if (myKey != null) {
                            String depKey = ClanDatabase.getDependentClan(myKey);
                            if (depKey != null) {
                                for (ClanDatabase.MemberData m : ClanDatabase.getMembers(depKey)) {
                                    if (m.playerName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(m.playerName());
                                }
                            }
                        }
                    }
                }
            }
            case "depedit" -> {
                if (args.length == 3) {
                    for (String s : List.of("rename", "descript", "selfpvp", "home", "role")) { if (s.startsWith(last)) out.add(s); }
                } else if (args.length == 4) {
                    String subEdit = args[2].toLowerCase(Locale.ROOT);
                    if (subEdit.equals("home")) {
                        for (String s : List.of("add", "set", "remove")) { if (s.startsWith(last)) out.add(s); }
                    } else if (subEdit.equals("selfpvp")) {
                        for (String s : List.of("on", "off")) { if (s.startsWith(last)) out.add(s); }
                    } else if (subEdit.equals("role")) {
                        for (String s : List.of("member", "moderator", "organizer")) { if (s.startsWith(last)) out.add(s); }
                    }
                } else if (args.length == 5 && args[2].equalsIgnoreCase("role")) {
                    if (sender instanceof Player p) {
                        String myKey = ClanDatabase.getClanKeyByPlayer(p.getUniqueId().toString());
                        if (myKey != null) {
                            String depKey = ClanDatabase.getDependentClan(myKey);
                            if (depKey != null) {
                                for (ClanDatabase.MemberData m : ClanDatabase.getMembers(depKey)) {
                                    if (m.playerName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(m.playerName());
                                }
                            }
                        }
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
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan list</yellow> <gray>— list all clans</gray>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan listclans <clan></yellow> <gray>— detailed view</gray>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan info</yellow> <gray>— your clan info</gray>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan online</yellow> <gray>— who is online</gray>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan home</yellow> <gray>— teleport to clan home</gray>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan request <clan></yellow> <gray>— join request</gray>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan request accept|decline <nick></yellow>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan invite accept|decline <clan></yellow> <gray>— handle invite</gray>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan leave</yellow>"));
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        player.sendMessage(MessageUtil.parse("<gold>  ✦ </gold><white>Edit (organizer+)</white>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan edit add <nick> <role></yellow>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan edit remove <nick></yellow>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan edit list</yellow>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan edit home <add|set|remove></yellow>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan edit selfpvp <on|off></yellow>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan edit rename <name></yellow> <gray>(leader)</gray>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan edit descript <text></yellow> <gray>(leader)</gray>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan edit role <role> <nick></yellow>"));
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        player.sendMessage(MessageUtil.parse("<gold>  ✦ </gold><white>Leader</white>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan transfer <nick></yellow>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan depinvite <clan></yellow>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan depaccept|depdecline</yellow>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan depdisband</yellow> <gray>— disband dependent</gray>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan depremove</yellow> <gray>— break dependency</gray>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan depredir <player></yellow> <gray>— redirect to main</gray>"));
        player.sendMessage(MessageUtil.parse("<gray>│ </gray><yellow>/ui clan depinfo|depstatus</yellow>"));
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
    }

    private void sendConfirmButton(Player player, String command, String label) {
        player.sendMessage(MessageUtil.parse("<dark_gray>┃     <dark_green>[<green>✔ " + label + "<dark_green>]</dark_green>")
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(command))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(MessageUtil.parse("<green>" + label + "</green>"))));
    }

    private static String normalizeKey(String raw) {
        return MessageUtil.toPlainText(raw).trim().toLowerCase(Locale.ROOT);
    }

    private static void suggestMemberNames(CommandSender sender, List<String> out, String last) {
        if (!(sender instanceof Player p)) return;
        String key = ClanDatabase.getClanKeyByPlayer(p.getUniqueId().toString());
        if (key == null) return;
        for (ClanDatabase.MemberData m : ClanDatabase.getMembers(key)) {
            if (m.playerName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(m.playerName());
        }
    }

    private static void suggestClanMembers(String clanName, List<String> out, String last) {
        String key = normalizeKey(clanName);
        for (ClanDatabase.MemberData m : ClanDatabase.getMembers(key)) {
            if (m.playerName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(m.playerName());
        }
    }

    private static void suggestRequesters(CommandSender sender, List<String> out, String last) {
        if (!(sender instanceof Player p)) return;
        String key = ClanDatabase.getClanKeyByPlayer(p.getUniqueId().toString());
        if (key == null) return;
        for (ClanDatabase.RequestData r : ClanDatabase.getRequests(key)) {
            if (r.playerName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(r.playerName());
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
