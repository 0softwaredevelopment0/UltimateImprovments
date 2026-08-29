package com.ultimateimprovments.datapack;

import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.FileLogger;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;
import java.util.zip.ZipFile;

public class DatapackInstaller {

    private static DatapackInstaller instance;

    public static void init(JavaPlugin plugin) {
        instance = new DatapackInstaller();
    }

    public static DatapackInstaller getInstance() {
        return instance;
    }

    // =========================
    // FIND DATAPACKS FOLDER
    // =========================

    /**
     * Automatically finds the datapacks folder in the world directory,
     * regardless of the Minecraft version and folder structure.
     * <p>
     * Search priority:
     * <ol>
     *   <li>Bukkit.getWorlds() — the first loaded world's folder (most reliable)</li>
     *   <li>server.properties → level-name → Bukkit.getWorldContainer()</li>
     *   <li>The "world" folder in Bukkit.getWorldContainer() (default)</li>
     * </ol>
     * If the datapacks folder is not found — it is created.
     */
    private static File findDatapacksFolder() {
        File worldRoot = findWorldRoot();

        File datapacksFolder = new File(worldRoot, "datapacks");
        FileLogger.ensureDirectory(datapacksFolder, "Datapack");
        return datapacksFolder;
    }

    /**
     * Finds the world root directory where datapacks should be installed.
     */
    private static File findWorldRoot() {
        // 1. Try to get the world folder via the Bukkit API (the most reliable way)
        World firstWorld = null;
        try {
            if (!Bukkit.getWorlds().isEmpty()) {
                firstWorld = Bukkit.getWorlds().get(0);
            }
        } catch (Exception ignored) {
            // Bukkit.getWorlds() may not be ready in the early loading stages
        }

        if (firstWorld != null) {
            File worldFolder = firstWorld.getWorldFolder();
            if (worldFolder != null && worldFolder.isDirectory()) {
                ConsoleLogger.info("[Datapack] World root found via Bukkit API: " + worldFolder.getAbsolutePath());
                return worldFolder;
            }
        }

        // 2. Fallback: read level-name from server.properties
        String levelName = "world";
        File serverDir = new File("").getAbsoluteFile();
        File serverPropsFile = new File(serverDir, "server.properties");
        if (serverPropsFile.exists()) {
            try (FileInputStream fis = new FileInputStream(serverPropsFile)) {
                Properties props = new Properties();
                props.load(fis);
                levelName = props.getProperty("level-name", "world");
            } catch (Exception e) {
                ConsoleLogger.warn("[Datapack] Failed to read server.properties: " + e.getMessage());
            }
        }

        File worldRoot = new File(Bukkit.getWorldContainer(), levelName);
        ConsoleLogger.info("[Datapack] World root from server.properties: " + worldRoot.getAbsolutePath());

        // 3. If the folder is not found, try the standard "world"
        if (!worldRoot.isDirectory()) {
            File defaultWorld = new File(Bukkit.getWorldContainer(), "world");
            if (defaultWorld.isDirectory()) {
                worldRoot = defaultWorld;
                ConsoleLogger.info("[Datapack] Fallback to default world folder: " + worldRoot.getAbsolutePath());
            }
        }

        return worldRoot;
    }

    // =========================
    // DATAPACK INSTALL
    // =========================

    public void install(JavaPlugin plugin) throws Exception {
        File datapacksFolder = findDatapacksFolder();

        File targetFolder = new File(datapacksFolder, "UI-Datapack");

        // Master toggle OFF — remove the already-installed datapack from the world.
        if (!DatapackModules.isMasterEnabled()) {
            if (targetFolder.exists()) {
                ConsoleLogger.warn("[Datapack] Master toggle is OFF (datapack.enabled: false) — removing installed UI-Datapack...");
                deleteRecursively(targetFolder);
                ConsoleLogger.info("[Datapack] Removed UI-Datapack from the world datapacks folder.");
            } else {
                ConsoleLogger.info("[Datapack] Master toggle is OFF (datapack.enabled: false) — datapack not installed.");
            }
            return;
        }

        String mode = DatapackModules.getMode();
        boolean folderExists = targetFolder.exists();
        boolean loadedInWorld = isLoadedInWorld(findWorldRoot());

        // ── Decide whether to (re)install based on the config mode ──
        boolean doInstall;
        switch (mode) {
            case DatapackModules.MODE_IGNORE -> {
                // Leave an existing folder untouched; install only if absent.
                doInstall = !folderExists;
                if (folderExists) {
                    ConsoleLogger.info("[Datapack] Mode: ignore — existing UI-Datapack folder left untouched.");
                }
            }
            case DatapackModules.MODE_CHECK_OVERRIDE -> {
                // Install only if the datapack is not in the world's enabled list.
                doInstall = !loadedInWorld;
                if (loadedInWorld) {
                    ConsoleLogger.info("[Datapack] Mode: check-override — UI-Datapack is already enabled in the world; skipping reinstall.");
                } else {
                    ConsoleLogger.info("[Datapack] Mode: check-override — UI-Datapack is not in the world datapack list; reinstalling...");
                }
            }
            default -> {
                // MODE_OVERRIDE — always re-extract to ensure the datapack
                // is up-to-date with the plugin version.
                doInstall = true;
            }
        }

        if (doInstall) {
            if (folderExists) {
                ConsoleLogger.info("[Datapack] Reinstalling existing datapack (folder exists: UI-Datapack)");
                deleteRecursively(targetFolder);
            } else {
                ConsoleLogger.info("[Datapack] Installing new datapack...");
            }

            targetFolder.mkdirs();
            copyFromJar(plugin, "datapacks/UI-Datapack/", targetFolder);

            ConsoleLogger.success("[Datapack] Installed to " + targetFolder.getAbsolutePath());
        }

        ConsoleLogger.info("[Datapack] Loaded parts: " + DatapackModules.describe());

        // ── Post-install check: is the datapack actually enabled in the world? ──
        checkLoaded(plugin, loadedInWorld);
    }

    /**
     * Verifies the datapack is enabled in the world after install.
     * If it isn't: tries {@code /datapack enable} (config: {@code datapack.auto_enable}),
     * warns (config: {@code datapack.warn_if_not_loaded}) and/or
     * auto-restarts the server (config: {@code datapack.restart_to_apply}).
     */
    private void checkLoaded(JavaPlugin plugin, boolean loadedBeforeInstall) {
        boolean loaded = loadedBeforeInstall || isLoadedInWorld(findWorldRoot());
        if (loaded) {
            ConsoleLogger.info("[Datapack] UI-Datapack is enabled in the world.");
            return;
        }

        // Try to enable the datapack automatically via the console command.
        if (DatapackModules.isAutoEnable()) {
            attemptAutoEnable(plugin);
            return;
        }

        warnNotLoaded(plugin);
    }

    /**
     * Runs {@code /datapack enable "file/UI-Datapack"} via the console, then re-checks
     * after a short delay whether the world now lists the datapack as enabled.
     */
    private void attemptAutoEnable(JavaPlugin plugin) {
        ConsoleLogger.info("[Datapack] datapack.auto_enable: true — running /datapack enable \"file/UI-Datapack\"...");
        try {
            boolean dispatched = Bukkit.dispatchCommand(
                    Bukkit.getConsoleSender(), "datapack enable \"file/UI-Datapack\"");
            if (!dispatched) {
                ConsoleLogger.warn("[Datapack] /datapack enable was not accepted — the datapack may be missing or already enabled.");
            }
        } catch (Throwable t) {
            ConsoleLogger.warn("[Datapack] Failed to run /datapack enable: " + t.getMessage());
        }

        // Give the world a moment to save level.dat, then verify.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            boolean nowLoaded = isLoadedInWorld(findWorldRoot());
            if (nowLoaded) {
                ConsoleLogger.success("[Datapack] UI-Datapack is now enabled in the world.");
                ConsoleLogger.info("[Datapack] A /reload or server restart is required for the datapack to take effect.");
                maybeRestart(plugin);
            } else {
                ConsoleLogger.warn("[Datapack] /datapack enable did not help — the datapack is still not enabled in the world.");
                warnNotLoaded(plugin);
            }
        }, 80L);
    }

    /**
     * Prints the "datapack not enabled" warning (config: {@code datapack.warn_if_not_loaded})
     * and possibly schedules a restart (config: {@code datapack.restart_to_apply}).
     */
    private void warnNotLoaded(JavaPlugin plugin) {
        if (DatapackModules.isWarnIfNotLoaded()) {
            ConsoleLogger.warn("");
            ConsoleLogger.warn("[Datapack] ⚠ UI-Datapack is NOT enabled in the world!");
            ConsoleLogger.warn("[Datapack] The datapack was installed but the world doesn't list it as enabled.");
            ConsoleLogger.warn("[Datapack] Possible causes:");
            ConsoleLogger.warn("[Datapack]   • The server was not restarted after installation — a restart is required.");
            ConsoleLogger.warn("[Datapack]   • The datapack was manually disabled via /datapack disable.");
            ConsoleLogger.warn("[Datapack]   • level.dat could not be read (world not fully loaded yet).");
            ConsoleLogger.warn("[Datapack] To enable it: run  /datapack enable \"file/UI-Datapack\"  then  /reload,");
            ConsoleLogger.warn("[Datapack] or restart the server.");
            ConsoleLogger.warn("");
        }

        maybeRestart(plugin);
    }

    /** Schedules a server restart if {@code datapack.restart_to_apply} is enabled. */
    private void maybeRestart(JavaPlugin plugin) {
        if (!DatapackModules.isRestartToApply()) return;
        ConsoleLogger.warn("[Datapack] datapack.restart_to_apply: true — restarting the server to load the datapack...");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                Bukkit.getServer().restart();
            } catch (Throwable t) {
                ConsoleLogger.warn("[Datapack] restart() unavailable, using shutdown(): " + t.getMessage());
                Bukkit.getServer().shutdown();
            }
        }, 80L);
    }

    /**
     * Checks the world's {@code level.dat} {@code DataPacks.Enabled} list for the
     * UI-Datapack. Returns true if the world has it enabled.
     */
    private static boolean isLoadedInWorld(File worldRoot) {
        File levelDat = new File(worldRoot, "level.dat");
        if (!levelDat.isFile()) return false;
        try {
            net.minecraft.nbt.CompoundTag root = net.minecraft.nbt.NbtIo.readCompressed(
                    levelDat.toPath(), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            if (root == null || !root.contains("DataPacks")) return false;
            net.minecraft.nbt.CompoundTag packs = root.getCompoundOrEmpty("DataPacks");
            if (!packs.contains("Enabled")) return false;
            net.minecraft.nbt.ListTag enabled = packs.getListOrEmpty("Enabled");
            for (int i = 0; i < enabled.size(); i++) {
                String name = enabled.getString(i).orElse(null);
                if (name != null && name.contains("UI-Datapack")) return true;
            }
            return false;
        } catch (Exception e) {
            ConsoleLogger.warn("[Datapack] Failed to read level.dat datapack list: " + e.getMessage());
            return false;
        }
    }

    /**
     * Resolves the plugin's own JAR file via the class code source.
     * {@code JavaPlugin.getFile()} is protected, so it cannot be used directly.
     */
    private static File getPluginJar(JavaPlugin plugin) {
        try {
            var location = plugin.getClass().getProtectionDomain().getCodeSource().getLocation();
            if (location != null) {
                return new File(location.toURI());
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[Datapack] Failed to resolve plugin JAR: " + e.getMessage());
        }
        return null;
    }

    private void deleteRecursively(File dir) throws Exception {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteRecursively(file);
                } else {
                    if (!file.delete() && file.exists()) {
                        throw new java.io.IOException("Cannot delete file: " + file.getAbsolutePath());
                    }
                }
            }
        }
        if (!dir.delete() && dir.exists()) {
            throw new java.io.IOException("Cannot delete directory: " + dir.getAbsolutePath());
        }
    }

    private void copyFromJar(JavaPlugin plugin, String resourcePath, File targetDir) throws Exception {

        File jar = getPluginJar(plugin);
        if (jar == null || !jar.isFile()) {
            throw new java.io.IOException("Cannot locate the UI-Datapack plugin JAR (code source: " + jar + ")");
        }

        try (ZipFile zip = new ZipFile(jar)) {

            var entries = zip.entries();

            while (entries.hasMoreElements()) {

                var entry = entries.nextElement();

                if (!entry.getName().startsWith(resourcePath)) continue;

                String relative = entry.getName().substring(resourcePath.length());

                if (relative.isEmpty()) continue;

                // Skip disabled datapack parts (config: datapack.modules.*)
                if (!DatapackModules.isPathEnabled(relative)) {
                    ConsoleLogger.info("[Datapack] Skipping (disabled part): " + relative);
                    continue;
                }

                File outFile = new File(targetDir, relative);

                if (entry.isDirectory()) {
                    outFile.mkdirs();
                    continue;
                }

                outFile.getParentFile().mkdirs();

                try (var in = zip.getInputStream(entry);
                     var out = new FileOutputStream(outFile)) {
                    in.transferTo(out);
                }
            }
        }
    }
}
