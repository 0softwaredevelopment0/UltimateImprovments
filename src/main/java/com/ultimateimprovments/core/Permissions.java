package com.ultimateimprovments.core;

import com.ultimateimprovments.util.ConsoleLogger;

import org.bukkit.Bukkit;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;

import java.util.List;
import java.util.Map;

/**
 * Canonical permission list of UltimateImprovments (previously declared in plugin.yml,
 * the {@code permissions:} section — now all in code).
 *
 * <p>Hierarchy:
 * <ul>
 *   <li>{@code ui.*} — all permissions (wildcard)</li>
 *   <li>{@code ui.admin} — full access (granted to OP by default; includes {@code ui} and {@code ui.*})</li>
 *   <li>{@code ui.command.*} — all /ui command permissions</li>
 *   <li>{@code ui.command.<name>} — permission for a specific command</li>
 * </ul>
 *
 * <p>Registration is idempotent: {@code addPermission} is only called if the permission
 * is not yet in the server registry, so repeated starts (PlugMan, {@code /ui reload})
 * do not fail with "already defined".
 */
public final class Permissions {

    private Permissions() {}

    // ═══════════ Root ═══════════
    public static final String UI = "ui";
    public static final String UI_ALL = "ui.*";
    public static final String UI_ADMIN = "ui.admin";
    public static final String UI_COMMAND_ALL = "ui.command.*";

    // ═══════════ Commands: utilities ═══════════
    public static final String CMD_RELOAD = "ui.command.reload";
    public static final String CMD_VERSION = "ui.command.version";
    public static final String CMD_CHECKVER = "ui.command.checkver";
    public static final String CMD_UPDATEJAR = "ui.command.updatejar";
    public static final String CMD_SWAPJAR = "ui.command.swapjar";
    public static final String CMD_PLUGIN = "ui.command.plugin";
    public static final String CMD_PLUGINS = "ui.command.plugins";
    public static final String CMD_HELP = "ui.command.help";

    // ═══════════ Commands: player ═══════════
    public static final String CMD_HOME = "ui.command.home";
    public static final String CMD_SETHOME = "ui.command.sethome";
    public static final String CMD_DELHOME = "ui.command.delhome";
    public static final String CMD_LISTHOMES = "ui.command.listhomes";
    public static final String CMD_OPHOMELS = "ui.command.ophomels";
    public static final String CMD_OPDELHOME = "ui.command.opdelhome";
    public static final String CMD_SPAWN = "ui.command.spawn";
    public static final String CMD_SETSPAWN = "ui.command.setspawn";
    public static final String CMD_FLY = "ui.command.fly";
    public static final String CMD_FLY_OTHER = "ui.command.fly.other";
    public static final String CMD_FLYSPEED = "ui.command.flyspeed";
    public static final String CMD_GOD = "ui.command.god";
    public static final String CMD_GOD_OTHER = "ui.command.god.other";
    public static final String CMD_HEAL = "ui.command.heal";
    public static final String CMD_FEED = "ui.command.feed";
    public static final String CMD_TOGGLESPEED = "ui.command.togglespeed";
    public static final String CMD_TOGGLEFLY = "ui.command.togglefly";
    public static final String CMD_TOGGLEBB = "ui.command.togglebb";
    public static final String CMD_TOGGLESB = "ui.command.togglesb";
    public static final String CMD_TOGGLEPING = "ui.command.toggleping";
    public static final String CMD_TOGGLEBIND = "ui.command.togglebind";
    public static final String CMD_TOGGLERADVIEW = "ui.command.toggleradview";
    public static final String CMD_VANISH = "ui.command.vanish";
    public static final String CMD_NOTES = "ui.command.notes";
    public static final String CMD_UNLOCK = "ui.command.unlock";
    public static final String CMD_SUICIDE = "ui.command.suicide";
    public static final String CMD_FORCESUICIDE = "ui.command.forcesuicide";
    public static final String CMD_EXPSPLIT = "ui.command.expsplit";
    public static final String CMD_NEAR = "ui.command.near";
    public static final String CMD_RTP = "ui.command.rtp";
    public static final String CMD_RTP_OTHER = "ui.command.rtp.other";
    public static final String CMD_RTP_BYPASSCOOLDOWN = "ui.command.rtp.bypasscooldown";
    public static final String CMD_ASKPOS = "ui.command.askpos";
    public static final String CMD_GETPOS = "ui.command.getpos";
    public static final String CMD_SHAREPOS = "ui.command.sharepos";
    public static final String CMD_TURRET = "ui.command.turret";

    // ═══════════ Commands: moderation ═══════════
    public static final String CMD_PUNISH = "ui.command.punish";
    public static final String CMD_PUNISH_KICK = "ui.command.punish.kick";
    public static final String CMD_PUNISH_BAN = "ui.command.punish.ban";
    public static final String CMD_PUNISH_MUTE = "ui.command.punish.mute";
    public static final String CMD_PUNISH_WARN = "ui.command.punish.warn";
    public static final String CMD_PUNISH_CRASH = "ui.command.punish.crash";
    public static final String CMD_PUNISH_LISTWARNS_SELF = "ui.command.punish.listwarns.self";
    public static final String CMD_PUNISH_LISTWARNS_OTHER = "ui.command.punish.listwarns.other";
    public static final String CMD_PUNISH_ACTIONLIST = "ui.command.punish.actionlist";
    public static final String CMD_INVSEE = "ui.command.invsee";
    public static final String CMD_ENDERSEE = "ui.command.endersee";
    public static final String CMD_INV_EDIT = "ui.command.inv.edit";
    public static final String CMD_CHGOP = "ui.command.chgop";
    public static final String CMD_BROADCAST = "ui.command.broadcast";
    public static final String CMD_CLEARCHAT = "ui.command.clearchat";
    public static final String CMD_EXECCHAT = "ui.command.execchat";
    public static final String CMD_BLACKLIST = "ui.command.blacklist";
    public static final String CMD_OPWHITELIST = "ui.command.opwhitelist";
    public static final String CMD_WHITELIST = "ui.command.whitelist";
    public static final String CMD_MAINTENANCE = "ui.command.maintenance";
    public static final String CMD_CHECK = "ui.command.check";
    public static final String CMD_AC = "ui.command.ac";
    public static final String CMD_PROTECTION = "ui.command.protection";
    public static final String CMD_STRUCTURES = "ui.command.structures";
    public static final String CMD_STRUCTURES_DFC = "ui.command.structures.dfc";
    public static final String CMD_STRUCTURES_MAGNET = "ui.command.structures.magnet";
    public static final String CMD_MENU = "ui.command.menu";
    public static final String CMD_REPORT = "ui.command.report";
    public static final String CMD_REPORTS = "ui.command.reports";
    public static final String CMD_REPSTATUS = "ui.command.repstatus";
    public static final String CMD_MODREPORT = "ui.command.modreport";

    // ═══════════ Commands: economy and system ═══════════
    public static final String CMD_MONEY = "ui.command.money";
    public static final String CMD_AUTH = "ui.command.auth";
    public static final String CMD_AUTH_FORCELOGIN = "ui.command.auth.forcelogin";
    public static final String CMD_AUTH_RESETAUTH = "ui.command.auth.resetauth";
    public static final String CMD_AUTH_DELSESSION = "ui.command.auth.delsession";
    public static final String CMD_AUTH_CHGPASS = "ui.command.auth.chgpass";
    public static final String CMD_CODEPANE = "ui.command.codepane";
    public static final String CMD_CODEPANE_KEY = "ui.command.codepane.key";
    public static final String CMD_CODEPANE_KEY_ADD = "ui.command.codepane.key.add";
    public static final String CMD_CODEPANE_KEY_LIST = "ui.command.codepane.key.list";
    public static final String CMD_CODEPANE_KEY_REMOVE = "ui.command.codepane.key.remove";
    public static final String CMD_CODEPANE_KEY_MODIFY = "ui.command.codepane.key.modify";
    public static final String CMD_CHGDIM = "ui.command.chgdim";
    public static final String CMD_CHGDIM_ALL = "ui.command.chgdim.*";
    public static final String CMD_ITEM = "ui.command.item";
    public static final String CMD_ENCHANT = "ui.command.enchant";
    public static final String CMD_REACTOR = "ui.command.reactor";
    public static final String CMD_SETRAD = "ui.command.setrad";
    public static final String CMD_METEOR_SPAWN = "ui.command.meteor.spawn";
    public static final String CMD_REDSTONE_LIST = "ui.command.redstone.list";
    public static final String CMD_POWER_OFF = "ui.command.power.off";
    public static final String CMD_POWER_REBOOT = "ui.command.power.reboot";
    public static final String CMD_POWER_UNDO = "ui.command.power.undo";
    public static final String CMD_SUDO = "ui.command.sudo";
    public static final String CMD_UUID = "ui.command.uuid";
    public static final String CMD_CILIST = "ui.command.cilist";
    public static final String CMD_VOTE = "ui.command.vote";
    public static final String CMD_VOTE_CREATE = "ui.command.vote.create";
    public static final String CMD_VOTE_CHANGE = "ui.command.vote.change";
    public static final String CMD_VOTE_CHANGE_OTHER = "ui.command.vote.change.other";
    public static final String CMD_VOTE_DELETE_OTHER = "ui.command.vote.delete.other";
    public static final String CMD_VOTE_STATS = "ui.command.vote.stats";
    public static final String CMD_VOTE_BYPASS = "ui.command.vote.bypass";
    public static final String CMD_PDC = "ui.command.pdc";
    public static final String CMD_ITEMNBT = "ui.command.itemnbt";
    public static final String CMD_CLAN = "ui.command.clan";
    public static final String CMD_CLAN_REMOVE = "ui.command.clan.remove";
    public static final String CMD_CLAN_HOME_BYPASS = "ui.command.clan.home.bypasscooldown";

    // ═══════════ Features and modifiers ═══════════
    public static final String UI_ALERTS = "ui.alerts";
    public static final String UI_AUTOCRAFT = "ui.autocraft";
    public static final String UI_SHOW_BRAND = "ui.show.brand";
    public static final String UI_SUDO = "ui.sudo";
    public static final String UI_ANTICHEAT_BYPASS = "ui.anticheat.bypass";
    public static final String UI_ANTICHEAT_NOTIFY = "ui.anticheat.notify";
    public static final String UI_CHAT_CUSTOM_BYPASS = "ui.chat.custom.bypass";
    public static final String UI_CHAT_FILTER_BYPASS = "ui.chat.filter.bypass";
    public static final String UI_GMPROTECT_BYPASS = "ui.gmprotect.bypass";
    public static final String UI_CREATIVE_BYPASS = "ui.creative.bypass";
    public static final String UI_PACKETGUARD_BYPASS = "ui.packetguard.bypass";
    public static final String UI_ENCHANT_AOE_BYPASS = "ui.enchant.aoe.bypass";
    public static final String UI_OVERLOAD_LOGS = "ui.overload.logs";
    public static final String UI_PUNISH_NOTIFY = "ui.punish.notify";
    public static final String UI_PROXY_SERVER = "ui.proxy.server";

    // ═══════════ REGISTRATION ═══════════

    /**
     * Registers all plugin permissions in the {@link PluginManager}.
     * Called once at startup (see {@link PluginStartup}).
     * Idempotent: if the permission is already in the registry — it is skipped.
     */
    public static void registerAll() {
        PluginManager pm = Bukkit.getPluginManager();

        List<Permission> permissions = List.of(
                // ── Root ──
                new Permission(UI, "Base access to UltimateImprovments commands", PermissionDefault.FALSE),
                new Permission(UI_ALL, "All UltimateImprovments permissions", PermissionDefault.FALSE),
                new Permission(UI_ADMIN, "Full access to all UltimateImprovments features",
                        PermissionDefault.OP, Map.of(UI, true, UI_ALL, true)),
                new Permission(UI_COMMAND_ALL, "All /ui command permissions", PermissionDefault.FALSE),

                // ── Commands: utilities ──
                new Permission(CMD_RELOAD, "Reload plugin configuration", PermissionDefault.FALSE),
                new Permission(CMD_VERSION, "Show plugin version", PermissionDefault.FALSE),
                new Permission(CMD_CHECKVER, "Check for plugin updates", PermissionDefault.FALSE),
                new Permission(CMD_UPDATEJAR, "Download and install plugin updates", PermissionDefault.FALSE),
                new Permission(CMD_SWAPJAR, "Swap plugin jar", PermissionDefault.FALSE),
                new Permission(CMD_PLUGIN, "Manage other plugins", PermissionDefault.FALSE),
                new Permission(CMD_PLUGINS, "List plugins", PermissionDefault.FALSE),
                new Permission(CMD_HELP, "Show command help", PermissionDefault.FALSE),

                // ── Commands: player ──
                new Permission(CMD_HOME, "Teleport to your home", PermissionDefault.FALSE),
                new Permission(CMD_SETHOME, "Set your home", PermissionDefault.FALSE),
                new Permission(CMD_DELHOME, "Delete your home", PermissionDefault.FALSE),
                new Permission(CMD_LISTHOMES, "List your homes", PermissionDefault.FALSE),
                new Permission(CMD_OPHOMELS, "List other players' homes", PermissionDefault.FALSE),
                new Permission(CMD_OPDELHOME, "Delete other players' homes", PermissionDefault.FALSE),
                new Permission(CMD_SPAWN, "Teleport to spawn", PermissionDefault.FALSE),
                new Permission(CMD_SETSPAWN, "Set the spawn point", PermissionDefault.FALSE),
                new Permission(CMD_FLY, "Toggle flight for yourself", PermissionDefault.FALSE),
                new Permission(CMD_FLY_OTHER, "Toggle flight for other players", PermissionDefault.FALSE),
                new Permission(CMD_FLYSPEED, "Set fly speed", PermissionDefault.FALSE),
                new Permission(CMD_GOD, "Toggle god mode for yourself", PermissionDefault.FALSE),
                new Permission(CMD_GOD_OTHER, "Toggle god mode for other players", PermissionDefault.FALSE),
                new Permission(CMD_HEAL, "Heal yourself", PermissionDefault.FALSE),
                new Permission(CMD_FEED, "Feed yourself", PermissionDefault.FALSE),
                new Permission(CMD_TOGGLESPEED, "Toggle speed display", PermissionDefault.FALSE),
                new Permission(CMD_TOGGLEFLY, "Toggle elytra boost on jump", PermissionDefault.FALSE),
                new Permission(CMD_TOGGLEBB, "Toggle per-player boss bar", PermissionDefault.FALSE),
                new Permission(CMD_TOGGLESB, "Toggle per-player scoreboard", PermissionDefault.FALSE),
                new Permission(CMD_TOGGLEPING, "Toggle ping sound", PermissionDefault.FALSE),
                new Permission(CMD_TOGGLEBIND, "Toggle wireless redstone binding", PermissionDefault.FALSE),
                new Permission(CMD_TOGGLERADVIEW, "Toggle radiation display", PermissionDefault.FALSE),
                new Permission(CMD_VANISH, "Vanish players", PermissionDefault.FALSE),
                new Permission(CMD_NOTES, "Open notes GUI", PermissionDefault.FALSE),
                new Permission(CMD_UNLOCK, "Unlock books and signs", PermissionDefault.FALSE),
                new Permission(CMD_SUICIDE, "Kill yourself (with confirmation)", PermissionDefault.FALSE),
                new Permission(CMD_FORCESUICIDE, "Force another player to suicide", PermissionDefault.FALSE),
                new Permission(CMD_EXPSPLIT, "Split your XP into an XP bottle", PermissionDefault.FALSE),
                new Permission(CMD_NEAR, "List players nearby", PermissionDefault.FALSE),
                new Permission(CMD_RTP, "Random teleport", PermissionDefault.FALSE),
                new Permission(CMD_RTP_OTHER, "Random teleport another player", PermissionDefault.FALSE),
                new Permission(CMD_RTP_BYPASSCOOLDOWN, "Bypass random teleport cooldown", PermissionDefault.FALSE),
                new Permission(CMD_ASKPOS, "Request player coordinates", PermissionDefault.FALSE),
                new Permission(CMD_GETPOS, "Get a player's coordinates", PermissionDefault.FALSE),
                new Permission(CMD_SHAREPOS, "Share your coordinates in chat (confirmation dialog)", PermissionDefault.TRUE),
                new Permission(CMD_TURRET, "Configure end crystal turrets", PermissionDefault.TRUE),

                // ── Commands: moderation ──
                new Permission(CMD_PUNISH, "Use punish commands", PermissionDefault.FALSE),
                new Permission(CMD_PUNISH_KICK, "Kick players", PermissionDefault.FALSE),
                new Permission(CMD_PUNISH_BAN, "Ban players", PermissionDefault.FALSE),
                new Permission(CMD_PUNISH_MUTE, "Mute players", PermissionDefault.FALSE),
                new Permission(CMD_PUNISH_WARN, "Warn players", PermissionDefault.FALSE),
                new Permission(CMD_PUNISH_CRASH, "Crash a player's client", PermissionDefault.FALSE),
                new Permission(CMD_PUNISH_LISTWARNS_SELF, "View your own warnings", PermissionDefault.FALSE),
                new Permission(CMD_PUNISH_LISTWARNS_OTHER, "View other players' warnings", PermissionDefault.FALSE),
                new Permission(CMD_PUNISH_ACTIONLIST, "View all active punishments (paginated list with tabs)", PermissionDefault.FALSE),
                new Permission(CMD_INVSEE, "View player inventories (online and offline)", PermissionDefault.FALSE),
                new Permission(CMD_ENDERSEE, "View player ender chests (online and offline)", PermissionDefault.FALSE),
                new Permission(CMD_INV_EDIT, "Edit offline player inventories (with backup)", PermissionDefault.FALSE),
                new Permission(CMD_CHGOP, "Grant or revoke operator status", PermissionDefault.FALSE),
                new Permission(CMD_BROADCAST, "Broadcast a message to the server", PermissionDefault.FALSE),
                new Permission(CMD_CLEARCHAT, "Clear the chat for a player or everyone", PermissionDefault.FALSE),
                new Permission(CMD_EXECCHAT, "Send a chat message or run a command as another player", PermissionDefault.FALSE),
                new Permission(CMD_BLACKLIST, "Manage the blacklist", PermissionDefault.FALSE),
                new Permission(CMD_OPWHITELIST, "Manage the operator whitelist", PermissionDefault.FALSE),
                new Permission(CMD_WHITELIST, "Manage the whitelist", PermissionDefault.FALSE),
                new Permission(CMD_MAINTENANCE, "Toggle maintenance mode", PermissionDefault.FALSE),
                new Permission(CMD_CHECK, "Check players", PermissionDefault.FALSE),
                new Permission(CMD_AC, "View anti-cheat statistics", PermissionDefault.FALSE),
                new Permission(CMD_PROTECTION, "Manage protection blocks", PermissionDefault.FALSE),
                new Permission(CMD_STRUCTURES, "Manage structures", PermissionDefault.FALSE),
                new Permission(CMD_STRUCTURES_DFC, "Manage DFC structures", PermissionDefault.FALSE),
                new Permission(CMD_STRUCTURES_MAGNET, "Manage magnet structures", PermissionDefault.FALSE),
                new Permission(CMD_MENU, "Open the admin menu", PermissionDefault.FALSE),
                new Permission(CMD_REPORT, "Report a player", PermissionDefault.FALSE),
                new Permission(CMD_REPORTS, "View reports", PermissionDefault.FALSE),
                new Permission(CMD_REPSTATUS, "Check your report status", PermissionDefault.FALSE),
                new Permission(CMD_MODREPORT, "File a mod report", PermissionDefault.FALSE),

                // ── Commands: economy and system ──
                new Permission(CMD_MONEY, "Manage player balances", PermissionDefault.FALSE),
                new Permission(CMD_AUTH, "Authentication commands", PermissionDefault.FALSE),
                new Permission(CMD_AUTH_FORCELOGIN, "Force a player to log in", PermissionDefault.FALSE),
                new Permission(CMD_AUTH_RESETAUTH, "Reset a player's authentication", PermissionDefault.FALSE),
                new Permission(CMD_AUTH_DELSESSION, "Delete a player's session", PermissionDefault.FALSE),
                new Permission(CMD_AUTH_CHGPASS, "Change a player's password", PermissionDefault.FALSE),
                new Permission(CMD_CODEPANE, "Use the code panel", PermissionDefault.FALSE),
                new Permission(CMD_CODEPANE_KEY, "Manage code panel keys", PermissionDefault.FALSE),
                new Permission(CMD_CODEPANE_KEY_ADD, "Add code panel keys", PermissionDefault.FALSE),
                new Permission(CMD_CODEPANE_KEY_LIST, "List code panel keys", PermissionDefault.FALSE),
                new Permission(CMD_CODEPANE_KEY_REMOVE, "Remove code panel keys", PermissionDefault.FALSE),
                new Permission(CMD_CODEPANE_KEY_MODIFY, "Modify code panel keys", PermissionDefault.FALSE),
                new Permission(CMD_CHGDIM, "Teleport between dimensions", PermissionDefault.FALSE),
                new Permission(CMD_CHGDIM_ALL, "Teleport to any dimension world", PermissionDefault.FALSE),
                new Permission(CMD_ITEM, "Manage items", PermissionDefault.FALSE),
                new Permission(CMD_ENCHANT, "Enchant items", PermissionDefault.FALSE),
                new Permission(CMD_REACTOR, "Manage the reactor", PermissionDefault.FALSE),
                new Permission(CMD_SETRAD, "Set radiation levels", PermissionDefault.FALSE),
                new Permission(CMD_METEOR_SPAWN, "Spawn meteors", PermissionDefault.FALSE),
                new Permission(CMD_REDSTONE_LIST, "List blocked redstone chunks", PermissionDefault.FALSE),
                new Permission(CMD_POWER_OFF, "Request server shutdown", PermissionDefault.FALSE),
                new Permission(CMD_POWER_REBOOT, "Request server restart", PermissionDefault.FALSE),
                new Permission(CMD_POWER_UNDO, "Cancel a pending shutdown or restart", PermissionDefault.FALSE),
                new Permission(CMD_SUDO, "Use sudo mode commands", PermissionDefault.FALSE),
                new Permission(CMD_UUID, "Look up player UUIDs", PermissionDefault.FALSE),
                new Permission(CMD_CILIST, "List custom items", PermissionDefault.FALSE),
                new Permission(CMD_VOTE, "Use vote commands", PermissionDefault.FALSE),
                new Permission(CMD_VOTE_CREATE, "Create votes", PermissionDefault.FALSE),
                new Permission(CMD_VOTE_CHANGE, "Change votes", PermissionDefault.FALSE),
                new Permission(CMD_VOTE_CHANGE_OTHER, "Change other players' votes", PermissionDefault.FALSE),
                new Permission(CMD_VOTE_DELETE_OTHER, "Delete other players' votes", PermissionDefault.FALSE),
                new Permission(CMD_VOTE_STATS, "View vote statistics", PermissionDefault.FALSE),
                new Permission(CMD_VOTE_BYPASS, "Bypass vote restrictions", PermissionDefault.FALSE),
                new Permission(CMD_PDC, "Manage PersistentDataContainer on items (add/modify/remove/list/clear/container)", PermissionDefault.FALSE),
                new Permission(CMD_ITEMNBT, "Edit item data components / NBT (name, lore, hide flags, unbreakable, equipment, food, repair cost)", PermissionDefault.FALSE),
                new Permission(CMD_CLAN, "Use the clan system (create/disband/manage/home/requests)", PermissionDefault.TRUE),
                new Permission(CMD_CLAN_REMOVE, "Remove any clan by name (admin)", PermissionDefault.FALSE),
                new Permission(CMD_CLAN_HOME_BYPASS, "Bypass the clan home teleport cooldown", PermissionDefault.FALSE),

                // ── Features and modifiers ──
                new Permission(UI_ALERTS, "Receive server alerts", PermissionDefault.FALSE),
                new Permission(UI_AUTOCRAFT, "Use auto-crafting", PermissionDefault.FALSE),
                new Permission(UI_SHOW_BRAND, "Show the server brand", PermissionDefault.FALSE),
                new Permission(UI_SUDO, "Protected by sudo mode (dangerous commands require sudo)", PermissionDefault.FALSE),
                new Permission(UI_ANTICHEAT_BYPASS, "Bypass anti-cheat checks", PermissionDefault.FALSE),
                new Permission(UI_ANTICHEAT_NOTIFY, "Receive anti-cheat notifications", PermissionDefault.FALSE),
                new Permission(UI_CHAT_CUSTOM_BYPASS, "Bypass custom chat formatting", PermissionDefault.FALSE),
                new Permission(UI_CHAT_FILTER_BYPASS, "Bypass the chat filter", PermissionDefault.FALSE),
                new Permission(UI_GMPROTECT_BYPASS, "Bypass game-mode protection", PermissionDefault.FALSE),
                new Permission(UI_CREATIVE_BYPASS, "Bypass creative item validation", PermissionDefault.FALSE),
                new Permission(UI_PACKETGUARD_BYPASS, "Bypass packet guard", PermissionDefault.FALSE),
                new Permission(UI_ENCHANT_AOE_BYPASS, "Bypass AOE enchantment restrictions", PermissionDefault.FALSE),
                new Permission(UI_OVERLOAD_LOGS, "Receive overload logs", PermissionDefault.FALSE),
                new Permission(UI_PUNISH_NOTIFY, "Receive punish notifications", PermissionDefault.FALSE),
                new Permission(UI_PROXY_SERVER, "Access proxy server features", PermissionDefault.FALSE)
        );

        int registered = 0;
        for (Permission permission : permissions) {
            if (pm.getPermission(permission.getName()) == null) {
                pm.addPermission(permission);
                registered++;
            }
        }
        ConsoleLogger.info("[Permissions] Registered " + registered + "/" + permissions.size()
                + " permissions (idempotent).");
    }
}
