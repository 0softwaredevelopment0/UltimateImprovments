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
            File worldFolder = Bukkit.getWorlds().get(0).getWorldFolder();
            File datapacksFolder = new File(worldFolder, "datapacks");

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
