package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.command.SubCommand;
import com.ultimateimprovments.core.Permissions;
import com.ultimateimprovments.mechanics.features.kits.Kit;
import com.ultimateimprovments.mechanics.features.kits.KitDialogs;
import com.ultimateimprovments.mechanics.features.kits.KitService;
import com.ultimateimprovments.mechanics.features.kits.KitStore;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /ui kit — kit (item set) management.
 * <p>
 * Two input styles:
 * <ul>
 *   <li><b>Players</b> — every management action opens a dialog
 *       ({@link KitDialogs}); the command itself only resolves the sub-action,
 *       the {@code -f} flag and the permission gate. {@code list} works as a
 *       plain command for players as well.</li>
 *   <li><b>Non-player senders (console, command blocks, …)</b> — no dialogs and
 *       no inventory: the same actions take plain arguments and flags (see
 *       {@code executeConsole}).</li>
 * </ul>
 * Console syntax:
 * <pre>
 *   /ui kit list [page]
 *   /ui kit give &lt;kit&gt; &lt;player&gt; [-f]
 *   /ui kit create &lt;name&gt; [description...]
 *   /ui kit rename &lt;old&gt; &lt;new&gt;
 *   /ui kit delete &lt;kit&gt; [-y]
 *   /ui kit import &lt;kit&gt; &lt;player&gt;
 *   /ui kit export &lt;kit&gt; &lt;player&gt; [-f]
 *   /ui kit cooldown &lt;kit&gt; &lt;seconds&gt;
 *   /ui kit globalcd &lt;kit&gt; &lt;seconds&gt;
 *   /ui kit resetcd &lt;player&gt; -c
 *   /ui kit resetglobalcd &lt;kit&gt; -c
 * </pre>
 * Permission model:
 * <ul>
 *   <li>{@code ui.command.kit} — base access (list); missing → error 002 before
 *       any dialog opens;</li>
 *   <li>{@code ui.command.kit.<action>} — per action (give/create/rename/delete/
 *       import/export/cooldown/globalcd/resetcd/resetglobalcd), also error 002
 *       before the dialog opens;</li>
 *   <li>{@code ui.command.kit.give.other} — required when the recipient is not
 *       the sender himself;</li>
 *   <li>{@code ui.command.kit.give.force} — the {@code -f} flag of give/export;</li>
 *   <li>{@code ui.command.kit.cooldown.bypass} — the player ignores per-player
 *       kit cooldowns when receiving a kit;</li>
 *   <li>{@code ui.command.kit.globalcd.bypass} — the player ignores the
 *       server-wide kit cooldown when receiving a kit.</li>
 * </ul>
 */
public final class KitSubcommand implements SubCommand {

    private static final List<String> ACTIONS = List.of(
            "list", "give", "create", "rename", "delete", "import", "export",
            "cooldown", "globalcd", "resetcd", "resetglobalcd");

    @Override
    public String getName() {
        return "kit";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!KitStore.isReady()) {
            CommandErrors.moduleDisabled(sender, "kits");
            return true;
        }
        if (!sender.hasPermission(Permissions.CMD_KIT)) {
            CommandErrors.noPermission(sender, Permissions.CMD_KIT);
            return true;
        }

        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "list";

        // -f / --force and -y / --yes may appear anywhere after the action;
        // everything else is a plain argument (the page number for `list`, the
        // action parameters for the console form).
        boolean force = false;
        boolean confirmed = false;
        List<String> rest = new ArrayList<>();
        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            if (isFlag(arg, "-f", "--force")) {
                force = true;
            } else if (isFlag(arg, "-y", "--yes", "-c", "--confirm")) {
                confirmed = true;
            } else {
                rest.add(arg);
            }
        }

        Player player = sender instanceof Player p ? p : null;

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
                return KitService.list(sender, page);
            }
            case "give" -> {
                if (!requirePermission(sender, Permissions.CMD_KIT_GIVE)) return true;
                if (force && !requirePermission(sender, Permissions.CMD_KIT_GIVE_FORCE)) return true;
                if (player != null) {
                    KitDialogs.openGive(player, force);
                    return true;
                }
                if (rest.isEmpty()) {
                    CommandErrors.syntaxError(sender);
                    return true;
                }
                // The recipient is mandatory for console; the service reports
                // error 033 when it is missing and 026 when it is offline.
                return KitService.give(sender, rest.get(0),
                        rest.size() > 1 ? rest.get(1) : "", force);
            }
            case "create" -> {
                if (!requirePermission(sender, Permissions.CMD_KIT_CREATE)) return true;
                if (player != null) {
                    KitDialogs.openCreate(player);
                    return true;
                }
                if (rest.isEmpty()) {
                    CommandErrors.syntaxError(sender);
                    return true;
                }
                String description = rest.size() > 1
                        ? String.join(" ", rest.subList(1, rest.size()))
                        : "";
                return KitService.create(sender, rest.get(0), description);
            }
            case "rename" -> {
                if (!requirePermission(sender, Permissions.CMD_KIT_RENAME)) return true;
                if (player != null) {
                    KitDialogs.openRename(player);
                    return true;
                }
                if (rest.size() < 2) {
                    CommandErrors.syntaxError(sender);
                    return true;
                }
                return KitService.rename(sender, rest.get(0), rest.get(1));
            }
            case "delete" -> {
                if (!requirePermission(sender, Permissions.CMD_KIT_DELETE)) return true;
                if (player != null) {
                    KitDialogs.openDelete(player);
                    return true;
                }
                if (rest.isEmpty()) {
                    CommandErrors.syntaxError(sender);
                    return true;
                }
                return KitService.delete(sender, rest.get(0), confirmed);
            }
            case "import" -> {
                if (!requirePermission(sender, Permissions.CMD_KIT_IMPORT)) return true;
                if (player != null) {
                    KitDialogs.openImport(player);
                    return true;
                }
                if (rest.isEmpty()) {
                    CommandErrors.syntaxError(sender);
                    return true;
                }
                return KitService.importFromHand(sender, rest.get(0),
                        rest.size() > 1 ? rest.get(1) : "");
            }
            case "export" -> {
                if (!requirePermission(sender, Permissions.CMD_KIT_EXPORT)) return true;
                if (force && !requirePermission(sender, Permissions.CMD_KIT_GIVE_FORCE)) return true;
                if (player != null) {
                    KitDialogs.openExport(player, force);
                    return true;
                }
                if (rest.isEmpty()) {
                    CommandErrors.syntaxError(sender);
                    return true;
                }
                return KitService.exportToHand(sender, rest.get(0),
                        rest.size() > 1 ? rest.get(1) : "", force);
            }
            case "cooldown" -> {
                if (!requirePermission(sender, Permissions.CMD_KIT_COOLDOWN)) return true;
                if (player != null) {
                    KitDialogs.openCooldown(player);
                    return true;
                }
                if (rest.size() < 2) {
                    CommandErrors.syntaxError(sender);
                    return true;
                }
                return KitService.setCooldown(sender, rest.get(0), rest.get(1));
            }
            case "globalcd" -> {
                if (!requirePermission(sender, Permissions.CMD_KIT_GLOBALCD)) return true;
                if (player != null) {
                    KitDialogs.openGlobalCooldown(player);
                    return true;
                }
                if (rest.size() < 2) {
                    CommandErrors.syntaxError(sender);
                    return true;
                }
                return KitService.setGlobalCooldown(sender, rest.get(0), rest.get(1));
            }
            case "resetcd" -> {
                if (!requirePermission(sender, Permissions.CMD_KIT_RESETCD)) return true;
                if (player != null) {
                    KitDialogs.openResetCooldown(player);
                    return true;
                }
                if (rest.isEmpty()) {
                    CommandErrors.syntaxError(sender);
                    return true;
                }
                return KitService.resetCooldowns(sender, rest.get(0), confirmed);
            }
            case "resetglobalcd" -> {
                if (!requirePermission(sender, Permissions.CMD_KIT_RESETGLOBALCD)) return true;
                if (player != null) {
                    KitDialogs.openResetGlobalCooldown(player);
                    return true;
                }
                if (rest.isEmpty()) {
                    CommandErrors.syntaxError(sender);
                    return true;
                }
                return KitService.resetGlobalCooldown(sender, rest.get(0), confirmed);
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
            return filter(ACTIONS, args[1]);
        }
        if (args.length < 3) {
            // "" suppresses the registry's fallback to online-player names
            return List.of("");
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        String partial = args[args.length - 1].toLowerCase(Locale.ROOT);
        int position = args.length - 3;

        // Players use dialogs for every action except `list`, so plain command
        // arguments are only suggested to non-player senders.
        if (sender instanceof Player) {
            if (position == 0 && (action.equals("give") || action.equals("export"))
                    && "-f".startsWith(partial)) {
                return List.of("-f");
            }
            return List.of("");
        }

        List<String> out = new ArrayList<>();
        switch (action) {
            case "list" -> {
                if (position == 0) out.addAll(filter(List.of("1", "2", "3", "4", "5"), partial));
            }
            case "give", "export" -> {
                if (position == 0) {
                    out.addAll(kitKeys(partial));
                } else if (position == 1) {
                    out.addAll(playerNames(partial));
                } else {
                    out.addAll(filter(List.of("-f"), partial));
                }
            }
            case "import" -> {
                if (position == 0) {
                    out.addAll(kitKeys(partial));
                } else if (position == 1) {
                    out.addAll(playerNames(partial));
                }
            }
            case "rename" -> {
                if (position == 0) out.addAll(kitKeys(partial));
            }
            case "delete" -> {
                if (position == 0) {
                    out.addAll(kitKeys(partial));
                } else {
                    out.addAll(filter(List.of("-y"), partial));
                }
            }
            case "cooldown", "globalcd" -> {
                if (position == 0) {
                    out.addAll(kitKeys(partial));
                } else if (position == 1) {
                    out.addAll(filter(List.of("0", "60", "300", "3600", "86400"), partial));
                }
            }
            case "resetcd" -> {
                if (position == 0) {
                    out.addAll(playerNames(partial));
                } else {
                    out.addAll(filter(List.of("-c"), partial));
                }
            }
            case "resetglobalcd" -> {
                if (position == 0) {
                    out.addAll(kitKeys(partial));
                } else {
                    out.addAll(filter(List.of("-c"), partial));
                }
            }
            default -> {
                // unknown action — nothing to suggest
            }
        }
        return out.isEmpty() ? List.of("") : out;
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /** True when the argument is one of the given flag spellings. */
    private static boolean isFlag(String arg, String... names) {
        for (String name : names) {
            if (arg.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    /** Case-insensitive prefix filter (keeps the original spelling). */
    private static List<String> filter(List<String> values, String partial) {
        if (partial == null || partial.isEmpty()) return new ArrayList<>(values);
        String lower = partial.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) out.add(value);
        }
        return out;
    }

    /** Plain (tag-free) kit keys for tab completion. */
    private static List<String> kitKeys(String partial) {
        List<String> keys = new ArrayList<>();
        for (Kit kit : KitStore.all()) keys.add(kit.key());
        return filter(keys, partial);
    }

    /** Online player names for tab completion. */
    private static List<String> playerNames(String partial) {
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) names.add(online.getName());
        return filter(names, partial);
    }

    /** Reports error 002 for the given node; false when the sender lacks it. */
    private static boolean requirePermission(CommandSender sender, String node) {
        if (sender.hasPermission(node)) return true;
        CommandErrors.noPermission(sender, node);
        return false;
    }
}
