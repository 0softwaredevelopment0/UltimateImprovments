package com.mcplugin.infrastructure.config;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.FileLogger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.logging.Logger;

/**
 * Управляет файлом messages-*.yml — все сообщения плагина вынесены сюда
 * для удобной настройки и перевода.
 * <p>
 * Язык выбирается в config.yml: messages.lang (ru | en). По умолчанию ru.
 * Загружается messages-{lang}.yml. Если файла нет — создаётся из ресурсов.
 * Если ресурса нет — fallback на messages.yml.
 */
public class MessagesManager {

    private static final String DEFAULT_MESSAGES = "messages.yml";
    private static String messagesFileName = DEFAULT_MESSAGES;
    private static FileConfiguration messages;
    private static Main plugin;

    private MessagesManager() {}

    /**
     * Инициализирует MessagesManager: читает lang из config,
     * сохраняет messages-{lang}.yml из ресурсов (если не существует),
     * проверяет целостность и загружает.
     */
    public static void init(Main plugin) {
        MessagesManager.plugin = plugin;

        // Читаем язык из config.yml
        String lang = plugin.getConfig().getString("messages.lang", "ru");
        if (!lang.equals("ru") && !lang.equals("en")) {
            lang = "ru";
        }

        messagesFileName = "messages-" + lang + ".yml";
        saveMessagesFile();
        ConfigIntegrityValidator.validateMessages(plugin);
        reload();

        plugin.getLogger().info("[Messages] Loaded: " + messagesFileName);
    }

    /**
     * Сохраняет messages-{lang}.yml из ресурсов плагина,
     * если файл ещё не существует. Fallback на messages.yml.
     */
    private static void saveMessagesFile() {
        File messagesFile = new File(plugin.getDataFolder(), messagesFileName);
        Logger log = plugin.getLogger();
        if (!messagesFile.exists()) {
            try {
                plugin.saveResource(messagesFileName, false);
                log.info("[Messages] Created new file: " + messagesFileName);
                return;
            } catch (Exception e) {
                log.warning("[Messages] Resource " + messagesFileName + " not found, trying fallback: " + DEFAULT_MESSAGES);
            }
        } else {
            log.info("[Messages] File exists: " + messagesFileName);
            return;
        }

        // Fallback на messages.yml
        messagesFileName = DEFAULT_MESSAGES;
        File fallbackFile = new File(plugin.getDataFolder(), messagesFileName);
        if (!fallbackFile.exists()) {
            try {
                plugin.saveResource(messagesFileName, false);
                log.info("[Messages] Created fallback file: " + messagesFileName);
            } catch (Exception e) {
                FileLogger.logError("Messages", "Failed to save " + messagesFileName + " from resources", log, e);
            }
        } else {
            log.info("[Messages] Fallback file exists: " + messagesFileName);
        }
    }

    /**
     * Перезагружает messages-файл с диска.
     * Вызывается при /mp reload.
     */
    public static void reload() {
        File messagesFile = new File(plugin.getDataFolder(), messagesFileName);
        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    /**
     * Возвращает строку сообщения из messages-файла по указанному пути.
     *
     * @param path путь в YAML (например "auth.messages.wrong_password")
     * @param def  значение по умолчанию, если путь не найден
     * @return строка сообщения или def, если путь отсутствует
     */
    public static String getString(String path, String def) {
        if (messages == null) return def;
        return messages.getString(path, def);
    }

    /**
     * Проверяет, загружен ли messages-файл.
     */
    public static boolean isLoaded() {
        return messages != null;
    }

    /**
     * Возвращает имя текущего загруженного messages-файла (например "messages-ru.yml").
     */
    public static String getMessagesFileName() {
        return messagesFileName;
    }
}
