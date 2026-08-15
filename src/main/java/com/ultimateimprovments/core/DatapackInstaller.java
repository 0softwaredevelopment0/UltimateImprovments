package com.ultimateimprovments.core;

import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.FileLogger;

import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;
import java.util.zip.ZipFile;

public class DatapackInstaller {

    private static DatapackInstaller instance;

    public static void init(Main plugin) {
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

    public void install(Main plugin) throws Exception {
        File datapacksFolder = findDatapacksFolder();

        File targetFolder = new File(datapacksFolder, "UI-Datapack");

        // Always re-extract to ensure datapack is up-to-date with plugin version.
        if (targetFolder.exists()) {
            ConsoleLogger.info("[Datapack] Reinstalling existing datapack (folder exists: UI-Datapack)");
            deleteRecursively(targetFolder);
        } else {
            ConsoleLogger.info("[Datapack] Installing new datapack...");
        }

        targetFolder.mkdirs();
        copyFromJar(plugin, "datapacks/UI-Datapack/", targetFolder);

        ConsoleLogger.success("[Datapack] Installed to " + targetFolder.getAbsolutePath());
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

    private void copyFromJar(Main plugin, String resourcePath, File targetDir) throws Exception {

        var jar = plugin.getPluginFile();

        try (ZipFile zip = new ZipFile(jar)) {

            var entries = zip.entries();

            while (entries.hasMoreElements()) {

                var entry = entries.nextElement();

                if (!entry.getName().startsWith(resourcePath)) continue;

                String relative = entry.getName().substring(resourcePath.length());

                if (relative.isEmpty()) continue;

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
