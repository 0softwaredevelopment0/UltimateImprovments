package com.mcplugin.infrastructure.config;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.ConsoleLogger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;

/**
 * Проверяет целостность config.yml и messages.yml при старте плагина.
 * <p>
 * Использует {@link ConfigRepairManager} для умного ремонта:
 * недостающие ключи ДОБАВЛЯЮТСЯ в конец файла, существующие значения НЕ ТРОГАЮТСЯ.
 * Полная замена файла (compromised-*) НЕ ИСПОЛЬЗУЕТСЯ.
 * <p>
 * Дополнительно выполняет валидацию значений config.yml через
 * {@link ConfigValueValidator} (типы, диапазоны, пустые строки, символы).
 */
public class ConfigIntegrityValidator {

    private static final String MESSAGES_FILE = "messages.yml";

    private ConfigIntegrityValidator() {}

    // =========================
    // CONFIG.YML VALIDATION
    // =========================
    public static void validate(Main plugin) {
        File configFile = new File(plugin.getDataFolder(), "config.yml");

        // 🧹 Шаг 1: удаляем дубликаты root-level ключей (оставляем ПЕРВЫЕ вхождения)
        boolean cleaned = YamlDuplicateCleaner.cleanDuplicates(configFile, "config.yml");
        if (cleaned) {
            plugin.reloadConfig();
        }

        FileConfiguration config = plugin.getConfig();

        // Умный ремонт: недостающие ключи добавляются в конец файла
        boolean repaired = ConfigRepairManager.repair(plugin, "config.yml", config, configFile);

        if (repaired) {
            plugin.reloadConfig();
            config = plugin.getConfig();
        }

        // 🧹 Одноразовая чистка: если repair НЕ выполнялся (не было ни Group A, ни Group B),
        // но в файле остались маркеры от старых repair-запусков — пересохраняем конфиг
        // через config.save(). Это убирает YAML-дубликаты root-секций.
        //
        // ⚠ ВАЖНО: Не запускать, если repair уже сработал:
        //   - Group A (set+save): save уже убрал старые маркеры — чистка не нужна
        //   - Group B (YAML append): config.save() перезатрёт только что добавленные ключи!
        if (!repaired && fileContainsMarker(configFile)) {
            try {
                config.save(configFile);
                plugin.reloadConfig();
                config = plugin.getConfig();
                ConsoleLogger.info("[ConfigRepair] Cleaned up duplicate YAML sections from " + configFile.getName());
            } catch (IOException e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "[ConfigRepair] Failed to clean up config file", e);
            }
        }

        // Валидация значений (делегировано в ConfigValueValidator)
        ConfigValueValidator.validateValues(plugin, config);
    }

    /**
     * Проверяет, содержит ли файл маркер старых repair-добавлений.
     * Если да — значит есть YAML-дубликаты, которые нужно вычистить.
     */
    private static boolean fileContainsMarker(File file) {
        if (!file.exists()) return false;
        try (BufferedReader reader = new BufferedReader(new java.io.FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("#") && line.contains("Missing keys")) {
                    return true;
                }
            }
        } catch (IOException e) {
            // Если файл не читается — пропускаем чистку
        }
        return false;
    }

    // =========================
    // MESSAGES VALIDATION
    // =========================
    public static void validateMessages(Main plugin) {
        File messagesFile = new File(plugin.getDataFolder(), MESSAGES_FILE);
        if (!messagesFile.exists()) {
            // При первом запуске MessagesManager создаст файл из ресурсов
            return;
        }

        // 🧹 Шаг 1: удаляем дубликаты root-level ключей
        YamlDuplicateCleaner.cleanDuplicates(messagesFile, MESSAGES_FILE);

        FileConfiguration userMessages = YamlConfiguration.loadConfiguration(messagesFile);

        // Умный ремонт: недостающие ключи добавляются в конец файла
        boolean repaired = ConfigRepairManager.repair(plugin, MESSAGES_FILE, userMessages, messagesFile);

        if (repaired) {
            // Перезагружаем messages через MessagesManager
            MessagesManager.reload();
        }
    }
}
