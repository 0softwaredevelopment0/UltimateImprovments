package com.ultimateimprovments.config;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.FileLogger;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;

/**
 * Управляет сообщениями плагина. С v26.2 все сообщения хранятся ВНУТРИ config.yml
 * под ключом {@code messages:} (русский) и {@code messages_en:} (английский).
 * <p>
 * Старые отдельные файлы (messages.yml/messages-en.yml) консолидированы в config.yml
 * по запросу пользователя. Метод {@link #init(Main)} автоматически мигрирует
 * устаревшие standalone-файлы из dataFolder в config.yml (один раз при первом запуске).
 * <p>
 * Публичный API ({@link #getString(String, String)}) НЕ ИЗМЕНЁН — call-сайты вызывают
 * {@code MessagesManager.getString("auth.gui.register", default)} и получают
 * строку из {@code config.yml: messages.auth.gui.register} (без префикса в пути).
 */
public class MessagesManager {

    /** Ключ основной (русский) секции сообщений в config.yml. */
    public static final String MESSAGES_KEY = "messages";
    /** Ключ английской секции сообщений в config.yml. */
    public static final String MESSAGES_EN_KEY = "messages_en";

    private static Main plugin;

    private MessagesManager() {}

    /**
     * Инициализирует MessagesManager. Сообщения читаются из config.yml — отдельные файлы
     * messages.yml/messages-en.yml НЕ нужны. Обратная совместимость: если в dataFolder
     * остались старые файлы от предыдущих версий плагина — мигрируем их содержимое
     * в config.yml под {@code messages:} и {@code messages_en:} и удаляем файлы.
     */
    public static void init(Main plugin) {
        MessagesManager.plugin = plugin;
        migrateFromStandaloneFiles();
        ConsoleLogger.info("[Messages] Embedded into config.yml under '" + MESSAGES_KEY
                + "' and '" + MESSAGES_EN_KEY + "' sections.");
    }

    /**
     * Возвращает строку из секции {@code messages:} ({@code messages_en:} отсутствует
     * в БД — обычно fallback на messages). Принимает путь БЕЗ префикса секции:
     * Если у существующего класса call-сайт вызывает
     * {@code getString("auth.gui.register", default)}, метод внутренне читает
     * {@code config.getString("messages.auth.gui.register")}.
     * <p>
     * Если в конфиге отсутствует русский вариант — пытается взять английский fallback.
     */
    public static String getString(String path, String def) {
        if (plugin == null) return def;
        FileConfiguration config = plugin.getConfig();
        // 1. Русский (основной)
        String value = config.getString(MESSAGES_KEY + "." + path, null);
        if (value != null) return value;
        // 2. Английский fallback
        value = config.getString(MESSAGES_EN_KEY + "." + path, null);
        if (value != null) return value;
        return def;
    }

    /**
     * Возвращает строку из английской секции напрямую (минуя fallback в русскую).
     * Используется редко — в основном для тестов или логирования.
     */
    public static String getStringEn(String path, String def) {
        if (plugin == null) return def;
        return plugin.getConfig().getString(MESSAGES_EN_KEY + "." + path, def);
    }

    /**
     * Записывает значение в messages-секцию и сохраняет config.yml.
     * Используется ядром плагина, например при динамической локализации в GUI.
     */
    public static void setString(String path, String value) {
        if (plugin == null) return;
        FileConfiguration config = plugin.getConfig();
        config.set(MESSAGES_KEY + "." + path, value);
        try {
            config.save(new File(plugin.getDataFolder(), "config.yml"));
        } catch (Exception e) {
            FileLogger.logError("Messages", "Failed to save config.yml: " + e.getMessage());
        }
    }

    /**
     * Всегда возвращает {@code true}, т.к. сообщения теперь живут в config.yml
     * (отдельный файл messages.yml больше не существует). Оставлено для совместимости.
     */
    public static boolean isLoaded() {
        return plugin != null && plugin.getConfig().isSet(MESSAGES_KEY);
    }

    /** Для обратной совместимости. Возвращает всегда {@code "config.yml#messages"}. */
    public static String getMessagesFileName() {
        return "config.yml#" + MESSAGES_KEY;
    }

    // ============================================================
    // Миграция устаревших standalone messages-файлов
    // ============================================================

    /**
     * Если в dataFolder остались отдельные messages.yml/messages-en.yml от старых версий
     * плагина — копируем их содержимое в config.yml под соответствующими ключами и удаляем.
     * <p>
     * Для безопасности: никогда не перезаписывает существующие ключи пользователя.
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
                config.save(new File(dataFolder, "config.yml"));
                plugin.reloadConfig();
            } catch (Exception e) {
                FileLogger.logError("Messages", "Failed to save config.yml after migration: " + e.getMessage());
            }
        }
    }

    /**
     * Копирует ключи из YAML-файла в указанную секцию config.yml; существующие ключи
     * НЕ перезаписываются. После успешного мерджа удаляет исходный файл.
     * @return true если что-то было перенесено или файл обработан
     */
    private static boolean migrateFile(File source, String targetKey, FileConfiguration config) {
        try {
            org.bukkit.configuration.file.FileConfiguration sourceCfg =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(source);
            ConfigurationSection sourceSection = sourceCfg;
            int copied = copySectionKeys(sourceSection, config, targetKey);
            if (copied > 0) {
                ConsoleLogger.info("[Messages] Migrated " + copied + " key(s) from "
                        + source.getName() + " to config.yml#" + targetKey);
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

    /** Рекурсивно копирует все leaf-ключи из {@code source} в {@code target.getConfigurationSection(targetKey)}. */
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
