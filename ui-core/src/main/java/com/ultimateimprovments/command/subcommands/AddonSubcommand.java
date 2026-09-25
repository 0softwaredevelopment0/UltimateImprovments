package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.addon.AddonEntry;
import com.ultimateimprovments.addon.AddonRegistry;
import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.command.SubCommand;
import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.core.Permissions;
import com.ultimateimprovments.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /ui addon — manage UltimateImprovments addons (open-ended set, never a fixed count).
 * <p>
 * Subcommands:
 * <ul>
 *   <li>{@code /ui addon} / {@code /ui addon list [page]} — paginated list (10/page),
 *       header shows loaded count + total load errors, never "X/11"</li>
 *   <li>{@code /ui addon status <addon>} — name, description, version, modules
 *       (loaded/total/failed) and the load errors</li>
 *   <li>{@code /ui addon enable|disable|restart <addon>} — real onEnable/onDisable
 *       with a confirmation step (some plugins crash on hot disable/enable)</li>
 * </ul>
 * There is no fixed addon set: any third-party jar with
 * {@code addon-for: UI-Core} in plugin.yml appears here automatically.
 */
public class AddonSubcommand implements SubCommand {

    /** Rows per list page. */
    private static final int PAGE_SIZE = 10;

    /** Confirmation timeout in ms. */
    private static final long CONFIRM_TIMEOUT_MS = 30_000L;

    private record Pending(String addon, String action, long at) {}

    /** sender UUID → pending lifecycle action (console = sentinel UUID). */
    private static final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (sender instanceof org.bukkit.entity.Player player
                && !player.hasPermission(Permissions.CMD_ADDONS)) {
            CommandErrors.noPermission(player);
            return true;
        }

        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "list";

        switch (sub) {
            case "list" -> { return showList(sender, args.length >= 3 ? args[2] : null); }
            case "status" -> { return showStatus(sender, args); }
            case "enable", "disable", "restart" -> { return lifecycle(sender, args, sub); }
            case "confirm" -> { return confirm(sender); }
            case "cancel" -> {
                pending.remove(senderUuid(sender));
                sender.sendMessage(msg("addon.action_cancelled",
                        "<green>✔</green> <white>Action cancelled.</white>"));
                return true;
            }
            default -> {
                sender.sendMessage(msg("addon.unknown_subcommand",
                        "<red>❌ Unknown subcommand. Use: list, status, enable, disable, restart.</red>"));
                return true;
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // LIST (paginated, open-ended)
    // ══════════════════════════════════════════════════════════════════

    private boolean showList(CommandSender sender, String pageArg) {
        if (!AddonRegistry.isDiscovered()) {
            AddonRegistry.discover();
        }
        var addons = AddonRegistry.getAddons();

        long loaded = addons.stream().filter(AddonEntry::isInstalled).count();
        int errors = addons.stream().mapToInt(AddonEntry::getErrorCount).sum();

        sender.sendMessage(msg("addon.list_header",
                "<gold>════ <white>UltimateImprovments addons</white> <gold>════"));

        if (errors > 0) {
            sender.sendMessage(msg("addon.list_summary_errors",
                    "<gray>Loaded: </gray><green>%loaded%</green><gray> — errors: </gray><red>%errors%</red>"
                            + "<gray> (see /ui addon status</gray>",
                    "%loaded%", String.valueOf(loaded),
                    "%errors%", String.valueOf(errors)));
        } else {
            sender.sendMessage(msg("addon.list_summary",
                    "<gray>Loaded: </gray><green>%loaded%</green><gray> — no load errors</gray>",
                    "%loaded%", String.valueOf(loaded)));
        }

        if (addons.isEmpty()) {
            sender.sendMessage(msg("addon.list_empty",
                    "<yellow>No addons found. Drop a jar with</yellow> <white>addon-for UI-Core</white>"
                            + "<yellow> in its plugin.yml into the plugins folder.</yellow>"));
            return true;
        }

        int totalPages = Math.max(1, (addons.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = 1;
        if (pageArg != null) {
            try {
                page = Math.max(1, Math.min(Integer.parseInt(pageArg), totalPages));
            } catch (NumberFormatException ignored) {
            }
        }

        int from = (page - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, addons.size());

        for (int i = from; i < to; i++) {
            sender.sendMessage(MessageUtil.parse(renderListRow(addons.get(i))));
        }

        if (totalPages > 1) {
            sender.sendMessage(msg("addon.list_page_footer",
                    "<dark_gray>Page %page%/%pages% — /ui addon list <page></dark_gray>",
                    "%page%", String.valueOf(page),
                    "%pages%", String.valueOf(totalPages)));
        }
        return true;
    }

    private static String renderListRow(AddonEntry entry) {
        Plugin p = Bukkit.getPluginManager().getPlugin(entry.getPluginName());
        boolean enabled = entry.isInstalled() && p != null && p.isEnabled();
        int failed = entry.getModulesFailed();
        int errors = entry.getErrorCount();

        String icon;
        if (!entry.isInstalled()) {
            icon = msgStr("addon.row_missing", "<dark_red>✖</dark_red>");
        } else if (errors > 0) {
            icon = msgStr("addon.row_errors", "<yellow>⚠</yellow>");
        } else if (enabled) {
            icon = msgStr("addon.row_ok", "<green>✔</green>");
        } else {
            icon = msgStr("addon.row_disabled", "<gray>○</gray>");
        }

        StringBuilder row = new StringBuilder(icon).append(" <white>")
                .append(entry.getPluginName()).append("</white>");
        if (p != null) {
            row.append(" <dark_gray>v").append(p.getDescription().getVersion()).append("</dark_gray>");
        }
        if (failed > 0) {
            row.append(" <red>").append(failed).append(" module(s) failed</red>");
        } else if (errors > 0) {
            row.append(" <yellow>").append(errors).append(" error(s)</yellow>");
        }
        if (entry.isManuallyDisabled()) {
            row.append(" <dark_gray>[disabled]</dark_gray>");
        }
        return row.toString();
    }

    // ══════════════════════════════════════════════════════════════════
    // STATUS
    // ══════════════════════════════════════════════════════════════════

    private boolean showStatus(CommandSender sender, String[] args) {
        String name = args.length >= 3 ? args[2] : null;
        if (name == null) {
            sender.sendMessage(msg("addon.status_usage",
                    "<red>❌ Usage: </red><white>/ui addon status <addon></white>"));
            return true;
        }
        Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
        AddonEntry entry = AddonRegistry.getEntry(name);
        if (plugin == null && entry == null) {
            sender.sendMessage(msg("addon.not_found",
                    "<red>❌ Addon not found: </red><white>%addon%</white>",
                    "%addon%", name));
            return true;
        }
        if (plugin == null) {
            sender.sendMessage(msg("addon.status_not_installed",
                    "<red>✖ %addon%</red> <gray>— the jar is registered but not loaded.</gray>",
                    "%addon%", entry.getPluginName()));
            return true;
        }
        var desc = plugin.getDescription();
        AddonEntry e = entry != null ? entry : AddonEntry.loaded(plugin);

        sender.sendMessage(msg("addon.status_header",
                "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));
        sender.sendMessage(msg("addon.status_name",
                "<gold>🧩 %name%</gold> <dark_gray>v%version%</dark_gray>",
                "%name%", plugin.getName(),
                "%version%", desc.getVersion()));
        sender.sendMessage(row("State", plugin.isEnabled()
                ? msgStr("addon.state_enabled", "<green>enabled</green>")
                : msgStr("addon.state_disabled", "<red>disabled</red>")));
        sender.sendMessage(row("Description",
                desc.getDescription() != null ? desc.getDescription() : "—"));
        sender.sendMessage(row("Version", desc.getVersion()));
        sender.sendMessage(row("Authors",
                desc.getAuthors().isEmpty() ? "—" : String.join(", ", desc.getAuthors())));

        int failed = e.getModulesFailed();
        String modules = e.getModulesTotal() > 0
                ? "<green>" + e.getModulesLoaded() + "</green><gray>/</gray>" + e.getModulesTotal()
                : msgStr("addon.modules_unknown", "<dark_gray>n/a (no module stats reported)</dark_gray>");
        if (failed > 0) {
            modules += " <red>(" + failed + " failed)</red>";
        }
        sender.sendMessage(msg("addon.status_modules",
                "<gray>Modules:</gray> %value%",
                "%value%", modules));

        if (!e.getLoadErrors().isEmpty()) {
            sender.sendMessage(msg("addon.status_errors_header",
                    "<red>Load errors (%count%):</red>",
                    "%count%", String.valueOf(e.getLoadErrors().size())));
            for (String err : e.getLoadErrors()) {
                sender.sendMessage(msg("addon.status_error_row",
                        "  <dark_red>•</dark_red> <red>%error%</red>",
                        "%error%", err));
            }
        } else {
            sender.sendMessage(msg("addon.status_no_errors", "<green>No load errors.</green>"));
        }
        sender.sendMessage(msg("addon.status_header",
                "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));
        return true;
    }

    private static Component row(String label, String value) {
        return MessageUtil.parse(msgStr("addon.status_row", "<gray>%label%:</gray> %value%")
                .replace("%label%", label)
                .replace("%value%", value));
    }

    // ══════════════════════════════════════════════════════════════════
    // LIFECYCLE: enable / disable / restart with confirmation
    // ══════════════════════════════════════════════════════════════════

    private boolean lifecycle(CommandSender sender, String[] args, String action) {
        String name = args.length >= 3 ? args[2] : null;
        if (name == null) {
            sender.sendMessage(msg("addon.action_usage",
                    "<red>❌ Usage: </red><white>/ui addon %action% <addon></white>",
                    "%action%", action));
            return true;
        }
        Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
        if (plugin == null) {
            sender.sendMessage(msg("addon.not_found",
                    "<red>❌ Addon not found: </red><white>%addon%</white>",
                    "%addon%", name));
            return true;
        }
        if (plugin.getName().equalsIgnoreCase("UI-Core")) {
            sender.sendMessage(msg("addon.core_protected",
                    "<red>❌ The core cannot be managed here. Use</red> <white>/ui reload</white><red>.</red>"));
            return true;
        }

        boolean isEnabled = plugin.isEnabled();
        if (action.equals("enable") && isEnabled) {
            sender.sendMessage(msg("addon.already_enabled",
                    "<yellow>⚠ %addon% is already enabled.</yellow>",
                    "%addon%", plugin.getName()));
            return true;
        }
        if (action.equals("disable") && !isEnabled) {
            sender.sendMessage(msg("addon.already_disabled",
                    "<yellow>⚠ %addon% is already disabled.</yellow>",
                    "%addon%", plugin.getName()));
            return true;
        }

        pending.put(senderUuid(sender), new Pending(plugin.getName(), action, System.currentTimeMillis()));
        sender.sendMessage(MessageUtil.parse(""));
        sender.sendMessage(msg("addon.confirm_header",
                "<yellow>⚠</yellow> <red>You are about to </red><yellow>%action%</yellow> <red>the addon:</red>",
                "%action%", action.toUpperCase(Locale.ROOT)));
        sender.sendMessage(msg("addon.confirm_row",
                "  <white>%addon%</white> <dark_gray>v%version%</dark_gray>",
                "%addon%", plugin.getName(),
                "%version%", plugin.getDescription().getVersion()));
        sender.sendMessage(msg("addon.confirm_warning",
                "  <red>Hot disable/enable may crash the addon or leave stale listeners."
                        + " Only proceed if you know what you are doing.</red>"));
        sender.sendMessage(MessageUtil.parse(""));
        sender.sendMessage(MessageUtil.parse(
                "<click:run_command:/ui addon confirm><dark_green>[</dark_green><green>✔ Confirm</green><dark_green>]</dark_green></click>"
                        + " <dark_gray>|</dark_gray> "
                        + "<click:run_command:/ui addon cancel><dark_red>[</dark_red><red>✖ Cancel</red><dark_red>]</dark_red></click>"));
        sender.sendMessage(MessageUtil.parse(""));
        return true;
    }

    private boolean confirm(CommandSender sender) {
        UUID uuid = senderUuid(sender);
        Pending p = pending.remove(uuid);
        if (p == null) {
            sender.sendMessage(msg("addon.no_pending", "<red>❌ No pending addon action.</red>"));
            return true;
        }
        if (System.currentTimeMillis() - p.at() > CONFIRM_TIMEOUT_MS) {
            sender.sendMessage(msg("addon.confirm_expired",
                    "<red>❌ Confirmation expired — run the command again.</red>"));
            return true;
        }

        Plugin plugin = Bukkit.getPluginManager().getPlugin(p.addon());
        if (plugin == null) {
            sender.sendMessage(msg("addon.not_found",
                    "<red>❌ Addon not found: </red><white>%addon%</white>",
                    "%addon%", p.addon()));
            return true;
        }

        AddonEntry entry = AddonRegistry.getEntry(p.addon());
        if (entry != null) entry.clearLoadErrors();

        try {
            switch (p.action()) {
                case "enable" -> {
                    Bukkit.getPluginManager().enablePlugin(plugin);
                    reportPostEnable(sender, plugin, entry, "enabled");
                }
                case "disable" -> {
                    Bukkit.getPluginManager().disablePlugin(plugin);
                    if (entry != null) entry.setManuallyDisabled(true);
                    sender.sendMessage(msg("addon.action_done",
                            "<green>✔</green> <white>Addon </white><yellow>%addon%</yellow> <white>disabled.</white>",
                            "%addon%", plugin.getName()));
                }
                case "restart" -> {
                    Bukkit.getPluginManager().disablePlugin(plugin);
                    Plugin again = Bukkit.getPluginManager().getPlugin(p.addon());
                    if (again != null) Bukkit.getPluginManager().enablePlugin(again);
                    reportPostEnable(sender, Bukkit.getPluginManager().getPlugin(p.addon()),
                            entry, "restarted");
                }
                default -> {
                }
            }
        } catch (Throwable t) {
            if (entry != null) entry.reportLoadError("lifecycle: " + t.getMessage());
            sender.sendMessage(msg("addon.action_failed",
                    "<red>❌ Failed to %action% %addon%: %error%</red>",
                    "%action%", p.action(),
                    "%addon%", p.addon(),
                    "%error%", String.valueOf(t.getMessage())));
        }
        return true;
    }

    /** After enable/restart — verify state and surface fresh load errors. */
    private void reportPostEnable(CommandSender sender, Plugin plugin, AddonEntry entry, String verb) {
        if (plugin == null) {
            sender.sendMessage(msg("addon.action_failed",
                    "<red>❌ Failed to %action% %addon%: plugin vanished</red>",
                    "%action%", verb, "%addon%", "—"));
            return;
        }
        boolean ok = plugin.isEnabled();
        int errors = entry != null ? entry.getErrorCount() : 0;
        if (ok && errors == 0) {
            sender.sendMessage(msg("addon.action_done",
                    "<green>✔</green> <white>Addon </white><yellow>%addon%</yellow> <white>" + verb + ".</white>",
                    "%addon%", plugin.getName()));
        } else if (ok) {
            sender.sendMessage(msg("addon.action_done_with_errors",
                    "<yellow>⚠</yellow> <white>Addon </white><yellow>%addon%</yellow> <white>" + verb
                            + ", but</white> <red>%errors% load error(s)</red><white> — see /ui addon status %addon%</white>",
                    "%addon%", plugin.getName(),
                    "%errors%", String.valueOf(errors)));
        } else {
            sender.sendMessage(msg("addon.action_not_enabled",
                    "<red>⚠ %addon% could not be enabled! Check the console.</red>",
                    "%addon%", plugin.getName()));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // TAB COMPLETE
    // ══════════════════════════════════════════════════════════════════

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return List.of("list", "status", "enable", "disable", "restart");
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("list")) {
            var addons = AddonRegistry.getAddons();
            int pages = Math.max(1, (addons.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            List<String> pagesList = new ArrayList<>();
            for (int i = 1; i <= pages; i++) pagesList.add(String.valueOf(i));
            return pagesList;
        }
        if (args.length == 3) {
            // every known addon (installed or not)
            List<String> names = new ArrayList<>();
            for (AddonEntry e : AddonRegistry.getAddons()) names.add(e.getPluginName());
            return names;
        }
        return List.of();
    }

    // ══════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════

    private static UUID senderUuid(CommandSender sender) {
        return sender instanceof org.bukkit.entity.Player player
                ? player.getUniqueId()
                : new UUID(0, 0);
    }

    /**
     * Localized + parsed message with %placeholder% replacement done on the
     * raw string BEFORE MiniMessage parsing (replacements are key/value pairs).
     */
    private static Component msg(String key, String def, String... replacements) {
        String raw = msgStr(key, def);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            raw = raw.replace(replacements[i], replacements[i + 1]);
        }
        return MessageUtil.parse(raw);
    }

    /** Raw localized MiniMessage string (no parsing, no replacement). */
    private static String msgStr(String key, String def) {
        return MessagesManager.getString(key, def);
    }
}
