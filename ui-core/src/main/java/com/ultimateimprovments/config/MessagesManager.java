package com.ultimateimprovments.config;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.FileLogger;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;

/**
 * Manages the plugin's messages. Since v26.2 all messages live INSIDE the
 * composite config (per-addon TOML files routed by {@link AddonCatalog}), not in
 * separate files. Legacy support: very old plugin versions shipped standalone
 * messages.yml / messages-en.yml; the {@link #init(Main)} method automatically
 * migrates those legacy files from dataFolder into the composite config (once
 * on first run).
 * <p>
 * Typical call site: {@code MessagesManager.getString("auth.gui.register", default)} —
 * reads {@code messages.auth.gui.register} (no prefix in the path), Russian when the
 * active language is "ru", English (from {@code messages_en....}) otherwise.
 * <p>
 * The public API ({@link #getString(String, String)}) is UNCHANGED.
 */
public class MessagesManager {

    /** Key of the main (Russian) messages section of the composite config. */
    public static final String MESSAGES_KEY = "messages";
    /** Key of the English messages section of the composite config. */
    public static final String MESSAGES_EN_KEY = "messages_en";

    private static Main plugin;

    private MessagesManager() {}

    /**
     * Initializes MessagesManager. Messages live in the per-addon TOML configs
     * (routed by {@link AddonCatalog}); the standalone messages.yml/messages-en.yml
     * files are NOT needed. Backward compatibility: if old files from previous
     * plugin versions remain in dataFolder — migrate their content into the
     * composite config under {@code messages:} / {@code messages_en:} and delete
     * the files.
     */
    public static void init(Main plugin) {
        MessagesManager.plugin = plugin;
        migrateFromStandaloneFiles();
        ConsoleLogger.info("[Messages] Messages live in the composite config under '" + MESSAGES_KEY
                + "' and '" + MESSAGES_EN_KEY + "' sections.");
    }

    /**
     * Returns a string for the CURRENT language ({@code messages.lang}, "ru"/"en"),
     * with fallback to the other language and then to {@code def}.
     * <p>
     * Accepts a path WITHOUT the section prefix: a call site invoking
     * {@code getString("auth.gui.register", default)} reads
     * {@code messages.auth.gui.register} (ru) or {@code messages_en.auth.gui.register} (en).
     * Messages are routed by {@link CompositeConfig} to the owning addon's TOML file.
     */
    public static String getString(String path, String def) {
        if (plugin == null) return def;
        FileConfiguration config = plugin.getConfig();
        boolean ru = isRuLang(config);
        String primary = ru ? MESSAGES_KEY : MESSAGES_EN_KEY;
        String fallback = ru ? MESSAGES_EN_KEY : MESSAGES_KEY;
        // 1. Current language
        String value = config.getString(primary + "." + path, null);
        if (value != null) return value;
        // 2. Other language fallback
        value = config.getString(fallback + "." + path, null);
        if (value != null) return value;
        return def;
    }

    /** Reads the effective language: per-addon override → global {@code messages.lang}. */
    private static boolean isRuLang(FileConfiguration config) {
        String lang = config.getString("messages.lang", "en");
        return "ru".equalsIgnoreCase(lang);
    }

    /**
     * Returns a string from the English section directly (bypassing the fallback to Russian).
     * Used rarely — mostly for tests or logging.
     */
    public static String getStringEn(String path, String def) {
        if (plugin == null) return def;
        return plugin.getConfig().getString(MESSAGES_EN_KEY + "." + path, def);
    }

    /**
     * Writes a value into the RU messages section and saves the owning addon's
     * TOML (routing happens in {@link CompositeConfig}#set). Used by the plugin
     * core, e.g. for dynamic localization in GUIs.
     */
    public static void setString(String path, String value) {
        if (plugin == null) return;
        FileConfiguration config = plugin.getConfig();
        config.set(MESSAGES_KEY + "." + path, value);
        try {
            plugin.saveConfig();
        } catch (Exception e) {
            FileLogger.logError("Messages", "Failed to save config: " + e.getMessage());
        }
    }

    /**
     * Sets the global UI language ("ru" or "en") in the core config and persists it.
     * @return the previously active language
     */
    public static String setLanguage(String lang) {
        FileConfiguration config = plugin.getConfig();
        String previous = currentLanguage();
        String normalized = "ru".equalsIgnoreCase(lang) ? "ru" : "en";
        config.set("messages.lang", normalized);
        try {
            plugin.saveConfig();
        } catch (Exception e) {
            FileLogger.logError("Messages", "Failed to save language: " + e.getMessage());
        }
        return previous;
    }

    /** @return the currently active UI language ("ru" or "en"). */
    public static String currentLanguage() {
        if (plugin == null) return "en";
        return "ru".equalsIgnoreCase(plugin.getConfig().getString("messages.lang", "en")) ? "ru" : "en";
    }

    /**
     * Whether the messages sections exist in the composite config (the standalone
     * messages.yml file no longer exists). Left for compatibility.
     */
    public static boolean isLoaded() {
        return plugin != null && plugin.getConfig().isSet(MESSAGES_KEY);
    }

    /** For backward compatibility. Now points at the per-addon TOML layout. */
    public static String getMessagesFileName() {
        return "configs/UI-<Addon>.toml#" + MESSAGES_KEY;
    }

    // ============================================================
    // Migration of legacy standalone messages files
    // ============================================================

    /**
     * If standalone messages.yml/messages-en.yml from old plugin versions remain in
     * dataFolder — copy their content into the composite config under the corresponding
     * keys and delete them.
     * <p>
     * For safety: never overwrites existing user keys.
     */
    private static void migrateFromStandaloneFiles() {
        if (plugin == null) return;
        File dataFolder = plugin.getDataFolder();
        File ru = new File(dataFolder, "messages.yml");
        File en = new File(dataFolder, "messages-en.yml");
        boolean migrated = false;
        FileConfiguration config = plugin.getConfig();
        if (ru.exists()) {
            migrated |= migrateFile(ru, MESSAGES_KEY, config);
        }
        if (en.exists()) {
            migrated |= migrateFile(en, MESSAGES_EN_KEY, config);
        }
        if (migrated) {
            try {
                plugin.saveConfig();
                plugin.reloadConfig();
            } catch (Exception e) {
                FileLogger.logError("Messages", "Failed to save config after migration: " + e.getMessage());
            }
        }
    }

    /**
     * Copies keys from a YAML file into the given config section; existing keys
     * are NOT overwritten. After a successful merge deletes the source file.
     * @return true if something was migrated or the file was processed
     */
    private static boolean migrateFile(File source, String targetKey, FileConfiguration config) {
        try {
            org.bukkit.configuration.file.FileConfiguration sourceCfg =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(source);
            ConfigurationSection sourceSection = sourceCfg;
            int copied = copySectionKeys(sourceSection, config, targetKey);
            if (copied > 0) {
                ConsoleLogger.info("[Messages] Migrated " + copied + " key(s) from "
                        + source.getName() + " to the config#" + targetKey);
            }
            if (!source.delete()) {
                ConsoleLogger.warn("[Messages] Failed to delete legacy file: " + source.getName());
                return true;
            }
            return true;
        } catch (Exception e) {
            ConsoleLogger.warn("[Messages] Failed to migrate " + source.getName() + ": " + e.getMessage());
            return false;
        }
    }

    /** Recursively copies all leaf keys from {@code source} into {@code target.getConfigurationSection(targetKey)}. */
    private static int copySectionKeys(ConfigurationSection source, FileConfiguration target, String targetKey) {
        int count = 0;
        for (String key : source.getKeys(false)) {
            Object val = source.get(key);
            String full = targetKey + "." + key;
            if (val instanceof ConfigurationSection) {
                count += copySectionKeys((ConfigurationSection) val, target, full);
            } else {
                if (!target.isSet(full)) {
                    target.set(full, val);
                    count++;
                }
            }
        }
        return count;
    }
}
