package com.mcplugin.config;

import com.mcplugin.Main;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * Управляет файлом messages.yml — все сообщения плагина вынесены сюда
 * из config.yml для удобной настройки и перевода.
 * <p>
 * При старте messages.yml создаётся из ресурсов, если ещё не существует.
 * Использует те же пути, что и в config.yml, чтобы код мог просто
 * заменить {@code getConfig().getString(path, def)} на
 * {@code MessagesManager.getString(path, def)} без изменения путей.
 */
public class MessagesManager {

    private static final String MESSAGES_FILE_NAME = "messages.yml";
    private static FileConfiguration messages;
    private static Main plugin;

    private MessagesManager() {}

    /**
     * Инициализирует MessagesManager: сохраняет messages.yml из ресурсов
     * (если не существует), проверяет целостность и загружает.
     * <p>
     * Если integrity check не проходит — файл переименовывается
     * в compromised-messages.yml и создаётся свежий из ресурсов.
     */
    public static void init(Main plugin) {
        MessagesManager.plugin = plugin;
        saveDefaultMessages();
        // Проверка целостности: если чего-то не хватает,
        // ConfigIntegrityValidator переименует в compromised-messages.yml
        // и создаст свежий файл из ресурсов
        ConfigIntegrityValidator.validateMessages(plugin);
        // Если файл был пересоздан — saveDefaultMessages(false) не сработает,
        // потому что файл уже существует (только что создан через saveResource).
        // Просто загружаем его.
        reload();
    }

    /**
     * Сохраняет дефолтный messages.yml из ресурсов плагина,
     * если файл ещё не существует в папке плагина.
     */
    private static void saveDefaultMessages() {
        File messagesFile = new File(plugin.getDataFolder(), MESSAGES_FILE_NAME);
        if (!messagesFile.exists()) {
            plugin.saveResource(MESSAGES_FILE_NAME, false);
        }
    }



    /**
     * Перезагружает messages.yml с диска.
     * Вызывается при {@code /mcplugin reload}.
     */
    public static void reload() {
        File messagesFile = new File(plugin.getDataFolder(), MESSAGES_FILE_NAME);
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
}
