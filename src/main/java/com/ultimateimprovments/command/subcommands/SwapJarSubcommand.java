package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.HandlerList;
import org.bukkit.entity.Player;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.UnknownDependencyException;

import java.io.File;
import com.google.common.graph.MutableGraph;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * /ui swapjar [path-to-new-jar]
 * <p>
 * Hotswap of the plugin JAR: the new JAR is copied over the current one,
 * the plugin is unloaded, the new version is loaded and enabled.
 * <p>
 * Requires the permission: {@code ui.command.swapjar}.
 */
public final class SwapJarSubcommand {

    private static final String PERMISSION = "ui.command.swapjar";
    private static final UUID CONSOLE_UUID = new UUID(0, 0);
    private static final Map<UUID, PendingSwap> pendingSwaps = new ConcurrentHashMap<>();

    private SwapJarSubcommand() {}

    // ==========================================================================
    // ENTRY POINT
    // ==========================================================================

    public static boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>You don't have permission to swap plugin JARs!</red>"));
            return true;
        }

        if (args.length < 2) {
            usage(sender);
            return true;
        }

        return switch (args[1].toLowerCase()) {
            case "confirm" -> handleConfirm(sender);
            case "cancel"  -> handleCancel(sender);
            default        -> handleSwapRequest(sender, args);
        };
    }

    // ==========================================================================
    // REQUEST: /ui swapjar [path]
    // ==========================================================================

    private static boolean handleSwapRequest(CommandSender sender, String[] args) {
        // Build the path from the remaining arguments
        StringBuilder pathBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (pathBuilder.length() > 0) pathBuilder.append(" ");
            pathBuilder.append(args[i]);
        }
        String jarPath = pathBuilder.toString();

        if (jarPath.isEmpty()) {
            usage(sender);
            return true;
        }

        File newJar = new File(jarPath);
        if (!newJar.exists() || !newJar.isFile()) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>File not found: </red><white>" + jarPath + "</white>"));
            return true;
        }

        if (!newJar.getName().endsWith(".jar")) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>Not a JAR file: </red><white>" + newJar.getName() + "</white>"));
            return true;
        }

        Main plugin = Main.getInstance();
        File currentJar = plugin.getPluginFile();

        if (currentJar == null || !currentJar.exists()) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>Cannot find current plugin JAR file!</red>"));
            return true;
        }

        // Check — isn't it the same file?
        try {
            if (currentJar.getCanonicalPath().equals(newJar.getCanonicalPath())) {
                sender.sendMessage(MessageUtil.parse(
                        "<yellow>⚠</yellow> <gray>That is the same file as the current plugin JAR!</gray>"));
                return true;
            }
        } catch (Exception ignored) {}

        UUID uuid = sender instanceof Player p ? p.getUniqueId() : CONSOLE_UUID;

        // Validate the new JAR BEFORE saving pending
        String validationError = validateJarFile(newJar);
        if (validationError != null) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>New JAR is invalid: </red><white>" + validationError + "</white>"));
            return true;
        }

        // Save pending
        pendingSwaps.put(uuid, new PendingSwap(newJar.getAbsolutePath(), currentJar.getAbsolutePath()));

        // Show a warning
        long newSize = newJar.length() / 1024;
        long currentSize = currentJar.length() / 1024;

        sender.sendMessage(MessageUtil.parse(""));
        sender.sendMessage(MessageUtil.parse(
                "<dark_red>⚠</dark_red> <red>WARNING: You are about to hot-swap the plugin JAR!</red>"));
        sender.sendMessage(MessageUtil.parse(""));
        sender.sendMessage(MessageUtil.parse(
                "  <gray>Current JAR:</gray> <white>" + currentJar.getName() + "</white> <dark_gray>(" + currentSize + " KB)</dark_gray>"));
        sender.sendMessage(MessageUtil.parse(
                "  <gray>New JAR:</gray>     <white>" + newJar.getName() + "</white> <dark_gray>(" + newSize + " KB)</dark_gray>"));
        sender.sendMessage(MessageUtil.parse(
                "  <gray>Plugin:</gray>      <white>" + plugin.getDescription().getName()
                + "</white> <dark_gray>v" + plugin.getDescription().getVersion() + "</dark_gray>"));
        sender.sendMessage(MessageUtil.parse(""));
        sender.sendMessage(MessageUtil.parse(
                "<red>This will replace the plugin JAR, disable the current plugin,</red>"));
        sender.sendMessage(MessageUtil.parse(
                "<red>and load + enable the new version.</red>"));
        sender.sendMessage(MessageUtil.parse(
                "<red>If the new JAR is incompatible, the plugin may fail to load.</red>"));
        sender.sendMessage(MessageUtil.parse(
                "<red>Make sure you have a backup of the original JAR.</red>"));
        sender.sendMessage(MessageUtil.parse(""));
        sender.sendMessage(MessageUtil.parse(
                "<click:run_command:/ui swapjar confirm><dark_green>[</dark_green><green>✔ Confirm Swap</green><dark_green>]</dark_green></click>"
                + " <dark_gray>|</dark_gray> "
                + "<click:run_command:/ui swapjar cancel><dark_red>[</dark_red><red>✖ Cancel</red><dark_red>]</dark_red></click>"));
        sender.sendMessage(MessageUtil.parse(""));

        ConsoleLogger.info("[SwapJar] Pending swap: " + currentJar.getName() + " → " + newJar.getName() + " by " + sender.getName());
        return true;
    }

    // ==========================================================================
    // CONFIRM: /ui swapjar confirm
    // ==========================================================================

    private static boolean handleConfirm(CommandSender sender) {
        UUID uuid = sender instanceof Player p ? p.getUniqueId() : CONSOLE_UUID;
        PendingSwap pending = pendingSwaps.remove(uuid);

        if (pending == null) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>No pending swap. Use </red><white>/ui swapjar <path-to-new-jar></white><red> first.</red>"));
            return true;
        }

        // Check whether the new JAR exists
        File newJar = new File(pending.newJarPath);
        if (!newJar.exists() || !newJar.isFile()) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>New JAR file no longer exists: </red><white>" + pending.newJarPath + "</white>"));
            return true;
        }

        File oldJar = new File(pending.oldJarPath);
        if (!oldJar.exists()) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>Current JAR file no longer exists! Cannot proceed.</red>"));
            return true;
        }

        Main plugin = Main.getInstance();
        PluginManager pm = Bukkit.getPluginManager();

        try {
            String pluginName = plugin.getDescription().getName();

            // STEP 1: Disable the plugin FIRST — frees the classloader and file lock
            ConsoleLogger.info("[SwapJar] Disabling plugin: " + pluginName);
            pm.disablePlugin(plugin);
            ConsoleLogger.info("[SwapJar] Plugin disabled.");

            // STEP 2: Rename the old JAR to .bak
            File backupFile = new File(oldJar.getParentFile(), oldJar.getName() + ".bak");
            Files.move(oldJar.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            ConsoleLogger.info("[SwapJar] Moved old JAR to " + backupFile.getName());

            // STEP 3: Copy the new JAR into the freed spot
            Files.copy(newJar.toPath(), oldJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
            ConsoleLogger.info("[SwapJar] Copied new JAR: " + newJar.getName());

            // STEP 4: Validate the installed JAR (broken copy due to I/O errors)
            String postCopyError = validateJarFile(oldJar);
            if (postCopyError != null) {
                forceReplaceFile(backupFile, oldJar);
                sender.sendMessage(MessageUtil.parse(
                        "<dark_red>❌</dark_red> <red>Copied JAR is corrupted! Backup restored.</red>"));
                ConsoleLogger.error("[SwapJar] Post-copy validation failed: " + postCopyError);

                // Remove the old plugin from the list (otherwise loadPlugin fails on a duplicate name)
                removePluginFromManager(pm, plugin);
                Plugin oldReloaded = pm.loadPlugin(oldJar);
                if (oldReloaded != null) {
                    pm.enablePlugin(oldReloaded);
                    sender.sendMessage(MessageUtil.parse(
                            "<yellow>⚠</yellow> <white>Old plugin reloaded as fallback.</white>"));
                }
                return true;
            }

            // STEP 5: Remove the old plugin from Bukkit's internal list (reflection)
            ConsoleLogger.info("[SwapJar] Removing old plugin from Bukkit plugin list...");
            removePluginFromManager(pm, plugin);

            // STEP 6: Load the new plugin
            ConsoleLogger.info("[SwapJar] Loading new plugin from: " + oldJar.getName());
            Plugin loaded = pm.loadPlugin(oldJar);
            if (loaded == null) {
                throw new InvalidPluginException("loadPlugin() returned null");
            }

            // STEP 7: Enable the new plugin
            ConsoleLogger.info("[SwapJar] Enabling new plugin: " + loaded.getName()
                    + " v" + loaded.getDescription().getVersion());
            pm.enablePlugin(loaded);

            sender.sendMessage(MessageUtil.parse(""));
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Plugin hot-swapped successfully!</white>"));
            sender.sendMessage(MessageUtil.parse(
                    "  <gray>New version:</gray> <white>" + loaded.getDescription().getVersion() + "</white>"));
            sender.sendMessage(MessageUtil.parse(""));

            try { Files.deleteIfExists(backupFile.toPath()); } catch (Exception ignored) {}

            ConsoleLogger.info("[SwapJar] Hot-swap completed: " + pluginName
                    + " → v" + loaded.getDescription().getVersion());

        } catch (InvalidPluginException | UnknownDependencyException e) {
            // Plugin failed to load — restore the backup
            ConsoleLogger.error("[SwapJar] Plugin load failed: " + e.getMessage());
            e.printStackTrace();

            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>Failed to load new plugin: </red><white>" + e.getMessage() + "</white>"));

            try {
                File backupFile = new File(oldJar.getParentFile(), oldJar.getName() + ".bak");
                if (backupFile.exists()) {
                    forceReplaceFile(backupFile, oldJar);
                    ConsoleLogger.info("[SwapJar] Backup restored.");

                    try {
                        Plugin fallback = pm.loadPlugin(oldJar);
                        if (fallback != null) {
                            pm.enablePlugin(fallback);
                            sender.sendMessage(MessageUtil.parse(
                                    "<yellow>⚠</yellow> <white>Old plugin reloaded as fallback. JAR restored.</white>"));
                        }
                    } catch (Exception fbErr) {
                        ConsoleLogger.error("[SwapJar] Fallback reload also failed: " + fbErr.getMessage());
                        sender.sendMessage(MessageUtil.parse(
                                "<red>❌ Fallback reload also failed! Restart server manually.</red>"));
                    }
                }
            } catch (Exception restoreErr) {
                ConsoleLogger.error("[SwapJar] Backup restoration failed: " + restoreErr.getMessage());
                sender.sendMessage(MessageUtil.parse(
                        "<red>❌ Backup restoration failed! Restart server manually. Backup at: </red><white>"
                        + new File(oldJar.getParentFile(), oldJar.getName() + ".bak").getAbsolutePath() + "</white>"));
            }

        } catch (Exception e) {
            // All other errors (I/O, reflection, etc.)
            ConsoleLogger.error("[SwapJar] Hot-swap failed: " + e.getMessage());
            e.printStackTrace();

            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>Hot-swap failed: </red><white>" + e.getMessage() + "</white>"));

            // Try to restore the backup
            try {
                File backupFile = new File(oldJar.getParentFile(), oldJar.getName() + ".bak");
                if (backupFile.exists()) {
                    forceReplaceFile(backupFile, oldJar);
                    ConsoleLogger.info("[SwapJar] Backup restored.");
                    sender.sendMessage(MessageUtil.parse(
                            "<yellow>⚠</yellow> <gray>Old JAR restored from backup.</gray>"));

                    // Remove the old plugin from the registries before reloading
                    removePluginFromManager(pm, plugin);

                    try {
                        Plugin fallback = pm.loadPlugin(oldJar);
                        if (fallback != null) {
                            pm.enablePlugin(fallback);
                            sender.sendMessage(MessageUtil.parse(
                                    "<yellow>⚠</yellow> <white>Old plugin reloaded as fallback.</white>"));
                        }
                    } catch (Exception fbErr) {
                        ConsoleLogger.error("[SwapJar] Fallback reload also failed: " + fbErr.getMessage());
                        sender.sendMessage(MessageUtil.parse(
                                "<red>❌ Fallback reload also failed! Restart server manually.</red>"));
                    }
                } else {
                    sender.sendMessage(MessageUtil.parse(
                            "<red>❌ No backup JAR found. Restart server manually.</red>"));
                }
            } catch (Exception restoreErr) {
                ConsoleLogger.error("[SwapJar] Backup restoration failed: " + restoreErr.getMessage());
                sender.sendMessage(MessageUtil.parse(
                        "<red>❌ Backup restoration failed! Restart server manually. Backup at: </red><white>"
                        + new File(oldJar.getParentFile(), oldJar.getName() + ".bak").getAbsolutePath() + "</white>"));
            }
        }

        return true;
    }

    // ==========================================================================
    // ✅ VALIDATE JAR
    // ==========================================================================

    /**
     * Validates a JAR file:
     * <ul>
     *   <li>Opens as a ZIP (the {@link ZipFile} constructor throws {@link ZipException} otherwise)</li>
     *   <li>Contains plugin.yml</li>
     *   <li>plugin.yml has the required main, name, version fields</li>
     * </ul>
     *
     * @return null if all good, or a string describing the error
     */
    private static String validateJarFile(File jarFile) {
        try (ZipFile zip = new ZipFile(jarFile)) {
            var pluginYml = zip.getEntry("plugin.yml");
            if (pluginYml == null) {
                return "Missing plugin.yml in JAR";
            }

            try (var in = zip.getInputStream(pluginYml)) {
                String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                if (!content.contains("main:"))   return "plugin.yml missing 'main:' field";
                if (!content.contains("name:"))   return "plugin.yml missing 'name:' field";
                if (!content.contains("version:")) return "plugin.yml missing 'version:' field";
            }

            return null;
        } catch (ZipException e) {
            return "Not a valid ZIP/JAR file: " + e.getMessage();
        } catch (Exception e) {
            return "Cannot read JAR file: " + e.getMessage();
        }
    }

    // ==========================================================================
    // 💾 FORCE REPLACE: bypassing the Windows file lock
    // ==========================================================================

    /**
     * Replaces a file even if it's locked (Windows).
     * First tries delete, then rename (if delete failed),
     * then copies the source over the target.
     *
     * @param source the source file (what we copy)
     * @param target the target file (what we replace, may be locked)
     */
    private static void forceReplaceFile(File source, File target) throws Exception {
        // Attempt 1: normal copy with replace
        try {
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return;
        } catch (Exception e) {
            // Possibly the file is locked (Windows) — try other methods
        }

        // Attempt 2: delete the locked file, then copy
        try {
            Files.deleteIfExists(target.toPath());
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return;
        } catch (Exception e) {
            // Delete also failed
        }

        // Attempt 3: rename the locked file, then copy
        File tmpFile = new File(target.getParentFile(), target.getName() + ".deleted." + System.currentTimeMillis());
        try {
            target.renameTo(tmpFile);
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            // Try to delete the temp file in the background
            tmpFile.deleteOnExit();
            return;
        } catch (Exception e) {
            // Rename also failed
        }

        // All failed — throw an exception
        throw new Exception("Cannot replace file " + target.getName() + " after multiple attempts (locked on Windows?)");
    }

    // ==========================================================================
    // 🔄 REFLECTION: remove the plugin from PluginManager's internal lists
    // ==========================================================================

    /**
     * Removes the plugin from all of PluginManager's internal registries.
     * <p>
     * In Paper 1.21+ PluginManager is PaperPluginManagerImpl, which
     * delegates plugin storage to PaperPluginInstanceManager (instanceManager field).
     * The old SimplePluginManager.plugins/lookupNames don't exist there.
     */
    @SuppressWarnings("unchecked")
    private static void removePluginFromManager(PluginManager pm, Plugin plugin) {
        try {
            String pmClassName = pm.getClass().getName();

            if (pmClassName.equals("io.papermc.paper.plugin.manager.PaperPluginManagerImpl")) {
                // Paper 1.21+: plugins are stored in PaperPluginInstanceManager
                removeFromPaperPluginManager(pm, plugin);
            } else {
                // Legacy Bukkit/Spigot: fields directly in SimplePluginManager
                removeFromSimplePluginManager(pm, plugin);
            }

            // Additionally: HandlerList.unregisterAll (in case disablePlugin didn't fully clean up)
            HandlerList.unregisterAll(plugin);

            ConsoleLogger.info("[SwapJar] Plugin removed from Bukkit registration.");
        } catch (Exception e) {
            ConsoleLogger.error("[SwapJar] Failed to remove plugin from Bukkit list: " + e.getMessage());
        }
    }

    /**
     * Removes the plugin from Paper 1.21+ PaperPluginManagerImpl → PaperPluginInstanceManager.
     */
    @SuppressWarnings("unchecked")
    private static void removeFromPaperPluginManager(PluginManager pm, Plugin plugin) throws Exception {
        // Get instanceManager from PaperPluginManagerImpl
        Field instanceManagerField = pm.getClass().getDeclaredField("instanceManager");
        instanceManagerField.setAccessible(true);
        Object instanceManager = instanceManagerField.get(pm);

        Class<?> imClass = instanceManager.getClass();

        // 1. Remove from plugins (List<Plugin>)
        Field pluginsField = imClass.getDeclaredField("plugins");
        pluginsField.setAccessible(true);
        List<Plugin> plugins = (List<Plugin>) pluginsField.get(instanceManager);
        plugins.remove(plugin);

        // 2. Remove from lookupNames (Map<String, Plugin>)
        Field lookupNamesField = imClass.getDeclaredField("lookupNames");
        lookupNamesField.setAccessible(true);
        Map<String, Plugin> lookupNames = (Map<String, Plugin>) lookupNamesField.get(instanceManager);
        lookupNames.remove(plugin.getName());

        // 3. Remove from dependencyTree: SimpleMetaDependencyTree stores a MutableGraph<String>,
        //    remove() takes a PluginProvider which we don't have — go directly into the graph.
        try {
            Field depTreeField = imClass.getDeclaredField("dependencyTree");
            depTreeField.setAccessible(true);
            Object depTree = depTreeField.get(instanceManager);

            // Look for a MutableGraph-typed field across the whole class hierarchy
            Field graphField = null;
            for (Class<?> c = depTree.getClass(); c != null && graphField == null; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (MutableGraph.class.isAssignableFrom(f.getType())) {
                        graphField = f;
                        break;
                    }
                }
            }
            if (graphField != null) {
                graphField.setAccessible(true);
                Object graph = graphField.get(depTree);
                // MutableGraph.removeNode(Object) removes a node from the graph
                Method removeNode = graph.getClass().getMethod("removeNode", Object.class);
                removeNode.invoke(graph, plugin.getName());
                ConsoleLogger.info("[SwapJar] Removed plugin from dependency graph.");
            }
        } catch (Exception ignored) {
            // dependencyTree cleanup — not critical if it fails
        }
    }

    /**
     * Removes the plugin from the old SimplePluginManager (Bukkit/Spigot).
     */
    @SuppressWarnings("unchecked")
    private static void removeFromSimplePluginManager(PluginManager pm, Plugin plugin) throws Exception {
        Field pluginsField = pm.getClass().getDeclaredField("plugins");
        pluginsField.setAccessible(true);
        List<Plugin> plugins = (List<Plugin>) pluginsField.get(pm);
        plugins.remove(plugin);

        try {
            Field lookupNamesField = pm.getClass().getDeclaredField("lookupNames");
            lookupNamesField.setAccessible(true);
            Map<String, Plugin> lookupNames = (Map<String, Plugin>) lookupNamesField.get(pm);
            lookupNames.remove(plugin.getName());
        } catch (Exception ignored) {}
    }

    // ==========================================================================
    // CANCEL: /ui swapjar cancel
    // ==========================================================================

    private static boolean handleCancel(CommandSender sender) {
        UUID uuid = sender instanceof Player p ? p.getUniqueId() : CONSOLE_UUID;
        PendingSwap removed = pendingSwaps.remove(uuid);

        if (removed == null) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>No pending swap to cancel.</red>"));
            return true;
        }

        sender.sendMessage(MessageUtil.parse(
                "<green>✔</green> <gray>Swap cancelled.</gray>"));
        ConsoleLogger.info("[SwapJar] Swap cancelled by " + sender.getName());
        return true;
    }

    // ==========================================================================
    // USAGE
    // ==========================================================================

    private static void usage(CommandSender sender) {
        sender.sendMessage(MessageUtil.parse(
                "<red>❌ Usage: </red><white>/ui swapjar <path-to-new-jar></white>"));
        sender.sendMessage(MessageUtil.parse(
                "  <gray>Examples:</gray>"));
        sender.sendMessage(MessageUtil.parse(
                "  <white>/ui swapjar plugins/UltimateImprovments-1.9.jar</white>"));
        sender.sendMessage(MessageUtil.parse(
                "  <white>/ui swapjar /home/server/plugins/UltimateImprovments-update.jar</white>"));
    }

    // ==========================================================================
    // INNER — PendingSwap
    // ==========================================================================

    private record PendingSwap(String newJarPath, String oldJarPath) {}
}
