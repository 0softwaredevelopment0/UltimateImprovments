package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.command.SubCommand;
import com.ultimateimprovments.core.Permissions;
import com.ultimateimprovments.mechanics.features.kits.KitDialogs;
import com.ultimateimprovments.mechanics.features.kits.KitService;
import com.ultimateimprovments.mechanics.features.kits.KitStore;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /ui kit — kit (item set) management.
 * <p>
 * Every management action opens a dialog ({@link KitDialogs}); the command itself
 * only resolves the sub-action, the {@code -f} flag and the permission gate.
 * Permission model:
 * <ul>
 *   <li>{@code ui.command.kit} — base access (list); missing → error 002 before
 *       any dialog opens;</li>
 *   <li>{@code ui.command.kit.<action>} — per action (give/create/rename/delete/
 *       import/export), also error 002 before the dialog opens;</li>
 *   <li>{@code ui.command.kit.give.other} — required when the recipient field is
 *       not empty (checked on submit, error 002 after the dialog closes);</li>
 *   <li>{@code ui.command.kit.give.force} — the {@code -f} flag of give/export.</li>
 * </ul>
 * Usage:
 * <pre>
 *   /ui kit [list] [page]
 *   /ui kit give [-f]
 *   /ui kit create
 *   /ui kit rename
 *   /ui kit delete
 *   /ui kit import
 *   /ui kit export [-f]
 * </pre>
 */
public final class KitSubcommand implements SubCommand {

    @Override
    public String getName() {
        return "kit";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            CommandErrors.playerOnly(sender);
            return true;
        }
        if (!KitStore.isReady()) {
            CommandErrors.moduleDisabled(player, "kits");
            return true;
        }
        if (!player.hasPermission(Permissions.CMD_KIT)) {
            CommandErrors.noPermission(player, Permissions.CMD_KIT);
            return true;
        }

        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "list";

        // -f / --force may appear anywhere after the action; everything else is
        // a plain argument (only `list` takes one — the page number).
        boolean force = false;
        List<String> rest = new ArrayList<>();
        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            if (arg.equalsIgnoreCase("-f") || arg.equalsIgnoreCase("--force")) {
                force = true;
            } else {
                rest.add(arg);
            }
        }

        switch (action) {
            case "list" -> {
                int page = 1;
                if (!rest.isEmpty()) {
                    try {
                        page = Math.max(1, Integer.parseInt(rest.get(0)));
                    } catch (NumberFormatException e) {
                        CommandErrors.syntaxError(sender);
                        return true;
                    }
                }
                return KitService.list(player, page);
            }
            case "give" -> {
                if (!requirePermission(player, Permissions.CMD_KIT_GIVE)) return true;
                if (force && !requirePermission(player, Permissions.CMD_KIT_GIVE_FORCE)) return true;
                KitDialogs.openGive(player, force);
                return true;
            }
            case "create" -> {
                if (!requirePermission(player, Permissions.CMD_KIT_CREATE)) return true;
                KitDialogs.openCreate(player);
                return true;
            }
            case "rename" -> {
                if (!requirePermission(player, Permissions.CMD_KIT_RENAME)) return true;
                KitDialogs.openRename(player);
                return true;
            }
            case "delete" -> {
                if (!requirePermission(player, Permissions.CMD_KIT_DELETE)) return true;
                KitDialogs.openDelete(player);
                return true;
            }
            case "import" -> {
                if (!requirePermission(player, Permissions.CMD_KIT_IMPORT)) return true;
                KitDialogs.openImport(player);
                return true;
            }
            case "export" -> {
                if (!requirePermission(player, Permissions.CMD_KIT_EXPORT)) return true;
                if (force && !requirePermission(player, Permissions.CMD_KIT_GIVE_FORCE)) return true;
                KitDialogs.openExport(player, force);
                return true;
            }
            default -> {
                CommandErrors.syntaxError(sender);
                return true;
            }
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            return List.of("list", "give", "create", "rename", "delete", "import", "export")
                    .stream().filter(s -> s.startsWith(partial)).toList();
        }
        if (args.length >= 3) {
            String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
            String partial = args[args.length - 1].toLowerCase(Locale.ROOT);
            if ((action.equals("give") || action.equals("export")) && "-f".startsWith(partial)) {
                return List.of("-f");
            }
            if (action.equals("list") && partial.isEmpty()) {
                return List.of("1", "2", "3");
            }
        }
        // "" suppresses the registry's fallback to online-player names
        return List.of("");
    }

    /** Reports error 002 for the given node; false when the player lacks it. */
    private static boolean requirePermission(Player player, String node) {
        if (player.hasPermission(node)) return true;
        CommandErrors.noPermission(player, node);
        return false;
    }
}
