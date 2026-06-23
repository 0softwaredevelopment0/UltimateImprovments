package com.mcplugin.infrastructure.core;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.FileLogger;

import org.bukkit.Bukkit;

import java.io.File;
import java.io.FileOutputStream;
import java.util.logging.Logger;
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
        //   <worlddir>/<worldname>/dimensions/<namespace>/<dimension>/
        // Bukkit.getWorlds().get(0).getWorldFolder() возвращает путь к измерению
        // (например, world/dimensions/minecraft/overworld/), НО датапаки должны
        // лежать в корне мира: <world>/datapacks/, а НЕ в подпапке измерения.
        //
        // Используем Bukkit.getWorldContainer() + имя мира — это гарантированно
        // даёт корневую папку мира независимо от структуры измерений,
        // уважает настройку world-container в bukkit.yml и не требует
        // навигации по подпапкам dimensions/.

        if (Bukkit.getWorlds().isEmpty()) {
            throw new IllegalStateException("No worlds loaded yet — cannot install datapack");
        }

        File worldRoot = new File(
                Bukkit.getWorldContainer(),
                Bukkit.getWorlds().get(0).getName()
        );

        File datapacksFolder = new File(worldRoot, "datapacks");
        Logger log = plugin.getLogger();

        FileLogger.ensureDirectory(datapacksFolder, "Datapack", log);

        File targetFolder = new File(datapacksFolder, "MC-Datapack");

        // Always re-extract to ensure datapack is up-to-date with plugin version.
        // Delete old folder first, then copy fresh from JAR.
        if (targetFolder.exists()) {
            log.info("[Datapack] Reinstalling existing datapack (folder exists: MC-Datapack)");
            deleteRecursively(targetFolder);
        } else {
            log.info("[Datapack] Installing new datapack...");
        }

        targetFolder.mkdirs();
        copyFromJar(plugin, "datapacks/MC-Datapack/", targetFolder);

        log.info("[Datapack] ✓ Installed to " + targetFolder.getAbsolutePath());
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
