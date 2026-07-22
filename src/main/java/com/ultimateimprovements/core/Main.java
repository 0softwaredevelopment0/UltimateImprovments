package com.ultimateimprovements.core;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Filter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Main — точка входа UltimateImprovements.
 * <p>
 * Инициализация разбита на независимые модули ({@link PluginModule}).
 * Каждый модуль обрабатывается в try-catch: если один модуль падает,
 * остальные продолжают работу.
 * <p>
 * При старте проверяется целостность config.yml — если ключей не хватает,
 * конфиг переименовывается в compromised-config.yml и создаётся свежий.
 */
public class Main extends JavaPlugin {

    private static Main instance;
    private Filter originalLogFilter;

    public static Main getInstance() {
        return instance;
    }

    public java.io.File getPluginFile() {
        return getFile();
    }

    @Override
    public void onEnable() {
        instance = this;

        // Подавляем 'Fatal error trying to convert...' от Paper — это сообщение
        // печатается Paper для КАЖДОГО класса с неподдерживаемой Java-версией
        // и не несёт полезной информации (нельзя починить, только обновить Java).
        suppressPaperConversionErrors();

        new PluginStartup(this).startupPlugin();
    }

    @Override
    public void onDisable() {
        // Восстанавливаем оригинальный фильтр при выключении
        if (originalLogFilter != null) {
            getLogger().setFilter(originalLogFilter);
        }
        new PluginShutdown(this).shutdownPlugin();
    }

    /**
     * Устанавливает фильтр на логгер плагина, подавляющий сообщения
     * Paper о несовместимости версий class файлов.
     * <p>
     * Paper PluginClassLoader.findClass() логирует 'Fatal error trying to convert'
     * для каждого класса с неподдерживаемой Java-версией. Это бессмысленный спам —
     * игрок не может это починить иначе как обновлением Java.
     * Мы сами пишем чистое предупреждение в checkJavaVersion().
     */
    private void suppressPaperConversionErrors() {
        Logger logger = getLogger();
        originalLogFilter = logger.getFilter();
        logger.setFilter(new Filter() {
            @Override
            public boolean isLoggable(LogRecord record) {
                if (record == null || record.getMessage() == null) return true;
                String msg = record.getMessage();
                // Подавляем 'Fatal error trying to convert' от Paper PluginClassLoader
                if (msg.contains("Fatal error trying to convert")) return false;
                // Подавляем техническое сообщение ASM о неподдерживаемой версии
                if (msg.contains("Unsupported class file major version")) return false;
                return true;
            }
        });
    }
}