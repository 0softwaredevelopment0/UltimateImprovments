package com.mcplugin.config;

import com.mcplugin.Main;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

/**
 * Проверяет целостность config.yml и messages.yml при старте плагина.
 * <p>
 * Вместо хардкоженного списка секций загружает дефолтный файл из ресурсов
 * JAR и динамически извлекает ВСЕ ключи. Если в пользовательском файле
 * не хватает каких-либо ключей — файл переименовывается в compromised-*
 * и создаётся свежий из ресурсов.
 * <p>
 * Дополнительно выполняет валидацию значений config.yml через
 * {@link ConfigValueValidator} (типы, диапазоны, пустые строки, символы).
 */
public class ConfigIntegrityValidator {

    private static final String CONFIG_COMPROMISED = "compromised-config.yml";
    private static final String MESSAGES_COMPROMISED = "compromised-messages.yml";
    private static final String MESSAGES_FILE = "messages.yml";

    private ConfigIntegrityValidator() {}

    // =========================
    // CONFIG.YML VALIDATION
    // =========================
    public static void validate(Main plugin) {
        FileConfiguration config = plugin.getConfig();
        FileConfiguration defaultConfig = loadDefaultResource(plugin, "config.yml");

        if (defaultConfig != null) {
            Set<String> requiredPaths = collectAllPaths(defaultConfig);
            List<String> missing = findMissingPaths(config, requiredPaths);

            if (!missing.isEmpty()) {
                handleCompromised(plugin, "config.yml", CONFIG_COMPROMISED, missing,
                        () -> {
                            plugin.saveDefaultConfig();
                            plugin.reloadConfig();
                        });
                // После пересоздания — берём свежий конфиг для валидации значений
                config = plugin.getConfig();
            }
        }

        // Валидация значений (делегировано в ConfigValueValidator)
        ConfigValueValidator.validateValues(plugin, config);
    }

    // =========================
    // MESSAGES.YML VALIDATION
    // =========================
    public static void validateMessages(Main plugin) {
        File messagesFile = new File(plugin.getDataFolder(), MESSAGES_FILE);
        if (!messagesFile.exists()) {
            // При первом запуске MessagesManager создаст файл из ресурсов
            return;
        }

        FileConfiguration userMessages = YamlConfiguration.loadConfiguration(messagesFile);
        FileConfiguration defaultMessages = loadDefaultResource(plugin, MESSAGES_FILE);
        if (defaultMessages == null) {
            plugin.getLogger().warning("[ConfigValidator] Cannot validate messages.yml: default resource not found.");
            return;
        }

        Set<String> requiredPaths = collectAllPaths(defaultMessages);
        List<String> missing = findMissingPaths(userMessages, requiredPaths);

        if (!missing.isEmpty()) {
            handleCompromised(plugin, MESSAGES_FILE, MESSAGES_COMPROMISED, missing,
                    () -> plugin.saveResource(MESSAGES_FILE, true));
        }
    }

    // =========================
    // GENERIC: извлечение ключей из дефолтного ресурса
    // =========================

    /**
     * Загружает YAML-файл из ресурсов JAR (plugin.getResource()).
     */
    private static FileConfiguration loadDefaultResource(Main plugin, String resourcePath) {
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) return null;
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in));
        } catch (Exception e) {
            plugin.getLogger().warning("[ConfigValidator] Failed to load default " + resourcePath + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Рекурсивно извлекает ВСЕ пути ключей из ConfigurationSection.
     * Возвращает Set с путями в формате "parent.child.leaf".
     * Порядок сохраняется (LinkedHashSet).
     */
    private static Set<String> collectAllPaths(ConfigurationSection section) {
        Set<String> paths = new LinkedHashSet<>();
        if (section == null) return paths;
        collectPathsRecursive(section, "", paths);
        return paths;
    }

    private static void collectPathsRecursive(ConfigurationSection section, String prefix, Set<String> paths) {
        for (String key : section.getKeys(false)) {
            String fullPath = prefix.isEmpty() ? key : prefix + "." + key;
            paths.add(fullPath);
            if (section.isConfigurationSection(key)) {
                ConfigurationSection child = section.getConfigurationSection(key);
                if (child != null) {
                    collectPathsRecursive(child, fullPath, paths);
                }
            }
        }
    }

    /**
     * Проверяет, все ли requiredPaths присутствуют в config.
     */
    private static List<String> findMissingPaths(FileConfiguration config, Set<String> requiredPaths) {
        List<String> missing = new ArrayList<>();
        for (String path : requiredPaths) {
            if (!config.isSet(path)) missing.add(path);
        }
        return missing;
    }

    // =========================
    // GENERIC: обработка compromised-файла
    // =========================

    /**
     * Переименовывает повреждённый файл в compromised-* и создаёт свежий из ресурсов.
     *
     * @param plugin         инстанс плагина
     * @param fileName       имя файла (например "config.yml" или "messages.yml")
     * @param compromisedName имя для compromised-копии
     * @param missing        список отсутствующих ключей (для лога)
     * @param restoreAction  действие для восстановления файла из ресурсов
     */
    private static void handleCompromised(Main plugin, String fileName, String compromisedName,
                                           List<String> missing, Runnable restoreAction) {
        plugin.getLogger().warning("");
        plugin.getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        plugin.getLogger().warning("!  " + fileName.toUpperCase() + " INTEGRITY CHECK FAILED!              !");
        plugin.getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        plugin.getLogger().warning("!  Missing " + missing.size() + " key(s):");
        for (int i = 0; i < Math.min(missing.size(), 10); i++) {
            plugin.getLogger().warning("!    - " + missing.get(i));
        }
        if (missing.size() > 10)
            plugin.getLogger().warning("!    ... and " + (missing.size() - 10) + " more");
        plugin.getLogger().warning("!                                                       !");
        plugin.getLogger().warning("!  Renaming current " + fileName + " to " + compromisedName + "     !");
        plugin.getLogger().warning("!  and creating a fresh one from resources.              !");
        plugin.getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        plugin.getLogger().warning("");

        File configFile = new File(plugin.getDataFolder(), fileName);
        File compromisedFile = new File(plugin.getDataFolder(), compromisedName);
        try {
            if (compromisedFile.exists()) {
                compromisedFile.delete();
            }
            if (configFile.exists()) {
                Files.move(configFile.toPath(), compromisedFile.toPath());
                plugin.getLogger().info("[ConfigValidator] Renamed " + fileName + " → " + compromisedName);
            }
            restoreAction.run();
            plugin.getLogger().info("[ConfigValidator] ✓ Fresh " + fileName + " created from resources.");
        } catch (IOException e) {
            plugin.getLogger().severe("[ConfigValidator] Failed to rename " + fileName + ": " + e.getMessage());
        }
    }
}
