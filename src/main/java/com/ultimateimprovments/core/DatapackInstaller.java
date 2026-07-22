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
     * Автоматически находит папку datapacks в директории мира,
     * независимо от версии Minecraft и структуры папок.
     * <p>
     * Приоритет поиска:
     * <ol>
     *   <li>Bukkit.getWorlds() — папка первого загруженного мира (самый надёжный)</li>
     *   <li>server.properties → level-name → Bukkit.getWorldContainer()</li>
     *   <li>Папка "world" в Bukkit.getWorldContainer() (значение по умолчанию)</li>
     * </ol>
     * Если папка datapacks не найдена — она создаётся.
     */
    private static File findDatapacksFolder() {
        File worldRoot = findWorldRoot();

        File datapacksFolder = new File(worldRoot, "datapacks");
        FileLogger.ensureDirectory(datapacksFolder, "Datapack");
        return datapacksFolder;
    }

    /**
     * Находит корневую директорию мира, в которую нужно устанавливать датапаки.
     */
    private static File findWorldRoot() {
        // 1. Пытаемся получить папку мира через Bukkit API (самый надёжный способ)
        World firstWorld = null;
        try {
            if (!Bukkit.getWorlds().isEmpty()) {
                firstWorld = Bukkit.getWorlds().get(0);
            }
        } catch (Exception ignored) {
            // Bukkit.getWorlds() может быть не готов на ранних этапах загрузки
        }

        if (firstWorld != null) {
            File worldFolder = firstWorld.getWorldFolder();
            if (worldFolder != null && worldFolder.isDirectory()) {
                ConsoleLogger.info("[Datapack] World root found via Bukkit API: " + worldFolder.getAbsolutePath());
                return worldFolder;
            }
        }

        // 2. Fallback: читаем level-name из server.properties
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

        // 3. Если папка не найдена, пробуем стандартную "world"
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

        File targetFolder = new File(datapacksFolder, "MC-Datapack");

        // Always re-extract to ensure datapack is up-to-date with plugin version.
        if (targetFolder.exists()) {
            ConsoleLogger.info("[Datapack] Reinstalling existing datapack (folder exists: MC-Datapack)");
            deleteRecursively(targetFolder);
        } else {
            ConsoleLogger.info("[Datapack] Installing new datapack...");
        }

        targetFolder.mkdirs();
        copyFromJar(plugin, "datapacks/MC-Datapack/", targetFolder);

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
