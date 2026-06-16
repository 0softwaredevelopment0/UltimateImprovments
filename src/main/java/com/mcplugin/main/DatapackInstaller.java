package com.mcplugin.main;

import com.mcplugin.Main;

import org.bukkit.Bukkit;

import java.io.File;
import java.io.FileOutputStream;
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
    public void install(Main plugin) {
        try {
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
            File worldRoot = new File(
                    Bukkit.getWorldContainer(),
                    Bukkit.getWorlds().get(0).getName()
            );

            File datapacksFolder = new File(worldRoot, "datapacks");

            if (!datapacksFolder.exists()) {
                datapacksFolder.mkdirs();
            }

            File targetFolder = new File(datapacksFolder, "MC-Datapack");

            if (targetFolder.exists()) return;

            targetFolder.mkdirs();
            copyFromJar(plugin, "datapacks/MC-Datapack/", targetFolder);

        } catch (Exception e) {
            plugin.getLogger().severe("[DATAPACK] Failed: " + e.getMessage());
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
