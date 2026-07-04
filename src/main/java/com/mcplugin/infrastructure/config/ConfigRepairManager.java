package com.mcplugin.infrastructure.config;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.ConsoleLogger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;

/**
 * 🔧 ConfigRepairManager — умный ремонт конфигов.
 * <p>
 * Вместо полной замены файла (как было с compromised-*) находит недостающие ключи
 * в текущем конфиге относительно эталона из JAR и ДОБАВЛЯЕТ их в конец файла.
 * <p>
 * Все существующие значения СОХРАНЯЮТСЯ — не нужно перенастраивать конфиг заново.
 */
public class ConfigRepairManager {

    private ConfigRepairManager() {}

    /**
     * Проверяет и чинит конфиг: если есть недостающие ключи — добавляет их в конец файла.
     *
     * @param plugin         экземпляр плагина
     * @param resourcePath   путь к эталону в JAR (например "config.yml")
     * @param config         текущий загруженный конфиг
     * @param dataFile       файл конфига на диске
     * @return true если были добавлены новые ключи, false если всё в порядке
     */
    public static boolean repair(Main plugin, String resourcePath, FileConfiguration config, File dataFile) {
        FileConfiguration defaultConfig = loadDefaultResource(plugin, resourcePath);
        if (defaultConfig == null) return false;

        // Собираем все пути из эталона
        Set<String> requiredPaths = collectAllPaths(defaultConfig);

        // Ищем недостающие
        List<String> missing = findMissingPaths(config, requiredPaths);

        if (missing.isEmpty()) {
            return false; // всё ок
        }

        ConsoleLogger.warn("[ConfigRepair] Missing " + missing.size() + " key(s) in " + dataFile.getName());
        for (String path : missing) {
            ConsoleLogger.warn("[ConfigRepair]   + " + path);
        }

        // Добавляем недостающие ключи в конец файла
        appendMissingKeys(plugin, config, dataFile, defaultConfig, missing);
        ConsoleLogger.info("[ConfigRepair] ✔ Added " + missing.size() + " missing key(s) to " + dataFile.getName());
        return true;
    }

    /**
     * Добавляет недостающие ключи в конец YAML-файла, сохраняя структуру.
     */
    /**
     * Добавляет недостающие ключи в конфиг.
     * <p>
     * Два режима:
     * <ol>
     *   <li><b>Новые root-секции</b> (корень не существует в userConfig) — дописываются YAML-блоком
     *       в конец файла. Это безопасно, т.к. дубликатов не будет.</li>
     *   <li><b>Под-ключи в существующих секциях</b> (корень уже есть) — устанавливаются через
     *       {@code config.set()}, после чего весь файл перезаписывается {@code config.save()}.
     *       Это необходимо, чтобы SnakeYAML не перезатёр существующие значения дубликатом root-секции.</li>
     * </ol>
     */
    private static void appendMissingKeys(Main plugin, FileConfiguration userConfig, File dataFile, FileConfiguration defaultConfig, List<String> missing) {
        // Разделяем недостающие ключи на две группы:
        //   A — root-секция уже существует → config.set() + save
        //   B — новая root-секция → YAML append (без риска дубликата)
        List<String> appendPaths = new ArrayList<>();
        boolean needsFullSave = false;

        for (String path : missing) {
            String rootKey = path.contains(".") ? path.substring(0, path.indexOf('.')) : path;

            if (userConfig.isSet(rootKey)) {
                // Root-секция уже существует — устанавливаем через set(),
                // чтобы избежать YAML-дубликата root-ключа
                userConfig.set(path, defaultConfig.get(path));
                needsFullSave = true;
            } else {
                appendPaths.add(path);
            }
        }

        // ── Группа A: под-ключи в существующих секциях → config.set() + save ──
        // Выполняется ПЕРВЫМ, чтобы save() не перезаписал YAML-append, который будет после.
        if (needsFullSave) {
            try {
                userConfig.save(dataFile);
                ConsoleLogger.info("[ConfigRepair] Saved config with merged missing sub-keys.");
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "[ConfigRepair] Failed to save config after merging missing keys", e);
            }
        }

        // ── Группа B: новые root-секции → YAML append в КОНЕЦ уже сохранённого файла ──
        if (!appendPaths.isEmpty()) {
            appendPaths.sort(Comparator.comparingInt(String::length));

            StringBuilder appendix = new StringBuilder();
            appendix.append("\n");
            appendix.append("# === Missing keys added by MC-Plugin (auto-repair) ===\n");

            // Группируем по корневой секции
            Map<String, Set<String>> sectionMap = new LinkedHashMap<>();
            for (String path : appendPaths) {
                String rootKey = path.contains(".") ? path.substring(0, path.indexOf('.')) : path;
                String relativePath = path.contains(".") ? path.substring(path.indexOf('.') + 1) : "";
                sectionMap.computeIfAbsent(rootKey, k -> new LinkedHashSet<>()).add(relativePath);
            }

            for (Map.Entry<String, Set<String>> entry : sectionMap.entrySet()) {
                String rootKey = entry.getKey();
                Set<String> subPaths = entry.getValue();

                if (defaultConfig.isConfigurationSection(rootKey)) {
                    ConfigurationSection section = defaultConfig.getConfigurationSection(rootKey);
                    appendix.append(rootKey).append(":\n");
                    for (String subPath : subPaths) {
                        if (subPath.isEmpty()) {
                            Object val = defaultConfig.get(rootKey);
                            appendix.append("  ").append(formatYamlValue(rootKey, val)).append("\n");
                        } else {
                            appendYamlPath(appendix, section, subPath, 1);
                        }
                    }
                } else {
                    Object val = defaultConfig.get(rootKey);
                    appendix.append(formatYamlValue(rootKey, val)).append("\n");
                }
            }

            try (FileWriter fw = new FileWriter(dataFile, true)) {
                fw.write(appendix.toString());
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "[ConfigRepair] Failed to append keys to " + dataFile.getName(), e);
            }
        }
    }

    /**
     * Рекурсивно строит YAML-путь для вложенного ключа.
     */
    private static void appendYamlPath(StringBuilder sb, ConfigurationSection section, String path, int depth) {
        String indent = "  ".repeat(depth);
        int dot = path.indexOf('.');
        String key;
        String rest;

        if (dot >= 0) {
            key = path.substring(0, dot);
            rest = path.substring(dot + 1);
        } else {
            key = path;
            rest = null;
        }

        if (rest != null && section.isConfigurationSection(key)) {
            sb.append(indent).append(key).append(":\n");
            appendYamlPath(sb, section.getConfigurationSection(key), rest, depth + 1);
        } else {
            Object val = section.get(path);
            if (val == null) {
                // Попробуем получить прямое значение ключа
                val = section.get(key);
            }
            sb.append(indent).append(formatYamlValue(key, val)).append("\n");
        }
    }

    /**
     * Форматирует YAML-значение: строки в кавычки, списки и т.д.
     */
    private static String formatYamlValue(String key, Object value) {
        if (value == null) {
            return key + ": null";
        }
        if (value instanceof Boolean || value instanceof Number) {
            return key + ": " + value;
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.isEmpty()) {
                return key + ": []";
            }
            StringBuilder sb = new StringBuilder(key).append(":\n");
            for (Object item : list) {
                sb.append("  - ").append(formatScalar(item)).append("\n");
            }
            return sb.toString().trim();
        }
        // Строка — в кавычки (экранируем бэкслеши, чтобы SnakeYAML не споткнулся о \p, \n и т.д.)
        return key + ": \"" + value.toString().replace("\\", "\\\\") + "\"";
    }

    private static String formatScalar(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        String str = value.toString();
        if (str.contains(" ") || str.contains("\\")) return "\"" + str.replace("\\", "\\\\") + "\"";
        return str;
    }

    // =========================
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ (из ConfigIntegrityValidator)
    // =========================

    private static FileConfiguration loadDefaultResource(Main plugin, String resourcePath) {
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) return null;
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in));
        } catch (Exception e) {
            ConsoleLogger.warn("[ConfigRepair] Failed to load default " + resourcePath + ": " + e.getMessage());
            return null;
        }
    }

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

    private static List<String> findMissingPaths(FileConfiguration config, Set<String> requiredPaths) {
        List<String> missing = new ArrayList<>();
        for (String path : requiredPaths) {
            if (!config.isSet(path)) missing.add(path);
        }
        return missing;
    }
}
