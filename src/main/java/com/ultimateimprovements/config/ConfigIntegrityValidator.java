package com.ultimateimprovements.config;

import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.util.ConsoleLogger;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.*;
import java.nio.file.Files;

/**
 * Проверяет целостность config.yml при старте плагина.
 * <p>
 * С v26.2 всѐ живет в config.yml (сообщения + гайд + настройки + meta-хеш).
 * Поэтому отдельный файл messages.yml больше не валидируется здесь —
 * миграция устаревших файлов делается в {@link MessagesManager#init(Main)} и
 * {@link ConfigGuideManager#init(Main)}, а валидация значений — только по config.yml.
 * <p>
 * Использует {@link ConfigRepairManager} для умного ремонта:
 * недостающие ключи ДОБАВЛЯЮТСЯ в конец файла, существующие значения НЕ ТРОГАЮТСЯ.
 * <p>
 * Дополнительно выполняет валидацию значений config.yml через
 * {@link ConfigValueValidator} (типы, диапазоны, пустые строки, символы).
 */
public class ConfigIntegrityValidator {

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
        // ⚠ ВАЖНО: Не запускать, если repair уже сработал.
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

        // Валидация значений (делегировано в ConfigValueValidator)
        ConfigValueValidator.validateValues(plugin, config);
    }

    /**
     * Проверяет, содержит ли файл маркер старых repair-добавлений (от прошлых версий,
     * когда сообщения хранились в отдельном файле). Если да — значит есть YAML-дубликаты,
     * которые нужно вычистить при следующем сохранении config.yml.
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
            // Если файл не читается — пропускаем чистку
        }
        return false;
    }

    /**
     * Удаляет устаревшие файлы из dataFolder. Вызывается после успешной валидации.
     * <p>
     * С v26.2 эти файлы не нужны — всё внутри config.yml. Если они остались — тихо
     * удаляем, чтобы пользовательский каталог оставался чистым.
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
