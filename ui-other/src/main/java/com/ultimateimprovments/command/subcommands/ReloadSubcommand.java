package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.core.PluginReloadCoordinator;
import com.ultimateimprovments.structure.StructureChunkTracker;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /ui reload — asynchronous plugin reload.
 * <pre>
 *   /ui reload            — the whole family (same as {@code all})
 *   /ui reload all        — the whole family
 *   /ui reload &lt;addon&gt;    — one UI-* plugin only (UI-Core, UI-Other, ...)
 * </pre>
 * <p>
 * <b>all:</b> Phase 1 (async, here) saves data; Phase 2 (sync,
 * {@link PluginReloadCoordinator} in ui-core): core subsystems restart in
 * place, then every addon gets a soft hot-reload via
 * {@code HotReloadEngine}: onDisable → reloadConfig → onEnable on the SAME
 * instance (classloader kept open). A fresh load from the JAR is impossible
 * on Paper/Purpur 26.3: runtime registration is hard-blocked for
 * paper-plugins ("Cannot register paper plugins during runtime!"), and plain
 * {@code disablePlugin} closes the classloader there ("zip file closed"
 * zombies on re-enable).
 * <p>
 * <b>&lt;addon&gt;:</b> that single addon is hot-reloaded the same way. A
 * targeted reload of UI-Core itself re-runs the core startup path
 * (infrastructure + modules) without touching the other family plugins.
 */
public final class ReloadSubcommand {

    private ReloadSubcommand() {}

    private static boolean reloadInProgress = false;

    public static boolean execute(CommandSender sender, String[] args) {
        if (sender instanceof Player player && !player.hasPermission("ui.command.reload")) {
            CommandErrors.noPermission(player, "ui.command.reload");
            return true;
        }

        String target = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "all";

        if (target.equals("all")) {
            return reloadAll(sender);
        }
        return reloadOne(sender, args[1]);
    }

    /** Kept for backward compatibility with the legacy adapter signature. */
    public static boolean execute(CommandSender sender) {
        return execute(sender, new String[0]);
    }

    // =========================
    // ALL — full family cycle
    // =========================

    private static boolean reloadAll(CommandSender sender) {
        if (reloadInProgress) {
            sender.sendMessage(MessageUtil.parse("<yellow>Reload already in progress, please wait..."));
            return true;
        }
        reloadInProgress = true;

        sender.sendMessage(MessageUtil.parse("<yellow>Reloading the whole UltimateImprovments family asynchronously..."));
        Main plugin = Main.getInstance();

        new BukkitRunnable() {
            @Override
            public void run() {
                long start = System.currentTimeMillis();

                try {
                    ConsoleLogger.info("[Reload] Saving persistent data (async)...");
                    StructureChunkTracker.save();
                } catch (Exception e) {
                    ConsoleLogger.warn("[Reload] Async save warning: " + e.getMessage());
                }

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        try {
                            PluginReloadCoordinator.runSyncPhase(plugin, sender, start);
                        } finally {
                            reloadInProgress = false;
                        }
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
        return true;
    }

    // =========================
    // ONE — targeted addon reload
    // =========================

    private static boolean reloadOne(CommandSender sender, String rawName) {
        if (reloadInProgress) {
            sender.sendMessage(MessageUtil.parse("<yellow>Reload already in progress, please wait..."));
            return true;
        }

        final Plugin targetPlugin = findFamilyPlugin(rawName);
        if (targetPlugin == null) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ <gray>Unknown addon: </gray><yellow>" + rawName
                            + "</yellow><gray>. Available: </gray><white>all, " + String.join(", ", familyNames())
                            + "</white>"));
            return true;
        }

        if (targetPlugin.getName().equals("UI-Core")) {
            Main plugin = Main.getInstance();
            if (reloadInProgress) {
                sender.sendMessage(MessageUtil.parse("<yellow>Reload already in progress, please wait..."));
                return true;
            }
            reloadInProgress = true;
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>Reloading </yellow><white>UI-Core</white><yellow> (core only, addons untouched)..."));
            new BukkitRunnable() {
                @Override
                public void run() {
                    long start = System.currentTimeMillis();
                    try {
                        saveDataAsyncThenCoreReload(plugin, sender, start);
                    } finally {
                        reloadInProgress = false;
                    }
                }
            }.runTask(plugin);
            return true;
        }

        if (!targetPlugin.isEnabled()) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ <gray>Plugin </gray><yellow>" + targetPlugin.getName()
                            + "</yellow><gray> is currently disabled.</gray>"));
            return true;
        }

        reloadInProgress = true;
        String name = targetPlugin.getName();
        sender.sendMessage(MessageUtil.parse("<yellow>Reloading </yellow><white>" + name + "</white><yellow>..."));
        Main plugin = Main.getInstance();

        new BukkitRunnable() {
            @Override
            public void run() {
                long start = System.currentTimeMillis();
                try {
                    // Soft hot-reload: onDisable → reloadConfig → onEnable on
                    // the SAME instance (classloader kept open). A fresh load
                    // from the JAR is hard-blocked on Paper/Purpur 26.3 for
                    // paper-plugins, and plain disablePlugin closes the JAR.
                    ConsoleLogger.info("[Reload] [" + name + "] Hot-reloading...");
                    Plugin fresh = com.ultimateimprovments.core.HotReloadEngine.hotReload(targetPlugin, name);

                    long time = System.currentTimeMillis() - start;
                    sender.sendMessage(MessageUtil.parse(
                            "<dark_green>✔ <green>Success: <gray>Reloaded </gray><yellow>" + name
                                    + "</yellow><gray> (v" + fresh.getPluginMeta().getVersion()
                                    + ") in <yellow>" + time + "ms"));
                    ConsoleLogger.info("[ULTIMATEIMPROVMENTS] [" + name + "] Hot-reloaded in " + time + "ms");
                } catch (Exception e) {
                    sender.sendMessage(MessageUtil.parse(
                            "<dark_red>❌ <red>Error: <gray>Reload of " + name + " failed! Check console."));
                    ConsoleLogger.error("[ULTIMATEIMPROVMENTS] [" + name + "] Reload failed: " + e.getMessage());
                    e.printStackTrace();
                    sender.sendMessage(MessageUtil.parse(
                            "<yellow>⚠ <gray>The addon may be unloaded now — use </gray><white>/ui reload all</white>"
                                    + "<gray> or restart the server.</gray>"));
                } finally {
                    reloadInProgress = false;
                }
            }
        }.runTask(plugin);
        return true;
    }

    /** Core-only reload: save async → on the main thread restart just UI-Core's subsystems. */
    private static void saveDataAsyncThenCoreReload(Main plugin, CommandSender sender, long start) {
        try {
            ConsoleLogger.info("[Reload] Saving persistent data (async part on main thread)...");
            StructureChunkTracker.save();
        } catch (Exception e) {
            ConsoleLogger.warn("[Reload] Async save warning: " + e.getMessage());
        }
        PluginReloadCoordinator.runCoreOnlyPhase(plugin, sender, start);
    }

    /** Resolves a family plugin by name (case-insensitive, "ui-" prefix optional). */
    private static Plugin findFamilyPlugin(String rawName) {
        String normalized = rawName.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("ui-")) {
            normalized = "ui-" + normalized;
        }
        final String wanted = normalized;
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            if (p.getName().toLowerCase(Locale.ROOT).equals(wanted)) return p;
        }
        return null;
    }

    /** All UI-* family plugin names (for the error message / tab-complete). */
    public static List<String> familyNames() {
        List<String> names = new ArrayList<>();
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            if (p.getName().startsWith("UI-")) names.add(p.getName());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /** Tab-complete for the {@code <addon>} argument: all + family names (without the ui- prefix). */
    public static List<String> tabCompleteTargets(String partial) {
        String p = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        if ("all".startsWith(p)) result.add("all");
        for (String name : familyNames()) {
            String shortName = name.substring("UI-".length()).toLowerCase(Locale.ROOT);
            if (shortName.startsWith(p)) result.add(shortName);
            if (name.toLowerCase(Locale.ROOT).startsWith(p)) result.add(name.toLowerCase(Locale.ROOT));
        }
        return result;
    }
}
