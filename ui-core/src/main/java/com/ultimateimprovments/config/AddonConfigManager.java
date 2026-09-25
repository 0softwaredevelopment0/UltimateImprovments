package com.ultimateimprovments.config;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.core.UltimateDirs;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AddonConfigManager — one TOML config per addon under {@code plugins/UltimateImprovments/configs/}.
 * <p>
 * The family shares a single data folder ({@link UltimateDirs}); each addon gets its own
 * {@code configs/UI-<Addon>.toml} that holds BOTH its settings and its messages
 * ({@code messages} RU + {@code messages_en} EN) so the whole addon is configured
 * in one editable file. The per-addon language lives in {@code lang} inside each file
 * ("en" | "ru"); {@code messages.lang} in UI-Core.toml keeps working as the
 * global fallback.
 * <p>
 * The public surface of {@code Main.getConfig()} is unchanged: keys are routed to the
 * owning addon's file by their first path segment ({@link #routeKey(String)}).
 */
public final class AddonConfigManager {

    /** Internal marker used to track dirty per-addon files. */
    public static final String DIRTY_KEY = "__dirty";

    /** Sub-folder inside the shared data folder holding all per-addon TOML files. */
    public static final String CONFIGS_DIR = UltimateDirs.CONFIGS;

    private static final Map<String, FileConfiguration> loaded = new LinkedHashMap<>();
    private static final List<PerAddonFile> files = new ArrayList<>();
    private static volatile boolean initialized = false;

    private AddonConfigManager() {}

    /** Bookkeeping for one per-addon TOML file. */
    private static final class PerAddonFile {
        final String addon;
        final File toml;
        PerAddonFile(String addon, File toml) { this.addon = addon; this.toml = toml; }
    }

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    /**
     * Generates every missing {@code configs/UI-<Addon>.toml} (settings + messages
     * templates from the bundled fragments) and loads all existing files into memory.
     */
    public static synchronized void init() {
        loaded.clear();
        files.clear();

        for (String addon : AddonCatalog.catalog()) {
            File toml = UltimateDirs.addonConfigToml(addon);
            files.add(new PerAddonFile(addon, toml));
            if (!toml.exists()) {
                generateFromBundle(addon, toml);
            }
            loaded.put(addon, loadAddonToml(addon, toml));
        }

        // Migrate user edits from a legacy monolithic config (if present) on top
        // of the generated files, then persist them into the per-addon files.
        migrateLegacyMonolith();

        ConsoleLogger.info("[Config] Addon configs in " + UltimateDirs.CONFIGS + ": "
                + files.size() + " file(s)");
        initialized = true;
    }

    /** Whether {@link #init()} has run (composite config layer is active). */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * One-time migration of a legacy monolithic config ({@code config.toml} or
     * {@code config.yml} inside the shared folder) into the per-addon files.
     * Every root section is moved into the owning addon's TOML, message sections
     * are routed by group. The legacy file is renamed to {@code *.migrated}
     * (user data is never deleted). Runs inside {@link #init()} after loading.
     */
    private static synchronized void migrateLegacyMonolith() {
        File legacyToml = UltimateDirs.file(TomlConfigManager.CONFIG_TOML);
        File legacyYml = UltimateDirs.file(TomlConfigManager.CONFIG_YML);
        File source = legacyToml.exists() ? legacyToml : (legacyYml.exists() ? legacyYml : null);
        if (source == null) return;

        YamlConfiguration legacy = new YamlConfiguration();
        boolean parsed = false;
        if (source.getName().endsWith(".toml")) {
            try (InputStream in = new java.io.FileInputStream(source)) {
                com.moandjiezana.toml.Toml toml = new com.moandjiezana.toml.Toml()
                        .read(new InputStreamReader(in, StandardCharsets.UTF_8));
                Map<String, Object> map = toml.toMap();
                if (map != null) flattenInto(map, "", legacy);
                parsed = true;
            } catch (Exception e) {
                ConsoleLogger.warn("[Config] Legacy config.toml unreadable: " + e.getMessage());
            }
        } else {
            try {
                legacy.load(source);
                parsed = true;
            } catch (Exception e) {
                ConsoleLogger.warn("[Config] Legacy config.yml unreadable: " + e.getMessage());
            }
        }
        if (!parsed) return;

        int moved = 0;
        for (String root : legacy.getKeys(false)) {
            // Message sections are routed by their group (2nd segment).
            if (root.equals("messages") || root.equals("messages_en")) {
                org.bukkit.configuration.ConfigurationSection section = legacy.getConfigurationSection(root);
                if (section == null) continue;
                for (String group : section.getKeys(false)) {
                    if (group.equalsIgnoreCase("lang")) {
                        viewOf(AddonCatalog.CORE).set("messages.lang", section.get(group));
                        moved++;
                        continue;
                    }
                    String targetAddon = AddonCatalog.addonOfMsgGroup(group);
                    org.bukkit.configuration.ConfigurationSection groupSection = section.getConfigurationSection(group);
                    if (groupSection == null) continue;
                    FileConfiguration targetView = viewOf(targetAddon);
                    for (String path : groupSection.getKeys(true)) {
                        if (!groupSection.isConfigurationSection(path)) {
                            // En section keeps its prefix; ru is written as messages.<group>...
                            String prefix = root.equals("messages") ? "messages" : "messages_en";
                            targetView.set(prefix + "." + group + "." + path, groupSection.get(path));
                            moved++;
                        }
                    }
                }
                continue;
            }

            String addon = AddonCatalog.addonOfRootKey(root);
            FileConfiguration view = viewOf(addon);
            org.bukkit.configuration.ConfigurationSection section = legacy.getConfigurationSection(root);
            if (section != null) {
                for (String path : section.getKeys(true)) {
                    if (!section.isConfigurationSection(path)) {
                        view.set(root + "." + path, section.get(path));
                        moved++;
                    }
                }
            } else {
                view.set(root, legacy.get(root));
                moved++;
            }
        }

        if (moved == 0) return;

        // Persist every touched file and retire the legacy monolith (never deleted).
        for (PerAddonFile f : files) {
            FileConfiguration cfg = loaded.get(f.addon);
            if (cfg != null && cfg.getBoolean(DIRTY_KEY, false)) {
                writeAddonToml(f.addon, f.toml, cfg);
                cfg.set(DIRTY_KEY, null);
            }
        }
        File retired = new File(source.getParentFile(), source.getName() + ".migrated");
        try {
            java.nio.file.Files.move(source.toPath(), retired.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            ConsoleLogger.warn("[Config] Could not retire legacy config: " + e.getMessage());
        }
        ConsoleLogger.info("[Config] Migrated " + moved + " key(s) from " + source.getName()
                + " into configs/UI-<Addon>.toml");
    }

    /** Re-reads every per-addon TOML file (called by {@code /ui reload}). */
    public static synchronized void reloadAll() {
        for (PerAddonFile f : files) {
            loaded.put(f.addon, loadAddonToml(f.addon, f.toml));
        }
        ConsoleLogger.info("[Config] Addon configs reloaded (" + files.size() + " file(s))");
    }

    /**
     * Saves in-memory changes back to disk.
     * Only files that were actually touched are rewritten ({@link #touch(String)}).
     */
    public static synchronized void saveAll() {
        for (PerAddonFile f : files) {
            FileConfiguration cfg = loaded.get(f.addon);
            if (cfg == null || !cfg.getBoolean("__dirty", false)) continue;
            writeAddonToml(f.addon, f.toml, cfg);
            cfg.set("__dirty", null);
        }
    }

    /** Marks an addon's config as modified (persisted by the next saveAll()). */
    public static void touch(String addon) {
        FileConfiguration cfg = loaded.get(addon);
        if (cfg != null) cfg.set("__dirty", true);
    }

    /** Marks the config that owns {@code key} as modified. */
    public static void touchKey(String key) {
        touch(addonOfKey(key));
    }

    // ========================================================================
    // VIEWS (for CompositeConfig routing)
    // ========================================================================

    /** @return the in-memory view of one addon's config (never null for known addons). */
    public static synchronized FileConfiguration viewOf(String addon) {
        FileConfiguration cfg = loaded.get(addon);
        if (cfg != null) return cfg;
        // unknown addon name — create an ephemeral in-memory view (not persisted)
        YamlConfiguration fresh = new YamlConfiguration();
        loaded.put(addon, fresh);
        return fresh;
    }

    /** @return snapshot of every loaded per-addon view (including the core's). */
    public static synchronized List<FileConfiguration> allViews() {
        return new ArrayList<>(loaded.values());
    }

    // ========================================================================
    // KEY ROUTING
    // ========================================================================

    /** @return the addon owning the given config key (first path segment). */
    public static String addonOfKey(String key) {
        String root = key;
        int dot = key.indexOf('.');
        if (dot > 0) root = key.substring(0, dot);
        return AddonCatalog.addonOfRootKey(root);
    }

    /** @return true if the key belongs to a per-addon file (false = UI-Core). */
    public static boolean isAddonKey(String key) {
        return !addonOfKey(key).equals(AddonCatalog.CORE);
    }

    /**
     * Reads a raw value from the owning addon's config.
     * Falls back to the bundled fragment when the key is missing on disk.
     */
    public static Object get(String key) {
        String addon = addonOfKey(key);
        FileConfiguration cfg = loaded.get(addon);
        if (cfg == null) return null;
        if (cfg.isSet(key)) return cfg.get(key);
        return bundledFragmentValue(addon, key);
    }

    /** Set + immediate persist into the owning addon's TOML. */
    public static void set(String key, Object value) {
        String addon = addonOfKey(key);
        FileConfiguration cfg = loaded.get(addon);
        if (cfg == null) return;
        cfg.set(key, value);
        writeAddonToml(addon, UltimateDirs.addonConfigToml(addon), cfg);
    }

    // ========================================================================
    // INTERNALS
    // ========================================================================

    /**
     * Loads (or creates) the in-memory view of one addon TOML. When the file is
     * unreadable its contents are salvaged ({@link TomlCrashSalvage}); if it still
     * cannot be parsed the file is backed up and regenerated from the bundle.
     */
    private static FileConfiguration loadAddonToml(String addon, File toml) {
        TomlCrashSalvage.salvageFile(toml, m -> ConsoleLogger.warn("[ConfigSalvage/" + addon + "] " + m));
        YamlConfiguration view = new YamlConfiguration();
        if (toml.exists()) {
            try (InputStream in = new java.io.FileInputStream(toml)) {
                com.moandjiezana.toml.Toml parsed = new com.moandjiezana.toml.Toml()
                        .read(new InputStreamReader(in, StandardCharsets.UTF_8));
                Map<String, Object> map = parsed.toMap();
                if (map != null) flattenInto(map, "", view);
            } catch (Exception e) {
                ConsoleLogger.warn("[Config/" + addon + "] unreadable (" + e.getMessage()
                        + ") — backing up and regenerating");
                backupFile(toml);
                generateFromBundle(addon, toml);
                if (toml.exists()) {
                    try (InputStream in = new java.io.FileInputStream(toml)) {
                        com.moandjiezana.toml.Toml parsed = new com.moandjiezana.toml.Toml()
                                .read(new InputStreamReader(in, StandardCharsets.UTF_8));
                        Map<String, Object> map = parsed.toMap();
                        if (map != null) flattenInto(map, "", view);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        // Repair: fill missing keys from the bundled fragment so new plugin versions
        // add new settings/messages even when the file already exists on disk.
        int repaired = repairFromBundle(addon, view);
        if (repaired > 0) {
            writeAddonToml(addon, toml, view);
        }
        return view;
    }

    /** Fills missing keys from the bundled fragment. @return number of added keys. */
    private static int repairFromBundle(String addon, YamlConfiguration view) {
        FileConfiguration ref = bundledFragment(addon);
        if (ref == null) return 0;
        int added = 0;
        for (String path : ref.getKeys(true)) {
            if (ref.isConfigurationSection(path)) continue;
            if (!view.isSet(path)) {
                view.set(path, ref.get(path));
                added++;
            }
        }
        return added;
    }

    /** Writes the in-memory view back to {@code configs/<addon>.toml}. */
    private static void writeAddonToml(String addon, File toml, FileConfiguration view) {
        try {
            Map<String, Object> root = TomlConfigManager.sectionToMap(view);
            // internal markers must not leak into the file
            root.remove(DIRTY_KEY);
            new com.moandjiezana.toml.TomlWriter().write(root, toml);
        } catch (Exception e) {
            ConsoleLogger.warn("[Config/" + addon + "] Failed to save " + toml.getName()
                    + ": " + e.getMessage());
        }
    }

    /** Creates the initial {@code configs/<addon>.toml} from the bundled fragments. */
    private static void generateFromBundle(String addon, File toml) {
        YamlConfiguration merged = new YamlConfiguration();
        FileConfiguration base = bundledFragment(addon);
        if (base != null) {
            // The main fragment already carries settings + messages (RU) + messages_en (EN).
            for (String path : base.getKeys(true)) {
                if (!base.isConfigurationSection(path)) merged.set(path, base.get(path));
            }
        }
        toml.getParentFile().mkdirs();
        writeAddonToml(addon, toml, merged);
        ConsoleLogger.info("[Config] Generated " + UltimateDirs.CONFIGS + "/" + toml.getName());
    }

    /** Loads the bundled resource fragment {@code config/<Addon>.yml} (or null). */
    private static FileConfiguration bundledFragment(String name) {
        Main plugin = Main.getInstance();
        if (plugin == null) return null;
        String res = "config/" + name + ".yml";
        try (InputStream in = plugin.getResource(res)) {
            if (in == null) return null;
            return org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            ConsoleLogger.warn("[Config] Failed to load bundled fragment " + res + ": " + e.getMessage());
            return null;
        }
    }

    /** Single-key lookup in the bundled fragment (fallback for missing disk keys). */
    private static Object bundledFragmentValue(String addon, String key) {
        FileConfiguration ref = bundledFragment(addon);
        if (ref == null) return null;
        return ref.get(key);
    }

    private static void backupFile(File file) {
        try {
            File backup = new File(file.getParentFile(), file.getName() + ".broken");
            java.nio.file.Files.move(file.toPath(), backup.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
        }
    }

    /** Recursively flattens a TOML map into the Bukkit view (dotted paths). */
    private static void flattenInto(Map<String, Object> map, String prefix, YamlConfiguration target) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String path = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            Object value = e.getValue();
            if (value instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) nested;
                flattenInto(nestedMap, path, target);
            } else {
                target.set(path, value);
            }
        }
    }
}
