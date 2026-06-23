package com.mcplugin.infrastructure.util;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Утилита для логирования создания файлов и директорий плагина при старте.
 * <p>
 * Используется на этапе инициализации, чтобы администратор сервера видел в консоли,
 * какие файлы были созданы, какие уже существовали, и если создание не удалось — полный стектрейс.
 */
public final class FileLogger {

    private FileLogger() {}

    // =========================
    // FILE
    // =========================

    /**
     * Проверяет существование файла и логирует результат.
     * Если файла нет — пытается создать.
     *
     * @param file        файл для проверки/создания
     * @param description человекочитаемое описание (например "Config", "Database")
     * @param logger      логгер плагина
     */
    public static void ensureFile(File file, String description, Logger logger) {
        if (file.exists()) {
            logger.info("[" + description + "] File exists: " + file.getName());
            return;
        }

        // Создаём родительскую директорию, если нужно
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            try {
                if (parent.mkdirs()) {
                    logger.info("[" + description + "] Created directory: " + parent.getPath());
                }
            } catch (Exception e) {
                logger.log(java.util.logging.Level.SEVERE, "[" + description + "] Failed to create directory: " + parent.getPath(), e);
            }
        }

        try {
            if (file.createNewFile()) {
                logger.info("[" + description + "] Created new file: " + file.getName());
            } else {
                // createNewFile returns false if the file already existed (race condition)
                logger.info("[" + description + "] File exists: " + file.getName());
            }
        } catch (IOException e) {
            logger.log(java.util.logging.Level.SEVERE, "[" + description + "] ERROR: Failed to create file: " + file.getAbsolutePath(), e);
        }
    }

    // =========================
    // DIRECTORY
    // =========================

    /**
     * Проверяет существование директории и логирует результат.
     * Если директории нет — пытается создать.
     *
     * @param dir         директория для проверки/создания
     * @param description человекочитаемое описание
     * @param logger      логгер плагина
     */
    public static void ensureDirectory(File dir, String description, Logger logger) {
        if (dir.exists()) {
            if (dir.isDirectory()) {
                logger.info("[" + description + "] Directory exists: " + dir.getPath());
            } else {
                logger.warning("[" + description + "] Path exists but is NOT a directory: " + dir.getPath());
            }
            return;
        }

        try {
            if (dir.mkdirs()) {
                logger.info("[" + description + "] Created directory: " + dir.getPath());
            } else {
                logger.severe("[" + description + "] ERROR: Failed to create directory: " + dir.getPath());
            }
        } catch (Exception e) {
            logger.log(java.util.logging.Level.SEVERE, "[" + description + "] ERROR: Failed to create directory: " + dir.getPath(), e);
        }
    }

    // =========================
    // RESOURCE (saveResource wrapper)
    // =========================

    /**
     * Логирует результат saveResource из JavaPlugin.
     *
     * @param success      true если saveResource вернул true / не выбросил исключение
     * @param resourceName имя ресурса (например "messages.yml")
     * @param description  человекочитаемое описание
     * @param logger       логгер плагина
     */
    public static void logResourceSave(boolean success, String resourceName, String description, Logger logger) {
        if (success) {
            logger.info("[" + description + "] Created new file from resources: " + resourceName);
        } else {
            logger.severe("[" + description + "] ERROR: Failed to save resource: " + resourceName);
        }
    }

    /**
     * Логирует ошибку с исключением.
     *
     * @param description человекочитаемое описание
     * @param message     сообщение об ошибке
     * @param logger      логгер плагина
     * @param thrown      исключение (может быть null)
     */
    public static void logError(String description, String message, Logger logger, Throwable thrown) {
        logger.severe("[" + description + "] ERROR: " + message);
        if (thrown != null) {
            thrown.printStackTrace();
        }
    }
}
