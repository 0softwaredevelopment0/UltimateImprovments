package com.ultimateimprovments.config;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.*;
import java.nio.file.Files;

/**
 * Validates the integrity of config.yml at plugin startup.
 * <p>
 * Since v26.2 everything lives in config.yml (messages + guide + settings + meta-hash).
 * Therefore the standalone messages.yml file is no longer validated here —
 * the migration of legacy files is done in {@link MessagesManager#init(Main)} and
 * {@link ConfigGuideManager#init(Main)}, and value validation only for config.yml.
 * <p>
 * Uses {@link ConfigRepairManager} for smart repair:
 * missing keys are ADDED to the end of the file, existing values are NOT touched.
 * <p>
 * Additionally performs config.yml value validation via
 * {@link ConfigValueValidator} (types, ranges, empty strings, characters).
 */
public class ConfigIntegrityValidator {

    private ConfigIntegrityValidator() {}

    // =========================
    // CONFIG.YML VALIDATION
    // =========================
    public static void validate(Main plugin) {
        File configFile = new File(plugin.getDataFolder(), "config.yml");

        // 🧹 Step 1: remove duplicate root-level keys (keep the FIRST occurrences)
        boolean cleaned = YamlDuplicateCleaner.cleanDuplicates(configFile, "config.yml");
        if (cleaned) {
            plugin.reloadConfig();
        }

        FileConfiguration config = plugin.getConfig();

        // Smart repair: missing keys are added to the end of the file
        boolean repaired = ConfigRepairManager.repair(plugin, "config.yml", config, configFile);

        if (repaired) {
            plugin.reloadConfig();
            config = plugin.getConfig();
        }

        // 🧹 One-time cleanup: if repair did NOT run (neither Group A nor Group B),
        // but the file still has markers from old repair runs — re-save the config
        // via config.save(). This removes YAML duplicates of root sections.
        //
        // ⚠ IMPORTANT: Do not run if repair already worked.
        if (!repaired && fileContainsMarker(configFile)) {
            try {
                config.save(configFile);
                plugin.reloadConfig();
                config = plugin.getConfig();
                ConsoleLogger.info("[ConfigRepair] Cleaned up duplicate YAML sections from config.yml");
            } catch (IOException e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "[ConfigRepair] Failed to clean up config file", e);
            }
        }

        // Value validation (delegated to ConfigValueValidator)
        ConfigValueValidator.validateValues(plugin, config);
    }

    /**
     * Checks whether the file contains a marker of old repair additions (from previous
     * versions, when messages were stored in a separate file). If so — there are YAML
     * duplicates that need to be cleaned up on the next config.yml save.
     */
    private static boolean fileContainsMarker(File file) {
        if (!file.exists()) return false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("#") && line.contains("Missing keys")) {
                    return true;
                }
            }
        } catch (IOException e) {
            // If the file cannot be read — skip the cleanup
        }
        return false;
    }

    /**
     * Removes legacy files from dataFolder. Called after a successful validation.
     * <p>
     * Since v26.2 these files are not needed — everything is inside config.yml.
     * If they remain — quietly delete them so the user's directory stays clean.
     */
    public static void cleanupLegacyFiles(Main plugin) {
        File data = plugin.getDataFolder();
        String[] legacy = {
                "messages.yml",
                "messages-en.yml",
                "plugin-guide.hash"
        };
        for (String name : legacy) {
            File f = new File(data, name);
            if (f.exists()) {
                try {
                    if (f.delete()) {
                        ConsoleLogger.info("[ConfigIntegrity] Removed legacy file: " + name);
                    }
                } catch (Exception e) {
                    ConsoleLogger.warn("[ConfigIntegrity] Could not delete legacy " + name + ": " + e.getMessage());
                }
            }
        }
    }
}
