package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /mp plugin &lt;name&gt; on|off|restart — управление другими плагинами.<br>
 * Показывает предупреждение с clickable кнопками подтверждения/отмены.
 * <p>
 * Использование:
 * <ul>
 *   <li>{@code /mp plugin <name> on} — включить плагин</li>
 *   <li>{@code /mp plugin <name> off} — выключить плагин</li>
 *   <li>{@code /mp plugin <name> restart} — перезагрузить плагин (disable + enable)</li>
 *   <li>{@code /mp plugin confirm} — подтвердить ожидающее действие</li>
 *   <li>{@code /mp plugin cancel} — отменить ожидающее действие</li>
 * </ul>
 * <p>
 * Требуется пермишен: {@code ui.command.plugin}.
 */
public final class PluginSubcommand {

    private PluginSubcommand() {}

    /** Permission required to use this command. */
    private static final String PERMISSION = "ui.command.plugin";

    /** Pending actions keyed by player UUID. Console uses a sentinel UUID. */
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
            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>You don't have permission to manage plugins!</red>"));
            return true;
        }

        if (args.length < 2) {
            usage(sender);
            return true;
        }

        return switch (args[1].toLowerCase()) {
            case "confirm" -> handleConfirm(sender);
            case "cancel"  -> handleCancel(sender);
            default        -> handlePluginAction(sender, args);
        };
    }

    // ==========================================================================
    // INITIAL ACTION: /mp plugin <name> <on|off|restart>
    // ==========================================================================

    private static boolean handlePluginAction(CommandSender sender, String[] args) {
        if (args.length < 3) {
            usage(sender);
            return true;
        }

        String pluginName = args[1];
        String action = args[2].toLowerCase();

        // Проверяем, существует ли такой плагин
        Plugin target = Bukkit.getPluginManager().getPlugin(pluginName);
        if (target == null) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>Plugin not found: </red><white>" + pluginName + "</white>"));
            return true;
        }

        // Info — read-only, доступно для любых плагинов включая UltimateImprovments
        if (action.equals("info")) {
            return handleInfo(sender, target);
        }

        if (!action.equals("on") && !action.equals("off") && !action.equals("restart")) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>Invalid action: </red><white>" + action
                    + "</white><gray>. Use on, off, restart, or info.</gray>"));
            return true;
        }

        // Не даём выключить свой же плагин
        if (target.getName().equals("UltimateImprovments")) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>Cannot manage UltimateImprovments itself. Use </red><white>/mp reload</white><red> instead.</red>"));
            return true;
        }

        // Проверяем, не в том ли уже состоянии плагин
        boolean isEnabled = target.isEnabled();
        if (action.equals("on") && isEnabled) {
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>⚠</yellow> <white>" + pluginName + "</white> <gray>is already enabled.</gray>"));
            return true;
        }
        if (action.equals("off") && !isEnabled) {
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>⚠</yellow> <white>" + pluginName + "</white> <gray>is already disabled.</gray>"));
            return true;
        }

        // Сохраняем ожидающее действие
        UUID uuid = sender instanceof Player player ? player.getUniqueId() : CONSOLE_UUID;
        pendingActions.put(uuid, new PendingAction(pluginName, action, System.currentTimeMillis()));

        // Запускаем фоновую очистку истёкших действий (только один раз)
        startCleanupTask();

        // Показываем предупреждение с clickable кнопками
        String actionDisplay = switch (action) {
            case "on" -> "ENABLE";
            case "off" -> "DISABLE";
            case "restart" -> "RESTART";
            default -> action.toUpperCase();
        };

        sender.sendMessage(MessageUtil.parse(""));
        sender.sendMessage(MessageUtil.parse(
                "<dark_red>⚠</dark_red> <red>WARNING: You are about to </red><yellow>" + actionDisplay
                + "</yellow> <red>this plugin:</red>"));
        sender.sendMessage(MessageUtil.parse(
                "  <white>" + pluginName + "</white> <dark_gray>(v" + target.getDescription().getVersion() + ")</dark_gray>"));
        sender.sendMessage(MessageUtil.parse(
                "  <gray>State: </gray>" + (isEnabled ? "<green>ENABLED</green>" : "<red>DISABLED</red>")));
        sender.sendMessage(MessageUtil.parse(
                "  <gray>Description: </gray><white>" + target.getDescription().getDescription() + "</white>"));
        sender.sendMessage(MessageUtil.parse(""));
        sender.sendMessage(MessageUtil.parse(
                "<red>Are you sure you want to proceed? Disabling or restarting a plugin may crash the server</red>"));
        sender.sendMessage(MessageUtil.parse(
                "<red>or cause data loss. Only proceed if you know what you are doing.</red>"));
        sender.sendMessage(MessageUtil.parse(""));
        sender.sendMessage(MessageUtil.parse(
                "<click:run_command:/mp plugin confirm><dark_green>[</dark_green><green>✔ Confirm</green><dark_green>]</dark_green></click>"
                + " <dark_gray>|</dark_gray> "
                + "<click:run_command:/mp plugin cancel><dark_red>[</dark_red><red>✖ Cancel</red><dark_red>]</dark_red></click>"));
        sender.sendMessage(MessageUtil.parse(""));

        ConsoleLogger.info("[PLUGIN] Pending " + action + " for " + pluginName + " by " + sender.getName());
        return true;
    }

    // ==========================================================================
    // INFO: /mp plugin <name> info
    // ==========================================================================

    private static boolean handleInfo(CommandSender sender, Plugin target) {
        var desc = target.getDescription();

        String authors = desc.getAuthors().isEmpty()
                ? "<gray>N/A</gray>"
                : "<white>" + String.join("</white><gray>, </gray><white>", desc.getAuthors()) + "</white>";

        String depend = desc.getDepend().isEmpty()
                ? "<gray>none</gray>"
                : "<white>" + String.join("</white><gray>, </gray><white>", desc.getDepend()) + "</white>";

        String softDepend = desc.getSoftDepend().isEmpty()
                ? "<gray>none</gray>"
                : "<white>" + String.join("</white><gray>, </gray><white>", desc.getSoftDepend()) + "</white>";

        String website = desc.getWebsite() != null
                ? "<click:open_url:" + desc.getWebsite() + "><aqua><u>" + desc.getWebsite() + "</u></aqua></click>"
                : "<gray>N/A</gray>";

        String apiVersion = desc.getAPIVersion() != null
                ? "<white>" + desc.getAPIVersion() + "</white>"
                : "<gray>N/A</gray>";

        String mainClass = desc.getMain() != null
                ? "<white>" + desc.getMain() + "</white>"
                : "<gray>N/A</gray>";

        sender.sendMessage(MessageUtil.parse(""));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));
        sender.sendMessage(MessageUtil.parse(
                "  <gold>📦</gold> <yellow>" + target.getName() + "</yellow> "
                + "<dark_gray>v</dark_gray><white>" + desc.getVersion() + "</white>"));
        sender.sendMessage(MessageUtil.parse(
                "  <dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));
        sender.sendMessage(MessageUtil.parse(
                "  <gray>State:</gray> " + (target.isEnabled() ? "<green>● Enabled</green>" : "<red>● Disabled</red>")));
        sender.sendMessage(MessageUtil.parse(
                "  <gray>Description:</gray> <white>"
                + (desc.getDescription() != null ? desc.getDescription() : "No description") + "</white>"));
        sender.sendMessage(MessageUtil.parse(
                "  <gray>Authors:</gray> " + authors));
        sender.sendMessage(MessageUtil.parse(
                "  <gray>Website:</gray> " + website));
        sender.sendMessage(MessageUtil.parse(""));
        sender.sendMessage(MessageUtil.parse(
                "  <dark_gray>──</dark_gray> <gray>Technical</gray> <dark_gray>────────────────────</dark_gray>"));
        sender.sendMessage(MessageUtil.parse(
                "  <gray>Main class:</gray> " + mainClass));
        sender.sendMessage(MessageUtil.parse(
                "  <gray>API version:</gray> " + apiVersion));
        sender.sendMessage(MessageUtil.parse(
                "  <gray>Depend:</gray> " + depend));
        sender.sendMessage(MessageUtil.parse(
                "  <gray>SoftDepend:</gray> " + softDepend));
        sender.sendMessage(MessageUtil.parse(
                "  <dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));
        sender.sendMessage(MessageUtil.parse(""));
        return true;
    }

    // ==========================================================================
    // CONFIRM: /mp plugin confirm
    // ==========================================================================

    private static boolean handleConfirm(CommandSender sender) {
        UUID uuid = sender instanceof Player player ? player.getUniqueId() : CONSOLE_UUID;
        PendingAction pending = pendingActions.remove(uuid);

        if (pending == null) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>No pending plugin action. Use </red><white>/mp plugin <name> <on|off|restart></white><red> first.</red>"));
            return true;
        }

        // Проверяем, не истекло ли время ожидания
        if (System.currentTimeMillis() - pending.createdAt > TIMEOUT_MS) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>Confirmation timeout expired (30s). Use </red><white>/mp plugin <name> <on|off|restart></white><red> again.</red>"));
            return true;
        }

        Plugin target = Bukkit.getPluginManager().getPlugin(pending.pluginName);
        if (target == null) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>Plugin </red><white>" + pending.pluginName + "</white> <red>no longer exists!</red>"));
            return true;
        }

        try {
            switch (pending.action) {
                case "on" -> {
                    Bukkit.getPluginManager().enablePlugin(target);
                    sender.sendMessage(MessageUtil.parse(
                            "<green>✔</green> <white>Plugin </white><yellow>" + pending.pluginName
                            + "</yellow> <white>enabled.</white>"));
                    ConsoleLogger.info("[PLUGIN] " + sender.getName() + " enabled " + pending.pluginName);
                }
                case "off" -> {
                    Bukkit.getPluginManager().disablePlugin(target);
                    sender.sendMessage(MessageUtil.parse(
                            "<green>✔</green> <white>Plugin </white><yellow>" + pending.pluginName
                            + "</yellow> <white>disabled.</white>"));
                    ConsoleLogger.info("[PLUGIN] " + sender.getName() + " disabled " + pending.pluginName);
                }
                case "restart" -> {
                    String name = pending.pluginName;
                    Bukkit.getPluginManager().disablePlugin(target);
                    Plugin reEnabled = Bukkit.getPluginManager().getPlugin(name);
                    if (reEnabled != null) {
                        Bukkit.getPluginManager().enablePlugin(reEnabled);
                    }
                    boolean success = reEnabled != null && Bukkit.getPluginManager().getPlugin(name).isEnabled();
                    if (success) {
                        sender.sendMessage(MessageUtil.parse(
                                "<green>✔</green> <white>Plugin </white><yellow>" + name
                                + "</yellow> <white>restarted.</white>"));
                    } else {
                        sender.sendMessage(MessageUtil.parse(
                                "<dark_red>⚠</dark_red> <red>Plugin </red><white>" + name
                                + "</white> <red>was disabled but could not be re-enabled! Check console for errors.</red>"));
                    }
                    ConsoleLogger.info("[PLUGIN] " + sender.getName() + " restarted " + name + " (success=" + success + ")");
                }
            }
        } catch (Exception e) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>Failed to </red><white>" + pending.action
                    + "</white> <red>plugin: </red><white>" + e.getMessage() + "</white>"));
            ConsoleLogger.error("[PLUGIN] Failed to " + pending.action + " " + pending.pluginName + ": " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    // ==========================================================================
    // CANCEL: /mp plugin cancel
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
                "<green>✔</green> <gray>Action cancelled: </gray><white>" + removed.action
                + " " + removed.pluginName + "</white>"));
        ConsoleLogger.info("[PLUGIN] " + sender.getName() + " cancelled " + removed.action + " for " + removed.pluginName);
        return true;
    }

    // ==========================================================================
    // UTILITY
    // ==========================================================================

    private static void usage(CommandSender sender) {
        sender.sendMessage(MessageUtil.parse(
                "<dark_red>❌</dark_red> <red>Usage: </red><white>/mp plugin <name> <on|off|restart|info></white>"));
        sender.sendMessage(MessageUtil.parse(
                "  <gray>Examples:</gray>"));
        sender.sendMessage(MessageUtil.parse(
                "  <white>/mp plugin WorldEdit on</white> <gray>— enable WorldEdit</gray>"));
        sender.sendMessage(MessageUtil.parse(
                "  <white>/mp plugin LuckPerms off</white> <gray>— disable LuckPerms</gray>"));
        sender.sendMessage(MessageUtil.parse(
                "  <white>/mp plugin Essentials restart</white> <gray>— restart Essentials</gray>"));
        sender.sendMessage(MessageUtil.parse(
                "  <white>/mp plugin Essentials info</white> <gray>— show plugin info</gray>"));
    }

    /**
     * Starts the background cleanup task (once).
     * Runs every 5 seconds, removes expired entries and notifies their owners.
     */
    private static void startCleanupTask() {
        if (cleanupTask != null && !cleanupTask.isCancelled()) return;

        cleanupTask = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), () -> {
            long now = System.currentTimeMillis();

            pendingActions.entrySet().removeIf(entry -> {
                if (now - entry.getValue().createdAt > TIMEOUT_MS) {
                    UUID uuid = entry.getKey();
                    PendingAction expired = entry.getValue();

                    // Уведомляем игрока, если онлайн
                    if (!uuid.equals(CONSOLE_UUID)) {
                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null && player.isOnline()) {
                            player.sendMessage(MessageUtil.parse(
                                    "<dark_red>⏰</dark_red> <red>Confirmation expired: </red><yellow>"
                                    + expired.action + " " + expired.pluginName
                                    + "</yellow> <gray>(30s timeout)</gray>"));
                        }
                    }

                    ConsoleLogger.info("[PLUGIN] Pending " + expired.action + " for "
                            + expired.pluginName + " expired (timeout)");
                    return true; // remove
                }
                return false;
            });

            // Если больше нет ожидающих действий — отменяем таск
            if (pendingActions.isEmpty() && cleanupTask != null) {
                cleanupTask.cancel();
                cleanupTask = null;
            }
        }, 100L, 100L); // первый запуск через 5 сек, потом каждые 5 сек
    }

    /**
     * Clears all pending actions. Called on plugin reload.
     */
    public static void clearPendingActions() {
        pendingActions.clear();
    }

    // ==========================================================================
    // INNER — PendingAction record
    // ==========================================================================

    private record PendingAction(String pluginName, String action, long createdAt) {}
}
