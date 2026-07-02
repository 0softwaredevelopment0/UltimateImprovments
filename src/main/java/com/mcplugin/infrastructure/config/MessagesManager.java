package com.mcplugin.infrastructure.config;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.FileLogger;
import com.mcplugin.infrastructure.util.ConsoleLogger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * Управляет файлом messages.yml — все сообщения плагина вынесены сюда
 * для удобной настройки и перевода.
 * <p>
 * Всегда загружается messages.yml (английский по умолчанию).
 * Игрок может самостоятельно перевести сообщения в этом файле.
 */
public class MessagesManager {

    private static final String MESSAGES_FILE = "messages.yml";
    private static FileConfiguration messages;
    private static Main plugin;

    private MessagesManager() {}

    /**
     * Инициализирует MessagesManager: сохраняет messages.yml из ресурсов
     * (если не существует), проверяет целостность и загружает.
     */
    public static void init(Main plugin) {
        MessagesManager.plugin = plugin;

        saveMessagesFile();
        ConfigIntegrityValidator.validateMessages(plugin);
        reload();

        ConsoleLogger.info("[Messages] Loaded: " + MESSAGES_FILE);
    }

    /**
     * Сохраняет messages.yml из ресурсов плагина, если файл ещё не существует.
     */
    private static void saveMessagesFile() {
        File messagesFile = new File(plugin.getDataFolder(), MESSAGES_FILE);
        if (!messagesFile.exists()) {
            try {
                plugin.saveResource(MESSAGES_FILE, false);
                ConsoleLogger.info("[Messages] Created new file: " + MESSAGES_FILE);
            } catch (Exception e) {
                FileLogger.logError("Messages", "Failed to save " + MESSAGES_FILE + " from resources");
            }
        } else {
            ConsoleLogger.info("[Messages] File exists: " + MESSAGES_FILE);
        }
    }

    /**
     * Перезагружает messages.yml с диска.
     * Вызывается при /mp reload.
     */
    public static void reload() {
        File messagesFile = new File(plugin.getDataFolder(), MESSAGES_FILE);
        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    /**
     * Возвращает строку сообщения из messages.yml по указанному пути.
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
     * Проверяет, загружен ли messages.yml.
     */
    public static boolean isLoaded() {
        return messages != null;
    }

    /**
     * Возвращает имя загруженного messages-файла (всегда "messages.yml").
     */
    public static String getMessagesFileName() {
        return MESSAGES_FILE;
    }
}
