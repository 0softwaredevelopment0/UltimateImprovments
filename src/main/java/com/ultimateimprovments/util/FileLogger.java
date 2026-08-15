package com.ultimateimprovments.util;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Utility for logging the creation of plugin files and directories at startup.
 * <p>
 * Used during initialization so the server admin sees in the console which files
 * were created, which already existed, and — if creation failed — a full stacktrace.
 */
public final class FileLogger {

    private FileLogger() {}

    // =========================
    // FILE
    // =========================

    /**
     * Checks whether a file exists and logs the result.
     * If the file does not exist — tries to create it.
     *
     * @param file        the file to check/create
     * @param description human-readable description (e.g. "Config", "Database")
     * @param logger      the plugin logger
     */
    public static void ensureFile(File file, String description, Logger logger) {
        if (file.exists()) {
            logger.info("[" + description + "] File exists: " + file.getName());
            return;
        }

        // Create the parent directory if needed
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

    /**
     * Checks whether a file exists and logs the result (via ConsoleLogger).
     *
     * @param file        the file to check/create
     * @param description human-readable description (e.g. "Config", "Database")
     */
    public static void ensureFile(File file, String description) {
        ensureFile(file, description, null);
    }

    // =========================
    // DIRECTORY
    // =========================

    /**
     * Checks whether a directory exists and logs the result.
     * If the directory does not exist — tries to create it.
     *
     * @param dir         the directory to check/create
     * @param description human-readable description
     * @param logger      the plugin logger
     */
    public static void ensureDirectory(File dir, String description, Logger logger) {
        if (dir.exists()) {
            if (dir.isDirectory()) {
                ConsoleLogger.info("[" + description + "] Directory exists: " + dir.getPath());
            } else {
                ConsoleLogger.warn("[" + description + "] Path exists but is NOT a directory: " + dir.getPath());
            }
            return;
        }

        try {
            if (dir.mkdirs()) {
                ConsoleLogger.info("[" + description + "] Created directory: " + dir.getPath());
            } else {
                ConsoleLogger.error("[" + description + "] ERROR: Failed to create directory: " + dir.getPath());
            }
        } catch (Exception e) {
            ConsoleLogger.error("[" + description + "] ERROR: Failed to create directory: " + dir.getPath());
        }
    }

    /**
     * Checks whether a directory exists and logs the result (via ConsoleLogger).
     *
     * @param dir         the directory to check/create
     * @param description human-readable description
     */
    public static void ensureDirectory(File dir, String description) {
        ensureDirectory(dir, description, null);
    }

    // =========================
    // RESOURCE (saveResource wrapper)
    // =========================

    /**
     * Logs the result of saveResource from JavaPlugin.
     *
     * @param success      true if saveResource returned true / did not throw
     * @param resourceName the resource name (e.g. "messages.yml")
     * @param description  human-readable description
     * @param logger       the plugin logger
     */
    public static void logResourceSave(boolean success, String resourceName, String description, Logger logger) {
        if (success) {
            ConsoleLogger.info("[" + description + "] Created new file from resources: " + resourceName);
        } else {
            ConsoleLogger.error("[" + description + "] ERROR: Failed to save resource: " + resourceName);
        }
    }

    /**
     * Logs the result of saveResource (via ConsoleLogger).
     */
    public static void logResourceSave(boolean success, String resourceName, String description) {
        logResourceSave(success, resourceName, description, null);
    }

    /**
     * Logs an error with an exception.
     *
     * @param description human-readable description
     * @param message     the error message
     * @param logger      the plugin logger
     * @param thrown      the exception (may be null)
     */
    public static void logError(String description, String message, Logger logger, Throwable thrown) {
        ConsoleLogger.error("[" + description + "] ERROR: " + message);
        if (thrown != null) {
            thrown.printStackTrace();
        }
    }

    /**
     * Logs an error (via ConsoleLogger).
     */
    public static void logError(String description, String message) {
        logError(description, message, null, null);
    }
}
