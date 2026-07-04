package com.mcplugin.infrastructure.core;

import com.mcplugin.infrastructure.util.ConsoleLogger;
import com.mcplugin.infrastructure.util.FileLogger;

import org.bukkit.Bukkit;

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
    // DATAPACK INSTALL
    // =========================
    public void install(Main plugin) throws Exception {
        // В Paper 1.21.4+ (Minecraft 1.21.4+) миры хранятся в новой структуре:
        //   <worlddir>/<level-name>/dimensions/<namespace>/<dimension>/
        // Датапаки должны лежать в корне мира: <level-name>/datapacks/.
        //
        // Во время load: STARTUP Bukkit.getWorlds() пуст (миры ещё не загружены),
        // поэтому читаем level-name напрямую из server.properties.
        // Bukkit.getWorldContainer() доступен всегда — он из bukkit.yml.

        String levelName = "world"; // значение по умолчанию
        File serverDir = new File("").getAbsoluteFile();
        File serverPropsFile = new File(serverDir, "server.properties");
        if (serverPropsFile.exists()) {
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(serverPropsFile)) {
                props.load(fis);
                levelName = props.getProperty("level-name", "world");
            }
        }

        File worldRoot = new File(Bukkit.getWorldContainer(), levelName);
        File datapacksFolder = new File(worldRoot, "datapacks");

        FileLogger.ensureDirectory(datapacksFolder, "Datapack");

        File targetFolder = new File(datapacksFolder, "MC-Datapack");

        // Always re-extract to ensure datapack is up-to-date with plugin version.
        // Delete old folder first, then copy fresh from JAR.
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
