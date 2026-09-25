package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /ui plugin &lt;restart|disable|enable|status&gt; &lt;name&gt; — manage other plugins.
 * <p>
 * The FIRST argument is the ACTION, the second is the plugin. Lifecycle actions
 * require a confirmation (hot disable/enable can crash third-party plugins).
 * {@code status} works for any plugin; UltimateImprovments family plugins should
 * use {@code /ui addon status} instead (there the module statistics live).
 * <p>
 * Usage:
 * <ul>
 *   <li>{@code /ui plugin status <name>} — full info: name, description, version,
 *       API version, authors, load type, loadBefore, depend, softdepend, libraries</li>
 *   <li>{@code /ui plugin enable|disable|restart <name>} — with confirmation</li>
 *   <li>{@code /ui plugin confirm|cancel}</li>
 * </ul>
 */
public final class PluginSubcommand {

    private PluginSubcommand() {}

    /** Permission required to use this command. */
    private static final String PERMISSION = "ui.command.plugin";

    /** Pending actions keyed by sender UUID (console = sentinel UUID). */
    private static final UUID CONSOLE_UUID = new UUID(0, 0);
    private static final Map<UUID, PendingAction> pendingActions = new ConcurrentHashMap<>();

    /** Timeout for pending confirmations (milliseconds). */
    private static final long TIMEOUT_MS = 30_000L;

    /** Periodic cleanup task reference, started lazily on first use. */
    private static BukkitTask cleanupTask;

    // ==========================================================================
    // PUBLIC ENTRY POINT
    // ==========================================================================

    public static boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            CommandErrors.noPermission(sender);
            return true;
        }

        // args[0] = "plugin"; args[1] = action; args[2] = plugin name
        if (args.length < 2) {
            usage(sender);
            return true;
        }

        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "confirm" -> handleConfirm(sender);
            case "cancel" -> handleCancel(sender);
            case "status" -> handleStatus(sender, args);
            case "enable", "disable", "restart" -> handleAction(sender, args);
            default -> {
                sender.sendMessage(MessageUtil.parse(
                        "<dark_red>❌</dark_red> <red>Invalid action: </red><white>" + args[1]
                                + "</white><gray>. Use enable, disable, restart or status.</gray>"));
                yield true;
            }
        };
    }

    // ==========================================================================
    // STATUS — full plugin.yml dump
    // ==========================================================================

    private static boolean handleStatus(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>Usage: </red><white>/ui plugin status <name></white>"));
            return true;
        }
        String pluginName = args[2];
        Plugin target = Bukkit.getPluginManager().getPlugin(pluginName);
        if (target == null) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>Plugin not found: </red><white>" + pluginName + "</white>"));
            return true;
        }
        if (target.getName().equalsIgnoreCase("UI-Core")
                || target.getName().startsWith("UI-")) {
            sender.sendMessage(MessageUtil.parse(
                    "  <yellow>ℹ UltimateImprovments family plugin — use</yellow> <white>/ui addon status "
                            + target.getName() + "</white> <yellow>for the module statistics.</yellow>"));
        }

        PluginDescriptionFile desc = target.getDescription();
        String state = target.isEnabled()
                ? "<green>● Enabled</green>"
                : "<red>● Disabled</red>";

        sender.sendMessage(MessageUtil.parse(""));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));
        sender.sendMessage(MessageUtil.parse(
                "  <gold>📦</gold> <yellow>" + target.getName() + "</yellow> "
                        + "<dark_gray>v</dark_gray><white>" + desc.getVersion() + "</white>"));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));
        sender.sendMessage(MessageUtil.parse("  <gray>State:</gray> " + state));
        sender.sendMessage(MessageUtil.parse("  <gray>Description:</gray> <white>"
                + (desc.getDescription() != null ? desc.getDescription() : "—") + "</white>"));
        sender.sendMessage(MessageUtil.parse("  <gray>Version:</gray> <white>" + desc.getVersion() + "</white>"));
        sender.sendMessage(MessageUtil.parse("  <gray>API version:</gray> " + orNone(desc.getAPIVersion())));
        sender.sendMessage(MessageUtil.parse("  <gray>Authors:</gray> " + orNone(
                desc.getAuthors().isEmpty() ? null : String.join(", ", desc.getAuthors()))));
        sender.sendMessage(MessageUtil.parse("  <gray>Load (ORDER):</gray> " + orNone(
                desc.getLoad() != null ? desc.getLoad().name() : null)));
        sender.sendMessage(MessageUtil.parse("  <gray>LoadBefore:</gray> " + orNone(
                desc.getLoadBefore().isEmpty() ? null : String.join(", ", desc.getLoadBefore()))));
        sender.sendMessage(MessageUtil.parse("  <gray>Depend:</gray> " + orNone(
                desc.getDepend().isEmpty() ? null : String.join(", ", desc.getDepend()))));
        sender.sendMessage(MessageUtil.parse("  <gray>SoftDepend:</gray> " + orNone(
                desc.getSoftDepend().isEmpty() ? null : String.join(", ", desc.getSoftDepend()))));
        sender.sendMessage(MessageUtil.parse("  <gray>Libraries:</gray> " + orNone(
                desc.getLibraries().isEmpty() ? null : String.join(", ", desc.getLibraries()))));
        sender.sendMessage(MessageUtil.parse("  <gray>Main class:</gray> " + orNone(desc.getMain())));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));
        sender.sendMessage(MessageUtil.parse(""));
        return true;
    }

    private static String orNone(String value) {
        return value == null || value.isBlank()
                ? "<gray>none</gray>"
                : "<white>" + value + "</white>";
    }

    // ==========================================================================
    // ACTION — enable/disable/restart with confirmation
    // ==========================================================================

    private static boolean handleAction(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>Usage: </red><white>/ui plugin " + args[1] + " <name></white>"));
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        String pluginName = args[2];

        Plugin target = Bukkit.getPluginManager().getPlugin(pluginName);
        if (target == null) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>Plugin not found: </red><white>" + pluginName + "</white>"));
            return true;
        }
        if (target.getName().equalsIgnoreCase("UI-Core")) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>Cannot manage the UI core here. Use </red><white>/ui reload</white><red> instead.</red>"));
            return true;
        }

        boolean isEnabled = target.isEnabled();
        if (action.equals("enable") && isEnabled) {
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>⚠</yellow> <white>" + target.getName() + "</white> <gray>is already enabled.</gray>"));
            return true;
        }
        if (action.equals("disable") && !isEnabled) {
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>⚠</yellow> <white>" + target.getName() + "</white> <gray>is already disabled.</gray>"));
            return true;
        }

        UUID uuid = sender instanceof Player player ? player.getUniqueId() : CONSOLE_UUID;
        pendingActions.put(uuid, new PendingAction(target.getName(), action, System.currentTimeMillis()));
        startCleanupTask();

        String actionDisplay = switch (action) {
            case "enable" -> "ENABLE";
            case "disable" -> "DISABLE";
            case "restart" -> "RESTART";
            default -> action.toUpperCase(Locale.ROOT);
        };

        sender.sendMessage(MessageUtil.parse(""));
        sender.sendMessage(MessageUtil.parse(
                "<dark_red>⚠</dark_red> <red>You are about to </red><yellow>" + actionDisplay
                        + "</yellow> <red>this plugin:</red>"));
        sender.sendMessage(MessageUtil.parse(
                "  <white>" + target.getName() + "</white> <dark_gray>(v"
                        + target.getDescription().getVersion() + ")</dark_gray>"));
        sender.sendMessage(MessageUtil.parse(
                "  <gray>State: </gray>" + (isEnabled ? "<green>ENABLED</green>" : "<red>DISABLED</red>")));
        sender.sendMessage(MessageUtil.parse(
                "  <gray>Description: </gray><white>" + target.getDescription().getDescription() + "</white>"));
        sender.sendMessage(MessageUtil.parse(""));
        sender.sendMessage(MessageUtil.parse(
                "<red>Disabling or restarting a plugin may crash it or leave stale listeners.</red>"));
        sender.sendMessage(MessageUtil.parse(
                "<red>Only proceed if you know what you are doing.</red>"));
        sender.sendMessage(MessageUtil.parse(""));
        sender.sendMessage(MessageUtil.parse(
                "<click:run_command:/ui plugin confirm><dark_green>[</dark_green><green>✔ Confirm</green><dark_green>]</dark_green></click>"
                        + " <dark_gray>|</dark_gray> "
                        + "<click:run_command:/ui plugin cancel><dark_red>[</dark_red><red>✖ Cancel</red><dark_red>]</dark_red></click>"));
        sender.sendMessage(MessageUtil.parse(""));

        ConsoleLogger.info("[PLUGIN] Pending " + action + " for " + target.getName() + " by " + sender.getName());
        return true;
    }

    // ==========================================================================
    // CONFIRM
    // ==========================================================================

    private static boolean handleConfirm(CommandSender sender) {
        UUID uuid = sender instanceof Player player ? player.getUniqueId() : CONSOLE_UUID;
        PendingAction pending = pendingActions.remove(uuid);

        if (pending == null) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>No pending plugin action. Use </red>"
                            + "<white>/ui plugin <enable|disable|restart> <name></white><red> first.</red>"));
            return true;
        }
        if (System.currentTimeMillis() - pending.createdAt() > TIMEOUT_MS) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>Confirmation timeout expired (30s). Run the command again.</red>"));
            return true;
        }

        Plugin target = Bukkit.getPluginManager().getPlugin(pending.pluginName());
        if (target == null) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>Plugin </red><white>" + pending.pluginName()
                            + "</white> <red>no longer exists!</red>"));
            return true;
        }

        try {
            switch (pending.action()) {
                case "enable" -> {
                    Bukkit.getPluginManager().enablePlugin(target);
                    sender.sendMessage(MessageUtil.parse(
                            "<green>✔</green> <white>Plugin </white><yellow>" + pending.pluginName()
                                    + "</yellow> <white>enabled.</white>"));
                    ConsoleLogger.info("[PLUGIN] " + sender.getName() + " enabled " + pending.pluginName());
                }
                case "disable" -> {
                    Bukkit.getPluginManager().disablePlugin(target);
                    sender.sendMessage(MessageUtil.parse(
                            "<green>✔</green> <white>Plugin </white><yellow>" + pending.pluginName()
                                    + "</yellow> <white>disabled.</white>"));
                    ConsoleLogger.info("[PLUGIN] " + sender.getName() + " disabled " + pending.pluginName());
                }
                case "restart" -> {
                    Bukkit.getPluginManager().disablePlugin(target);
                    Plugin again = Bukkit.getPluginManager().getPlugin(pending.pluginName());
                    if (again != null) Bukkit.getPluginManager().enablePlugin(again);
                    Plugin now = Bukkit.getPluginManager().getPlugin(pending.pluginName());
                    boolean success = now != null && now.isEnabled();
                    if (success) {
                        sender.sendMessage(MessageUtil.parse(
                                "<green>✔</green> <white>Plugin </white><yellow>" + pending.pluginName()
                                        + "</yellow> <white>restarted.</white>"));
                    } else {
                        sender.sendMessage(MessageUtil.parse(
                                "<dark_red>⚠</dark_red> <red>Plugin </red><white>" + pending.pluginName()
                                        + "</white> <red>was disabled but could not be re-enabled! Check console.</red>"));
                    }
                    ConsoleLogger.info("[PLUGIN] " + sender.getName() + " restarted "
                            + pending.pluginName() + " (success=" + success + ")");
                }
                default -> {
                }
            }
        } catch (Throwable t) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>Failed to </red><white>" + pending.action()
                            + "</white> <red>plugin: </red><white>" + t.getMessage() + "</white>"));
            ConsoleLogger.error("[PLUGIN] Failed to " + pending.action() + " "
                    + pending.pluginName() + ": " + t.getMessage());
        }
        return true;
    }

    // ==========================================================================
    // CANCEL
    // ==========================================================================

    private static boolean handleCancel(CommandSender sender) {
        UUID uuid = sender instanceof Player player ? player.getUniqueId() : CONSOLE_UUID;
        PendingAction removed = pendingActions.remove(uuid);

        if (removed == null) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>No pending plugin action to cancel.</red>"));
            return true;
        }
        sender.sendMessage(MessageUtil.parse(
                "<green>✔</green> <gray>Action cancelled: </gray><white>" + removed.action()
                        + " " + removed.pluginName() + "</white>"));
        ConsoleLogger.info("[PLUGIN] " + sender.getName() + " cancelled " + removed.action()
                + " for " + removed.pluginName());
        return true;
    }

    // ==========================================================================
    // UTILITY
    // ==========================================================================

    private static void usage(CommandSender sender) {
        sender.sendMessage(MessageUtil.parse(
                "<dark_red>❌</dark_red> <red>Usage: </red><white>/ui plugin <enable|disable|restart|status> <name></white>"));
        sender.sendMessage(MessageUtil.parse("  <gray>Examples:</gray>"));
        sender.sendMessage(MessageUtil.parse(
                "  <white>/ui plugin status WorldEdit</white> <gray>— full plugin info</gray>"));
        sender.sendMessage(MessageUtil.parse(
                "  <white>/ui plugin enable Essentials</white> <gray>— enable a plugin</gray>"));
        sender.sendMessage(MessageUtil.parse(
                "  <white>/ui plugin disable LuckPerms</white> <gray>— disable a plugin</gray>"));
        sender.sendMessage(MessageUtil.parse(
                "  <white>/ui plugin restart Vault</white> <gray>— restart a plugin (with confirmation)</gray>"));
    }

    /** Tab-complete for the LegacySubCommandAdapter consumer. */
    public static List<String> tabComplete(CommandSender sender, String[] args) {
        // args[0] = "plugin"
        if (args.length == 2) {
            return List.of("enable", "disable", "restart", "status");
        }
        if (args.length == 3) {
            if (args[1].equalsIgnoreCase("status")) {
                List<String> names = new ArrayList<>();
                for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                    if (!p.getName().equalsIgnoreCase("UI-Core")) names.add(p.getName());
                }
                return names;
            }
            // lifecycle: suggest only plugins in the opposite state
            String action = args[1].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                boolean enabled = p.isEnabled();
                boolean relevant = switch (action) {
                    case "enable" -> !enabled;
                    case "disable", "restart" -> enabled;
                    default -> false;
                };
                if (relevant && !p.getName().equalsIgnoreCase("UI-Core")) names.add(p.getName());
            }
            return names;
        }
        return List.of();
    }

    private static void startCleanupTask() {
        if (cleanupTask != null && !cleanupTask.isCancelled()) return;

        cleanupTask = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), () -> {
            long now = System.currentTimeMillis();

            pendingActions.entrySet().removeIf(entry -> {
                if (now - entry.getValue().createdAt() > TIMEOUT_MS) {
                    ConsoleLogger.info("[PLUGIN] Pending " + entry.getValue().action()
                            + " for " + entry.getValue().pluginName() + " expired (timeout)");
                    return true;
                }
                return false;
            });

            if (pendingActions.isEmpty() && cleanupTask != null) {
                cleanupTask.cancel();
                cleanupTask = null;
            }
        }, 100L, 100L);
    }

    /** Clears all pending actions. Called on plugin reload. */
    public static void clearPendingActions() {
        pendingActions.clear();
    }

    private record PendingAction(String pluginName, String action, long createdAt) {}
}
