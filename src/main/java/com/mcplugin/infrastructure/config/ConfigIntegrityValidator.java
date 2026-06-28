package com.mcplugin.infrastructure.config;

import com.mcplugin.infrastructure.core.Main;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;

/**
 * Проверяет целостность config.yml и messages.yml при старте плагина.
 * <p>
 * Использует {@link ConfigRepairManager} для умного ремонта — если в конфиге
 * не хватает ключей, они ДОБАВЛЯЮТСЯ в конец файла, а не заменяют весь файл.
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
        FileConfiguration config = plugin.getConfig();

        // Умный ремонт: недостающие ключи добавляются в конец файла
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        boolean repaired = ConfigRepairManager.repair(plugin, "config.yml", config, configFile);

        if (repaired) {
            plugin.reloadConfig();
            config = plugin.getConfig();
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

        // Умный ремонт: недостающие ключи добавляются в конец файла
        boolean repaired = ConfigRepairManager.repair(plugin, MESSAGES_FILE, userMessages, messagesFile);

        if (repaired) {
            // Перезагружаем messages через MessagesManager
            MessagesManager.reload();
        }
    }
}
