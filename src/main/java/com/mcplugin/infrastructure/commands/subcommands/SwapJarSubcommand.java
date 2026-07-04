package com.mcplugin.infrastructure.commands.subcommands;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.ConsoleLogger;
import com.mcplugin.infrastructure.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.UnknownDependencyException;

import java.io.File;
import java.lang.reflect.Field;
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
 * /mp swapjar [path-to-new-jar]
 * <p>
 * Hotswap JAR файла плагина: новый JAR копируется поверх текущего,
 * плагин выгружается, загружается новая версия и включается.
 * <p>
 * Требуется пермишен: {@code mcplugin.command.swapjar}.
 */
public final class SwapJarSubcommand {

    private static final String PERMISSION = "mcplugin.command.swapjar";
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
    // REQUEST: /mp swapjar [path]
    // ==========================================================================

    private static boolean handleSwapRequest(CommandSender sender, String[] args) {
        // Собираем путь из оставшихся аргументов
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

        // Проверяем — не тот же ли это файл?
        try {
            if (currentJar.getCanonicalPath().equals(newJar.getCanonicalPath())) {
                sender.sendMessage(MessageUtil.parse(
                        "<yellow>⚠</yellow> <gray>That is the same file as the current plugin JAR!</gray>"));
                return true;
            }
        } catch (Exception ignored) {}

        UUID uuid = sender instanceof Player p ? p.getUniqueId() : CONSOLE_UUID;

        // Валидируем новый JAR ПЕРЕД сохранением pending
        String validationError = validateJarFile(newJar);
        if (validationError != null) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>New JAR is invalid: </red><white>" + validationError + "</white>"));
            return true;
        }

        // Сохраняем pending
        pendingSwaps.put(uuid, new PendingSwap(newJar.getAbsolutePath(), currentJar.getAbsolutePath()));

        // Показываем предупреждение
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
                "<click:run_command:/mp swapjar confirm><dark_green>[</dark_green><green>✔ Confirm Swap</green><dark_green>]</dark_green></click>"
                + " <dark_gray>|</dark_gray> "
                + "<click:run_command:/mp swapjar cancel><dark_red>[</dark_red><red>✖ Cancel</red><dark_red>]</dark_red></click>"));
        sender.sendMessage(MessageUtil.parse(""));

        ConsoleLogger.info("[SwapJar] Pending swap: " + currentJar.getName() + " → " + newJar.getName() + " by " + sender.getName());
        return true;
    }

    // ==========================================================================
    // CONFIRM: /mp swapjar confirm
    // ==========================================================================

    private static boolean handleConfirm(CommandSender sender) {
        UUID uuid = sender instanceof Player p ? p.getUniqueId() : CONSOLE_UUID;
        PendingSwap pending = pendingSwaps.remove(uuid);

        if (pending == null) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>No pending swap. Use </red><white>/mp swapjar <path-to-new-jar></white><red> first.</red>"));
            return true;
        }

        // Проверяем, существует ли новый JAR
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

            // ШАГ 1: Отключаем плагин ПЕРВЫМ — освобождаем classloader и file lock
            ConsoleLogger.info("[SwapJar] Disabling plugin: " + pluginName);
            pm.disablePlugin(plugin);
            ConsoleLogger.info("[SwapJar] Plugin disabled.");

            // ШАГ 2: Переименовываем старый JAR в .bak
            File backupFile = new File(oldJar.getParentFile(), oldJar.getName() + ".bak");
            Files.move(oldJar.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            ConsoleLogger.info("[SwapJar] Moved old JAR to " + backupFile.getName());

            // ШАГ 3: Копируем новый JAR на освободившееся место
            Files.copy(newJar.toPath(), oldJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
            ConsoleLogger.info("[SwapJar] Copied new JAR: " + newJar.getName());

            // ШАГ 4: Валидируем установленный JAR (битая копия из-за I/O ошибок)
            String postCopyError = validateJarFile(oldJar);
            if (postCopyError != null) {
                Files.copy(backupFile.toPath(), oldJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
                sender.sendMessage(MessageUtil.parse(
                        "<dark_red>❌</dark_red> <red>Copied JAR is corrupted! Backup restored.</red>"));
                ConsoleLogger.error("[SwapJar] Post-copy validation failed: " + postCopyError);

                // Удаляем старый плагин из списка (иначе loadPlugin упадёт с дубликатом имени)
                removePluginFromManager(pm, plugin);
                Plugin oldReloaded = pm.loadPlugin(oldJar);
                if (oldReloaded != null) {
                    pm.enablePlugin(oldReloaded);
                    sender.sendMessage(MessageUtil.parse(
                            "<yellow>⚠</yellow> <white>Old plugin reloaded as fallback.</white>"));
                }
                return true;
            }

            // ШАГ 5: Удаляем старый плагин из внутреннего списка Bukkit (рефлексия)
            ConsoleLogger.info("[SwapJar] Removing old plugin from Bukkit plugin list...");
            removePluginFromManager(pm, plugin);

            // ШАГ 6: Загружаем новый плагин
            ConsoleLogger.info("[SwapJar] Loading new plugin from: " + oldJar.getName());
            Plugin loaded = pm.loadPlugin(oldJar);
            if (loaded == null) {
                throw new InvalidPluginException("loadPlugin() returned null");
            }

            // ШАГ 7: Включаем новый плагин
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
            // Плагин не загрузился — восстанавливаем backup
            ConsoleLogger.error("[SwapJar] Plugin load failed: " + e.getMessage());
            e.printStackTrace();

            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>Failed to load new plugin: </red><white>" + e.getMessage() + "</white>"));

            try {
                File backupFile = new File(oldJar.getParentFile(), oldJar.getName() + ".bak");
                if (backupFile.exists()) {
                    Files.copy(backupFile.toPath(), oldJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
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
            // Все остальные ошибки (I/O, reflection, etc.)
            ConsoleLogger.error("[SwapJar] Hot-swap failed: " + e.getMessage());
            e.printStackTrace();

            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>Hot-swap failed: </red><white>" + e.getMessage() + "</white>"));
            sender.sendMessage(MessageUtil.parse(
                    "<gray>Check console for details. A backup JAR may exist at: </gray><white>"
                    + new File(oldJar.getParentFile(), oldJar.getName() + ".bak").getAbsolutePath() + "</white>"));
        }

        return true;
    }

    // ==========================================================================
    // ✅ VALIDATE JAR
    // ==========================================================================

    /**
     * Проверяет JAR-файл на валидность:
     * <ul>
     *   <li>Открывается как ZIP (конструктор {@link ZipFile} выбросит {@link ZipException} если нет)</li>
     *   <li>Содержит plugin.yml</li>
     *   <li>plugin.yml содержит обязательные поля main, name, version</li>
     * </ul>
     *
     * @return null если всё ок, или строку с описанием ошибки
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
    // 🔄 REFLECTION: удалить плагин из внутренних списков PluginManager
    // ==========================================================================

    @SuppressWarnings("unchecked")
    private static void removePluginFromManager(PluginManager pm, Plugin plugin) {
        try {
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

            ConsoleLogger.info("[SwapJar] Plugin removed from Bukkit registration.");
        } catch (Exception e) {
            ConsoleLogger.error("[SwapJar] Failed to remove plugin from Bukkit list: " + e.getMessage());
        }
    }

    // ==========================================================================
    // CANCEL: /mp swapjar cancel
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
                "<red>❌ Usage: </red><white>/mp swapjar <path-to-new-jar></white>"));
        sender.sendMessage(MessageUtil.parse(
                "  <gray>Examples:</gray>"));
        sender.sendMessage(MessageUtil.parse(
                "  <white>/mp swapjar plugins/MC-Plugin-1.9.jar</white>"));
        sender.sendMessage(MessageUtil.parse(
                "  <white>/mp swapjar /home/server/plugins/MC-Plugin-update.jar</white>"));
    }

    // ==========================================================================
    // INNER — PendingSwap
    // ==========================================================================

    private record PendingSwap(String newJarPath, String oldJarPath) {}
}
