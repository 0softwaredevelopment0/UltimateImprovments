package com.ultimateimprovments.config;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;

import java.io.File;

/**
 * Validates config values at plugin startup.
 * <p>
 * With the per-addon TOML backend ({@code configs/UI-<Addon>.toml}) duplicate
 * sections are impossible (toml4j map model) and missing keys are repaired by
 * {@link AddonConfigManager} from the bundled fragments — so only value validation
 * ({@link ConfigValueValidator}) runs here. The former legacy YAML cleanup paths
 * were removed together with the monolithic config.
 */
public class ConfigIntegrityValidator {

    private ConfigIntegrityValidator() {}

    // =========================
    // CONFIG VALIDATION (per-addon TOML backend)
    // =========================
    public static void validate(Main plugin) {
        // ── Per-addon TOML backend: duplicates are impossible, missing keys are
        // filled by AddonConfigManager.loadAddonToml(). Only value validation runs.
        File legacyToml = new File(com.ultimateimprovments.core.UltimateDirs.base(),
                TomlConfigManager.CONFIG_TOML);
        if (legacyToml.exists()) {
            // A stray legacy monolithic config.toml from the pre-split layout —
            // keep the file (user data), it is simply no longer read.
            ConsoleLogger.info("[Config] Legacy monolithic config.toml found in "
                    + com.ultimateimprovments.core.UltimateDirs.DIR_NAME
                    + " — settings now live in configs/UI-<Addon>.toml");
        }
        ConfigValueValidator.validateValues(plugin, plugin.getConfig());
    }

    /**
     * Removes legacy message files from the shared folder. Kept for compatibility
     * with earlier startup code paths.
     */
    public static void cleanupLegacyFiles(Main plugin) {
        File data = com.ultimateimprovments.core.UltimateDirs.base();
        String[] legacy = {
                "messages.yml",
                "messages-en.yml"
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
